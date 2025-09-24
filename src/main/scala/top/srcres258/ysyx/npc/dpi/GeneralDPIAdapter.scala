package top.srcres258.ysyx.npc.dpi

import chisel3._
import chisel3.util._

/**
  * 外部 DPI 接口适配器，仅用于仿真
  */
class GeneralDPIAdapter(xLen: Int = 32) extends DPIAdapter {
    val io = IO(Flipped(new GeneralDPIBundle(xLen)))
}
