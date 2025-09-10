package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq

import top.srcres258.ysyx.npc.stage._
import top.srcres258.ysyx.npc.dpi.DPIAdapter
import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.regfile.ControlAndStatusRegisterFile

/**
  * RV32I 单周期处理器核心
  */
class ProcessorCore extends Module {
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
        val dataStrobe = Output(UInt(LoadAndStoreUnit.DATA_STROBE_LEN.W))
        /**
          * 输出：读入/写出程序数据，地址
          */
        val address = Output(UInt(32.W))
    })

    val pc_r = RegInit(ProcessorCore.PC_INITIAL_VAL)
    io.pc := pc_r

    val if_id_r = RegInit(IF_ID_Bundle())
    val id_ex_r = RegInit(ID_EX_Bundle())
    val ex_ma_r = RegInit(EX_MA_Bundle())
    val ma_wb_r = RegInit(MA_WB_Bundle())
    val wb_upc_r = RegInit(WB_UPC_Bundle())

    val stageCtrl = Module(new StageController)
    val stage = Wire(UInt(StageController.STAGE_LEN.W))
    stage := stageCtrl.io.stage

    /* 
    寄存器堆: ID 阶段读取数据, WB 阶段写入数据, 二者理论上不会发生读写冲突.
     */
    val gprFile = Module(new GeneralPurposeRegisterFile)
    gprFile.io.writePort.writeEnable := false.B
    gprFile.io.writePort.writeData := 0.U
    gprFile.io.writePort.writeAddress := 0.U
    gprFile.io.readPort.readAddress1 := 0.U
    gprFile.io.readPort.readAddress2 := 0.U

    val csrFile = Module(new ControlAndStatusRegisterFile)
    for (writePort <- List(csrFile.io.writePort1, csrFile.io.writePort2)) {
        writePort.writeEnable := false.B
        writePort.writeData := 0.U
        writePort.writeAddress := 0.U
    }
    for (readPort <- List(csrFile.io.readPort1, csrFile.io.readPort2, csrFile.io.readPort3)) {
        readPort.readAddress := 0.U
    }

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

    val ioDPI = IO(new DPIBundle)
    for (i <- 0 until ioDPI.gprs.length) {
        ioDPI.gprs(i) := gprFile.io.registers(i)
    }
    ioDPI.csr_mstatus := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MSTATUS)
    ioDPI.csr_mtvec := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MTVEC)
    ioDPI.csr_mepc := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MEPC)
    ioDPI.csr_mcause := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MCAUSE)
    ioDPI.csr_mtval := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MTVAL)
    ioDPI.inst_jal := 0.U
    ioDPI.inst_jalr := 0.U
    ioDPI.rs1 := 0.U
    ioDPI.rs2 := 0.U
    ioDPI.rd := 0.U
    ioDPI.imm := 0.U
    ioDPI.rs1Data := 0.U
    ioDPI.rs2Data := 0.U
    ioDPI.stage := stage

    val ifu = Module(new IFUnit)
    ifu.io.pc := 0.U
    ifu.io.instData := 0.U

    val idu = Module(new IDUnit)
    idu.io.gprReadPort.readData1 := 0.U
    idu.io.gprReadPort.readData2 := 0.U
    for (csrReadPort <- List(idu.io.csrReadPort1, idu.io.csrReadPort2, idu.io.csrReadPort3)) {
        csrReadPort.readData := 0.U
    }
    idu.io.prevStage <> IF_ID_Bundle()

    val exu = Module(new EXUnit)
    exu.io.pc := 0.U
    exu.io.prevStage <> ID_EX_Bundle()

    val mau = Module(new MAUnit)
    mau.io.readData := 0.U
    mau.io.prevStage <> EX_MA_Bundle()

    val wbu = Module(new WBUnit())
    wbu.io.pc := 0.U
    wbu.io.prevStage <> MA_WB_Bundle()

    val upcu = Module(new UPCUnit())
    upcu.io.prevStage <> WB_UPC_Bundle()

    io.readEnable := false.B
    io.writeEnable := false.B
    io.writeData := 0.U
    io.dataStrobe := 0.U
    io.address := 0.U
    when(stage === StageController.STAGE_IF.U(StageController.STAGE_LEN.W)) {
        // IF 阶段
        ifu.io.pc := pc_r
        ifu.io.instData := io.instData

        if_id_r <> ifu.io.nextStage
    }.elsewhen(stage === StageController.STAGE_ID.U(StageController.STAGE_LEN.W)) {
        // ID 阶段
        idu.io.gprReadPort <> gprFile.io.readPort
        idu.io.csrReadPort1 <> csrFile.io.readPort1
        idu.io.csrReadPort2 <> csrFile.io.readPort2
        idu.io.csrReadPort3 <> csrFile.io.readPort3
        idu.io.prevStage <> if_id_r

        id_ex_r <> idu.io.nextStage

        ioDPI.rs1 := idu.ioDPI.rs1
        ioDPI.rs2 := idu.ioDPI.rs2
        ioDPI.rd := idu.ioDPI.rd
        ioDPI.imm := idu.ioDPI.imm
    }.elsewhen(stage === StageController.STAGE_EX.U(StageController.STAGE_LEN.W)) {
        // EX 阶段
        exu.io.pc := pc_r
        exu.io.prevStage <> id_ex_r

        ex_ma_r <> exu.io.nextStage

        ioDPI.rs1Data := exu.ioDPI.rs1Data
        ioDPI.rs2Data := exu.ioDPI.rs2Data
    }.elsewhen(stage === StageController.STAGE_MA.U(StageController.STAGE_LEN.W)) {
        // MA 阶段
        mau.io.readData := io.readData
        mau.io.prevStage <> ex_ma_r

        io.readEnable := mau.io.readEnable
        io.writeEnable := mau.io.writeEnable
        io.writeData := mau.io.writeData
        io.dataStrobe := mau.io.dataStrobe
        io.address := mau.io.address
        ma_wb_r <> mau.io.nextStage

        ioDPI.inst_jal := mau.ioDPI.inst_jal
        ioDPI.inst_jalr := mau.ioDPI.inst_jalr
    }.elsewhen(stage === StageController.STAGE_WB.U(StageController.STAGE_LEN.W)) {
        // WB 阶段
        wbu.io.pc := pc_r
        wbu.io.gprWritePort <> gprFile.io.writePort
        wbu.io.prevStage <> ma_wb_r

        csrFile.io.writePort1 <> wbu.io.csrWritePort1
        csrFile.io.writePort2 <> wbu.io.csrWritePort2
        wb_upc_r <> wbu.io.nextStage
    }.elsewhen(stage === StageController.STAGE_UPC.U(StageController.STAGE_LEN.W)) {
        // UPC 阶段
        upcu.io.prevStage <> wb_upc_r

        pc_r := upcu.io.pc
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
