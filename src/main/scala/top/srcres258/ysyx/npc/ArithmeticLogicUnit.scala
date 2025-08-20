package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import scala.collection.mutable.ListBuffer

/**
  * 算术逻辑单元 ALU
  */
class ArithmeticLogicUnit(
    /**
      * xLen: 操作数位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32,
    /**
      * regAddrWidth: 寄存器编号位数，在 RV32I 指令集中为 5
      */
    val regAddrWidth: Int = 5
) extends Module {
    val io = IO(new Bundle {
        /**
          * 输出：ALU 计算结果
          */
        val alu = Output(UInt(xLen.W))
        /**
          * 输入：ALU 操作数 A
          */
        val aluPortA = Input(UInt(xLen.W))
        /**
          * 输入：ALU 操作数 B
          */
        val aluPortB = Input(UInt(xLen.W))
        /**
          * 输入：ALU 操作类型选择器
          */
        val aluSel = Input(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
    })

    val aluSelOH = UIntToOH(io.aluSel)
    val aluSelCases = Array.fill[UInt](1 << ArithmeticLogicUnit.ALU_SEL_LEN)(0.U(xLen.W))
    aluSelCases(ArithmeticLogicUnit.OP_ADD) = io.aluPortA + io.aluPortB
    aluSelCases(ArithmeticLogicUnit.OP_SUB) = io.aluPortA - io.aluPortB
    aluSelCases(ArithmeticLogicUnit.OP_AND) = io.aluPortA & io.aluPortB
    aluSelCases(ArithmeticLogicUnit.OP_OR) = io.aluPortA | io.aluPortB
    aluSelCases(ArithmeticLogicUnit.OP_XOR) = io.aluPortA ^ io.aluPortB
    aluSelCases(ArithmeticLogicUnit.OP_SLL) = io.aluPortA << io.aluPortB(regAddrWidth - 1, 0)
    aluSelCases(ArithmeticLogicUnit.OP_SRL) = io.aluPortA >> io.aluPortB(regAddrWidth - 1, 0)
    aluSelCases(ArithmeticLogicUnit.OP_SRA) = (io.aluPortA.asSInt >> io.aluPortB(regAddrWidth - 1, 0)).asUInt
    io.alu := Mux1H(aluSelOH, aluSelCases.toIndexedSeq)
}

object ArithmeticLogicUnit {
    /**
      * ALU 操作类型选择器的位宽
      */
    val ALU_SEL_LEN: Int = 4

    // ----- ALU 的各种操作类型 -----
    /**
      * 加法
      */
    val OP_ADD: Int = 0
    /**
      * 减法
      */
    val OP_SUB: Int = 1
    /**
      * 按位与
      */
    val OP_AND: Int = 2
    /**
      * 按位或
      */
    val OP_OR: Int = 3
    /**
      * 按位异或
      */
    val OP_XOR: Int = 4
    /**
      * 逻辑左移
      */
    val OP_SLL: Int = 5
    /**
      * 逻辑右移
      */
    val OP_SRL: Int = 6
    /**
      * 算术右移
      */
    val OP_SRA: Int = 7
    /**
      * 未知
      */
    val OP_UNKNOWN: Int = 15
}
