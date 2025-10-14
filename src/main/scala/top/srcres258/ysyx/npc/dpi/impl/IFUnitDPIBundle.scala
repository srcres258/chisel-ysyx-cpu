package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class IFUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    /**
      * 输出: 处理器 IF 阶段传给下一阶段的信息的 valid 信号.
      */
    val if_nextStage_valid = Output(Bool())
    /**
      * 输出: 处理器当前 PC 值处的指令内容.
      */
    val instData = Output(UInt(xLen.W))
}
