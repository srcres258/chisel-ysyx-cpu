package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * 比较器单元.
  */
class ComparatorUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)
    
    val io = IO(new Bundle {
        /**
          * 输出: 比较结果.
          */
        val comp = Output(Bool())
        /**
          * 输入: 比较操作数 A.
          */
        val compPortA = Input(UInt(xLen.W))
        /**
          * 输入: 比较操作数 B.
          */
        val compPortB = Input(UInt(xLen.W))
        /**
          * 输入: 比较操作类型选择器.
          */
        val compOpSel = Input(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
    })

    val compOpSelOH = UIntToOH(io.compOpSel)
    val compOpSelCases = Array.fill[Bool](1 << ComparatorUnit.COMP_OP_SEL_LEN)(false.B)
    compOpSelCases(ComparatorUnit.OP_BEQ) = io.compPortA === io.compPortB
    compOpSelCases(ComparatorUnit.OP_BNE) = io.compPortA =/= io.compPortB
    compOpSelCases(ComparatorUnit.OP_BLTU) = io.compPortA < io.compPortB
    compOpSelCases(ComparatorUnit.OP_BGEU) = io.compPortA >= io.compPortB
    compOpSelCases(ComparatorUnit.OP_BLT) = io.compPortA.asSInt < io.compPortB.asSInt
    compOpSelCases(ComparatorUnit.OP_BGE) = io.compPortA.asSInt >= io.compPortB.asSInt
    io.comp := Mux1H(compOpSelOH, compOpSelCases.toIndexedSeq)
}

object ComparatorUnit {
    /**
      * 比较操作类型选择器的位宽.
      */
    val COMP_OP_SEL_LEN: Int = 4

    // ----- 各种比较操作类型 -----
    /**
      * 等于
      */
    val OP_BEQ: Int = 0
    /**
      * 不等于
      */
    val OP_BNE: Int = 1
    /**
      * 无符号比较：小于
      */
    val OP_BLTU: Int = 2
    /**
      * 无符号比较：大于或等于
      */
    val OP_BGEU: Int = 3
    /**
      * 有符号比较：小于
      */
    val OP_BLT: Int = 4
    /**
      * 有符号比较：大于或等于
      */
    val OP_BGE: Int = 5
    /**
      * 未知
      */
    val OP_UNKNOWN: Int = 1 << COMP_OP_SEL_LEN - 1
}
