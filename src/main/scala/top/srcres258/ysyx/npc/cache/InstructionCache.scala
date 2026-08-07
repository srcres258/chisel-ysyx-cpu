package top.srcres258.ysyx.npc.cache

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.Config
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.bus.AXI4
import top.srcres258.ysyx.npc.util.SoCMemoryRanges

class InstructionCachePerfObsBundle extends Bundle {
    val request_fire    = Output(Bool())
    val hit             = Output(Bool())
    val miss            = Output(Bool())
    val bypass          = Output(Bool())
    val lower_req_fire  = Output(Bool())
    val lower_resp_fire = Output(Bool())
    val refill_fire     = Output(Bool())
    val response_fire   = Output(Bool())
    val response_blocked = Output(Bool())
}

class InstructionCache(val xLen: Int) extends Module {
  private val N_ENTRIES: Int = 16
  private val INDEX_WIDTH: Int = log2Ceil(N_ENTRIES)
  private val OFFSET_WIDTH: Int = 2
  private val TAG_WIDTH: Int = xLen - INDEX_WIDTH - OFFSET_WIDTH

  private val RESP_OKAY: UInt = 0.U(AXI4.RESP_WIDTH.W)

  val io = IO(new Bundle {
    val cpuReq  = Flipped(Decoupled(Output(new LoadAndStoreUnit.IfetchReq(xLen))))
    val cpuResp = Decoupled(Output(new LoadAndStoreUnit.IfetchResp(xLen)))
    val lowerReq  = Decoupled(Output(new LoadAndStoreUnit.IfetchReq(xLen)))
    val lowerResp = Flipped(Decoupled(Output(new LoadAndStoreUnit.IfetchResp(xLen))))
  })
  val perfObs = if (Config.enableDPI) Some(IO(new InstructionCachePerfObsBundle)) else None

  private val valid = RegInit(VecInit(Seq.fill(N_ENTRIES)(false.B)))
  private val tag   = Reg(Vec(N_ENTRIES, UInt(TAG_WIDTH.W)))
  private val data  = Reg(Vec(N_ENTRIES, UInt(xLen.W)))

  private val s_idle :: s_send_mem_req :: s_wait_mem_resp :: s_cpu_resp :: Nil = Enum(4)
  private val state = RegInit(s_idle)

  private val reqAddr        = Reg(UInt(xLen.W))
  private val reqIsCacheable = Reg(Bool())
  private val reqIsHit       = Reg(Bool())
  private val lowerData      = Reg(UInt(xLen.W))
  private val lowerResp      = Reg(UInt(AXI4.RESP_WIDTH.W))

  private val curOffset = io.cpuReq.bits.addr(OFFSET_WIDTH - 1, 0)
  private val curIndex  = io.cpuReq.bits.addr(INDEX_WIDTH + OFFSET_WIDTH - 1, OFFSET_WIDTH)
  private val curTag    = io.cpuReq.bits.addr(xLen - 1, INDEX_WIDTH + OFFSET_WIDTH)

  private val capIndex = reqAddr(INDEX_WIDTH + OFFSET_WIDTH - 1, OFFSET_WIDTH)
  private val capTag   = reqAddr(xLen - 1, INDEX_WIDTH + OFFSET_WIDTH)

  private val curHit = valid(curIndex) && tag(curIndex) === curTag

  io.cpuReq.ready      := false.B
  io.cpuResp.valid     := false.B
  io.cpuResp.bits.data := DontCare
  io.cpuResp.bits.resp := DontCare
  io.cpuResp.bits.cacheable := DontCare
  io.lowerReq.valid    := false.B
  io.lowerReq.bits.addr := DontCare
  io.lowerResp.ready   := false.B

  when (state === s_idle) {
    io.cpuReq.ready := true.B
    when (io.cpuReq.fire) {
      reqAddr        := io.cpuReq.bits.addr
      reqIsCacheable := SoCMemoryRanges.isInstCacheable(io.cpuReq.bits.addr)
      reqIsHit       := curHit
      state          := Mux(curHit, s_cpu_resp, s_send_mem_req)
    }
  }

  when (state === s_send_mem_req) {
    io.lowerReq.valid    := true.B
    io.lowerReq.bits.addr := reqAddr
    when (io.lowerReq.fire) {
      state := s_wait_mem_resp
    }
  }

  when (state === s_wait_mem_resp) {
    io.lowerResp.ready := true.B
    when (io.lowerResp.fire) {
      lowerData := io.lowerResp.bits.data
      lowerResp := io.lowerResp.bits.resp
      state := s_cpu_resp
    }
  }

  when (state === s_cpu_resp) {
    io.cpuResp.valid          := true.B
    io.cpuResp.bits.data      := Mux(reqIsHit, data(capIndex), lowerData)
    io.cpuResp.bits.resp      := Mux(reqIsHit, RESP_OKAY, lowerResp)
    io.cpuResp.bits.cacheable := reqIsCacheable
    when (io.cpuResp.fire) {
      when (!reqIsHit && reqIsCacheable && lowerResp === RESP_OKAY) {
        valid(capIndex) := true.B
        tag(capIndex)   := capTag
        data(capIndex)  := lowerData
      }
      state := s_idle
    }
  }

  assert(
    !(state === s_idle && io.cpuReq.fire) || curOffset === 0.U,
    "[ICache] CPU request address not 4B-aligned"
  )

  assert(
    !(state =/= s_idle && io.cpuReq.fire),
    "[ICache] CPU request fired while cache not idle"
  )

  private val prevRespData = RegNext(io.cpuResp.bits.data, 0.U)
  private val prevRespResp = RegNext(io.cpuResp.bits.resp, 0.U)
  private val prevStateCpuResp = RegNext(state === s_cpu_resp, false.B)
  assert(
    !prevStateCpuResp ||
      (io.cpuResp.bits.data === prevRespData &&
       io.cpuResp.bits.resp === prevRespResp),
    "[ICache] cpuResp payload changed while valid asserted"
  )

  assert(
    !(state === s_wait_mem_resp && io.lowerReq.fire),
    "[ICache] Duplicate lower memory request"
  )

  // ---- Perf observation — DPI-only, no hardware in synthesis path ----
  if (Config.enableDPI) {
    val obs = perfObs.get
    val isCacheable = SoCMemoryRanges.isInstCacheable(io.cpuReq.bits.addr)

    // Event pulses
    obs.request_fire := (state === s_idle) && io.cpuReq.fire
    obs.hit          := obs.request_fire && curHit
    obs.miss         := obs.request_fire && !curHit && isCacheable
    obs.bypass       := obs.request_fire && !curHit && !isCacheable

    obs.lower_req_fire  := (state === s_send_mem_req) && io.lowerReq.fire
    obs.lower_resp_fire := (state === s_wait_mem_resp) && io.lowerResp.fire

    obs.refill_fire := (state === s_cpu_resp) && io.cpuResp.fire &&
      !reqIsHit && reqIsCacheable && lowerResp === RESP_OKAY

    obs.response_fire := (state === s_cpu_resp) && io.cpuResp.fire

    obs.response_blocked := (state === s_cpu_resp) &&
      io.cpuResp.valid && !io.cpuResp.ready
  }
}
