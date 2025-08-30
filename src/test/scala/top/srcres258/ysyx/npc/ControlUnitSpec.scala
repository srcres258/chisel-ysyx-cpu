package top.srcres258.ysyx.npc

import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

class ControlUnitSpec extends AnyFunSpec with ChiselSim {
    describe("ControlUnit") {
        it("main") {
            simulate(new ControlUnit) { top =>
                val clock = top.clock

                // outputs
                val regWriteEnable = top.io.regWriteEnable
                val immSel = top.io.immSel
                val executePortASel = top.io.executePortASel
                val executePortBSel = top.io.executePortBSel
                val aluOpSel = top.io.aluOpSel
                val compOpSel = top.io.compOpSel
                val lsType = top.io.lsType
                val dataMemWriteEnable = top.io.dataMemWriteEnable
                val regWriteDataSel = top.io.regWriteDataSel
                
                // inputs
                val opCode = top.io.opCode
                val funct3 = top.io.funct3
                val funct7Bit5 = top.io.funct7Bit5

                opCode.poke(0b0010111)
                funct3.poke(0b000)
                funct7Bit5.poke(0)
                clock.step(1)
                regWriteEnable.expect(1)
                immSel.expect(ControlUnit.IMM_U_TYPE)
                executePortASel.expect(0)
                executePortBSel.expect(0)
                aluOpSel.expect(ArithmeticLogicUnit.OP_ADD)
                compOpSel.expect(ComparatorUnit.OP_UNKNOWN)
                lsType.expect(LoadAndStoreUnit.LS_UNKNOWN)
                dataMemWriteEnable.expect(0)
                regWriteDataSel.expect(ControlUnit.RD_MUX_ALU)
            }
        }
    }
}
