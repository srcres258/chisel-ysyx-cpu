package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.impl.IFUnitDPIBundle
import top.srcres258.ysyx.npc.device.PhysicalRAM
import top.srcres258.ysyx.npc.arbiter.RoundRobinArbiter
import top.srcres258.ysyx.npc.util.Assertion
import top.srcres258.ysyx.npc.bus.AXI4

/**
  * 处理器的取指 (Instruction Fetch) 单元.
  */
class IFUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val executionInfo = Flipped(Decoupled(Flipped(new IFUnit.ExecutionInfo(xLen))))
        val memBus = new AXI4(xLen)
        val arbiterReq = Output(Bool())
        val arbiterGranted = Input(Bool())
        val arbiterRelease = Output(Bool())
        val arbiterReleaseReady = Input(Bool())

        val nextStage = Decoupled(Output(new IF_ID_Bundle(xLen)))

        val dpi = new IFUnitDPIBundle(xLen)

        val working = Output(Bool())
    });

    val pc = Wire(UInt(xLen.W))
    val instData = RegInit(0.U(xLen.W))

    val nextStageData = Wire(new IF_ID_Bundle(xLen))

    /* 
    IF 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待 ProcessorCore 输送执行信息 (executionInfo).
    2. waitData: 接收执行信息数据, 等待数据稳定到达.
    3. waitArbiterGrant: 向仲裁器申请对 RAM 总线的访问权, 等待仲裁器放行.
    4. wait_arready: 向 RAM 总线的 AR 信道输送取指地址 (PC 的值) 后, 等待 AR 信道的 ready 信号.
    5. wait_rvalid: 等待 RAM 总线的 R 信道的 valid 信号.
    6. wait_arbiterReleaseReady: 从 RAM 总线的 R 信道取出指令数据, 准备下一处理器阶段单元的数据,
       然后向仲裁器释放对 RAM 总线的访问权, 等待仲裁器确认收到释放信号.
    7. wait_nextStage_ready: 向下一处理器阶段单元输送已准备好的数据,
       然后等待下一处理器阶段单元的 ready 信号.
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3:
        向仲裁器写入 req 信号.
    3 -> 4:
        1. 回复 executionInfo 信息 (executionInfo.ready <- true).
        2. 从 executionInfo 信息中获取 PC 值.
        3. 向 RAM 总线的 AR 信道输送 PC 值, 作为取指地址.
    4 -> 5:
        无 (还需等待 rvalid 信号才能获取到指令数据以便后续操作).
    5 -> 6:
        1. 回复来自 RAM 总线的 R 信道的信息 (rready <- true).
        2. 从 RAM 总线的 R 信道取出指令数据.
           TODO: 注意: 这里默认 RAM 已经成功读出了数据 (即忽略 rresp 信号的信息), 后续若要将情况更一般化, rresp 信号是需要处理的.
           可考虑将此情况反馈回 ProcessorCore, 让其进行异常情况处理;
           或将此情况顺着流水顺序沿阶段继续向下传播, 让后续处理器阶段单元处理异常情况.
        3. 准备下一处理器阶段单元的数据 (nextStageData).
    6 -> 7:
        向下一处理器阶段单元输送数据 (nextStage.valid <- true, nextStage.bits <- nextStageData).
    7 -> 1:
        无 (本轮 IF 操作处理完毕, 等待 ProcessorCore 的下一份执行信息).
     */
    val s_idle :: s_waitData :: s_waitArbiterGrant :: s_wait_arready :: s_wait_rvalid :: (
        s_wait_arbiterReleaseReady :: s_wait_nextStage_ready :: Nil) = Enum(7)

    // 注意, 我们对电路建模时, 不要采用上面描述状态机行为的行为式语言对电路进行行为建模.
    // 而是要回归组合逻辑, 即用组合逻辑描述各状态之间所处理的事务, 以及到下个状态的转移条件.
    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.executionInfo.fire, s_waitData, s_idle),
        s_waitData -> s_waitArbiterGrant,
        s_waitArbiterGrant -> Mux(io.arbiterGranted, s_wait_arready, s_waitArbiterGrant),
        s_wait_arready -> Mux(io.memBus.ar.fire, s_wait_rvalid, s_wait_arready),
        s_wait_rvalid -> Mux(io.memBus.r.fire, s_wait_arbiterReleaseReady, s_wait_rvalid),
        s_wait_arbiterReleaseReady -> Mux(io.arbiterReleaseReady, s_wait_nextStage_ready, s_wait_arbiterReleaseReady),
        s_wait_nextStage_ready -> Mux(io.nextStage.fire, s_idle, s_wait_nextStage_ready)
    ))
    io.executionInfo.ready := state === s_idle
    io.memBus.ar.valid := state === s_wait_arready
    io.memBus.r.ready := state === s_wait_rvalid
    io.memBus.aw.valid := false.B
    io.memBus.aw.bits := DontCare
    io.memBus.w.valid := false.B
    io.memBus.w.bits := DontCare
    io.memBus.b.ready := false.B
    io.nextStage.valid := state === s_wait_nextStage_ready
    io.nextStage.bits := nextStageData

    io.arbiterReq := state === s_waitArbiterGrant

    pc := io.executionInfo.bits.pc
    io.memBus.ar.bits.addr := pc
    io.memBus.ar.bits.id := 0.U
    io.memBus.ar.bits.len := 0.U
    io.memBus.ar.bits.size := ((xLen / 8) >> 1).U
    io.memBus.ar.bits.burst := AXI4.BURST_FIXED.U
    when(state === s_wait_rvalid && io.memBus.r.fire) {
        instData := io.memBus.r.bits.data
    }

    nextStageData.pcCur := pc
    nextStageData.pcNext := pc + 4.U(xLen.W)
    nextStageData.inst := instData

    io.arbiterRelease := state === s_wait_arbiterReleaseReady

    io.dpi.if_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}

object IFUnit {
    class ExecutionInfo(val xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)
        
        val pc = Input(UInt(xLen.W))
    }
}
