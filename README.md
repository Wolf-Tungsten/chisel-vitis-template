# 在 Vitis 平台上运行 Chisel 设计

✅ 以下流程已在 Alveo U280 平台上成功运行

## Step1: Chisel Kernel 实例化与 Verilog 生成

在 `src/main/scala/vitisrtlkernel/VitisRTLKernel.scala` 中：
* 修改 `VitisRTLKernelDataIF`，添加需要的 Scalar 参数和 Memory Port；
* 修改 `VitisRTLKernel`，实例化 kernel 模块（参考例子中的 VecAdd）。

⚠️ 不要修改该文件的其他部分

顶层 kernel 的 io port 必须为：
```scala
  val io = IO(new Bundle {
    val dataIF = (new VitisRTLKernelDataIF)
    val done   = Output(Bool())
  })
```

* **reset 高电平有效**，在复位，dataIF 中的所有参数都已生效，可以直接读取参数、开始执行 kernel 逻辑。

* kernel 逻辑运行结束后，**使能 done 输出信号**，通知 vitis 执行完成，host 端 wait 函数返回。

⚠️ **在 done 信号使能后、下次 reset 有效前，不要操作 memory port**。

* test 目录下提供了简单的 AXISlaveSim 实现，可用于 VitisRTLKernel 测试，具体使用方法参考 VitisRTLKernelTest 的实现。

* 生成 Verilog：kernel 测试无误后，执行 `make verilog`，生成的文件位于 `build/chisel/VitisRTLKernel.v`。


## Step2: 使用 RTL Kernel Wizard 打包 kernel 为 xo 格式

* 启动 Vitis IDE（不是 Vitis HLS），创建一个临时的 Vitis Application 项目，**项目创建时需要正确选择 platform**，启动 RTL Kernel Wizard 创建 RTL Kernel。（不要直接通过 Vivado 启动 RTL Kernel Wizard，因为 Vivado 会出现 platform 无法选择的 bug。）

* 参考 【UG1393】Ch47. RTL Kernel Wizard 配置 RTL Kernel 所需的 Scalar 参数和 Memory Port，特别注意以下参数配置：

  * General Settings - Kernel Control Interface - ap ctrl hs

  * General Settings - Number of clocks - 1

  * General Settings - Has reset - 0


* RTL Kernel Wizard 会创建一个 Vivado 项目并启动 Vivado：

  * 添加 `build/chisel/VitisRTLKernel.v` 为设计文件；
  * 修改 kernel 顶层文件，实例化 chisel kernel，例如：

```verilog
///////////////////////////////////////////////////////////////////////////////
// Add kernel logic here.  Modify/remove example code as necessary.
///////////////////////////////////////////////////////////////////////////////
// 注释掉 Vivado 生成的例子
// Example RTL block.  Remove to insert custom logic.
//chisel_vecadd_example #(
//  .C_M00_AXI_ADDR_WIDTH ( C_M00_AXI_ADDR_WIDTH ),
//  .C_M00_AXI_DATA_WIDTH ( C_M00_AXI_DATA_WIDTH )
//)
//inst_example (
//  .ap_clk          ( ap_clk          ),
//  .ap_rst_n        ( 1'b1            ),
//  .m00_axi_awvalid ( m00_axi_awvalid ),
//  .m00_axi_awready ( m00_axi_awready ),
//  .m00_axi_awaddr  ( m00_axi_awaddr  ),
//  .m00_axi_awlen   ( m00_axi_awlen   ),
//  .m00_axi_wvalid  ( m00_axi_wvalid  ),
//  .m00_axi_wready  ( m00_axi_wready  ),
//  .m00_axi_wdata   ( m00_axi_wdata   ),
//  .m00_axi_wstrb   ( m00_axi_wstrb   ),
//  .m00_axi_wlast   ( m00_axi_wlast   ),
//  .m00_axi_bvalid  ( m00_axi_bvalid  ),
//  .m00_axi_bready  ( m00_axi_bready  ),
//  .m00_axi_arvalid ( m00_axi_arvalid ),
//  .m00_axi_arready ( m00_axi_arready ),
//  .m00_axi_araddr  ( m00_axi_araddr  ),
//  .m00_axi_arlen   ( m00_axi_arlen   ),
//  .m00_axi_rvalid  ( m00_axi_rvalid  ),
//  .m00_axi_rready  ( m00_axi_rready  ),
//  .m00_axi_rdata   ( m00_axi_rdata   ),
//  .m00_axi_rlast   ( m00_axi_rlast   ),
//  .ap_start        ( ap_start        ),
//  .ap_done         ( ap_done         ),
//  .ap_idle         ( ap_idle         ),
//  .ap_ready        ( ap_ready        ),
//  .readLength      ( readLength      ),
//  .readAddress     ( readAddress     ),
//  .writeAddress    ( writeAddress    )
//);

// 换上我们自己的
  VitisRTLKernel example(
    .ap_clk          ( ap_clk          ),
    .ap_start        ( ap_start        ),
    .ap_done         ( ap_done         ),
    .ap_idle         ( ap_idle         ),
    .ap_ready        ( ap_ready        ),
    .dataIF_readLength            ( readLength      ),
    .dataIF_readAddress           ( readAddress     ),
    .dataIF_writeAddress          ( writeAddress    ),
    .dataIF_m00Read_ar_ready      (m00_axi_arready),
    .dataIF_m00Read_ar_valid      (m00_axi_arvalid),
    .dataIF_m00Read_ar_bits_addr  (m00_axi_araddr),
    .dataIF_m00Read_ar_bits_len   (m00_axi_arlen),
    .dataIF_m00Read_r_ready       (m00_axi_rready),
    .dataIF_m00Read_r_valid       (m00_axi_rvalid),
    .dataIF_m00Read_r_bits_data   (m00_axi_rdata),
    .dataIF_m00Read_r_bits_last   (m00_axi_rlast),
    .dataIF_m00Write_aw_ready     (m00_axi_awready),
    .dataIF_m00Write_aw_valid     (m00_axi_awvalid),
    .dataIF_m00Write_aw_bits_addr (m00_axi_awaddr),
    .dataIF_m00Write_aw_bits_len  (m00_axi_awlen),
    .dataIF_m00Write_w_ready      (m00_axi_wready),
    .dataIF_m00Write_w_valid      (m00_axi_wvalid),
    .dataIF_m00Write_w_bits_data  (m00_axi_wdata),
    .dataIF_m00Write_w_bits_strb  (m00_axi_wstrb),
    .dataIF_m00Write_w_bits_last  (m00_axi_wlast),
    .dataIF_m00Write_b_ready      (m00_axi_bready),
    .dataIF_m00Write_b_valid      (m00_axi_bvalid)
  );
    
```

* 建议在此处运行一次综合，保证综合无误再继续向前。

* 在左侧导航栏中点击 Generate RTL Kernel，生成 xo 文件。

* 之后每次修改 Chisel 设计后都需要重新进行以上的打包流程。

## Step3: 生成 XCLBIN

在 Vitis 平台中，XCLBIN 的功能类比于 bitstream，XCLBIN 在 host 程序执行时通过 XRT API 加载到加速器板卡。

生成 XCLBIN 的过程在 Vitis 平台中称为 link，具体步骤如下：

* 将 RTL Kernel Wizard 生成的 xo 文件复制到项目 `xo_kernel` 目录下，假设文件名为 `<XO_NAME>.xo`（例如 `chisel_vecadd.xo`)。

* 参考 `xo_kernel/chisel_vecadd.cfg` 和 【UG1393】Ch13.Build the Device Binary - Linking the Kernels，编写 v++ link 配置，保存为 `<XO_NAME>.cfg`（例如 `chisel_vecadd.cfg`)。其中一些关键字段解释：
  * plaform 设置为运行kernel的 vitis 平台名称，可以通过 `plaforminfo -l` 命令查询
  * `[connectivity]` 中 nk 设置 kernel 实例化的数量和名称
  * `[connectivity]` 中 sp 设置 kernel memory port 和板卡 memory 的连接，板卡的 memory 资源信息可以通过 `platforminfo ` 查询
  * `[debug]` 和 `[profile]` 启用调试和性能分析配置，根据需要启用，具体参考 【UG1393】Ch18, Ch19, Ch20.
  
* 执行 `make xclbin XO=<XO_NAME>` (例子中为 `make xclbin XO=chisel_vecadd`)，开始 xclbin 打包。(时间很久)

* 完成后，`xo_kernel` 目录下会产生：`<XO_NAME>.xclbin` 及一系列辅助文件，可以使用 Vitis Analyzer 分析，查看时序、面积等信息。

## Step4: 编译 Host 端程序并执行

`host/host.cpp` 提供了一个使用 XRT Native API 编写的 Host 端程序的完整例子，Host 端程序负责控制数据传输和Kernel执行，可参考 【UG1393】Ch6 了解 Host 端程序的开发方法。

* 根据 Host 端程序构建需要修改 Makefile 中 host flow 部分；

* 使用 XRT Native API 需要添加 `-I$(XILINX_XRT)/include -L$(XILINX_XRT)/lib -lxrt_coreutil -pthread` 编译器参数；

* 默认编译生成的可执行文件位于 `build/host/host_executable` 处。

* 如果 Host 端程序符合 Vitis 建议的方式——在第一个参数指定 xclbin 路径，则可直接使用 `make run XCLBIN=<XCLBIN_NAME>` 命令执行，XCLBIN_NAME 和之前的 XO_NAME 对应（例如：`make run XCLBIN=chisel_vecadd`)

## Hardware 调试

* 如果在 Step3 中 `<XO_NAME>.cfg` 配置启用了调试，可通过 Vivado Hardware Manager 连接到 XVC 使用 System ILA 等调试功能。

* 执行 `make hw_debug` 命令启动 Xilinx Virtual Cable，之后可在 Vivado Hardware Manager 中连接。（可能需要修改 Makefile 中 DEV_XVC_PUB 变量）

* ILA probe 需要的 ltx 文件位于 `xo_kernel` 目录中。

* 使用 ILA 时，需要在 host program 中插入停顿，以提供设置 trigger 的机会，参考 `host/host.cpp` 中 `wait_for_enter` 函数实现。



