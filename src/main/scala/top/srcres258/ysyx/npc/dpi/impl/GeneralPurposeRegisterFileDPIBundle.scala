package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class GeneralPurposeRegisterFileDPIBundle(val xLen: Int = 32) extends DPIBundle {
    /**
      * 输出：通用寄存器。
      */
    val gprs = Output(Vec(1 << 5, UInt(xLen.W)))
}
