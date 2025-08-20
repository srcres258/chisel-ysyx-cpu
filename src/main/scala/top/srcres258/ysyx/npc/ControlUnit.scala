package top.srcres258.ysyx.npc

import chisel3._
import chisel3.util._

/**
  * 控制单元 CU
  */
class ControlUnit(
    /**
      * xLen: 操作数位数，在 RV32I 指令集中为 32
      */
    val xLen: Int = 32
) extends Module {
    val io = IO(new Bundle {
        val pcMuxSel = Output(Bool())
        val regWriteEnable = Output(Bool())
        val immSel = Output(UInt(ControlUnit.IMM_SEL_LEN.W))
        val executePortASel = Output(Bool())
        val executePortBSel = Output(Bool())
        val aluOpSel = Output(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
        val compOpSel = Output(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
        val lsType = Output(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
        val dataMemWriteEnable = Output(Bool())
        val regWriteDataSel = Output(UInt(ControlUnit.RD_MUX_SEL_LEN.W))
        val opCode = Input(UInt(7.W))
        val funct3 = Input(UInt(3.W))
        val funct7Bit5 = Input(Bool())
        val branchEnable = Input(Bool())
    })

    {
        // default values
        io.pcMuxSel := false.B
        io.regWriteEnable := false.B
        io.immSel := ControlUnit.IMM_UNKNOWN_TYPE.U
        io.executePortASel := false.B
        io.executePortBSel := false.B
        io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
        io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
        io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
        io.dataMemWriteEnable := false.B
        io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U

        when(io.opCode === ControlUnit.OP_R_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_UNKNOWN_TYPE.U
            io.executePortASel := true.B

            {
                // default values
                io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                io.executePortBSel := true.B
                io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U

                val v = Cat(io.funct7Bit5.asUInt, io.funct3)
                when(v === 0b0000.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b1000.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SUB.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b0001.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SLL.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b0010.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLT.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }
                when(v === 0b0011.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLTU.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }
                when(v === 0b0100.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_XOR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b0101.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SRL.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b1101.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SRA.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b0110.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_OR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(v === 0b0111.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_AND.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
            }

            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
        }
        when(io.opCode === ControlUnit.OP_B_TYPE.U(7.W)) {
            io.pcMuxSel := io.branchEnable
            io.regWriteEnable := false.B
            io.immSel := ControlUnit.IMM_B_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U

            {
                // default values
                io.compOpSel := ComparatorUnit.OP_UNKNOWN.U

                when(io.funct3 === 0b000.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BEQ.U
                }
                when(io.funct3 === 0b001.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BNE.U
                }
                when(io.funct3 === 0b100.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BLT.U
                }
                when(io.funct3 === 0b101.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BGE.U
                }
                when(io.funct3 === 0b110.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BLTU.U
                }
                when(io.funct3 === 0b111.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BGEU.U
                }
            }

            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        }
        when(io.opCode === ControlUnit.OP_S_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := false.B
            io.immSel := ControlUnit.IMM_S_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U

            {
                // default values
                io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U

                when(io.funct3 === 0b000.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_S_B.U
                }
                when(io.funct3 === 0b001.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_S_H.U
                }
                when(io.funct3 === 0b010.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_S_W.U
                }
            }

            io.dataMemWriteEnable := true.B
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        }
        when(io.opCode === ControlUnit.OP_I_JALR_TYPE.U(7.W)) {
            io.pcMuxSel := true.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_PC_N.U
        }
        when(io.opCode === ControlUnit.OP_I_LOAD_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U

            {
                // default values
                io.lsType := LoadAndStoreUnit.LS_L_W.U

                when(io.funct3 === 0b000.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_B.U
                }
                when(io.funct3 === 0b001.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_H.U
                }
                when(io.funct3 === 0b010.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_W.U
                }
                when(io.funct3 === 0b100.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_BU.U
                }
                when(io.funct3 === 0b101.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_HU.U
                }
            }

            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_DMEM.U
        }
        when(io.opCode === ControlUnit.OP_I_ALU_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B

            {
                // default values
                io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                io.executePortBSel := false.B
                io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U

                when(io.funct3 === 0b000.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(io.funct3 === 0b010.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLT.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }
                when(io.funct3 === 0b011.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLTU.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }
                when(io.funct3 === 0b100.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_XOR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(io.funct3 === 0b110.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_OR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(io.funct3 === 0b111.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_AND.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(io.funct3 === 0b001.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SLL.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
                when(io.funct3 === 0b101.U(3.W)) {
                    io.aluOpSel := Mux(io.funct7Bit5, ArithmeticLogicUnit.OP_SRA.U, ArithmeticLogicUnit.OP_SRL.U)
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }

                io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
                io.dataMemWriteEnable := false.B
            }
        }
        when(io.opCode === ControlUnit.OP_I_FENCE_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := false.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        }
        when(io.opCode === ControlUnit.OP_I_ECALL_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := false.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        }
        when(io.opCode === ControlUnit.OP_U_LUI_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_U_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_IMM.U
        }
        when(io.opCode === ControlUnit.OP_U_AUIPC_TYPE.U(7.W)) {
            io.pcMuxSel := false.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_U_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
        }
        when(io.opCode === ControlUnit.OP_J_TYPE.U(7.W)) {
            io.pcMuxSel := true.B
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_J_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.dataMemWriteEnable := false.B
            io.regWriteDataSel := ControlUnit.RD_MUX_PC_N.U
        }
    }
}

object ControlUnit {
    val IMM_SEL_LEN: Int = 3

    val IMM_B_TYPE: Int = 0
    val IMM_S_TYPE: Int = 1
    val IMM_U_TYPE: Int = 2
    val IMM_I_TYPE: Int = 3
    val IMM_J_TYPE: Int = 4
    val IMM_UNKNOWN_TYPE: Int = 1 << IMM_SEL_LEN - 1

    val RD_MUX_SEL_LEN: Int = 3

    val RD_MUX_DMEM: Int = 0
    val RD_MUX_ALU: Int = 1
    val RD_MUX_BCU: Int = 2
    val RD_MUX_IMM: Int = 3
    val RD_MUX_PC_N: Int = 4
    val RD_MUX_UNKNOWN: Int = 5

    private val OP_R_TYPE       = 0b0110011
    private val OP_B_TYPE       = 0b1100011
    private val OP_S_TYPE       = 0b0100011
    private val OP_I_JALR_TYPE  = 0b1100111
    private val OP_I_LOAD_TYPE  = 0b0000011
    private val OP_I_ALU_TYPE   = 0b0010011
    private val OP_I_FENCE_TYPE = 0b0001111
    private val OP_I_ECALL_TYPE = 0b1110011
    private val OP_U_LUI_TYPE   = 0b0110111
    private val OP_U_AUIPC_TYPE = 0b0010111
    private val OP_J_TYPE       = 0b1101111
}
