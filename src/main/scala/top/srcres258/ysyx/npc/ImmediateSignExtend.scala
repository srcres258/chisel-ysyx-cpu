package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

/**
  * 立即数符号扩展模块，用于将各类型的指令数据中的立即数进行符号扩展
  * （若该指令类型的结构中包含立即数）
  */
class ImmediateSignExtend(
    /**
      * xLen: 操作数位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val immOut = Output(UInt(xLen.W))
        val inst = Input(UInt(xLen.W))
        val immSel = Input(UInt(ControlUnit.IMM_SEL_LEN.W))
    })

    {
        // default values
        io.immOut := 0.U

        when(io.immSel === ControlUnit.IMM_B_TYPE.U(ControlUnit.IMM_SEL_LEN.W)) {
            io.immOut := Cat(io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W)).asSInt.pad(xLen).asUInt
        }
        when(io.immSel === ControlUnit.IMM_S_TYPE.U(ControlUnit.IMM_SEL_LEN.W)) {
            io.immOut := Cat(io.inst(31, 25), io.inst(11, 7)).asSInt.pad(xLen).asUInt
        }
        when(io.immSel === ControlUnit.IMM_U_TYPE.U(ControlUnit.IMM_SEL_LEN.W)) {
            io.immOut := Cat(io.inst(31, 12), 0.U(12.W))
        }
        when(io.immSel === ControlUnit.IMM_I_TYPE.U(ControlUnit.IMM_SEL_LEN.W)) {
            io.immOut := io.inst(31, 20).asSInt.pad(xLen).asUInt
        }
        when(io.immSel === ControlUnit.IMM_J_TYPE.U(ControlUnit.IMM_SEL_LEN.W)) {
            io.immOut := Cat(io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W)).asSInt.pad(xLen).asUInt
        }
    }
}
