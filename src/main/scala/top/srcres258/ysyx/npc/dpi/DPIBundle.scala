package top.srcres258.ysyx.npc.dpi

import chisel3._
import top.srcres258.ysyx.npc.ProcessorCore

/**
  * 用于向外部 DPI 提供信息的线集，仅供外部后台仿真环境获取信息用
  */
class DPIBundle extends Bundle {
    /**
      * 输出：是否应终止仿真。检测到高电平上升沿后自动终止仿真。
      */
    val halt = Output(Bool())

    /**
      * 输出：通用寄存器。
      */
    val gprs = Output(Vec(1 << 5, UInt(32.W)))
    /**
      * 输出：控制与状态寄存器 mstatus。
      */
    val csr_mstatus = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mtvec。
      */
    val csr_mtvec = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mepc。
      */
    val csr_mepc = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mcause。
      */
    val csr_mcause = Output(UInt(32.W))
    /**
      * 输出：控制与状态寄存器 mtval。
      */
    val csr_mtval = Output(UInt(32.W))

    /**
      * 输出：当前指令是否为 jal。
      */
    val inst_jal = Output(Bool())
    /**
      * 输出：当前指令是否为 jalr。
      */
    val inst_jalr = Output(Bool())

    /**
      * 输出：当前指令中的源寄存器 rs1 字段的编号。
      */
    val rs1 = Output(UInt(5.W))
    /**
      * 输出：当前指令中的源寄存器 rs2 字段的编号。
      */
    val rs2 = Output(UInt(5.W))
    /**
      * 输出：当前指令中的 (跳转指令中的) 目的寄存器 rd 字段的编号。
      */
    val rd = Output(UInt(5.W))

    /**
      * 输出：当前指令中的立即数 imm 字段的数据内容（已进行符号扩展）。
      */
    val imm = Output(UInt(32.W))
    /**
      * 输出：当前指令中的源寄存器 rs1 字段的数据内容（已进行符号扩展）。
      */
    val rs1Data = Output(UInt(32.W))
    /**
      * 输出：当前指令中的源寄存器 rs2 字段的数据内容（已进行符号扩展）。
      */
    val rs2Data = Output(UInt(32.W))

    /**
      * 输出：内存写使能。检测到上升沿时会自动从后台
      * 仿真环境根据访存类型和访存地址将处理器提供的
      * 数据写入主存。
      */
    val memWriteEnable = Output(Bool())
    /**
      * 输出：内存读使能。检测到上升沿时会自动从后台
      * 仿真环境根据访存类型和访存地址读取主存数据并
      * 提供给处理器。
      */
    val memReadEnable = Output(Bool())

    /**
      * 输出：是否触发环境调用。
      */
    val ecallEnable = Output(Bool())

    /**
      * 输出：处理器是否正在执行。
      */
    val executing = Output(Bool())

    /**
      * 输出：处理器核心提供给 IF 单元的信息的 valid 信号。
      */
    val ifuInputValid = Output(Bool())

    /**
      * 输出：处理器 IF 阶段传给下一阶段的信息的 valid 信号。
      */
    val if_nextStage_valid = Output(Bool())
    /**
      * 输出：处理器 ID 阶段传给下一阶段的信息的 valid 信号。
      */
    val id_nextStage_valid = Output(Bool())
    /**
      * 输出：处理器 EX 阶段传给下一阶段的信息的 valid 信号。
      */
    val ex_nextStage_valid = Output(Bool())
    /**
      * 输出：处理器 MA 阶段传给下一阶段的信息的 valid 信号。
      */
    val ma_nextStage_valid = Output(Bool())
    /**
      * 输出：处理器 WB 阶段传给下一阶段的信息的 valid 信号。
      */
    val wb_nextStage_valid = Output(Bool())

    /**
      * 输出：处理器 UPC 单元提供给处理器核心的 PC 输出信息的 valid 信号。
      */
    val upcu_pcOutput_valid = Output(Bool())
}

object DPIBundle {
    def defaultIO(): DPIBundle = {
        val default = IO(new DPIBundle)

        default.halt := false.B
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
        default.memWriteEnable := false.B
        default.memReadEnable := false.B
        default.ecallEnable := false.B
        default.executing := false.B
        default.ifuInputValid := false.B
        default.if_nextStage_valid := false.B
        default.id_nextStage_valid := false.B
        default.ex_nextStage_valid := false.B
        default.ma_nextStage_valid := false.B
        default.wb_nextStage_valid := false.B
        default.upcu_pcOutput_valid := false.B

        default
    }
}
