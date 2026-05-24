package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._
import top.srcres258.ysyx.npc.bus.AXI4

class SimpleAXI4RAM(xLen: Int, depth: Int = 32768) extends Module {
    val io = IO(new Bundle {
        val axi = Flipped(new AXI4(xLen))
    })

    val mem = Reg(Vec(depth, UInt(xLen.W)))

    val rdataReg = RegInit(0.U(xLen.W))
    val rvalidReg = RegInit(false.B)

    io.axi.ar.ready := !rvalidReg
    io.axi.r.valid := rvalidReg
    io.axi.r.bits.data := rdataReg
    io.axi.r.bits.resp := 0.U
    io.axi.r.bits.last := true.B
    io.axi.r.bits.id := 0.U

    io.axi.aw.ready := true.B
    io.axi.w.ready := true.B
    val bvalidReg = RegInit(false.B)
    io.axi.b.valid := bvalidReg
    io.axi.b.bits.resp := 0.U
    io.axi.b.bits.id := 0.U
    when(io.axi.aw.fire && io.axi.w.fire) {
        bvalidReg := true.B
    }.elsewhen(io.axi.b.fire) {
        bvalidReg := false.B
    }

    when(io.axi.ar.fire) {
        val wordAddr = (io.axi.ar.bits.addr >> 2.U)(log2Ceil(depth) - 1, 0)
        rdataReg := mem(wordAddr)
        rvalidReg := true.B
    }
    when(io.axi.r.fire) {
        rvalidReg := false.B
    }
    when(io.axi.aw.fire && io.axi.w.fire) {
        val wordAddr = (io.axi.aw.bits.addr >> 2.U)(log2Ceil(depth) - 1, 0)
        val wmask = io.axi.w.bits.strb
        val oldData = mem(wordAddr)
        val b0 = Mux(wmask(0), io.axi.w.bits.data(7, 0), oldData(7, 0))
        val b1 = Mux(wmask(1), io.axi.w.bits.data(15, 8), oldData(15, 8))
        val b2 = Mux(wmask(2), io.axi.w.bits.data(23, 16), oldData(23, 16))
        val b3 = Mux(wmask(3), io.axi.w.bits.data(31, 24), oldData(31, 24))
        mem(wordAddr) := Cat(b3, b2, b1, b0)
    }
}
