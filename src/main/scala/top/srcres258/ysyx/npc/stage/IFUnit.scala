package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.Config
import top.srcres258.ysyx.npc.dpi.impl.IFUnitDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的取指 (Instruction Fetch) 单元.
  */
class IFUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)

    val io = IO(new Bundle {
        val executionInfo = Flipped(Decoupled(Flipped(new IFUnit.ExecutionInfo(xLen))))

        val lsuIfetchReq = Decoupled(Output(new LoadAndStoreUnit.IfetchReq(xLen)))
        val lsuIfetchResp = Flipped(Decoupled(Output(new LoadAndStoreUnit.IfetchResp(xLen))))

        val nextStage = Decoupled(Output(new IF_ID_Bundle(xLen)))

        val working = Output(Bool())
    });

    val dpi = if (Config.enableDPI) Some(IO(new IFUnitDPIBundle(xLen))) else None

    val pc = Wire(UInt(xLen.W))
    val instData = RegInit(0.U(xLen.W))

    val nextStageData = Wire(new IF_ID_Bundle(xLen))

    /* 
    IF 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待 ProcessorCore 输送执行信息 (executionInfo).
    2. waitData: 接收执行信息数据, 等待数据稳定到达.
    3. sendFetchReq: 向 LSU 发送取指请求.
    4. waitResp: 等待 LSU 返回取指数据.
    5. wait_nextStage_ready: 向下一处理器阶段单元输送已准备好的数据,
       然后等待下一处理器阶段单元的 ready 信号.
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 4 -> 5 -> 1 -> ...
    */
    val s_idle :: s_waitData :: s_sendFetchReq :: s_waitResp :: s_wait_nextStage_ready :: Nil = Enum(5)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.executionInfo.fire, s_waitData, s_idle),
        s_waitData -> s_sendFetchReq,
        s_sendFetchReq -> Mux(io.lsuIfetchReq.fire, s_waitResp, s_sendFetchReq),
        s_waitResp -> Mux(io.lsuIfetchResp.fire, s_wait_nextStage_ready, s_waitResp),
        s_wait_nextStage_ready -> Mux(io.nextStage.fire, s_idle, s_wait_nextStage_ready)
    ))
    io.executionInfo.ready := state === s_idle
    io.lsuIfetchReq.valid := state === s_sendFetchReq
    io.lsuIfetchReq.bits.addr := pc
    io.lsuIfetchResp.ready := state === s_waitResp
    io.nextStage.valid := state === s_wait_nextStage_ready
    io.nextStage.bits := nextStageData

    pc := io.executionInfo.bits.pc
    when(state === s_waitResp && io.lsuIfetchResp.fire) {
        instData := io.lsuIfetchResp.bits.data
    }

    nextStageData.pcCur := pc
    nextStageData.pcNext := pc + 4.U(xLen.W)
    nextStageData.inst := instData

    dpi.foreach { dpiBundle =>
        dpiBundle.if_nextStage_valid := io.nextStage.valid
        dpiBundle.instData := instData
    }

    io.working := state =/= s_idle
}

object IFUnit {
    class ExecutionInfo(val xLen: Int) extends Bundle {
        Assertion.assertProcessorXLen(xLen)
        
        val pc = Input(UInt(xLen.W))
    }
}
