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

  // Geometry sweep configs (T5)
  private val cfg8x8  = ICacheConfig(blockBytes = 8,  numEntries = 8)
  private val cfg16x4 = ICacheConfig(blockBytes = 16, numEntries = 4)
  private val cfg32x2 = ICacheConfig(blockBytes = 32, numEntries = 2)

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

    describe("ICacheConfig parameterization") {
      it("default 4B×16 config passes basic hit/miss") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 4, numEntries = 16))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xFACEFEEDL, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFACEFEEDL)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFACEFEEDL)
          dut.io.lowerReq.valid.expect(false)
        }
      }

      it("alternate 4B×32 geometry elaborates and passes basic hit/miss") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 4, numEntries = 32))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xCAFEF00DL, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xCAFEF00DL)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(false)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xCAFEF00DL)
        }
      }

      it("alternate 4B×8 geometry elaborates and passes basic hit/miss") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 4, numEntries = 8))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xABC00001L, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(false)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xABC00001L)
        }
      }

      it("non-power-of-2 blockBytes throws IllegalArgumentException") {
        assertThrows[IllegalArgumentException] {
          ICacheConfig(blockBytes = 3, numEntries = 16)
        }
      }

      it("non-power-of-2 numEntries throws IllegalArgumentException") {
        assertThrows[IllegalArgumentException] {
          ICacheConfig(blockBytes = 4, numEntries = 15)
        }
      }

      it("blockBytes less than 4 throws IllegalArgumentException") {
        assertThrows[IllegalArgumentException] {
          ICacheConfig(blockBytes = 2, numEntries = 16)
        }
      }

      it("non-positive blockBytes throws IllegalArgumentException") {
        assertThrows[IllegalArgumentException] {
          ICacheConfig(blockBytes = 0, numEntries = 16)
        }
      }

      it("non-positive numEntries throws IllegalArgumentException") {
        assertThrows[IllegalArgumentException] {
          ICacheConfig(blockBytes = 4, numEntries = 0)
        }
      }
    }

    describe("Multi-word line refill (T3)") {
      it("16B cold miss at offset 8 returns correct word after full refill") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 16, numEntries = 4))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & ~15L
          val reqAddr  = lineBase + 8L

          issueRequest(dut, reqAddr)
          val lineBaseBig = (CACHEABLE_PSRAM & ~15L)

          // Word 0 at lineBase+0
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBaseBig + 0)
          respondLower(dut, 0xAAAABBBBL, RESP_OKAY, cacheable = true)

          // Word 1 at lineBase+4
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBaseBig + 4)
          respondLower(dut, 0xCCCCDDDDL, RESP_OKAY, cacheable = true)

          // Word 2 at lineBase+8 (the requested word)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBaseBig + 8)
          respondLower(dut, 0xDEADBEEFL, RESP_OKAY, cacheable = true)

          // Word 3 at lineBase+12
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBaseBig + 12)
          respondLower(dut, 0xFFFF0000L, RESP_OKAY, cacheable = true)

          // After 4th word, line is valid; cpuResp returns requested word (word 2)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xDEADBEEFL)
          dut.io.cpuResp.bits.resp.expect(RESP_OKAY)
          acceptCpuResp(dut)

          // Spatial hits at other offsets within the same line
          // Offset 0
          issueRequest(dut, lineBaseBig + 0)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xAAAABBBBL)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)

          // Offset 4
          issueRequest(dut, lineBaseBig + 4)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xCCCCDDDDL)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)

          // Offset 8 (original)
          issueRequest(dut, lineBaseBig + 8)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xDEADBEEFL)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)

          // Offset 12
          issueRequest(dut, lineBaseBig + 12)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFFFF0000L)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)
        }
      }

      it("8B cold miss at offset 4 returns correct word") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 8, numEntries = 4))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & ~7L
          val reqAddr  = lineBase + 4L

          issueRequest(dut, reqAddr)

          // Word 0 at lineBase+0
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 0)
          respondLower(dut, 0x11112222L, RESP_OKAY, cacheable = true)

          // Word 1 at lineBase+4 (requested)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 4)
          respondLower(dut, 0x33334444L, RESP_OKAY, cacheable = true)

          // Returns requested word
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x33334444L)
          acceptCpuResp(dut)

          // Hit at offset 0
          issueRequest(dut, lineBase + 0)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x11112222L)
          acceptCpuResp(dut)
        }
      }

      it("refill error on word 1 of 4 leaves line invalid") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 16, numEntries = 4))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & ~15L
          val reqAddr  = lineBase + 4L

          issueRequest(dut, reqAddr)

          // Word 0: OK
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 0)
          respondLower(dut, 0xAAAAAAAAAL, RESP_OKAY, cacheable = true)

          // Word 1: ERROR (the requested word)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 4)
          respondLower(dut, 0xBADDBADDL, RESP_SLVERR, cacheable = true)

          // Error forwarded to CPU, line NOT installed
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xBADDBADDL)
          dut.io.cpuResp.bits.resp.expect(RESP_SLVERR)
          acceptCpuResp(dut)

          // Same address re-requested: must miss again (valid still false)
          issueRequest(dut, reqAddr)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 0)
        }
      }

      it("refill error on last word leaves line invalid") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 16, numEntries = 4))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & ~15L

          issueRequest(dut, lineBase + 0)

          // Words 0,1,2: OK
          respondLower(dut, 0xAAAAAAAAL, RESP_OKAY, cacheable = true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 4)
          respondLower(dut, 0xBBBBBBBBL, RESP_OKAY, cacheable = true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 8)
          respondLower(dut, 0xCCCCCCCCL, RESP_OKAY, cacheable = true)

          // Word 3: ERROR
          dut.io.lowerReq.bits.addr.expect(lineBase + 12)
          respondLower(dut, 0x00000000L, RESP_DECERR, cacheable = true)

          // Error forwarded
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.resp.expect(RESP_DECERR)
          acceptCpuResp(dut)

          // Must miss again
          issueRequest(dut, lineBase + 0)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("32B line refills 8 words in order") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 32, numEntries = 2))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & ~31L

          issueRequest(dut, lineBase + 0)

          // Verify word addresses are sequential
          for (i <- 0L until 7L) {
            dut.io.lowerReq.valid.expect(true)
            dut.io.lowerReq.bits.addr.expect(lineBase + i * 4)
            respondLower(dut, i * 0x100000L, RESP_OKAY, cacheable = true)
          }

          // Last word
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 7L * 4)
          respondLower(dut, 0x77777777L, RESP_OKAY, cacheable = true)

          // Response should be word 0 data
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x0L)  // word 0 was refilled with 0
          acceptCpuResp(dut)

          // Hit at offset 28 (last word)
          issueRequest(dut, lineBase + 28L)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x77777777L)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)
        }
      }

      it("default 4B config still passes basic hit/miss after T3 changes") {
        simulate(new InstructionCache(32)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xFACEFEEDL, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFACEFEEDL)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFACEFEEDL)
          dut.io.lowerReq.valid.expect(false)
        }
      }
    }

    describe("Geometry sweep — 4B×16 (T5)") {
      it("explicit 4B×16 config confirms cold miss, hit, and conflict") {
        simulate(new InstructionCache(32, ICacheConfig(blockBytes = 4, numEntries = 16))) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          // Cold miss + hit
          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(CACHEABLE_PSRAM)
          respondLower(dut, 0xACE00001L, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xACE00001L)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xACE00001L)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)

          // Conflict: SDRAM replaces PSRAM (same index, different tag)
          issueRequest(dut, CACHEABLE_SDRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xB0B0B0B0L, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          // PSRAM re-request misses (was evicted)
          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
        }
      }
    }

    describe("Geometry sweep — 8B×8 (T5)") {
      val lineMask8 = ~7L

      it("cold miss refills 2 words and subsequent access hits") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)

          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(CACHEABLE_PSRAM + 0)
          respondLower(dut, 0xAAAABBBBL, RESP_OKAY, cacheable = true)

          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(CACHEABLE_PSRAM + 4)
          respondLower(dut, 0xCCCCDDDDL, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xAAAABBBBL)
          dut.io.cpuResp.bits.resp.expect(RESP_OKAY)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xAAAABBBBL)
          dut.io.lowerReq.valid.expect(false)
        }
      }

      it("same-line spatial hits at both offsets without extra refill") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)

          respondLower(dut, 0x11112222L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0x33334444L, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x11112222L)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM + 0)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x11112222L)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM + 4)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x33334444L)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)
        }
      }

      it("conflict replaces entry on same-index different-tag") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          respondLower(dut, 0xAAAAAAAAL, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xAAAA1111L, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_SDRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xBBBBBBBBL, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xBBBB2222L, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("correct requested word at offset 4 returns word 1 data") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val reqAddr = (CACHEABLE_PSRAM & lineMask8) + 4L

          issueRequest(dut, reqAddr)

          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(CACHEABLE_PSRAM + 0)
          respondLower(dut, 0xDEAD0000L, RESP_OKAY, cacheable = true)

          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(CACHEABLE_PSRAM + 4)
          respondLower(dut, 0xBEEF1111L, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xBEEF1111L)
          dut.io.cpuResp.bits.resp.expect(RESP_OKAY)
          acceptCpuResp(dut)

          // Word 0 still accessible
          issueRequest(dut, CACHEABLE_PSRAM + 0)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xDEAD0000L)
          acceptCpuResp(dut)
        }
      }

      it("refill error on word 1 leaves line invalid and re-misses") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & lineMask8

          issueRequest(dut, lineBase + 0)

          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 0)
          respondLower(dut, 0xFEED0000L, RESP_OKAY, cacheable = true)

          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 4)
          respondLower(dut, 0xBADDBADDL, RESP_SLVERR, cacheable = true)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xBADDBADDL)
          dut.io.cpuResp.bits.resp.expect(RESP_SLVERR)
          acceptCpuResp(dut)

          issueRequest(dut, lineBase + 0)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 0)
        }
      }

      it("reset invalidates all valid bits") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          respondLower(dut, 0xACE00000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xACE00004L, RESP_OKAY, cacheable = true)
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

      it("bypass does not refill for non-cacheable addresses") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, NONCACHEABLE_UART)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(NONCACHEABLE_UART)
          respondLower(dut, 0xFEEDFACEL, RESP_OKAY, cacheable = false)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFEEDFACEL)
          dut.io.cpuResp.bits.cacheable.expect(false)
          acceptCpuResp(dut)

          issueRequest(dut, NONCACHEABLE_UART)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("backpressure on lowerReq stalls refill and progresses when ready") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(false)

          issueRequest(dut, CACHEABLE_PSRAM)

          dut.io.lowerReq.valid.expect(true)
          dut.clock.step(2)
          dut.io.lowerReq.valid.expect(true)

          dut.io.lowerReq.ready.poke(true)

          respondLower(dut, 0xAAAABBBBL, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xCCCCDDDDL, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xAAAABBBBL)
          acceptCpuResp(dut)
        }
      }

      it("backpressure on cpuResp holds data stable after multi-word refill") {
        simulate(new InstructionCache(32, cfg8x8)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          respondLower(dut, 0x11112222L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0x33334444L, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.ready.poke(false)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x11112222L)
          dut.clock.step(1)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x11112222L)

          acceptCpuResp(dut)
          dut.io.cpuReq.ready.expect(true)
        }
      }
    }

    describe("Geometry sweep — 16B×4 (T5)") {
      val lineMask16 = ~15L

      it("conflict replaces entry on same-index different-tag with 4-word refill") {
        simulate(new InstructionCache(32, cfg16x4)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          respondLower(dut, 0xA0000000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xA0000004L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xA0000008L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xA000000CL, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_SDRAM)
          dut.io.lowerReq.valid.expect(true)
          respondLower(dut, 0xB0000000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xB0000004L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xB0000008L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xB000000CL, RESP_OKAY, cacheable = true)
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("reset invalidates all valid bits after 4-word refill") {
        simulate(new InstructionCache(32, cfg16x4)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          respondLower(dut, 0xCC000000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xCC000004L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xCC000008L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xCC00000CL, RESP_OKAY, cacheable = true)
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

      it("bypass does not refill for non-cacheable addresses") {
        simulate(new InstructionCache(32, cfg16x4)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, NONCACHEABLE_UART)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(NONCACHEABLE_UART)
          respondLower(dut, 0xFEEDFACEL, RESP_OKAY, cacheable = false)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFEEDFACEL)
          dut.io.cpuResp.bits.cacheable.expect(false)
          acceptCpuResp(dut)

          issueRequest(dut, NONCACHEABLE_UART)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("backpressure on lowerReq stalls multi-word refill") {
        simulate(new InstructionCache(32, cfg16x4)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(false)

          issueRequest(dut, CACHEABLE_PSRAM)

          dut.io.lowerReq.valid.expect(true)
          dut.clock.step(2)
          dut.io.lowerReq.valid.expect(true)

          dut.io.lowerReq.ready.poke(true)

          respondLower(dut, 0xDD000000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xDD000004L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xDD000008L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xDD00000CL, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xDD000000L)
          acceptCpuResp(dut)
        }
      }

      it("backpressure on cpuResp holds data stable after 4-word refill") {
        simulate(new InstructionCache(32, cfg16x4)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          respondLower(dut, 0xEE000000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xEE000004L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xEE000008L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xEE00000CL, RESP_OKAY, cacheable = true)

          dut.io.cpuResp.ready.poke(false)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xEE000000L)
          dut.clock.step(1)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xEE000000L)

          acceptCpuResp(dut)
        }
      }
    }

    describe("Geometry sweep — 32B×2 (T5)") {
      val lineMask32 = ~31L

      it("same-line spatial hits at all 8 offsets without extra refill") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & lineMask32

          issueRequest(dut, lineBase + 0)

          val expected = Array(
            0xF0000000L, 0xF0000004L, 0xF0000008L, 0xF000000CL,
            0xF0000010L, 0xF0000014L, 0xF0000018L, 0xF000001CL
          )
          for (i <- 0 until 8) {
            respondLower(dut, expected(i), RESP_OKAY, cacheable = true)
          }

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(expected(0))
          acceptCpuResp(dut)

          for (i <- 0 until 8) {
            issueRequest(dut, lineBase + i * 4L)
            dut.io.cpuResp.valid.expect(true)
            dut.io.cpuResp.bits.data.expect(expected(i))
            dut.io.lowerReq.valid.expect(false)
            acceptCpuResp(dut)
          }
        }
      }

      it("conflict replaces entry on same-index different-tag with 8-word refill") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          for (i <- 0 until 8) {
            respondLower(dut, 0xA0000000L + i * 4, RESP_OKAY, cacheable = true)
          }
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_SDRAM)
          dut.io.lowerReq.valid.expect(true)
          for (i <- 0 until 8) {
            respondLower(dut, 0xB0000000L + i * 4, RESP_OKAY, cacheable = true)
          }
          dut.io.cpuResp.valid.expect(true)
          acceptCpuResp(dut)

          issueRequest(dut, CACHEABLE_PSRAM)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("correct requested word at offset 20 returns word 5 data") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & lineMask32
          val reqAddr  = lineBase + 20L

          issueRequest(dut, reqAddr)

          val refillData = Array(
            0x10000000L, 0x20000000L, 0x30000000L, 0x40000000L,
            0x50000000L, 0x60000000L, 0x70000000L, 0x80000000L
          )
          for (i <- 0 until 8) {
            respondLower(dut, refillData(i), RESP_OKAY, cacheable = true)
          }

          // Word at offset 20 = word index 5 (20/4 = 5)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x60000000L)
          dut.io.cpuResp.bits.resp.expect(RESP_OKAY)
          acceptCpuResp(dut)

          // Hit at offset 12 (word 3)
          issueRequest(dut, lineBase + 12L)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0x40000000L)
          dut.io.lowerReq.valid.expect(false)
          acceptCpuResp(dut)
        }
      }

      it("refill error on word 3 of 8 leaves line invalid and re-misses") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          val lineBase = CACHEABLE_PSRAM & lineMask32

          issueRequest(dut, lineBase + 0)

          // Words 0,1,2: OK
          respondLower(dut, 0xAAA00000L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xAAA00004L, RESP_OKAY, cacheable = true)
          respondLower(dut, 0xAAA00008L, RESP_OKAY, cacheable = true)

          // Word 3: ERROR
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 12L)
          respondLower(dut, 0xBADDBADDL, RESP_SLVERR, cacheable = true)

          // Error forwarded, no more refill words
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xBADDBADDL)
          dut.io.cpuResp.bits.resp.expect(RESP_SLVERR)
          acceptCpuResp(dut)

          // Re-request: must miss again (valid still false)
          issueRequest(dut, lineBase + 0)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(lineBase + 0)
        }
      }

      it("reset invalidates all valid bits after 8-word refill") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          for (i <- 0 until 8) {
            respondLower(dut, 0xCC000000L + i * 4, RESP_OKAY, cacheable = true)
          }
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

      it("bypass does not refill for non-cacheable addresses") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, NONCACHEABLE_UART)
          dut.io.lowerReq.valid.expect(true)
          dut.io.lowerReq.bits.addr.expect(NONCACHEABLE_UART)
          respondLower(dut, 0xFACEFEEDL, RESP_OKAY, cacheable = false)

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xFACEFEEDL)
          dut.io.cpuResp.bits.cacheable.expect(false)
          acceptCpuResp(dut)

          issueRequest(dut, NONCACHEABLE_UART)
          dut.io.lowerReq.valid.expect(true)
        }
      }

      it("backpressure on lowerReq stalls 8-word refill") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(false)

          issueRequest(dut, CACHEABLE_PSRAM)

          dut.io.lowerReq.valid.expect(true)
          dut.clock.step(2)
          dut.io.lowerReq.valid.expect(true)

          dut.io.lowerReq.ready.poke(true)

          for (i <- 0 until 8) {
            respondLower(dut, 0xDD000000L + i * 4, RESP_OKAY, cacheable = true)
          }

          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xDD000000L)
          acceptCpuResp(dut)
        }
      }

      it("backpressure on cpuResp holds data stable after 8-word refill") {
        simulate(new InstructionCache(32, cfg32x2)) { dut =>
          dut.clock.step(1)
          dut.io.lowerReq.ready.poke(true)

          issueRequest(dut, CACHEABLE_PSRAM)
          for (i <- 0 until 8) {
            respondLower(dut, 0xEE000000L + i * 4, RESP_OKAY, cacheable = true)
          }

          dut.io.cpuResp.ready.poke(false)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xEE000000L)
          dut.clock.step(1)
          dut.io.cpuResp.valid.expect(true)
          dut.io.cpuResp.bits.data.expect(0xEE000000L)

          acceptCpuResp(dut)
        }
      }
    }
  }
}
