package vitisrtlkernel

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import scala.util.Random

import vitisrtlkernel.mmstream.MemSim
import vitisrtlkernel.mmstream.AXIReadSlaveSim
import vitisrtlkernel.mmstream.AXIWriteSlaveSim

class VitisRTLKernelTest extends AnyFreeSpec with ChiselScalatestTester {

  class VitisRTLKernelWrapper extends Module {
    val io = IO(new Bundle {
      val ap_start = Input(Bool())
      val ap_idle  = Output(Bool())
      val ap_done  = Output(Bool())
      val ap_ready = Output(Bool())
      val dataIF   = new VitisRTLKernelDataIF
      val done     = Output(Bool())
    })

    val vitisRTLKernel = Module(new VitisRTLKernel)
    vitisRTLKernel.ap_clk   := clock
    vitisRTLKernel.ap_start := io.ap_start
    io.ap_idle              := vitisRTLKernel.ap_idle
    io.ap_done              := vitisRTLKernel.ap_done
    io.ap_ready             := vitisRTLKernel.ap_ready
    vitisRTLKernel.dataIF <> io.dataIF

    val done_reg = RegInit(false.B)
    when(vitisRTLKernel.ap_done) {
      done_reg := true.B
    }
    io.done := done_reg
  }

  "KernelExecution" in {
    test(new VitisRTLKernelWrapper)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val rand    = new Random()
        val readMem = new MemSim(16 * 1024 * 1024)
        readMem.randomInit()
        val writeMem      = new MemSim(16 * 1024 * 1024)
        val readAddress   = 64 * rand.nextInt(1024)
        val writeAddress  = 64 * rand.nextInt(1024)
        val readLength    = 1 + rand.nextInt(1024)
        val axiReadSlave  = new AXIReadSlaveSim(readMem, dut.io.dataIF.m00Read, dut.clock, dut.io.done, true, true)
        val axiWriteSlave = new AXIWriteSlaveSim(writeMem, dut.io.dataIF.m00Write, dut.clock, dut.io.done, true, true)
        fork {
          dut.io.dataIF.readAddress.poke(readAddress.U)
          dut.io.dataIF.readLength.poke(readLength.U)
          dut.io.dataIF.writeAddress.poke(writeAddress.U)
          dut.io.ap_start.poke(false.B)
          dut.clock.step(2)
          dut.io.ap_start.poke(true.B)
          while(!dut.io.done.peek().litToBoolean){
            dut.clock.step(1)
          }
          dut.io.ap_start.poke(false.B)
        }.fork {
          axiReadSlave.serve()
        }.fork {
          axiWriteSlave.serve()
        }.join()
        // 检查正确性
        for (i <- 0 until readLength / 2 * 64) {
          assert((readMem.read(readAddress + i * 2, 2) + 47) % 65536 == writeMem.read(writeAddress + i * 2, 2))
        }
      }
  }
}
