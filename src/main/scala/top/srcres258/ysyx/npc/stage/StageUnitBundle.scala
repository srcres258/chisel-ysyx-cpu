package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

class StageUnitBundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Bundle {
}
