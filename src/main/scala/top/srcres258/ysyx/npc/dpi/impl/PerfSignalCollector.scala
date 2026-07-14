package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.Config
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
        val mem_lsu_resp_valid    = Input(Bool())
        val mem_lsu_resp_ready    = Input(Bool())
        /** CLINT 总线 AR 信道 .fire (clintBus.ar.valid && clintBus.ar.ready) */
        val mem_clint_ar_fire     = Input(Bool())

        // ---- 异常信号 ----
        /** WB 写回 CSR 端口 2 的 writeEnable (仅 ecall 时置位) */
        val wb_csr_write2_enable = Input(Bool())

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
}
