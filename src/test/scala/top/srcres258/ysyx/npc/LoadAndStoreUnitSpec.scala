package top.srcres258.ysyx.npc

import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

class LoadAndStoreUnitSpec extends AnyFunSpec with ChiselSim {
    describe("LoadAndStoreUnit") {
        it("prioritizes IFetch over memory requests") {
            simulate(new LoadAndStoreUnit(32)) { top =>
                val clock = top.clock

                clock.step(1)

                top.io.ifetchReq.valid.poke(true)
                top.io.ifetchReq.bits.addr.poke(0x1000)
                top.io.memReq.valid.poke(true)
                top.io.memReq.bits.addr.poke(0x2000)
                top.io.memReq.bits.writeData.poke(0xdeadbeefL)
                top.io.memReq.bits.isWrite.poke(false)
                top.io.memReq.bits.lsType.poke(LoadAndStoreUnit.LS_L_W)

                top.io.ifetchReq.ready.expect(true)
                top.io.memReq.ready.expect(false)
            }
        }

        it("accepts memory request when fetch is idle") {
            simulate(new LoadAndStoreUnit(32)) { top =>
                val clock = top.clock

                clock.step(1)

                top.io.ifetchReq.valid.poke(false)
                top.io.ifetchReq.bits.addr.poke(0x1000)
                top.io.memReq.valid.poke(true)
                top.io.memReq.bits.addr.poke(0x2000)
                top.io.memReq.bits.writeData.poke(0xdeadbeefL)
                top.io.memReq.bits.isWrite.poke(false)
                top.io.memReq.bits.lsType.poke(LoadAndStoreUnit.LS_L_W)

                top.io.memReq.ready.expect(true)
            }
        }
    }
}
