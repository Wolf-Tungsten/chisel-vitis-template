# Chisel Vitis Template

Build Vitis RTL Kernel with Chisel.


## Chisel：Kernel 实例化与 Verilog 生成

在 `src/main/scala/vitisrtlkernel/VitisRTLKernel.scala` 中：
* 修改 `VitisRTLKernelDataIF`，添加需要的寄存器参数和 Memory Port；
* 修改 `VitisRTLKernel`，实例化 kernel 模块。
⚠️ 不要修改该文件的其他部分

顶层 kernel 的 io port 必须为：
```scala
  val io = IO(new Bundle {
    val dataIF = (new VitisRTLKernelDataIF)
    val done   = Output(Bool())
  })
```

在 reset 后，dataIF 中的所有参数都已有效，可以直接开始执行 kernel 逻辑。

kernel 逻辑结束后，使能 done 输出信号，**在 done 信号使能后、下次 reset 有效前，不要操作 memory port**。

kernel 测试无误后，执行 `make verilog`，生成的 verilog 位于 `build/chisel/VitisRTLKernel.v`。

## Vitis IDE/Vivado：使用 RTL Kernel Wizard 打包 kernel 为 xo

创建一个临时的 Vitis 项目，选对 platform，启动 RTL Kernel Wizard 创建 RTL Kernel。

不要直接通过 Vivado 启动 RTL Kernel Wizard，因为 Vivado 会出现 platform 无法选择的 bug。

修改 RTL Kernel Wizard 生成的顶层文件，实例化 `VitisRTLKernel` 并连接 port。

建议在此处综合一下，确保没有综合问题。

Generate RTL Kernel，生成 xo 文件。

## v++：编译 XCLBIN

将 Vivado 生成的 xo 文件复制到 `xo_kernel` 目录下，建议命名为 `<XO_NAME>.xo`（例如 `chisel_vecadd.xo`)。

参考 `xo_kernel/chisel_vecadd.cfg` 编写 v++ link 配置，保存为 `<XO_NAME>.cfg`。

执行 `make xclbin XO=<XO_NAME>` (例子中为 `make xclbin XO=chisel_vecadd`)，开始 xclbin 打包。

xclbin 打包时间极长，注意进程不要被杀掉。

打包完成后，`xo_kernel` 目录下会产生：`<XO_NAME>.xclbin` 及一系列辅助文件，可以使用 Vitis Analyzer 分析。

## g++：编译 host 并执行

编写 host 并根据需要修改 Makefile 中 host 目标的编译方法，使用 `make host` 命令进行编译。

编译生成的可执行文件位于 `build/host/host_executable` 处。

如果host编写符合 Vitis 建议的方式——在第一个参数指定 xclbin 路径，则可直接使用 `make run XCLBIN=<XCLBIN_NAME>` 命令执行。

## Vivado：调试

执行 `make hw_debug` 命令启动 Xilinx Virtual Cable，之后可在 Vivado Hardware Manager 中连接。

如果连接了 USB JTAG，需要区分 XVC 设备和 JTAG 设备，只有通过 XVC 设备才能调试 kernel。

ILA probe 需要的 ltx 文件位于 `xo_kernel` 目录中。

使用 ILA 时，需要在 host program 中插入停顿，参考 `host/host.cpp` 中 `wait_for_enter` 函数实现。



