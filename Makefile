BUILD_DIR = ./build

test:
	mill -i __.test.testOnly vitisrtlkernel.VitisRTLKernelTest

verilog:
	mkdir -p $(BUILD_DIR)
	mill -i chiselVitisTemplate.runMain --mainClass vitisrtlkernel.VitisRTLKernelVerilog -td $(BUILD_DIR)

help:
	mill -i __.runMain --mainClass vitisrtlkernel.VitisRTLKernelVerilog --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help compile bsp reformat checkformat clean