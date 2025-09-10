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
        val pc = Input(UInt(xLen.W))
        val gprWritePort = Flipped(new GeneralPurposeRegisterFile.WritePort(xLen))
        val csrWritePort1 = Flipped(new ControlAndStatusRegisterFile.WritePort(xLen))
        val csrWritePort2 = Flipped(new ControlAndStatusRegisterFile.WritePort(xLen))

        val prevStage = Input(new MA_WB_Bundle(xLen))
        val nextStage = Output(new WB_UPC_Bundle(xLen))
    })

    val gprData = Wire(UInt(xLen.W))
    val csrData = Wire(UInt(xLen.W))

    gprData := 0.U
    when(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_DMEM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := io.prevStage.memReadData
    }.elsewhen(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_ALU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := io.prevStage.aluOutput
    }.elsewhen(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_BCU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := Cat(0.U(31.W), io.prevStage.compBranchEnable.asUInt)
    }.elsewhen(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_IMM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := io.prevStage.imm
    }.elsewhen(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_PC_N.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := io.prevStage.pcNext
    }.elsewhen(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_CSR_DATA.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := io.prevStage.csrData
    }

    csrData := 0.U
    when(io.prevStage.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_W.U) {
        csrData := io.prevStage.rs1Data
    }.elsewhen(io.prevStage.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_S.U) {
        csrData := io.prevStage.csrData | io.prevStage.rs1Data
    }.elsewhen(io.prevStage.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_C.U) {
        csrData := io.prevStage.csrData & (~io.prevStage.rs1Data)
    }.elsewhen(io.prevStage.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_W_IMM.U) {
        csrData := io.prevStage.zimm
    }.elsewhen(io.prevStage.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_S_IMM.U) {
        csrData := io.prevStage.csrData | io.prevStage.zimm
    }.elsewhen(io.prevStage.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_C_IMM.U) {
        csrData := io.prevStage.csrData & (~io.prevStage.zimm)
    }

    io.gprWritePort.writeEnable := io.prevStage.regWriteEnable
    io.gprWritePort.writeData := gprData
    io.gprWritePort.writeAddress := io.prevStage.rd

    io.csrWritePort1.writeEnable := io.prevStage.csrRegWriteEnable
    io.csrWritePort1.writeData := csrData
    io.csrWritePort1.writeAddress := io.prevStage.csr
    io.csrWritePort2.writeEnable := false.B
    io.csrWritePort2.writeData := 0.U
    io.csrWritePort2.writeAddress := 0.U
    when(io.prevStage.ecallEnable) {
        // 环境调用 ecall

        // 1. 先要将当前 pc 写入 mepc 寄存器
        io.csrWritePort1.writeEnable := true.B
        io.csrWritePort1.writeData := io.pc
        io.csrWritePort1.writeAddress := ControlAndStatusRegisterFile.CSR_MEPC.U

        // 2. 再将原因写入 mcause 寄存器
        io.csrWritePort2.writeEnable := true.B
        io.csrWritePort2.writeData := io.prevStage.ecallCause
        io.csrWritePort2.writeAddress := ControlAndStatusRegisterFile.CSR_MCAUSE.U
    }

    io.nextStage.pcTarget := io.prevStage.pcTarget
}
