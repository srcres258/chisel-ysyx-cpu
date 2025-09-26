package top.srcres258.ysyx.npc.device

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.dpi.impl.CLINTDPIBundle
import top.srcres258.ysyx.npc.ProcessorCore

/**
  * CLINT(Core Local INTerrupt controller) 模块.
  * 
  * 该模块是 RISC-V 系统中较通用的中断控制器, 是一个用于维护时钟中断和软件中断的模块.
  */
class CLINT(val xLen: Int) extends Module {
    /* 
    目前 CLINT 包含一个只读的设备寄存器 mtime.

    考虑该模块的实现方式, 还是通过 DPI-C 接口由仿真环境提供 mtime 的值.
     */

    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val bus = Flipped(new AXI4Lite(xLen))

        val dpi = new CLINTDPIBundle(xLen)
    })

    val readRoutineTimer = RegInit(0.U(CLINT.READ_ROUTINE_TIMER_WIDTH.W))
    val readRoutineTimerMax = RegInit(CLINT.READ_ROUTINE_CLOCK_CYCLES.U(CLINT.READ_ROUTINE_TIMER_WIDTH.W))
    val writeRoutineTimer = RegInit(0.U(CLINT.WRITE_ROUTINE_TIMER_WIDTH.W))
    val writeRoutineTimerMax = RegInit(CLINT.WRITE_ROUTINE_CLOCK_CYCLES.U(CLINT.WRITE_ROUTINE_TIMER_WIDTH.W))
    val readRoutineDone = Wire(Bool())
    val writeRoutineDone = Wire(Bool())
    val araddr = RegInit(0.U(xLen.W))
    val rdata = RegInit(0.U(xLen.W))
    val rresp = RegInit(0.U(AXI4Lite.RESP_WIDTH.W))
    val awaddr = RegInit(0.U(xLen.W))
    val wdata = RegInit(0.U(xLen.W))
    val wstrb = RegInit(0.U(xLen.W))
    val bresp = RegInit(0.U(AXI4Lite.RESP_WIDTH.W))

    val bus = Wire(Flipped(new AXI4Lite(xLen)))
    bus <> io.bus

    /*
    CLINT 模块的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待来自总线中 AR 或 AW 信道的请求.
    2. read_doAction (读事务分支): 进行读事务操作 (目前通过 DPI-C 接口实现), 等待读事务操作完成.
    3. read_wait_rready (读事务分支): 等待来自总线中 R 信道的 ready 信号.
    4. write_wait_wvalid (写事务分支): 等待来自总线中 W 信道的 valid 信号.
    5. write_doAction (写事务分支): 进行写事务操作 (目前通过 DPI-C 接口实现), 等待写事务操作完成.
    6. write_wait_bready (写事务分支): 等待来自总线中 B 信道的 ready 信号.

    状态流转方式:
      读事务分支:  +-> 2 -> 3      --+
    1 (初始状态) --+                 +-> 1 -> ...
      写事务分支:  +-> 4 -> 5 -> 6 --+

    各状态之间所处理的事务:
    1 -> 2: (条件: 来自 AR 信道的 arvalid 信号被设置)
        1. 回复来自 AR 信道的请求.
        2. 从 AR 信道取读地址 (araddr).
    2 -> 3: (读事务分支)
        目前读事务的底层逻辑通过 DPI-C 接口实现:
        1. 置 DPI-C 接口的读取侧的 readEnable 信号为高电平, 以及 readAddress 信号为读地址.
        2. 等待若干个时钟周期, 由常量配置数据定义, 见下方对象声明 (通过计时寄存器实现, 读事务完成后归零).
        3. 从 DPI-C 接口的读取侧的 readData 信号取回所读取的数据.
        4. 恢复 readEnable 信号为低电平, readAddress 信号为 0.
        5. 通过 R 信道从总线回复读事务所得的数据 (rdata) 以及读状态 (rresp).
    3 -> 1: (读事务分支)
        无 (本次读事务处理完毕, 等待下一个事务).
    1 -> 4: (条件: 来自 AW 信道的 awvalid 信号被设置)
        1. 回复来自 AW 信道的请求.
        2. 从 AW 信道取写地址 (awaddr).
    4 -> 5: (写事务分支)
        1. 回复来自 W 信道的请求.
        2. 从 W 信道取写数据 (wdata) 和写掩码 (wstrb).
    5 -> 6: (写事务分支)
        目前写事务的底层逻辑通过 DPI-C 接口实现:
        1. 置 DPI-C 接口的写入侧的 writeEnable 信号为高电平, writeAddress 信号为写地址,
           以及 writeData 信号为写数据, writeDataStrobe 信号为写掩码.
        2. 等待若干个时钟周期, 由常量配置数据定义, 见下方对象声明 (通过计时寄存器实现, 写事务完成后归零).
        3. 恢复 readEnable 信号为低电平, readAddress 信号为 0.
        4. 通过 B 信道从总线回复写事务的写状态 (bresp).
    6 -> 1: (写事务分支)
        无 (本次写事务处理完毕, 等待下一个事务).
     */
    val s_idle :: s_read_doAction :: s_read_wait_rready :: (
        s_write_wait_wvalid :: s_write_doAction :: s_write_wait_bready :: Nil) = Enum(6)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> MuxCase(s_idle, Seq(
            bus.ar.fire -> s_read_doAction,
            bus.aw.fire -> s_write_wait_wvalid
        )),

        s_read_doAction -> Mux(readRoutineDone, s_read_wait_rready, s_read_doAction),
        s_read_wait_rready -> Mux(bus.r.fire, s_idle, s_read_wait_rready),

        s_write_wait_wvalid -> Mux(bus.w.fire, s_write_doAction, s_write_wait_wvalid),
        s_write_doAction -> Mux(writeRoutineDone, s_write_wait_bready, s_write_doAction),
        s_write_wait_bready -> Mux(bus.b.fire, s_idle, s_write_wait_bready)
    ))
    bus.ar.ready := state === s_idle
    bus.aw.ready := state === s_idle
    bus.r.valid := state === s_read_wait_rready
    bus.w.ready := state === s_write_wait_wvalid
    bus.b.valid := state === s_write_wait_bready

    readRoutineDone := readRoutineTimer >= readRoutineTimerMax
    io.dpi.read.readEnable := false.B
    io.dpi.read.readAddress := 0.U
    when(state === s_idle && bus.ar.fire) {
        araddr := bus.ar.bits.addr
    }.elsewhen(state === s_read_doAction) {
        when(readRoutineDone) {
            readRoutineTimer := 0.U
            if (ProcessorCore.enableRandomDelay) {
                readRoutineTimerMax := random.LFSR(CLINT.READ_ROUTINE_TIMER_WIDTH)
            } else {
                readRoutineTimerMax := CLINT.READ_ROUTINE_CLOCK_CYCLES.U
            }
            rdata := io.dpi.read.readData
            rresp := 0.U // TODO: 在读事务逻辑中实现真正的 rresp 信号获取.
        }.otherwise {
            io.dpi.read.readEnable := true.B
            io.dpi.read.readAddress := araddr
            readRoutineTimer := readRoutineTimer + 1.U
        }
    }
    bus.r.bits.data := rdata
    bus.r.bits.resp := rresp

    writeRoutineDone := writeRoutineTimer >= writeRoutineTimerMax
    io.dpi.write.writeEnable := false.B
    io.dpi.write.writeAddress := 0.U
    io.dpi.write.writeData := 0.U
    io.dpi.write.writeDataStrobe := 0.U
    when(state === s_idle && bus.aw.fire) {
        awaddr := bus.aw.bits.addr
    }.elsewhen(state === s_write_wait_wvalid && bus.w.fire) {
        wdata := bus.w.bits.data
        wstrb := bus.w.bits.strb
    }.elsewhen(state === s_write_doAction) {
        when(writeRoutineDone) {
            writeRoutineTimer := 0.U
            if (ProcessorCore.enableRandomDelay) {
                writeRoutineTimerMax := random.LFSR(CLINT.WRITE_ROUTINE_TIMER_WIDTH)
            } else {
                writeRoutineTimerMax := CLINT.WRITE_ROUTINE_CLOCK_CYCLES.U
            }
            bresp := 0.U // TODO: 在写事务逻辑中实现真正的 bresp 信号获取.
        }.otherwise {
            io.dpi.write.writeEnable := true.B
            io.dpi.write.writeAddress := awaddr
            io.dpi.write.writeData := wdata
            io.dpi.write.writeDataStrobe := wstrb
            writeRoutineTimer := writeRoutineTimer + 1.U
        }
    }
    bus.b.bits.resp := bresp
}

object CLINT {
    val READ_ROUTINE_CLOCK_CYCLES: Int = 5
    val WRITE_ROUTINE_CLOCK_CYCLES: Int = 5
    val READ_ROUTINE_TIMER_WIDTH: Int = ProcessorCore.RANDOM_DELAY_WIDTH
    val WRITE_ROUTINE_TIMER_WIDTH: Int = ProcessorCore.RANDOM_DELAY_WIDTH
}
