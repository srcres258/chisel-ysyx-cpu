package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

/**
  * 处理器执行阶段控制器，控制处理器的下一次执行阶段
  */
class StageController extends Module {
    val io = IO(new Bundle {
        val stage = Output(UInt(StageController.STAGE_LEN.W))
    })

    val stage = RegInit(StageController.STAGE_IF.U(StageController.STAGE_LEN.W))

    when(reset.asBool || stage === StageController.STAGE_UPC.U) {
        stage := StageController.STAGE_IF.U
    }.otherwise {
        stage := stage + 1.U
    }

    io.stage := stage
}

object StageController {
    /* 
    处理器执行的 6 个阶段: (每个阶段各耗时一个时钟周期)

    阶段1: IF  - Instruction Fetch      取指
    阶段2: ID  - Instruction Decode     译码
    阶段3: EX  - Execute                执行
    阶段4: MA  - Memory Access          访存
    阶段5: WB  - Write Back             写回
    阶段6: UPC - Update Program Counter 更新程序计数器

    处理器的执行过程:
    从阶段1按序执行到阶段6;
    阶段6过后再回到阶段1继续执行.
     */

    val STAGE_LEN: Int = 3

    val STAGE_IF : Int = 0
    val STAGE_ID : Int = 1
    val STAGE_EX : Int = 2
    val STAGE_MA : Int = 3
    val STAGE_WB : Int = 4
    val STAGE_UPC: Int = 5
}
