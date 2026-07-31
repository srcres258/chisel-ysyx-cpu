package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 从 ID 阶段到 EX 阶段所需流转的数据.
  */
class ID_EX_Bundle(xLen: Int) extends StageUnitBundle(xLen) {
    Assertion.assertProcessorXLen(xLen)

    val pcCur = UInt(xLen.W)
    val pcNext = UInt(xLen.W)
    val inst = UInt(xLen.W)
    val rs1Data = UInt(xLen.W)
    val rs2Data = UInt(xLen.W)
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val csr = UInt(12.W)
    val csrData = UInt(xLen.W)
    val epcData = UInt(xLen.W)
    val tvecData = UInt(xLen.W)
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
}

object ID_EX_Bundle {
    def apply(xLen: Int): ID_EX_Bundle = {
        Assertion.assertProcessorXLen(xLen)

        val default = WireDefault(0.U.asTypeOf(new ID_EX_Bundle(xLen)))
        default.cuJumpType := ControlUnit.JUMP_TYPE_JAL.U
        default
    }
}
