package top.srcres258.ysyx.npc

case class ICacheConfig(blockBytes: Int = 4, numEntries: Int = 16) {
    require(blockBytes > 0 && (blockBytes & (blockBytes - 1)) == 0,
        s"ICacheConfig.blockBytes must be a positive power of 2, got $blockBytes")
    require(blockBytes >= 4,
        s"ICacheConfig.blockBytes must be at least 4 (xLen byte width), got $blockBytes")
    require(numEntries > 0 && (numEntries & (numEntries - 1)) == 0,
        s"ICacheConfig.numEntries must be a positive power of 2, got $numEntries")
}

object Config {
    sealed trait IntegrationMode
    case object Standalone extends IntegrationMode
    case object YsyxSoCIntegrated extends IntegrationMode

    var integrationMode: IntegrationMode = YsyxSoCIntegrated
    var enableDPI: Boolean = true
    var icacheConfig: ICacheConfig = ICacheConfig()

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
