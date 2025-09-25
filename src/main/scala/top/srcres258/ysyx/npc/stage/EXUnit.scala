package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.PCTargetController
import top.srcres258.ysyx.npc.dpi.impl.EXUnitDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的执行 (Execute) 单元.
  */
class EXUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val prevStage = Flipped(Decoupled(Output(new ID_EX_Bundle(xLen))))
        val nextStage = Decoupled(Output(new EX_MA_Bundle(xLen)))

        val dpi = new EXUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val prevStageData = Wire(new ID_EX_Bundle(xLen))
    val nextStageData = Wire(new EX_MA_Bundle(xLen))

    /* 
    EX 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 ID 单元传送数据.
    2. waitData: 接收来自上游 ID 单元传送的数据, 等待数据稳定到达.
    3. wait_nextStage_ready: 准备下一处理器阶段单元的数据并输送, 然后等待下一处理器阶段单元的 ready 信号.
       (注: EX 单元的工作在单周期内即可完成, 所以无需存在中间的工作状态.)
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让上游数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3:
        1. 回复上游 ID 单元的传递数据请求.
        2. 取出来自 ID 单元的数据, 对数据用组合逻辑进行处理, 形成传递给下一处理器阶段单元的数据.
        3. 向下一处理器阶段单元输送数据.
    3 -> 1:
        无 (本轮 EX 操作处理完毕, 等待来自上游 ID 单元的下一份数据).
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

    val aluOutput = Wire(UInt(xLen.W))
    val branchEnable = Wire(Bool())

    val aluPortA = Wire(UInt(xLen.W))
    val aluPortB = Wire(UInt(xLen.W))
    val compPortA = Wire(UInt(xLen.W))
    val compPortB = Wire(UInt(xLen.W))

    aluPortA := Mux(prevStageData.aluPortASel, prevStageData.rs1Data, prevStageData.pcCur)
    aluPortB := Mux(prevStageData.aluPortBSel, prevStageData.rs2Data, prevStageData.imm)
    compPortA := prevStageData.rs1Data
    compPortB := Mux(prevStageData.aluPortBSel, prevStageData.imm, prevStageData.rs2Data)

    val alu = Module(new ArithmeticLogicUnit(xLen))
    aluOutput := alu.io.alu
    alu.io.aluPortA := aluPortA
    alu.io.aluPortB := aluPortB
    alu.io.aluSel := prevStageData.aluOpSel

    val compU = Module(new ComparatorUnit(xLen))
    branchEnable := compU.io.comp
    compU.io.compPortA := compPortA
    compU.io.compPortB := compPortB
    compU.io.compOpSel := prevStageData.compOpSel

    val pcTargetCtrl = Module(new PCTargetController(xLen))
    pcTargetCtrl.io.cuJumpEnable := prevStageData.cuJumpEnable
    pcTargetCtrl.io.cuJumpType := prevStageData.cuJumpType
    pcTargetCtrl.io.compBranchEnable := branchEnable
    pcTargetCtrl.io.cuBranchEnable := prevStageData.cuBranchEnable
    pcTargetCtrl.io.pc := prevStageData.pcCur
    pcTargetCtrl.io.imm := prevStageData.imm
    pcTargetCtrl.io.pcNext := prevStageData.pcNext
    pcTargetCtrl.io.rs1Data := prevStageData.rs1Data
    pcTargetCtrl.io.epcRecoverEnable := prevStageData.epcRecoverEnable
    pcTargetCtrl.io.epcData := prevStageData.epcData
    pcTargetCtrl.io.ecallEnable := prevStageData.ecallEnable
    pcTargetCtrl.io.tvecData := prevStageData.tvecData

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.pcTarget := pcTargetCtrl.io.pcTarget
    nextStageData.aluOutput := aluOutput
    nextStageData.compBranchEnable := branchEnable
    nextStageData.rs1Data := prevStageData.rs1Data
    nextStageData.storeData := prevStageData.rs2Data
    nextStageData.imm := prevStageData.imm
    nextStageData.rd := prevStageData.rd
    nextStageData.rs1 := prevStageData.rs1
    nextStageData.rs2 := prevStageData.rs2
    nextStageData.csr := prevStageData.csr
    nextStageData.csrData := prevStageData.csrData
    nextStageData.zimm := prevStageData.zimm
    nextStageData.ecallCause := prevStageData.ecallCause
    nextStageData.lsType := prevStageData.lsType
    nextStageData.memReadEnable := prevStageData.memReadEnable
    nextStageData.memWriteEnable := prevStageData.memWriteEnable
    nextStageData.regWriteEnable := prevStageData.regWriteEnable
    nextStageData.csrRegWriteEnable := prevStageData.csrRegWriteEnable
    nextStageData.regWriteDataSel := prevStageData.regWriteDataSel
    nextStageData.csrRegWriteDataSel := prevStageData.csrRegWriteDataSel
    nextStageData.ecallEnable := prevStageData.ecallEnable
    nextStageData.inst_jal := prevStageData.inst_jal
    nextStageData.inst_jalr := prevStageData.inst_jalr

    when(state === s_wait_nextStage_ready) {
        io.dpi.ecallEnable := prevStageData.ecallEnable
    }.otherwise {
        io.dpi.ecallEnable := false.B
    }
    io.dpi.ex_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}
