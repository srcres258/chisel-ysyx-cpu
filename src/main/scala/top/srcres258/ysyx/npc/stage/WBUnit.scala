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

        val prevStage = Flipped(Decoupled(Output(new MA_WB_Bundle(xLen))))
        val nextStage = Decoupled(Output(new WB_UPC_Bundle(xLen)))

        val dpi = new WBUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val prevStageData = Wire(new MA_WB_Bundle(xLen))
    val nextStageData = Wire(new WB_UPC_Bundle(xLen))

    /* 
    WB 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 MA 单元传送数据.
    2. waitData: 接收来自上游 MA 单元传送的数据, 等待数据稳定到达.
    3. wait_nextStage_ready: 准备下一处理器阶段单元的数据并输送, 然后等待下一处理器阶段单元的 ready 信号.
       (注: WB 单元的工作在单周期内即可完成, 所以无需存在中间的工作状态.)
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让上游数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3:
        1. 回复上游 MA 单元的传递数据请求.
        2. 取出来自 MA 单元的数据, 对数据用组合逻辑进行处理, 形成传递给下一处理器阶段单元的数据.
        3. 向下一处理器阶段单元输送数据.
    3 -> 1:
        无 (本轮 EX 操作处理完毕, 等待来自上游 MA 单元的下一份数据).
     */
    val s_idle :: s_waitData :: s_wait_nextStage_ready :: Nil = Enum(3)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.prevStage.fire, s_waitData, s_idle),
        s_waitData -> s_wait_nextStage_ready,
        s_wait_nextStage_ready -> Mux(io.nextStage.fire, s_idle, s_wait_nextStage_ready)
    ))
    io.prevStage.ready := state === s_idle
    io.nextStage.valid := state === s_wait_nextStage_ready
    prevStageData := io.prevStage.bits
    io.nextStage.bits := nextStageData

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

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcTarget := prevStageData.pcTarget

    io.dpi.wb_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}
