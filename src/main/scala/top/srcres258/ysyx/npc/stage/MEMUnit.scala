package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.dpi.impl.MEMUnitDPIBundle
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.Config

class MEMUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)
    
    val io = IO(new Bundle {
        val clintBus = new AXI4Lite(xLen)

        val lsuMemReq = Decoupled(Output(new LoadAndStoreUnit.MemReq(xLen)))
        val lsuMemResp = Flipped(Decoupled(Output(new LoadAndStoreUnit.MemResp(xLen))))

        val prevStage = Flipped(Decoupled(Output(new EX_MEM_Bundle(xLen))))
        val nextStage = Decoupled(Output(new MEM_WB_Bundle(xLen)))

        val working = Output(Bool())
    })

    val dpi = if (Config.enableDPI) Some(IO(new MEMUnitDPIBundle(xLen))) else None

    val skip = Wire(Bool())
    val rdata = RegInit(0.U(xLen.W))
    val rresp = RegInit(0.U(2.W))
    val memPc = RegInit(0.U(xLen.W))

    val prevStageData = Wire(new EX_MEM_Bundle(xLen))
    val prevStageDataLatchedAluOutput = RegInit(0.U(xLen.W))
    val prevStageDataLatchedStoreData = RegInit(0.U(xLen.W))
    val prevStageDataLatchedMemWriteEnable = RegInit(false.B)
    val prevStageDataLatchedMemReadEnable = RegInit(false.B)
    val prevStageDataLatchedLsType = RegInit(LoadAndStoreUnit.LS_UNKNOWN.U)
    val nextStageData = Wire(new MEM_WB_Bundle(xLen))

    val s_idle :: s_waitData :: s_sendLsuReq :: s_waitLsuResp :: (
        s_clint_load_wait_arready :: s_clint_load_wait_rvalid :: (
        s_wait_nextStage_ready :: Nil)) = Enum(7)

    val state = RegInit(s_idle)

    val address = Wire(UInt(xLen.W))
    address := Mux(state === s_waitData, prevStageData.aluOutput, prevStageDataLatchedAluOutput)
    val isClintAddr = address >= Config.clintAddrBase.U &&
                      address < (Config.clintAddrBase + Config.clintAddrSize).U

    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.prevStage.fire, s_waitData, s_idle),
        s_waitData -> MuxCase(s_wait_nextStage_ready, Seq(
            (isClintAddr && io.prevStage.bits.memReadEnable) -> s_clint_load_wait_arready,
            io.prevStage.bits.memReadEnable -> s_sendLsuReq,
            io.prevStage.bits.memWriteEnable -> s_sendLsuReq
        )),
        s_sendLsuReq -> Mux(io.lsuMemReq.fire, s_waitLsuResp, s_sendLsuReq),
        s_waitLsuResp -> Mux(io.lsuMemResp.fire, s_wait_nextStage_ready, s_waitLsuResp),
        s_clint_load_wait_arready -> Mux(io.clintBus.ar.fire, s_clint_load_wait_rvalid, s_clint_load_wait_arready),
        s_clint_load_wait_rvalid -> Mux(io.clintBus.r.fire, s_wait_nextStage_ready, s_clint_load_wait_rvalid),
        s_wait_nextStage_ready -> Mux(io.nextStage.fire, s_idle, s_wait_nextStage_ready)
    ))
    io.prevStage.ready := state === s_idle
    io.nextStage.valid := state === s_wait_nextStage_ready
    prevStageData := io.prevStage.bits
    when(state === s_waitData) {
        prevStageDataLatchedAluOutput := prevStageData.aluOutput
        prevStageDataLatchedStoreData := prevStageData.storeData
        prevStageDataLatchedMemWriteEnable := prevStageData.memWriteEnable
        prevStageDataLatchedMemReadEnable := prevStageData.memReadEnable
        prevStageDataLatchedLsType := prevStageData.lsType
    }
    io.nextStage.bits := nextStageData

    when(state === s_waitData) {
        memPc := io.prevStage.bits.pcCur
    }.elsewhen(state === s_idle) {
        memPc := 0.U
    }

    io.lsuMemReq.valid := state === s_sendLsuReq
    io.lsuMemReq.bits.addr := address
    io.lsuMemReq.bits.writeData := prevStageDataLatchedStoreData
    io.lsuMemReq.bits.isWrite := prevStageDataLatchedMemWriteEnable
    io.lsuMemReq.bits.lsType := prevStageDataLatchedLsType
    io.lsuMemResp.ready := state === s_waitLsuResp
    when(state === s_waitLsuResp && io.lsuMemResp.fire) {
        rdata := io.lsuMemResp.bits.readData
        rresp := io.lsuMemResp.bits.resp
    }

    io.clintBus.ar.valid := state === s_clint_load_wait_arready
    io.clintBus.ar.bits.addr := address
    io.clintBus.r.ready := state === s_clint_load_wait_rvalid
    io.clintBus.aw.valid := false.B
    io.clintBus.aw.bits.addr := 0.U
    io.clintBus.w.valid := false.B
    io.clintBus.w.bits.data := 0.U
    io.clintBus.w.bits.strb := 0.U
    io.clintBus.b.ready := false.B
    when(state === s_clint_load_wait_rvalid && io.clintBus.r.fire) {
        rdata := io.clintBus.r.bits.data
        rresp := io.clintBus.r.bits.resp
    }

    skip := !(prevStageDataLatchedMemReadEnable || prevStageDataLatchedMemWriteEnable)

    val addrByteOffset = prevStageDataLatchedAluOutput(1, 0)
    val byteShift = Cat(addrByteOffset, 0.U(3.W))
    val rdataShifted = rdata >> byteShift

    val readDataAligned = Wire(UInt(xLen.W))
    val lsTypeOH = UIntToOH(prevStageDataLatchedLsType)

    val lsTypeCasesR = Array.fill[UInt](1 << LoadAndStoreUnit.LS_TYPE_LEN)(rdata)
    lsTypeCasesR(LoadAndStoreUnit.LS_L_B) = rdataShifted(7, 0).asSInt.pad(xLen).asUInt
    lsTypeCasesR(LoadAndStoreUnit.LS_L_BU) = Cat(Fill(24, 0.U(1.W)), rdataShifted(7, 0))
    lsTypeCasesR(LoadAndStoreUnit.LS_L_H) = rdataShifted(15, 0).asSInt.pad(xLen).asUInt
    lsTypeCasesR(LoadAndStoreUnit.LS_L_HU) = Cat(Fill(16, 0.U(1.W)), rdataShifted(15, 0))
    readDataAligned := Mux1H(lsTypeOH, lsTypeCasesR.toIndexedSeq)

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.inst := prevStageData.inst
    nextStageData.pcTarget := prevStageData.pcTarget
    nextStageData.memReadData := Mux(skip, 0.U, readDataAligned)
    nextStageData.aluOutput := prevStageDataLatchedAluOutput
    nextStageData.compBranchEnable := prevStageData.compBranchEnable
    nextStageData.rs1Data := prevStageData.rs1Data
    nextStageData.imm := prevStageData.imm
    nextStageData.rd := prevStageData.rd
    nextStageData.rs1 := prevStageData.rs1
    nextStageData.csr := prevStageData.csr
    nextStageData.csrData := prevStageData.csrData
    nextStageData.regWriteEnable := prevStageData.regWriteEnable
    nextStageData.csrRegWriteEnable := prevStageData.csrRegWriteEnable
    nextStageData.regWriteDataSel := prevStageData.regWriteDataSel
    nextStageData.csrRegWriteDataSel := prevStageData.csrRegWriteDataSel
    nextStageData.ecallEnable := prevStageData.ecallEnable

    dpi.foreach { dpiBundle =>
        when(state === s_wait_nextStage_ready) {
            dpiBundle.memWriteEnable := prevStageDataLatchedMemWriteEnable
            dpiBundle.memReadEnable := prevStageDataLatchedMemReadEnable
            dpiBundle.memAddr := prevStageDataLatchedAluOutput
            dpiBundle.memData := Mux(prevStageDataLatchedMemWriteEnable, prevStageDataLatchedStoreData, readDataAligned)
            dpiBundle.memStrobe := MuxLookup(prevStageDataLatchedLsType, 0.U((xLen / 8).W))(Seq(
                LoadAndStoreUnit.LS_S_B.U -> "b0001".U((xLen / 8).W),
                LoadAndStoreUnit.LS_S_H.U -> "b0011".U((xLen / 8).W),
                LoadAndStoreUnit.LS_S_W.U -> "b1111".U((xLen / 8).W)
            ))
            dpiBundle.memResp := rresp
            dpiBundle.memLsType := prevStageDataLatchedLsType
            dpiBundle.memPc := memPc
        }.otherwise {
            dpiBundle.memWriteEnable := false.B
            dpiBundle.memReadEnable := false.B
            dpiBundle.memAddr := 0.U
            dpiBundle.memData := 0.U
            dpiBundle.memStrobe := 0.U
            dpiBundle.memResp := 0.U
            dpiBundle.memLsType := LoadAndStoreUnit.LS_UNKNOWN.U
            dpiBundle.memPc := memPc
        }
    }
    dpi.foreach { dpiBundle => dpiBundle.mem_nextStage_valid := io.nextStage.valid }

    io.working := state =/= s_idle
}
