package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class IFUnitDPIBundle(val xLen: Int = 32) extends DPIBundle {
    /**
      * 输出：处理器 IF 阶段传给下一阶段的信息的 valid 信号。
      */
    val if_nextStage_valid = Output(Bool())
}
