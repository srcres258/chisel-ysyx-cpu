package top.srcres258.ysyx.npc.dpi.impl

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

class PhysicalRAMDPIBundle(val xLen: Int = 32) extends DPIBundle {
    val read = new PhysicalRAMDPIBundle.ReadPort(xLen)
    val write = new PhysicalRAMDPIBundle.WritePort(xLen)
}

object PhysicalRAMDPIBundle {
    class ReadPort(val xLen: Int) extends Bundle {
        val readEnable = Output(Bool())
        val readAddress = Output(UInt(xLen.W))
        val readData = Input(UInt(xLen.W))
    }

    class WritePort(val xLen: Int) extends Bundle {
        val dataStrobeLen: Int = xLen / 8

        val writeEnable = Output(Bool())
        val writeAddress = Output(UInt(xLen.W))
        val writeDataStrobe = Output(UInt(dataStrobeLen.W))
        val writeData = Output(UInt(xLen.W))
    }
}
