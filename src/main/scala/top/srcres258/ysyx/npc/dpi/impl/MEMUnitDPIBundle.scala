package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class MEMUnitDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val memWriteEnable = Output(Bool())
    val memReadEnable = Output(Bool())
    val memAddr = Output(UInt(xLen.W))
    val memData = Output(UInt(xLen.W))
    val memStrobe = Output(UInt((xLen / 8).W))
    val memResp = Output(UInt(2.W))
    val memLsType = Output(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
    val memPc = Output(UInt(xLen.W))

    /**
      * 输出: 处理器 MEM 阶段传给下一阶段的信息的 valid 信号.
      */
    val mem_nextStage_valid = Output(Bool())
}
