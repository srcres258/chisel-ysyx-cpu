package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ImmediateSignExtend
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.regfile.ControlAndStatusRegisterFile
import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.debug.DebugBundle

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
        val csrReadPort1 = Flipped(new ControlAndStatusRegisterFile.ReadPort(xLen))
        val csrReadPort2 = Flipped(new ControlAndStatusRegisterFile.ReadPort(xLen))
        val csrReadPort3 = Flipped(new ControlAndStatusRegisterFile.ReadPort(xLen))

        val prevStage = Flipped(Decoupled(Output(new IF_ID_Bundle(xLen))))
        val nextStage = Decoupled(Output(new ID_EX_Bundle(xLen)))

        val debug = Output(new IDUnitDebugBundle(xLen))
    })

    val prevStageData = Wire(new IF_ID_Bundle(xLen))
    val nextStageData = Wire(new ID_EX_Bundle(xLen))
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
    io.prevStage.ready := !nextStagePrepared
    io.nextStage.valid := nextStageState === s_nextStage_waitReady
    io.nextStage.bits := nextStageData
    prevStageData := io.prevStage.bits
    when(io.prevStage.valid) {
        nextStagePrepared := true.B
    }
    when(nextStagePrepared && io.nextStage.ready) {
        nextStagePrepared := false.B
    }

    val rs1 = Wire(UInt(5.W))
    val rs2 = Wire(UInt(5.W))
    val rs1Data = Wire(UInt(xLen.W))
    val rs2Data = Wire(UInt(xLen.W))
    val rd = Wire(UInt(5.W))
    val imm = Wire(UInt(xLen.W))
    val csr = Wire(UInt(12.W))
    val csrData = Wire(UInt(xLen.W))
    val zimm = Wire(UInt(xLen.W))
    val epcData = Wire(UInt(xLen.W))
    val tvecData = Wire(UInt(xLen.W))
    
    val regWriteEnable = Wire(Bool())
    val immSel = Wire(UInt(ControlUnit.IMM_SEL_LEN.W))
    val executePortASel = Wire(Bool())
    val executePortBSel = Wire(Bool())
    val aluOpSel = Wire(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
    val compOpSel = Wire(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
    val dataMemWriteEnable = Wire(Bool())
    val dataMemReadEnable = Wire(Bool())
    val regWriteDataSel = Wire(UInt(ControlUnit.RD_MUX_SEL_LEN.W))
    val csrRegWriteDataSel = Wire(UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W))

    val lsType = Wire(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))

    rs1 := prevStageData.inst(19, 15)
    rs2 := prevStageData.inst(24, 20)
    io.gprReadPort.readAddress1 := rs1
    io.gprReadPort.readAddress2 := rs2
    rs1Data := io.gprReadPort.readData1
    rs2Data := io.gprReadPort.readData2
    rd := prevStageData.inst(11, 7)

    csr := prevStageData.inst(31, 20)
    io.csrReadPort1.readAddress := csr
    csrData := io.csrReadPort1.readData

    zimm := rs1.asUInt.pad(xLen)

    io.csrReadPort2.readAddress := ControlAndStatusRegisterFile.CSR_MEPC.U
    epcData := io.csrReadPort2.readData

    io.csrReadPort3.readAddress := ControlAndStatusRegisterFile.CSR_MTVEC.U
    tvecData := io.csrReadPort3.readData

    val ise = Module(new ImmediateSignExtend(xLen))
    ise.io.inst := prevStageData.inst
    ise.io.immSel := immSel
    imm := ise.io.immOut

    val cu = Module(new ControlUnit(xLen))
    cu.io.opCode := prevStageData.inst(6, 0)
    cu.io.funct3 := prevStageData.inst(14, 12)
    cu.io.funct7 := prevStageData.inst(31, 25)
    cu.io.rd := rd
    cu.io.rs1 := rs1
    cu.io.rs2 := rs2

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
    csrRegWriteDataSel := cu.io.csrRegWriteDataSel

    io.debug.rs1 := rs1
    io.debug.rs2 := rs2
    io.debug.rd := rd
    io.debug.imm := imm
    io.debug.rs1Data := rs1Data
    io.debug.rs2Data := rs2Data
    io.debug.inst := prevStageData.inst
    io.debug.inst_jal := cu.io.inst_jal
    io.debug.inst_jalr := cu.io.inst_jalr

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.rs1Data := rs1Data
    nextStageData.rs2Data := rs2Data
    nextStageData.imm := imm
    nextStageData.rd := rd
    nextStageData.rs1 := rs1
    nextStageData.rs2 := rs2
    nextStageData.csr := csr
    nextStageData.csrData := csrData
    nextStageData.zimm := zimm
    nextStageData.epcData := epcData
    nextStageData.tvecData := tvecData
    nextStageData.ecallCause := ControlUnit.MCAUSE_ECALL_FROM_M_MODE.U
    nextStageData.regWriteEnable := regWriteEnable
    nextStageData.csrRegWriteEnable := cu.io.csrRegWriteEnable
    nextStageData.aluPortASel := executePortASel
    nextStageData.aluPortBSel := executePortBSel
    nextStageData.aluOpSel := aluOpSel
    nextStageData.compOpSel := compOpSel
    nextStageData.lsType := lsType
    nextStageData.memWriteEnable := dataMemWriteEnable
    nextStageData.memReadEnable := dataMemReadEnable
    nextStageData.regWriteDataSel := regWriteDataSel
    nextStageData.csrRegWriteDataSel := csrRegWriteDataSel
    nextStageData.cuJumpEnable := cu.io.jumpEnable
    nextStageData.cuJumpType := cu.io.jumpType
    nextStageData.cuBranchEnable := cu.io.branchEnable
    nextStageData.epcRecoverEnable := cu.io.epcRecoverEnable
    nextStageData.ecallEnable := cu.io.ecallEnable
    nextStageData.inst_jal := cu.io.inst_jal
    nextStageData.inst_jalr := cu.io.inst_jalr
}

class IDUnitDebugBundle(val xLen: Int = 32) extends DebugBundle {
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val rd = UInt(5.W)
    val imm = UInt(xLen.W)
    val rs1Data = UInt(xLen.W)
    val rs2Data = UInt(xLen.W)
    val inst = UInt(xLen.W)
    val inst_jal = Bool()
    val inst_jalr = Bool()
}

object IDUnitDebugBundle {
    def apply(xLen: Int = 32): IDUnitDebugBundle = {
        val default = Wire(new IDUnitDebugBundle(xLen))

        default.rs1 := 0.U
        default.rs2 := 0.U
        default.rd := 0.U
        default.imm := 0.U
        default.rs1Data := 0.U
        default.rs2Data := 0.U
        default.inst := 0.U
        default.inst_jal := false.B
        default.inst_jalr := false.B

        default
    }
}
