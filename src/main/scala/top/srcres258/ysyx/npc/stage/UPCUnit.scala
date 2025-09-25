package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.dpi.impl.UPCUnitDPIBundle
import top.srcres258.ysyx.npc.util.Assertion

/**
  * 处理器的更新程序计数器 (Update Program Counter) 单元.
  */
class UPCUnit(val xLen: Int) extends Module {
    Assertion.assertProcessorXLen(xLen)
    
    val io = IO(new Bundle {
        val pcOutput = Decoupled(Output(UInt(xLen.W)))

        val prevStage = Flipped(Decoupled(Output(new WB_UPC_Bundle(xLen))))

        val dpi = new UPCUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val prevStageData = Wire(new WB_UPC_Bundle(xLen))

    /* 
    UPC 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 WB 单元传送数据.
    2. waitData: 接收来自上游 MA 单元传送的数据, 等待数据稳定到达.
    3. wait_pcOutput_ready: 通过组合逻辑计算要输出的更新后的 PC 地址并输送,
       然后等待来自接收方 (ProcessorCore) 的 ready 信号.
       (注: UPC 单元的工作在单周期内即可完成, 所以无需存在中间的工作状态.)
    
    状态流转方式:
    1 (初始状态) -> 2 -> 3 -> 1 -> ...

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让上游数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3:
        1. 回复上游 WB 单元的传递数据请求.
        2. 取出来自 WB 单元的数据, 对数据用组合逻辑进行处理, 得出更新后的 PC 地址数据.
        3. 向 ProcessorCore 输送数据.
    3 -> 1:
        无 (本轮 UPC 操作处理完毕, 等待来自上游 WB 单元的下一份数据).
     */
    val s_idle :: s_waitData :: s_wait_pcOutput_ready :: Nil = Enum(3)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.prevStage.fire, s_waitData, s_idle),
        s_waitData -> s_wait_pcOutput_ready,
        s_wait_pcOutput_ready -> Mux(io.pcOutput.fire, s_idle, s_wait_pcOutput_ready)
    ))
    io.prevStage.ready := state === s_idle
    prevStageData := io.prevStage.bits

    io.pcOutput.bits := prevStageData.pcTarget
    io.pcOutput.valid := state === s_wait_pcOutput_ready

    io.dpi.upcu_pcOutput_valid := io.pcOutput.valid

    io.working := state =/= s_idle
}
