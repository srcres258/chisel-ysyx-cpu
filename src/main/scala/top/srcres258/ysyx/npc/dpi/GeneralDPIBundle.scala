package top.srcres258.ysyx.npc.dpi

import chisel3._

import top.srcres258.ysyx.npc.dpi.impl._

/**
  * 用于向外部 DPI 提供信息的线集，仅供外部后台仿真环境获取信息用
  */
class GeneralDPIBundle(
    /**
      * 处理器字长. 32 位 RISC-V ISA 下默认为 32.
      */
    val xLen: Int = 32
) extends DPIBundle {
    // /**
    //   * 输出：程序计数器地址。处理器将会从这个主存地址取指令。
    //   */
    // val pc = Output(UInt(xLen.W))
    // /**
    //   * 输出：是否应终止仿真。检测到高电平上升沿后自动终止仿真。
    //   */
    // val halt = Output(Bool())

    // /**
    //   * 输出：通用寄存器。
    //   */
    // val gprs = Output(Vec(1 << 5, UInt(xLen.W)))
    // /**
    //   * 输出：控制与状态寄存器 mstatus。
    //   */
    // val csr_mstatus = Output(UInt(xLen.W))
    // /**
    //   * 输出：控制与状态寄存器 mtvec。
    //   */
    // val csr_mtvec = Output(UInt(xLen.W))
    // /**
    //   * 输出：控制与状态寄存器 mepc。
    //   */
    // val csr_mepc = Output(UInt(xLen.W))
    // /**
    //   * 输出：控制与状态寄存器 mcause。
    //   */
    // val csr_mcause = Output(UInt(xLen.W))
    // /**
    //   * 输出：控制与状态寄存器 mtval。
    //   */
    // val csr_mtval = Output(UInt(xLen.W))

    // /**
    //   * 输出：当前指令是否为 jal。
    //   */
    // val inst_jal = Output(Bool())
    // /**
    //   * 输出：当前指令是否为 jalr。
    //   */
    // val inst_jalr = Output(Bool())

    // /**
    //   * 输出：当前指令中的源寄存器 rs1 字段的编号。
    //   */
    // val rs1 = Output(UInt(5.W))
    // /**
    //   * 输出：当前指令中的源寄存器 rs2 字段的编号。
    //   */
    // val rs2 = Output(UInt(5.W))
    // /**
    //   * 输出：当前指令中的 (跳转指令中的) 目的寄存器 rd 字段的编号。
    //   */
    // val rd = Output(UInt(5.W))

    // /**
    //   * 输出：当前指令中的立即数 imm 字段的数据内容（已进行符号扩展）。
    //   */
    // val imm = Output(UInt(xLen.W))
    // /**
    //   * 输出：当前指令中的源寄存器 rs1 字段的数据内容（已进行符号扩展）。
    //   */
    // val rs1Data = Output(UInt(xLen.W))
    // /**
    //   * 输出：当前指令中的源寄存器 rs2 字段的数据内容（已进行符号扩展）。
    //   */
    // val rs2Data = Output(UInt(xLen.W))

    // /**
    //   * 输出：内存写使能。检测到上升沿时会自动从后台
    //   * 仿真环境根据访存类型和访存地址将处理器提供的
    //   * 数据写入主存。
    //   */
    // val memWriteEnable = Output(Bool())
    // /**
    //   * 输出：内存读使能。检测到上升沿时会自动从后台
    //   * 仿真环境根据访存类型和访存地址读取主存数据并
    //   * 提供给处理器。
    //   */
    // val memReadEnable = Output(Bool())

    // /**
    //   * 输出：是否触发环境调用。
    //   */
    // val ecallEnable = Output(Bool())

    // /**
    //   * 输出：处理器是否正在执行。
    //   */
    // val executing = Output(Bool())

    // /**
    //   * 输出：处理器核心提供给 IF 单元的信息的 valid 信号。
    //   */
    // val ifuInputValid = Output(Bool())

    // /**
    //   * 输出：处理器 IF 阶段传给下一阶段的信息的 valid 信号。
    //   */
    // val if_nextStage_valid = Output(Bool())
    // /**
    //   * 输出：处理器 ID 阶段传给下一阶段的信息的 valid 信号。
    //   */
    // val id_nextStage_valid = Output(Bool())
    // /**
    //   * 输出：处理器 EX 阶段传给下一阶段的信息的 valid 信号。
    //   */
    // val ex_nextStage_valid = Output(Bool())
    // /**
    //   * 输出：处理器 MA 阶段传给下一阶段的信息的 valid 信号。
    //   */
    // val ma_nextStage_valid = Output(Bool())
    // /**
    //   * 输出：处理器 WB 阶段传给下一阶段的信息的 valid 信号。
    //   */
    // val wb_nextStage_valid = Output(Bool())

    // /**
    //   * 输出：处理器 UPC 单元提供给处理器核心的 PC 输出信息的 valid 信号。
    //   */
    // val upcu_pcOutput_valid = Output(Bool())

    val core = new ProcessorCoreDPIBundle(xLen)
    val physicalRAM = new PhysicalRAMDPIBundle(xLen)
    val gpr = new GeneralPurposeRegisterFileDPIBundle(xLen)
    val csr = new ControlAndStatusRegisterFileDPIBundle(xLen)

    val ifu = new IFUnitDPIBundle(xLen)
    val idu = new IDUnitDPIBundle(xLen)
    val exu = new EXUnitDPIBundle(xLen)
    val mau = new MAUnitDPIBundle(xLen)
    val wbu = new WBUnitDPIBundle(xLen)
    val upcu = new UPCUnitDPIBundle(xLen)
}
