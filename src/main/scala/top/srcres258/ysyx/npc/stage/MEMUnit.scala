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

        val dpi = new MEMUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val skip = RegInit(false.B)
    val rdata = RegInit(0.U(xLen.W))
    val rresp = RegInit(0.U(2.W))

    val prevStageData = Wire(new EX_MEM_Bundle(xLen))
    val nextStageData = Wire(new MEM_WB_Bundle(xLen))

    val s_idle :: s_waitData :: s_sendLsuReq :: s_waitLsuResp :: (
        s_clint_load_wait_arready :: s_clint_load_wait_rvalid :: (
        s_wait_nextStage_ready :: Nil)) = Enum(7)

    val state = RegInit(s_idle)

    val address = Wire(UInt(xLen.W))
    address := prevStageData.aluOutput
    val isClintAddr = address >= Config.CLINT_ADDR_BASE.U &&
                      address < (Config.CLINT_ADDR_BASE + Config.CLINT_ADDR_SIZE).U

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
    io.nextStage.bits := nextStageData

    io.lsuMemReq.valid := state === s_sendLsuReq
    io.lsuMemReq.bits.addr := address
    io.lsuMemReq.bits.writeData := prevStageData.storeData
    io.lsuMemReq.bits.isWrite := io.prevStage.bits.memWriteEnable
    io.lsuMemReq.bits.lsType := prevStageData.lsType
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

    when(state === s_waitData) {
        skip := !(io.prevStage.bits.memReadEnable || io.prevStage.bits.memWriteEnable)
    }

    val readDataAligned = Wire(UInt(xLen.W))
    val lsTypeOH = UIntToOH(prevStageData.lsType)

    val lsTypeCasesR = Array.fill[UInt](1 << LoadAndStoreUnit.LS_TYPE_LEN)(rdata)
    lsTypeCasesR(LoadAndStoreUnit.LS_L_B) = rdata(7, 0).asSInt.pad(xLen).asUInt
    lsTypeCasesR(LoadAndStoreUnit.LS_L_BU) = Cat(Fill(24, 0.U(1.W)), rdata(7, 0))
    lsTypeCasesR(LoadAndStoreUnit.LS_L_H) = rdata(15, 0).asSInt.pad(xLen).asUInt
    lsTypeCasesR(LoadAndStoreUnit.LS_L_HU) = Cat(Fill(16, 0.U(1.W)), rdata(15, 0))
    readDataAligned := Mux1H(lsTypeOH, lsTypeCasesR.toIndexedSeq)

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.pcTarget := prevStageData.pcTarget
    nextStageData.memReadData := Mux(skip, 0.U, readDataAligned)
    nextStageData.aluOutput := prevStageData.aluOutput
    nextStageData.compBranchEnable := prevStageData.compBranchEnable
    nextStageData.rs1Data := prevStageData.rs1Data
    nextStageData.imm := prevStageData.imm
    nextStageData.rd := prevStageData.rd
    nextStageData.rs1 := prevStageData.rs1
    nextStageData.rs2 := prevStageData.rs2
    nextStageData.csr := prevStageData.csr
    nextStageData.csrData := prevStageData.csrData
    nextStageData.zimm := prevStageData.zimm
    nextStageData.ecallCause := prevStageData.ecallCause
    nextStageData.regWriteEnable := prevStageData.regWriteEnable
    nextStageData.csrRegWriteEnable := prevStageData.csrRegWriteEnable
    nextStageData.regWriteDataSel := prevStageData.regWriteDataSel
    nextStageData.csrRegWriteDataSel := prevStageData.csrRegWriteDataSel
    nextStageData.ecallEnable := prevStageData.ecallEnable

    when(state === s_wait_nextStage_ready) {
        io.dpi.memWriteEnable := prevStageData.memWriteEnable
        io.dpi.memReadEnable := prevStageData.memReadEnable
    }.otherwise {
        io.dpi.memWriteEnable := false.B
        io.dpi.memReadEnable := false.B
    }
    io.dpi.mem_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}
