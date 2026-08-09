package top.srcres258.ysyx.npc.dpi.impl

import chisel3._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 核心性能计数子束: 处理器整体运行状态.
  *
  * 与 C++ counter 映射:
  *  running (non-reset clock) → core.cycle
  *  commitFire → core.instret
  *  busy → core.busy.cycle
  *  stall → core.stall.cycle
  */
class PerfCoreDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    /** 核心时钟有效 (非复位状态下每个周期为 1) */
    val running = Output(Bool())
    /** 本周期有指令退休 (WB done) */
    val commitFire = Output(Bool())
    /** 任何流水级正在工作 (非 idle) */
    val busy = Output(Bool())
    /** 处理器停止 (busy 但没有退休) */
    val stall = Output(Bool())
}

/**
  * 指令分类子束: 退休指令的分类信号.
  *
  * 同一周期最多一个分类为 1 (单发射).
  * 与 C++ counter 映射:
  *  alu    → inst.class.alu.count
  *  load   → inst.class.load.count
  *  store  → inst.class.store.count
  *  branch → inst.class.branch.count
  *  jal    → inst.class.jal.count
  *  jalr   → inst.class.jalr.count
  *  csr    → inst.class.csr.count
  *  muldiv → inst.class.muldiv.count (当前 CPU 不支持, 始终为 0)
  */
class PerfInstDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val alu    = Output(Bool())
    val load   = Output(Bool())
    val store  = Output(Bool())
    val branch = Output(Bool())
    val jal    = Output(Bool())
    val jalr   = Output(Bool())
    val csr    = Output(Bool())
    val muldiv = Output(Bool())
}

/**
  * 流水线状态子束: 每个阶段是否在本周期处于活跃状态 (working).
  *
  * 与 C++ counter 映射:
  *  fetch_cycle      → state.fetch.cycle
  *  decode_cycle     → state.decode.cycle
  *  execute_cycle    → state.execute.cycle
  *  memory_cycle     → state.memory.cycle
  *  writeback_cycle  → state.writeback.cycle
  */
class PerfStateDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val fetch_cycle      = Output(Bool())
    val decode_cycle     = Output(Bool())
    val execute_cycle    = Output(Bool())
    val memory_cycle     = Output(Bool())
    val writeback_cycle  = Output(Bool())
}

/**
  * 停顿原因子束: 各停顿源的逐周期指示.
  *
  * 与 C++ counter 映射:
  *  ifetch_wait_resp       → stall.ifetch.wait_resp.cycle
  *  mem_wait_resp         → stall.mem.wait_resp.cycle
  *  mem_req_blocked       → stall.mem.req_blocked.cycle
  *  structural_shared_mem → stall.structural.shared_mem.cycle
  *  muldiv_busy           → stall.muldiv.busy.cycle
  */
class PerfStallDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val ifetch_wait_resp       = Output(Bool())
    val mem_wait_resp          = Output(Bool())
    val mem_req_blocked        = Output(Bool())
    val structural_shared_mem  = Output(Bool())
    val muldiv_busy            = Output(Bool())
}

/**
  * 内存访问请求子束: 每周期 LSU 请求的触发信号.
  *
  * 与 C++ counter 映射:
  *  load_req_fire  → mem.load.req.count
  *  store_req_fire → mem.store.req.count
  *  mmio_req_fire  → mem.mmio.req.count
  */
class PerfMemDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val load_req_fire  = Output(Bool())
    val store_req_fire = Output(Bool())
    val mmio_req_fire  = Output(Bool())
}

/**
  * 异常/陷阱子束: 异常触发的逐周期指示.
  *
  * 与 C++ counter 映射:
  *  exception_fire → trap.exception.count
  */
class PerfTrapDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val exception_fire = Output(Bool())
}

/**
  * 流水级阶段生命周期子束: 各阶段入口/出口的 fire 事件.
  *
  * 捕获 Decoupled 握手完成信号, 用于观测流水级之间的数据传递边界:
  *  - entry_fire: 数据从上游成功进入本级 (prevStage.fire 或 executionInfo.fire)
  *  - exit_fire:  数据从本级成功传递给下游 (nextStage.fire 或 done)
  *
  * 与未来 C++ counter 映射 (T3+):
  *  阶段生命周期计数 → 每个 entry_fire/exit_fire 脉冲累加
  *  各阶段激活→退出的 cycle 差 → 阶段延迟分析
  */
class PerfPhaseDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    // ---- Stage entry ----
    val if_entry_fire  = Output(Bool()) // IF: executionInfo.fire
    val id_entry_fire  = Output(Bool()) // ID: prevStage.fire (from IF)
    val ex_entry_fire  = Output(Bool()) // EX: prevStage.fire (from ID)
    val mem_entry_fire = Output(Bool()) // MEM: prevStage.fire (from EX)
    val wb_entry_fire  = Output(Bool()) // WB: prevStage.fire (from MEM)

    // ---- Stage exit ----
    val if_exit_fire   = Output(Bool()) // IF: nextStage.fire (to ID)
    val id_exit_fire   = Output(Bool()) // ID: nextStage.fire (to EX)
    val ex_exit_fire   = Output(Bool()) // EX: nextStage.fire (to MEM)
    val mem_exit_fire  = Output(Bool()) // MEM: nextStage.fire (to WB)
    val wb_exit_fire   = Output(Bool()) // WB: done (retirement)
}

/**
  * 寄存器上下文子束: GPR/CSR 写事件与写回数据源/模式的分解.
  *
  * 观测寄存器写事件和字段使用 (regWriteDataSel / csrRegWriteDataSel 分解),
  * 基于 MEM_WB 阶段已有的控制信号, 不引入新的译码逻辑.
  *
  * 与未来 C++ counter 映射 (T3):
  *  gpr_wb_fire → reg.gpr.write.count
  *  csr_wb_fire → reg.csr.write.count
  *  gpr_src_*   → reg.gpr.src.{alu,dmem,imm,pc_next,bcu,csr}.count
  *  csr_mode_*  → reg.csr.mode.{rw,rs,rc,imm}.count
  */
class PerfRegDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    // ---- Register write events ----
    val gpr_wb_fire = Output(Bool())  // GPR writeback fire
    val csr_wb_fire = Output(Bool())  // CSR writeback fire (any)

    // ---- GPR writeback source decomposition (regWriteDataSel) ----
    val gpr_src_alu     = Output(Bool())  // RD_MUX_ALU
    val gpr_src_dmem    = Output(Bool())  // RD_MUX_DMEM
    val gpr_src_imm     = Output(Bool())  // RD_MUX_IMM
    val gpr_src_pc_next = Output(Bool())  // RD_MUX_PC_N
    val gpr_src_bcu     = Output(Bool())  // RD_MUX_BCU
    val gpr_src_csr     = Output(Bool())  // RD_MUX_CSR_DATA

    // ---- CSR writeback mode decomposition (csrRegWriteDataSel) ----
    val csr_mode_rw   = Output(Bool())  // CSR_RD_MUX_W (direct rs1 write)
    val csr_mode_rs   = Output(Bool())  // CSR_RD_MUX_S (bit-set)
    val csr_mode_rc   = Output(Bool())  // CSR_RD_MUX_C (bit-clear)
    val csr_mode_imm  = Output(Bool())  // CSR_RD_MUX_W_IMM / S_IMM / C_IMM
}

/**
  * GPR 利用率子束: GPR 读/写端口真实活动与语义消费分解.
  *
  * 从 IDU 退火事件 (nextStage.fire) 观测 GPR 读端口利用,
  * 从 WBU 写回事件 (done) 观测 GPR 写抑制.
  *
  * 与未来 C++ counter 映射 (T4):
  *  gpr_read_* → gpr.read.*.count
  *  gpr_write_suppressed_x0 → gpr.write.suppressed_x0.count
  */
class PerfGprDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val gpr_read_rs1               = Output(Bool())
    val gpr_read_rs2               = Output(Bool())
    val gpr_read_both              = Output(Bool())
    val gpr_read_rs1_x0            = Output(Bool())
    val gpr_read_rs2_x0            = Output(Bool())
    val gpr_read_rs1_eq_rs2        = Output(Bool())
    val gpr_read_rs2_unused        = Output(Bool())
    val gpr_read_upper16           = Output(Bool())
    val gpr_write_suppressed_x0    = Output(Bool())
}

/**
  * CSR 利用率子束: CSR 读端口并发、写入分类与地址分布.
  *
  * 从 IDU 观测 CSR 读端口并发 (3 个端口),
  * 从 WBU 观测 CSR 写分类 (normal/trap/return) 与目标地址.
  *
  * 与未来 C++ counter 映射 (T4):
  *  csr_read_port* → csr.read.port*.enable.count
  *  csr_read_concurrent_* → csr.read.concurrent_*.count
  *  csr_write_* → csr.write.*.count
  *  csr_addr_* → csr.addr.*.count
  */
class PerfCsrDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val csr_read_port1_enable       = Output(Bool())
    val csr_read_port2_enable       = Output(Bool())
    val csr_read_port3_enable       = Output(Bool())
    val csr_read_concurrent_2port   = Output(Bool())
    val csr_read_concurrent_3port   = Output(Bool())
    val csr_write_normal            = Output(Bool())
    val csr_write_trap              = Output(Bool())
    val csr_write_return            = Output(Bool())
    val csr_addr_mstatus            = Output(Bool())
    val csr_addr_mtvec              = Output(Bool())
    val csr_addr_mepc               = Output(Bool())
    val csr_addr_mcause             = Output(Bool())
    val csr_addr_mtval              = Output(Bool())
    val csr_addr_mvendorid          = Output(Bool())
    val csr_addr_marchid            = Output(Bool())
}

/**
  * IFetch 事务与相位分解子束: IFU 取指请求/响应/阶段周期.
  *
  * 捕获 IFetch 事务边界 (request/response fire) 与 IFU 状态机相位,
  * 用于分析取指延迟来源与前端的反馈式停顿.
  *
  * FSM 相位映射:
  *   s_idle                    → accept_pc       (等待 PC)
  *   s_waitData                → prepare_request (内部准备)
  *   s_sendFetchReq            → request_blocked (向 LSU 发送取指请求)
  *   s_waitResp                → wait_response   (等待 LSU 返回数据)
  *   s_wait_nextStage_ready    → response_buffered / output_blocked (数据就绪, 等待下游)
  *
  * 与 C++ counter 映射 (T5):
  *   request_fire                → ifetch.request.count
  *   lsu_req_fire                → ifetch.lsu_req.fire.count
  *   axi_ar_fire                 → ifetch.axi_ar.fire.count
  *   axi_r_fire                  → ifetch.axi_r.fire.count
  *   response_fire               → ifetch.response.fire.count
  *   consumer_ready_at_response  → ifetch.consumer_ready_at_response.count
  *   response_consumed_first_cycle → ifetch.response_consumed_first_cycle.count
  *   phase_*                     → ifetch.phase.<name>.cycle
  */
class PerfIfetchDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    // ---- IFetch 事务事件 ----
    val request_fire                  = Output(Bool())  // executionInfo.fire (新 PC 触发)
    val lsu_req_fire                  = Output(Bool())  // IFU→LSU 取指请求握手
    val axi_ar_fire                   = Output(Bool())  // IFetch 的 AXI AR 握手
    val axi_r_fire                    = Output(Bool())  // IFetch 的 AXI R 握手
    val response_fire                 = Output(Bool())  // LSU→IFU 取指响应握手
    val consumer_ready_at_response    = Output(Bool())  // 响应到达时下游 (ID) ready
    val response_consumed_first_cycle = Output(Bool())  // 响应到达后首个周期即被消费

    // ---- IFetch 相位周期 (互斥) ----
    val phase_accept_pc        = Output(Bool())  // s_idle     : 空闲, 等待新 PC
    val phase_prepare_request  = Output(Bool())  // s_waitData : 内部准备 (1 周期)
    val phase_request_blocked  = Output(Bool())  // s_sendFetchReq : 向 LSU 发送请求
    val phase_wait_response    = Output(Bool())  // s_waitResp : 等待 LSU 响应
    val phase_response_buffered = Output(Bool()) // s_wait_nextStage_ready && !nextStage.ready
    val phase_output_blocked   = Output(Bool())  // s_wait_nextStage_ready && nextStage.ready
}

/**
  * LSU 事务与相位分解子束: 访存分解, AXI 信道计数, 存储串行化观测.
  *
  * 观测 LSU 内部的 load/store 分解 (对齐/非对齐, byte/half/word),
  * AXI 信道握手, 以及 AW/W 串行化机会.
  *
  * 与 C++ counter 映射 (T5):
  *   load_byte/half/word_fire      → lsu.load.{byte,half,word}.count
  *   load_aligned/unaligned_fire   → lsu.load.{aligned,unaligned}.count
  *   store_byte/half/word_fire     → lsu.store.{byte,half,word}.count
  *   store_aligned/unaligned_fire  → lsu.store.{aligned,unaligned}.count
  *   unaligned_extra_transaction   → lsu.unaligned.extra_transaction.count
  *   axi_{ar,aw,w,r,b}_fire        → lsu.axi.{ar,aw,w,r,b}.fire.count
  *   concurrent_ready_opportunity  → lsu.store.concurrent_ready_opportunity.cycle
  *   aw_done_wait_w                → lsu.store.aw_done_wait_w.cycle
  *   w_done_wait_aw                → lsu.store.w_done_wait_aw.cycle
  */
class PerfLsuDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    // ---- Load 分解 (在 MEM→LSU req fire 时刻观测) ----
    val load_byte_fire        = Output(Bool())
    val load_half_fire        = Output(Bool())
    val load_word_fire        = Output(Bool())
    val load_aligned_fire     = Output(Bool())
    val load_unaligned_fire   = Output(Bool())

    // ---- Store 分解 (在 MEM→LSU req fire 时刻观测) ----
    val store_byte_fire       = Output(Bool())
    val store_half_fire       = Output(Bool())
    val store_word_fire       = Output(Bool())
    val store_aligned_fire    = Output(Bool())
    val store_unaligned_fire  = Output(Bool())

    // ---- 非对齐额外事务 ----
    val unaligned_extra_transaction = Output(Bool())

    // ---- AXI 信道握手 (全部事务, 非仅 MEM) ----
    val axi_ar_fire = Output(Bool())
    val axi_aw_fire = Output(Bool())
    val axi_w_fire  = Output(Bool())
    val axi_r_fire  = Output(Bool())
    val axi_b_fire  = Output(Bool())

    // ---- Store AW/W 串行化观测 ----
    val concurrent_ready_opportunity = Output(Bool())
    val aw_done_wait_w              = Output(Bool())
    val w_done_wait_aw              = Output(Bool())
}

/**
  * EX 阶段 ALU / PC 目标 / 地址生成并发子束: ALU 操作类型分解,
  * 加法器需求意图, 以及同周期并发需求.
  *
  * 从退休指令的 opcode/funct3/funct7 字段与指令分类中推导,
  * 而非读取原始 ALU/比较器选择器信号 (selector toggles).
  * 所有信号由 WB stage 退休时确定, 无 EX 阶段直接依赖.
  *
  * 与 C++ counter 映射 (T6):
  *   alu_op_{add,sub,sll,srl,sra,and,or,xor} → alu.op.{add,sub,...}.count
  *   adder_{compute,agen_ls,agen_branch,agen_auipc} → ex.adder.{compute,...}.count
  *   concurrency_{alu_only,pc_only,both} → ex.concurrency.{alu_only,...}.count
  */
class PerfExDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    // ---- ALU 操作类型分解 (8 op types) ----
    val alu_op_add  = Output(Bool())  // ADD / ADDI / AUIPC / load / store / branch
    val alu_op_sub  = Output(Bool())  // SUB
    val alu_op_sll  = Output(Bool())  // SLL / SLLI
    val alu_op_srl  = Output(Bool())  // SRL / SRLI
    val alu_op_sra  = Output(Bool())  // SRA / SRAI
    val alu_op_and  = Output(Bool())  // AND / ANDI
    val alu_op_or   = Output(Bool())  // OR / ORI
    val alu_op_xor  = Output(Bool())  // XOR / XORI

    // ---- 加法器需求意图 (adder demand by result intent) ----
    val adder_compute       = Output(Bool())  // 纯计算: 算术结果写入 GPR (ADD/SUB/ADDI types)
    val adder_agen_ls       = Output(Bool())  // 地址计算: load/store rs1+imm
    val adder_agen_branch   = Output(Bool())  // 分支目标: branch/JAL pc+imm (taken)
    val adder_agen_auipc    = Output(Bool())  // AUIPC: pc+imm → GPR

    // ---- 同周期并发需求 (same-cycle concurrency) ----
    val concurrency_alu_only  = Output(Bool())  // 仅需 ALU (计算/load/store/LUI)
    val concurrency_pc_only   = Output(Bool())  // 仅需 PC 目标 (JAL/JALR/ecall/mret)
    val concurrency_both      = Output(Bool())  // 同时需要 ALU + PC 目标 (taken branch, AUIPC)
}

/**
  * I-cache 性能子束: 独立 I-cache 的事件脉冲与状态观测.
  *
  * 对 IFU↔ICache 和 ICache↔LSU 两边的 Decoupled 事务进行观测,
  * 不修改缓存数据通路. 所有信号方向均为 Output.
  *
  * 与 C++ counter 映射 (T5):
  *   request_fire      → icache.request.count
  *   hit               → icache.hit.count
  *   miss              → icache.miss.count
  *   bypass            → icache.bypass.count
  *   lower_req_fire    → icache.lower_req.count
  *   lower_resp_fire   → icache.lower_resp.count
  *   refill_fire       → icache.refill.count
  *   response_fire     → icache.response.count
  *   response_blocked  → icache.response_blocked.cycle
  */
class PerfICacheDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    // ---- Event pulses (one-cycle, edges detected at fire) ----
    val request_fire    = Output(Bool())  // cpuReq.fire (in s_idle)
    val hit             = Output(Bool())  // request_fire && tag match
    val miss            = Output(Bool())  // request_fire && !tag match && cacheable
    val bypass          = Output(Bool())  // request_fire && !cacheable
    val lower_req_fire  = Output(Bool())  // lowerReq.fire (in s_send_mem_req)
    val lower_resp_fire = Output(Bool())  // lowerResp.fire (in s_wait_mem_resp)
    val refill_fire     = Output(Bool())  // cache line valid-bit set (in s_cpu_resp)
    val response_fire   = Output(Bool())  // cpuResp.fire (in s_cpu_resp)

    // ---- Cycle-level signals (level semantics) ----
    val response_blocked = Output(Bool()) // cpuResp.valid && !cpuResp.ready

    // ---- Line-size-aware counters (T4, indices 118+) ----
    val refill_word_fire        = Output(Bool()) // per-word refill beat
    val refill_transaction_fire = Output(Bool()) // full-line refill completion
    val miss_wait_cycle         = Output(Bool()) // level: cache handling cacheable miss
    val bypass_wait_cycle       = Output(Bool()) // level: cache handling bypass
    val total_miss_time_cycle   = Output(Bool()) // level: any non-hit response delay
}

/**
  * 性能计数器 DPI 总束.
  *
  * 聚合所有性能语义子束, 作为 `GeneralDPIBundle` 的 `perf` 字段.
  * 所有信号方向均为 Output (仅从 RTL 向 C++ 仿真环境传递).
  * DPIInline 的递归端口展开会自动将字段名展平为 `perf_core_running` 等 SV 端口名.
  */
class PerfDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val core   = new PerfCoreDPIBundle(xLen)
    val inst   = new PerfInstDPIBundle(xLen)
    val state  = new PerfStateDPIBundle(xLen)
    val stall  = new PerfStallDPIBundle(xLen)
    val mem    = new PerfMemDPIBundle(xLen)
    val trap   = new PerfTrapDPIBundle(xLen)
    val phase  = new PerfPhaseDPIBundle(xLen)
    val reg    = new PerfRegDPIBundle(xLen)
    val gpr    = new PerfGprDPIBundle(xLen)
    val csr    = new PerfCsrDPIBundle(xLen)
    val ifetch = new PerfIfetchDPIBundle(xLen)
    val lsu    = new PerfLsuDPIBundle(xLen)
    val ex     = new PerfExDPIBundle(xLen)
    val icache = new PerfICacheDPIBundle(xLen)
}
