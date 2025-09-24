package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class EXUnitDPIBundle(val xLen: Int = 32) extends DPIBundle {
    val ecallEnable = Output(Bool())

    /**
      * 输出：处理器 EX 阶段传给下一阶段的信息的 valid 信号。
      */
    val ex_nextStage_valid = Output(Bool())
}
