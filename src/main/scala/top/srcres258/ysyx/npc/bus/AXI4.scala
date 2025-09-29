package top.srcres258.ysyx.npc.bus

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * AMBA AXI4 总线协议的 IO 接口.
  * 
  * 默认定义的 IO 主动方为主设备 (master), 若由从设备 (slave) 定义该 IO 接口,
  * 需要外套 Flipped 反转 IO 方向.
  */
class AXI4(val xLen: Int) extends Bundle {
    Assertion.assertProcessorXLen(xLen)

    // 协议定义的所有信道.
    /**
      * AR (读地址) 信道, 传信方向: master -> slave.
      */
    val ar = Decoupled(new AXI4.AR(xLen))
    /**
      * R (读数据) 信道, 传信方向: slave -> master.
      */
    val r = Flipped(Decoupled(Flipped(new AXI4.R(xLen))))
    /**
      * AW (写地址) 信道, 传信方向: master -> slave.
      */
    val aw = Decoupled(new AXI4.AW(xLen))
    /**
      * W (写数据) 信道, 传信方向: master -> slave.
      */
    val w = Decoupled(new AXI4.W(xLen))
    /**
      * B (写回复) 信道, 传信方向: slave -> master.
      */
    val b = Flipped(Decoupled(Flipped(new AXI4.B(xLen))))
}

object AXI4 {
    val ID_WIDTH: Int = 4
    val LEN_WIDTH: Int = 8
    val SIZE_WIDTH: Int = 3
    val BURST_WIDTH: Int = 2
    val RESP_WIDTH: Int = 2

    val BURST_FIXED: Int = 0
    val BURST_INCR: Int = 1
    val BURST_WRAP: Int = 2

    /**
      * AMBA AXI4 总线协议的 AR (读地址) 信道.
      */
    class AR(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val addr = Output(UInt(xLen.W))
        val id = Output(UInt(ID_WIDTH.W))
        val len = Output(UInt(LEN_WIDTH.W))
        val size = Output(UInt(SIZE_WIDTH.W))
        val burst = Output(UInt(BURST_WIDTH.W))
    }

    /**
      * AMBA AXI4 总线协议的 R (读数据) 信道.
      */
    class R(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val resp = Input(UInt(RESP_WIDTH.W))
        val data = Input(UInt(xLen.W))
        val last = Input(Bool())
        val id = Input(UInt(ID_WIDTH.W))
    }

    /**
      * AMBA AXI4 总线协议的 AW (写地址) 信道.
      */
    class AW(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val addr = Output(UInt(xLen.W))
        val id = Output(UInt(ID_WIDTH.W))
        val len = Output(UInt(LEN_WIDTH.W))
        val size = Output(UInt(SIZE_WIDTH.W))
        val burst = Output(UInt(BURST_WIDTH.W))
    }

    /**
      * AMBA AXI4 总线协议的 W (写数据) 信道.
      */
    class W(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val strbWidth: Int = xLen / 8

        val data = Output(UInt(xLen.W))
        val strb = Output(UInt(strbWidth.W))
        val last = Output(Bool())
    }

    /**
      * AMBA AXI4-Lite 总线协议的 B (写回复) 信道.
      */
    class B(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val resp = Input(UInt(RESP_WIDTH.W))
        val id = Input(UInt(ID_WIDTH.W))
    }

    def defaultValuesForMaster(bus: AXI4): Unit = {
        bus.ar.valid := false.B
        bus.ar.bits.addr := 0.U
        bus.ar.bits.id := 0.U
        bus.ar.bits.len := 0.U
        bus.ar.bits.size := 0.U
        bus.ar.bits.burst := 0.U
        bus.r.ready := false.B
        bus.aw.valid := false.B
        bus.aw.bits.addr := 0.U
        bus.aw.bits.id := 0.U
        bus.aw.bits.len := 0.U
        bus.aw.bits.size := 0.U
        bus.aw.bits.burst := 0.U
        bus.w.valid := false.B
        bus.w.bits.data := 0.U
        bus.w.bits.strb := 0.U
        bus.w.bits.last := false.B
        bus.b.ready := false.B
    }

    def defaultValuesForSlave(bus: AXI4): Unit = {
        bus.ar.ready := false.B
        bus.r.valid := false.B
        bus.r.bits.resp := 0.U
        bus.r.bits.data := 0.U
        bus.r.bits.last := false.B
        bus.r.bits.id := 0.U
        bus.aw.ready := false.B
        bus.w.ready := false.B
        bus.b.valid := false.B
        bus.b.bits.resp := 0.U
        bus.b.bits.id := 0.U
    }
}
