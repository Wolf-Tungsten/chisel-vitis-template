CHISEL_BUILD_DIR = ./build/chisel

############################## Chisel Flow #############################
test:
	mill -i __.test.testOnly vitisrtlkernel.VitisRTLKernelTest

verilog:
	mkdir -p $(CHISEL_BUILD_DIR)
	mill -i chiselVitisTemplate.runMain --mainClass vitisrtlkernel.VitisRTLKernelVerilog -td $(CHISEL_BUILD_DIR)

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
	-rm -rf $(CHISEL_BUILD_DIR)

.PHONY: test verilog help compile bsp reformat checkformat clean

############################## XCLBIN Flow #############################

XCLBIN_BUILD_DIR = ./build/xclbin

XCLBIN_TEMP_DIR = $(XCLBIN_BUILD_DIR)/tmp
XCLBIN_LOG_DIR = $(XCLBIN_BUILD_DIR)/log 
XCLBIN_REPORT_DIR = $(XCLBIN_BUILD_DIR)/report

VPP = v++
KERNEL_XO = $(XO).xo
LINK_CFG = $(XO).cfg


xclbin: $(KERNEL_XO) $(LINK_CFG)
	$(VPP) -t hw \
	--temp_dir $(XCLBIN_TEMP_DIR) --save_temps --log_dir $(XCLBIN_LOG_DIR) --report_dir $(XCLBIN_REPORT_DIR) \
	--link $(KERNEL_XO) \
	--config $(LINK_CFG) -o $(XO).xclbin

xclbin_profile: $(KERNEL_XO) $(LINK_CFG)
	$(VPP) -t hw \
	--temp_dir $(XCLBIN_TEMP_DIR) --save_temps --log_dir $(XCLBIN_LOG_DIR) --report_dir $(XCLBIN_REPORT_DIR) \
	--link $(KERNEL_XO) --profile.data:all:all:all --profile.stall:all\
	--config $(LINK_CFG) -o $(XO).profile.xclbin

clean_vpp :
	-rm -rf $(XCLBIN_TEMP_DIR)
	-rm -rf $(XCLBIN_LOG_DIR)
	-rm -rf $(XCLBIN_REPORT_DIR)
	-rm -rf ./.ipcaches

.PHONY: xclbin clean_vpp xclbin_profile

############################## Host Flow #############################

HOST_BUILD_DIR = ./build/host

HOST_SRC = ./host/*.cpp
HOST_INCLUDE = ./host/include

HOST_EXECUTEABLE = $(HOST_BUILD_DIR)/host_executeable

CXX := g++
CXXFLAGS += -g -std=c++17 -Wall
LDFLAGS += -I$(HOST_INCLUDE) -I$(XILINX_XRT)/include -L$(XILINX_XRT)/lib -lxrt_coreutil -pthread

host: $(HOST_SRC)
	mkdir -p $(HOST_BUILD_DIR)
	$(CXX) $(CXXFLAGS) $(LDFLAGS) $(HOST_SRC) -o $(HOST_EXECUTEABLE)

run_host: host $(HOST_EXECUTEABLE)
	$(HOST_EXECUTEABLE) $(XCLBIN)

profile_host: host $(HOST_EXECUTEABLE)
	$(HOST_EXECUTEABLE) $(XCLBIN)

DEV_XVC_PUB := /dev/xvc_pub.u0
hw_debug:
	xvc_pcie -d $(DEV_XVC_PUB)

.PHONY: host run_host hw_debug profile_host