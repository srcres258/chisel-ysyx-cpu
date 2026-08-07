package top.srcres258.ysyx.npc.util

import chisel3._
import chisel3.util._

/**
  * 定义外部 SoC (目前是 ysyxSoC) 的各外部设备的 MMIO 地址范围,
  * 以及指令 Cache 的缓存策略.
  */
object SoCMemoryRanges {
    val DEVICES: Seq[(String, MemoryRange)] = Seq(
        ("uart", MemoryRange.ofSize(0x10000000L, 0x1000L)),
        ("gpio", MemoryRange.ofSize(0x10002000L, 0x10L)),
        ("keyboard", MemoryRange.ofSize(0x10011000L, 0x8L)),
        ("vga", MemoryRange.ofSize(0x21000000L, 0x200000L)),
        ("spi_controller", MemoryRange.ofSize(0x10001000L, 0x1000L)),
        ("spi_xip_flash", MemoryRange.ofSize(0x30000000L, 0x10000000L)),
        ("psram", MemoryRange.ofSize(0x80000000L, 0x400000L)),
        ("mrom", MemoryRange.ofSize(0x20000000L, 0x1000L)),
        ("sram", MemoryRange.ofSize(0x0f000000L, 0x2000L)),
        ("sdram", MemoryRange.ofSize(0xa0000000L, 0x8000000L))
    )

    /**
      * 指令 Cache 可缓存的地址区域 (正向允许列表).
      * 不在该列表内的任意地址一律视为不可缓存 (bypass).
      */
    val INST_CACHEABLE_REGIONS: Seq[MemoryRange] = Seq(
        MemoryRange.ofSize(0x30000000L, 0x10000000L),   // SPI XIP Flash
        MemoryRange.ofSize(0x80000000L, 0x400000L),     // PSRAM
        MemoryRange.ofSize(0xa0000000L, 0x8000000L)     // SDRAM
    )

    /**
      * 判断给定地址是否属于指令 Cache 可缓存区域.
      * 未知地址一律返回 false (bypass) —— 永不将未识别的地址当作可缓存.
      */
    def isInstCacheable(addr: UInt): Bool = {
        INST_CACHEABLE_REGIONS.map(_.isInRange(addr)).reduce(_ || _)
    }
}
