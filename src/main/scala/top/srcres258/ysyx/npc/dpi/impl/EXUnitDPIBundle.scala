package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class EXUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)
    
    /**
      * 输出：是否发生环境调用.
      */
    val ecallEnable = Output(Bool())

    /**
      * 输出: 处理器 EX 阶段传给下一阶段的信息的 valid 信号.
      */
    val ex_nextStage_valid = Output(Bool())
}
