package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.PCTargetController
import top.srcres258.ysyx.npc.dpi.DPIBundle

/**
  * 处理器的执行 (Execute) 单元。
  */
class EXUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val pc = Input(UInt(xLen.W))

        val prevStage = Input(new ID_EX_Bundle(xLen))
        val nextStage = Output(new EX_MA_Bundle(xLen))
    })
    val ioDPI = DPIBundle.defaultIO()

    val aluOutput = Wire(UInt(xLen.W))
    val branchEnable = Wire(Bool())

    val aluPortA = Wire(UInt(xLen.W))
    val aluPortB = Wire(UInt(xLen.W))
    val compPortA = Wire(UInt(xLen.W))
    val compPortB = Wire(UInt(xLen.W))

    aluPortA := Mux(io.prevStage.aluPortASel, io.prevStage.rs1Data, io.pc)
    aluPortB := Mux(io.prevStage.aluPortBSel, io.prevStage.rs2Data, io.prevStage.imm)
    compPortA := io.prevStage.rs1Data
    compPortB := Mux(io.prevStage.aluPortBSel, io.prevStage.imm, io.prevStage.rs2Data)

    val alu = Module(new ArithmeticLogicUnit(xLen))
    aluOutput := alu.io.alu
    alu.io.aluPortA := aluPortA
    alu.io.aluPortB := aluPortB
    alu.io.aluSel := io.prevStage.aluOpSel

    val compU = Module(new ComparatorUnit(xLen))
    branchEnable := compU.io.comp
    compU.io.compPortA := compPortA
    compU.io.compPortB := compPortB
    compU.io.compOpSel := io.prevStage.compOpSel

    val pcTargetCtrl = Module(new PCTargetController(xLen))
    pcTargetCtrl.io.cuJumpEnable := io.prevStage.cuJumpEnable
    pcTargetCtrl.io.cuJumpType := io.prevStage.cuJumpType
    pcTargetCtrl.io.compBranchEnable := branchEnable
    pcTargetCtrl.io.cuBranchEnable := io.prevStage.cuBranchEnable
    pcTargetCtrl.io.pc := io.pc
    pcTargetCtrl.io.imm := io.prevStage.imm
    pcTargetCtrl.io.pcNext := io.prevStage.pcNext
    pcTargetCtrl.io.rs1Data := io.prevStage.rs1Data

    ioDPI.rs1Data := io.prevStage.rs1Data
    ioDPI.rs2Data := io.prevStage.rs2Data

    io.nextStage.pcNext := io.prevStage.pcNext
    io.nextStage.pcTarget := pcTargetCtrl.io.pcTarget
    io.nextStage.aluOutput := aluOutput
    io.nextStage.compBranchEnable := branchEnable
    io.nextStage.storeData := io.prevStage.rs2Data
    io.nextStage.imm := io.prevStage.imm
    io.nextStage.rd := io.prevStage.rd
    io.nextStage.rs1 := io.prevStage.rs1
    io.nextStage.rs2 := io.prevStage.rs2
    io.nextStage.lsType := io.prevStage.lsType
    io.nextStage.memReadEnable := io.prevStage.memReadEnable
    io.nextStage.memWriteEnable := io.prevStage.memWriteEnable
    io.nextStage.regWriteEnable := io.prevStage.regWriteEnable
    io.nextStage.regWriteDataSel := io.prevStage.regWriteDataSel
    io.nextStage.inst_jal := io.prevStage.inst_jal
    io.nextStage.inst_jalr := io.prevStage.inst_jalr
}
