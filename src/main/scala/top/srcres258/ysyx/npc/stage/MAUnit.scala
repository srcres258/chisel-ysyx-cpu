package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.debug.DebugBundle

/**
  * 处理器的访存 (Memory Access) 单元。
  */
class MAUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val readEnable = Output(Bool())
        val readData = Input(UInt(xLen.W))
        val writeEnable = Output(Bool())
        val writeData = Output(UInt(xLen.W))
        val dataStrobe = Output(UInt(LoadAndStoreUnit.DATA_STROBE_LEN.W))
        val address = Output(UInt(xLen.W))

        val prevStage = Flipped(Decoupled(Output(new EX_MA_Bundle(xLen))))
        val nextStage = Decoupled(Output(new MA_WB_Bundle(xLen)))

        val debug = Output(new MAUnitDebugBundle(xLen))
    })

    val prevStageData = Wire(new EX_MA_Bundle(xLen))
    val nextStageData = Wire(new MA_WB_Bundle(xLen))
    val nextStagePrepared = RegInit(false.B)

    val s_prevStage_idle :: s_prevStage_waitReset :: Nil = Enum(2)
    val s_nextStage_idle :: s_nextStage_waitReady :: Nil = Enum(2)

    val prevStageState = RegInit(s_prevStage_idle)
    val nextStageState = RegInit(s_nextStage_idle)
    prevStageState := MuxLookup(prevStageState, s_prevStage_idle)(List(
        s_prevStage_idle -> Mux(io.prevStage.valid, s_prevStage_waitReset, s_prevStage_idle),
        s_prevStage_waitReset -> Mux(!io.prevStage.valid, s_prevStage_idle, s_prevStage_waitReset)
    ))
    nextStageState := MuxLookup(nextStageState, s_nextStage_idle)(List(
        s_nextStage_idle -> Mux(nextStagePrepared, s_nextStage_waitReady, s_nextStage_idle),
        s_nextStage_waitReady -> Mux(io.nextStage.ready, s_nextStage_idle, s_nextStage_waitReady)
    ))
    io.prevStage.ready := !nextStagePrepared
    io.nextStage.valid := nextStageState === s_nextStage_waitReady
    io.nextStage.bits := nextStageData
    prevStageData := io.prevStage.bits
    when(io.prevStage.valid) {
        nextStagePrepared := true.B
    }
    when(nextStagePrepared && io.nextStage.ready) {
        nextStagePrepared := false.B
    }

    val dmemData = Wire(UInt(xLen.W))

    val readDataAligned = Wire(UInt(xLen.W))
    val writeDataUnaligned = Wire(UInt(xLen.W))

    val lsu = Module(new LoadAndStoreUnit)
    readDataAligned := lsu.io.readDataOut
    lsu.io.readDataIn := io.readData
    io.writeData := lsu.io.writeDataOut
    lsu.io.writeDataIn := writeDataUnaligned
    lsu.io.lsType := prevStageData.lsType
    io.dataStrobe := lsu.io.dataStrobe

    io.address := prevStageData.aluOutput
    dmemData := readDataAligned
    io.writeEnable := prevStageData.memWriteEnable
    io.readEnable := prevStageData.memReadEnable
    writeDataUnaligned := prevStageData.storeData

    io.debug.memWriteEnable := prevStageData.memWriteEnable
    io.debug.memReadEnable := prevStageData.memReadEnable

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.pcTarget := prevStageData.pcTarget
    nextStageData.memReadData := dmemData
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
}

class MAUnitDebugBundle(val xLen: Int = 32) extends DebugBundle {
    val memWriteEnable = Bool()
    val memReadEnable = Bool()
}

object MAUnitDebugBundle {
    def apply(xLen: Int = 32): MAUnitDebugBundle = {
        val default = Wire(new MAUnitDebugBundle(xLen))

        default.memWriteEnable := false.B
        default.memReadEnable := false.B

        default
    }
}
