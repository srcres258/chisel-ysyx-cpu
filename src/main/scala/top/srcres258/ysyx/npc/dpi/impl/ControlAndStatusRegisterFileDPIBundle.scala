package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class ControlAndStatusRegisterFileDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    /**
      * 输出: 控制与状态寄存器 mstatus.
      */
    val csr_mstatus = Output(UInt(xLen.W))
    /**
      * 输出: 控制与状态寄存器 mtvec.
      */
    val csr_mtvec = Output(UInt(xLen.W))
    /**
      * 输出: 控制与状态寄存器 mepc.
      */
    val csr_mepc = Output(UInt(xLen.W))
    /**
      * 输出: 控制与状态寄存器 mcause.
      */
    val csr_mcause = Output(UInt(xLen.W))
    /**
      * 输出: 控制与状态寄存器 mtval.
      */
    val csr_mtval = Output(UInt(xLen.W))
}
