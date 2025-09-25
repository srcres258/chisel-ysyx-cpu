package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 从 EX 阶段到 MA 阶段所需流转的数据.
  */
class EX_MA_Bundle(xLen: Int) extends StageUnitBundle(xLen) {
    Assertion.assertProcessorXLen(xLen)

    val pcCur = UInt(xLen.W)
    val pcNext = UInt(xLen.W)
    val pcTarget = UInt(xLen.W)
    // 注: 由于分支目标地址本身也经 ALU 计算, 所以当分支启用时,
    // aluOutput 中存的就是分支目标地址.
    val aluOutput = UInt(xLen.W)
    val compBranchEnable = Bool()
    val rs1Data = UInt(xLen.W) // rs1Data 需继续传递至 WB 阶段 (CSR 写入操作需要用到)
    val storeData = UInt(xLen.W) // 来自 ID 阶段的 rs2Data
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val csr = UInt(12.W)
    val csrData = UInt(xLen.W)
    val zimm = UInt(xLen.W)
    val ecallCause = UInt(xLen.W)
    // 控制信号组
    val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
    val memReadEnable = Bool()
    val memWriteEnable = Bool()
    val regWriteEnable = Bool()
    val csrRegWriteEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    val csrRegWriteDataSel = UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W)
    val ecallEnable = Bool()
    // 调试信号 (仅供仿真使用)
    val inst_jal = Bool()
    val inst_jalr = Bool()
}

object EX_MA_Bundle {
    def setDefaultValues(bundle: EX_MA_Bundle): Unit = {
        bundle.pcCur := 0.U
        bundle.pcNext := 0.U
        bundle.pcTarget := 0.U
        bundle.aluOutput := 0.U
        bundle.compBranchEnable := false.B
        bundle.rs1Data := 0.U
        bundle.storeData := 0.U
        bundle.imm := 0.U
        bundle.rd := 0.U
        bundle.rs1 := 0.U
        bundle.rs2 := 0.U
        bundle.csr := 0.U
        bundle.csrData := 0.U
        bundle.zimm := 0.U
        bundle.ecallCause := 0.U
        bundle.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
        bundle.memReadEnable := false.B
        bundle.memWriteEnable := false.B
        bundle.regWriteEnable := false.B
        bundle.csrRegWriteEnable := false.B
        bundle.regWriteDataSel := 0.U
        bundle.csrRegWriteDataSel := 0.U
        bundle.ecallEnable := false.B
        bundle.inst_jal := false.B
        bundle.inst_jalr := false.B
    }

    def apply(xLen: Int): EX_MA_Bundle = {
        Assertion.assertProcessorXLen(xLen)

        val default = Wire(new EX_MA_Bundle(xLen))

        setDefaultValues(default)
        
        default
    }
}
