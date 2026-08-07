package top.srcres258.ysyx.npc

import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

import top.srcres258.ysyx.npc.cache.InstructionCache

class InstructionCacheSpec extends AnyFunSpec with ChiselSim {

  private val CACHEABLE_PSRAM  = 0x80000000L
  private val CACHEABLE_SDRAM  = 0xA0000000L
  private val CACHEABLE_FLASH  = 0x30000000L
  private val NONCACHEABLE_UART = 0x10000000L

  private val RESP_OKAY: Int  = 0
  private val RESP_SLVERR: Int = 2
  private val RESP_DECERR: Int = 3

  private def issueRequest(dut: InstructionCache, addr: Long): Unit = {
    dut.io.cpuReq.valid.poke(true)
    dut.io.cpuReq.bits.addr.poke(addr)
    dut.io.cpuReq.ready.expect(true)
    dut.clock.step(1)
  }

  private def issueRequestNoExpect(dut: InstructionCache, addr: Long): Unit = {
    dut.io.cpuReq.valid.poke(true)
    dut.io.cpuReq.bits.addr.poke(addr)
    dut.clock.step(1)
  }

  private def respondLower(
    dut: InstructionCache, data: Long, resp: Int, cacheable: Boolean
  ): Unit = {
    dut.io.lowerResp.valid.poke(true)
    dut.io.lowerResp.bits.data.poke(data)
    dut.io.lowerResp.bits.resp.poke(resp)
    dut.io.lowerResp.bits.cacheable.poke(cacheable)
    dut.clock.step(1)
    dut.clock.step(1)
  }

  private def acceptCpuResp(dut: InstructionCache): Unit = {
    dut.io.cpuResp.ready.poke(true)
    dut.clock.step(1)
  }

  describe("InstructionCache") {

    it("cold miss refills cache and subsequent access hits") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_PSRAM)

        dut.io.lowerReq.valid.expect(true)
        dut.io.lowerReq.bits.addr.expect(CACHEABLE_PSRAM)

        respondLower(dut, 0xCAFEBABEL, RESP_OKAY, cacheable = true)

        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xCAFEBABEL)
        dut.io.cpuResp.bits.resp.expect(RESP_OKAY)
        dut.io.cpuResp.bits.cacheable.expect(true)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_PSRAM)

        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xCAFEBABEL)
        dut.io.cpuResp.bits.resp.expect(RESP_OKAY)
        dut.io.cpuResp.bits.cacheable.expect(true)
        dut.io.lowerReq.valid.expect(false)
      }
    }

    it("conflict replaces entry on same-index different-tag") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_PSRAM)
        respondLower(dut, 0xAAAAAAAAL, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_SDRAM)
        dut.io.lowerReq.valid.expect(true)
        respondLower(dut, 0xBBBBBBBBL, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_PSRAM)
        dut.io.lowerReq.valid.expect(true)
      }
    }

    it("separate indices are cached independently") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_FLASH)
        respondLower(dut, 0x11111111L, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        val index1Addr = CACHEABLE_PSRAM + 4
        issueRequest(dut, index1Addr)
        dut.io.lowerReq.valid.expect(true)
        respondLower(dut, 0x22222222L, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_FLASH)
        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0x11111111L)
        dut.io.lowerReq.valid.expect(false)
      }
    }

    it("bypass does not refill for non-cacheable addresses") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, NONCACHEABLE_UART)
        dut.io.lowerReq.valid.expect(true)
        respondLower(dut, 0xFEEDFACEL, RESP_OKAY, cacheable = false)

        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xFEEDFACEL)
        dut.io.cpuResp.bits.cacheable.expect(false)
        acceptCpuResp(dut)

        issueRequest(dut, NONCACHEABLE_UART)
        dut.io.lowerReq.valid.expect(true)
      }
    }

    it("reset invalidates all valid bits") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_PSRAM)
        respondLower(dut, 0xDEADBEEFL, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        dut.io.cpuReq.valid.poke(false)

        dut.reset.poke(true)
        dut.clock.step(1)
        dut.reset.poke(false)
        dut.clock.step(1)

        issueRequest(dut, CACHEABLE_PSRAM)
        dut.io.lowerReq.valid.expect(true)
      }
    }

    it("backpressure on lowerReq stalls the miss path") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(false)

        issueRequestNoExpect(dut, CACHEABLE_PSRAM)

        dut.io.lowerReq.valid.expect(true)
        dut.clock.step(1)
        dut.io.lowerReq.valid.expect(true)

        dut.io.lowerReq.ready.poke(true)
        dut.clock.step(1)

        respondLower(dut, 0xBBBBBBBBL, RESP_OKAY, cacheable = true)

        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xBBBBBBBBL)
      }
    }

    it("backpressure on cpuResp holds data stable") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_PSRAM)
        respondLower(dut, 0xCAFECAFEL, RESP_OKAY, cacheable = true)

        dut.io.cpuResp.ready.poke(false)
        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xCAFECAFEL)
        dut.clock.step(1)
        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xCAFECAFEL)

        acceptCpuResp(dut)
        dut.io.cpuReq.ready.expect(true)
      }
    }

    it("error response is not cached even for cacheable addresses") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_PSRAM)
        respondLower(dut, 0xBADDBADDL, RESP_SLVERR, cacheable = true)

        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xBADDBADDL)
        dut.io.cpuResp.bits.resp.expect(RESP_SLVERR)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_PSRAM)
        dut.io.lowerReq.valid.expect(true)
      }
    }

    it("DECERR response is forwarded and not cached") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_FLASH)
        respondLower(dut, 0x00000000L, RESP_DECERR, cacheable = true)

        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.resp.expect(RESP_DECERR)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_FLASH)
        dut.io.lowerReq.valid.expect(true)
      }
    }

    it("bypass then cacheable same index preserves cache isolation") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, NONCACHEABLE_UART)
        respondLower(dut, 0x11111111L, RESP_OKAY, cacheable = false)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_PSRAM)
        dut.io.lowerReq.valid.expect(true)
        respondLower(dut, 0xCAFEBABEL, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xCAFEBABEL)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_PSRAM)
        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xCAFEBABEL)
        dut.io.lowerReq.valid.expect(false)
      }
    }

    it("second request while miss in-flight is not accepted") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)

        issueRequestNoExpect(dut, CACHEABLE_PSRAM)
        dut.io.cpuReq.ready.expect(false)
        dut.io.cpuReq.valid.poke(false)
        dut.clock.step(1)
      }
    }

    it("hit path does not issue lower memory request") {
      simulate(new InstructionCache(32)) { dut =>
        dut.clock.step(1)
        dut.io.lowerReq.ready.poke(true)

        issueRequest(dut, CACHEABLE_PSRAM)
        respondLower(dut, 0xABCDABCDL, RESP_OKAY, cacheable = true)
        dut.io.cpuResp.valid.expect(true)
        acceptCpuResp(dut)

        issueRequest(dut, CACHEABLE_PSRAM)
        dut.io.lowerReq.valid.expect(false)
        dut.io.cpuResp.valid.expect(true)
        dut.io.cpuResp.bits.data.expect(0xABCDABCDL)
      }
    }
  }
}
