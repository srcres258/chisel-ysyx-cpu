package top.srcres258.ysyx.npc.device

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.dpi.impl.CLINTDPIBundle
import top.srcres258.ysyx.npc.Top
import top.srcres258.ysyx.npc.Config

/**
  * ACLINT MTIMER 兼容的 CLINT 模块.
  *
  * 该模块实现 ACLINT v1.0-rc4 标准中 MTIMER 设备的 MTIME 寄存器,
  * 通过 DPI-C 接口从仿真环境获取真实时钟计数.
  *
  * 注意: 当 `Config.enableDPI = false` 时, 该模块不再连接任何 DPI 线网,
  * MTIME 读回值会被硬连线为 0. 这对综合与 STA 没有影响, 但意味着
  * `rdtime` / timer 语义失去真实时间源; 若要上 FPGA 或做真实运行,
  * 需要额外补一个硬件计数器时间源.
  *
  * 当前实现:
  *   - MTIME (0x0200bff8 ~ 0x0200bfff): 64-bit 单调递增时间计数器 (RW)
  *   - MTIMECMP (0x02004000 ~ 0x02007ff7): 预留, 读写返回 0
  *   - MSWI (0x02000000 ~ 0x02003fff): 预留, 读写返回 0
  */
class CLINT(val xLen: Int) extends Module {
    /* 
    目前 CLINT 包含 MTIME 设备寄存器 (64-bit, 通过 DPI-C 提供).
    */

    Assertion.assertProcessorXLen(xLen)

    private val hasDPI = Config.enableDPI

    val io = IO(new Bundle {
        val bus = Flipped(new AXI4Lite(xLen))
    })

    val dpi = if (hasDPI) Some(IO(new CLINTDPIBundle(xLen))) else None

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

    // DPI disabled: MTIME 没有真实时间源, 直接返回 0.
    // 这不会影响综合/STA, 但依赖 rdtime/timer 的软件需要额外硬件时间源.
    val mtimeReadData = if (hasDPI) dpi.get.read.readData else 0.U(xLen.W)

    // 判断地址是否命中 ACLINT MTIME 寄存器范围 (8 字节)
    val araddrIn = Wire(UInt(xLen.W))
    araddrIn := araddr  // 读事务中锁存的读地址
    val hitMTIME_r = araddrIn >= Config.aclintMtimeBase.U &&
                     araddrIn < (Config.aclintMtimeBase + Config.aclintMtimeSize).U
    val hitMTIME_w = awaddr >= Config.aclintMtimeBase.U &&
                     awaddr < (Config.aclintMtimeBase + Config.aclintMtimeSize).U

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
    dpi.foreach { dpiBundle =>
        dpiBundle.read.readEnable := false.B
        dpiBundle.read.readAddress := 0.U
    }
    when(state === s_idle && bus.ar.fire) {
        araddr := bus.ar.bits.addr
    }.elsewhen(state === s_read_doAction) {
        when(readRoutineDone) {
            readRoutineTimer := 0.U
            if (Top.enableRandomDelay) {
                readRoutineTimerMax := random.LFSR(CLINT.READ_ROUTINE_TIMER_WIDTH)
            } else {
                readRoutineTimerMax := CLINT.READ_ROUTINE_CLOCK_CYCLES.U
            }
            // 读数据: MTIME 地址走 DPI-C, 其他 CLINT 内地址返回 0
            rdata := Mux(hitMTIME_r, mtimeReadData, 0.U)
            rresp := 0.U
        }.otherwise {
            dpi.foreach { dpiBundle =>
                dpiBundle.read.readEnable := true.B
                dpiBundle.read.readAddress := araddr
            }
            readRoutineTimer := readRoutineTimer + 1.U
        }
    }
    bus.r.bits.data := rdata
    bus.r.bits.resp := rresp

    writeRoutineDone := writeRoutineTimer >= writeRoutineTimerMax
    dpi.foreach { dpiBundle =>
        dpiBundle.write.writeEnable := false.B
        dpiBundle.write.writeAddress := 0.U
        dpiBundle.write.writeData := 0.U
        dpiBundle.write.writeDataStrobe := 0.U
    }
    when(state === s_idle && bus.aw.fire) {
        awaddr := bus.aw.bits.addr
    }.elsewhen(state === s_write_wait_wvalid && bus.w.fire) {
        wdata := bus.w.bits.data
        wstrb := bus.w.bits.strb
    }.elsewhen(state === s_write_doAction) {
        when(writeRoutineDone) {
            writeRoutineTimer := 0.U
            if (Top.enableRandomDelay) {
                writeRoutineTimerMax := random.LFSR(CLINT.WRITE_ROUTINE_TIMER_WIDTH)
            } else {
                writeRoutineTimerMax := CLINT.WRITE_ROUTINE_CLOCK_CYCLES.U
            }
            bresp := 0.U
        }.otherwise {
            // MTIME 写操作: 通过 DPI-C 写入; 非 MTIME 地址: 忽略 (no-op)
            dpi.foreach { dpiBundle =>
                when(hitMTIME_w) {
                    dpiBundle.write.writeEnable := true.B
                    dpiBundle.write.writeAddress := awaddr
                    dpiBundle.write.writeData := wdata
                    dpiBundle.write.writeDataStrobe := wstrb
                }
            }
            writeRoutineTimer := writeRoutineTimer + 1.U
        }
    }
    bus.b.bits.resp := bresp
}

object CLINT {
    val READ_ROUTINE_CLOCK_CYCLES: Int = 5
    val WRITE_ROUTINE_CLOCK_CYCLES: Int = 5
    val READ_ROUTINE_TIMER_WIDTH: Int = Config.randomDelayWidth
    val WRITE_ROUTINE_TIMER_WIDTH: Int = Config.randomDelayWidth
}
