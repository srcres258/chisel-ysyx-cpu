package top.srcres258.ysyx.npc

object Config {
    sealed trait IntegrationMode
    case object Standalone extends IntegrationMode
    case object YsyxSoCIntegrated extends IntegrationMode

    var INTEGRATION_MODE: IntegrationMode = YsyxSoCIntegrated

    val XLEN: Int = 32

    val PHYS_MEMORY_OFFSET: BigInt = BigInt(0x80000000L)
    val PHYS_MEMORY_SIZE: BigInt = BigInt(0xb0000000L - 0x80000000L)

    val UART_MEMORY_OFFSET: BigInt = BigInt(0x10000000L)
    val UART_MEMORY_SIZE: BigInt = BigInt(0x1000L)

    val ACLINT_MTIME_BASE: BigInt = BigInt(0x0200bff8L)
    val ACLINT_MTIME_SIZE: BigInt = BigInt(8)

    val CLINT_ADDR_BASE: BigInt = BigInt(0x02000000L)
    val CLINT_ADDR_SIZE: BigInt = BigInt(0x10000L)

    val PC_INITIAL_VAL: BigInt = BigInt(0x30000000L)

    val RANDOM_DELAY_WIDTH: Int = 4

    object Arbiter {
        val ARBITER_MAX_MASTER_AMOUNT: Int = 4
        val ARBITER_MASTER_IDX_IF_UNIT: Int = 0
        val ARBITER_MASTER_IDX_MEM_UNIT: Int = 1
    }
}
