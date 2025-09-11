package top.srcres258.ysyx.npc.dpi

import chisel3._

import top.srcres258.ysyx.npc.StageController

/**
  * 用于向外部 DPI 提供信息的线集，仅供外部后台仿真环境获取信息用
  */
class DPIBundle extends Bundle {
    /**
      * 输出：通用寄存器
      */
    val gprs = Output(Vec(1 << 5, UInt(32.W)))
    /**
      * 输出：控制与状态寄存器 mstatus
      */
    val csr_mstatus = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mtvec
      */
    val csr_mtvec = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mepc
      */
    val csr_mepc = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mcause
      */
    val csr_mcause = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mtval
      */
    val csr_mtval = Output(UInt(32.W))

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

    /**
      * 输出：是否触发环境调用
      */
    val ecallEnable = Output(Bool())
}

object DPIBundle {
    def defaultIO(): DPIBundle = {
        val default = IO(new DPIBundle)
        for (i <- 0 until default.gprs.length) {
            default.gprs(i) := 0.U
        }
        default.csr_mstatus := 0.U
        default.csr_mtvec := 0.U
        default.csr_mepc := 0.U
        default.csr_mcause := 0.U
        default.csr_mtval := 0.U
        default.inst_jal := false.B
        default.inst_jalr := false.B
        default.rs1 := 0.U
        default.rs2 := 0.U
        default.rd := 0.U
        default.imm := 0.U
        default.rs1Data := 0.U
        default.rs2Data := 0.U
        default.stage := 0.U
        default.ecallEnable := false.B
        default
    }
}
