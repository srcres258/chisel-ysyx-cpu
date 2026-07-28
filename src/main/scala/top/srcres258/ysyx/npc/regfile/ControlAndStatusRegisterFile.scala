package top.srcres258.ysyx.npc.regfile

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.Config
import top.srcres258.ysyx.npc.dpi.impl.ControlAndStatusRegisterFileDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的控制与状态寄存器 (CSR) 文件模块.
  */
class ControlAndStatusRegisterFile(
    /**
      * xLen: 操作数位数.
      */
    val xLen: Int,
    /**
      * regAddrWidth: 寄存器 (CSR) 编号位数，在 RV32I 指令集中为 12.
      */
    val regAddrWidth: Int = 12
) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val readPort1 = new ControlAndStatusRegisterFile.ReadPort(xLen, regAddrWidth)
        val readPort2 = new ControlAndStatusRegisterFile.ReadPort(xLen, regAddrWidth)
        val readPort3 = new ControlAndStatusRegisterFile.ReadPort(xLen, regAddrWidth)
        val writePort1 = new ControlAndStatusRegisterFile.WritePort(xLen, regAddrWidth)
        val writePort2 = new ControlAndStatusRegisterFile.WritePort(xLen, regAddrWidth)
    })

    val dpi = if (Config.enableDPI) Some(IO(new ControlAndStatusRegisterFileDPIBundle(xLen))) else None

    val registers = RegInit(ControlAndStatusRegisterFile.RegisterBundle(xLen))
    val roRegisters = ControlAndStatusRegisterFile.ReadOnlyRegisterBundle(xLen)

    io.readPort1.readData := 0.U
    when(io.readPort1.readAddress.orR) {
        when(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MSTATUS.U(regAddrWidth.W)) {
            io.readPort1.readData := registers.mstatus
        }.elsewhen(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MTVEC.U(regAddrWidth.W)) {
            io.readPort1.readData := registers.mtvec
        }.elsewhen(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MEPC.U(regAddrWidth.W)) {
            io.readPort1.readData := registers.mepc
        }.elsewhen(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MCAUSE.U(regAddrWidth.W)) {
            io.readPort1.readData := registers.mcause
        }.elsewhen(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MTVAL.U(regAddrWidth.W)) {
            io.readPort1.readData := registers.mtval
        }.elsewhen(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MVENDORID.U(regAddrWidth.W)) {
            io.readPort1.readData := roRegisters.mvendorid
        }.elsewhen(io.readPort1.readAddress === ControlAndStatusRegisterFile.CSR_MARCHID.U(regAddrWidth.W)) {
            io.readPort1.readData := roRegisters.marchid
        }
    }

    io.readPort2.readData := registers.mepc
    io.readPort3.readData := registers.mtvec

    Seq(io.writePort1, io.writePort2).foreach(writePort => {
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
    })

    dpi.foreach { dpiBundle =>
        dpiBundle.csr_mstatus := registers.mstatus
        dpiBundle.csr_mtvec := registers.mtvec
        dpiBundle.csr_mepc := registers.mepc
        dpiBundle.csr_mcause := registers.mcause
        dpiBundle.csr_mtval := registers.mtval
        dpiBundle.csr_mvendorid := roRegisters.mvendorid
        dpiBundle.csr_marchid := roRegisters.marchid
    }
}

object ControlAndStatusRegisterFile {
    /**
      * 定义在处理器中需要实现的 CSR 寄存器.
      */
    class RegisterBundle(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val mstatus = UInt(xLen.W)
        val mtvec = UInt(xLen.W)
        val mepc = UInt(xLen.W)
        val mcause = UInt(xLen.W)
        val mtval = UInt(xLen.W)
    }

    object RegisterBundle {
        def apply(xLen: Int): RegisterBundle = {
            Assertion.assertProcessorXLen(xLen)

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
      * 定义在处理器中需要实现的只读 (read-only) CSR 寄存器.
      */
    class ReadOnlyRegisterBundle(xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val mvendorid = UInt(xLen.W)
        val marchid = UInt(xLen.W)
    }

    object ReadOnlyRegisterBundle {
        def apply(xLen: Int): ReadOnlyRegisterBundle = {
            Assertion.assertProcessorXLen(xLen)

            val default = Wire(new ReadOnlyRegisterBundle(xLen))

            /* read-only CSRs */
            // ysyx 规定: mvendorid 为 "ysyx" 的各个字符的 ASCII 码按大端序组合,
            // 即 0x79737978.
            default.mvendorid := 0x79737978L.U
            // ysyx 规定: marchid 为 ysyx 学号的数字部分的十进制表示.
            // 作者的 ysyx 学号为 ysyx_25070190, 对应的十进制表示为 25070190.
            default.marchid := 25070190.U

            default
        }
    }

    /**
      * 寄存器文件的读取端口.
      */
    class ReadPort(
        /**
         * xLen: 操作数位数.
         */
        xLen: Int,
        /**
         * regAddrWidth: 寄存器 (GPR) 编号位数, 在 RV32I 指令集中为 5.
         */
        regAddrWidth: Int = 12
    ) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        val readData = Output(UInt(xLen.W))
        val readAddress = Input(UInt(regAddrWidth.W))
    }

    object ReadPort {
        def defaultValuesForMaster(readPort: ReadPort): Unit = {
            readPort.readAddress := 0.U
        }

        def defaultValuesForSlave(readPort: ReadPort): Unit = {
            readPort.readData := 0.U
        }
    }

    /**
      * 寄存器文件的写入端口.
      */
    class WritePort(
        /**
         * xLen: 操作数位数.
         */
        xLen: Int,
        /**
         * regAddrWidth: 寄存器 (GPR) 编号位数, 在 RV32I 指令集中为 5.
         */
        regAddrWidth: Int = 12
    ) extends Bundle {
        Assertion.assertProcessorXLen(xLen)
        
        val writeEnable = Input(Bool())
        val writeData = Input(UInt(xLen.W))
        val writeAddress = Input(UInt(regAddrWidth.W))
    }

    object WritePort {
        def defaultValuesForMaster(writePort: WritePort): Unit = {
            writePort.writeEnable := false.B
            writePort.writeData := 0.U
            writePort.writeAddress := 0.U
        }
    }

    /* (处理器已经实现的) CSR 编号. */

    val CSR_MSTATUS: Int = 0x300
    val CSR_MTVEC: Int = 0x305
    val CSR_MEPC: Int = 0x341
    val CSR_MCAUSE: Int = 0x342
    val CSR_MTVAL: Int = 0x343
    val CSR_MVENDORID: Int = 0xF11
    val CSR_MARCHID: Int = 0xF12

    def defaultValuesForMaster(csrFile: ControlAndStatusRegisterFile): Unit = {
        for (readPort <- List(csrFile.io.readPort1, csrFile.io.readPort2, csrFile.io.readPort3)) {
            ReadPort.defaultValuesForMaster(readPort)
        }
        for (writePort <- List(csrFile.io.writePort1, csrFile.io.writePort2)) {
            WritePort.defaultValuesForMaster(writePort)
        }
    }

    def defaultValuesForSlave(csrFile: ControlAndStatusRegisterFile): Unit = {
        for (readPort <- List(csrFile.io.readPort1, csrFile.io.readPort2, csrFile.io.readPort3)) {
            ReadPort.defaultValuesForSlave(readPort)
        }
    }
}
