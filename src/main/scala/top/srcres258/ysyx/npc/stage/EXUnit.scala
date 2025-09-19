package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.PCTargetController
import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.debug.DebugBundle

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
        val prevStage = Flipped(Decoupled(Output(new ID_EX_Bundle(xLen))))
        val nextStage = Decoupled(Output(new EX_MA_Bundle(xLen)))

        val debug = Output(new DebugBundle {
            val ecallEnable = Bool()
        })
    })
    val ioDPI = DPIBundle.defaultIO()

    val prevStageData = RegInit(ID_EX_Bundle(xLen))
    val nextStageData = Wire(new EX_MA_Bundle(xLen))
    val nextStagePrepared = RegInit(false.B)

    val s_prevStage_idle :: s_prevStage_waitReset :: Nil = Enum(2)
    val s_nextStage_idle :: s_nextStage_waitReady :: Nil = Enum(2)

    val prevStageState = RegInit(s_prevStage_idle)
    val nextStageState = RegInit(s_nextStage_idle)
    prevStageState := MuxLookup(prevStageState, s_prevStage_idle)(List(
        s_prevStage_idle -> Mux(io.prevStage.valid, s_prevStage_waitReset, s_prevStage_idle),
        s_prevStage_waitReset -> Mux(!io.prevStage.valid, s_prevStage_idle, s_prevStage_waitReset)
    ))
    nextStageState := MuxLookup(nextStageState, s_nextStage_idle)(List(
        s_nextStage_idle -> Mux(nextStagePrepared, s_nextStage_waitReady, s_nextStage_idle),
        s_nextStage_waitReady -> Mux(io.nextStage.ready, s_nextStage_idle, s_nextStage_waitReady)
    ))
    io.prevStage.ready := prevStageState === s_prevStage_waitReset
    io.nextStage.valid := nextStageState === s_nextStage_waitReady
    io.nextStage.bits := nextStageData
    when(io.prevStage.valid) {
        prevStageData := io.prevStage.bits
        nextStagePrepared := true.B
    }
    when(nextStagePrepared && io.nextStage.ready) {
        nextStagePrepared := false.B
    }

    val aluOutput = Wire(UInt(xLen.W))
    val branchEnable = Wire(Bool())

    val aluPortA = Wire(UInt(xLen.W))
    val aluPortB = Wire(UInt(xLen.W))
    val compPortA = Wire(UInt(xLen.W))
    val compPortB = Wire(UInt(xLen.W))

    aluPortA := Mux(prevStageData.aluPortASel, prevStageData.rs1Data, prevStageData.pcCur)
    aluPortB := Mux(prevStageData.aluPortBSel, prevStageData.rs2Data, prevStageData.imm)
    compPortA := prevStageData.rs1Data
    compPortB := Mux(prevStageData.aluPortBSel, prevStageData.imm, prevStageData.rs2Data)

    val alu = Module(new ArithmeticLogicUnit(xLen))
    aluOutput := alu.io.alu
    alu.io.aluPortA := aluPortA
    alu.io.aluPortB := aluPortB
    alu.io.aluSel := prevStageData.aluOpSel

    val compU = Module(new ComparatorUnit(xLen))
    branchEnable := compU.io.comp
    compU.io.compPortA := compPortA
    compU.io.compPortB := compPortB
    compU.io.compOpSel := prevStageData.compOpSel

    val pcTargetCtrl = Module(new PCTargetController(xLen))
    pcTargetCtrl.io.cuJumpEnable := prevStageData.cuJumpEnable
    pcTargetCtrl.io.cuJumpType := prevStageData.cuJumpType
    pcTargetCtrl.io.compBranchEnable := branchEnable
    pcTargetCtrl.io.cuBranchEnable := prevStageData.cuBranchEnable
    pcTargetCtrl.io.pc := prevStageData.pcCur
    pcTargetCtrl.io.imm := prevStageData.imm
    pcTargetCtrl.io.pcNext := prevStageData.pcNext
    pcTargetCtrl.io.rs1Data := prevStageData.rs1Data
    pcTargetCtrl.io.epcRecoverEnable := prevStageData.epcRecoverEnable
    pcTargetCtrl.io.epcData := prevStageData.epcData
    pcTargetCtrl.io.ecallEnable := prevStageData.ecallEnable
    pcTargetCtrl.io.tvecData := prevStageData.tvecData

    ioDPI.rs1Data := prevStageData.rs1Data
    ioDPI.rs2Data := prevStageData.rs2Data
    ioDPI.ecallEnable := prevStageData.ecallEnable

    io.debug.ecallEnable := prevStageData.ecallEnable

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.pcTarget := pcTargetCtrl.io.pcTarget
    nextStageData.aluOutput := aluOutput
    nextStageData.compBranchEnable := branchEnable
    nextStageData.rs1Data := prevStageData.rs1Data
    nextStageData.storeData := prevStageData.rs2Data
    nextStageData.imm := prevStageData.imm
    nextStageData.rd := prevStageData.rd
    nextStageData.rs1 := prevStageData.rs1
    nextStageData.rs2 := prevStageData.rs2
    nextStageData.csr := prevStageData.csr
    nextStageData.csrData := prevStageData.csrData
    nextStageData.zimm := prevStageData.zimm
    nextStageData.ecallCause := prevStageData.ecallCause
    nextStageData.lsType := prevStageData.lsType
    nextStageData.memReadEnable := prevStageData.memReadEnable
    nextStageData.memWriteEnable := prevStageData.memWriteEnable
    nextStageData.regWriteEnable := prevStageData.regWriteEnable
    nextStageData.csrRegWriteEnable := prevStageData.csrRegWriteEnable
    nextStageData.regWriteDataSel := prevStageData.regWriteDataSel
    nextStageData.csrRegWriteDataSel := prevStageData.csrRegWriteDataSel
    nextStageData.ecallEnable := prevStageData.ecallEnable
    nextStageData.inst_jal := prevStageData.inst_jal
    nextStageData.inst_jalr := prevStageData.inst_jalr
}
