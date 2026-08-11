# Thin Makefile wrapper for Chisel build via Mill
# Allows npc/Makefile to invoke via: $(MAKE) -C vsrc-chisel mill-run

MILL ?= mill -i ChiselYSYXCpu.run
MILL_TEST ?= mill -i ChiselYSYXCpu.test
MILL_ARGS ?=

mill-run:
	$(MILL) $(MILL_ARGS)

mill-run-standalone:
	$(MILL) standalone $(MILL_ARGS)

test:
	$(MILL_TEST)

clean:
	rm -rf generated

.DEFAULT_GOAL := mill-run
.PHONY: mill-run mill-run-standalone test clean
