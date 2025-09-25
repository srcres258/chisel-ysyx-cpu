package top.srcres258.ysyx.npc.bus

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * AMBA AXI4-Lite 总线协议的 IO 接口.
  * 
  * 默认定义的 IO 主动方为主设备 (master), 若由从设备 (slave) 定义该 IO 接口,
  * 需要外套 Flipped 反转 IO 方向.
  */
class AXI4Lite(val xLen: Int) extends Bundle {
    Assertion.assertProcessorXLen(xLen)

    // 协议定义的所有信道.
    /**
      * AR (读地址) 信道, 传信方向: master -> slave.
      */
    val ar = Decoupled(new AXI4Lite.AR(xLen))
    /**
      * R (读数据) 信道, 传信方向: slave -> master.
      */
    val r = Flipped(Decoupled(Flipped(new AXI4Lite.R(xLen))))
    /**
      * AW (写地址) 信道, 传信方向: master -> slave.
      */
    val aw = Decoupled(new AXI4Lite.AW(xLen))
    /**
      * W (写数据) 信道, 传信方向: master -> slave.
      */
    val w = Decoupled(new AXI4Lite.W(xLen))
    /**
      * B (写回复) 信道, 传信方向: slave -> master.
      */
    val b = Flipped(Decoupled(Flipped(new AXI4Lite.B(xLen))))
}

object AXI4Lite {
    val RESP_WIDTH: Int = 2

    /**
      * AMBA AXI4-Lite 总线协议的 AR (读地址) 信道.
      */
    class AR(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val addr = Output(UInt(xLen.W))
    }

    /**
      * AMBA AXI4-Lite 总线协议的 R (读数据) 信道.
      */
    class R(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)
        
        val data = Input(UInt(xLen.W))
        val resp = Input(UInt(RESP_WIDTH.W))
    }

    /**
      * AMBA AXI4-Lite 总线协议的 AW (写地址) 信道.
      */
    class AW(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val addr = Output(UInt(xLen.W))
    }

    /**
      * AMBA AXI4-Lite 总线协议的 W (写数据) 信道.
      */
    class W(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val strbWidth: Int = xLen / 8

        val data = Output(UInt(xLen.W))
        val strb = Output(UInt(strbWidth.W))
    }

    /**
      * AMBA AXI4-Lite 总线协议的 B (写回复) 信道.
      */
    class B(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)
        
        val resp = Input(UInt(RESP_WIDTH.W))
    }

    def defaultValuesForMaster(bus: AXI4Lite): Unit = {
        bus.ar.valid := false.B
        bus.ar.bits.addr := 0.U
        bus.r.ready := false.B
        bus.aw.valid := false.B
        bus.aw.bits.addr := 0.U
        bus.w.valid := false.B
        bus.w.bits.data := 0.U
        bus.w.bits.strb := 0.U
        bus.b.ready := false.B
    }

    def defaultValuesForSlave(bus: AXI4Lite): Unit = {
        bus.ar.ready := false.B
        bus.r.valid := false.B
        bus.r.bits.data := 0.U
        bus.r.bits.resp := 0.U
        bus.aw.ready := false.B
        bus.w.ready := false.B
        bus.b.valid := false.B
        bus.b.bits.resp := 0.U
    }
}
