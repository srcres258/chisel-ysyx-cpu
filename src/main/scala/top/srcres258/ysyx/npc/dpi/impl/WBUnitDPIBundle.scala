package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class WBUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    /**
      * 输出: 处理器 WB 阶段退休指令的 PC.
      */
    val pc = Output(UInt(xLen.W))

    /**
      * 输出: 处理器 WB 阶段退休指令的下一条 PC.
      */
    val pcNext = Output(UInt(xLen.W))

    /**
      * 输出: 处理器 WB 阶段退休指令的原始指令字.
      */
    val inst = Output(UInt(xLen.W))

    /**
      * 输出: 处理器 WB 阶段退休指令的 rs1 编号.
      */
    val rs1 = Output(UInt(5.W))

    /**
      * 输出: 处理器 WB 阶段退休指令的 rd 编号.
      */
    val rd = Output(UInt(5.W))

    /**
      * 输出: 处理器 WB 阶段退休指令的立即数.
      */
    val imm = Output(UInt(xLen.W))

    /**
      * 输出: 处理器 WB 阶段退休指令的 rs1 数据.
      */
    val rs1Data = Output(UInt(xLen.W))

    /**
      * 输出: 退休指令是否是 jal.
      */
    val inst_jal = Output(Bool())

    /**
      * 输出: 退休指令是否是 jalr.
      */
    val inst_jalr = Output(Bool())

    /**
      * 输出: 处理器 WB 阶段传给下一阶段的信息的 valid 信号.
      */
    val wb_nextStage_valid = Output(Bool())
}
