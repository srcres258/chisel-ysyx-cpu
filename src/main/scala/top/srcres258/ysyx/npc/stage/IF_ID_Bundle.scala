package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

/**
  * 从 IF 阶段到 ID 阶段所需流转的数据。
  */
class IF_ID_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    xLen: Int = 32
) extends StageUnitBundle(xLen) {
    val pcCur = UInt(xLen.W)
    val pcNext = UInt(xLen.W)
    val inst = UInt(xLen.W)
}

object IF_ID_Bundle {
    def apply(xLen: Int = 32): IF_ID_Bundle = {
        val default = Wire(new IF_ID_Bundle(xLen))

        default.pcCur := 0.U
        default.pcNext := 0.U
        default.inst := 0.U
        
        default
    }
}
