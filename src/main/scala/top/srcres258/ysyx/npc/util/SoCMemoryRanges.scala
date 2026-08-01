package top.srcres258.ysyx.npc.util

/**
  * 定义外部 SoC (目前是 ysyxSoC) 的各外部设备的 MMIO 地址范围.
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
}
