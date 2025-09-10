package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

/**
  * 从 WB 阶段到 UPC 阶段所需流转的数据。
  */
class WB_UPC_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Bundle {
    val pcTarget = UInt(xLen.W)
}

object WB_UPC_Bundle {
    def apply(xLen: Int = 32): WB_UPC_Bundle = {
        val default = Wire(new WB_UPC_Bundle(xLen))
        default.pcTarget := 0.U
        default
    }
}
