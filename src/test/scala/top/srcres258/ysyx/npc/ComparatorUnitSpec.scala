package top.srcres258.ysyx.npc

import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

class ComparatorUnitSpec extends AnyFunSpec with ChiselSim {
    describe("ComparatorUnit") {
        it("main") {
            simulate(new ComparatorUnit(32)) { top =>
                val clock = top.clock

                val comp = top.io.comp
                val compPortA = top.io.compPortA
                val compPortB = top.io.compPortB
                val compOpSel = top.io.compOpSel

                /**
                  * 等于比较操作
                  * 
                  * 操作数A：114
                  * 操作数B：114
                  * 预期结果：1 (true)
                  */
                println("测试：等于比较操作 (OP_BEQ)")
                compPortA.poke(114)
                compPortB.poke(114)
                compOpSel.poke(ComparatorUnit.OP_BEQ)
                clock.step(1)
                comp.expect(1)

                /**
                  * 不等于比较操作
                  * 
                  * 操作数A：114
                  * 操作数B：514
                  * 预期结果：1 (true)
                  */
                println("测试：不等于比较操作 (OP_BNE)")
                compPortA.poke(114)
                compPortB.poke(514)
                compOpSel.poke(ComparatorUnit.OP_BNE)
                clock.step(1)
                comp.expect(1)

                /**
                  * 无符号小于比较操作
                  * 
                  * 操作数A：114
                  * 操作数B：514
                  * 预期结果：1 (true)
                  */
                println("测试：无符号小于比较操作 (OP_BLTU)")
                compPortA.poke(114)
                compPortB.poke(514)
                compOpSel.poke(ComparatorUnit.OP_BLTU)
                clock.step(1)
                comp.expect(1)

                /**
                  * 无符号大于或等于比较操作
                  * 
                  * 操作数A：514
                  * 操作数B：114
                  * 预期结果：1 (true)
                  */
                println("测试：无符号大于或等于比较操作 (OP_BGEU)")
                compPortA.poke(514)
                compPortB.poke(114)
                compOpSel.poke(ComparatorUnit.OP_BGEU)
                clock.step(1)
                comp.expect(1)

                /**
                  * 有符号小于比较操作
                  * 
                  * 操作数A：-114
                  * 操作数B：514
                  * 预期结果：1 (true)
                  */
                println("测试：有符号小于比较操作 (OP_BLT)")
                compPortA.poke(-114)
                compPortB.poke(514)
                compOpSel.poke(ComparatorUnit.OP_BLT)
                clock.step(1)
                comp.expect(1)

                /**
                  * 有符号大于或等于比较操作
                  * 
                  * 操作数A：114
                  * 操作数B：-514
                  * 预期结果：1 (true)
                  */
                println("测试：有符号大于或等于比较操作 (OP_BGE)")
                compPortA.poke(114)
                compPortB.poke(-514)
                compOpSel.poke(ComparatorUnit.OP_BGE)
                clock.step(1)
                comp.expect(1)
            }
        }
    }
}
