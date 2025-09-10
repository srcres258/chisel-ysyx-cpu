package top.srcres258.ysyx.npc.regfile

import chisel3._
import chisel3.util._

/**
  * 处理器的控制与状态寄存器 (CSR) 文件模块
  */
class ControlAndStatusRegisterFile(
    /**
      * xLen: 操作数位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32,
    /**
      * regAddrWidth: 寄存器 (CSR) 编号位数，在 RV32I 指令集中为 12
      */
    val regAddrWidth: Int = 12
) extends Module {
    val io = IO(new Bundle {
        val readPort1 = new ControlAndStatusRegisterFile.ReadPort(xLen, regAddrWidth)
        val readPort2 = new ControlAndStatusRegisterFile.ReadPort(xLen, regAddrWidth)
        val readPort3 = new ControlAndStatusRegisterFile.ReadPort(xLen, regAddrWidth)
        val writePort1 = new ControlAndStatusRegisterFile.WritePort(xLen, regAddrWidth)
        val writePort2 = new ControlAndStatusRegisterFile.WritePort(xLen, regAddrWidth)
        
        val registers = Output(Vec(1 << regAddrWidth, UInt(xLen.W)))
    })

    val registers = RegInit(ControlAndStatusRegisterFile.RegisterBundle(xLen))

    for (readPort <- List(io.readPort1, io.readPort2, io.readPort3)) {
        readPort.readData := 0.U
        when(readPort.readAddress.orR) {
            when(readPort.readAddress === ControlAndStatusRegisterFile.CSR_MSTATUS.U(regAddrWidth.W)) {
                readPort.readData := registers.mstatus
            }.elsewhen(readPort.readAddress === ControlAndStatusRegisterFile.CSR_MTVEC.U(regAddrWidth.W)) {
                readPort.readData := registers.mtvec
            }.elsewhen(readPort.readAddress === ControlAndStatusRegisterFile.CSR_MEPC.U(regAddrWidth.W)) {
                readPort.readData := registers.mepc
            }.elsewhen(readPort.readAddress === ControlAndStatusRegisterFile.CSR_MCAUSE.U(regAddrWidth.W)) {
                readPort.readData := registers.mcause
            }.elsewhen(readPort.readAddress === ControlAndStatusRegisterFile.CSR_MTVAL.U(regAddrWidth.W)) {
                readPort.readData := registers.mtval
            }
        }
    }

    for (writePort <- List(io.writePort1, io.writePort2)) {
        when(writePort.writeEnable && writePort.writeAddress.orR) {
            when(writePort.writeAddress === ControlAndStatusRegisterFile.CSR_MSTATUS.U(regAddrWidth.W)) {
                registers.mstatus := writePort.writeData
            }.elsewhen(writePort.writeAddress === ControlAndStatusRegisterFile.CSR_MTVEC.U(regAddrWidth.W)) {
                registers.mtvec := writePort.writeData
            }.elsewhen(writePort.writeAddress === ControlAndStatusRegisterFile.CSR_MEPC.U(regAddrWidth.W)) {
                registers.mepc := writePort.writeData
            }.elsewhen(writePort.writeAddress === ControlAndStatusRegisterFile.CSR_MCAUSE.U(regAddrWidth.W)) {
                registers.mcause := writePort.writeData
            }.elsewhen(writePort.writeAddress === ControlAndStatusRegisterFile.CSR_MTVAL.U(regAddrWidth.W)) {
                registers.mtval := writePort.writeData
            }
        }
    }

    for (i <- 0 until io.registers.length) {
        if (i == ControlAndStatusRegisterFile.CSR_MSTATUS) {
            io.registers(i) := registers.mstatus
        } else if (i == ControlAndStatusRegisterFile.CSR_MTVEC) {
            io.registers(i) := registers.mtvec
        } else if (i == ControlAndStatusRegisterFile.CSR_MEPC) {
            io.registers(i) := registers.mepc
        } else if (i == ControlAndStatusRegisterFile.CSR_MCAUSE) {
            io.registers(i) := registers.mcause
        } else if (i == ControlAndStatusRegisterFile.CSR_MTVAL) {
            io.registers(i) := registers.mtval
        } else {
            io.registers(i) := 0.U
        }
    }
}

object ControlAndStatusRegisterFile {
    /**
      * 定义在处理器中需要实现的 CSR 寄存器。
      */
    class RegisterBundle(
        /**
         * xLen: 操作数位数，在 RV32I 指令集中为 32
         */
        val xLen: Int = 32
    ) extends Bundle {
        val mstatus = UInt(xLen.W)
        val mtvec = UInt(xLen.W)
        val mepc = UInt(xLen.W)
        val mcause = UInt(xLen.W)
        val mtval = UInt(xLen.W)
    }
    object RegisterBundle {
        def apply(xLen: Int = 32): RegisterBundle = {
            val default = Wire(new RegisterBundle(xLen))
            default.mstatus := 0.U
            default.mtvec := 0.U
            default.mepc := 0.U
            default.mcause := 0.U
            default.mtval := 0.U
            default
        }
    }

    /**
      * 寄存器文件的读取端口
      */
    class ReadPort(
        /**
         * xLen: 操作数位数，在 RV32I 指令集中为 32
         */
        val xLen: Int = 32,
        /**
         * regAddrWidth: 寄存器 (GPR) 编号位数，在 RV32I 指令集中为 5
         */
        val regAddrWidth: Int = 12
    ) extends Bundle {
        val readData = Output(UInt(xLen.W))
        val readAddress = Input(UInt(regAddrWidth.W))
    }

    /**
      * 寄存器文件的写入端口
      */
    class WritePort(
        /**
         * xLen: 操作数位数，在 RV32I 指令集中为 32
         */
        val xLen: Int = 32,
        /**
         * regAddrWidth: 寄存器 (GPR) 编号位数，在 RV32I 指令集中为 5
         */
        val regAddrWidth: Int = 12
    ) extends Bundle {
        val writeEnable = Input(Bool())
        val writeData = Input(UInt(xLen.W))
        val writeAddress = Input(UInt(regAddrWidth.W))
    }

    /* (处理器已经实现的) CSR 编号 */

    val CSR_MSTATUS: Int = 0x300
    val CSR_MTVEC: Int = 0x305
    val CSR_MEPC: Int = 0x341
    val CSR_MCAUSE: Int = 0x342
    val CSR_MTVAL: Int = 0x343
}
