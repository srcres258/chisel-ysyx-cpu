package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ControlUnit

/**
  * 从 ID 阶段到 EX 阶段所需流转的数据。
  */
class ID_EX_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Bundle {
    val pcNext = UInt(xLen.W)
    val rs1Data = UInt(xLen.W)
    val rs2Data = UInt(xLen.W)
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val csr = UInt(12.W)
    val csrData = UInt(xLen.W)
    val zimm = UInt(xLen.W)
    val epcData = UInt(xLen.W)
    val tvecData = UInt(xLen.W)
    val ecallCause = UInt(xLen.W)
    // 控制信号组
    val regWriteEnable = Bool()
    val csrRegWriteEnable = Bool()
    val aluPortASel = Bool()
    val aluPortBSel = Bool()
    val aluOpSel = UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W)
    val compOpSel = UInt(ComparatorUnit.COMP_OP_SEL_LEN.W)
    val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
    val memWriteEnable = Bool()
    val memReadEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    val csrRegWriteDataSel = UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W)
    val cuJumpEnable = Bool()
    val cuJumpType = UInt(ControlUnit.JUMP_TYPE_LEN.W)
    val cuBranchEnable = Bool()
    val epcRecoverEnable = Bool()
    val ecallEnable = Bool()
    // 调试信号 (仅供仿真使用)
    val inst_jal = Bool()
    val inst_jalr = Bool()
}

object ID_EX_Bundle {
    def apply(xLen: Int = 32): ID_EX_Bundle = {
        val default = Wire(new ID_EX_Bundle(xLen))
        default.pcNext := 0.U
        default.rs1Data := 0.U
        default.rs2Data := 0.U
        default.imm := 0.U
        default.rd := 0.U
        default.rs1 := 0.U
        default.rs2 := 0.U
        default.csr := 0.U
        default.csrData := 0.U
        default.zimm := 0.U
        default.epcData := 0.U
        default.tvecData := 0.U
        default.ecallCause := 0.U
        default.regWriteEnable := false.B
        default.csrRegWriteEnable := false.B
        default.aluPortASel := false.B
        default.aluPortBSel := false.B
        default.aluOpSel := 0.U
        default.compOpSel := 0.U
        default.lsType := 0.U
        default.memWriteEnable := false.B
        default.memReadEnable := false.B
        default.regWriteDataSel := 0.U
        default.csrRegWriteDataSel := 0.U
        default.cuJumpEnable := false.B
        default.cuJumpType := ControlUnit.JUMP_TYPE_JAL.U
        default.cuBranchEnable := false.B
        default.epcRecoverEnable := false.B
        default.ecallEnable := false.B
        default.inst_jal := false.B
        default.inst_jalr := false.B
        default
    }
}
