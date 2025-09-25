package top.srcres258.ysyx.npc.dpi

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

/**
  * 外部 DPI 接口适配器, 仅用于仿真.
  */
class GeneralDPIAdapter(xLen: Int) extends DPIAdapter {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(Flipped(new GeneralDPIBundle(xLen)))
}
