package top.srcres258.ysyx.npc.dpi

import chisel3._
import chisel3.reflect.DataMirror

object DPIInline {
    private case class PortDef(name: String, direction: String, width: Option[Int], public: Boolean)

    private def sanitizePath(path: String): String = {
        path.stripPrefix(".")
            .replace('.', '_')
            .replace('(', '_')
            .replace(')', ' ')
            .trim
            .replace(' ', '_')
    }

    private def portName(path: String): String = sanitizePath(path)

    private def portDefs(data: Data): Seq[PortDef] = {
        def recurse(node: Data, path: String): Seq[PortDef] = node match {
            case record: Record =>
                record.elements.toSeq.reverse.flatMap { case (fieldName, fieldData) =>
                    val nextPath = if (path.isEmpty) fieldName else s"${path}_$fieldName"
                    recurse(fieldData, nextPath)
                }
            case vec: Vec[_] =>
                vec.getElements.zipWithIndex.flatMap { case (fieldData, index) =>
                    val nextPath = if (path.isEmpty) index.toString else s"${path}_$index"
                    recurse(fieldData, nextPath)
                }
            case leaf =>
                val name = portName(path)
                val width = leaf.widthOption.filter(_ > 1)
                val direction = DataMirror.directionOf(leaf) match {
                    case ActualDirection.Input  => "input"
                    case ActualDirection.Output => "output"
                    case other =>
                        throw new IllegalArgumentException(s"Unsupported DPI port direction: $other for $path")
                }
                Seq(PortDef(name, direction, width, direction == "input" && name != "clock" && name != "reset"))
        }

        recurse(data, "")
    }

    private def renderPort(port: PortDef): String = {
        val widthStr = port.width.map(width => s"[${width - 1}:0] ").getOrElse("")
        val publicStr = if (port.public) " /*verilator public*/" else ""
        s"${port.direction} ${widthStr}${port.name}$publicStr"
    }

    private def renderModule(moduleName: String, io: Data, bodyLines: Seq[String]): String = {
        val ports = portDefs(io).map(renderPort).mkString(",\n    ")
        val body = if (bodyLines.isEmpty) "" else bodyLines.map(line => s"    $line").mkString("\n\n")
        Seq(
            s"module $moduleName (",
            s"    $ports",
            s");",
            "",
            body,
            "endmodule"
        ).filter(_.nonEmpty).mkString("\n")
    }

    def generalDPIEnabled(moduleName: String, io: GeneralDPIBundle): String = {
        val port = portName _
        renderModule(
            moduleName,
            io,
            Seq(
                "import \"DPI-C\" function void       dpi_halt(input logic halt);",
                "import \"DPI-C\" function void       dpi_onRetireTrace(input logic trig);",
                "import \"DPI-C\" function void       dpi_onEcallEnable(input logic [31:0] pc, input logic ecallEnable);",
                "import \"DPI-C\" function void       dpi_onEpcRecoverEnable(input logic [31:0] pc, input logic epcRecoverEnable);",
                "import \"DPI-C\" function void       dpi_onMemAccess(",
                "    input logic [31:0] memPc,",
                "    input logic memWriteEnable,",
                "    input logic memReadEnable,",
                "    input logic [31:0] memAddr,",
                "    input logic [31:0] memData,",
                "    input logic [3:0] memStrobe,",
                "    input logic [1:0] memResp,",
                "    input logic [3:0] memLsType",
                ");",
                "import \"DPI-C\" function void       dpi_onPosEdge_ifuInputValid(input logic ifuInputValid);",
                "import \"DPI-C\" function void       dpi_onPosEdge_if_nextStage_valid(input logic if_nextStage_valid);",
                "import \"DPI-C\" function void       dpi_onPosEdge_id_nextStage_valid(input logic id_nextStage_valid);",
                "import \"DPI-C\" function void       dpi_onPosEdge_ex_nextStage_valid(input logic ex_nextStage_valid);",
                "import \"DPI-C\" function void       dpi_onPosEdge_mem_nextStage_valid(input logic mem_nextStage_valid);",
                "import \"DPI-C\" function void       dpi_onPosEdge_wb_nextStage_valid(input logic wb_nextStage_valid);",
                "import \"DPI-C\" function bit [31:0] dpi_clint_onReadEnable(input logic [31:0] pc, input logic clint_read_readEnable);",
                "import \"DPI-C\" function void       dpi_clint_onWriteEnable(input logic [31:0] pc, input logic clint_write_writeEnable);",
                "",
                s"logic [31:0] clint_readData_reg;",
                s"initial clint_readData_reg = 32'h0;",
                s"assign ${port("clint.read.readData")} = clint_readData_reg;",
                "",
                s"always_ff @( posedge ${port("core.halt")} ) begin : call_dpi_halt",
                s"    dpi_halt(${port("core.halt")});",
                "end",
                s"always_ff @( posedge ${port("exu.ecallEnable")} ) begin : call_dpi_onEcallEnable",
                s"    dpi_onEcallEnable(${port("exu.exPc")}, ${port("exu.ecallEnable")});",
                "end",
                s"always_ff @( posedge ${port("exu.epcRecoverEnable")} ) begin : call_dpi_onEpcRecoverEnable",
                s"    dpi_onEpcRecoverEnable(${port("exu.exPc")}, ${port("exu.epcRecoverEnable")});",
                "end",
                s"always_ff @( posedge ${port("core.ifuInputValid")} ) begin : call_dpi_onPosEdge_ifuInputValid",
                s"    dpi_onPosEdge_ifuInputValid(${port("core.ifuInputValid")});",
                "end",
                s"always_ff @( posedge ${port("ifu.if_nextStage_valid")} ) begin : call_dpi_onPosEdge_if_nextStage_valid",
                s"    dpi_onPosEdge_if_nextStage_valid(${port("ifu.if_nextStage_valid")});",
                "end",
                s"always_ff @( posedge ${port("idu.id_nextStage_valid")} ) begin : call_dpi_onPosEdge_id_nextStage_valid",
                s"    dpi_onPosEdge_id_nextStage_valid(${port("idu.id_nextStage_valid")});",
                "end",
                s"always_ff @( posedge ${port("exu.ex_nextStage_valid")} ) begin : call_dpi_onPosEdge_ex_nextStage_valid",
                s"    dpi_onPosEdge_ex_nextStage_valid(${port("exu.ex_nextStage_valid")});",
                "end",
                s"always_ff @( posedge ${port("memu.mem_nextStage_valid")} ) begin : call_dpi_onPosEdge_mem_nextStage_valid",
                s"    dpi_onPosEdge_mem_nextStage_valid(${port("memu.mem_nextStage_valid")});",
                s"    dpi_onMemAccess(",
                s"        ${port("memu.memPc")},",
                s"        ${port("memu.memWriteEnable")},",
                s"        ${port("memu.memReadEnable")},",
                s"        ${port("memu.memAddr")},",
                s"        ${port("memu.memData")},",
                s"        ${port("memu.memStrobe")},",
                s"        ${port("memu.memResp")},",
                s"        ${port("memu.memLsType")}",
                "    );",
                "end",
                s"always_ff @( posedge ${port("wbu.wb_nextStage_valid")} ) begin : call_dpi_onPosEdge_wb_nextStage_valid",
                s"    dpi_onPosEdge_wb_nextStage_valid(${port("wbu.wb_nextStage_valid")});",
                s"    dpi_onRetireTrace(${port("wbu.wb_nextStage_valid")});",
                "end",
                s"always_ff @( posedge ${port("clint.read.readEnable")} ) begin : call_dpi_clint_onReadEnable",
                s"    clint_readData_reg <= dpi_clint_onReadEnable(${port("memu.memPc")}, ${port("clint.read.readEnable")});",
                "end",
                s"always_ff @( posedge ${port("clint.write.writeEnable")} ) begin : call_dpi_clint_onWriteEnable",
                s"    dpi_clint_onWriteEnable(${port("memu.memPc")}, ${port("clint.write.writeEnable")});",
                "end"
            )
        )
    }

    def generalDPISignalWrapper(moduleName: String, io: GeneralDPIBundle): String = {
        val port = portName _
        renderModule(
            moduleName,
            io,
            Seq(
                s"assign ${port("clint.read.readData")} = 32'h0;"
            )
        )
    }

    def standaloneMemEnabled(moduleName: String, io: Bundle): String = {
        val port = portName _
        renderModule(
            moduleName,
            io,
            Seq(
                "import \"DPI-C\" function int dpi_pmem_read(input int addr);",
                "import \"DPI-C\" function void dpi_pmem_write(input int addr, input int data, input byte strb);",
                "import \"DPI-C\" function void dpi_set_pmem_word(input int word_addr, input int data);",
                "",
                s"assign ${port("axi.ar.ready")} = 1'b1;",
                s"assign ${port("axi.aw.ready")} = 1'b1;",
                s"assign ${port("axi.w.ready")} = 1'b1;",
                "",
                s"logic [31:0] rdata_reg;",
                s"logic        rvalid_reg;",
                s"assign ${port("axi.r.bits.data")} = rdata_reg;",
                s"assign ${port("axi.r.bits.resp")} = 2'b00;",
                s"assign ${port("axi.r.bits.last")} = 1'b1;",
                s"assign ${port("axi.r.bits.id")} = 4'b0;",
                s"assign ${port("axi.r.valid")} = rvalid_reg;",
                "",
                s"always @(posedge ${port("clock")}) begin",
                s"    if (${port("reset")}) begin",
                s"        rvalid_reg <= 1'b0;",
                s"        rdata_reg <= 32'b0;",
                "    end else begin",
                s"        if (${port("axi.ar.valid")} && ${port("axi.ar.ready")}) begin",
                s"            rdata_reg <= dpi_pmem_read(${port("axi.ar.bits.addr")});",
                s"            rvalid_reg <= 1'b1;",
                "        end",
                s"        if (${port("axi.r.valid")} && ${port("axi.r.ready")}) begin",
                s"            rvalid_reg <= 1'b0;",
                "        end",
                "    end",
                "end",
                "",
                s"logic        aw_captured;",
                s"logic [31:0] aw_addr_reg;",
                s"logic        w_captured;",
                s"logic [31:0] w_data_reg;",
                s"logic [3:0]  w_strb_reg;",
                s"logic        bvalid_reg;",
                "",
                s"assign ${port("axi.b.bits.resp")} = 2'b00;",
                s"assign ${port("axi.b.bits.id")} = 4'b0;",
                s"assign ${port("axi.b.valid")} = bvalid_reg;",
                "",
                s"always @(posedge ${port("clock")}) begin",
                s"    if (${port("reset")}) begin",
                s"        aw_captured <= 1'b0;",
                s"        w_captured  <= 1'b0;",
                s"        bvalid_reg  <= 1'b0;",
                "    end else begin",
                s"        if (${port("axi.aw.valid")} && ${port("axi.aw.ready")}) begin",
                s"            aw_addr_reg <= ${port("axi.aw.bits.addr")};",
                s"            aw_captured <= 1'b1;",
                "        end",
                s"        if (${port("axi.w.valid")} && ${port("axi.w.ready")}) begin",
                s"            w_data_reg <= ${port("axi.w.bits.data")};",
                s"            w_strb_reg <= ${port("axi.w.bits.strb")};",
                s"            w_captured <= 1'b1;",
                "        end",
                s"        if (aw_captured && w_captured && !bvalid_reg) begin",
                s"            dpi_pmem_write(aw_addr_reg, w_data_reg, w_strb_reg);",
                s"            bvalid_reg  <= 1'b1;",
                s"            aw_captured <= 1'b0;",
                s"            w_captured  <= 1'b0;",
                "        end",
                s"        if (${port("axi.aw.valid")} && ${port("axi.aw.ready")} && ${port("axi.w.valid")} && ${port("axi.w.ready")}) begin",
                s"            dpi_pmem_write(${port("axi.aw.bits.addr")}, ${port("axi.w.bits.data")}, ${port("axi.w.bits.strb")});",
                s"            bvalid_reg  <= 1'b1;",
                s"            aw_captured <= 1'b0;",
                s"            w_captured  <= 1'b0;",
                "        end",
                s"        if (${port("axi.b.valid")} && ${port("axi.b.ready")}) begin",
                s"            bvalid_reg <= 1'b0;",
                "        end",
                "    end",
                "end"
            )
        )
    }
}
