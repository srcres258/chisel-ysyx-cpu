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
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.util.MemoryRange
import top.srcres258.ysyx.npc.device.CLINT
import top.srcres258.ysyx.npc.bus.AXI4
import top.srcres258.ysyx.npc.cache.InstructionCache

class NPCWithSoC(val xLen: Int) extends Module {
    override def desiredName: String = "ysyx_25070190"

    Assertion.assertProcessorXLen(xLen)

    val master = Wire(new AXI4(xLen))
    AXI4.defaultValuesForMaster(master)
    val slave = Wire(Flipped(new AXI4(xLen)))
    AXI4.defaultValuesForSlave(slave)

    val io = IO(new Bundle {
        val interrupt = Input(Bool())
        val master = new Top.OutMasterBundle(xLen)
        val slave = Flipped(new Top.OutMasterBundle(xLen))
    })
    io.master.masterConnectWith(master)
    io.slave.slaveConnectWith(slave)

    val executing = RegInit(false.B)

    val pc_r = RegInit(Config.pcInitialVal.U(xLen.W))

    val gprFile = Module(new GeneralPurposeRegisterFile(xLen))
    val csrFile = Module(new ControlAndStatusRegisterFile(xLen))
    GeneralPurposeRegisterFile.defaultValuesForMaster(gprFile)
    ControlAndStatusRegisterFile.defaultValuesForMaster(csrFile)

    val lsu = Module(new LoadAndStoreUnit(xLen))
    lsu.io.memBus <> master

    val clint = Module(new CLINT(xLen))

    val ifu = Module(new IFUnit(xLen))
    ifu.io.executionInfo.bits.pc := pc_r
    when(!executing && ifu.io.nextStage.fire) {
        executing := true.B
    }
    ifu.io.executionInfo.valid := !reset.asBool && !executing
    val icache = Module(new InstructionCache(xLen, Config.icacheConfig))
    icache.io.cpuReq <> ifu.io.lsuIfetchReq
    icache.io.cpuResp <> ifu.io.lsuIfetchResp
    icache.io.lowerReq <> lsu.io.ifetchReq
    icache.io.lowerResp <> lsu.io.ifetchResp

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

    val memu = Module(new MEMUnit(xLen))
    DecoupledIOConnect(exu.io.nextStage, memu.io.prevStage, DecoupledIOConnect.Pipeline)
    lsu.io.memReq <> memu.io.lsuMemReq
    lsu.io.memResp <> memu.io.lsuMemResp

    memu.io.clintBus <> clint.io.bus

    val wbu = Module(new WBUnit(xLen))
    DecoupledIOConnect(memu.io.nextStage, wbu.io.prevStage, DecoupledIOConnect.Pipeline)
    when(wbu.io.working) {
        gprFile.io.writePort <> wbu.io.gprWritePort
        csrFile.io.writePort1 <> wbu.io.csrWritePort1
        csrFile.io.writePort2 <> wbu.io.csrWritePort2
    }.otherwise {
    }

    when(wbu.io.done) {
        pc_r := wbu.io.pcTargetOut
        executing := false.B
    }

    if (Config.enableDPI) {
        val generalDPI = Wire(new GeneralDPIBundle(xLen))
        generalDPI.clock := clock
        generalDPI.reset := reset

        generalDPI.core.pc := pc_r
        generalDPI.core.halt := idu.dpi.get.inst === "h00100073".U(32.W)
        generalDPI.core.executing := executing
        generalDPI.core.ifuInputValid := !executing

        generalDPI.clint <> clint.dpi.get
        generalDPI.gpr <> gprFile.dpi.get
        generalDPI.csr <> csrFile.dpi.get

        generalDPI.ifu <> ifu.dpi.get
        generalDPI.idu <> idu.dpi.get
        generalDPI.exu <> exu.dpi.get
        generalDPI.memu <> memu.dpi.get
        generalDPI.wbu <> wbu.dpi.get

        // Perf signal collector — gathers semantic perf signals from stage IOs.
        // This whole DPI observation graph is only elaborated in the DPI-enabled build.
        val perfCollector = Module(new PerfSignalCollector(xLen))
        perfCollector.io.core_executing := executing
        perfCollector.io.if_working  := ifu.io.working
        perfCollector.io.id_working  := idu.io.working
        perfCollector.io.ex_working  := exu.io.working
        perfCollector.io.mem_working := memu.io.working
        perfCollector.io.wb_working  := wbu.io.working
        perfCollector.io.wb_done      := wbu.io.done
        perfCollector.io.wb_inst      := wbu.dpi.get.inst
        perfCollector.io.wb_inst_jal  := wbu.dpi.get.inst_jal
        perfCollector.io.wb_inst_jalr := wbu.dpi.get.inst_jalr
        perfCollector.io.if_nextStage_valid  := ifu.io.nextStage.valid
        perfCollector.io.if_ifetch_req_valid  := ifu.io.lsuIfetchReq.valid
        perfCollector.io.if_ifetch_req_ready  := ifu.io.lsuIfetchReq.ready
        perfCollector.io.if_ifetch_resp_valid := ifu.io.lsuIfetchResp.valid
        perfCollector.io.if_ifetch_resp_ready := ifu.io.lsuIfetchResp.ready
        perfCollector.io.mem_nextStage_valid   := memu.io.nextStage.valid
        perfCollector.io.mem_lsu_req_valid     := memu.io.lsuMemReq.valid
        perfCollector.io.mem_lsu_req_ready     := memu.io.lsuMemReq.ready
        perfCollector.io.mem_lsu_req_isWrite   := memu.io.lsuMemReq.bits.isWrite
        perfCollector.io.mem_lsu_req_addr      := memu.io.lsuMemReq.bits.addr
        perfCollector.io.mem_lsu_req_ls_type   := memu.io.lsuMemReq.bits.lsType
        perfCollector.io.mem_lsu_resp_valid    := memu.io.lsuMemResp.valid
        perfCollector.io.mem_lsu_resp_ready    := memu.io.lsuMemResp.ready
        perfCollector.io.mem_clint_ar_fire     := memu.io.clintBus.ar.valid && memu.io.clintBus.ar.ready
        perfCollector.io.wb_csr_write2_enable  := wbu.io.csrWritePort2.writeEnable
        perfCollector.io.if_entry_fire   := ifu.io.executionInfo.fire
        perfCollector.io.id_entry_fire   := idu.io.prevStage.fire
        perfCollector.io.ex_entry_fire   := exu.io.prevStage.fire
        perfCollector.io.mem_entry_fire  := memu.io.prevStage.fire
        perfCollector.io.wb_entry_fire   := wbu.io.prevStage.fire
        perfCollector.io.if_exit_fire    := ifu.io.nextStage.fire
        perfCollector.io.id_exit_fire    := idu.io.nextStage.fire
        perfCollector.io.ex_exit_fire    := exu.io.nextStage.fire
        perfCollector.io.mem_exit_fire   := memu.io.nextStage.fire
        perfCollector.io.wb_exit_fire    := wbu.io.done
        perfCollector.io.wb_gpr_write_enable  := wbu.io.gprWritePort.writeEnable
        perfCollector.io.wb_csr_write1_enable := wbu.io.csrWritePort1.writeEnable
        perfCollector.io.wb_reg_write_data_sel := wbu.io.prevStage.bits.regWriteDataSel
        perfCollector.io.wb_csr_write_data_sel := wbu.io.prevStage.bits.csrRegWriteDataSel
        perfCollector.io.idu_rs1              := idu.io.gprReadPort.readAddress1
        perfCollector.io.idu_rs2              := idu.io.gprReadPort.readAddress2
        perfCollector.io.idu_rs2_unused       := idu.io.rs2Unused
        perfCollector.io.idu_is_system_inst   := idu.io.isSystemInst
        perfCollector.io.idu_csr_read_addr    := idu.io.csrReadPort1.readAddress
        perfCollector.io.wbu_gpr_write_suppressed_x0 := wbu.io.gprWriteSuppressedX0
        perfCollector.io.wbu_csr_write_addr   := wbu.io.csrWritePort1.writeAddress
        // IFetch phase + transaction signals (from IFUnit)
        perfCollector.io.ifu_ifetch_state    := ifu.io.ifetchState
        perfCollector.io.ifu_nextStage_ready := ifu.io.nextStage.ready
        // LSU observation signals (from LoadAndStoreUnit)
        perfCollector.io.lsu_state            := lsu.io.perfState
        perfCollector.io.lsu_pending_fetch    := lsu.io.perfPendingFetch
        perfCollector.io.lsu_pending_write    := lsu.io.perfPendingWrite
        perfCollector.io.lsu_needs_byte_split := lsu.io.perfNeedsByteSplit
        perfCollector.io.lsu_pending_ls_type  := lsu.io.perfPendingLsType
        perfCollector.io.lsu_axi_ar_fire      := lsu.io.memBus.ar.valid && lsu.io.memBus.ar.ready
        perfCollector.io.lsu_axi_aw_fire      := lsu.io.memBus.aw.valid && lsu.io.memBus.aw.ready
        perfCollector.io.lsu_axi_w_fire       := lsu.io.memBus.w.valid && lsu.io.memBus.w.ready
        perfCollector.io.lsu_axi_r_fire       := lsu.io.memBus.r.valid && lsu.io.memBus.r.ready
        perfCollector.io.lsu_axi_b_fire       := lsu.io.memBus.b.valid && lsu.io.memBus.b.ready
        perfCollector.io.lsu_aw_ready         := lsu.io.memBus.aw.ready
        perfCollector.io.lsu_w_ready          := lsu.io.memBus.w.ready
        perfCollector.io.wb_comp_branch_enable   := wbu.io.prevStage.bits.compBranchEnable
        // I-cache perf observation — DPI-only, no hardware in synthesis path
        perfCollector.io.icache_request_fire     := icache.perfObs.get.request_fire
        perfCollector.io.icache_hit              := icache.perfObs.get.hit
        perfCollector.io.icache_miss             := icache.perfObs.get.miss
        perfCollector.io.icache_bypass           := icache.perfObs.get.bypass
        perfCollector.io.icache_lower_req_fire   := icache.perfObs.get.lower_req_fire
        perfCollector.io.icache_lower_resp_fire  := icache.perfObs.get.lower_resp_fire
        perfCollector.io.icache_refill_fire      := icache.perfObs.get.refill_fire
        perfCollector.io.icache_response_fire    := icache.perfObs.get.response_fire
        perfCollector.io.icache_response_blocked := icache.perfObs.get.response_blocked
        perfCollector.io.icache_refill_word_fire        := icache.perfObs.get.refill_word_fire
        perfCollector.io.icache_refill_transaction_fire := icache.perfObs.get.refill_transaction_fire
        perfCollector.io.icache_miss_wait_cycle         := icache.perfObs.get.miss_wait_cycle
        perfCollector.io.icache_bypass_wait_cycle       := icache.perfObs.get.bypass_wait_cycle
        perfCollector.io.icache_total_miss_time_cycle   := icache.perfObs.get.total_miss_time_cycle
        generalDPI.perf <> perfCollector.io.perf

        val dpi = Module(new GeneralDPIAdapter(xLen))
        dpi.io <> generalDPI
    }
}

class NPCStandalone(val xLen: Int) extends Module {
    override def desiredName: String = "ysyx_25070190"

    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val interrupt = Input(Bool())
    })

    val executing = RegInit(false.B)
    val pc_r = RegInit(0x80000000L.U(xLen.W))

    val gprFile = Module(new GeneralPurposeRegisterFile(xLen))
    val csrFile = Module(new ControlAndStatusRegisterFile(xLen))
    GeneralPurposeRegisterFile.defaultValuesForMaster(gprFile)
    ControlAndStatusRegisterFile.defaultValuesForMaster(csrFile)

    val lsu = Module(new LoadAndStoreUnit(xLen))
    if (Config.enableDPI) {
        val mem = Module(new StandaloneMemDPI(xLen))
        mem.io.clock := clock
        mem.io.reset := reset
        lsu.io.memBus <> mem.io.axi
    } else {
        val mem = Module(new SimpleAXI4RAM(xLen))
        lsu.io.memBus <> mem.io.axi
    }

    val clint = Module(new CLINT(xLen))

    val ifu = Module(new IFUnit(xLen))
    ifu.io.executionInfo.bits.pc := pc_r
    when(!executing && ifu.io.nextStage.fire) {
        executing := true.B
    }
    ifu.io.executionInfo.valid := !reset.asBool && !executing
    val icache = Module(new InstructionCache(xLen, Config.icacheConfig))
    icache.io.cpuReq <> ifu.io.lsuIfetchReq
    icache.io.cpuResp <> ifu.io.lsuIfetchResp
    icache.io.lowerReq <> lsu.io.ifetchReq
    icache.io.lowerResp <> lsu.io.ifetchResp

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

    val memu = Module(new MEMUnit(xLen))
    DecoupledIOConnect(exu.io.nextStage, memu.io.prevStage, DecoupledIOConnect.Pipeline)
    lsu.io.memReq <> memu.io.lsuMemReq
    lsu.io.memResp <> memu.io.lsuMemResp
    memu.io.clintBus <> clint.io.bus

    val wbu = Module(new WBUnit(xLen))
    DecoupledIOConnect(memu.io.nextStage, wbu.io.prevStage, DecoupledIOConnect.Pipeline)
    when(wbu.io.working) {
        gprFile.io.writePort <> wbu.io.gprWritePort
        csrFile.io.writePort1 <> wbu.io.csrWritePort1
        csrFile.io.writePort2 <> wbu.io.csrWritePort2
    }.otherwise {
    }

    when(wbu.io.done) {
        pc_r := wbu.io.pcTargetOut
        executing := false.B
    }

    if (Config.enableDPI) {
        val generalDPI = Wire(new GeneralDPIBundle(xLen))
        generalDPI.clock := clock
        generalDPI.reset := reset
        generalDPI.core.pc := pc_r
        generalDPI.core.halt := idu.dpi.get.inst === "h00100073".U(32.W)
        generalDPI.core.executing := executing
        generalDPI.core.ifuInputValid := !executing
        generalDPI.clint <> clint.dpi.get
        generalDPI.gpr <> gprFile.dpi.get
        generalDPI.csr <> csrFile.dpi.get
        generalDPI.ifu <> ifu.dpi.get
        generalDPI.idu <> idu.dpi.get
        generalDPI.exu <> exu.dpi.get
        generalDPI.memu <> memu.dpi.get
        generalDPI.wbu <> wbu.dpi.get

        // Perf signal collector — gathers semantic perf signals from stage IOs.
        // This whole DPI observation graph is only elaborated in the DPI-enabled build.
        val perfCollector = Module(new PerfSignalCollector(xLen))
        perfCollector.io.core_executing := executing
        perfCollector.io.if_working  := ifu.io.working
        perfCollector.io.id_working  := idu.io.working
        perfCollector.io.ex_working  := exu.io.working
        perfCollector.io.mem_working := memu.io.working
        perfCollector.io.wb_working  := wbu.io.working
        perfCollector.io.wb_done      := wbu.io.done
        perfCollector.io.wb_inst      := wbu.dpi.get.inst
        perfCollector.io.wb_inst_jal  := wbu.dpi.get.inst_jal
        perfCollector.io.wb_inst_jalr := wbu.dpi.get.inst_jalr
        perfCollector.io.if_nextStage_valid  := ifu.io.nextStage.valid
        perfCollector.io.if_ifetch_req_valid  := ifu.io.lsuIfetchReq.valid
        perfCollector.io.if_ifetch_req_ready  := ifu.io.lsuIfetchReq.ready
        perfCollector.io.if_ifetch_resp_valid := ifu.io.lsuIfetchResp.valid
        perfCollector.io.if_ifetch_resp_ready := ifu.io.lsuIfetchResp.ready
        perfCollector.io.mem_nextStage_valid   := memu.io.nextStage.valid
        perfCollector.io.mem_lsu_req_valid     := memu.io.lsuMemReq.valid
        perfCollector.io.mem_lsu_req_ready     := memu.io.lsuMemReq.ready
        perfCollector.io.mem_lsu_req_isWrite   := memu.io.lsuMemReq.bits.isWrite
        perfCollector.io.mem_lsu_req_addr      := memu.io.lsuMemReq.bits.addr
        perfCollector.io.mem_lsu_req_ls_type   := memu.io.lsuMemReq.bits.lsType
        perfCollector.io.mem_lsu_resp_valid    := memu.io.lsuMemResp.valid
        perfCollector.io.mem_lsu_resp_ready    := memu.io.lsuMemResp.ready
        perfCollector.io.mem_clint_ar_fire     := memu.io.clintBus.ar.valid && memu.io.clintBus.ar.ready
        perfCollector.io.wb_csr_write2_enable  := wbu.io.csrWritePort2.writeEnable
        perfCollector.io.if_entry_fire   := ifu.io.executionInfo.fire
        perfCollector.io.id_entry_fire   := idu.io.prevStage.fire
        perfCollector.io.ex_entry_fire   := exu.io.prevStage.fire
        perfCollector.io.mem_entry_fire  := memu.io.prevStage.fire
        perfCollector.io.wb_entry_fire   := wbu.io.prevStage.fire
        perfCollector.io.if_exit_fire    := ifu.io.nextStage.fire
        perfCollector.io.id_exit_fire    := idu.io.nextStage.fire
        perfCollector.io.ex_exit_fire    := exu.io.nextStage.fire
        perfCollector.io.mem_exit_fire   := memu.io.nextStage.fire
        perfCollector.io.wb_exit_fire    := wbu.io.done
        perfCollector.io.wb_gpr_write_enable  := wbu.io.gprWritePort.writeEnable
        perfCollector.io.wb_csr_write1_enable := wbu.io.csrWritePort1.writeEnable
        perfCollector.io.wb_reg_write_data_sel := wbu.io.prevStage.bits.regWriteDataSel
        perfCollector.io.wb_csr_write_data_sel := wbu.io.prevStage.bits.csrRegWriteDataSel
        perfCollector.io.idu_rs1              := idu.io.gprReadPort.readAddress1
        perfCollector.io.idu_rs2              := idu.io.gprReadPort.readAddress2
        perfCollector.io.idu_rs2_unused       := idu.io.rs2Unused
        perfCollector.io.idu_is_system_inst   := idu.io.isSystemInst
        perfCollector.io.idu_csr_read_addr    := idu.io.csrReadPort1.readAddress
        perfCollector.io.wbu_gpr_write_suppressed_x0 := wbu.io.gprWriteSuppressedX0
        perfCollector.io.wbu_csr_write_addr   := wbu.io.csrWritePort1.writeAddress
        // IFetch phase + transaction signals (from IFUnit)
        perfCollector.io.ifu_ifetch_state    := ifu.io.ifetchState
        perfCollector.io.ifu_nextStage_ready := ifu.io.nextStage.ready
        // LSU observation signals (from LoadAndStoreUnit)
        perfCollector.io.lsu_state            := lsu.io.perfState
        perfCollector.io.lsu_pending_fetch    := lsu.io.perfPendingFetch
        perfCollector.io.lsu_pending_write    := lsu.io.perfPendingWrite
        perfCollector.io.lsu_needs_byte_split := lsu.io.perfNeedsByteSplit
        perfCollector.io.lsu_pending_ls_type  := lsu.io.perfPendingLsType
        perfCollector.io.lsu_axi_ar_fire      := lsu.io.memBus.ar.valid && lsu.io.memBus.ar.ready
        perfCollector.io.lsu_axi_aw_fire      := lsu.io.memBus.aw.valid && lsu.io.memBus.aw.ready
        perfCollector.io.lsu_axi_w_fire       := lsu.io.memBus.w.valid && lsu.io.memBus.w.ready
        perfCollector.io.lsu_axi_r_fire       := lsu.io.memBus.r.valid && lsu.io.memBus.r.ready
        perfCollector.io.lsu_axi_b_fire       := lsu.io.memBus.b.valid && lsu.io.memBus.b.ready
        perfCollector.io.lsu_aw_ready         := lsu.io.memBus.aw.ready
        perfCollector.io.lsu_w_ready          := lsu.io.memBus.w.ready
        perfCollector.io.wb_comp_branch_enable := wbu.io.prevStage.bits.compBranchEnable
        perfCollector.io.icache_request_fire    := icache.perfObs.get.request_fire
        perfCollector.io.icache_hit             := icache.perfObs.get.hit
        perfCollector.io.icache_miss            := icache.perfObs.get.miss
        perfCollector.io.icache_bypass          := icache.perfObs.get.bypass
        perfCollector.io.icache_lower_req_fire  := icache.perfObs.get.lower_req_fire
        perfCollector.io.icache_lower_resp_fire := icache.perfObs.get.lower_resp_fire
        perfCollector.io.icache_refill_fire     := icache.perfObs.get.refill_fire
        perfCollector.io.icache_response_fire   := icache.perfObs.get.response_fire
        perfCollector.io.icache_response_blocked := icache.perfObs.get.response_blocked
        perfCollector.io.icache_refill_word_fire        := icache.perfObs.get.refill_word_fire
        perfCollector.io.icache_refill_transaction_fire := icache.perfObs.get.refill_transaction_fire
        perfCollector.io.icache_miss_wait_cycle         := icache.perfObs.get.miss_wait_cycle
        perfCollector.io.icache_bypass_wait_cycle       := icache.perfObs.get.bypass_wait_cycle
        perfCollector.io.icache_total_miss_time_cycle   := icache.perfObs.get.total_miss_time_cycle
        generalDPI.perf <> perfCollector.io.perf

        val dpi = Module(new GeneralDPIAdapter(xLen))
        dpi.io <> generalDPI
    }
}

object Top extends App {
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

    val enableRandomDelay = args.contains("enableRandomDelay")
    val isStandalone = args.contains("standalone")
    val disableDPI = args.contains("disableDPI")

    if (isStandalone) {
        Config.integrationMode = Config.Standalone
    }
    Config.enableDPI = !disableDPI

    val icacheBlockBytes = args.collectFirst {
      case s if s.startsWith("icacheBlockBytes=") => s.drop("icacheBlockBytes=".length).toInt
    }.getOrElse(ICacheConfig().blockBytes)
    val icacheNumEntries = args.collectFirst {
      case s if s.startsWith("icacheNumEntries=") => s.drop("icacheNumEntries=".length).toInt
    }.getOrElse(ICacheConfig().numEntries)
    val icacheConfig = ICacheConfig(icacheBlockBytes, icacheNumEntries)
    Config.icacheConfig = icacheConfig

    val cs = new ChiselStage
    val modeStr = if (isStandalone) "Standalone" else "YsyxSoC"
    println(s"Emitting SystemVerilog for ProcessorCore (mode: $modeStr) with arguments:")
    println(s"  enableRandomDelay: $enableRandomDelay")
    println(s"  enableDPI: ${Config.enableDPI}")
    println(s"  icacheBlockBytes: $icacheBlockBytes")
    println(s"  icacheNumEntries: $icacheNumEntries")
    cs.execute(
        Array(
            "--target", "systemverilog",
            "--target-dir", "generated",
            "--split-verilog",
            "--firtool-option", "-lowering-options=disallowLocalVariables,disallowPackedArrays",
            "--firtool-option", "--verification-flavor=if-else-fatal"
        ),
        Seq(ChiselGeneratorAnnotation(() =>
            if (isStandalone)
                new NPCStandalone(xLen = Config.xlen)
            else
                new NPCWithSoC(xLen = Config.xlen)
        ))
    )
}
