package top.srcres258.ysyx.npc

import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

class ImmediateSignExtendSpec extends AnyFunSpec with ChiselSim {
    describe("ArithmeticLogicUnit") {
        it("main") {
            simulate(new ImmediateSignExtend(32)) { top =>
                val clock = top.clock

                val immOut = top.io.immOut
                val inst = top.io.inst
                val immSel = top.io.immSel

                inst.poke(0xffc10113L)
                immSel.poke(ControlUnit.IMM_I_TYPE)
                clock.step(1)
                immOut.expect(0xfffffffcL)

                inst.poke(0x00c000efL)
                immSel.poke(ControlUnit.IMM_J_TYPE)
                clock.step(1)
                immOut.expect(0xcL)
            }
        }
    }
}
