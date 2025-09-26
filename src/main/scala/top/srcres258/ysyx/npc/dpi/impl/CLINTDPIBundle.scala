package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle
import top.srcres258.ysyx.npc.util.Assertion

class CLINTDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val read = new CLINTDPIBundle.ReadPort(xLen)
    val write = new CLINTDPIBundle.WritePort(xLen)
}

object CLINTDPIBundle {
    class ReadPort(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val readEnable = Output(Bool())
        val readAddress = Output(UInt(xLen.W))
        val readData = Input(UInt(xLen.W))
    }

    class WritePort(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val dataStrobeLen: Int = xLen / 8

        val writeEnable = Output(Bool())
        val writeAddress = Output(UInt(xLen.W))
        val writeDataStrobe = Output(UInt(dataStrobeLen.W))
        val writeData = Output(UInt(xLen.W))
    }
}
