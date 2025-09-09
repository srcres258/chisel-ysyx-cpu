package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

/**
  * 处理器的通用寄存器 (GPR) 模块
  */
class GeneralPurposeRegisterFile(
    /**
      * xLen: 操作数位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32,
    /**
      * regAddrWidth: 寄存器 (GPR) 编号位数，在 RV32I 指令集中为 5
      */
    val regAddrWidth: Int = 5
) extends Module {
    val io = IO(new Bundle {
        val readData1 = Output(UInt(xLen.W))
        val readData2 = Output(UInt(xLen.W))
        val writeEnable = Input(Bool())
        val writeData = Input(UInt(xLen.W))
        val writeAddress = Input(UInt(regAddrWidth.W))
        val readAddress1 = Input(UInt(regAddrWidth.W))
        val readAddress2 = Input(UInt(regAddrWidth.W))

        val registers = Output(Vec(xLen, UInt(xLen.W)))
    })

    val registers = RegInit(VecInit(Seq.fill(xLen)(0.U(xLen.W))))

    io.readData1 := Mux(io.readAddress1.orR, registers(io.readAddress1), 0.U)
    io.readData2 := Mux(io.readAddress2.orR, registers(io.readAddress2), 0.U)

    when(io.writeEnable && io.writeAddress.orR) {
        registers(io.writeAddress) := io.writeData
    }

    for (i <- 0 until io.registers.length) {
        io.registers(i) := registers(i)
    }

    registers(0.U) := 0.U // RISC-V 规范规定：x0 寄存器恒为 0
}
