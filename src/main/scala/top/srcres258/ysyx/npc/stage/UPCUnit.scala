package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

/**
  * 处理器的更新程序计数器 (Update Program Counter) 单元。
  */
class UPCUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val pc = Output(UInt(xLen.W))

        val prevStage = Input(new WB_UPC_Bundle(xLen))
    })

    io.pc := io.prevStage.pcTarget
}
