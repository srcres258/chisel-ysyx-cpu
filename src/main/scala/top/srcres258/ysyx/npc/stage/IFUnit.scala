package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

/**
  * 处理器的取指 (Instruction Fetch) 单元。
  */
class IFUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val pc = Input(UInt(xLen.W))
        val instData = Input(UInt(xLen.W))

        val nextStage = Output(new IF_ID_Bundle(xLen))
    });

    io.nextStage.pcNext := io.pc + 4.U(xLen.W)
    io.nextStage.inst := io.instData
}
