package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class MAUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val memWriteEnable = Output(Bool())
    val memReadEnable = Output(Bool())

    /**
      * 输出: 处理器 MA 阶段传给下一阶段的信息的 valid 信号.
      */
    val ma_nextStage_valid = Output(Bool())
}
