package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * 从 WB 阶段到 UPC 阶段所需流转的数据.
  */
class WB_UPC_Bundle(xLen: Int) extends StageUnitBundle(xLen) {
    Assertion.assertProcessorXLen(xLen)

    val pcCur = UInt(xLen.W)
    val pcTarget = UInt(xLen.W)
}

object WB_UPC_Bundle {
    def setDefaultValues(bundle: WB_UPC_Bundle): Unit = {
        bundle.pcCur := 0.U
        bundle.pcTarget := 0.U
    }

    def apply(xLen: Int): WB_UPC_Bundle = {
        Assertion.assertProcessorXLen(xLen)

        val default = Wire(new WB_UPC_Bundle(xLen))

        setDefaultValues(default)
        
        default
    }
}
