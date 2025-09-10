package top.srcres258.ysyx.npc.regfile

import chisel3._
import chisel3.util._

/**
  * 处理器的通用寄存器 (GPR) 文件模块
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
        val readPort = new GeneralPurposeRegisterFile.ReadPort(xLen, regAddrWidth)
        val writePort = new GeneralPurposeRegisterFile.WritePort(xLen, regAddrWidth)

        val registers = Output(Vec(xLen, UInt(xLen.W)))
    })

    val registers = RegInit(VecInit(Seq.fill(xLen)(0.U(xLen.W))))

    io.readPort.readData1 := Mux(io.readPort.readAddress1.orR, registers(io.readPort.readAddress1), 0.U)
    io.readPort.readData2 := Mux(io.readPort.readAddress2.orR, registers(io.readPort.readAddress2), 0.U)

    when(io.writePort.writeEnable && io.writePort.writeAddress.orR) {
        registers(io.writePort.writeAddress) := io.writePort.writeData
    }

    for (i <- 0 until io.registers.length) {
        io.registers(i) := registers(i)
    }

    registers(0.U) := 0.U // RISC-V 规范规定：x0 寄存器恒为 0
}

object GeneralPurposeRegisterFile {
    /**
      * 通用寄存器文件的读取端口
      */
    class ReadPort(
        /**
         * xLen: 操作数位数，在 RV32I 指令集中为 32
         */
        val xLen: Int = 32,
        /**
         * regAddrWidth: 寄存器 (GPR) 编号位数，在 RV32I 指令集中为 5
         */
        val regAddrWidth: Int = 5
    ) extends Bundle {
        // 提供两个读取端口 (考虑到 RISC-V 的 R 型指令需要同时读取两个寄存器)
        val readData1 = Output(UInt(xLen.W))
        val readData2 = Output(UInt(xLen.W))
        val readAddress1 = Input(UInt(regAddrWidth.W))
        val readAddress2 = Input(UInt(regAddrWidth.W))
    }

    /**
      * 通用寄存器文件的写入端口
      */
    class WritePort(
        /**
         * xLen: 操作数位数，在 RV32I 指令集中为 32
         */
        val xLen: Int = 32,
        /**
         * regAddrWidth: 寄存器 (GPR) 编号位数，在 RV32I 指令集中为 5
         */
        val regAddrWidth: Int = 5
    ) extends Bundle {
        val writeEnable = Input(Bool())
        val writeData = Input(UInt(xLen.W))
        val writeAddress = Input(UInt(regAddrWidth.W))
    }
}
