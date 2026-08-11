# Thin Makefile wrapper for Chisel build via Mill
# Allows npc/Makefile to invoke via: $(MAKE) -C vsrc-chisel mill-run

MILL ?= mill -i ChiselYSYXCpu.run
MILL_ARGS ?=

mill-run:
	$(MILL) $(MILL_ARGS)

mill-run-standalone:
	$(MILL) standalone $(MILL_ARGS)

clean:
	rm -rf generated

.DEFAULT_GOAL := mill-run
.PHONY: mill-run mill-run-standalone clean
