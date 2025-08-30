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
      * 从 IF 阶段到 ID 阶段所需流转的数据。
      */
    class IF_ID_Bundle extends Bundle {
        val pcNext = UInt(32.W)
        val inst = UInt(32.W)
    }
    object IF_ID_Bundle {
        def apply(): IF_ID_Bundle = {
            val default = Wire(new IF_ID_Bundle)

            default.pcNext := 0.U
            default.inst := 0.U

            default
        }
    }

    /**
      * 从 ID 阶段到 EX 阶段所需流转的数据。
      */
    class ID_EX_Bundle extends Bundle {
        val pcNext = UInt(32.W)
        val rs1Data = UInt(32.W)
        val rs2Data = UInt(32.W)
        val imm = UInt(32.W)
        val rd = UInt(5.W)
        val rs1 = UInt(5.W)
        val rs2 = UInt(5.W)

        // 控制信号组
        val regWriteEnable = Bool()
        val aluPortASel = Bool()
        val aluPortBSel = Bool()
        val aluOpSel = UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W)
        val compOpSel = UInt(ComparatorUnit.COMP_OP_SEL_LEN.W)
        val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
        val memWriteEnable = Bool()
        val memReadEnable = Bool()
        val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
        val cuJumpEnable = Bool()
        val cuJumpType = UInt(ControlUnit.JUMP_TYPE_LEN.W)
        val cuBranchEnable = Bool()

        // 调试信号 (仅供仿真使用)
        val inst_jal = Bool()
        val inst_jalr = Bool()
    }
    object ID_EX_Bundle {
        def apply(): ID_EX_Bundle = {
            val default = Wire(new ID_EX_Bundle)

            default.pcNext := 0.U
            default.rs1Data := 0.U
            default.rs2Data := 0.U
            default.imm := 0.U
            default.rd := 0.U
            default.rs1 := 0.U
            default.rs2 := 0.U
            default.regWriteEnable := false.B
            default.aluPortASel := false.B
            default.aluPortBSel := false.B
            default.aluOpSel := 0.U
            default.compOpSel := 0.U
            default.lsType := 0.U
            default.memWriteEnable := false.B
            default.memReadEnable := false.B
            default.regWriteDataSel := 0.U
            default.cuJumpEnable := false.B
            default.cuJumpType := ControlUnit.JUMP_TYPE_JAL.U
            default.cuBranchEnable := false.B
            default.inst_jal := false.B
            default.inst_jalr := false.B

            default
        }
    }

    /**
      * 从 EX 阶段到 MA 阶段所需流转的数据。
      */
    class EX_MA_Bundle extends Bundle {
        val pcNext = UInt(32.W)
        val pcTarget = UInt(32.W)
        // 注：由于分支目标地址本身也经 ALU 计算，所以当分支启用时，
        // aluOutput 中存的就是分支目标地址。
        val aluOutput = UInt(32.W)
        val compBranchEnable = Bool()
        val storeData = UInt(32.W) // 来自 ID 阶段的 rs2Data
        val rd = UInt(5.W)

        // 控制信号组
        val lsType = UInt(LoadAndStoreUnit.LS_TYPE_LEN.W)
        val memReadEnable = Bool()
        val memWriteEnable = Bool()
        val regWriteEnable = Bool()
        val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)

        // 调试信号 (仅供仿真使用)
        val inst_jal = Bool()
        val inst_jalr = Bool()
    }
    object EX_MA_Bundle {
        def apply(): EX_MA_Bundle = {
            val default = Wire(new EX_MA_Bundle)

            default.pcNext := 0.U
            default.pcTarget := 0.U
            default.aluOutput := 0.U
            default.compBranchEnable := false.B
            default.storeData := 0.U
            default.rd := 0.U
            default.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            default.memReadEnable := false.B
            default.memWriteEnable := false.B
            default.regWriteEnable := false.B
            default.regWriteDataSel := 0.U
            default.inst_jal := false.B
            default.inst_jalr := false.B

            default
        }
    }

    /**
      * 从 MA 阶段到 WB 阶段所需流转的数据。
      */
    class MA_WB_Bundle extends Bundle {
        val pcNext = UInt(32.W)
        val pcTarget = UInt(32.W)
        val memReadData = UInt(32.W)
        val aluOutput = UInt(32.W)
        val compBranchEnable = Bool()
        val rd = UInt(5.W)

        // 控制信号组
        val regWriteEnable = Bool()
        val regWriteDataSel = UInt(ControlUnit.RD_MUX_SEL_LEN.W)
    }
    object MA_WB_Bundle {
        def apply(): MA_WB_Bundle = {
            val default = Wire(new MA_WB_Bundle)

            default.pcNext := 0.U
            default.pcTarget := ProcessorCore.PC_INITIAL_VAL
            default.memReadData := 0.U
            default.aluOutput := 0.U
            default.compBranchEnable := false.B
            default.rd := 0.U
            default.regWriteEnable := false.B
            default.regWriteDataSel := 0.U

            default
        }
    }

    /**
      * 从 WB 阶段到 UPC 阶段所需流转的数据。
      */
    class WB_UPC_Bundle extends Bundle {
        val pcTarget = UInt(32.W)
    }
    object WB_UPC_Bundle {
        def apply(): WB_UPC_Bundle = {
            val default = Wire(new WB_UPC_Bundle)

            default.pcTarget := 0.U

            default
        }
    }

    /**
      * 目前的 IO 方式采用哈佛架构（指令的存取与程序数据的存取分离）
      * 但是实际仿真时也可适用于冯诺依曼架构（将指令与程序数据存在一块即可）
      */
    val io = IO(new Bundle {
        /**
          * 输出：程序计数器地址。处理器将会从这个主存地址取指令
          */
        val pc = Output(UInt(32.W))
        /**
          * 输入：指令数据
          */
        val instData = Input(UInt(32.W))

        /**
          * 输出：读入程序数据，读使能
          */
        val readEnable = Output(Bool())
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
          * 输出：读入/写出程序数据，字掩码
          */
        val dataStrobe = Output(UInt(4.W))
        /**
          * 输出：读入/写出程序数据，地址
          */
        val address = Output(UInt(32.W))
    })

    val ioDPI = IO(new DPIBundle)

    val stage = Wire(UInt(StageController.STAGE_LEN.W))

    val pc_r = RegInit(ProcessorCore.PC_INITIAL_VAL)

    val if_id_r = RegInit(IF_ID_Bundle())
    val id_ex_r = RegInit(ID_EX_Bundle())
    val ex_ma_r = RegInit(EX_MA_Bundle())
    val ma_wb_r = RegInit(MA_WB_Bundle())
    val wb_upc_r = RegInit(WB_UPC_Bundle())

    val rs1Data = Wire(UInt(32.W))
    val rs2Data = Wire(UInt(32.W))
    val imm = Wire(UInt(32.W))

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

    val regWriteEnable = Wire(Bool())
    val immSel = Wire(UInt(ControlUnit.IMM_SEL_LEN.W))
    val executePortASel = Wire(Bool())
    val executePortBSel = Wire(Bool())
    val aluOpSel = Wire(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
    val compOpSel = Wire(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
    val dataMemWriteEnable = Wire(Bool())
    val dataMemReadEnable = Wire(Bool())
    val regWriteDataSel = Wire(UInt(ControlUnit.RD_MUX_SEL_LEN.W))

    val regData = Wire(UInt(32.W))

    io.pc := pc_r

    val stageCtrl = Module(new StageController)
    stage := stageCtrl.io.stage

    /* 
    寄存器堆: ID 阶段读取数据, WB 阶段写入数据, 二者理论上不会发生读写冲突.
     */
    val regFile = Module(new RegisterFile)
    rs1Data := regFile.io.readData1
    rs2Data := regFile.io.readData2
    regFile.io.writeEnable := Mux(stage === StageController.STAGE_WB.U, ma_wb_r.regWriteEnable, false.B)
    regFile.io.writeData := regData
    regFile.io.writeAddress := ma_wb_r.rd
    regFile.io.readAddress1 := if_id_r.inst(19, 15)
    regFile.io.readAddress2 := if_id_r.inst(24, 20)

    {
        // default values
        // regData := DontCare
        regData := 0.U(32.W)

        when(ma_wb_r.regWriteDataSel === ControlUnit.RD_MUX_DMEM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := ma_wb_r.memReadData
        }
        when(ma_wb_r.regWriteDataSel === ControlUnit.RD_MUX_ALU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := ma_wb_r.aluOutput
        }
        when(ma_wb_r.regWriteDataSel === ControlUnit.RD_MUX_BCU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := Cat(0.U(31.W), ma_wb_r.compBranchEnable.asUInt)
        }
        when(ma_wb_r.regWriteDataSel === ControlUnit.RD_MUX_IMM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := ma_wb_r.aluOutput
        }
        when(ma_wb_r.regWriteDataSel === ControlUnit.RD_MUX_PC_N.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            regData := ma_wb_r.pcNext
        }
    }

    val ise = Module(new ImmediateSignExtend)
    imm := ise.io.immOut
    ise.io.inst := if_id_r.inst
    ise.io.immSel := immSel

    val cu = Module(new ControlUnit)
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
    cu.io.opCode := if_id_r.inst(6, 0)
    cu.io.funct3 := if_id_r.inst(14, 12)
    cu.io.funct7Bit5 := if_id_r.inst(30)

    aluPortA := Mux(id_ex_r.aluPortASel, id_ex_r.rs1Data, pc_r)
    aluPortB := Mux(id_ex_r.aluPortBSel, id_ex_r.rs2Data, id_ex_r.imm)
    compPortA := id_ex_r.rs1Data
    compPortB := Mux(executePortBSel, id_ex_r.imm, id_ex_r.rs2Data)
    
    val alu = Module(new ArithmeticLogicUnit)
    aluOutput := alu.io.alu
    alu.io.aluPortA := aluPortA
    alu.io.aluPortB := aluPortB
    alu.io.aluSel := id_ex_r.aluOpSel

    val compU = Module(new ComparatorUnit)
    branchEnable := compU.io.comp
    compU.io.compPortA := compPortA
    compU.io.compPortB := compPortB
    compU.io.compOpSel := id_ex_r.compOpSel

    val lsu = Module(new LoadAndStoreUnit)
    readDataAligned := lsu.io.readDataOut
    lsu.io.readDataIn := io.readData
    io.writeData := lsu.io.writeDataOut
    lsu.io.writeDataIn := writeDataUnaligned
    lsu.io.lsType := ex_ma_r.lsType
    io.dataStrobe := lsu.io.dataStrobe

    io.address := ex_ma_r.aluOutput
    dmemData := readDataAligned
    io.writeEnable := ex_ma_r.memWriteEnable
    io.readEnable := dataMemReadEnable
    writeDataUnaligned := ex_ma_r.storeData

    val pcTargetCtrl = Module(new PCTargetController)
    pcTargetCtrl.io.cuJumpEnable := id_ex_r.cuJumpEnable
    pcTargetCtrl.io.cuJumpType := id_ex_r.cuJumpType
    pcTargetCtrl.io.compBranchEnable := branchEnable
    pcTargetCtrl.io.cuBranchEnable := id_ex_r.cuBranchEnable
    pcTargetCtrl.io.pc := pc_r
    pcTargetCtrl.io.imm := id_ex_r.imm
    pcTargetCtrl.io.pcNext := id_ex_r.pcNext
    pcTargetCtrl.io.rs1Data := id_ex_r.rs1Data

    val dpi = Module(new DPIAdapter)
    /* 
    RV32I 中，ebreak 指令的机器码是 0x00100073
    目前我们硬编码该指令为处理器仿真结束指令，遇到该指令时将 halt 输出置高电平，
    外部 BlackBox 检测到到该电平上升沿后，自动调用 DPI-C 接口终止仿真。
    */
    dpi.io.halt := Mux(stage === StageController.STAGE_ID.U, if_id_r.inst === "h00100073".U(32.W), false.B)
    dpi.io.inst_jal := false.B
    dpi.io.inst_jalr := false.B
    dpi.io.memWriteEnable := false.B
    dpi.io.memReadEnable := false.B
    when(stage === StageController.STAGE_MA.U) {
        dpi.io.inst_jal := ex_ma_r.inst_jal
        dpi.io.inst_jalr := ex_ma_r.inst_jalr
        dpi.io.memWriteEnable := ex_ma_r.memWriteEnable
        dpi.io.memReadEnable := ex_ma_r.memReadEnable
    }
    dpi.io.stage := stage

    for (i <- 0 until ioDPI.registers.length) {
        ioDPI.registers(i) := regFile.io.registers(i)
    }
    ioDPI.inst_jal := ex_ma_r.inst_jal
    ioDPI.inst_jalr := ex_ma_r.inst_jalr
    ioDPI.rs1 := if_id_r.inst(19, 15)
    ioDPI.rs2 := if_id_r.inst(24, 20)
    ioDPI.rd := if_id_r.inst(11, 7)
    ioDPI.imm := imm
    ioDPI.rs1Data := id_ex_r.rs1Data
    ioDPI.rs2Data := id_ex_r.rs1Data
    ioDPI.stage := stage

    when(stage === StageController.STAGE_IF.U(StageController.STAGE_LEN.W)) {
        // IF 阶段
        if_id_r.pcNext := pc_r + 4.U(32.W)
        if_id_r.inst := io.instData
    }.elsewhen(stage === StageController.STAGE_ID.U(StageController.STAGE_LEN.W)) {
        // ID 阶段
        id_ex_r.pcNext := if_id_r.pcNext
        id_ex_r.rs1Data := rs1Data
        id_ex_r.rs2Data := rs2Data
        id_ex_r.imm := imm
        id_ex_r.rd := if_id_r.inst(11, 7)
        id_ex_r.rs1 := if_id_r.inst(19, 15)
        id_ex_r.rs2 := if_id_r.inst(24, 20)
        id_ex_r.regWriteEnable := regWriteEnable
        id_ex_r.aluPortASel := executePortASel
        id_ex_r.aluPortBSel := executePortBSel
        id_ex_r.aluOpSel := aluOpSel
        id_ex_r.compOpSel := compOpSel
        id_ex_r.lsType := lsType
        id_ex_r.memWriteEnable := dataMemWriteEnable
        id_ex_r.memReadEnable := dataMemReadEnable
        id_ex_r.regWriteDataSel := regWriteDataSel
        id_ex_r.cuJumpEnable := cu.io.jumpEnable
        id_ex_r.cuJumpType := cu.io.jumpType
        id_ex_r.cuBranchEnable := cu.io.branchEnable
        id_ex_r.inst_jal := cu.io.inst_jal
        id_ex_r.inst_jalr := cu.io.inst_jalr
    }.elsewhen(stage === StageController.STAGE_EX.U(StageController.STAGE_LEN.W)) {
        // EX 阶段
        ex_ma_r.pcNext := id_ex_r.pcNext
        ex_ma_r.pcTarget := pcTargetCtrl.io.pcTarget
        ex_ma_r.aluOutput := aluOutput
        ex_ma_r.compBranchEnable := branchEnable
        ex_ma_r.storeData := id_ex_r.rs2Data
        ex_ma_r.rd := id_ex_r.rd
        ex_ma_r.lsType := id_ex_r.lsType
        ex_ma_r.memReadEnable := id_ex_r.memReadEnable
        ex_ma_r.memWriteEnable := id_ex_r.memWriteEnable
        ex_ma_r.regWriteEnable := id_ex_r.regWriteEnable
        ex_ma_r.regWriteDataSel := id_ex_r.regWriteDataSel
        ex_ma_r.inst_jal := id_ex_r.inst_jal
        ex_ma_r.inst_jalr := id_ex_r.inst_jalr
    }.elsewhen(stage === StageController.STAGE_MA.U(StageController.STAGE_LEN.W)) {
        // MA 阶段
        ma_wb_r.pcNext := ex_ma_r.pcNext
        ma_wb_r.pcTarget := ex_ma_r.pcTarget
        ma_wb_r.memReadData := dmemData
        ma_wb_r.aluOutput := ex_ma_r.aluOutput
        ma_wb_r.compBranchEnable := ex_ma_r.compBranchEnable
        ma_wb_r.rd := ex_ma_r.rd
        ma_wb_r.regWriteEnable := ex_ma_r.regWriteEnable
        ma_wb_r.regWriteDataSel := ex_ma_r.regWriteDataSel
    }.elsewhen(stage === StageController.STAGE_WB.U(StageController.STAGE_LEN.W)) {
        // WB 阶段
        wb_upc_r.pcTarget := ma_wb_r.pcTarget
    }.elsewhen(stage === StageController.STAGE_UPC.U(StageController.STAGE_LEN.W)) {
        // UPC 阶段
        pc_r := wb_upc_r.pcTarget
    }
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
