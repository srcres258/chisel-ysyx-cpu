package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class UPCUnitDPIBundle(val xLen: Int = 32) extends DPIBundle {
    /**
      * 输出：处理器 UPC 单元提供给处理器核心的 PC 输出信息的 valid 信号。
      */
    val upcu_pcOutput_valid = Output(Bool())
}
