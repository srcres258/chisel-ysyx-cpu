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
  * 性能计数器 DPI 总束.
  *
  * 聚合所有性能语义子束, 作为 `GeneralDPIBundle` 的 `perf` 字段.
  * 所有信号方向均为 Output (仅从 RTL 向 C++ 仿真环境传递).
  * DPIInline 的递归端口展开会自动将字段名展平为 `perf_core_running` 等 SV 端口名.
  */
class PerfDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val core  = new PerfCoreDPIBundle(xLen)
    val inst  = new PerfInstDPIBundle(xLen)
    val state = new PerfStateDPIBundle(xLen)
    val stall = new PerfStallDPIBundle(xLen)
    val mem   = new PerfMemDPIBundle(xLen)
    val trap  = new PerfTrapDPIBundle(xLen)
}
