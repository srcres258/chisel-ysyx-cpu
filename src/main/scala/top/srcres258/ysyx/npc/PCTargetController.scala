package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * 程序计数器目标地址控制器, 控制程序计数器的下一个地址.
  */
class PCTargetController(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)
    
    val io = IO(new Bundle {
        val cuJumpEnable = Input(Bool())
        val cuJumpType = Input(UInt(ControlUnit.JUMP_TYPE_LEN.W))
        val compBranchEnable = Input(Bool())
        val cuBranchEnable = Input(Bool())
        val pc = Input(UInt(xLen.W))
        val imm = Input(UInt(xLen.W))
        val pcNext = Input(UInt(xLen.W))
        val rs1Data = Input(UInt(xLen.W))
        val epcRecoverEnable = Input(Bool())
        val epcData = Input(UInt(xLen.W))
        val ecallEnable = Input(Bool())
        val tvecData = Input(UInt(xLen.W))

        val pcTarget = Output(UInt(xLen.W))
    })

    io.pcTarget := io.pcNext
    when(io.ecallEnable) {
        io.pcTarget := io.tvecData
    }.elsewhen(io.epcRecoverEnable) {
        io.pcTarget := io.epcData
    }.elsewhen(io.cuJumpEnable) {
        when(io.cuJumpType === ControlUnit.JUMP_TYPE_JAL.U) {
            io.pcTarget := io.pc + io.imm
        }.elsewhen(io.cuJumpType === ControlUnit.JUMP_TYPE_JALR.U) {
            io.pcTarget := io.rs1Data + io.imm
        }
    }.elsewhen(io.compBranchEnable && io.cuBranchEnable) {
        io.pcTarget := io.pc + io.imm
    }
}
