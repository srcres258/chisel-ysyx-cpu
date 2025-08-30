package top.srcres258.ysyx.npc

import chisel3._

/**
  * 用于向外部 DPI 提供信息的线集，仅供外部后台仿真环境获取信息用
  */
class DPIBundle extends Bundle {
    /**
      * 输出：寄存器
      */
    val registers = Output(Vec(32, UInt(32.W)))

    /**
      * 输出：当前指令是否为 jal
      */
    val inst_jal = Output(Bool())
    /**
      * 输出：当前指令是否为 jalr
      */
    val inst_jalr = Output(Bool())

    /**
      * 输出：当前指令中的源寄存器 rs1 字段的编号
      */
    val rs1 = Output(UInt(5.W))
    /**
      * 输出：当前指令中的源寄存器 rs2 字段的编号
      */
    val rs2 = Output(UInt(5.W))
    /**
      * 输出：当前指令中的 (跳转指令中的) 目的寄存器 rd 字段的编号
      */
    val rd = Output(UInt(5.W))

    /**
      * 输出：当前指令中的立即数 imm 字段的数据内容（已进行符号扩展）
      */
    val imm = Output(UInt(32.W))
    /**
      * 输出：当前指令中的源寄存器 rs1 字段的数据内容（已进行符号扩展）
      */
    val rs1Data = Output(UInt(32.W))
    /**
      * 输出：当前指令中的源寄存器 rs2 字段的数据内容（已进行符号扩展）
      */
    val rs2Data = Output(UInt(32.W))

    /**
      * 输出：当前执行阶段
      */
    val stage = Output(UInt(StageController.STAGE_LEN.W))
}
