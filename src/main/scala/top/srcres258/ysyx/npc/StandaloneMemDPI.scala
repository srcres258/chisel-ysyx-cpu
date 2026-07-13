package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import top.srcres258.ysyx.npc.bus.AXI4
import top.srcres258.ysyx.npc.dpi.DPIInline
import chisel3.util.HasBlackBoxInline

class StandaloneMemDPI(xLen: Int) extends BlackBox with HasBlackBoxInline {
    val io = IO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Reset())
        val axi = Flipped(new AXI4(xLen))
    })

    setInline(s"${desiredName}.sv", DPIInline.standaloneMemEnabled(desiredName, io))
}
