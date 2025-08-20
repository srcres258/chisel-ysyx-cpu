package top.srcres258.ysyx.npc

import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

class ArithmeticLogicUnitSpec extends AnyFunSpec with ChiselSim {
    describe("ArithmeticLogicUnit") {
        it("main") {
            simulate(new ArithmeticLogicUnit) { top =>
                val clock = top.clock

                val aluPortA = top.io.aluPortA
                val aluPortB = top.io.aluPortB
                val aluSel = top.io.aluSel
                val alu = top.io.alu

                /**
                  * 加法操作
                  * 
                  * 操作数A：0x00000001
                  * 操作数B：0x00000002
                  * 预期结果：0x00000003
                  */
                println("测试：加法操作 (OP_ADD)")
                aluPortA.poke(0x00000001)
                aluPortB.poke(0x00000002)
                aluSel.poke(ArithmeticLogicUnit.OP_ADD)
                clock.step(1)
                alu.expect(0x00000003)

                /**
                  * 减法操作
                  * 
                  * 操作数A：0x00000003
                  * 操作数B：0x00000002
                  * 预期结果：0x00000001
                  */
                println("测试：减法操作 (OP_SUB)")
                aluPortA.poke(0x00000003)
                aluPortB.poke(0x00000002)
                aluSel.poke(ArithmeticLogicUnit.OP_SUB)
                clock.step(1)
                alu.expect(0x00000001)

                /**
                  * 按位与操作
                  * 
                  * 操作数A：0x00000003 (0b0011)
                  * 操作数B：0x0000000D (0b1101)
                  * 预期结果：0x00000001 (0b0001)
                  */
                println("测试：按位与操作 (OP_AND)")
                aluPortA.poke(0x00000003)
                aluPortB.poke(0x0000000D)
                aluSel.poke(ArithmeticLogicUnit.OP_AND)
                clock.step(1)
                alu.expect(0x00000001)

                /**
                  * 按位或操作
                  * 
                  * 操作数A：0x00000003 (0b0011)
                  * 操作数B：0x0000000D (0b1101)
                  * 预期结果：0x0000000F (0b1111)
                  */
                println("测试：按位或操作 (OP_OR)")
                aluPortA.poke(0x00000003)
                aluPortB.poke(0x0000000D)
                aluSel.poke(ArithmeticLogicUnit.OP_OR)
                clock.step(1)
                alu.expect(0x0000000F)

                /**
                  * 按位异或操作
                  * 
                  * 操作数A：0x00000003 (0b0011)
                  * 操作数B：0x0000000D (0b1101)
                  * 预期结果：0x0000000E (0b1110)
                  */
                println("测试：按位异或操作 (OP_XOR)")
                aluPortA.poke(0x00000003)
                aluPortB.poke(0x0000000D)
                aluSel.poke(ArithmeticLogicUnit.OP_XOR)
                clock.step(1)
                alu.expect(0x0000000E)

                /**
                  * 逻辑左移操作
                  * 
                  * 操作数A：0x00000003 (0b000011)
                  * 操作数B：0x00000003
                  * 预期结果：0x00000018 (0b011000)
                  */
                println("测试：逻辑左移操作 (OP_SLL)")
                aluPortA.poke(0x00000003)
                aluPortB.poke(0x00000003)
                aluSel.poke(ArithmeticLogicUnit.OP_SLL)
                clock.step(1)
                alu.expect(0x00000018)

                /**
                  * 逻辑右移操作
                  * 
                  * 操作数A：0x00000018 (0b011000)
                  * 操作数B：0x00000003
                  * 预期结果：0x00000003 (0b000011)
                  */
                println("测试：逻辑右移操作 (OP_SRL)")
                aluPortA.poke(0x00000018)
                aluPortB.poke(0x00000003)
                aluSel.poke(ArithmeticLogicUnit.OP_SRL)
                clock.step(1)
                alu.expect(0x00000003)

                /**
                  * 算术右移操作
                  * 
                  * 操作数A：0xF0000000
                  * 操作数B：0x00000008
                  * 预期结果：0xFFF00000
                  */
                println("测试：算术右移操作 (OP_SRA)")
                aluPortA.poke(BigInt(0xF0000000L))
                aluPortB.poke(BigInt(0x00000008L))
                aluSel.poke(ArithmeticLogicUnit.OP_SRA)
                clock.step(1)
                alu.expect(BigInt(0xFFF00000L))
            }
        }
    }
}
