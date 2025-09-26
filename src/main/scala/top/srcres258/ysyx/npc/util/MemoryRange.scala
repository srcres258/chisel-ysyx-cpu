package top.srcres258.ysyx.npc.util

import chisel3._
import chisel3.util._

/**
  * 一段内存地址范围的闭区间. [start, end]
  */
case class MemoryRange(start: BigInt, end: BigInt) {
    def isInRange(addr: UInt): Bool = addr >= start.U && addr <= end.U
}

object MemoryRange {
    def ofSize(start: BigInt, size: BigInt): MemoryRange = MemoryRange(start, start + size - 1)
}
