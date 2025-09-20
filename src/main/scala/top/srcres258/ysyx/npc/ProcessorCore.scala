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
class ProcessorCore(
    /**
      * 是否启用 DPI 信号输出, 供仿真环境访问.
      * 
      * 网表综合时应将该选项置为 false 以省去不必要信号元件,
      * 获得最真实的硬件级仿真结果.
      */
    enableDPI: Boolean
) extends Module {
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

    val executing = RegInit(false.B)

    val pc_r = RegInit(ProcessorCore.PC_INITIAL_VAL)
    io.pc := pc_r

    /* 
    寄存器堆: ID 阶段读取数据, WB 阶段写入数据, 二者理论上不会发生读写冲突.
     */
    val gprFile = Module(new GeneralPurposeRegisterFile)

    val csrFile = Module(new ControlAndStatusRegisterFile)

    val ifu = Module(new IFUnit)
    val ifuInputValid = RegInit(false.B)
    // ifu.io.pc := 0.U
    // ifu.io.instData := 0.U
    ifu.io.input.bits.pc := pc_r
    ifu.io.input.bits.instData := io.instData
    when(!executing) {
        ifuInputValid := true.B

        when(ifu.io.input.ready) {
            executing := true.B
            ifuInputValid := false.B
        }
    }
    ifu.io.input.valid := ifuInputValid

    val idu = Module(new IDUnit)
    idu.io.prevStage <> ifu.io.nextStage
    idu.io.gprReadPort <> gprFile.io.readPort
    idu.io.csrReadPort1 <> csrFile.io.readPort1
    idu.io.csrReadPort2 <> csrFile.io.readPort2
    idu.io.csrReadPort3 <> csrFile.io.readPort3

    val exu = Module(new EXUnit)
    exu.io.prevStage <> idu.io.nextStage

    val mau = Module(new MAUnit)
    mau.io.prevStage <> exu.io.nextStage
    mau.io.readData := io.readData
    io.readEnable := mau.io.readEnable
    io.writeEnable := mau.io.writeEnable
    io.writeData := mau.io.writeData
    io.dataStrobe := mau.io.dataStrobe
    io.address := mau.io.address

    val wbu = Module(new WBUnit)
    wbu.io.prevStage <> mau.io.nextStage
    wbu.io.gprWritePort <> gprFile.io.writePort
    csrFile.io.writePort1 <> wbu.io.csrWritePort1
    csrFile.io.writePort2 <> wbu.io.csrWritePort2

    val upcu = Module(new UPCUnit)
    val upcuPCOutputReady = RegInit(false.B)
    upcu.io.prevStage <> wbu.io.nextStage
    when(upcu.io.pcOutput.valid) {
        pc_r := upcu.io.pcOutput.bits
        upcuPCOutputReady := true.B
        executing := false.B
    }.otherwise {
        upcuPCOutputReady := false.B
    }
    upcu.io.pcOutput.ready := upcuPCOutputReady

    if (enableDPI) {
        val dpiBundleTemp = Wire(new DPIBundle)
        /* 
        RV32I 中，ebreak 指令的机器码是 0x00100073
        目前我们硬编码该指令为处理器仿真结束指令，遇到该指令时将 halt 输出置高电平，
        外部 BlackBox 检测到到该电平上升沿后，自动调用 DPI-C 接口终止仿真。
        */
        dpiBundleTemp.halt := idu.io.debug.inst === "h00100073".U(32.W)
        for (i <- 0 until dpiBundleTemp.gprs.length) {
            dpiBundleTemp.gprs(i) := gprFile.io.registers(i)
        }
        dpiBundleTemp.csr_mstatus := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MSTATUS)
        dpiBundleTemp.csr_mtvec := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MTVEC)
        dpiBundleTemp.csr_mepc := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MEPC)
        dpiBundleTemp.csr_mcause := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MCAUSE)
        dpiBundleTemp.csr_mtval := csrFile.io.registers(ControlAndStatusRegisterFile.CSR_MTVAL)

        val inst_jal_dpi_r = RegInit(false.B)
        val inst_jalr_dpi_r = RegInit(false.B)
        val memWriteEnable_dpi_r = RegInit(false.B)
        val memReadEnable_dpi_r = RegInit(false.B)
        val ecallEnable_dpi_r = RegInit(false.B)
        when(mau.io.prevStage.ready) {
            inst_jal_dpi_r := mau.ioDPI.inst_jal
            inst_jalr_dpi_r := mau.ioDPI.inst_jalr
            memWriteEnable_dpi_r := mau.io.debug.memWriteEnable
            memReadEnable_dpi_r := mau.io.debug.memReadEnable
        }
        when(mau.io.nextStage.ready) {
            inst_jal_dpi_r := false.B
            inst_jalr_dpi_r := false.B
            memWriteEnable_dpi_r := false.B
            memReadEnable_dpi_r := false.B
        }
        when(exu.io.prevStage.ready) {
            ecallEnable_dpi_r := exu.io.debug.ecallEnable
        }
        when(exu.io.nextStage.ready) {
            ecallEnable_dpi_r := false.B
        }
        dpiBundleTemp.inst_jal := inst_jal_dpi_r
        dpiBundleTemp.inst_jalr := inst_jalr_dpi_r
        // TODO: Provide with debug data from each stage unit.
        dpiBundleTemp.rs1 := 0.U
        dpiBundleTemp.rs2 := 0.U
        dpiBundleTemp.rd := 0.U
        dpiBundleTemp.imm := 0.U
        dpiBundleTemp.rs1Data := 0.U
        dpiBundleTemp.rs2Data := 0.U
        dpiBundleTemp.memWriteEnable := memWriteEnable_dpi_r
        dpiBundleTemp.memReadEnable := memReadEnable_dpi_r
        dpiBundleTemp.ecallEnable := ecallEnable_dpi_r

        dpiBundleTemp.executing := executing
        dpiBundleTemp.ifuInputValid := ifuInputValid
        dpiBundleTemp.if_nextStage_valid := ifu.io.nextStage.valid
        dpiBundleTemp.id_nextStage_valid := idu.io.nextStage.valid
        dpiBundleTemp.ex_nextStage_valid := exu.io.nextStage.valid
        dpiBundleTemp.ma_nextStage_valid := mau.io.nextStage.valid
        dpiBundleTemp.wb_nextStage_valid := wbu.io.nextStage.valid
        dpiBundleTemp.upcu_pcOutput_valid := upcu.io.pcOutput.valid

        val ioDPI = IO(new DPIBundle)
        ioDPI <> dpiBundleTemp

        val dpi = Module(new DPIAdapter)
        dpi.io <> dpiBundleTemp
    }
}

object ProcessorCore extends App {
    val PC_INITIAL_VAL: UInt = "h80000000".U(32.W)

    /* 
    处理器执行的 6 个阶段: (每个阶段各耗时一个时钟周期)

    阶段1: IF  - Instruction Fetch      取指
    阶段2: ID  - Instruction Decode     译码
    阶段3: EX  - Execute                执行
    阶段4: MA  - Memory Access          访存
    阶段5: WB  - Write Back             写回
    阶段6: UPC - Update Program Counter 更新程序计数器

    目前 (多周期, 未流水线化) 处理器的执行过程:
    取指令后, 从阶段1按序执行到阶段6;
    阶段6过后再回到阶段1继续取指令.
     */

    // 我们把阶段的表示省掉, 因为各个阶段单元已经模块化, 之间遵循握手协议传递信息.
    // 所以没必要再量化表示各个阶段.

    val cs = new ChiselStage
    cs.execute(
        Array(
            "--target", "systemverilog",
            "--target-dir", "generated",
            "--split-verilog",
            "--firtool-option", "-lowering-options=disallowLocalVariables"
        ),
        Seq(ChiselGeneratorAnnotation(() => new ProcessorCore(
            args.length > 0 && args(0) == "enableDPI"
        )))
    )
}
