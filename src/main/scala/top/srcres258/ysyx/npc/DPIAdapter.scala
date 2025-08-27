package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

/**
  * 外部 DPI 接口适配器，仅用于仿真
  */
class DPIAdapter extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        /**
          * 输入：是否应终止仿真。检测到高电平上升沿后自动终止仿真。
          */
        val halt = Input(Bool())
        /**
          * 输入：读入/写出程序数据，地址。每当地址发生改变时，
          * 自动从外部主存读取该地址处的数据并据此更新处理器核心的“读入程序数据”输入。
          */
        val address = Input(UInt(32.W))
        /**
          * 输入：访存单元访存类型。每当类型发生改变时，
          * 自动在仿真环境中记录 mtrace （访存记录）。
          */
        val lsType = Input(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
        /**
          * 输入：当前指令类型是否为 jal 。
          * 检测到上升沿时自动进行 ftrace 的检测与记录。
          */
        val inst_jal = Input(Bool())
        /**
          * 输入：当前指令类型是否为 jalr 。
          * 检测到上升沿时自动进行 ftrace 的检测与记录。
          */
        val inst_jalr = Input(Bool())
    })
}
