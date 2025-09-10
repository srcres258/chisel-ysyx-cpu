package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ControlUnit

/**
  * 从 EX 阶段到 MA 阶段所需流转的数据。
  */
class EX_MA_Bundle(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Bundle {
    val pcNext = UInt(xLen.W)
    val pcTarget = UInt(xLen.W)
    // 注：由于分支目标地址本身也经 ALU 计算，所以当分支启用时，
    // aluOutput 中存的就是分支目标地址。
    val aluOutput = UInt(xLen.W)
    val compBranchEnable = Bool()
    val storeData = UInt(xLen.W) // 来自 ID 阶段的 rs2Data
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    // 控制信号组
    val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
    val memReadEnable = Bool()
    val memWriteEnable = Bool()
    val regWriteEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    // 调试信号 (仅供仿真使用)
    val inst_jal = Bool()
    val inst_jalr = Bool()
}

object EX_MA_Bundle {
    def apply(xLen: Int = 32): EX_MA_Bundle = {
        val default = Wire(new EX_MA_Bundle(xLen))
        default.pcNext := 0.U
        default.pcTarget := 0.U
        default.aluOutput := 0.U
        default.compBranchEnable := false.B
        default.storeData := 0.U
        default.imm := 0.U
        default.rd := 0.U
        default.rs1 := 0.U
        default.rs2 := 0.U
        default.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
        default.memReadEnable := false.B
        default.memWriteEnable := false.B
        default.regWriteEnable := false.B
        default.regWriteDataSel := 0.U
        default.inst_jal := false.B
        default.inst_jalr := false.B
        default
    }
}
