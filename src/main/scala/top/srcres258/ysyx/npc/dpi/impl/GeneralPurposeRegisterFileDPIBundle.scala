package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class GeneralPurposeRegisterFileDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    /**
      * 输出: 通用寄存器.
      */
    val gprs = Output(Vec(1 << 5, UInt(xLen.W)))
}
