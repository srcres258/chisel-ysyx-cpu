package top.srcres258.ysyx.npc.xbar

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.MemoryRange
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.arbiter.RoundRobinArbiter
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 基于 AMBA AXI4-Lite 总线协议的 Xbar 模块.
  * 
  * 说明: Xbar是一个总线的多路开关模块, 可以根据输入端的总线请求的地址, 将请求转发到不同的输出端,
  * 从而传递给不同的下游模块. 这些下游模块可能是设备, 也可能是另一个Xbar. 
  */
class AXI4LiteXbar(
    val xLen: Int,
    val deviceMemoryRanges: Seq[MemoryRange]
) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val busPorts = Vec(AXI4LiteXbar.ARBITER_MAX_MASTER_AMOUNT, Flipped(new AXI4Lite(xLen)))
        val arbiter = new RoundRobinArbiter.IOBundle(AXI4LiteXbar.ARBITER_MAX_MASTER_AMOUNT)

        val deviceBuses = Vec(deviceMemoryRanges.size, new AXI4Lite(xLen))
    })

    for (i <- 0 until deviceMemoryRanges.size) {
        AXI4Lite.defaultValuesForMaster(io.deviceBuses(i))
    }

    /**
      * 事务类型: 读事务 (read) 或 写事务 (write).
      */
    val routineType_read :: routineType_write :: Nil = Enum(2)

    val curRoutineType = RegInit(routineType_read)
    val curDeviceBusIdx = Wire(UInt(log2Ceil(deviceMemoryRanges.size).W))
    val curDeviceBus = Wire(new AXI4Lite(xLen))
    io.deviceBuses(curDeviceBusIdx) <> curDeviceBus

    val addr = RegInit(0.U(xLen.W))
    val addrValid = Wire(Bool())
    val rdata = Wire(UInt(xLen.W))
    val rresp = Wire(UInt(AXI4Lite.RESP_WIDTH.W))
    val wdata = Wire(UInt(xLen.W))
    val wstrb = Wire(UInt(xLen.W))
    val bresp = Wire(UInt(AXI4Lite.RESP_WIDTH.W))

    /**
      * 仲裁器: 我们这里选用 Round-Robin Arbiter.
      */
    val arbiter = Module(new RoundRobinArbiter(AXI4LiteXbar.ARBITER_MAX_MASTER_AMOUNT))
    io.arbiter <> arbiter.io
    /**
      * 当前由仲裁器所放行的总线信号.
      */
    val bus = Wire(Flipped(new AXI4Lite(xLen)))
    for (i <- 0 until AXI4LiteXbar.ARBITER_MAX_MASTER_AMOUNT) {
        AXI4Lite.defaultValuesForSlave(io.busPorts(i))
    }
    when(arbiter.io.grantIdx === AXI4LiteXbar.ARBITER_MAX_MASTER_AMOUNT.U) {
        AXI4Lite.defaultValuesForMaster(bus)
    }.otherwise {
        bus <> io.busPorts(arbiter.io.grantIdxNormal)
    }

    /*
    AXI4LiteXbar 模块的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待来自总线中 AR 或 AW 信道的请求 (valid 信号).
    2. selectDevice: 根据请求的地址, 选择目标设备.
    3. read_wait_arready (读事务分支): 等待目标设备总线的 AR 信道的 ready 信号.
    4. read_wait_rvalid (读事务分支): 等待目标设备总线的 R 信道的 valid 信号.
    5. read_wait_rready (读事务分支): 等待来自主设备总线的 R 信道的 ready 信号.
    6. read_reply_rready (读事务分支): 向目标设备总线的 R 信道写入 ready 信号作为回复.
    7. write_wait_awready (写事务分支): 等待目标设备总线的 AW 信道的 ready 信号.
    8. write_wait_wvalid (写事务分支): 等待来自主设备总线的 W 信道的 valid 信号.
    9. write_wait_wready (写事务分支): 等待目标设备总线的 W 信道的 ready 信号.
    10. write_wait_bvalid (写事务分支): 等待来自目标设备总线的 B 信道的 valid 信号.
    11. write_wait_bready (写事务分支): 等待来自主设备总线的 B 信道的 ready 信号.
    12. write_reply_bready (写事务分支): 向目标设备总线的 B 信道写入 ready 信号作为回复.
    13. decerr_wait_bready (异常分支): 等待来自目标设备总线的 B 信道的 ready 信号.

    状态流转方式:
      读事务分支:       +-> 3 -> 4 -> 5 -> 6              --+
    1 (初始状态) -> 2 --+                                   +-> 1 -> ...
      写事务分支:       +-> 7 -> 8 -> 9 -> 10 -> 11 -> 12 --+
      异常分支:         +-> 13                            --+
    
    各状态之间所处理的事务:
    1 -> 2:
        1. 从 AR 信道取读地址 (araddr) 或从 AW 信道取写地址 (awaddr).
        2. 判断并记录事务类型 (读事务或写事务).
    2 -> 3 或 7 或 13:
        (间隔一个时钟周期, 待目标设备选定后, 再处理事务.)
        1. 根据读地址或写地址, 选择目标设备.
        2. 根据事务类型, 进入相应分支.
           (1) 若进入读事务分支: 还要向目标设备总线的 AR 信道转发来自主设备总线的 AR 信道的请求内容.
           (2) 若进入写事务分支: 还要向目标设备总线的 AW 信道转发来自主设备总线的 AW 信道的请求内容.
           (3) 若进入异常分支: 表示地址解析错误, 向主设备总线的 B 信道写入 decerr 信号.
    3 -> 4: (读事务分支)
        向目标设备总线的 AR 信道写入 ready 信号作为回复.
    4 -> 5: (读事务分支)
        向主设备总线的 R 信道转发来自目标设备总线的 R 信道的响应内容.
    5 -> 6: (读事务分支)
        向目标设备总线的 R 信道写入 ready 信号作为回复.
        (为时一个时钟周期, 以将来自主设备的回复信号转达到目标设备.)
    6 -> 1: (读事务分支)
        无 (本次读事务处理完毕, 等待下一个事务).
    7 -> 8: (写事务分支)
        向主设备总线的 AW 信道写入 ready 信号作为回复.
    8 -> 9: (写事务分支)
        向目标设备总线的 W 信道写入来自主设备总线的 W 信道的请求内容.
    9 -> 10: (写事务分支)
        向主设备总线的 W 信道写入 ready 信号作为回复.
    10 -> 11: (写事务分支)
        向主设备总线的 B 信道写入来自目标设备总线的 B 信道的响应内容.
    11 -> 12: (写事务分支)
        向目标设备总线的 B 信道写入 ready 信号作为回复.
        (为时一个时钟周期, 以将来自主设备的回复信号转达到目标设备.)
    12 -> 1: (写事务分支)
        无 (本次写事务处理完毕, 等待下一个事务).
    13 -> 1: (异常分支)
        无 (异常处理完毕, 等待下一个事务).
     */
    val s_idle :: s_selectDevice :: s_read_wait_arready :: s_read_wait_rvalid :: (
        s_read_wait_rready :: s_read_reply_rready :: s_write_wait_awready :: (
        s_write_wait_wvalid :: s_write_wait_wready :: s_write_wait_bvalid :: (
        s_write_wait_bready :: s_write_reply_bready :: s_decerr_wait_bready :: Nil))) = Enum(13)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(bus.ar.valid || bus.aw.valid, s_selectDevice, s_idle),
        s_selectDevice -> Mux(
            addrValid,
            Mux(curRoutineType === routineType_read, s_read_wait_arready, s_write_wait_awready),
            s_decerr_wait_bready
        ),

        s_read_wait_arready -> Mux(curDeviceBus.ar.fire, s_read_wait_rvalid, s_read_wait_arready),
        s_read_wait_rvalid -> Mux(curDeviceBus.r.valid, s_read_wait_rready, s_read_wait_rvalid),
        s_read_wait_rready -> Mux(bus.r.fire, s_read_reply_rready, s_read_wait_rready),
        s_read_reply_rready -> s_idle,

        s_write_wait_awready -> Mux(curDeviceBus.aw.fire, s_write_wait_wvalid, s_write_wait_awready),
        s_write_wait_wvalid -> Mux(bus.w.valid, s_write_wait_wready, s_write_wait_wvalid),
        s_write_wait_wready -> Mux(curDeviceBus.w.fire, s_write_wait_bvalid, s_write_wait_wready),
        s_write_wait_bvalid -> Mux(curDeviceBus.b.valid, s_write_wait_bready, s_write_wait_bvalid),
        s_write_wait_bready -> Mux(bus.b.fire, s_write_reply_bready, s_write_wait_bready),
        s_write_reply_bready -> s_idle,

        s_decerr_wait_bready -> s_idle
    ))
    bus.ar.ready := state === s_read_wait_rvalid
    bus.r.valid := state === s_read_wait_rready
    bus.aw.ready := state === s_write_wait_wvalid
    bus.w.ready := state === s_write_wait_bvalid
    bus.b.valid := state === s_write_wait_bready

    when(state === s_idle) {
        when(bus.ar.valid) {
            addr := bus.ar.bits.addr
            curRoutineType := routineType_read
        }.elsewhen(bus.aw.valid) {
            addr := bus.aw.bits.addr
            curRoutineType := routineType_write
        }
    }

    private def judgeAddrValid(addr: UInt): Bool = deviceMemoryRanges
        .map(_.isInRange(addr)).reduce(_ || _)
    private def selectDeviceIdx(addr: UInt): UInt = MuxCase(
        0.U, deviceMemoryRanges.zipWithIndex.map((item) => item._1.isInRange(addr) -> item._2.U)
    )
    addrValid := judgeAddrValid(addr)
    curDeviceBusIdx := selectDeviceIdx(addr)

    curDeviceBus.ar.bits.addr := addr
    curDeviceBus.ar.valid := state === s_read_wait_arready
    bus.ar.ready := state === s_read_wait_rvalid
    rdata := curDeviceBus.r.bits.data
    rresp := curDeviceBus.r.bits.resp
    bus.r.bits.data := rdata
    bus.r.bits.resp := rresp
    bus.r.valid := state === s_read_wait_rready
    curDeviceBus.r.ready := state === s_read_reply_rready

    curDeviceBus.aw.bits.addr := addr
    curDeviceBus.aw.valid := state === s_write_wait_awready
    bus.aw.ready := state === s_write_wait_wvalid
    wdata := bus.w.bits.data
    wstrb := bus.w.bits.strb
    curDeviceBus.w.bits.data := wdata
    curDeviceBus.w.bits.strb := wstrb
    curDeviceBus.w.valid := state === s_write_wait_wready
    bus.w.ready := state === s_write_wait_bvalid
    bresp := Mux(state === s_decerr_wait_bready, AXI4Lite.RESP_DECERR.U, curDeviceBus.b.bits.resp)
    bus.b.bits.resp := bresp
    bus.b.valid := state === s_write_wait_bready || state === s_decerr_wait_bready
    curDeviceBus.b.ready := state === s_write_reply_bready
}

object AXI4LiteXbar {
    val ARBITER_MAX_MASTER_AMOUNT: Int = 4
    val ARBITER_MASTER_IDX_IF_UNIT: Int = 0 // IFUnit
    val ARBITER_MASTER_IDX_MA_UNIT: Int = 1 // MAUnit
}
