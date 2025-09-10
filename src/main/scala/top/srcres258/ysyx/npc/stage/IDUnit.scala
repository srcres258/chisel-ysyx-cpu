package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ImmediateSignExtend
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.dpi.DPIBundle

/**
  * 处理器的译码 (Instruction Decode) 单元。
  */
class IDUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val gprReadPort = Flipped(new GeneralPurposeRegisterFile.ReadPort(xLen))

        val prevStage = Input(new IF_ID_Bundle(xLen))
        val nextStage = Output(new ID_EX_Bundle(xLen))
    })
    val ioDPI = DPIBundle.defaultIO()

    val rs1 = Wire(UInt(5.W))
    val rs2 = Wire(UInt(5.W))
    val rs1Data = Wire(UInt(xLen.W))
    val rs2Data = Wire(UInt(xLen.W))
    val rd = Wire(UInt(5.W))
    val imm = Wire(UInt(xLen.W))
    
    val regWriteEnable = Wire(Bool())
    val immSel = Wire(UInt(ControlUnit.IMM_SEL_LEN.W))
    val executePortASel = Wire(Bool())
    val executePortBSel = Wire(Bool())
    val aluOpSel = Wire(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
    val compOpSel = Wire(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
    val dataMemWriteEnable = Wire(Bool())
    val dataMemReadEnable = Wire(Bool())
    val regWriteDataSel = Wire(UInt(ControlUnit.RD_MUX_SEL_LEN.W))

    val lsType = Wire(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))

    rs1 := io.prevStage.inst(19, 15)
    rs2 := io.prevStage.inst(24, 20)
    io.gprReadPort.readAddress1 := rs1
    io.gprReadPort.readAddress2 := rs2
    rs1Data := io.gprReadPort.readData1
    rs2Data := io.gprReadPort.readData2
    rd := io.prevStage.inst(11, 7)

    val ise = Module(new ImmediateSignExtend(xLen))
    ise.io.inst := io.prevStage.inst
    ise.io.immSel := immSel
    imm := ise.io.immOut

    val cu = Module(new ControlUnit(xLen))
    cu.io.opCode := io.prevStage.inst(6, 0)
    cu.io.funct3 := io.prevStage.inst(14, 12)
    cu.io.funct7Bit5 := io.prevStage.inst(30)

    regWriteEnable := cu.io.regWriteEnable
    immSel := cu.io.immSel
    executePortASel := cu.io.executePortASel
    executePortBSel := cu.io.executePortBSel
    aluOpSel := cu.io.aluOpSel
    compOpSel := cu.io.compOpSel
    lsType := cu.io.lsType
    dataMemWriteEnable := cu.io.dataMemWriteEnable
    dataMemReadEnable := cu.io.dataMemReadEnable
    regWriteDataSel := cu.io.regWriteDataSel

    ioDPI.rs1 := rs1
    ioDPI.rs2 := rs2
    ioDPI.rd := rd
    ioDPI.imm := imm

    io.nextStage.pcNext := io.prevStage.pcNext
    io.nextStage.rs1Data := rs1Data
    io.nextStage.rs2Data := rs2Data
    io.nextStage.imm := imm
    io.nextStage.rd := rd
    io.nextStage.rs1 := rs1
    io.nextStage.rs2 := rs2
    io.nextStage.regWriteEnable := regWriteEnable
    io.nextStage.aluPortASel := executePortASel
    io.nextStage.aluPortBSel := executePortBSel
    io.nextStage.aluOpSel := aluOpSel
    io.nextStage.compOpSel := compOpSel
    io.nextStage.lsType := lsType
    io.nextStage.memWriteEnable := dataMemWriteEnable
    io.nextStage.memReadEnable := dataMemReadEnable
    io.nextStage.regWriteDataSel := regWriteDataSel
    io.nextStage.cuJumpEnable := cu.io.jumpEnable
    io.nextStage.cuJumpType := cu.io.jumpType
    io.nextStage.cuBranchEnable := cu.io.branchEnable
    io.nextStage.inst_jal := cu.io.inst_jal
    io.nextStage.inst_jalr := cu.io.inst_jalr
}
