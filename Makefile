# Thin Makefile wrapper for Chisel build via Mill
# Allows npc/Makefile to invoke via: $(MAKE) -C vsrc-chisel mill-run

MILL := ./mill -i ChiselYSYXCpu.run

mill-run:
	$(MILL)

mill-run-standalone:
	$(MILL) standalone

clean:
	rm -rf generated

.DEFAULT_GOAL := mill-run
.PHONY: mill-run mill-run-standalone clean
