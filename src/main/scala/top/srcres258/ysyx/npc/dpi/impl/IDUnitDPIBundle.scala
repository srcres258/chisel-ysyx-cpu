package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class IDUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val rs1 = Output(UInt(5.W))
    val rs2 = Output(UInt(5.W))
    val rd = Output(UInt(5.W))
    val imm = Output(UInt(xLen.W))
    val rs1Data = Output(UInt(xLen.W))
    val rs2Data = Output(UInt(xLen.W))
    val inst = Output(UInt(xLen.W))
    val inst_jal = Output(Bool())
    val inst_jalr = Output(Bool())

    /**
      * 输出: 处理器 ID 阶段传给下一阶段的信息的 valid 信号.
      */
    val id_nextStage_valid = Output(Bool())
}
