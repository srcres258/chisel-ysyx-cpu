package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.ProcessorCore

/**
  * 从 MA 阶段到 WB 阶段所需流转的数据。
  */
class MA_WB_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Bundle {
    val pcNext = UInt(xLen.W)
    val pcTarget = UInt(xLen.W)
    val memReadData = UInt(xLen.W)
    val aluOutput = UInt(xLen.W)
    val compBranchEnable = Bool()
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    // 控制信号组
    val regWriteEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
}

object MA_WB_Bundle {
    def apply(xLen: Int = 32): MA_WB_Bundle = {
        val default = Wire(new MA_WB_Bundle(xLen))
        default.pcNext := 0.U
        default.pcTarget := ProcessorCore.PC_INITIAL_VAL
        default.memReadData := 0.U
        default.aluOutput := 0.U
        default.compBranchEnable := false.B
        default.imm := 0.U
        default.rd := 0.U
        default.rs1 := 0.U
        default.rs2 := 0.U
        default.regWriteEnable := false.B
        default.regWriteDataSel := 0.U
        default
    }
}
