package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.DPIBundle

/**
  * 处理器的取指 (Instruction Fetch) 单元。
  */
class IFUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val input = Flipped(Decoupled(Output(new IFUnit.InputBundle(xLen))))

        val nextStage = Decoupled(Output(new IF_ID_Bundle(xLen)))
    });

    // val inputData = RegInit(IFUnit.InputBundle(xLen))
    val inputData = Wire(new IFUnit.InputBundle(xLen))
    val nextStageData = Wire(new IF_ID_Bundle(xLen))
    val nextStagePrepared = RegInit(false.B)

    val s_nextStage_idle :: s_nextStage_waitReady :: Nil = Enum(2)

    val nextStageState = RegInit(s_nextStage_idle)
    nextStageState := MuxLookup(nextStageState, s_nextStage_idle)(List(
        s_nextStage_idle -> Mux(nextStagePrepared, s_nextStage_waitReady, s_nextStage_idle),
        s_nextStage_waitReady -> Mux(io.nextStage.ready, s_nextStage_idle, s_nextStage_waitReady)
    ))
    io.nextStage.valid := nextStageState === s_nextStage_waitReady
    io.nextStage.bits := nextStageData

    inputData := io.input.bits
    io.input.ready := !nextStagePrepared
    when(io.input.valid) {
        nextStagePrepared := true.B
    }
    when(nextStagePrepared && io.nextStage.ready) {
        nextStagePrepared := false.B
    }

    nextStageData.pcCur := inputData.pc
    nextStageData.pcNext := inputData.pc + 4.U(xLen.W)
    nextStageData.inst := inputData.instData
}

object IFUnit {
    class InputBundle(
        /**
          * xLen: 处理器位数，在 RV32I 指令集中为 32
          */
        val xLen: Int = 32
    ) extends Bundle {
        val pc = UInt(xLen.W)
        val instData = UInt(xLen.W)
    }

    object InputBundle {
        def apply(xLen: Int = 32): InputBundle = {
            val result = Wire(new InputBundle(xLen))
            result.pc := 0.U(xLen.W)
            result.instData := 0.U(xLen.W)
            result
        }
    }
}
