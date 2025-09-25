package top.srcres258.ysyx.npc.util

object Assertion {
    def assertProcessorXLen(xLen: Int): Unit = {
        assert(xLen == 32 || xLen == 64, s"Unsupported XLEN: $xLen. Only 32-bit or 64-bit processor core is supported.")
    }
}
