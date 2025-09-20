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
        val pcOutput = Decoupled(Output(UInt(xLen.W)))

        val prevStage = Flipped(Decoupled(Output(new WB_UPC_Bundle(xLen))))
    })

    val pcOutputValid = RegInit(false.B)

    val prevStageData = Wire(new WB_UPC_Bundle(xLen))

    val s_prevStage_idle :: s_prevStage_waitReset :: Nil = Enum(2)
    val s_pcOutput_idle :: s_pcOutput_waitReady :: Nil = Enum(2)

    val prevStageState = RegInit(s_prevStage_idle)
    prevStageState := MuxLookup(prevStageState, s_prevStage_idle)(List(
        s_prevStage_idle -> Mux(io.prevStage.valid, s_prevStage_waitReset, s_prevStage_idle),
        s_prevStage_waitReset -> Mux(!io.prevStage.valid, s_prevStage_idle, s_prevStage_waitReset)
    ))
    io.prevStage.ready := !pcOutputValid
    prevStageData := io.prevStage.bits
    when(io.prevStage.valid) {
        pcOutputValid := true.B
    }
    when(pcOutputValid && io.pcOutput.ready) {
        pcOutputValid := false.B
    }

    io.pcOutput.bits := prevStageData.pcTarget
    io.pcOutput.valid := pcOutputValid
}
