package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.Config
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.util.MemoryRange
import top.srcres258.ysyx.npc.util.SoCMemoryRanges

/**
  * 性能信号采集器.
  *
  * 从各流水级模块的 IO 信号中汇聚语义化的性能指标,
  * 不修改任何数据通路逻辑. 所有输出均为 Output 方向,
  * 供 GeneralDPIBundle.perf 使用.
  *
  * 指令分类逻辑通过解码退休指令的 opcode/funct3 字段实现,
  * 仅复用 RV32I 的 opcode 常数值, 不依赖 ControlUnit 模块.
  */
class PerfSignalCollector(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    private val socPeripheralNames = Set(
        "uart",
        "gpio",
        "keyboard",
        "vga",
        "spi_controller"
    )

    private val socPeripheralRanges: Seq[MemoryRange] =
        SoCMemoryRanges.DEVICES.collect {
            case (name, range) if socPeripheralNames.contains(name) => range
        }

    // ---- RV32I opcode 常量 (局部定义, 不引入额外模块依赖) ----
    private val OP_R_TYPE             = "b0110011".U(7.W)

    // ---- RD_MUX 选择常量 (局部定义, 与 ControlUnit 保持一致) ----
    private val RD_MUX_DMEM    = 0.U(3.W)
    private val RD_MUX_ALU     = 1.U(3.W)
    private val RD_MUX_BCU     = 2.U(3.W)
    private val RD_MUX_IMM     = 3.U(3.W)
    private val RD_MUX_PC_N    = 4.U(3.W)
    private val RD_MUX_CSR_DATA = 5.U(3.W)

    // ---- CSR_RD_MUX 选择常量 (局部定义, 与 ControlUnit 保持一致) ----
    private val CSR_RD_MUX_C      = 0.U(3.W)
    private val CSR_RD_MUX_S      = 1.U(3.W)
    private val CSR_RD_MUX_W      = 2.U(3.W)
    private val CSR_RD_MUX_C_IMM  = 3.U(3.W)
    private val CSR_RD_MUX_S_IMM  = 4.U(3.W)
    private val CSR_RD_MUX_W_IMM  = 5.U(3.W)
    private val OP_B_TYPE             = "b1100011".U(7.W)
    private val OP_S_TYPE             = "b0100011".U(7.W)
    private val OP_I_JALR_TYPE        = "b1100111".U(7.W)
    private val OP_I_LOAD_TYPE        = "b0000011".U(7.W)
    private val OP_I_ALU_TYPE         = "b0010011".U(7.W)
    private val OP_I_SYSTEM_TYPE      = "b1110011".U(7.W)
    private val OP_U_LUI_TYPE         = "b0110111".U(7.W)
    private val OP_U_AUIPC_TYPE       = "b0010111".U(7.W)
    private val OP_J_TYPE             = "b1101111".U(7.W)

    val io = IO(new Bundle {
        // ---- 核心级信号 ----
        /** 处理器 executing 寄存器值 (来自 Top 的 executing) */
        val core_executing = Input(Bool())

        // ---- 流水级 working 信号 (stage.io.working) ----
        val if_working  = Input(Bool())
        val id_working  = Input(Bool())
        val ex_working  = Input(Bool())
        val mem_working = Input(Bool())
        val wb_working  = Input(Bool())

        // ---- WB 退休信号 ----
        /** WB 阶段完成 (wbu.io.done) */
        val wb_done      = Input(Bool())
        /** 退休指令的原始指令字 (wbu.io.dpi.inst) */
        val wb_inst      = Input(UInt(xLen.W))
        /** 退休指令是否是 jal (wbu.io.dpi.inst_jal) */
        val wb_inst_jal  = Input(Bool())
        /** 退休指令是否是 jalr (wbu.io.dpi.inst_jalr) */
        val wb_inst_jalr = Input(Bool())

        // ---- IF 停顿/状态信号 ----
        val if_nextStage_valid  = Input(Bool())
        val if_ifetch_req_valid  = Input(Bool())
        val if_ifetch_req_ready  = Input(Bool())
        val if_ifetch_resp_valid = Input(Bool())
        val if_ifetch_resp_ready = Input(Bool())

        // ---- MEM 停顿/内存访问信号 ----
        val mem_nextStage_valid   = Input(Bool())
        val mem_lsu_req_valid     = Input(Bool())
        val mem_lsu_req_ready     = Input(Bool())
        val mem_lsu_req_isWrite   = Input(Bool())
        val mem_lsu_req_addr      = Input(UInt(xLen.W))
        val mem_lsu_req_ls_type   = Input(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
        val mem_lsu_resp_valid    = Input(Bool())
        val mem_lsu_resp_ready    = Input(Bool())
        /** CLINT 总线 AR 信道 .fire (clintBus.ar.valid && clintBus.ar.ready) */
        val mem_clint_ar_fire     = Input(Bool())

        // ---- 异常信号 ----
        /** WB 写回 CSR 端口 2 的 writeEnable (仅 ecall 时置位) */
        val wb_csr_write2_enable = Input(Bool())

        // ---- 阶段生命周期信号 (entry/exit fire) ----
        val if_entry_fire  = Input(Bool())
        val id_entry_fire  = Input(Bool())
        val ex_entry_fire  = Input(Bool())
        val mem_entry_fire = Input(Bool())
        val wb_entry_fire  = Input(Bool())
        val if_exit_fire   = Input(Bool())
        val id_exit_fire   = Input(Bool())
        val ex_exit_fire   = Input(Bool())
        val mem_exit_fire  = Input(Bool())
        val wb_exit_fire   = Input(Bool())

        // ---- 寄存器上下文信号 (GPR/CSR write events + field-use) ----
        /** WB done 时的 GPR 写使能 (wbu.io.gprWritePort.writeEnable) */
        val wb_gpr_write_enable  = Input(Bool())
        /** WB done 时的 CSR 写端口 1 使能 (wbu.io.csrWritePort1.writeEnable) */
        val wb_csr_write1_enable = Input(Bool())
        /** 写回数据选择 (3-bit, 来自 MEM_WB bundle 的 regWriteDataSel) */
        val wb_reg_write_data_sel = Input(UInt(3.W))
        /** CSR 写回数据选择 (3-bit, 来自 MEM_WB bundle 的 csrRegWriteDataSel) */
        val wb_csr_write_data_sel = Input(UInt(3.W))

        // ---- GPR/CSR 利用率信号 (来自 IDU/WBU) ----
        /** IDU GPR 读端口 rs1 字段 (5-bit) */
        val idu_rs1 = Input(UInt(5.W))
        /** IDU GPR 读端口 rs2 字段 (5-bit) */
        val idu_rs2 = Input(UInt(5.W))
        /** IDU: 指令语义上不使用 rs2 (来自 idu.io.rs2Unused) */
        val idu_rs2_unused = Input(Bool())
        /** IDU: 指令 opcode == SYSTEM (0x73) (来自 idu.io.isSystemInst) */
        val idu_is_system_inst = Input(Bool())
        /** IDU CSR 读端口 1 地址 (12-bit, 来自 idu.csrReadPort1.readAddress) */
        val idu_csr_read_addr = Input(UInt(12.W))
        /** WBU: GPR 写因 rd==x0 被抑制 (来自 wbu.io.gprWriteSuppressedX0) */
        val wbu_gpr_write_suppressed_x0 = Input(Bool())
        /** WBU CSR 写端口 1 地址 (12-bit, 来自 wbu.csrWritePort1.writeAddress) */
        val wbu_csr_write_addr = Input(UInt(12.W))

        // ---- IFetch 相位/事务信号 (来自 IFUnit) ----
        /** IFU FSM state encoding (3-bit: s_idle=0 .. s_wait_nextStage_ready=4) */
        val ifu_ifetch_state = Input(UInt(3.W))
        /** IFU nextStage.ready (下游 ID 是否可接收数据) */
        val ifu_nextStage_ready = Input(Bool())

        // ---- LSU 观测信号 (来自 LoadAndStoreUnit) ----
        /** LSU FSM state encoding (4-bit, 12 states) */
        val lsu_state = Input(UInt(4.W))
        /** LSU pendingIsFetch (当前事务是否为取指) */
        val lsu_pending_fetch = Input(Bool())
        /** LSU AXI AR 信道 .fire (valid && ready) */
        val lsu_axi_ar_fire = Input(Bool())
        /** LSU AXI AW 信道 .fire */
        val lsu_axi_aw_fire = Input(Bool())
        /** LSU AXI W 信道 .fire */
        val lsu_axi_w_fire = Input(Bool())
        /** LSU AXI R 信道 .fire */
        val lsu_axi_r_fire = Input(Bool())
        /** LSU AXI B 信道 .fire */
        val lsu_axi_b_fire = Input(Bool())
        /** LSU io.memBus.aw.ready (从设备 AW 信道就绪) */
        val lsu_aw_ready = Input(Bool())
        /** LSU io.memBus.w.ready (从设备 W 信道就绪) */
        val lsu_w_ready = Input(Bool())

        // ---- EX 阶段并发信号 (来自 MEM_WB bundle) ----
        /** WB 退休指令的比较器分支使能 (compBranchEnable, 来自 wbu.io.prevStage.bits) */
        val wb_comp_branch_enable = Input(Bool())

        // ---- I-cache perf observation signals (from InstructionCache.perfObs) ----
        val icache_request_fire     = Input(Bool())
        val icache_hit              = Input(Bool())
        val icache_miss             = Input(Bool())
        val icache_bypass           = Input(Bool())
        val icache_lower_req_fire   = Input(Bool())
        val icache_lower_resp_fire  = Input(Bool())
        val icache_refill_fire      = Input(Bool())
        val icache_response_fire    = Input(Bool())
        val icache_response_blocked = Input(Bool())
        // Line-size-aware (T4)
        val icache_refill_word_fire        = Input(Bool())
        val icache_refill_transaction_fire = Input(Bool())
        val icache_miss_wait_cycle         = Input(Bool())
        val icache_bypass_wait_cycle       = Input(Bool())
        val icache_total_miss_time_cycle   = Input(Bool())

        // ---- 输出 ----
        val perf = new PerfDPIBundle(xLen)
    })

    // ================================================================
    // 1. 核心信号 (PerfCoreDPIBundle)
    // ================================================================

    /** 核心正在运行: 非复位状态下的每个时钟周期 */
    io.perf.core.running   := !reset.asBool

    /** 本周期有指令退休 */
    io.perf.core.commitFire := io.wb_done

    /** 任一流水级在工作 (非 idle) */
    io.perf.core.busy       := io.if_working  ||
                                io.id_working  ||
                                io.ex_working  ||
                                io.mem_working ||
                                io.wb_working

    /** 核心停顿: busy 但本周期无退休 */
    io.perf.core.stall      := io.perf.core.busy && !io.perf.core.commitFire

    // ================================================================
    // 2. 指令分类 (PerfInstDPIBundle) — 当 WB done 时解码 opcode
    // ================================================================
    val opcode = io.wb_inst(6, 0)
    val funct3 = io.wb_inst(14, 12)
    val funct7 = io.wb_inst(31, 25)
    val rs2    = io.wb_inst(24, 20)
    val rs1    = io.wb_inst(19, 15)
    val rd     = io.wb_inst(11, 7)

    val isALU = opcode === OP_R_TYPE ||
                opcode === OP_I_ALU_TYPE ||
                opcode === OP_U_LUI_TYPE ||
                opcode === OP_U_AUIPC_TYPE
    val isLoad   = opcode === OP_I_LOAD_TYPE
    val isStore  = opcode === OP_S_TYPE
    val isBranch = opcode === OP_B_TYPE
    // JAL/JALR 使用 wbu 给出的解码标志 (更精确)
    val isCSR = opcode === OP_I_SYSTEM_TYPE &&
                funct3 =/= 0.U(3.W)
    val isMulDiv = false.B  // 当前 CPU 不支持乘除法

    // WB done 时输出指令分类 (打一拍 RegNext 以保证与 commitFire 时序对齐)
    val wb_done_d = RegNext(io.wb_done, false.B)
    val instALU    = RegNext(isALU,    false.B)
    val instLoad   = RegNext(isLoad,   false.B)
    val instStore  = RegNext(isStore,  false.B)
    val instBranch = RegNext(isBranch, false.B)
    val instJAL    = RegNext(io.wb_inst_jal,  false.B)
    val instJALR   = RegNext(io.wb_inst_jalr, false.B)
    val instCSR    = RegNext(isCSR,    false.B)

    io.perf.inst.alu    := wb_done_d && instALU
    io.perf.inst.load   := wb_done_d && instLoad
    io.perf.inst.store  := wb_done_d && instStore
    io.perf.inst.branch := wb_done_d && instBranch
    io.perf.inst.jal    := wb_done_d && instJAL
    io.perf.inst.jalr   := wb_done_d && instJALR
    io.perf.inst.csr    := wb_done_d && instCSR
    io.perf.inst.muldiv := false.B

    // ================================================================
    // 3. 流水级状态 (PerfStateDPIBundle)
    // ================================================================
    io.perf.state.fetch_cycle      := io.if_working
    io.perf.state.decode_cycle     := io.id_working
    io.perf.state.execute_cycle    := io.ex_working
    io.perf.state.memory_cycle     := io.mem_working
    io.perf.state.writeback_cycle  := io.wb_working

    // ================================================================
    // 4. 停顿信号 (PerfStallDPIBundle)
    // ================================================================

    /**
      * stall.ifetch.wait_resp:
      * IFU 正在等待 LSU 取指响应.
      * 条件: IFU 已发起取指请求且尚未收到有效数据:
      *  ifetch_resp_ready && !ifetch_resp_valid
      * 表示 IFU 愿意接收数据但 LSU 没有数据.
      */
    io.perf.stall.ifetch_wait_resp :=
        io.if_ifetch_resp_ready && !io.if_ifetch_resp_valid

    /**
      * stall.mem.wait_resp:
      * MEM 正在等待 LSU 访存响应.
      * 条件: MEM 的 LSU resp 通道 ready 但 valid 为低.
      */
    io.perf.stall.mem_wait_resp :=
        io.mem_lsu_resp_ready && !io.mem_lsu_resp_valid

    /**
      * stall.mem.req_blocked:
      * MEM 无法将请求发送到 LSU.
      * 条件: MEM req valid 但 !ready (LSU 不接受).
      */
    io.perf.stall.mem_req_blocked :=
        io.mem_lsu_req_valid && !io.mem_lsu_req_ready

    /**
      * stall.structural.shared_mem:
      * MEM 被 IFU 阻塞 (LSU 仲裁器 IFU 优先).
      * 条件: IFU 和 MEM 都有请求, LSU 仲裁选 IFU 导致 MEM 等待.
      * 即: MEM 有请求但无法发送, 同时 IFU 也在使用 LSU.
      */
    io.perf.stall.structural_shared_mem :=
        io.mem_lsu_req_valid && !io.mem_lsu_req_ready &&
        io.if_ifetch_req_valid

    /**
      * stall.muldiv.busy:
      * 乘除法单元繁忙 (当前 RV32I CPU 无乘除法硬件, 始终为 0).
      */
    io.perf.stall.muldiv_busy := false.B

    // ================================================================
    // 5. 内存访问请求 (PerfMemDPIBundle)
    // ================================================================

    private def isInAnyRange(addr: UInt, ranges: Seq[MemoryRange]): Bool =
        ranges.map(_.isInRange(addr)).foldLeft(false.B)(_ || _)

    /** 非 store 的 LSU 请求触发: load */
    io.perf.mem.load_req_fire :=
        io.mem_lsu_req_valid && io.mem_lsu_req_ready && !io.mem_lsu_req_isWrite

    /** store 请求触发 */
    io.perf.mem.store_req_fire :=
        io.mem_lsu_req_valid && io.mem_lsu_req_ready && io.mem_lsu_req_isWrite

    /**
      * MMIO 请求触发:
      *  - CLINT 读请求 (通过 clintBus.ar.fire)
      *  - CLINT 写请求 (通过 LSU 请求, 地址在 CLINT 范围且 isWrite)
      *  - SoC 外设请求 (UART/GPIO/Keyboard/VGA/SPI, 通过 LSU 请求)
      */
    val clintAddrBase = Config.clintAddrBase.U(xLen.W)
    val clintAddrEnd  = (Config.clintAddrBase + Config.clintAddrSize).U(xLen.W)
    val isClintAddr = io.mem_lsu_req_addr >= clintAddrBase &&
                       io.mem_lsu_req_addr < clintAddrEnd
    val clintWriteFire = io.mem_lsu_req_valid && io.mem_lsu_req_ready &&
                          io.mem_lsu_req_isWrite && isClintAddr

    val socPeripheralFire = io.mem_lsu_req_valid && io.mem_lsu_req_ready &&
                            isInAnyRange(io.mem_lsu_req_addr, socPeripheralRanges)

    io.perf.mem.mmio_req_fire := io.mem_clint_ar_fire || clintWriteFire || socPeripheralFire

    // ================================================================
    // 6. 异常/陷阱 (PerfTrapDPIBundle)
    // ================================================================

    /**
      * trap.exception_fire:
      * ecall 触发: WB done 且 CSR 写端口 2 使能
      * (仅 ecall 路径会置位 wbu.io.csrWritePort2.writeEnable).
      */
    io.perf.trap.exception_fire := io.wb_done && io.wb_csr_write2_enable

    // ================================================================
    // 7. 阶段生命周期 (PerfPhaseDPIBundle) — Decoupled 握手完成信号
    // ================================================================
    io.perf.phase.if_entry_fire  := io.if_entry_fire
    io.perf.phase.id_entry_fire  := io.id_entry_fire
    io.perf.phase.ex_entry_fire  := io.ex_entry_fire
    io.perf.phase.mem_entry_fire := io.mem_entry_fire
    io.perf.phase.wb_entry_fire  := io.wb_entry_fire
    io.perf.phase.if_exit_fire   := io.if_exit_fire
    io.perf.phase.id_exit_fire   := io.id_exit_fire
    io.perf.phase.ex_exit_fire   := io.ex_exit_fire
    io.perf.phase.mem_exit_fire  := io.mem_exit_fire
    io.perf.phase.wb_exit_fire   := io.wb_exit_fire

    // ================================================================
    // 8. 寄存器上下文 (PerfRegDPIBundle) — GPR/CSR write events + field-use
    // ================================================================

    // --- Register write events ---
    // GPR 写事件: WB 退休且 regWriteEnable 置位
    io.perf.reg.gpr_wb_fire := io.wb_done && io.wb_gpr_write_enable
    // CSR 写事件: WB 退休且任意 CSR 写端口使能
    io.perf.reg.csr_wb_fire := io.wb_done &&
                                (io.wb_csr_write1_enable || io.wb_csr_write2_enable)

    // --- GPR 写回数据源分解 (regWriteDataSel) ---
    io.perf.reg.gpr_src_alu     := io.wb_done && io.wb_gpr_write_enable &&
                                    io.wb_reg_write_data_sel === RD_MUX_ALU
    io.perf.reg.gpr_src_dmem    := io.wb_done && io.wb_gpr_write_enable &&
                                    io.wb_reg_write_data_sel === RD_MUX_DMEM
    io.perf.reg.gpr_src_imm     := io.wb_done && io.wb_gpr_write_enable &&
                                    io.wb_reg_write_data_sel === RD_MUX_IMM
    io.perf.reg.gpr_src_pc_next := io.wb_done && io.wb_gpr_write_enable &&
                                    io.wb_reg_write_data_sel === RD_MUX_PC_N
    io.perf.reg.gpr_src_bcu     := io.wb_done && io.wb_gpr_write_enable &&
                                    io.wb_reg_write_data_sel === RD_MUX_BCU
    io.perf.reg.gpr_src_csr     := io.wb_done && io.wb_gpr_write_enable &&
                                    io.wb_reg_write_data_sel === RD_MUX_CSR_DATA

    // --- CSR 写回模式分解 (csrRegWriteDataSel) ---
    // CSR 写事件: csrRegWriteEnable 或 ecall 触发的 CSR 写
    val csrWriteActive = io.wb_done && (io.wb_csr_write1_enable || io.wb_csr_write2_enable)

    io.perf.reg.csr_mode_rw  := csrWriteActive &&
                                 io.wb_csr_write_data_sel === CSR_RD_MUX_W
    io.perf.reg.csr_mode_rs  := csrWriteActive &&
                                 io.wb_csr_write_data_sel === CSR_RD_MUX_S
    io.perf.reg.csr_mode_rc  := csrWriteActive &&
                                 io.wb_csr_write_data_sel === CSR_RD_MUX_C
    io.perf.reg.csr_mode_imm := csrWriteActive && (
                                 io.wb_csr_write_data_sel === CSR_RD_MUX_W_IMM ||
                                 io.wb_csr_write_data_sel === CSR_RD_MUX_S_IMM ||
                                 io.wb_csr_write_data_sel === CSR_RD_MUX_C_IMM)

    // ================================================================
    // 9. GPR 利用率 (PerfGprDPIBundle) — IDU 读端口活动 + WBU 写抑制
    // ================================================================

    val iduExitFire = io.id_exit_fire

    // GPR 读端口活动: 5-stage 标量流水线每个指令都驱动两个读端口,
    // 因此 rs1/rs2/both 都等价于 iduExitFire.
    io.perf.gpr.gpr_read_rs1        := iduExitFire
    io.perf.gpr.gpr_read_rs2        := iduExitFire
    io.perf.gpr.gpr_read_both       := iduExitFire
    io.perf.gpr.gpr_read_rs1_x0     := iduExitFire && io.idu_rs1 === 0.U
    io.perf.gpr.gpr_read_rs2_x0     := iduExitFire && io.idu_rs2 === 0.U
    io.perf.gpr.gpr_read_rs1_eq_rs2 := iduExitFire &&
                                        io.idu_rs1 === io.idu_rs2 &&
                                        io.idu_rs1 =/= 0.U
    io.perf.gpr.gpr_read_rs2_unused := iduExitFire && io.idu_rs2_unused
    // upper16: rs1 或 rs2 地址 bit4 为 1 (寄存器 x16–x31)
    io.perf.gpr.gpr_read_upper16    := iduExitFire &&
                                        (io.idu_rs1(4) || io.idu_rs2(4))
    // GPR 写抑制: regWriteEnable 但 rd==x0
    io.perf.gpr.gpr_write_suppressed_x0 := io.wb_done &&
                                            io.wbu_gpr_write_suppressed_x0

    // ================================================================
    // 10. CSR 利用率 (PerfCsrDPIBundle) — 读端口并发 + 写分类 + 地址分布
    // ================================================================

    // CSR 读端口使能:
    //   Port1: 指令驱动的 CSR 地址, 仅 SYSTEM 指令有意义
    //   Port2: 固定读 mepc (0x341), 所有指令
    //   Port3: 固定读 mtvec (0x305), 所有指令
    // 并发: 端口 2 和 3 总是活跃, 因此 concurrent_2port 在所有 IDU 退出时均为真;
    //       concurrent_3port 仅在端口 1 也活跃时 (SYSTEM 指令) 才为真.
    val csrReadActive = iduExitFire
    io.perf.csr.csr_read_port1_enable     := csrReadActive && io.idu_is_system_inst
    io.perf.csr.csr_read_port2_enable     := csrReadActive
    io.perf.csr.csr_read_port3_enable     := csrReadActive
    io.perf.csr.csr_read_concurrent_2port := csrReadActive
    io.perf.csr.csr_read_concurrent_3port := csrReadActive && io.idu_is_system_inst

    // CSR 写分类:
    //   normal: CSR 指令写 (csrRegWriteEnable, 无 ecall)
    //   trap:   ecall 写 (mepc via port1, mcause via port2)
    //   return: mret 写 — 当前设计中 mret 不写 CSR, 始终为 0
    io.perf.csr.csr_write_normal := io.wb_done && io.wb_csr_write1_enable &&
                                     !io.wb_csr_write2_enable
    io.perf.csr.csr_write_trap   := io.wb_done && io.wb_csr_write2_enable
    io.perf.csr.csr_write_return := false.B

    // CSR 地址分布: 统计对每个真实 CSR 的访问 (写为主, 读辅助)
    // 写端口 1 负责正常 CSR 写 + ecall 的 mepc 写;
    // 写端口 2 仅在 ecall 时写 mcause (0x342).
    val wbCsrWrFire = io.wb_done &&
                       (io.wb_csr_write1_enable || io.wb_csr_write2_enable)
    val csrAddrHit = (target: Int) => io.wbu_csr_write_addr === target.U(12.W)

    io.perf.csr.csr_addr_mstatus   := wbCsrWrFire && csrAddrHit(0x300)
    io.perf.csr.csr_addr_mtvec     := wbCsrWrFire && csrAddrHit(0x305)
    io.perf.csr.csr_addr_mepc      := wbCsrWrFire && csrAddrHit(0x341)
    io.perf.csr.csr_addr_mcause    := (wbCsrWrFire && csrAddrHit(0x342)) ||
                                       (io.wb_done && io.wb_csr_write2_enable)
    io.perf.csr.csr_addr_mtval     := wbCsrWrFire && csrAddrHit(0x343)
    // mvendorid / marchid 为只读 CSR, 不会有写事件
    io.perf.csr.csr_addr_mvendorid := false.B
    io.perf.csr.csr_addr_marchid   := false.B

    // ================================================================
    // 11. IFetch 事务与相位分解 (PerfIfetchDPIBundle)
    // ================================================================

    // ---- IFetch 事务事件 ----
    val ifetchReqFire  = io.if_ifetch_req_valid && io.if_ifetch_req_ready
    val ifetchRespFire = io.if_ifetch_resp_valid && io.if_ifetch_resp_ready
    // AXI AR/R channel fires attributed to IFetch (pendingIsFetch asserted in LSU)
    val ifetchAxiArFire = io.lsu_axi_ar_fire && io.lsu_pending_fetch
    val ifetchAxiRFire  = io.lsu_axi_r_fire  && io.lsu_pending_fetch

    io.perf.ifetch.request_fire   := io.if_entry_fire          // executionInfo.fire
    io.perf.ifetch.lsu_req_fire   := ifetchReqFire
    io.perf.ifetch.axi_ar_fire    := ifetchAxiArFire
    io.perf.ifetch.axi_r_fire     := ifetchAxiRFire
    io.perf.ifetch.response_fire  := ifetchRespFire

    // consumer_ready_at_response: 响应到达时下游 ID 已 ready
    io.perf.ifetch.consumer_ready_at_response := ifetchRespFire && io.ifu_nextStage_ready

    // response_consumed_first_cycle: 响应到达后首个周期即被下游消费
    // IFU FSM: resp.fire → s_wait_nextStage_ready → 下一周期 nextStage.valid=1
    // 如果下一周期 nextStage.fire, 则说明首个周期即被消费
    val ifetchRespArrivedLastCycle = RegNext(ifetchRespFire, false.B)
    val ifetchFirstCycleConsumed = ifetchRespArrivedLastCycle &&
                                    io.ifu_nextStage_ready &&
                                    (io.ifu_ifetch_state === 4.U)  // s_wait_nextStage_ready=4
    io.perf.ifetch.response_consumed_first_cycle := ifetchFirstCycleConsumed

    // ---- IFetch 相位周期 (互斥, 覆盖 IFU 所有状态) ----
    // IFU FSM encoding: s_idle=0  s_waitData=1  s_sendFetchReq=2  s_waitResp=3  s_wait_nextStage_ready=4
    val ifSt = io.ifu_ifetch_state
    io.perf.ifetch.phase_accept_pc        := ifSt === 0.U  // s_idle
    io.perf.ifetch.phase_prepare_request  := ifSt === 1.U  // s_waitData
    io.perf.ifetch.phase_request_blocked  := ifSt === 2.U  // s_sendFetchReq
    io.perf.ifetch.phase_wait_response    := ifSt === 3.U  // s_waitResp
    // s_wait_nextStage_ready=4: split by downstream readiness
    io.perf.ifetch.phase_response_buffered := ifSt === 4.U && io.ifu_nextStage_ready
    io.perf.ifetch.phase_output_blocked    := ifSt === 4.U && !io.ifu_nextStage_ready

    // ================================================================
    // 12. LSU 事务与相位分解 (PerfLsuDPIBundle)
    // ================================================================

    // ---- LSU Load/Store 分解 (在 MEM→LSU 请求 fire 时观测) ----
    val memReqFire = io.mem_lsu_req_valid && io.mem_lsu_req_ready
    val memReqIsLoad  = memReqFire && !io.mem_lsu_req_isWrite
    val memReqIsStore = memReqFire && io.mem_lsu_req_isWrite

    // 直接用本次 MEM→LSU 请求的字段分类，避免读 LSU 内部 pending 寄存器的陈旧值。
    val reqLsType      = io.mem_lsu_req_ls_type
    val reqByteOffset  = io.mem_lsu_req_addr(1, 0)
    val reqIsWordLoad  = reqLsType === LoadAndStoreUnit.LS_L_W.U
    val reqIsWordStore = reqLsType === LoadAndStoreUnit.LS_S_W.U
    val reqNeedsSplit  = (reqIsWordLoad || reqIsWordStore) && reqByteOffset =/= 0.U

    val isByteLoad  = reqLsType === LoadAndStoreUnit.LS_L_B.U  || reqLsType === LoadAndStoreUnit.LS_L_BU.U
    val isHalfLoad  = reqLsType === LoadAndStoreUnit.LS_L_H.U  || reqLsType === LoadAndStoreUnit.LS_L_HU.U
    val isWordLoad  = reqLsType === LoadAndStoreUnit.LS_L_W.U
    val isByteStore = reqLsType === LoadAndStoreUnit.LS_S_B.U
    val isHalfStore = reqLsType === LoadAndStoreUnit.LS_S_H.U
    val isWordStore = reqLsType === LoadAndStoreUnit.LS_S_W.U

    io.perf.lsu.load_byte_fire       := memReqIsLoad  && isByteLoad
    io.perf.lsu.load_half_fire       := memReqIsLoad  && isHalfLoad
    io.perf.lsu.load_word_fire       := memReqIsLoad  && isWordLoad
    io.perf.lsu.load_aligned_fire    := memReqIsLoad  && !reqNeedsSplit
    io.perf.lsu.load_unaligned_fire  := memReqIsLoad  && reqNeedsSplit
    io.perf.lsu.store_byte_fire      := memReqIsStore && isByteStore
    io.perf.lsu.store_half_fire      := memReqIsStore && isHalfStore
    io.perf.lsu.store_word_fire      := memReqIsStore && isWordStore
    io.perf.lsu.store_aligned_fire   := memReqIsStore && !reqNeedsSplit
    io.perf.lsu.store_unaligned_fire := memReqIsStore && reqNeedsSplit

    // ---- LSU AXI 信道握手 (全部事务) ----
    io.perf.lsu.axi_ar_fire := io.lsu_axi_ar_fire
    io.perf.lsu.axi_aw_fire := io.lsu_axi_aw_fire
    io.perf.lsu.axi_w_fire  := io.lsu_axi_w_fire
    io.perf.lsu.axi_r_fire  := io.lsu_axi_r_fire
    io.perf.lsu.axi_b_fire  := io.lsu_axi_b_fire

    // ---- 非对齐额外事务: split 状态中首个事务之后的 AXI 信道握手 ----
    // LSU FSM: s_split_read_ar=7  s_split_read_r=8  s_split_write_aw=9  s_split_write_w=10  s_split_write_b=11
    val lsuStateIsSplit = io.lsu_state >= 7.U && io.lsu_state <= 11.U
    val justExitedSplit = !lsuStateIsSplit && RegNext(lsuStateIsSplit, false.B)
    // 跟踪 split 序列中的首个事务
    val splitFirstSeen = RegInit(false.B)
    when (justExitedSplit) {
        splitFirstSeen := false.B
    }.elsewhen (lsuStateIsSplit && !splitFirstSeen &&
                 (io.lsu_axi_ar_fire || io.lsu_axi_aw_fire)) {
        splitFirstSeen := true.B
    }
    // 首个事务之后的任何 split 状态 AXI 信道握手均为 "extra"
    val splitChannelFire = lsuStateIsSplit &&
                            (io.lsu_axi_ar_fire || io.lsu_axi_aw_fire || io.lsu_axi_w_fire)
    io.perf.lsu.unaligned_extra_transaction := splitChannelFire && splitFirstSeen

    // ---- Store AW/W 串行化观测 ----
    // concurrent_ready_opportunity: AW 和 W 信道同时 ready, 但 LSU 串行化它们
    // 这发生在 s_write_aw 或 s_split_write_aw 状态下, 当 w.ready 也为真时
    val lsuInWriteAddrState = io.lsu_state === 3.U || io.lsu_state === 9.U  // s_write_aw=3  s_split_write_aw=9
    io.perf.lsu.concurrent_ready_opportunity := lsuInWriteAddrState &&
                                                io.lsu_aw_ready && io.lsu_w_ready
    // aw_done_wait_w: AW 已完成但还在等待 W (s_write_w 或 s_split_write_w 状态)
    val lsuInWriteDataState = io.lsu_state === 4.U || io.lsu_state === 10.U  // s_write_w=4  s_split_write_w=10
    io.perf.lsu.aw_done_wait_w := lsuInWriteDataState
    // w_done_wait_aw: W 已完成但在 split 序列中等待下一个 AW
    // 发生在刚离开 s_split_write_w (w.fire 完成) 进入 s_split_write_aw 时,
    // 以及刚离开 s_split_write_b (b.fire 完成) 进入 s_split_write_aw 时
    val lsuEnteringSplitAwFromW = io.lsu_state === 9.U &&  // s_split_write_aw=9
                                   RegNext(io.lsu_state === 10.U || io.lsu_state === 11.U, false.B)
    io.perf.lsu.w_done_wait_aw := lsuEnteringSplitAwFromW

    // ================================================================
    // 13. EX 阶段并发计数 (PerfExDPIBundle) — ALU/PC 目标/地址生成
    //    从退休指令的 opcode/funct3/funct7 推导, 而非选择器 toggles.
    // ================================================================

    // ALU 操作解码: 根据退休指令的 opcode/funct3/funct7 字段
    val isRType = opcode === OP_R_TYPE
    val isITypeAlu = opcode === OP_I_ALU_TYPE
    val isLui = opcode === OP_U_LUI_TYPE
    val isAuipc = opcode === OP_U_AUIPC_TYPE

    val funct3_000 = funct3 === 0.U(3.W)
    val funct3_001 = funct3 === 1.U(3.W)
    val funct3_100 = funct3 === 4.U(3.W)
    val funct3_101 = funct3 === 5.U(3.W)
    val funct3_110 = funct3 === 6.U(3.W)
    val funct3_111 = funct3 === 7.U(3.W)
    val funct7_00  = funct7(5) === 0.U
    val funct7_20  = funct7(5) === 1.U

    // ALU op 分类: 指令是否使用了此 ALU 操作类型 (不要求 GPR 写回)
    val decAdd = (isRType && funct3_000 && funct7_00) ||       // ADD
                 (isITypeAlu && funct3_000) ||                  // ADDI
                 isAuipc || isLoad || isStore || isBranch       // AUIPC, load, store, branch (all use ADD)
    val decSub = isRType && funct3_000 && funct7_20              // SUB
    val decSll = (isRType && funct3_001) ||                     // SLL
                 (isITypeAlu && funct3_001)                      // SLLI
    val decSrl = (isRType && funct3_101 && funct7_00) ||        // SRL
                 (isITypeAlu && funct3_101 && funct7_00)         // SRLI
    val decSra = (isRType && funct3_101 && funct7_20) ||        // SRA
                 (isITypeAlu && funct3_101 && funct7_20)         // SRAI
    val decAnd = (isRType && funct3_111) ||                     // AND
                 (isITypeAlu && funct3_111)                      // ANDI
    val decOr  = (isRType && funct3_110) ||                     // OR
                 (isITypeAlu && funct3_110)                      // ORI
    val decXor = (isRType && funct3_100) ||                     // XOR
                 (isITypeAlu && funct3_100)                      // XORI

    // RegNext into wb_done_d domain (same delay as inst classification)
    val aluOpAdd_d = RegNext(decAdd, false.B)
    val aluOpSub_d = RegNext(decSub, false.B)
    val aluOpSll_d = RegNext(decSll, false.B)
    val aluOpSrl_d = RegNext(decSrl, false.B)
    val aluOpSra_d = RegNext(decSra, false.B)
    val aluOpAnd_d = RegNext(decAnd, false.B)
    val aluOpOr_d  = RegNext(decOr,  false.B)
    val aluOpXor_d = RegNext(decXor, false.B)

    io.perf.ex.alu_op_add := wb_done_d && aluOpAdd_d
    io.perf.ex.alu_op_sub := wb_done_d && aluOpSub_d
    io.perf.ex.alu_op_sll := wb_done_d && aluOpSll_d
    io.perf.ex.alu_op_srl := wb_done_d && aluOpSrl_d
    io.perf.ex.alu_op_sra := wb_done_d && aluOpSra_d
    io.perf.ex.alu_op_and := wb_done_d && aluOpAnd_d
    io.perf.ex.alu_op_or  := wb_done_d && aluOpOr_d
    io.perf.ex.alu_op_xor := wb_done_d && aluOpXor_d

    // ---- 加法器需求意图 (adder demand by result intent) ----
    // 纯计算: 非 AUIPC/LUI 的 ALU 指令, 结果写入 GPR 且来自 ALU
    val isPureCompute = isALU && !isAuipc && !isLui &&
                         io.wb_gpr_write_enable &&
                         io.wb_reg_write_data_sel === RD_MUX_ALU
    io.perf.ex.adder_compute := RegNext(isPureCompute && io.wb_done, false.B)
    // Load/store 地址生成
    io.perf.ex.adder_agen_ls := RegNext((isLoad || isStore) && io.wb_done, false.B)
    // 分支目标: branch/JAL (taken), 即 compBranchEnable 为真或 cuJumpEnable 为真
    // 通过 wb_comp_branch_enable 和 inst_jal/inst_jalr 判断
    val decBranchTaken = isBranch && io.wb_comp_branch_enable
    io.perf.ex.adder_agen_branch := RegNext((decBranchTaken || io.wb_inst_jal ||
                                              io.wb_inst_jalr) && io.wb_done, false.B)
    // AUIPC: pc+imm → GPR
    io.perf.ex.adder_agen_auipc := RegNext(isAuipc && io.wb_done, false.B)

    // ---- 同周期并发需求 (same-cycle concurrency) ----
    // 三个类别必须互斥且完全覆盖退休指令集.
    // 仅需 PC 目标: JAL, JALR, ecall, mret (epcRecover), CSR mret
    // (CSR 指令中 mret 会置 epcRecoverEnable, 即 wb_csr_write2_enable 不涵盖 mret)
    // 简化: 非 branch/ALU/load/store 的指令
    val decPcOnly = (io.wb_inst_jal || io.wb_inst_jalr || isCSR) && !isLoad && !isStore && !isBranch
    // Both: taken branch (ALU 计算目标 + PC 目标改变) 或 AUIPC (ALU 计算 + PC 变化?)
    // AUIPC 不改变 PC target (pcNext 为默认), 故仅为 ALU only
    // taken branch: ALU 计算 pc+imm → PC target consumed
    val decBoth = decBranchTaken
    // decPcOnly 和 decBoth 先选出特殊类别; decAluOnly 作为残余类别涵盖其余所有指令
    // (untaken branch, AUIPC, ecall/mret, 以及其他既非分支也非 PC-only 的指令).
    val decAluOnly = !decPcOnly && !decBoth

    io.perf.ex.concurrency_alu_only := RegNext(decAluOnly && io.wb_done, false.B)
    io.perf.ex.concurrency_pc_only  := RegNext(decPcOnly && io.wb_done, false.B)
    io.perf.ex.concurrency_both     := RegNext(decBoth && io.wb_done, false.B)

    // ================================================================
    // 14. I-cache 性能信号 (PerfICacheDPIBundle) — 直接透传
    // ================================================================
    io.perf.icache.request_fire     := io.icache_request_fire
    io.perf.icache.hit              := io.icache_hit
    io.perf.icache.miss             := io.icache_miss
    io.perf.icache.bypass           := io.icache_bypass
    io.perf.icache.lower_req_fire   := io.icache_lower_req_fire
    io.perf.icache.lower_resp_fire  := io.icache_lower_resp_fire
    io.perf.icache.refill_fire      := io.icache_refill_fire
    io.perf.icache.response_fire    := io.icache_response_fire
    io.perf.icache.response_blocked := io.icache_response_blocked
    io.perf.icache.refill_word_fire        := io.icache_refill_word_fire
    io.perf.icache.refill_transaction_fire := io.icache_refill_transaction_fire
    io.perf.icache.miss_wait_cycle         := io.icache_miss_wait_cycle
    io.perf.icache.bypass_wait_cycle       := io.icache_bypass_wait_cycle
    io.perf.icache.total_miss_time_cycle   := io.icache_total_miss_time_cycle
}
