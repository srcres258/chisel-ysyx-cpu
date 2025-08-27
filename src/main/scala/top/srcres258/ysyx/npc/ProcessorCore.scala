package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq

/**
  * RV32I 单周期处理器核心
  */
class ProcessorCore extends Module {
    /**
      * 目前的 IO 方式采用哈佛架构（指令的存取与程序数据的存取分离）
      */
    val io = IO(new Bundle {
        /**
          * 输出：指令地址
          */
        val instAddr = Output(UInt(32.W))
        /**
          * 输入：指令数据
          */
        val instData = Input(UInt(32.W))

        /**
          * 输入：读入程序数据
          */
        val readData = Input(UInt(32.W))
        /**
          * 输出：写出程序数据，写使能
          */
        val writeEnable = Output(Bool())
        /**
          * 输出：写出程序数据
          */
        val writeData = Output(UInt(32.W))
        /**
          * 输出：写出程序数据，字掩码
          */
        val writeDataStrobe = Output(UInt(4.W))
        /**
          * 输出：读入/写出程序数据，地址
          */
        val address = Output(UInt(32.W))
    })

    val ioDPI = IO(new DPIBundle)

    val pc = RegInit(ProcessorCore.PC_INITIAL_VAL)
    val pcNext = Wire(UInt(32.W))
    val pcUpdate = Wire(UInt(32.W))

    val inst = Wire(UInt(32.W))

    val imm = Wire(UInt(32.W))
    val rs1 = Wire(UInt(32.W))
    val rs2 = Wire(UInt(32.W))

    val aluPortA = Wire(UInt(32.W))
    val aluPortB = Wire(UInt(32.W))
    val compPortA = Wire(UInt(32.W))
    val compPortB = Wire(UInt(32.W))

    val aluOutput = Wire(UInt(32.W))
    val branchEnable = Wire(Bool())

    val readDataAligned = Wire(UInt(32.W))
    val writeDataUnaligned = Wire(UInt(32.W))
    val lsType = Wire(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))

    val dmemData = Wire(UInt(32.W))

    val pcMuxSel = Wire(Bool())
    val regWriteEnable = Wire(Bool())
    val immSel = Wire(UInt(ControlUnit.IMM_SEL_LEN.W))
    val executePortASel = Wire(Bool())
    val executePortBSel = Wire(Bool())
    val aluOpSel = Wire(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
    val compOpSel = Wire(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
    val dataMemWriteEnable = Wire(Bool())
    val regWriteDataSel = Wire(UInt(ControlUnit.RD_MUX_SEL_LEN.W))

    val regData = Wire(UInt(32.W))

    /**
      * 处理器是否应当停机 (仅在仿真时使用)
      */
    val halt = Wire(Bool())             // for simulating purpose only at present
    /**
      * 当前指令类型是否为 jal (仅在仿真时使用)
      */
    val inst_jal = Wire(Bool())         // for simulating purpose only at present
    /**
      * 当前指令类型是否为 jalr (仅在仿真时使用)
      */
    val inst_jalr = Wire(Bool())        // for simulating purpose only at present

    pcNext := pc + 4.U(32.W)
    pcUpdate := Mux(pcMuxSel, aluOutput, pcNext)

    pc := pcUpdate

    io.instAddr := pc
    inst := io.instData

    val regFile = Module(new RegisterFile)
    rs1 := regFile.io.readData1
    rs2 := regFile.io.readData2
    regFile.io.writeEnable := regWriteEnable
    regFile.io.writeData := regData
    regFile.io.writeAddress := inst(11, 7)
    regFile.io.readAddress1 := inst(19, 15)
    regFile.io.readAddress2 := inst(24, 20)

    val ise = Module(new ImmediateSignExtend)
    imm := ise.io.immOut
    ise.io.inst := inst
    ise.io.immSel := immSel

    val cu = Module(new ControlUnit)
    pcMuxSel := cu.io.pcMuxSel
    regWriteEnable := cu.io.regWriteEnable
    immSel := cu.io.immSel
    executePortASel := cu.io.executePortASel
    executePortBSel := cu.io.executePortBSel
    aluOpSel := cu.io.aluOpSel
    compOpSel := cu.io.compOpSel
    lsType := cu.io.lsType
    dataMemWriteEnable := cu.io.dataMemWriteEnable
    regWriteDataSel := cu.io.regWriteDataSel
    cu.io.opCode := inst(6, 0)
    cu.io.funct3 := inst(14, 12)
    cu.io.funct7Bit5 := inst(30)
    cu.io.branchEnable := branchEnable

    aluPortA := Mux(executePortASel, rs1, pc)
    aluPortB := Mux(executePortBSel, rs2, imm)
    compPortA := rs1
    compPortB := Mux(executePortBSel, imm, rs2)

    val alu = Module(new ArithmeticLogicUnit)
    aluOutput := alu.io.alu
    alu.io.aluPortA := aluPortA
    alu.io.aluPortB := aluPortB
    alu.io.aluSel := aluOpSel

    val compU = Module(new ComparatorUnit)
    branchEnable := compU.io.comp
    compU.io.compPortA := compPortA
    compU.io.compPortB := compPortB
    compU.io.compOpSel := compOpSel

    val lsu = Module(new LoadAndStoreUnit)
    readDataAligned := lsu.io.readDataOut
    lsu.io.readDataIn := io.readData
    io.writeData := lsu.io.writeDataOut
    lsu.io.writeDataIn := writeDataUnaligned
    lsu.io.lsType := lsType
    io.writeDataStrobe := lsu.io.writeDataStrobe

    io.address := aluOutput
    dmemData := readDataAligned
    io.writeEnable := dataMemWriteEnable
    writeDataUnaligned := rs2

    {
        // default values
        // regData := DontCare
        regData := 0.U(32.W)

        when(regWriteDataSel === ControlUnit.RD_MUX_DMEM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := dmemData
        }
        when(regWriteDataSel === ControlUnit.RD_MUX_ALU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := aluOutput
        }
        when(regWriteDataSel === ControlUnit.RD_MUX_BCU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := Cat(0.U(31.W), branchEnable.asUInt)
        }
        when(regWriteDataSel === ControlUnit.RD_MUX_IMM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := imm
        }
        when(regWriteDataSel === ControlUnit.RD_MUX_PC_N.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := pcNext
        }
    }

    /* 
    RV32I 中，ebreak 指令的机器码是 0x00100073
    目前我们硬编码该指令为处理器仿真结束指令，遇到该指令时将 halt 输出置高电平，
    外部 BlackBox 检测到到该电平上升沿后，自动调用 DPI-C 接口终止仿真。
    */
    halt := inst === "h00100073".U(32.W)

    inst_jal := cu.io.inst_jal
    inst_jalr := cu.io.inst_jalr

    val dpi = Module(new DPIAdapter)
    dpi.io.halt := halt
    dpi.io.address := io.address
    dpi.io.lsType := lsType
    dpi.io.inst_jal := inst_jal
    dpi.io.inst_jalr := inst_jalr

    ioDPI.pc := pc
    for (i <- 0 until ioDPI.registers.length) {
        ioDPI.registers(i) := regFile.io.registers(i)
    }
    ioDPI.lsType := lsType
    ioDPI.inst_jal := inst_jal
    ioDPI.inst_jalr := inst_jalr
    ioDPI.rs1 := inst(19, 15)
    ioDPI.rs2 := inst(24, 20)
    ioDPI.rd := inst(11, 7)
    ioDPI.imm := imm
    ioDPI.rs1Data := rs1
    ioDPI.rs2Data := rs2
}

object ProcessorCore extends App {
    val PC_INITIAL_VAL: UInt = "h80000000".U(32.W)

    val cs = new ChiselStage
    cs.execute(
        Array(
            "--target", "systemverilog",
            "--target-dir", "generated",
            "--split-verilog"
        ),
        Seq(ChiselGeneratorAnnotation(() => new ProcessorCore))
    )
}
