package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * 从 IF 阶段到 ID 阶段所需流转的数据.
  */
class IF_ID_Bundle(xLen: Int) extends StageUnitBundle(xLen) {
    Assertion.assertProcessorXLen(xLen)

    val pcCur = UInt(xLen.W)
    val pcNext = UInt(xLen.W)
    val inst = UInt(xLen.W)
}

object IF_ID_Bundle {
    private def setDefaultValues(bundle: IF_ID_Bundle): Unit = {
        bundle.pcCur := 0.U
        bundle.pcNext := 0.U
        bundle.inst := 0.U
    }

    def apply(xLen: Int): IF_ID_Bundle = {
        Assertion.assertProcessorXLen(xLen)

        val default = Wire(new IF_ID_Bundle(xLen))

        setDefaultValues(default)
        
        default
    }
}
