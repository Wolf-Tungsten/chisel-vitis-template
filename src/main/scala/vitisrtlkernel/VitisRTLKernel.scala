package vitisrtlkernel

import chisel3._
import chisel3.util._
import vitisrtlkernel.interface.VitisAXIReadMaster
import vitisrtlkernel.interface.VitisAXIWriteMaster

class VitisRTLKernelDataIF extends Bundle {
  // Register Args
  val readAddress = Input(UInt(64.W))
  val readLength = Input(UInt(64.W))
  val writeAddress = Input(UInt(64.W))

  // HBM/DDR ports
  val m00Read  = new VitisAXIReadMaster(64, 512)
  val m00Write = new VitisAXIWriteMaster(64, 512)
}

class VitisRTLKernel extends RawModule {
  val ap_clk   = IO(Input(Clock()))
  val ap_start = IO(Input(Bool()))
  val ap_idle  = IO(Output(Bool()))
  val ap_done  = IO(Output(Bool()))
  val ap_ready = IO(Output(Bool()))

  val dataIF = IO(new VitisRTLKernelDataIF)

  ap_idle := false.B
  ap_done := false.B
  ap_ready := false.B

  val reset_w = Wire(Bool())

  reset_w := false.B

  val exampleDesign = withClockAndReset(ap_clk, reset_w)(Module(new VecAdd))

  dataIF <> exampleDesign.io.dataIF

  val sIdle :: sReset1 :: sReset2 :: sBusy :: sDone :: Nil = Enum(5)

  val state_r = withClockAndReset(ap_clk, !ap_start)(RegInit(sIdle))


  switch(state_r) {
    is(sIdle) {
      ap_idle := true.B
      reset_w := true.B
      when(ap_start) {
        state_r := sReset1
      }
    }
    is(sReset1) {
      reset_w := true.B
      state_r := sReset2
    }
    is(sReset2) {
      reset_w := true.B
      state_r := sBusy
    }
    is(sBusy) {
      when(exampleDesign.io.done) {
        state_r := sDone
      }
    }
    is(sDone) {
      ap_done  := true.B
      ap_ready := true.B
      state_r  := sIdle
    }
  }
}
