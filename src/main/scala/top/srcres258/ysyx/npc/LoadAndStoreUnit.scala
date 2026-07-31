package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.bus.AXI4

/**
  * 访存单元: 集中化管理所有 AXI4 总线访问.
  * 内部包含 IFU 取指和数据访存的仲裁器, 以及对齐逻辑.
  */
class LoadAndStoreUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)
    
    val dataStrobeLen: Int = xLen / 8

    val io = IO(new Bundle {
        // === AXI4 Master (唯一的总线出口) ===
        val memBus = new AXI4(xLen)

        // === 指令取指请求 (来自 IFU) ===
        val ifetchReq = Flipped(Decoupled(Output(new LoadAndStoreUnit.IfetchReq(xLen))))
        val ifetchResp = Decoupled(Output(new LoadAndStoreUnit.IfetchResp(xLen)))

        // === 数据访存请求 (来自 MEMU) ===
        val memReq = Flipped(Decoupled(Output(new LoadAndStoreUnit.MemReq(xLen))))
        val memResp = Decoupled(Output(new LoadAndStoreUnit.MemResp(xLen)))

        // 输出信号: 当前是否正在工作
        val working = Output(Bool())

        // Perf 观测信号 — 只读, 不修改 LSU 行为
        val perfState       = Output(UInt(4.W))   // FSM state (12 states)
        val perfPendingFetch = Output(Bool())      // pendingIsFetch
        val perfPendingWrite = Output(Bool())      // pendingIsWrite
        val perfNeedsByteSplit = Output(Bool())    // needsByteSplit
        val perfPendingLsType = Output(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)) // pendingLsType
    })

    // === 内部寄存器 ===
    val rdata = RegInit(0.U(xLen.W))
    val rresp = RegInit(0.U(AXI4.RESP_WIDTH.W))
    val bresp = RegInit(0.U(AXI4.RESP_WIDTH.W))
    val pendingReq = RegInit(false.B)         // 是否有待处理请求
    val pendingIsFetch = RegInit(false.B)     // 待处理的是取指请求
    val pendingIsWrite = RegInit(false.B)     // 待处理的是写请求
    val pendingAddr = RegInit(0.U(xLen.W))    // 待处理请求地址
    val pendingWriteData = RegInit(0.U(xLen.W))
    val pendingLsType = RegInit(0.U(LoadAndStoreUnit.LS_TYPE_LEN.W))

    val reqArbiter = Module(new Arbiter(new LoadAndStoreUnit.RequestBundle(xLen), 2))
    reqArbiter.io.in(0).valid := io.ifetchReq.valid
    reqArbiter.io.in(0).bits.isFetch := true.B
    reqArbiter.io.in(0).bits.addr := io.ifetchReq.bits.addr
    reqArbiter.io.in(0).bits.writeData := 0.U
    reqArbiter.io.in(0).bits.isWrite := false.B
    reqArbiter.io.in(0).bits.lsType := LoadAndStoreUnit.LS_UNKNOWN.U

    reqArbiter.io.in(1).valid := io.memReq.valid
    reqArbiter.io.in(1).bits.isFetch := false.B
    reqArbiter.io.in(1).bits.addr := io.memReq.bits.addr
    reqArbiter.io.in(1).bits.writeData := io.memReq.bits.writeData
    reqArbiter.io.in(1).bits.isWrite := io.memReq.bits.isWrite
    reqArbiter.io.in(1).bits.lsType := io.memReq.bits.lsType

    reqArbiter.io.out.ready := !pendingReq

    io.ifetchReq.ready := reqArbiter.io.in(0).ready
    io.memReq.ready := reqArbiter.io.in(1).ready

    val pendingByteOffset = pendingAddr(1, 0)
    val pendingByteShift = Cat(pendingByteOffset, 0.U(3.W))
    val alignedWriteData = pendingWriteData << pendingByteShift
    val alignedWriteStrobe = calcDataStrobe(pendingLsType) << pendingByteOffset

    val isWordLoad = pendingLsType === LoadAndStoreUnit.LS_L_W.U
    val isWordStore = pendingLsType === LoadAndStoreUnit.LS_S_W.U
    val needsByteSplit = (isWordLoad || isWordStore) && pendingByteOffset =/= 0.U

    val splitByteIndex = RegInit(0.U(2.W))
    val splitReadData = RegInit(0.U(xLen.W))

    val splitByteAddr = pendingAddr + splitByteIndex
    val splitByteOffset = splitByteAddr(1, 0)
    val splitByteShift = Cat(splitByteOffset, 0.U(3.W))
    val splitBytePos = Cat(splitByteIndex, 0.U(3.W))
    val splitReadByte = (io.memBus.r.bits.data >> splitByteShift)(7, 0)
    val splitReadAccum = splitReadData | (splitReadByte << splitBytePos)
    val splitWriteByte = (pendingWriteData >> splitBytePos)(7, 0)
    val splitWriteData = splitWriteByte << splitByteShift
    val splitWriteStrobe = 1.U(dataStrobeLen.W) << splitByteOffset

    // === 仲裁器状态: 采用标准库 Arbiter, 在空闲时对 IFU / MEMU 做固定优先级选择 ===
    when(reqArbiter.io.out.fire) {
        pendingReq := true.B
        pendingIsFetch := reqArbiter.io.out.bits.isFetch
        pendingIsWrite := reqArbiter.io.out.bits.isWrite
        pendingAddr := reqArbiter.io.out.bits.addr
        pendingWriteData := reqArbiter.io.out.bits.writeData
        pendingLsType := reqArbiter.io.out.bits.lsType
        splitByteIndex := 0.U
        splitReadData := 0.U
    }

    // === AXI4 读事务状态机 ===
    val s_idle :: s_read_ar :: s_read_r :: s_write_aw :: s_write_w :: s_write_b :: s_resp :: s_split_read_ar :: s_split_read_r :: s_split_write_aw :: s_split_write_w :: s_split_write_b :: Nil = Enum(12)
    val state = RegInit(s_idle)

    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(
            pendingReq,
            Mux(
                pendingIsWrite,
                Mux(needsByteSplit, s_split_write_aw, s_write_aw),
                Mux(needsByteSplit, s_split_read_ar, s_read_ar)
            ),
            s_idle
        ),
        s_read_ar -> Mux(io.memBus.ar.fire, s_read_r, s_read_ar),
        s_read_r -> Mux(io.memBus.r.fire, s_resp, s_read_r),
        s_write_aw -> Mux(io.memBus.aw.fire, s_write_w, s_write_aw),
        s_write_w -> Mux(io.memBus.w.fire, s_write_b, s_write_w),
        s_write_b -> Mux(io.memBus.b.fire, s_resp, s_write_b),
        s_split_read_ar -> Mux(io.memBus.ar.fire, s_split_read_r, s_split_read_ar),
        s_split_read_r -> Mux(
            io.memBus.r.fire,
            Mux(splitByteIndex === 3.U, s_resp, s_split_read_ar),
            s_split_read_r
        ),
        s_split_write_aw -> Mux(io.memBus.aw.fire, s_split_write_w, s_split_write_aw),
        s_split_write_w -> Mux(io.memBus.w.fire, s_split_write_b, s_split_write_w),
        s_split_write_b -> Mux(
            io.memBus.b.fire,
            Mux(splitByteIndex === 3.U, s_resp, s_split_write_aw),
            s_split_write_b
        ),
        s_resp -> Mux(
            Mux(pendingIsFetch, io.ifetchResp.fire, io.memResp.fire),
            s_idle, s_resp
        )
    ))

    // === AXI4 AR 通道 ===
    io.memBus.ar.valid := state === s_read_ar || state === s_split_read_ar
    io.memBus.ar.bits.addr := Mux(state === s_split_read_ar, splitByteAddr, pendingAddr)
    io.memBus.ar.bits.id := 0.U
    io.memBus.ar.bits.len := 0.U
    io.memBus.ar.bits.size := Mux(
        state === s_split_read_ar,
        AXI4.sizeToAxSize(1).U,
        Mux(
            pendingIsFetch,
            AXI4.sizeToAxSize(xLen / 8).U,
            calcAxSize(pendingLsType)
        )
    )
    io.memBus.ar.bits.burst := AXI4.BURST_FIXED.U
    when(io.memBus.ar.valid) {
        Assertion.assertMemoryAccessAddress(io.memBus.ar.bits.addr)
    }

    // === AXI4 R 通道 ===
    io.memBus.r.ready := state === s_read_r || state === s_split_read_r
    when(state === s_read_r && io.memBus.r.fire) {
        rdata := io.memBus.r.bits.data
        rresp := io.memBus.r.bits.resp
    }
    when(state === s_split_read_r && io.memBus.r.fire) {
        splitReadData := splitReadAccum
        rdata := splitReadAccum
        rresp := io.memBus.r.bits.resp
        when(splitByteIndex =/= 3.U) {
            splitByteIndex := splitByteIndex + 1.U
        }
    }

    // === AXI4 AW 通道 ===
    io.memBus.aw.valid := state === s_write_aw || state === s_split_write_aw
    io.memBus.aw.bits.addr := Mux(state === s_split_write_aw, splitByteAddr, pendingAddr)
    io.memBus.aw.bits.id := 0.U
    io.memBus.aw.bits.len := 0.U
    io.memBus.aw.bits.size := Mux(state === s_split_write_aw, AXI4.sizeToAxSize(1).U, calcAxSize(pendingLsType))
    io.memBus.aw.bits.burst := AXI4.BURST_FIXED.U
    when(io.memBus.aw.valid) {
        Assertion.assertMemoryAccessAddress(io.memBus.aw.bits.addr)
    }

    // === AXI4 W 通道 ===
    io.memBus.w.valid := state === s_write_w || state === s_split_write_w
    io.memBus.w.bits.data := Mux(state === s_split_write_w, splitWriteData, alignedWriteData)
    io.memBus.w.bits.strb := Mux(state === s_split_write_w, splitWriteStrobe, alignedWriteStrobe)
    io.memBus.w.bits.last := true.B

    // === AXI4 B 通道 ===
    io.memBus.b.ready := state === s_write_b || state === s_split_write_b
    when(state === s_write_b && io.memBus.b.fire) {
        bresp := io.memBus.b.bits.resp
    }
    when(state === s_split_write_b && io.memBus.b.fire) {
        bresp := io.memBus.b.bits.resp
        when(splitByteIndex =/= 3.U) {
            splitByteIndex := splitByteIndex + 1.U
        }
    }

    // === 响应阶段: 清除 pending 并提供数据 ===
    when(state === s_resp) {
        pendingReq := false.B
    }

    io.ifetchResp.valid := state === s_resp && pendingIsFetch
    io.ifetchResp.bits.data := rdata

    io.memResp.valid := state === s_resp && !pendingIsFetch
    io.memResp.bits.readData := rdata
    io.memResp.bits.resp := Mux(pendingIsWrite, bresp, rresp)

    io.working := state =/= s_idle || pendingReq

    io.perfState       := state
    io.perfPendingFetch := pendingIsFetch
    io.perfPendingWrite := pendingIsWrite
    io.perfNeedsByteSplit := needsByteSplit
    io.perfPendingLsType := pendingLsType

    // === 辅助函数: 计算 AXI4 axsize ===
    def calcAxSize(lsTypeIn: UInt): UInt = {
        MuxCase(AXI4.sizeToAxSize(xLen / 8).U, Seq(
            (lsTypeIn === LoadAndStoreUnit.LS_L_B.U)  -> AXI4.sizeToAxSize(1).U,
            (lsTypeIn === LoadAndStoreUnit.LS_L_BU.U) -> AXI4.sizeToAxSize(1).U,
            (lsTypeIn === LoadAndStoreUnit.LS_S_B.U)  -> AXI4.sizeToAxSize(1).U,
            (lsTypeIn === LoadAndStoreUnit.LS_L_H.U)  -> AXI4.sizeToAxSize(2).U,
            (lsTypeIn === LoadAndStoreUnit.LS_L_HU.U) -> AXI4.sizeToAxSize(2).U,
            (lsTypeIn === LoadAndStoreUnit.LS_S_H.U)  -> AXI4.sizeToAxSize(2).U,
            (lsTypeIn === LoadAndStoreUnit.LS_L_W.U)  -> AXI4.sizeToAxSize(4).U,
            (lsTypeIn === LoadAndStoreUnit.LS_S_W.U)  -> AXI4.sizeToAxSize(4).U
        ))
    }

    // === 辅助函数: 计算 data strobe ===
    def calcDataStrobe(lsTypeIn: UInt): UInt = {
        MuxCase(0.U(dataStrobeLen.W), Seq(
            (lsTypeIn === LoadAndStoreUnit.LS_S_B.U) -> 0b0001.U(dataStrobeLen.W),
            (lsTypeIn === LoadAndStoreUnit.LS_S_H.U) -> 0b0011.U(dataStrobeLen.W),
            (lsTypeIn === LoadAndStoreUnit.LS_S_W.U) -> 0b1111.U(dataStrobeLen.W)
        ))
    }
}

object LoadAndStoreUnit {
    val LS_TYPE_LEN: Int = 4

    val LS_L_W: Int = 0
    val LS_L_H: Int = 1
    val LS_L_HU: Int = 2
    val LS_L_B: Int = 3
    val LS_L_BU: Int = 4
    val LS_S_W: Int = 5
    val LS_S_H: Int = 6
    val LS_S_B: Int = 7
    val LS_UNKNOWN: Int = 1 << LS_TYPE_LEN - 1

    class RequestBundle(val xLen: Int) extends Bundle {
        val isFetch = Bool()
        val addr = UInt(xLen.W)
        val writeData = UInt(xLen.W)
        val isWrite = Bool()
        val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
    }

    /**
      * 取指请求: 只需地址.
      */
    class IfetchReq(xLen: Int) extends Bundle {
        val addr = UInt(xLen.W)
    }

    /**
      * 取指响应: 返回指令数据.
      */
    class IfetchResp(xLen: Int) extends Bundle {
        val data = UInt(xLen.W)
    }

    /**
      * 数据访存请求.
      */
    class MemReq(xLen: Int) extends Bundle {
        val addr = UInt(xLen.W)
        val writeData = UInt(xLen.W)
        val isWrite = Bool()
        val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
    }

    /**
      * 数据访存响应.
      */
    class MemResp(xLen: Int) extends Bundle {
        val readData = UInt(xLen.W)
        val resp = UInt(2.W)
    }
}
