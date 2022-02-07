#include <xrt/xrt_kernel.h>
#include <xrt/xrt_bo.h>
#include <iostream>
#include <assert.h>

void wait_for_enter(const std::string &msg) {
    std::cout << msg << std::endl;
    std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
}

int main(int argc, char **args)
{
    std::cout << args[1] << std::endl;

    /**
     * @brief how to determine device_index
     * TIP: The device ID can be obtained using the xbutil  command for a specific accelerator.
     */
    unsigned int device_index = 0; 
    std::cout << "Open the device" << device_index << std::endl;
    auto device = xrt::device(device_index);

    std::string xclbin_path(args[1]);
    std::cout << "Load the xclbin " << xclbin_path << std::endl;
    auto xclbin_uuid = device.load_xclbin(xclbin_path);

    // instantiate kernel
    auto krnl = xrt::kernel(device, xclbin_uuid, "chisel_vecadd");

    wait_for_enter("setup ila and [Enter] to continue...");

    
    size_t data_num = 4096;
    uint32_t input_data[data_num];
    uint32_t output_data[data_num];
    for(size_t i = 0; i < data_num; i++){
        input_data[i] = i % 128;
    }
    
    // allocate buffer on board
    auto read_buffer = xrt::bo(device, data_num * sizeof(uint32_t), krnl.group_id(1));
    auto write_buffer = xrt::bo(device, data_num * sizeof(uint32_t), krnl.group_id(2));
    
    // 输入数据传输到 board
    read_buffer.write(input_data);
    read_buffer.sync(XCL_BO_SYNC_BO_TO_DEVICE);

    auto run = krnl(data_num * 4 / 64, read_buffer, write_buffer);
    run.wait();

    // 计算结果从 board read 回 host
    write_buffer.sync(XCL_BO_SYNC_BO_FROM_DEVICE);
    write_buffer.read(output_data);

    // check result
    for(size_t i = 0; i < data_num; i++){
        assert(input_data[i] + 47 == output_data[i]);
        std::cout << "input:" << input_data[i] << " output:" << output_data[i] << std::endl;
    } 
}