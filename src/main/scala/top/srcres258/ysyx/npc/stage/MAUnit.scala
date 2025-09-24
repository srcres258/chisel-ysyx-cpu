package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.LoadAndStoreUnit
import top.srcres258.ysyx.npc.dpi.impl.MAUnitDPIBundle
import top.srcres258.ysyx.npc.PhysicalRAM
import top.srcres258.ysyx.npc.bus.AXI4Lite
import top.srcres258.ysyx.npc.arbiter.RoundRobinArbiter

/**
  * 处理器的访存 (Memory Access) 单元。
  */
class MAUnit(
    /**
      * xLen: 处理器位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val ramBus = new AXI4Lite(xLen)
        val arbiterReq = Output(Bool())
        val arbiterGranted = Input(Bool())
        val arbiterRelease = Output(Bool())
        val arbiterReleaseReady = Input(Bool())

        val prevStage = Flipped(Decoupled(Output(new EX_MA_Bundle(xLen))))
        val nextStage = Decoupled(Output(new MA_WB_Bundle(xLen)))

        val dpi = new MAUnitDPIBundle(xLen)

        val working = Output(Bool())
    })

    val skip = RegInit(false.B)
    val rdata = RegInit(0.U(xLen.W))
    val rresp = RegInit(0.U(AXI4Lite.RESP_WIDTH.W))
    val bresp = RegInit(0.U(AXI4Lite.RESP_WIDTH.W))

    val prevStageData = Wire(new EX_MA_Bundle(xLen))
    val nextStageData = Wire(new MA_WB_Bundle(xLen))

    /* 
    MA 单元的所有状态 (从状态机视角考虑):
    1. idle: 空闲状态, 等待上游 EX 单元传送数据.
    2. waitData: 接收来自上游 EX 单元传送的数据, 等待数据稳定到达.
    3. waitArbiterGrant: 向仲裁器申请对 RAM 总线的访问权, 等待仲裁器放行.
    4. load_wait_arready (读事务分支): 向 RAM 总线的 AR 信道输送读地址后, 等待 AR 信道的 ready 信号.
    5. load_wait_rvalid (读事务分支): 等待 RAM 总线的 R 信道的 valid 信号.
    6. store_wait_awready (写事务分支): 向 RAM 总线的 AW 信道输送写地址后, 等待 AW 信道的 ready 信号.
    7. store_wait_wready (写事务分支): 向 RAM 总线的 W 信道输送写数据后, 等待 W 信道的 ready 信号.
    8. store_wait_bvalid (写事务分支): 等待 RAM 总线的 B 信道的 valid 信号.
    9. wait_arbiterReleaseReady: 准备下一处理器阶段单元的数据, 然后向仲裁器释放对 RAM 总线的访问权, 等待仲裁器确认收到释放信号.
    10. wait_nextStage_ready: 向下一处理器阶段单元输送数据, 然后等待下一处理器阶段单元的 ready 信号.
    
    状态流转方式:
      读事务分支:       +-> 3 -> 4 -> 5      --+
    1 (初始状态) -> 2 --+----+-----------------+-> 9 -> 10 -> 1 -> ...
      写事务分支:       +-> 3 -> 6 -> 7 -> 8 --+

    各状态之间所处理的事务:
    1 -> 2:
        等待一个时钟周期, 让上游数据平稳传递后再处理.
        (防止因为数据还没到达就处理, 造成使用错误的数据处理事务.)
    2 -> 3 或 10:
        1. 回复上游 EX 单元的传递数据请求.
        2. 取出来自 EX 单元的数据, 对数据用组合逻辑进行处理, 为主存的读写事务准备数据,
           并得到 读事务 / 写事务 的判断信号.
        3. 根据判断信号, 流转到状态 3 或状态 10.
           (1) 若流转到状态 3, 还需要向仲裁器写入 req 信号.
           (2) 若流转到状态 10, 还需要将跳过寄存器置高电平, 以表示本轮操作无事务处理,
               仅将已有数据传递给下一处理器阶段.
    3 -> 4 或 6: (读事务分支) (写事务分支)
        根据 读事务 / 写事务 的判断信号, 流转到状态 4 或状态 6.
        (1) 若流转到状态 4, 还需要:
            a. 将跳过寄存器置低电平.
            b. 向 RAM 总线的 AR 信道输送访存地址.
        (2) 若流转到状态 6, 还需要:
            a. 将跳过寄存器置低电平.
            b. 向 RAM 总线的 AW 信道输送访存地址.
    4 -> 5: (读事务分支)
        无 (还需等待 rvalid 信号, 才能获取到来自 RAM 读出的数据, 以及 rresp 状态信号以便后续操作).
    6 -> 7: (写事务分支)
        根据前序准备数据, 向 RAM 总线的 W 信道输送访存数据.
    7 -> 8: (写事务分支)
        无 (还需等待 bvalid 信号, 才能获取到 bresp 状态信号以判断 RAM 对于写操作的处理结果).
    5 或 8 -> 9:
        (若本轮操作未处理任何事务, 则跳过步骤 1 和步骤 2)
        1. 根据前序阶段的具体分支类型, 回复来自相应信道的信息 (rready <- true 或 bready <- true).
        2. 汇总前序事务的处理结果, 生成下一处理器阶段单元的数据 (nextStageData).
           注意: 这里默认 RAM 的访存操作是成功的 (即忽略 rresp 和 bresp 信号的信息),
           后续若要将情况更一般化, 这些反馈信号是需要处理的.
           可考虑将此情况顺着流水顺序沿阶段继续向下传播, 让后续处理器阶段单元处理异常情况.
        3. 向下一处理器阶段单元输送数据.
    9 -> 10:
        向下一处理器阶段单元输送数据.
    10 -> 1:
        无 (本轮 MA 操作处理完毕, 等待来自上游 EX 单元的下一份数据).
     */
    val s_idle :: s_waitData :: s_waitArbiterGrant :: (
        s_load_wait_arready :: s_load_wait_rvalid :: (
        s_store_wait_awready :: s_store_wait_wready :: s_store_wait_bvalid :: (
        s_wait_arbiterReleaseReady :: s_wait_nextStage_ready :: Nil))) = Enum(10)

    val state = RegInit(s_idle)
    state := MuxLookup(state, s_idle)(List(
        s_idle -> Mux(io.prevStage.fire, s_waitData, s_idle),
        s_waitData -> Mux(io.prevStage.bits.memReadEnable || io.prevStage.bits.memWriteEnable, s_waitArbiterGrant, s_wait_nextStage_ready),
        s_waitArbiterGrant -> MuxCase(s_wait_arbiterReleaseReady, Seq(
            io.prevStage.bits.memReadEnable -> s_load_wait_arready,
            io.prevStage.bits.memWriteEnable -> s_store_wait_awready
        )),

        s_load_wait_arready -> Mux(io.ramBus.ar.fire, s_load_wait_rvalid, s_load_wait_arready),
        s_load_wait_rvalid -> Mux(io.ramBus.r.fire, s_wait_arbiterReleaseReady, s_load_wait_rvalid),
        
        s_store_wait_awready -> Mux(io.ramBus.aw.fire, s_store_wait_wready, s_store_wait_awready),
        s_store_wait_wready -> Mux(io.ramBus.w.fire, s_store_wait_bvalid, s_store_wait_wready),
        s_store_wait_bvalid -> Mux(io.ramBus.b.fire, s_wait_arbiterReleaseReady, s_store_wait_bvalid),

        s_wait_arbiterReleaseReady -> Mux(io.arbiterReleaseReady, s_wait_nextStage_ready, s_wait_arbiterReleaseReady),
        s_wait_nextStage_ready -> Mux(io.nextStage.fire, s_idle, s_wait_nextStage_ready)
    ))
    io.prevStage.ready := state === s_idle
    io.ramBus.ar.valid := state === s_load_wait_arready
    io.ramBus.r.ready := state === s_load_wait_rvalid
    io.ramBus.aw.valid := state === s_store_wait_awready
    io.ramBus.w.valid := state === s_store_wait_wready
    io.ramBus.b.ready := state === s_store_wait_bvalid
    io.nextStage.valid := state === s_wait_nextStage_ready
    prevStageData := io.prevStage.bits
    io.nextStage.bits := nextStageData
    
    when(state === s_waitData) {
        skip := !(io.prevStage.bits.memReadEnable || io.prevStage.bits.memWriteEnable)
    }

    io.arbiterReq := state === s_waitArbiterGrant

    val address = Wire(UInt(xLen.W))
    address := prevStageData.aluOutput

    val lsu = Module(new LoadAndStoreUnit(xLen))
    lsu.io.lsType := prevStageData.lsType

    val readDataAligned = Wire(UInt(xLen.W))
    io.ramBus.ar.bits.addr := Mux(io.prevStage.bits.memReadEnable, address, 0.U)
    when(state === s_load_wait_rvalid && io.ramBus.r.fire) {
        rdata := io.ramBus.r.bits.data
        rresp := io.ramBus.r.bits.resp
    }
    lsu.io.readDataIn := rdata
    readDataAligned := lsu.io.readDataOut

    val writeDataUnaligned = Wire(UInt(xLen.W))
    io.ramBus.aw.bits.addr := Mux(io.prevStage.bits.memWriteEnable, address, 0.U)
    writeDataUnaligned := prevStageData.storeData
    lsu.io.writeDataIn := writeDataUnaligned
    io.ramBus.w.bits.data := lsu.io.writeDataOut
    io.ramBus.w.bits.strb := lsu.io.dataStrobe
    when(state === s_store_wait_bvalid && io.ramBus.b.fire) {
        bresp := io.ramBus.b.bits.resp
    }

    nextStageData.pcCur := prevStageData.pcCur
    nextStageData.pcNext := prevStageData.pcNext
    nextStageData.pcTarget := prevStageData.pcTarget
    nextStageData.memReadData := Mux(skip, 0.U, readDataAligned)
    nextStageData.aluOutput := prevStageData.aluOutput
    nextStageData.compBranchEnable := prevStageData.compBranchEnable
    nextStageData.rs1Data := prevStageData.rs1Data
    nextStageData.imm := prevStageData.imm
    nextStageData.rd := prevStageData.rd
    nextStageData.rs1 := prevStageData.rs1
    nextStageData.rs2 := prevStageData.rs2
    nextStageData.csr := prevStageData.csr
    nextStageData.csrData := prevStageData.csrData
    nextStageData.zimm := prevStageData.zimm
    nextStageData.ecallCause := prevStageData.ecallCause
    nextStageData.regWriteEnable := prevStageData.regWriteEnable
    nextStageData.csrRegWriteEnable := prevStageData.csrRegWriteEnable
    nextStageData.regWriteDataSel := prevStageData.regWriteDataSel
    nextStageData.csrRegWriteDataSel := prevStageData.csrRegWriteDataSel
    nextStageData.ecallEnable := prevStageData.ecallEnable

    io.arbiterRelease := state === s_wait_arbiterReleaseReady

    when(state === s_wait_nextStage_ready) {
        io.dpi.memWriteEnable := prevStageData.memWriteEnable
        io.dpi.memReadEnable := prevStageData.memReadEnable
    }.otherwise {
        io.dpi.memWriteEnable := false.B
        io.dpi.memReadEnable := false.B
    }
    io.dpi.ma_nextStage_valid := io.nextStage.valid

    io.working := state =/= s_idle
}
