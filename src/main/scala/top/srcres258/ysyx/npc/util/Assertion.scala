package top.srcres258.ysyx.npc.util

import chisel3._

object Assertion {
    def assertProcessorXLen(xLen: Int): Unit = {
        assert(xLen == 32 || xLen == 64, s"Unsupported XLEN: $xLen. Only 32-bit or 64-bit processor core is supported.")
    }

    def assertMemoryAccessAddress(address: UInt): Unit = {
        assert(
            SoCMemoryRanges.DEVICES.map(entry => entry._2.isInRange(address)).reduce(_ || _),
            cf"Address 0x$address%8x out of range (not in any device's memory range)."
        )
    }
}
