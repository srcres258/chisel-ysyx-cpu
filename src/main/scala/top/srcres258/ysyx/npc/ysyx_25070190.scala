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
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.util.MemoryRange
import top.srcres258.ysyx.npc.device.CLINT
import top.srcres258.ysyx.npc.dpi.dummy.DummyCLINT
import top.srcres258.ysyx.npc.bus.AXI4

/**
  * RV32I 单周期处理器核心.
  */
class ysyx_25070190(
    /**
      * 处理器字长.
      */
    val xLen: Int
) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val master = Wire(new AXI4(xLen))
    AXI4.defaultValuesForMaster(master)
    val slave = Wire(Flipped(new AXI4(xLen)))
    AXI4.defaultValuesForSlave(slave)

    val io = IO(new Bundle {
        val interrupt = Input(Bool()) // 中断信号
        val master = new ysyx_25070190.OutMasterBundle(xLen)
        val slave = Flipped(new ysyx_25070190.OutMasterBundle(xLen)) // For unknown purpose, ysyxSoC 要求处理器核心模块暴露这些信号出来.
    })
    io.master.masterConnectWith(master)
    io.slave.slaveConnectWith(slave)

    val executing = RegInit(false.B)

    val pc_r = RegInit(Configuration.PC_INITIAL_VAL.U(xLen.W))

    /* 
    寄存器堆: ID 阶段读取数据, WB 阶段写入数据, 二者理论上不会发生读写冲突.
     */
    val gprFile = Module(new GeneralPurposeRegisterFile(xLen))
    val csrFile = Module(new ControlAndStatusRegisterFile(xLen))
    GeneralPurposeRegisterFile.defaultValuesForMaster(gprFile)
    ControlAndStatusRegisterFile.defaultValuesForMaster(csrFile)

    val clint = Module(new CLINT(xLen))
    AXI4Lite.defaultValuesForMaster(clint.io.bus)

    /**
      * 仲裁器: 我们这里选用 Round-Robin Arbiter.
      */
    val arbiter = Module(new RoundRobinArbiter(Configuration.Arbiter.ARBITER_MAX_MASTER_AMOUNT))
    RoundRobinArbiter.IOBundle.defaultValuesForMaster(arbiter.io)

    val ifu = Module(new IFUnit(xLen))
    ifu.io.executionInfo.bits.pc := pc_r
    when(!executing && ifu.io.nextStage.fire) {
        executing := true.B
    }
    ifu.io.executionInfo.valid := !reset.asBool && !executing
    when(ifu.io.working) {
        arbiter.io.req(Configuration.Arbiter.ARBITER_MASTER_IDX_IF_UNIT) := ifu.io.arbiterReq
        arbiter.io.release(Configuration.Arbiter.ARBITER_MASTER_IDX_IF_UNIT).valid := ifu.io.arbiterRelease
        ifu.io.arbiterGranted := arbiter.io.grantIdx === Configuration.Arbiter.ARBITER_MASTER_IDX_IF_UNIT.U
        ifu.io.arbiterReleaseReady := arbiter.io.release(Configuration.Arbiter.ARBITER_MASTER_IDX_IF_UNIT).ready
        ifu.io.memBus <> master
    }.otherwise {
        ifu.io.arbiterGranted := false.B
        ifu.io.arbiterReleaseReady := false.B
        AXI4.defaultValuesForSlave(ifu.io.memBus)
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
        arbiter.io.req(Configuration.Arbiter.ARBITER_MASTER_IDX_MA_UNIT) := mau.io.arbiterReq
        arbiter.io.release(Configuration.Arbiter.ARBITER_MASTER_IDX_MA_UNIT).valid := mau.io.arbiterRelease
        mau.io.arbiterGranted := arbiter.io.grantIdx === Configuration.Arbiter.ARBITER_MASTER_IDX_MA_UNIT.U
        mau.io.arbiterReleaseReady := arbiter.io.release(Configuration.Arbiter.ARBITER_MASTER_IDX_MA_UNIT).ready
        mau.io.memBus <> master
    }.otherwise {
        mau.io.arbiterGranted := false.B
        mau.io.arbiterReleaseReady := false.B
        AXI4.defaultValuesForSlave(mau.io.memBus)
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

    val generalDPI = Wire(new GeneralDPIBundle(xLen))
    generalDPI.clock := clock
    generalDPI.reset := reset

    generalDPI.core.pc := pc_r
    /* 
    RV32I 中，ebreak 指令的机器码是 0x00100073
    目前我们硬编码该指令为处理器仿真结束指令，遇到该指令时将 halt 输出置高电平，
    外部 BlackBox 检测到到该电平上升沿后，自动调用 DPI-C 接口终止仿真。
     */
    generalDPI.core.halt := idu.io.dpi.inst === "h00100073".U(32.W)
    generalDPI.core.executing := executing
    generalDPI.core.ifuInputValid := !executing

    generalDPI.clint <> clint.io.dpi
    generalDPI.gpr <> gprFile.io.dpi
    generalDPI.csr <> csrFile.io.dpi

    generalDPI.ifu <> ifu.io.dpi
    generalDPI.idu <> idu.io.dpi
    generalDPI.exu <> exu.io.dpi
    generalDPI.mau <> mau.io.dpi
    generalDPI.wbu <> wbu.io.dpi
    generalDPI.upcu <> upcu.io.dpi

    val dpi = Module(new GeneralDPIAdapter(xLen))
    dpi.io <> generalDPI
}

object ysyx_25070190 extends App {
    class OutMasterBundle(xLen: Int) extends Bundle {
        val arvalid = Output(Bool())
        val araddr = Output(UInt(xLen.W))
        val arid = Output(UInt(4.W))
        val arlen = Output(UInt(8.W))
        val arsize = Output(UInt(3.W))
        val arburst = Output(UInt(2.W))
        val arready = Input(Bool())
        
        val rvalid = Input(Bool())
        val rresp = Input(UInt(2.W))
        val rdata = Input(UInt(xLen.W))
        val rlast = Input(Bool())
        val rid = Input(UInt(4.W))
        val rready = Output(Bool())

        val awvalid = Output(Bool())
        val awaddr = Output(UInt(xLen.W))
        val awid = Output(UInt(4.W))
        val awlen = Output(UInt(8.W))
        val awsize = Output(UInt(3.W))
        val awburst = Output(UInt(2.W))
        val awready = Input(Bool())
        
        val wvalid = Output(Bool())
        val wdata = Output(UInt(xLen.W))
        val wstrb = Output(UInt((xLen / 8).W))
        val wlast = Output(Bool())
        val wready = Input(Bool())

        val bvalid = Input(Bool())
        val bresp = Input(UInt(2.W))
        val bid = Input(UInt(4.W))
        val bready = Output(Bool())

        /**
          * 作为 AXI4 总线的 master 端进行信号连接.
          */
        def masterConnectWith(master: AXI4): Unit = {
            arvalid := master.ar.valid
            araddr := master.ar.bits.addr
            arid := master.ar.bits.id
            arlen := master.ar.bits.len
            arsize := master.ar.bits.size
            arburst := master.ar.bits.burst
            master.ar.ready := arready

            master.r.valid := rvalid
            master.r.bits.resp := rresp
            master.r.bits.data := rdata
            master.r.bits.last := rlast
            master.r.bits.id := rid
            rready := master.r.ready

            awvalid := master.aw.valid
            awaddr := master.aw.bits.addr
            awid := master.aw.bits.id
            awlen := master.aw.bits.len
            awsize := master.aw.bits.size
            awburst := master.aw.bits.burst
            master.aw.ready := awready

            wvalid := master.w.valid
            wdata := master.w.bits.data
            wstrb := master.w.bits.strb
            wlast := master.w.bits.last
            master.w.ready := wready

            master.b.valid := bvalid
            master.b.bits.resp := bresp
            master.b.bits.id := bid
            bready := master.b.ready
        }

        /**
          * 作为 AXI4 总线的 slave 端进行信号连接.
          */
        def slaveConnectWith(slave: AXI4): Unit = {
            slave.ar.valid := arvalid
            slave.ar.bits.addr := araddr
            slave.ar.bits.id := arid
            slave.ar.bits.len := arlen
            slave.ar.bits.size := arsize
            slave.ar.bits.burst := arburst
            arready := slave.ar.ready

            rvalid := slave.r.valid
            rresp := slave.r.bits.resp
            rdata := slave.r.bits.data
            rlast := slave.r.bits.last
            rid := slave.r.bits.id
            slave.r.ready := rready

            slave.aw.valid := awvalid
            slave.aw.bits.addr := awaddr
            slave.aw.bits.id := awid
            slave.aw.bits.len := awlen
            slave.aw.bits.size := awsize
            slave.aw.bits.burst := awburst
            awready := slave.aw.ready

            slave.w.valid := wvalid
            slave.w.bits.data := wdata
            slave.w.bits.strb := wstrb
            slave.w.bits.last := wlast
            wready := slave.w.ready

            bvalid := slave.b.valid
            bresp := slave.b.bits.resp
            bid := slave.b.bits.id
            slave.b.ready := bready
        }
    }

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

    // val enableDPIAdapter = args.contains("enableDPIAdapter")
    val enableRandomDelay = args.contains("enableRandomDelay")

    val cs = new ChiselStage
    println("Emitting SystemVerilog for ProcessorCore with arguments:")
    // println(s"  enableDPIAdapter: $enableDPIAdapter")
    println(s"  enableRandomDelay: $enableRandomDelay")
    cs.execute(
        Array(
            "--target", "systemverilog",
            "--target-dir", "generated",
            "--split-verilog",
            "--firtool-option", "-lowering-options=disallowLocalVariables"
        ),
        Seq(ChiselGeneratorAnnotation(() => new ysyx_25070190(xLen = Configuration.XLEN)))
    )
}
