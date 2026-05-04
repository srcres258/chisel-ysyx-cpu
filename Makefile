# Thin Makefile wrapper for Chisel build via Mill
# Allows npc/Makefile to invoke via: $(MAKE) -C vsrc-chisel mill-run

mill-run:
	./mill -i ChiselYSYXCpu.run

clean:
	rm -rf generated

.DEFAULT_GOAL := mill-run
.PHONY: mill-run clean
