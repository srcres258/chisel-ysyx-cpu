package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 从 EX 阶段到 MEM 阶段所需流转的数据.
  */
class EX_MEM_Bundle(xLen: Int) extends StageUnitBundle(xLen) {
    Assertion.assertProcessorXLen(xLen)

    val pcCur = UInt(xLen.W)
    val inst = UInt(xLen.W)
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
    val csr = UInt(12.W)
    val csrData = UInt(xLen.W)
    // 控制信号组
    val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
    val memReadEnable = Bool()
    val memWriteEnable = Bool()
    val regWriteEnable = Bool()
    val csrRegWriteEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    val csrRegWriteDataSel = UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W)
    val ecallEnable = Bool()
}

object EX_MEM_Bundle {
    def apply(xLen: Int): EX_MEM_Bundle = {
        Assertion.assertProcessorXLen(xLen)

        val default = WireDefault(0.U.asTypeOf(new EX_MEM_Bundle(xLen)))
        default.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
        default
    }
}
