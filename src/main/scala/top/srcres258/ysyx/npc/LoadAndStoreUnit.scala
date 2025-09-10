package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

/**
  * 访存单元
  */
class LoadAndStoreUnit(
    /**
      * xLen: 操作数位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val readDataOut = Output(UInt(xLen.W))
        val readDataIn = Input(UInt(xLen.W))
        val writeDataOut = Output(UInt(xLen.W))
        val writeDataIn = Input(UInt(xLen.W))
        val lsType = Input(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
        val dataStrobe = Output(UInt(4.W))
    })

    val lsTypeOH = UIntToOH(io.lsType)

    val lsTypeCasesR = Array.fill[UInt](1 << LoadAndStoreUnit.LS_TYPE_LEN)(io.readDataIn)
    lsTypeCasesR(LoadAndStoreUnit.LS_L_B) = io.readDataIn(xLen / 4 - 1, 0).asSInt.pad(xLen).asUInt
    lsTypeCasesR(LoadAndStoreUnit.LS_L_BU) = Cat(Fill(3 * xLen / 4, 0.U(1.W)), io.readDataIn(xLen / 4 - 1, 0))
    lsTypeCasesR(LoadAndStoreUnit.LS_L_H) = io.readDataIn(xLen / 2 - 1, 0).asSInt.pad(xLen).asUInt
    lsTypeCasesR(LoadAndStoreUnit.LS_L_HU) = Cat(Fill(xLen / 2, 0.U(1.W)), io.readDataIn(xLen / 2 - 1, 0))
    io.readDataOut := Mux1H(lsTypeOH, lsTypeCasesR.toIndexedSeq)

    val lsTypeCasesW = Array.fill[UInt](1 << LoadAndStoreUnit.LS_TYPE_LEN)(io.writeDataIn)
    lsTypeCasesW(LoadAndStoreUnit.LS_S_B) = Cat(Fill(3 * xLen / 4, 0.U(1.W)), io.writeDataIn(xLen / 4 - 1, 0))
    lsTypeCasesW(LoadAndStoreUnit.LS_S_H) = Cat(Fill(xLen / 2, 0.U(1.W)), io.writeDataIn(xLen / 2 - 1, 0))
    io.writeDataOut := Mux1H(lsTypeOH, lsTypeCasesW.toIndexedSeq)

    val lsTypeCasesStrobe = Array.fill[UInt](1 << LoadAndStoreUnit.LS_TYPE_LEN)(0.U(LoadAndStoreUnit.DATA_STROBE_LEN.W))
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_L_B)  = 0b0001.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_L_BU) = 0b0001.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_L_H)  = 0b0011.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_L_HU) = 0b0011.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_L_W)  = 0b1111.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_S_B)  = 0b0001.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_S_H)  = 0b0011.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    lsTypeCasesStrobe(LoadAndStoreUnit.LS_S_W)  = 0b1111.U(LoadAndStoreUnit.DATA_STROBE_LEN.W)
    io.dataStrobe := Mux1H(lsTypeOH, lsTypeCasesStrobe.toIndexedSeq)
}

object LoadAndStoreUnit {
    val LS_TYPE_LEN: Int = 4

    /* 
    W: 一个字 (4个字节)
    H: 半个字 (2个字节)
    B: 一个字节
     */
    val LS_L_W: Int = 0
    val LS_L_H: Int = 1
    val LS_L_HU: Int = 2
    val LS_L_B: Int = 3
    val LS_L_BU: Int = 4
    val LS_S_W: Int = 5
    val LS_S_H: Int = 6
    val LS_S_B: Int = 7
    val LS_UNKNOWN: Int = 1 << LS_TYPE_LEN - 1

    val DATA_STROBE_LEN: Int = 4
}
