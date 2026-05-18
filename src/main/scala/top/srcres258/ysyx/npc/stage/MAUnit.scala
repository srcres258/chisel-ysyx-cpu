package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.dpi.impl.MAUnitDPIBundle
import top.srcres258.ysyx.npc.arbiter.RoundRobinArbiter
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.bus.AXI4
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.Configuration

/**
  * 处理器的访存 (Memory Access) 单元.
  */
class MAUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)
    
    val io = IO(new Bundle {
        val memBus = new AXI4(xLen)
        val clintBus = new AXI4Lite(xLen)
        val arbiterReq = Output(Bool())
        val arbiterGranted = Input(Bool())
        val arbiterRelease = Output(Bool())
        val arbiterReleaseReady = Input(Bool())

        val prevStage = Flipped(Decoupled(Output(new EX_MA_Bundle(xLen))))
        val nextStage = Decoupled(Output(new MA_WB_Bundle(xLen)))

        val dpi = new MAUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val skip = RegInit(false.B)
    val rdata = RegInit(0.U(xLen.W))
    val rresp = RegInit(0.U(AXI4.RESP_WIDTH.W))
    val bresp = RegInit(0.U(AXI4.RESP_WIDTH.W))

    val prevStageData = Wire(new EX_MA_Bundle(xLen))
    val nextStageData = Wire(new MA_WB_Bundle(xLen))

    /* 
    MA 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 EX 单元传送数据.
    2. waitData: 接收来自上游 EX 单元传送的数据, 等待数据稳定到达.
    3. waitArbiterGrant: 向仲裁器申请对 RAM 总线的访问权, 等待仲裁器放行.
    4. load_wait_arready (读事务分支): 向 RAM 总线的 AR 信道输送读地址后, 等待 AR 信道的 ready 信号.
    5. load_wait_rvalid (读事务分支): 等待 RAM 总线的 R 信道的 valid 信号.
    6. store_wait_awready (写事务分支): 向 RAM 总线的 AW 信道输送写地址后, 等待 AW 信道的 ready 信号.
    7. store_wait_wready (写事务分支): 向 RAM 总线的 W 信道输送写数据后, 等待 W 信道的 ready 信号.
    8. store_wait_bvalid (写事务分支): 等待 RAM 总线的 B 信道的 valid 信号.
    9. wait_arbiterReleaseReady: 准备下一处理器阶段单元的数据, 然后向仲裁器释放对 RAM 总线的访问权, 等待仲裁器确认收到释放信号.
    10. wait_nextStage_ready: 向下一处理器阶段单元输送数据, 然后等待下一处理器阶段单元的 ready 信号.
    11. clint_load_wait_arready (CLINT 读事务分支): 向内部 CLINT AXI4-Lite AR 信道输送读地址后, 等待 AR 信道的 ready 信号.
    12. clint_load_wait_rvalid (CLINT 读事务分支): 等待内部 CLINT AXI4-Lite R 信道的 valid 信号.
    
    状态流转方式:
      读事务分支:       +-> 3 -> 4 -> 5      --+
    1 (初始状态) -> 2 --+  --  CLINT 读: 11 -> 12 --+-> 9 -> 10 -> 1 -> ...
      写事务分支:       +-> 3 -> 6 -> 7 -> 8 --+
    */
    val s_idle :: s_waitData :: s_waitArbiterGrant :: (
        s_load_wait_arready :: s_load_wait_rvalid :: (
        s_store_wait_awready :: s_store_wait_wready :: s_store_wait_bvalid :: (
        s_wait_arbiterReleaseReady :: s_wait_nextStage_ready :: (
        s_clint_load_wait_arready :: s_clint_load_wait_rvalid :: Nil)))) = Enum(12)

    val state = RegInit(s_idle)

    // 判断当前访问地址是否在 CLINT 地址空间内
    val address = Wire(UInt(xLen.W))
    address := prevStageData.aluOutput
    val isClintAddr = address >= Configuration.CLINT_ADDR_BASE.U &&
                      address < (Configuration.CLINT_ADDR_BASE + Configuration.CLINT_ADDR_SIZE).U

    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.prevStage.fire, s_waitData, s_idle),
        s_waitData -> MuxCase(s_wait_nextStage_ready, Seq(
            // CLINT 读事务：走内部 CLINT AXI4-Lite 通路
            (isClintAddr && io.prevStage.bits.memReadEnable) -> s_clint_load_wait_arready,
            // CLINT 写事务：走外部 AXI4 总线 (写事务通过 MAU 外部总线, CLINT 模块内部会判断是否为 MTIME 地址)
            (isClintAddr && io.prevStage.bits.memWriteEnable) -> s_waitArbiterGrant,
            // 非 CLINT 读事务：走外部 AXI4 总线 (原路径)
            io.prevStage.bits.memReadEnable -> s_waitArbiterGrant,
            // 非 CLINT 写事务：走外部 AXI4 总线 (原路径)
            io.prevStage.bits.memWriteEnable -> s_waitArbiterGrant
        )),
        s_waitArbiterGrant -> MuxCase(s_wait_arbiterReleaseReady, Seq(
            io.prevStage.bits.memReadEnable -> s_load_wait_arready,
            io.prevStage.bits.memWriteEnable -> s_store_wait_awready
        )),

        s_load_wait_arready -> Mux(io.memBus.ar.fire, s_load_wait_rvalid, s_load_wait_arready),
        s_load_wait_rvalid -> Mux(io.memBus.r.fire, s_wait_arbiterReleaseReady, s_load_wait_rvalid),
        
        s_store_wait_awready -> Mux(io.memBus.aw.fire, s_store_wait_wready, s_store_wait_awready),
        s_store_wait_wready -> Mux(io.memBus.w.fire, s_store_wait_bvalid, s_store_wait_wready),
        s_store_wait_bvalid -> Mux(io.memBus.b.fire, s_wait_arbiterReleaseReady, s_store_wait_bvalid),

        // CLINT 读事务状态
        s_clint_load_wait_arready -> Mux(io.clintBus.ar.fire, s_clint_load_wait_rvalid, s_clint_load_wait_arready),
        s_clint_load_wait_rvalid -> Mux(io.clintBus.r.fire, s_wait_nextStage_ready, s_clint_load_wait_rvalid),

        s_wait_arbiterReleaseReady -> Mux(io.arbiterReleaseReady, s_wait_nextStage_ready, s_wait_arbiterReleaseReady),
        s_wait_nextStage_ready -> Mux(io.nextStage.fire, s_idle, s_wait_nextStage_ready)
    ))
    io.prevStage.ready := state === s_idle
    io.memBus.ar.valid := state === s_load_wait_arready
    io.memBus.r.ready := state === s_load_wait_rvalid
    io.memBus.aw.valid := state === s_store_wait_awready
    io.memBus.w.valid := state === s_store_wait_wready
    io.memBus.b.ready := state === s_store_wait_bvalid
    io.nextStage.valid := state === s_wait_nextStage_ready
    prevStageData := io.prevStage.bits
    io.nextStage.bits := nextStageData
    
    // CLINT AXI4-Lite 总线信号
    io.clintBus.ar.valid := state === s_clint_load_wait_arready
    io.clintBus.ar.bits.addr := address
    io.clintBus.r.ready := state === s_clint_load_wait_rvalid
    // CLINT 写通道未使用, 置默认值
    io.clintBus.aw.valid := false.B
    io.clintBus.aw.bits.addr := 0.U
    io.clintBus.w.valid := false.B
    io.clintBus.w.bits.data := 0.U
    io.clintBus.w.bits.strb := 0.U
    io.clintBus.b.ready := false.B

    when(state === s_waitData) {
        skip := !(io.prevStage.bits.memReadEnable || io.prevStage.bits.memWriteEnable)
    }

    io.arbiterReq := state === s_waitArbiterGrant

    val lsu = Module(new LoadAndStoreUnit(xLen))
    lsu.io.lsType := prevStageData.lsType

    def calcAxSize(lsTypeIn: UInt = io.prevStage.bits.lsType): UInt = {
        val lsType = Wire(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
        lsType := lsTypeIn
        MuxCase(AXI4.sizeToAxSize(xLen / 8).U, Seq(
            (lsType === LoadAndStoreUnit.LS_L_B.U) -> AXI4.sizeToAxSize(1).U,
            (lsType === LoadAndStoreUnit.LS_L_BU.U) -> AXI4.sizeToAxSize(1).U,
            (lsType === LoadAndStoreUnit.LS_S_B.U) -> AXI4.sizeToAxSize(1).U,
            (lsType === LoadAndStoreUnit.LS_L_H.U) -> AXI4.sizeToAxSize(2).U,
            (lsType === LoadAndStoreUnit.LS_L_HU.U) -> AXI4.sizeToAxSize(2).U,
            (lsType === LoadAndStoreUnit.LS_S_H.U) -> AXI4.sizeToAxSize(2).U,
            (lsType === LoadAndStoreUnit.LS_L_W.U) -> AXI4.sizeToAxSize(4).U,
            (lsType === LoadAndStoreUnit.LS_S_W.U) -> AXI4.sizeToAxSize(4).U
        ))
    }

    val readDataAligned = Wire(UInt(xLen.W))
    when(io.memBus.ar.valid) {
        Assertion.assertMemoryAccessAddress(io.memBus.ar.bits.addr)
    }
    io.memBus.ar.bits.addr := Mux(io.prevStage.bits.memReadEnable, address, 0.U)
    io.memBus.ar.bits.id := 0.U
    io.memBus.ar.bits.len := 0.U
    io.memBus.ar.bits.size := calcAxSize()
    io.memBus.ar.bits.burst := AXI4.BURST_FIXED.U
    when(state === s_load_wait_rvalid && io.memBus.r.fire) {
        rdata := io.memBus.r.bits.data
        rresp := io.memBus.r.bits.resp
    }
    // CLINT 读事务: 从 clintBus R 信道读取数据
    when(state === s_clint_load_wait_rvalid && io.clintBus.r.fire) {
        rdata := io.clintBus.r.bits.data
        rresp := io.clintBus.r.bits.resp
    }
    // 地址低 2 位: 用于 SRAM 和 MROM 的字节/半字对齐
    val addrLow = Wire(UInt(2.W))
    addrLow := address(1, 0)

    // SRAM (0x0f000000) 和 MROM (0x20000000) 是 AXI4 原生设备, 数据在自然通道上,
    // 需要根据 addrLow 移位使 LSU 从最低字节提取.
    // 通过 APB 桥接的设备 (FLASH/UART/GPIO 等) 已经在 AXI4ToAPB 中处理了对齐.
    // CLINT 地址也不需要在 MAU 做移位
    val isSRAM = address >= 0x0f000000L.U && address < 0x0f002000L.U
    val isMROM = address >= 0x20000000L.U && address < 0x20001000L.U
    val needShift = isSRAM || isMROM

    // 读数据对齐
    val rdataShifted = Wire(UInt(xLen.W))
    rdataShifted := Mux(needShift, rdata >> (addrLow * 8.U), rdata)
    lsu.io.readDataIn := rdataShifted
    readDataAligned := lsu.io.readDataOut

    val writeDataUnaligned = Wire(UInt(xLen.W))
    when(io.memBus.aw.valid) {
        Assertion.assertMemoryAccessAddress(io.memBus.aw.bits.addr)
    }
    io.memBus.aw.bits.addr := Mux(io.prevStage.bits.memWriteEnable, address, 0.U)
    io.memBus.aw.bits.id := 0.U
    io.memBus.aw.bits.len := 0.U
    io.memBus.aw.bits.size := calcAxSize()
    io.memBus.aw.bits.burst := AXI4.BURST_FIXED.U
    writeDataUnaligned := prevStageData.storeData
    lsu.io.writeDataIn := writeDataUnaligned

    // 写数据对齐: 对 SRAM/MROM 需要将数据移位到 AXI4 的正确字节通道,
    // 因为 LSU 总是将数据放在最低字节.
    // APB 桥接设备通过 AXI4ToAPB 处理对齐, 不需要此处移位.
    val wdataShiftAmount = Wire(UInt(2.W))
    when(io.prevStage.bits.lsType === LoadAndStoreUnit.LS_S_B.U) {
        wdataShiftAmount := addrLow
    }.elsewhen(io.prevStage.bits.lsType === LoadAndStoreUnit.LS_S_H.U) {
        wdataShiftAmount := Cat(addrLow(1), false.B)
    }.otherwise {
        wdataShiftAmount := 0.U
    }
    val wdataShifted = Wire(UInt(xLen.W))
    val wstrbShifted = Wire(UInt((xLen / 8).W))
    wdataShifted := Mux(needShift,
        lsu.io.writeDataOut << (wdataShiftAmount * 8.U),
        lsu.io.writeDataOut)
    wstrbShifted := Mux(needShift,
        lsu.io.dataStrobe << wdataShiftAmount,
        lsu.io.dataStrobe)
    io.memBus.w.bits.data := wdataShifted
    io.memBus.w.bits.strb := wstrbShifted
    io.memBus.w.bits.last := true.B
    when(state === s_store_wait_bvalid && io.memBus.b.fire) {
        bresp := io.memBus.b.bits.resp
    }

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.pcTarget := prevStageData.pcTarget
    nextStageData.memReadData := Mux(skip, 0.U, readDataAligned)
    nextStageData.aluOutput := prevStageData.aluOutput
    nextStageData.compBranchEnable := prevStageData.compBranchEnable
    nextStageData.rs1Data := prevStageData.rs1Data
    nextStageData.imm := prevStageData.imm
    nextStageData.rd := prevStageData.rd
    nextStageData.rs1 := prevStageData.rs1
    nextStageData.rs2 := prevStageData.rs2
    nextStageData.csr := prevStageData.csr
    nextStageData.csrData := prevStageData.csrData
    nextStageData.zimm := prevStageData.zimm
    nextStageData.ecallCause := prevStageData.ecallCause
    nextStageData.regWriteEnable := prevStageData.regWriteEnable
    nextStageData.csrRegWriteEnable := prevStageData.csrRegWriteEnable
    nextStageData.regWriteDataSel := prevStageData.regWriteDataSel
    nextStageData.csrRegWriteDataSel := prevStageData.csrRegWriteDataSel
    nextStageData.ecallEnable := prevStageData.ecallEnable

    io.arbiterRelease := state === s_wait_arbiterReleaseReady

    when(state === s_wait_nextStage_ready) {
        io.dpi.memWriteEnable := prevStageData.memWriteEnable
        io.dpi.memReadEnable := prevStageData.memReadEnable
    }.otherwise {
        io.dpi.memWriteEnable := false.B
        io.dpi.memReadEnable := false.B
    }
    io.dpi.ma_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}