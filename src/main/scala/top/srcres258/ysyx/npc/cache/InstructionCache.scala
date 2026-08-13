package top.srcres258.ysyx.npc.cache

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.Config
import top.srcres258.ysyx.npc.ICacheConfig
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.bus.AXI4
import top.srcres258.ysyx.npc.util.SoCMemoryRanges

class InstructionCachePerfObsBundle extends Bundle {
        val request_fire     = Output(Bool())
        val hit              = Output(Bool())
        val miss             = Output(Bool())
        val bypass           = Output(Bool())
        val lower_req_fire   = Output(Bool())
        val lower_resp_fire  = Output(Bool())
        val refill_fire      = Output(Bool())
        val response_fire    = Output(Bool())
        val response_blocked = Output(Bool())
        // ── Line-size-aware counters (T4) ──
        val refill_word_fire      = Output(Bool())  // per-word refill beat (lowerResp.fire during cacheable refill)
        val refill_transaction_fire = Output(Bool()) // full-line refill completion (same event as refill_fire, separate counter)
        val miss_wait_cycle       = Output(Bool())  // level: cache handling cacheable miss (s_send_mem_req | s_wait_mem_resp)
        val bypass_wait_cycle     = Output(Bool())  // level: cache handling bypass (!reqIsCacheable, s_send_mem_req | s_wait_mem_resp)
        val total_miss_time_cycle = Output(Bool())  // level: any non-hit response delay (state =/= s_idle && state =/= s_cpu_resp)
}

class InstructionCache(val xLen: Int, val icacheConfig: ICacheConfig = ICacheConfig()) extends Module {
    private val N_ENTRIES: Int = icacheConfig.numEntries
    private val BLOCK_BYTES: Int = icacheConfig.blockBytes
    private val WORDS_PER_LINE: Int = BLOCK_BYTES / 4
    private val OFFSET_WIDTH: Int = log2Ceil(BLOCK_BYTES)
    private val INDEX_WIDTH: Int = log2Ceil(N_ENTRIES)
    private val TAG_WIDTH: Int = xLen - INDEX_WIDTH - OFFSET_WIDTH

    private val WORD_IDX_WIDTH: Int = if (WORDS_PER_LINE > 1) log2Ceil(WORDS_PER_LINE) else 1
    private val REFILL_CNT_WIDTH: Int = if (WORDS_PER_LINE > 1) log2Ceil(WORDS_PER_LINE) else 1

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
    private val data  = Reg(Vec(N_ENTRIES, Vec(WORDS_PER_LINE, UInt(xLen.W))))

    private val s_idle :: s_send_mem_req :: s_wait_mem_resp :: s_cpu_resp :: Nil = Enum(4)
    private val state = RegInit(s_idle)

    private val reqAddr        = Reg(UInt(xLen.W))
    private val reqIsCacheable = Reg(Bool())
    private val reqIsHit       = Reg(Bool())
    private val lowerData      = Reg(UInt(xLen.W))
    private val lowerResp      = Reg(UInt(AXI4.RESP_WIDTH.W))

    private val refillWordIdx = RegInit(0.U(REFILL_CNT_WIDTH.W))
    private val refillError   = RegInit(false.B)

    private val curOffset = io.cpuReq.bits.addr(OFFSET_WIDTH - 1, 0)
    private val curIndex  = io.cpuReq.bits.addr(INDEX_WIDTH + OFFSET_WIDTH - 1, OFFSET_WIDTH)
    private val curTag    = io.cpuReq.bits.addr(xLen - 1, INDEX_WIDTH + OFFSET_WIDTH)

    private val capIndex = reqAddr(INDEX_WIDTH + OFFSET_WIDTH - 1, OFFSET_WIDTH)
    private val capTag   = reqAddr(xLen - 1, INDEX_WIDTH + OFFSET_WIDTH)
    private val selectedLine  = data(capIndex)

    private val capWordOffset = Wire(UInt(WORD_IDX_WIDTH.W))
    if (WORDS_PER_LINE > 1) {
        capWordOffset := reqAddr(OFFSET_WIDTH - 1, 2)
    } else {
        capWordOffset := 0.U
    }

    private val lineBase = reqAddr(xLen - 1, OFFSET_WIDTH) ## 0.U(OFFSET_WIDTH.W)

    // Only instruction-cacheable addresses participate in tag-lookup semantics.
    // All other fetches bypass the I-cache and may never be classified as hit or miss.
    private val requestFire  = (state === s_idle) && io.cpuReq.fire
    private val curCacheable = SoCMemoryRanges.isInstCacheable(io.cpuReq.bits.addr)
    private val curTagHit    = valid(curIndex) && tag(curIndex) === curTag
    private val curHit       = curCacheable && curTagHit
    private val curMiss      = curCacheable && !curTagHit
    private val curBypass    = !curCacheable

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
            reqIsCacheable := curCacheable
            reqIsHit       := curHit
            refillWordIdx  := 0.U
            refillError    := false.B
            when (curBypass) {
                state := s_send_mem_req
            }.elsewhen (curHit) {
                state := s_cpu_resp
            }.otherwise {
                state := s_send_mem_req
            }
        }
    }

    when (state === s_send_mem_req) {
        io.lowerReq.valid    := true.B
        io.lowerReq.bits.addr := Mux(reqIsCacheable, lineBase + (refillWordIdx << 2), reqAddr)
        when (io.lowerReq.fire) {
            state := s_wait_mem_resp
        }
    }

    when (state === s_wait_mem_resp) {
        io.lowerResp.ready := true.B
        when (io.lowerResp.fire) {
            lowerData := io.lowerResp.bits.data
            lowerResp := io.lowerResp.bits.resp

            when (!reqIsCacheable) {
                state := s_cpu_resp
            }.elsewhen (io.lowerResp.bits.resp =/= RESP_OKAY) {
                refillError := true.B
                state := s_cpu_resp
            }.otherwise {
                val updatedLine = Wire(Vec(WORDS_PER_LINE, UInt(xLen.W)))
                for (wordIdx <- 0 until WORDS_PER_LINE) {
                    updatedLine(wordIdx) := Mux(refillWordIdx === wordIdx.U, io.lowerResp.bits.data, selectedLine(wordIdx))
                }
                data(capIndex) := updatedLine
                when (refillWordIdx +& 1.U >= WORDS_PER_LINE.U) {
                    state := s_cpu_resp
                }.otherwise {
                    refillWordIdx := refillWordIdx + 1.U
                    state := s_send_mem_req
                }
            }
        }
    }

    when (state === s_cpu_resp) {
        val fromCache = reqIsHit || (reqIsCacheable && !refillError)
        val selectedWord = Mux1H(UIntToOH(capWordOffset), selectedLine)
        io.cpuResp.valid          := true.B
        io.cpuResp.bits.data      := Mux(fromCache, selectedWord, lowerData)
        io.cpuResp.bits.resp      := Mux(fromCache, RESP_OKAY, lowerResp)
        io.cpuResp.bits.cacheable := reqIsCacheable
        when (io.cpuResp.fire) {
            when (!reqIsHit && reqIsCacheable && !refillError) {
                valid(capIndex) := true.B
                tag(capIndex)   := capTag
            }
            state := s_idle
        }
    }

    assert(
        !(state === s_idle && io.cpuReq.fire) || io.cpuReq.bits.addr(1, 0) === 0.U,
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

    assert(
        !(state === s_wait_mem_resp && refillError),
        "[ICache] Refill error detected but state not yet at cpuResp"
    )

    when (requestFire) {
        assert(
            PopCount(Seq(curHit, curMiss, curBypass)) === 1.U,
            "[ICache] Request classification must be one-hot across hit/miss/bypass"
        )
        when (curCacheable) {
            assert(
                !curBypass && (curHit ^ curMiss),
                "[ICache] Cacheable request must classify as exactly one of hit or miss"
            )
        }.otherwise {
            assert(
                !curHit && !curMiss && curBypass,
                "[ICache] Non-cacheable request must classify as bypass only"
            )
        }
    }

    // Perf observation, DPI-only, no hardware in synthesis path
    if (Config.enableDPI) {
        val obs = perfObs.get

        obs.request_fire := requestFire
        obs.hit          := requestFire && curHit
        obs.miss         := requestFire && curMiss
        obs.bypass       := requestFire && curBypass

        obs.lower_req_fire  := (state === s_send_mem_req) && io.lowerReq.fire
        obs.lower_resp_fire := (state === s_wait_mem_resp) && io.lowerResp.fire

        obs.refill_fire := (state === s_cpu_resp) && io.cpuResp.fire &&
            !reqIsHit && reqIsCacheable && !refillError

        obs.response_fire := (state === s_cpu_resp) && io.cpuResp.fire

        obs.response_blocked := (state === s_cpu_resp) &&
            io.cpuResp.valid && !io.cpuResp.ready

        obs.refill_word_fire := (state === s_wait_mem_resp) && io.lowerResp.fire &&
            reqIsCacheable

        obs.refill_transaction_fire := (state === s_cpu_resp) && io.cpuResp.fire &&
            !reqIsHit && reqIsCacheable && !refillError

        obs.miss_wait_cycle := reqIsCacheable &&
            (state === s_send_mem_req || state === s_wait_mem_resp)

        obs.bypass_wait_cycle := !reqIsCacheable &&
            (state === s_send_mem_req || state === s_wait_mem_resp)

        obs.total_miss_time_cycle := state =/= s_idle && state =/= s_cpu_resp
    }
}
