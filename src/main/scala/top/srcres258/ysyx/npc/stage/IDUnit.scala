package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.ArithmeticLogicUnit
import top.srcres258.ysyx.npc.ComparatorUnit
import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.ImmediateSignExtend
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile
import top.srcres258.ysyx.npc.regfile.ControlAndStatusRegisterFile
import top.srcres258.ysyx.npc.dpi.impl.IDUnitDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的译码 (Instruction Decode) 单元.
  */
class IDUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val gprReadPort = Flipped(new GeneralPurposeRegisterFile.ReadPort(xLen))
        val csrReadPort1 = Flipped(new ControlAndStatusRegisterFile.ReadPort(xLen))
        val csrReadPort2 = Flipped(new ControlAndStatusRegisterFile.ReadPort(xLen))
        val csrReadPort3 = Flipped(new ControlAndStatusRegisterFile.ReadPort(xLen))

        val prevStage = Flipped(Decoupled(Output(new IF_ID_Bundle(xLen))))
        val nextStage = Decoupled(Output(new ID_EX_Bundle(xLen)))

        val dpi = new IDUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val prevStageData = Wire(new IF_ID_Bundle(xLen))
    val nextStageData = Wire(new ID_EX_Bundle(xLen))

    /* 
    ID 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 IF 单元传送数据.
    2. waitData: 接收来自上游 IF 单元传送的数据, 等待数据稳定到达.
    3. wait_nextStage_ready: 准备下一处理器阶段单元的数据并输送, 然后等待下一处理器阶段单元的 ready 信号.
       (注: ID 单元的工作在单周期内即可完成, 所以无需存在中间的工作状态.)
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让上游数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3:
        1. 回复上游 IF 单元的传递数据请求.
        2. 取出来自 IF 单元的数据, 对数据用组合逻辑进行处理, 形成传递给下一处理器阶段单元的数据.
        3. 向下一处理器阶段单元输送数据.
    3 -> 1:
        无 (本轮 ID 操作处理完毕, 等待来自上游 IF 单元的下一份数据).
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

    val rs1 = Wire(UInt(5.W))
    val rs2 = Wire(UInt(5.W))
    val rs1Data = Wire(UInt(xLen.W))
    val rs2Data = Wire(UInt(xLen.W))
    val rd = Wire(UInt(5.W))
    val imm = Wire(UInt(xLen.W))
    val csr = Wire(UInt(12.W))
    val csrData = Wire(UInt(xLen.W))
    val zimm = Wire(UInt(xLen.W))
    val epcData = Wire(UInt(xLen.W))
    val tvecData = Wire(UInt(xLen.W))
    
    val regWriteEnable = Wire(Bool())
    val immSel = Wire(UInt(ControlUnit.IMM_SEL_LEN.W))
    val executePortASel = Wire(Bool())
    val executePortBSel = Wire(Bool())
    val aluOpSel = Wire(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
    val compOpSel = Wire(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
    val dataMemWriteEnable = Wire(Bool())
    val dataMemReadEnable = Wire(Bool())
    val regWriteDataSel = Wire(UInt(ControlUnit.RD_MUX_SEL_LEN.W))
    val csrRegWriteDataSel = Wire(UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W))

    val lsType = Wire(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))

    rs1 := prevStageData.inst(19, 15)
    rs2 := prevStageData.inst(24, 20)
    io.gprReadPort.readAddress1 := rs1
    io.gprReadPort.readAddress2 := rs2
    rs1Data := io.gprReadPort.readData1
    rs2Data := io.gprReadPort.readData2
    rd := prevStageData.inst(11, 7)

    csr := prevStageData.inst(31, 20)
    io.csrReadPort1.readAddress := csr
    csrData := io.csrReadPort1.readData

    zimm := rs1.asUInt.pad(xLen)

    io.csrReadPort2.readAddress := ControlAndStatusRegisterFile.CSR_MEPC.U
    epcData := io.csrReadPort2.readData

    io.csrReadPort3.readAddress := ControlAndStatusRegisterFile.CSR_MTVEC.U
    tvecData := io.csrReadPort3.readData

    val ise = Module(new ImmediateSignExtend(xLen))
    ise.io.inst := prevStageData.inst
    ise.io.immSel := immSel
    imm := ise.io.immOut

    val cu = Module(new ControlUnit(xLen))
    cu.io.opCode := prevStageData.inst(6, 0)
    cu.io.funct3 := prevStageData.inst(14, 12)
    cu.io.funct7 := prevStageData.inst(31, 25)
    cu.io.rd := rd
    cu.io.rs1 := rs1
    cu.io.rs2 := rs2

    regWriteEnable := cu.io.regWriteEnable
    immSel := cu.io.immSel
    executePortASel := cu.io.executePortASel
    executePortBSel := cu.io.executePortBSel
    aluOpSel := cu.io.aluOpSel
    compOpSel := cu.io.compOpSel
    lsType := cu.io.lsType
    dataMemWriteEnable := cu.io.dataMemWriteEnable
    dataMemReadEnable := cu.io.dataMemReadEnable
    regWriteDataSel := cu.io.regWriteDataSel
    csrRegWriteDataSel := cu.io.csrRegWriteDataSel

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.rs1Data := rs1Data
    nextStageData.rs2Data := rs2Data
    nextStageData.imm := imm
    nextStageData.rd := rd
    nextStageData.rs1 := rs1
    nextStageData.rs2 := rs2
    nextStageData.csr := csr
    nextStageData.csrData := csrData
    nextStageData.zimm := zimm
    nextStageData.epcData := epcData
    nextStageData.tvecData := tvecData
    nextStageData.ecallCause := ControlUnit.MCAUSE_ECALL_FROM_M_MODE.U
    nextStageData.regWriteEnable := regWriteEnable
    nextStageData.csrRegWriteEnable := cu.io.csrRegWriteEnable
    nextStageData.aluPortASel := executePortASel
    nextStageData.aluPortBSel := executePortBSel
    nextStageData.aluOpSel := aluOpSel
    nextStageData.compOpSel := compOpSel
    nextStageData.lsType := lsType
    nextStageData.memWriteEnable := dataMemWriteEnable
    nextStageData.memReadEnable := dataMemReadEnable
    nextStageData.regWriteDataSel := regWriteDataSel
    nextStageData.csrRegWriteDataSel := csrRegWriteDataSel
    nextStageData.cuJumpEnable := cu.io.jumpEnable
    nextStageData.cuJumpType := cu.io.jumpType
    nextStageData.cuBranchEnable := cu.io.branchEnable
    nextStageData.epcRecoverEnable := cu.io.epcRecoverEnable
    nextStageData.ecallEnable := cu.io.ecallEnable
    nextStageData.inst_jal := cu.io.inst_jal
    nextStageData.inst_jalr := cu.io.inst_jalr

    when(state === s_wait_nextStage_ready) {
        io.dpi.rs1 := rs1
        io.dpi.rs2 := rs2
        io.dpi.rd := rd
        io.dpi.imm := imm
        io.dpi.rs1Data := rs1Data
        io.dpi.rs2Data := rs2Data
        io.dpi.inst := prevStageData.inst
        io.dpi.inst_jal := cu.io.inst_jal
        io.dpi.inst_jalr := cu.io.inst_jalr
    }.otherwise {
        io.dpi.rs1 := 0.U
        io.dpi.rs2 := 0.U
        io.dpi.rd := 0.U
        io.dpi.imm := 0.U
        io.dpi.rs1Data := 0.U
        io.dpi.rs2Data := 0.U
        io.dpi.inst := 0.U
        io.dpi.inst_jal := false.B
        io.dpi.inst_jalr := false.B
    }
    io.dpi.id_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}
