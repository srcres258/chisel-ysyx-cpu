package top.srcres258.ysyx.npc.dpi.dummy

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.impl.PhysicalRAMDPIBundle
import top.srcres258.ysyx.npc.ysyx_25070190
import top.srcres258.ysyx.npc.util.Assertion

class DummyPhysicalRAM(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val dpi = Flipped(new PhysicalRAMDPIBundle(xLen))
    })

    val sram = SyncReadMem(DummyPhysicalRAM.RAM_SIZE, UInt(8.W))
    val readData = RegInit(0.U(xLen.W))

    when(io.dpi.read.readEnable) {
        val addr = Wire(UInt(xLen.W))
        addr := io.dpi.read.readAddress - DummyPhysicalRAM.RAM_ADDRESS_OFFSET
        readData := Range(0, xLen, 8).map(i => sram.read(addr + (i / 8).U) << i)
            .reduce(_ | _)
    }
    io.dpi.read.readData := readData

    when(io.dpi.write.writeEnable) {
        val addr = Wire(UInt(xLen.W))
        addr := io.dpi.write.writeAddress - DummyPhysicalRAM.RAM_ADDRESS_OFFSET
        val data = Wire(UInt(xLen.W))
        data := io.dpi.write.writeData
        val strobe = Wire(UInt(io.dpi.write.dataStrobeLen.W))
        strobe := io.dpi.write.writeDataStrobe
        Range(0, xLen, 8).foreach(i => when(strobe(i / 8).asBool) {
            sram.write(addr + (i / 8).U, (data >> i)(7, 0))
        })
    }
}

object DummyPhysicalRAM {
    val RAM_SIZE: Int = 1024 * 2 // 2 KB
    val RAM_ADDRESS_OFFSET: UInt = ysyx_25070190.PC_INITIAL_VAL
}
