package top.srcres258.ysyx.npc

object Config {
    sealed trait IntegrationMode
    case object Standalone extends IntegrationMode
    case object YsyxSoCIntegrated extends IntegrationMode

    var integrationMode: IntegrationMode = YsyxSoCIntegrated
    var enableDPI: Boolean = true

    val xlen: Int = 32

    val physMemoryOffset: BigInt = BigInt(0x80000000L)
    val physMemorySize: BigInt = BigInt(0xb0000000L - 0x80000000L)

    val uartMemoryOffset: BigInt = BigInt(0x10000000L)
    val uartMemorySize: BigInt = BigInt(0x1000L)

    val aclintMtimeBase: BigInt = BigInt(0x0200bff8L)
    val aclintMtimeSize: BigInt = BigInt(8)

    val clintAddrBase: BigInt = BigInt(0x02000000L)
    val clintAddrSize: BigInt = BigInt(0x10000L)

    val pcInitialVal: BigInt = BigInt(0x30000000L)

    val randomDelayWidth: Int = 4
}
