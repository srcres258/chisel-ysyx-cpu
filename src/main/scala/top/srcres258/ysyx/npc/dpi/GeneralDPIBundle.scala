package top.srcres258.ysyx.npc.dpi

import chisel3._

import top.srcres258.ysyx.npc.dpi.impl._
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 用于向外部 DPI 提供信息的线集, 仅供外部后台仿真环境获取信息用.
  */
class GeneralDPIBundle(xLen: Int) extends DPIBundle {
    Assertion.assertProcessorXLen(xLen)

    val core = new ProcessorCoreDPIBundle(xLen)
    val physicalRAM = new PhysicalRAMDPIBundle(xLen)
    val uart = new UARTDPIBundle(xLen)
    val clint = new CLINTDPIBundle(xLen)
    val gpr = new GeneralPurposeRegisterFileDPIBundle(xLen)
    val csr = new ControlAndStatusRegisterFileDPIBundle(xLen)

    val ifu = new IFUnitDPIBundle(xLen)
    val idu = new IDUnitDPIBundle(xLen)
    val exu = new EXUnitDPIBundle(xLen)
    val mau = new MAUnitDPIBundle(xLen)
    val wbu = new WBUnitDPIBundle(xLen)
    val upcu = new UPCUnitDPIBundle(xLen)
}
