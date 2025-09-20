package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.ProcessorCore
import os.group.set

/**
  * 从 MA 阶段到 WB 阶段所需流转的数据。
  */
class MA_WB_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    xLen: Int = 32
) extends StageUnitBundle(xLen) {
    val pcCur = UInt(xLen.W)
    val pcNext = UInt(xLen.W)
    val pcTarget = UInt(xLen.W)
    val memReadData = UInt(xLen.W)
    val aluOutput = UInt(xLen.W)
    val compBranchEnable = Bool()
    val rs1Data = UInt(xLen.W)
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val csr = UInt(12.W)
    val csrData = UInt(xLen.W)
    val zimm = UInt(xLen.W)
    val ecallCause = UInt(xLen.W)
    // 控制信号组
    val regWriteEnable = Bool()
    val csrRegWriteEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    val csrRegWriteDataSel = UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W)
    val ecallEnable = Bool()
}

object MA_WB_Bundle {
    def setDefaultValues(bundle: MA_WB_Bundle): Unit = {
        bundle.pcCur := 0.U
        bundle.pcNext := 0.U
        bundle.pcTarget := ProcessorCore.PC_INITIAL_VAL
        bundle.memReadData := 0.U
        bundle.aluOutput := 0.U
        bundle.compBranchEnable := false.B
        bundle.rs1Data := 0.U
        bundle.imm := 0.U
        bundle.rd := 0.U
        bundle.rs1 := 0.U
        bundle.rs2 := 0.U
        bundle.csr := 0.U
        bundle.csrData := 0.U
        bundle.zimm := 0.U
        bundle.ecallCause := 0.U
        bundle.regWriteEnable := false.B
        bundle.csrRegWriteEnable := false.B
        bundle.regWriteDataSel := 0.U
        bundle.csrRegWriteDataSel := 0.U
        bundle.ecallEnable := false.B
    }

    def apply(xLen: Int = 32): MA_WB_Bundle = {
        val default = Wire(new MA_WB_Bundle(xLen))

        setDefaultValues(default)
        
        default
    }
}
