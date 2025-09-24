package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class ProcessorCoreDPIBundle(val xLen: Int = 32) extends DPIBundle {
    /**
      * 输出：程序计数器地址。处理器将会从这个主存地址取指令。
      */
    val pc = Output(UInt(xLen.W))
    /**
      * 输出：是否应终止仿真。检测到高电平上升沿后自动终止仿真。
      */
    val halt = Output(Bool())
    /**
      * 输出：处理器是否正在执行。
      */
    val executing = Output(Bool())
    /**
      * 输出：处理器核心提供给 IF 单元的信息的 valid 信号。
      */
    val ifuInputValid = Output(Bool())
}
