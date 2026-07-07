package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.regfile.ControlAndStatusRegisterFile
import top.srcres258.ysyx.npc.dpi.impl.WBUnitDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的写回 (Write Back) 单元.
  */
class WBUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val gprWritePort = Flipped(new GeneralPurposeRegisterFile.WritePort(xLen))
        val csrWritePort1 = Flipped(new ControlAndStatusRegisterFile.WritePort(xLen))
        val csrWritePort2 = Flipped(new ControlAndStatusRegisterFile.WritePort(xLen))

        val prevStage = Flipped(Decoupled(Output(new MEM_WB_Bundle(xLen))))

        val pcTargetOut = Output(UInt(xLen.W))
        val done = Output(Bool())

        val dpi = new WBUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val prevStageData = Wire(new MEM_WB_Bundle(xLen))

    /* 
    WB 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 MEM 单元传送数据.
    2. waitData: 接收来自上游 MEM 单元传送的数据, 等待数据稳定到达.
    3. s_writeBack: 执行写回操作 (GPR/CSR 写入), 完成后通知顶层更新 PC.
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让上游数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3:
        1. 回复上游 MEM 单元的传递数据请求.
        2. 取出来自 MEM 单元的数据, 对数据用组合逻辑进行处理, 执行写回.
        3. 通知顶层单元写回已完成.
    3 -> 1:
        等待顶层确认 done 信号后回到空闲状态.
     */
    val s_idle :: s_waitData :: s_writeBack :: Nil = Enum(3)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.prevStage.fire, s_waitData, s_idle),
        s_waitData -> s_writeBack,
        s_writeBack -> Mux(io.done, s_idle, s_writeBack)
    ))
    io.prevStage.ready := state === s_idle
    io.done := state === s_writeBack
    prevStageData := io.prevStage.bits

    val gprData = Wire(UInt(xLen.W))
    val csrData = Wire(UInt(xLen.W))

    gprData := 0.U
    when(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_DMEM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.memReadData
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_ALU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.aluOutput
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_BCU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := Cat(0.U(31.W), prevStageData.compBranchEnable.asUInt)
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_IMM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.imm
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_PC_N.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.pcNext
    }.elsewhen(prevStageData.regWriteDataSel === ControlUnit.RD_MUX_CSR_DATA.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
        gprData := prevStageData.csrData
    }

    csrData := 0.U
    when(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_W.U) {
        csrData := prevStageData.rs1Data
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_S.U) {
        csrData := prevStageData.csrData | prevStageData.rs1Data
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_C.U) {
        csrData := prevStageData.csrData & (~prevStageData.rs1Data)
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_W_IMM.U) {
        csrData := prevStageData.zimm
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_S_IMM.U) {
        csrData := prevStageData.csrData | prevStageData.zimm
    }.elsewhen(prevStageData.csrRegWriteDataSel === ControlUnit.CSR_RD_MUX_C_IMM.U) {
        csrData := prevStageData.csrData & (~prevStageData.zimm)
    }

    io.gprWritePort.writeEnable := prevStageData.regWriteEnable
    io.gprWritePort.writeData := gprData
    io.gprWritePort.writeAddress := prevStageData.rd

    io.csrWritePort1.writeEnable := prevStageData.csrRegWriteEnable
    io.csrWritePort1.writeData := csrData
    io.csrWritePort1.writeAddress := prevStageData.csr
    io.csrWritePort2.writeEnable := false.B
    io.csrWritePort2.writeData := 0.U
    io.csrWritePort2.writeAddress := 0.U
    when(prevStageData.ecallEnable) {
        // 环境调用 ecall

        // 1. 先要将当前 pc 写入 mepc 寄存器
        io.csrWritePort1.writeEnable := true.B
        io.csrWritePort1.writeData := prevStageData.pcCur
        io.csrWritePort1.writeAddress := ControlAndStatusRegisterFile.CSR_MEPC.U

        // 2. 再将原因写入 mcause 寄存器
        io.csrWritePort2.writeEnable := true.B
        io.csrWritePort2.writeData := prevStageData.ecallCause
        io.csrWritePort2.writeAddress := ControlAndStatusRegisterFile.CSR_MCAUSE.U
    }

    io.pcTargetOut := prevStageData.pcTarget

    when(io.done) {
        io.dpi.pc := prevStageData.pcCur
        io.dpi.pcNext := prevStageData.pcNext
        io.dpi.inst := prevStageData.inst
        io.dpi.rs1 := prevStageData.rs1
        io.dpi.rd := prevStageData.rd
        io.dpi.imm := prevStageData.imm
        io.dpi.rs1Data := prevStageData.rs1Data
        io.dpi.inst_jal := prevStageData.inst_jal
        io.dpi.inst_jalr := prevStageData.inst_jalr
    }.otherwise {
        io.dpi.pc := 0.U
        io.dpi.pcNext := 0.U
        io.dpi.inst := 0.U
        io.dpi.rs1 := 0.U
        io.dpi.rd := 0.U
        io.dpi.imm := 0.U
        io.dpi.rs1Data := 0.U
        io.dpi.inst_jal := false.B
        io.dpi.inst_jalr := false.B
    }

    io.dpi.wb_nextStage_valid := io.done

    io.working := state =/= s_idle
}
