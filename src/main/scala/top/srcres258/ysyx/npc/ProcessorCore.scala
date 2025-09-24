package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq

import top.srcres258.ysyx.npc.stage._
import top.srcres258.ysyx.npc.dpi.GeneralDPIAdapter
import top.srcres258.ysyx.npc.dpi.GeneralDPIBundle
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.regfile.ControlAndStatusRegisterFile
import top.srcres258.ysyx.npc.util.DecoupledIOConnect
import top.srcres258.ysyx.npc.dpi.impl._
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.arbiter.RoundRobinArbiter

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
    enableDPI: Boolean,
    /**
      * 处理器字长. 32 位 RISC-V ISA 下默认为 32.
      */
    val xLen: Int = 32
) extends Module {
    require(xLen == 32 || xLen == 64, "Only 32-bit or 64-bit processor core is supported.")

    val executing = RegInit(false.B)

    val pc_r = RegInit(ProcessorCore.PC_INITIAL_VAL)

    /* 
    寄存器堆: ID 阶段读取数据, WB 阶段写入数据, 二者理论上不会发生读写冲突.
     */
    val gprFile = Module(new GeneralPurposeRegisterFile(xLen))
    val csrFile = Module(new ControlAndStatusRegisterFile(xLen))
    GeneralPurposeRegisterFile.defaultValuesForMaster(gprFile)
    ControlAndStatusRegisterFile.defaultValuesForMaster(csrFile)

    val physicalRAM = Module(new PhysicalRAM(xLen))
    for (i <- 0 until PhysicalRAM.ARBITER_MAX_MASTER_AMOUNT) {
        AXI4Lite.defaultValuesForMaster(physicalRAM.io.busPorts(i))
    }
    RoundRobinArbiter.IOBundle.defaultValuesForMaster(physicalRAM.io.arbiter)

    val ifu = Module(new IFUnit(xLen))
    ifu.io.executionInfo.bits.pc := pc_r
    when(!executing && ifu.io.nextStage.fire) {
        executing := true.B
    }
    ifu.io.executionInfo.valid := !executing
    when(ifu.io.working) {
        physicalRAM.io.arbiter.req(PhysicalRAM.ARBITER_MASTER_IDX_IF_UNIT) := ifu.io.arbiterReq
        physicalRAM.io.arbiter.release(PhysicalRAM.ARBITER_MASTER_IDX_IF_UNIT).valid := ifu.io.arbiterRelease
        ifu.io.arbiterGranted := physicalRAM.io.arbiter.grantIdx === PhysicalRAM.ARBITER_MASTER_IDX_IF_UNIT.U
        ifu.io.arbiterReleaseReady := physicalRAM.io.arbiter.release(PhysicalRAM.ARBITER_MASTER_IDX_IF_UNIT).ready
        ifu.io.ramBus <> physicalRAM.io.busPorts(PhysicalRAM.ARBITER_MASTER_IDX_IF_UNIT)
    }.otherwise {
        ifu.io.arbiterGranted := false.B
        ifu.io.arbiterReleaseReady := false.B
        AXI4Lite.defaultValuesForSlave(ifu.io.ramBus)
    }

    val idu = Module(new IDUnit(xLen))
    DecoupledIOConnect(ifu.io.nextStage, idu.io.prevStage, DecoupledIOConnect.Pipeline)
    when(idu.io.working) {
        idu.io.gprReadPort <> gprFile.io.readPort
        idu.io.csrReadPort1 <> csrFile.io.readPort1
        idu.io.csrReadPort2 <> csrFile.io.readPort2
        idu.io.csrReadPort3 <> csrFile.io.readPort3
    }.otherwise {
        GeneralPurposeRegisterFile.ReadPort.defaultValuesForSlave(idu.io.gprReadPort)
        ControlAndStatusRegisterFile.ReadPort.defaultValuesForSlave(idu.io.csrReadPort1)
        ControlAndStatusRegisterFile.ReadPort.defaultValuesForSlave(idu.io.csrReadPort2)
        ControlAndStatusRegisterFile.ReadPort.defaultValuesForSlave(idu.io.csrReadPort3)
    }

    val exu = Module(new EXUnit(xLen))
    DecoupledIOConnect(idu.io.nextStage, exu.io.prevStage, DecoupledIOConnect.Pipeline)

    val mau = Module(new MAUnit(xLen))
    DecoupledIOConnect(exu.io.nextStage, mau.io.prevStage, DecoupledIOConnect.Pipeline)
    when(mau.io.working) {
        physicalRAM.io.arbiter.req(PhysicalRAM.ARBITER_MASTER_IDX_MA_UNIT) := mau.io.arbiterReq
        physicalRAM.io.arbiter.release(PhysicalRAM.ARBITER_MASTER_IDX_MA_UNIT).valid := mau.io.arbiterRelease
        mau.io.arbiterGranted := physicalRAM.io.arbiter.grantIdx === PhysicalRAM.ARBITER_MASTER_IDX_MA_UNIT.U
        mau.io.arbiterReleaseReady := physicalRAM.io.arbiter.release(PhysicalRAM.ARBITER_MASTER_IDX_MA_UNIT).ready
        mau.io.ramBus <> physicalRAM.io.busPorts(PhysicalRAM.ARBITER_MASTER_IDX_MA_UNIT)
    }.otherwise {
        mau.io.arbiterGranted := false.B
        mau.io.arbiterReleaseReady := false.B
        AXI4Lite.defaultValuesForSlave(mau.io.ramBus)
    }

    val wbu = Module(new WBUnit(xLen))
    DecoupledIOConnect(mau.io.nextStage, wbu.io.prevStage, DecoupledIOConnect.Pipeline)
    when(wbu.io.working) {
        gprFile.io.writePort <> wbu.io.gprWritePort
        csrFile.io.writePort1 <> wbu.io.csrWritePort1
        csrFile.io.writePort2 <> wbu.io.csrWritePort2
    }.otherwise {
        // Nothing to do here. 因为 GPR 和 CSR 两个寄存器文件的写端都没有输出,
        // 所以 WB 单元的写端没有接收信号线, 即没有缺省值需要赋.
    }

    val upcu = Module(new UPCUnit(xLen))
    DecoupledIOConnect(wbu.io.nextStage, upcu.io.prevStage, DecoupledIOConnect.Pipeline)
    when(upcu.io.pcOutput.fire) {
        pc_r := upcu.io.pcOutput.bits
        executing := false.B
    }
    upcu.io.pcOutput.ready := executing

    if (enableDPI) {
        val generalDPI = Wire(new GeneralDPIBundle(xLen))

        generalDPI.core.pc := pc_r
        /* 
        RV32I 中，ebreak 指令的机器码是 0x00100073
        目前我们硬编码该指令为处理器仿真结束指令，遇到该指令时将 halt 输出置高电平，
        外部 BlackBox 检测到到该电平上升沿后，自动调用 DPI-C 接口终止仿真。
         */
        generalDPI.core.halt := idu.io.dpi.inst === "h00100073".U(32.W)
        generalDPI.core.executing := executing
        generalDPI.core.ifuInputValid := !executing

        generalDPI.physicalRAM <> physicalRAM.io.dpi
        generalDPI.gpr <> gprFile.io.dpi
        generalDPI.csr <> csrFile.io.dpi

        generalDPI.ifu <> ifu.io.dpi
        generalDPI.idu <> idu.io.dpi
        generalDPI.exu <> exu.io.dpi
        generalDPI.mau <> mau.io.dpi
        generalDPI.wbu <> wbu.io.dpi
        generalDPI.upcu <> upcu.io.dpi

        val ioDPI = IO(new GeneralDPIBundle(xLen))
        ioDPI <> generalDPI

        val dpi = Module(new GeneralDPIAdapter(xLen))
        dpi.io <> generalDPI
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
    // 所以在硬件电路中就没必要再量化表示各个阶段.

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
