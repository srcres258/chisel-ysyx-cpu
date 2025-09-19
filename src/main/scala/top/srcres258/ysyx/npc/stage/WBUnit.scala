package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.regfile.ControlAndStatusRegisterFile

/**
  * 处理器的写回 (Write Back) 单元。
  */
class WBUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val gprWritePort = Flipped(new GeneralPurposeRegisterFile.WritePort(xLen))
        val csrWritePort1 = Flipped(new ControlAndStatusRegisterFile.WritePort(xLen))
        val csrWritePort2 = Flipped(new ControlAndStatusRegisterFile.WritePort(xLen))

        val prevStage = Flipped(Decoupled(Output(new MA_WB_Bundle(xLen))))
        val nextStage = Decoupled(Output(new WB_UPC_Bundle(xLen)))
    })

    val prevStageData = RegInit(MA_WB_Bundle(xLen))
    val nextStageData = Wire(new WB_UPC_Bundle(xLen))
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

    val gprData = Wire(UInt(xLen.W))
    val csrData = Wire(UInt(xLen.W))

    gprData := 0.U
    when(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_DMEM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.memReadData
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_ALU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.aluOutput
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_BCU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := Cat(0.U(31.W), prevStageData.compBranchEnable.asUInt)
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_IMM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.imm
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_PC_N.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.pcNext
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_CSR_DATA.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.csrData
    }

    csrData := 0.U
    when(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_W.U) {
        csrData := prevStageData.rs1Data
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_S.U) {
        csrData := prevStageData.csrData | prevStageData.rs1Data
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_C.U) {
        csrData := prevStageData.csrData & (~prevStageData.rs1Data)
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_W_IMM.U) {
        csrData := prevStageData.zimm
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_S_IMM.U) {
        csrData := prevStageData.csrData | prevStageData.zimm
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_C_IMM.U) {
        csrData := prevStageData.csrData & (~prevStageData.zimm)
    }

    io.gprWritePort.writeEnable := prevStageData.regWriteEnable
    io.gprWritePort.writeData := gprData
    io.gprWritePort.writeAddress := prevStageData.rd

    io.csrWritePort1.writeEnable := prevStageData.csrRegWriteEnable
    io.csrWritePort1.writeData := csrData
    io.csrWritePort1.writeAddress := prevStageData.csr
    io.csrWritePort2.writeEnable := false.B
    io.csrWritePort2.writeData := 0.U
    io.csrWritePort2.writeAddress := 0.U
    when(prevStageData.ecallEnable) {
        // 环境调用 ecall

        // 1. 先要将当前 pc 写入 mepc 寄存器
        io.csrWritePort1.writeEnable := true.B
        io.csrWritePort1.writeData := prevStageData.pcCur
        io.csrWritePort1.writeAddress := ControlAndStatusRegisterFile.CSR_MEPC.U

        // 2. 再将原因写入 mcause 寄存器
        io.csrWritePort2.writeEnable := true.B
        io.csrWritePort2.writeData := prevStageData.ecallCause
        io.csrWritePort2.writeAddress := ControlAndStatusRegisterFile.CSR_MCAUSE.U
    }

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcTarget := prevStageData.pcTarget
}
