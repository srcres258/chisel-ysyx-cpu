package top.srcres258.ysyx.npc.regfile

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.Config
import top.srcres258.ysyx.npc.dpi.impl.GeneralPurposeRegisterFileDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的通用寄存器 (GPR) 文件模块.
  */
class GeneralPurposeRegisterFile(
    /**
      * xLen: 操作数位数.
      */
    val xLen: Int,
    /**
      * regAddrWidth: 寄存器 (GPR) 编号位数, 在 RV32I 指令集中为 5.
      */
    val regAddrWidth: Int = 5,
    /**
      * regCount: 寄存器数量. 要求数量必须在 regAddrWidth 可表示的范围内.
      * 例如在 RV32I 指令集中, regAddrWidth 为 5, 则 regCount 最大为 2^5 - 1 = 31.
      * (x0 硬编码为 0 不算有效寄存器.)
      */
    val gprCount: Int = 16
) extends Module {
    Assertion.assertProcessorXLen(xLen)
    assert(
        gprCount <= (1 << regAddrWidth) - 1,
        s"gprCount ($gprCount) must be less than or equal to 2^regAddrWidth (${1 << regAddrWidth})."
    )

    val io = IO(new Bundle {
        val readPort = new GeneralPurposeRegisterFile.ReadPort(xLen, regAddrWidth)
        val writePort = new GeneralPurposeRegisterFile.WritePort(xLen, regAddrWidth)
    })

    val dpi = if (Config.enableDPI) Some(IO(new GeneralPurposeRegisterFileDPIBundle(xLen))) else None

    val registers = RegInit(VecInit(Seq.fill(gprCount)(0.U(xLen.W))))

    io.readPort.readData1 := MuxLookup(io.readPort.readAddress1, 0.U)((1 until (gprCount + 1))
        .map(i => i.U -> registers(i - 1)).toSeq)
    io.readPort.readData2 := MuxLookup(io.readPort.readAddress2, 0.U)((1 until (gprCount + 1))
        .map(i => i.U -> registers(i - 1)).toSeq)

    when(io.writePort.writeEnable && io.writePort.writeAddress.orR) {
        for (i <- 1 until (gprCount + 1)) {
            when(io.writePort.writeAddress === i.U) {
                registers(i - 1) := io.writePort.writeData
            }
        }
    }

    dpi.foreach { dpiBundle =>
        dpiBundle.gprs(0) := 0.U
        for (i <- 1 until dpiBundle.gprs.length) {
            if (i <= gprCount) {
                dpiBundle.gprs(i) := registers(i - 1)
            } else {
                dpiBundle.gprs(i) := 0.U
            }
        }
    }
}

object GeneralPurposeRegisterFile {
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
        regAddrWidth: Int = 5
    ) extends Bundle {
        Assertion.assertProcessorXLen(xLen)

        // 提供两个读取端口 (考虑到 RISC-V 的 R 型指令需要同时读取两个寄存器).
        val readData1 = Output(UInt(xLen.W))
        val readData2 = Output(UInt(xLen.W))
        val readAddress1 = Input(UInt(regAddrWidth.W))
        val readAddress2 = Input(UInt(regAddrWidth.W))
    }

    object ReadPort {
        def defaultValuesForMaster(readPort: ReadPort): Unit = {
            readPort.readAddress1 := 0.U
            readPort.readAddress2 := 0.U
        }

        def defaultValuesForSlave(readPort: ReadPort): Unit = {
            readPort.readData1 := 0.U
            readPort.readData2 := 0.U
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
        regAddrWidth: Int = 5
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

    def defaultValuesForMaster(gprFile: GeneralPurposeRegisterFile): Unit = {
        ReadPort.defaultValuesForMaster(gprFile.io.readPort)
        WritePort.defaultValuesForMaster(gprFile.io.writePort)
    }

    def defaultValuesForSlave(gprFile: GeneralPurposeRegisterFile): Unit = {
        ReadPort.defaultValuesForSlave(gprFile.io.readPort)
        WritePort.defaultValuesForMaster(gprFile.io.writePort)
    }
}
