package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class ControlAndStatusRegisterFileDPIBundle(val xLen: Int = 32) extends DPIBundle {
    /**
      * 输出：控制与状态寄存器 mstatus。
      */
    val csr_mstatus = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mtvec。
      */
    val csr_mtvec = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mepc。
      */
    val csr_mepc = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mcause。
      */
    val csr_mcause = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mtval。
      */
    val csr_mtval = Output(UInt(32.W))
}
