package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class MEMUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val memWriteEnable = Output(Bool())
    val memReadEnable = Output(Bool())

    /**
      * 输出: 处理器 MEM 阶段传给下一阶段的信息的 valid 信号.
      */
    val mem_nextStage_valid = Output(Bool())
}
