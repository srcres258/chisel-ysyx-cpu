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
        val regWriteEnable = Output(Bool())
        val csrRegWriteEnable = Output(Bool())
        val immSel = Output(UInt(ControlUnit.IMM_SEL_LEN.W))
        val executePortASel = Output(Bool())
        val executePortBSel = Output(Bool())
        val aluOpSel = Output(UInt(ArithmeticLogicUnit.ALU_SEL_LEN.W))
        val compOpSel = Output(UInt(ComparatorUnit.COMP_OP_SEL_LEN.W))
        val lsType = Output(UInt(LoadAndStoreUnit.LS_TYPE_LEN.W))
        val dataMemWriteEnable = Output(Bool())
        val dataMemReadEnable = Output(Bool())
        val regWriteDataSel = Output(UInt(ControlUnit.RD_MUX_SEL_LEN.W))
        val csrRegWriteDataSel = Output(UInt(ControlUnit.CSR_RD_MUX_SEL_LEN.W))
        val jumpEnable = Output(Bool())
        val jumpType = Output(UInt(ControlUnit.JUMP_TYPE_LEN.W))
        val branchEnable = Output(Bool())
        val epcRecoverEnable = Output(Bool())
        val ecallEnable = Output(Bool())
        val opCode = Input(UInt(7.W))
        val funct3 = Input(UInt(3.W))
        val funct7 = Input(UInt(7.W))
        val rd = Input(UInt(5.W))
        val rs1 = Input(UInt(5.W))
        val rs2 = Input(UInt(5.W))
        
        val inst_jal = Output(Bool())
        val inst_jalr = Output(Bool())
    })

    {
        // default values
        io.regWriteEnable := false.B
        io.csrRegWriteEnable := false.B
        io.immSel := ControlUnit.IMM_UNKNOWN_TYPE.U
        io.executePortASel := false.B
        io.executePortBSel := false.B
        io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
        io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
        io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
        io.dataMemWriteEnable := false.B
        io.dataMemReadEnable := false.B
        io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_UNKNOWN.U
        io.jumpEnable := false.B
        io.jumpType := ControlUnit.JUMP_TYPE_JAL.U
        io.branchEnable := false.B
        io.epcRecoverEnable := false.B
        io.ecallEnable := false.B
        io.inst_jal := false.B
        io.inst_jalr := false.B

        /* ----- RV32I 指令 ----- */
        when(io.opCode === ControlUnit.OP_R_TYPE.U(7.W)) {
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_UNKNOWN_TYPE.U
            io.executePortASel := true.B

            {
                // default values
                io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                io.executePortBSel := true.B
                io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U

                val v = Cat(io.funct7(5), io.funct3)
                when(v === 0b0000.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b1000.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SUB.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b0001.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SLL.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b0010.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLT.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }.elsewhen(v === 0b0011.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLTU.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }.elsewhen(v === 0b0100.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_XOR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b0101.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SRL.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b1101.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SRA.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b0110.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_OR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(v === 0b0111.U(4.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_AND.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }
            }

            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
        }.elsewhen(io.opCode === ControlUnit.OP_B_TYPE.U(7.W)) {
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
                }.elsewhen(io.funct3 === 0b001.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BNE.U
                }.elsewhen(io.funct3 === 0b100.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BLT.U
                }.elsewhen(io.funct3 === 0b101.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BGE.U
                }.elsewhen(io.funct3 === 0b110.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BLTU.U
                }.elsewhen(io.funct3 === 0b111.U(3.W)) {
                    io.compOpSel := ComparatorUnit.OP_BGEU.U
                }
            }

            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
            io.branchEnable := true.B
        }.elsewhen(io.opCode === ControlUnit.OP_S_TYPE.U(7.W)) {
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
                }.elsewhen(io.funct3 === 0b001.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_S_H.U
                }.elsewhen(io.funct3 === 0b010.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_S_W.U
                }
            }

            io.dataMemWriteEnable := true.B
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        }.elsewhen(io.opCode === ControlUnit.OP_I_JALR_TYPE.U(7.W)) {
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_PC_N.U
            io.inst_jalr := true.B
            io.jumpEnable := true.B
            io.jumpType := ControlUnit.JUMP_TYPE_JALR.U
        }.elsewhen(io.opCode === ControlUnit.OP_I_LOAD_TYPE.U(7.W)) {
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
                }.elsewhen(io.funct3 === 0b001.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_H.U
                }.elsewhen(io.funct3 === 0b010.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_W.U
                }.elsewhen(io.funct3 === 0b100.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_BU.U
                }.elsewhen(io.funct3 === 0b101.U(3.W)) {
                    io.lsType := LoadAndStoreUnit.LS_L_HU.U
                }
            }

            io.dataMemReadEnable := true.B
            io.regWriteDataSel := ControlUnit.RD_MUX_DMEM.U
        }.elsewhen(io.opCode === ControlUnit.OP_I_ALU_TYPE.U(7.W)) {
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
                }.elsewhen(io.funct3 === 0b010.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLT.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }.elsewhen(io.funct3 === 0b011.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
                    io.compOpSel := ComparatorUnit.OP_BLTU.U
                    io.executePortBSel := true.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_BCU.U
                }.elsewhen(io.funct3 === 0b100.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_XOR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(io.funct3 === 0b110.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_OR.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(io.funct3 === 0b111.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_AND.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(io.funct3 === 0b001.U(3.W)) {
                    io.aluOpSel := ArithmeticLogicUnit.OP_SLL.U
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }.elsewhen(io.funct3 === 0b101.U(3.W)) {
                    io.aluOpSel := Mux(io.funct7(5).asBool, ArithmeticLogicUnit.OP_SRA.U, ArithmeticLogicUnit.OP_SRL.U)
                    io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
                    io.executePortBSel := false.B
                    io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
                }

                io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            }
        }.elsewhen(io.opCode === ControlUnit.OP_I_FENCE_TYPE.U(7.W)) {
            io.regWriteEnable := false.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U
        }.elsewhen(
            io.opCode === ControlUnit.OP_I_ENVIRONMENT_TYPE.U(7.W) &&
            io.rd === 0b00000.U(5.W) &&
            io.funct3 === 0b000.U(3.W) &&
            io.rs1 === 0b00000.U(5.W) &&
            io.funct7 === 0b0000000.U(7.W)
        ) {
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := true.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_UNKNOWN.U

            when(io.rs2 === 0b00000.U(5.W)) {
                // 指令: ecall
                io.ecallEnable := true.B
            }.elsewhen(io.rs2 === 0b00001.U(5.W)) {
                // 指令: ebreak
                // TODO 实现 ebreak 指令
            }
        }.elsewhen(io.opCode === ControlUnit.OP_U_LUI_TYPE.U(7.W)) {
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_U_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_IMM.U
        }.elsewhen(io.opCode === ControlUnit.OP_U_AUIPC_TYPE.U(7.W)) {
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_U_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_ALU.U
        }.elsewhen(io.opCode === ControlUnit.OP_J_TYPE.U(7.W)) {
            io.regWriteEnable := true.B
            io.immSel := ControlUnit.IMM_J_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_ADD.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_PC_N.U
            io.inst_jal := true.B
            io.jumpEnable := true.B
        }

        /* ----- Zicsr 指令 ----- */
        when(io.opCode === ControlUnit.OP_I_CSR_TYPE.U(7.W)) {
            io.regWriteEnable := true.B
            io.csrRegWriteEnable := true.B
            io.immSel := ControlUnit.IMM_I_TYPE.U
            io.executePortASel := false.B
            io.executePortBSel := false.B
            io.aluOpSel := ArithmeticLogicUnit.OP_UNKNOWN.U
            io.compOpSel := ComparatorUnit.OP_UNKNOWN.U
            io.lsType := LoadAndStoreUnit.LS_UNKNOWN.U
            io.regWriteDataSel := ControlUnit.RD_MUX_CSR_DATA.U

            when(io.funct3 === 0b001.U(3.W)) {
                // 指令: csrrw
                io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_W.U
            }.elsewhen(io.funct3 === 0b010.U(3.W)) {
                // 指令: csrrs
                io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_S.U
            }.elsewhen(io.funct3 === 0b011.U(3.W)) {
                // 指令: csrrc
                io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_C.U
            }.elsewhen(io.funct3 === 0b101.U(3.W)) {
                // 指令: csrrwi
                io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_W_IMM.U
            }.elsewhen(io.funct3 === 0b110.U(3.W)) {
                // 指令: csrrsi
                io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_S_IMM.U
            }.elsewhen(io.funct3 === 0b111.U(3.W)) {
                // 指令: csrrci
                io.csrRegWriteDataSel := ControlUnit.CSR_RD_MUX_C_IMM.U
            }
        }

        /* ----- 特权架构 指令 ----- */
        when(
            io.opCode === ControlUnit.OP_R_PRIVILEGED_TYPE.U(7.W) &&
            io.rd === 0b00000.U(5.W) &&
            io.funct3 === 0b000.U(3.W) &&
            io.rs1 === 0b00000.U(5.W) &&
            io.rs2 === 0b00010.U(5.W) &&
            io.funct7 === 0b0011000.U(7.W)
        ) {
            // 指令: mret
            io.epcRecoverEnable := true.B
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
    val RD_MUX_CSR_DATA: Int = 5
    val RD_MUX_UNKNOWN: Int = 1 << RD_MUX_SEL_LEN - 1

    val CSR_RD_MUX_SEL_LEN: Int = 3
    val CSR_RD_MUX_C: Int = 0
    val CSR_RD_MUX_S: Int = 1
    val CSR_RD_MUX_W: Int = 2
    val CSR_RD_MUX_C_IMM: Int = 3
    val CSR_RD_MUX_S_IMM: Int = 4
    val CSR_RD_MUX_W_IMM: Int = 5
    val CSR_RD_MUX_UNKNOWN: Int = 1 << CSR_RD_MUX_SEL_LEN - 1

    val JUMP_TYPE_LEN: Int = 1

    val JUMP_TYPE_JAL: Int = 0
    val JUMP_TYPE_JALR: Int = 1

    val MCAUSE_ECALL_FROM_M_MODE: Int = 11

    // ----- RV32I opcodes -----
    private val OP_R_TYPE               = 0b0110011
    private val OP_B_TYPE               = 0b1100011
    private val OP_S_TYPE               = 0b0100011
    private val OP_I_JALR_TYPE          = 0b1100111
    private val OP_I_LOAD_TYPE          = 0b0000011
    private val OP_I_ALU_TYPE           = 0b0010011
    private val OP_I_FENCE_TYPE         = 0b0001111
    private val OP_I_ENVIRONMENT_TYPE   = 0b1110011
    private val OP_U_LUI_TYPE           = 0b0110111
    private val OP_U_AUIPC_TYPE         = 0b0010111
    private val OP_J_TYPE               = 0b1101111

    // ----- Zicsr opcodes -----
    private val OP_I_CSR_TYPE = 0b1110011

    // ----- 特权架构 opcodes -----
    private val OP_R_PRIVILEGED_TYPE = 0b1110011
}
