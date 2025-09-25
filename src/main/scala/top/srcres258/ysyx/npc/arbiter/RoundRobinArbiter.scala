package top.srcres258.ysyx.npc.arbiter

import chisel3._
import chisel3.util._

/**
  * 轮询仲裁器 Round-Robin Arbiter (a.k.a. 循环优先级仲裁器).
  * 
  * 特点: 会依次轮流给予各个请求者 (主设备) 授权机会, 每个请求者可公平地获得对目的从设备访问权.
  */
class RoundRobinArbiter(
    /**
      * 最多允许的请求者 (主设备) 数量.
      */
    val num: Int
) extends Module {
    require(num > 0, "Number of masters must be greater than 0.")

    private val idxWidthNum = RoundRobinArbiter.calcIdxWidth(num)
    private val idxWidth = idxWidthNum.W
    private val normalIdxWidthNum = RoundRobinArbiter.calcNormalIdxWidth(num)
    private val normalIdxWidth = normalIdxWidthNum.W

    val io = IO(new RoundRobinArbiter.IOBundle(num))

    /**
      * 轮询状态: 记录上一次授权的请求者索引, 初始值为 num - 1.
      */
    val lastGrantIdx = RegInit((num - 1).U(normalIdxWidth))
    /**
      * 当前被授权的请求者索引.
      */
    val curGrantIdx = RegInit((num - 1).U(normalIdxWidth))
    /**
      * 优先查找的起始索引.
      */
    val startIdx = Wire(UInt(normalIdxWidth))
    /**
      * 是否查找到了可以进行授权的请求者.
      */
    val found = Wire(Bool())
    /**
      * 当前已授权的请求者是否已发出释放信号.
      */
    val release = Wire(Bool())

    /* 
    PhysicalRAM 模块的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待来自各请求者的请求信号.
    2. waitRelease: 等待当前被授权访问的请求者释放访问权.
    3. replyRelease: 使用一个时钟周期回复当前被授权访问的请求者的释放访问权信号.

    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2 或 1 (保持不变):
        1. 根据上一次授权的请求者索引, 开始进行轮询, 选出下一个发出仲裁请求的请求者.
        2. 若按照顺序找到下一个发出仲裁请求的请求者, 则将访问权授予该请求者, 然后流转到状态 2.
        3. 若没有找到下一个发出仲裁请求的请求者, 则输出仲裁信号为 "当前无任何请求者被授权", 然后保持状态不变.
    2 -> 3:
        无 (等待当前被授权访问的请求者释放访问权).
    3 -> 1:
        回复当前被授权访问的请求者的释放访问权信号.
     */
    val s_idle :: s_waitRelease :: s_replyRelease :: Nil = Enum(3)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(found, s_waitRelease, s_idle),
        s_waitRelease -> Mux(release, s_replyRelease, s_waitRelease),
        s_replyRelease -> s_idle
    ))

    startIdx := (lastGrantIdx + 1.U) % num.U // 从 lastGrantIdx + 1 开始, 循环查找
    val grantIdxTemp = Wire(UInt(idxWidth))
    grantIdxTemp := num.U(idxWidth) // 默认为 num 表示无授权.
    // 轮询查找下一个有请求的请求者.
    found := (0 until num).map(i => io.req((startIdx + i.U) % num.U))
        .reduce(_ || _)
    grantIdxTemp := (0 to num).map(i => (startIdx + i.U) % num.U)
        .reduceRight((latter, former) => Mux(io.req(former(normalIdxWidthNum - 1, 0)), former, latter))
    when(state === s_idle && found) {
        curGrantIdx := grantIdxTemp
    }

    release := io.release(curGrantIdx).valid

    for (i <- 0 until num) {
        io.release(i).ready := i.U === curGrantIdx
    }
    val grantIdx = Wire(UInt(idxWidth))
    grantIdx := Mux(state === s_idle, num.U, curGrantIdx)
    io.grantIdx := grantIdx
    io.grantIdxNormal := grantIdx(normalIdxWidthNum - 1, 0)
}

object RoundRobinArbiter {
    /**
      * Round-Robin Arbiter 的 IO 接口, IO 主动方为从设备 (slave).
      */
    class IOBundle(val num: Int) extends Bundle {
        /**
          * 来自各请求者的请求信号.
          */
        val req = Input(Vec(num, Bool()))
        /**
          * 来自各请求者的释放信号 (需要握手).
          */
        val release = Vec(num, Flipped(Decoupled()))

        /**
          * 当前被授权的请求者索引.
          * 
          * 合法范围: [0, num - 1]. 若当前无请求者向该仲裁器提出请求 (空闲),
          * 则输出 num (表示无请求者被授权).
          */
        val grantIdx = Output(UInt(RoundRobinArbiter.calcIdxWidth(num).W)) // 可输出 num 表示无请求.
        /**
          * 当前被授权的请求者索引. 输出值同 grantIdx, 但位数为请求者索引合法的情况, 即不会多出数位表示无请求的情况.
          */
        val grantIdxNormal = Output(UInt(RoundRobinArbiter.calcNormalIdxWidth(num).W))
    }

    object IOBundle {
        def defaultValuesForMaster(io: IOBundle): Unit = {
            for (i <- 0 until io.num) {
                io.req(i) := false.B
                io.release(i).valid := false.B
            }
        }
    }

    private def calcIdxWidth(num: Int): Int = log2Ceil(num + 1)

    private def calcNormalIdxWidth(num: Int): Int = log2Ceil(num)
}
