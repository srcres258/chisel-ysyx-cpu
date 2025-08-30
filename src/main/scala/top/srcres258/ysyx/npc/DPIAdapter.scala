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
          * 输入：当前指令类型是否为 jal 。
          * 检测到上升沿时自动进行 ftrace 的检测与记录。
          */
        val inst_jal = Input(Bool())
        /**
          * 输入：当前指令类型是否为 jalr 。
          * 检测到上升沿时自动进行 ftrace 的检测与记录。
          */
        val inst_jalr = Input(Bool())

        /**
          * 输入：内存写使能。检测到上升沿时会自动从后台
          * 仿真环境根据访存类型和访存地址将处理器提供的
          * 数据写入主存。
          */
        val memWriteEnable = Input(Bool())
        /**
          * 输入：内存读使能。检测到上升沿时会自动从后台
          * 仿真环境根据访存类型和访存地址读取主存数据并
          * 提供给处理器。
          */
        val memReadEnable = Input(Bool())

        /**
          * 输入：当前执行阶段。检测到该值变化时后台仿真环境自动同步
          * 处理器状态信息。
          */
        val stage = Input(UInt(StageController.STAGE_LEN.W))
    })
}
