package top.srcres258.ysyx.npc.stage

import chisel3._
import chisel3.util._

import top.srcres258.ysyx.npc.util.Assertion

class StageUnitBundle(xLen: Int) extends Bundle {
    Assertion.assertProcessorXLen(xLen)
}
