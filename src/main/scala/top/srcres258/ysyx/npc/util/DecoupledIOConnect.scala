package top.srcres258.ysyx.npc.util

import chisel3._
import chisel3.util._

object DecoupledIOConnect {
    sealed abstract class Type(val name: String)
    case object Single extends Type("single")
    case object Multi extends Type("multi")
    case class Pipeline(val initData: Data) extends Type("pipeline")
    case object OOO extends Type("ooo")

    def apply[T <: Data](
        left: DecoupledIO[T],
        right: DecoupledIO[T],
        connectType: Type = Single
    ): Unit = connectType match {
        case Single => right.bits := left.bits
        case Multi => right <> left
        case p: Pipeline => {
            val reg = RegEnable(left.bits, p.initData, left.fire)
            right.valid := left.valid
            right.bits := reg
            left.ready := right.ready
        }
        case OOO => right <> Queue(left, 16)
    }
}
