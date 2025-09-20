package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ControlUnit
import os.group.set

/**
  * 从 ID 阶段到 EX 阶段所需流转的数据。
  */
class ID_EX_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    xLen: Int = 32
) extends StageUnitBundle(xLen) {
    val pcCur = UInt(xLen.W)
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
    private def setDefaultValues(bundle: ID_EX_Bundle): Unit = {
        bundle.pcCur := 0.U
        bundle.pcNext := 0.U
        bundle.rs1Data := 0.U
        bundle.rs2Data := 0.U
        bundle.imm := 0.U
        bundle.rd := 0.U
        bundle.rs1 := 0.U
        bundle.rs2 := 0.U
        bundle.csr := 0.U
        bundle.csrData := 0.U
        bundle.zimm := 0.U
        bundle.epcData := 0.U
        bundle.tvecData := 0.U
        bundle.ecallCause := 0.U
        bundle.regWriteEnable := false.B
        bundle.csrRegWriteEnable := false.B
        bundle.aluPortASel := false.B
        bundle.aluPortBSel := false.B
        bundle.aluOpSel := 0.U
        bundle.compOpSel := 0.U
        bundle.lsType := 0.U
        bundle.memWriteEnable := false.B
        bundle.memReadEnable := false.B
        bundle.regWriteDataSel := 0.U
        bundle.csrRegWriteDataSel := 0.U
        bundle.cuJumpEnable := false.B
        bundle.cuJumpType := ControlUnit.JUMP_TYPE_JAL.U
        bundle.cuBranchEnable := false.B
        bundle.epcRecoverEnable := false.B
        bundle.ecallEnable := false.B
        bundle.inst_jal := false.B
        bundle.inst_jalr := false.B
    }

    def apply(xLen: Int = 32): ID_EX_Bundle = {
        val default = Wire(new ID_EX_Bundle(xLen))

        setDefaultValues(default)
        
        default
    }
}
