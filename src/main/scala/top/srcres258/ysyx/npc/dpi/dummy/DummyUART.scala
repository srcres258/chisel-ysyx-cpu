package top.srcres258.ysyx.npc.dpi.dummy

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.dpi.impl.UARTDPIBundle

class DummyUART(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val dpi = Flipped(new UARTDPIBundle(xLen))
    })

    val writeData = RegInit(0.U(xLen.W))

    io.dpi.read.readData := "hDEADBEEF".U

    when(io.dpi.write.writeEnable) {
        writeData := io.dpi.write.writeData
    }
}
