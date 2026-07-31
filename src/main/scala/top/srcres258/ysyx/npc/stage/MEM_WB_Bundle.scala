package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.Config

/**
  * 从 MEM 阶段到 WB 阶段所需流转的数据.
  */
class MEM_WB_Bundle(xLen: Int) extends StageUnitBundle(xLen) {
    Assertion.assertProcessorXLen(xLen)

    val pcCur = UInt(xLen.W)
    val inst = UInt(xLen.W)
    val pcTarget = UInt(xLen.W)
    val memReadData = UInt(xLen.W)
    val aluOutput = UInt(xLen.W)
    val compBranchEnable = Bool()
    val rs1Data = UInt(xLen.W)
    val imm = UInt(xLen.W)
    val rd = UInt(5.W)
    val rs1 = UInt(5.W)
    val csr = UInt(12.W)
    val csrData = UInt(xLen.W)
    // 控制信号组
    val regWriteEnable = Bool()
    val csrRegWriteEnable = Bool()
    val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    val csrRegWriteDataSel = UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W)
    val ecallEnable = Bool()
}

object MEM_WB_Bundle {
    def apply(xLen: Int): MEM_WB_Bundle = {
        Assertion.assertProcessorXLen(xLen)

        val default = WireDefault(0.U.asTypeOf(new MEM_WB_Bundle(xLen)))
        default.pcTarget := Config.pcInitialVal.U
        default
    }
}
