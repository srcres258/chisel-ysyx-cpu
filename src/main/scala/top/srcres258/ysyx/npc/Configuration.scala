package top.srcres258.ysyx.npc

object Configuration {
    val XLEN: Int = 32 // 32 位 RISC-V ISA, 处理器字长为 32.

    val PHYS_MEMORY_OFFSET: BigInt = BigInt(0x80000000L)
    // val PHYS_MEMORY_SIZE: BigInt = BigInt(1024L * 1024L * 128L)
    /* 
    TODO: 目前设置这么大内存, 好让 NPC 能够直接通过 DPI-C 经由仿真环境提供的 MMIO 方式访问外设.
    以后在 NPC 中通过 Xbar 以硬件方式把 MMIO 实现了, 需要改回上面被注释掉的代码的真实物理内存大小.
     */
    val PHYS_MEMORY_SIZE: BigInt = BigInt(0xb0000000L - 0x80000000L)

    val UART_MEMORY_OFFSET: BigInt = BigInt(0x10000000L)
    val UART_MEMORY_SIZE: BigInt = BigInt(0x1000L)

    val CLINT_MEMORY_OFFSET: BigInt = BigInt(0xa0000048L)
    val CLINT_MEMORY_SIZE: BigInt = BigInt(8)

    val PC_INITIAL_VAL: BigInt = BigInt(0x20000000L)

    val RANDOM_DELAY_WIDTH: Int = 4

    object Arbiter {
        val ARBITER_MAX_MASTER_AMOUNT: Int = 4
        val ARBITER_MASTER_IDX_IF_UNIT: Int = 0 // IFUnit
        val ARBITER_MASTER_IDX_MA_UNIT: Int = 1 // MAUnit
    }
}
