package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.ControlUnit
import top.srcres258.ysyx.npc.regfile.GeneralPurposeRegisterFile

/**
  * 处理器的写回 (Write Back) 单元。
  */
class WBUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val gprWritePort = Flipped(new GeneralPurposeRegisterFile.WritePort(xLen))

        val prevStage = Input(new MA_WB_Bundle(xLen))
        val nextStage = Output(new WB_UPC_Bundle(xLen))
    })

    val gprData = Wire(UInt(32.W))

    {
        // default values
        // regData := DontCare
        gprData := 0.U(32.W)

        when(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_DMEM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            gprData := io.prevStage.memReadData
        }
        when(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_ALU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            gprData := io.prevStage.aluOutput
        }
        when(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_BCU.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            gprData := Cat(0.U(31.W), io.prevStage.compBranchEnable.asUInt)
        }
        when(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_IMM.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            gprData := io.prevStage.imm
        }
        when(io.prevStage.regWriteDataSel === ControlUnit.RD_MUX_PC_N.U(ControlUnit.RD_MUX_SEL_LEN.W)) {
            gprData := io.prevStage.pcNext
        }
    }

    io.gprWritePort.writeEnable := io.prevStage.regWriteEnable
    io.gprWritePort.writeData := gprData
    io.gprWritePort.writeAddress := io.prevStage.rd

    io.nextStage.pcTarget := io.prevStage.pcTarget
}
