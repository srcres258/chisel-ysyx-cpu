package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import top.srcres258.ysyx.npc.bus.AXI4

class StandaloneMemDPI(xLen: Int) extends BlackBox {
    val io = IO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Reset())
        val axi = Flipped(new AXI4(xLen))
    })
}
