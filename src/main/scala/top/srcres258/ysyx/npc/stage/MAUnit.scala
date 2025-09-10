package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.dpi.DPIBundle

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

        val prevStage = Input(new EX_MA_Bundle(xLen))
        val nextStage = Output(new MA_WB_Bundle(xLen))
    })
    val ioDPI = DPIBundle.defaultIO()

    val dmemData = Wire(UInt(xLen.W))

    val readDataAligned = Wire(UInt(xLen.W))
    val writeDataUnaligned = Wire(UInt(xLen.W))

    val lsu = Module(new LoadAndStoreUnit)
    readDataAligned := lsu.io.readDataOut
    lsu.io.readDataIn := io.readData
    io.writeData := lsu.io.writeDataOut
    lsu.io.writeDataIn := writeDataUnaligned
    lsu.io.lsType := io.prevStage.lsType
    io.dataStrobe := lsu.io.dataStrobe

    io.address := io.prevStage.aluOutput
    dmemData := readDataAligned
    io.writeEnable := io.prevStage.memWriteEnable
    io.readEnable := io.prevStage.memReadEnable
    writeDataUnaligned := io.prevStage.storeData

    ioDPI.inst_jal := io.prevStage.inst_jal
    ioDPI.inst_jalr := io.prevStage.inst_jalr

    io.nextStage.pcNext := io.prevStage.pcNext
    io.nextStage.pcTarget := io.prevStage.pcTarget
    io.nextStage.memReadData := dmemData
    io.nextStage.aluOutput := io.prevStage.aluOutput
    io.nextStage.compBranchEnable := io.prevStage.compBranchEnable
    io.nextStage.imm := io.prevStage.imm
    io.nextStage.rd := io.prevStage.rd
    io.nextStage.rs1 := io.prevStage.rs1
    io.nextStage.rs2 := io.prevStage.rs2
    io.nextStage.regWriteEnable := io.prevStage.regWriteEnable
    io.nextStage.regWriteDataSel := io.prevStage.regWriteDataSel
}
