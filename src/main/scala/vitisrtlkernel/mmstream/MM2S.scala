package vitisrtlkernel.mmstream

import chisel3._
import chisel3.util._

import chisel3._
import chisel3.util._
import vitisrtlkernel.util.DebugLog
import vitisrtlkernel.interface.VitisAXIReadMaster

class MM2S(val ADDR_WIDTH: Int, val DATA_WIDTH: Int) extends Module with DebugLog {

  val LEN_WIDTH = 32
  val dataWidthBytes: Int = DATA_WIDTH / 8
  val addrAlignBits:  Int = log2Ceil(dataWidthBytes) // 强制地址对齐到数据位宽
  val BURST_LEN = 64

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new Bundle {
      val addr = UInt(ADDR_WIDTH.W)
      val len  = UInt(LEN_WIDTH.W)
    }))
    val axiRead = new VitisAXIReadMaster(ADDR_WIDTH, DATA_WIDTH)
    val streamOut = Decoupled(new Bundle {
      val data = UInt(DATA_WIDTH.W)
      val last = Bool()
    })
  })

  val addr_reg      = Reg(UInt(ADDR_WIDTH.W))
  val len_reg       = Reg(UInt(LEN_WIDTH.W))
  val remainLen_reg = Reg(UInt(LEN_WIDTH.W))
  val issuedLen_reg = Reg(UInt(LEN_WIDTH.W))

  val buffer_module = Module(
    new Queue(UInt(DATA_WIDTH.W), (BURST_LEN * 1.5).toInt)
  )
  val bufferSpace_wire          = (buffer_module.entries.U - buffer_module.io.count)
  val isAlignedTo4KBoundry_wire = ((addr_reg & Fill(12, 1.U)) === 0.U)
  val next4KBoundary_wire       = ((addr_reg + 4096.U) & (~"hfff".U(ADDR_WIDTH.W)).asUInt)
  val isAcross4KBoundry_wire    = (addr_reg + (len_reg << addrAlignBits)) > next4KBoundary_wire
  io.req.ready        := false.B
  io.axiRead.ar.valid := false.B
  io.axiRead.ar.bits  := 0.U.asTypeOf(io.axiRead.ar.bits)
  io.axiRead.r.ready  := false.B

  buffer_module.io.enq.valid := false.B
  buffer_module.io.enq.bits  := io.axiRead.r.bits.data

  val sIdle :: sAddrCompute :: sHeadAddr :: sHeadData :: s4KAlignedAddr :: s4KAlignedData :: sTailAddr :: sTailData :: sFlush :: sEmpty :: nil =
    Enum(10)
  val state_reg: UInt = RegInit(sIdle)
  switch(state_reg) {
    is(sIdle) {
      io.req.ready := true.B
      when(io.req.valid) {
        addr_reg  := Cat(io.req.bits.addr >> addrAlignBits, 0.U(addrAlignBits.W))
        len_reg   := io.req.bits.len
        state_reg := sAddrCompute
      }
    }

    is(sAddrCompute) {
      remainLen_reg := len_reg
      issuedLen_reg := 0.U
      debugLog(
        p"[sAddrCompute] addr_reg=${addr_reg} len_reg=${len_reg} " +
          p"isAlignedTo4KBoundry_wire=${isAlignedTo4KBoundry_wire} " +
          p"isAcross4kBoundry_wire=${isAcross4KBoundry_wire} " +
          p"next4KBoundary_wire=${next4KBoundary_wire} " +
          p"endAddress=${(addr_reg + (len_reg << addrAlignBits))}\n"
      )
      when(isAlignedTo4KBoundry_wire) {
        when(isAcross4KBoundry_wire) {
          state_reg := s4KAlignedAddr
        }.otherwise {
          state_reg := sTailAddr
        }
      }.otherwise {
        when(isAcross4KBoundry_wire) {
          state_reg := sHeadAddr
        }.otherwise {
          state_reg := sTailAddr
        }

      }
    }
    is(sHeadAddr) {
      debugLog("sHeadAddr\n")
      val headLen_wire: UInt = ((next4KBoundary_wire - addr_reg) >> addrAlignBits).asUInt
      when(bufferSpace_wire > headLen_wire) {
        io.axiRead.ar.valid     := true.B
        io.axiRead.ar.bits.addr := addr_reg
        io.axiRead.ar.bits.len  := headLen_wire - 1.U
        when(io.axiRead.ar.ready) {
          state_reg     := sHeadData
          remainLen_reg := len_reg - headLen_wire
        }
      }
    }
    is(sHeadData) {
      debugLog("sHeadData\n")
      io.axiRead.r.ready         := buffer_module.io.enq.ready
      buffer_module.io.enq.valid := io.axiRead.r.valid
      when(
        io.axiRead.r.valid && buffer_module.io.enq.ready && io.axiRead.r.bits.last
      ) {
        when(remainLen_reg >= BURST_LEN.U) {
          state_reg := s4KAlignedAddr
        }.elsewhen(remainLen_reg === 0.U) {
          state_reg := sFlush
        }.elsewhen(remainLen_reg < BURST_LEN.U) {
          state_reg := sTailAddr
        }
      }
    }
    is(s4KAlignedAddr) {
      debugLog("s4KAlignedAddr\n")
      when(bufferSpace_wire > BURST_LEN.U) {
        io.axiRead.ar.valid     := true.B
        io.axiRead.ar.bits.addr := addr_reg + ((len_reg - remainLen_reg) << addrAlignBits)
        io.axiRead.ar.bits.len  := (BURST_LEN - 1).U
        when(io.axiRead.ar.ready) {
          state_reg     := s4KAlignedData
          remainLen_reg := remainLen_reg - BURST_LEN.U
        }
      }
    }
    is(s4KAlignedData) {
      debugLog("s4KAlignedData\n")
      io.axiRead.r.ready         := buffer_module.io.enq.ready
      buffer_module.io.enq.valid := io.axiRead.r.valid
      when(
        io.axiRead.r.valid && buffer_module.io.enq.ready && io.axiRead.r.bits.last
      ) {
        when(remainLen_reg >= BURST_LEN.U) {
          state_reg := s4KAlignedAddr
        }.elsewhen(remainLen_reg === 0.U) {
          state_reg := sFlush
        }.elsewhen(remainLen_reg < BURST_LEN.U) {
          state_reg := sTailAddr
        }
      }
    }
    is(sTailAddr) {
      //debugLog("sTailAddr\n")
      when(bufferSpace_wire > remainLen_reg) {
        io.axiRead.ar.valid     := true.B
        io.axiRead.ar.bits.addr := addr_reg + ((len_reg - remainLen_reg) << addrAlignBits)
        io.axiRead.ar.bits.len  := remainLen_reg - 1.U
        when(io.axiRead.ar.ready) {
          state_reg := sTailData
        }
      }
    }
    is(sTailData) {
      //debugLog("sTailData\n")
      io.axiRead.r.ready         := buffer_module.io.enq.ready
      buffer_module.io.enq.valid := io.axiRead.r.valid
      when(
        io.axiRead.r.valid && buffer_module.io.enq.ready && io.axiRead.r.bits.last
      ) {
        state_reg := sFlush
      }
    }
    is(sFlush) {
      //debugLog("sFlush\n")
      // 等待 buffer 中数据读取完
      when(issuedLen_reg === len_reg) {
        state_reg := sEmpty
      }
    }
    is(sEmpty) {
      buffer_module.io.deq.ready := true.B
      when(buffer_module.io.count === 0.U) {
        state_reg := sIdle
      }
    }
  }

  io.streamOut.valid         := false.B
  io.streamOut.bits          := 0.U.asTypeOf(io.streamOut.bits)
  buffer_module.io.deq.ready := false.B

  when(state_reg =/= sIdle && state_reg =/= sAddrCompute) {
    io.streamOut.valid         := buffer_module.io.deq.valid
    buffer_module.io.deq.ready := io.streamOut.ready
    io.streamOut.bits.data     := buffer_module.io.deq.bits
    when(buffer_module.io.deq.valid) {
      io.streamOut.bits.last := (issuedLen_reg === len_reg - 1.U)
      when(io.streamOut.ready) {
        issuedLen_reg := issuedLen_reg + 1.U
      }
    }
  }

}
