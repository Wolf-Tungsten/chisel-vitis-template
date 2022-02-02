package vitisrtlkernel.mmstream

import chisel3._
import chisel3.util._
import vitisrtlkernel.util.DebugLog
import vitisrtlkernel.interface.VitisAXIWriteMaster

class S2MM(val ADDR_WIDTH: Int, val DATA_WIDTH: Int) extends Module with DebugLog {

  val LEN_WIDTH = 32
  val dataWidthBytes: Int = DATA_WIDTH / 8
  val addrAlignBits:  Int = log2Ceil(dataWidthBytes) // 强制地址对齐到数据位宽
  val BURST_LEN = 64

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new Bundle {
      val addr = UInt(ADDR_WIDTH.W)
    }))
    val axiWrite = new VitisAXIWriteMaster(ADDR_WIDTH, DATA_WIDTH)
    val streamIn = Flipped(Decoupled(new Bundle {
      val data = UInt(DATA_WIDTH.W)
      val last = Bool()
    }))
  })

  val addr_reg      = Reg(UInt(ADDR_WIDTH.W))
  val burstLen_reg  = Reg(UInt(LEN_WIDTH.W))
  val issuedLen_reg = Reg(UInt(LEN_WIDTH.W))
  val eot_reg       = RegInit(true.B)
  val burstLast_wire = (burstLen_reg <= 1.U)

  val buffer_module = Module(
    new Queue(UInt(DATA_WIDTH.W), (BURST_LEN * 1.5).toInt)
  )

  val isAlignedTo4KBoundry_wire = ((addr_reg & Fill(12, 1.U)) === 0.U)
  val next4KBoundary_wire       = ((addr_reg + 4096.U) & (~"hfff".U(ADDR_WIDTH.W)).asUInt)
  val headLen_wire = (next4KBoundary_wire - addr_reg) >> addrAlignBits

  val sIdle :: sAddrCompute :: sHeadWaitBuffer :: sHeadAddr :: sHeadData :: sHeadResp :: sWaitBuffer :: sAddr :: sData :: sResp :: Nil =
    Enum(10)
  val state_reg = RegInit(sIdle)

  // 各个接口的初始状态
  io.req.ready := false.B

  io.axiWrite.aw.valid     := false.B
  io.axiWrite.aw.bits.addr := addr_reg + (issuedLen_reg << addrAlignBits)
  io.axiWrite.aw.bits.len  := 0.U

  io.axiWrite.w.valid     := false.B
  io.axiWrite.w.bits.data := buffer_module.io.deq.bits
  io.axiWrite.w.bits.last := burstLast_wire
  io.axiWrite.w.bits.strb := Fill(io.axiWrite.w.bits.strb.getWidth, 1.U)

  io.axiWrite.b.ready := false.B

  buffer_module.io.enq.bits := io.streamIn.bits.data
  buffer_module.io.deq.ready := false.B

  // enqueue flow control
  val freezeBuffer_wire =
    (eot_reg || state_reg === sIdle || state_reg === sAddrCompute || state_reg === sHeadAddr || state_reg === sAddr)
  buffer_module.io.enq.valid := ~freezeBuffer_wire && io.streamIn.valid
  io.streamIn.ready          := ~freezeBuffer_wire && buffer_module.io.enq.ready
  when(io.streamIn.fire && io.streamIn.bits.last) {
    // streamIn 中 last 有效时置位 eot，在下一次 idle 之前不能再输入
    eot_reg := true.B
  }

  switch(state_reg) {
    is(sIdle) {
      io.req.ready := true.B
      when(io.req.valid){
        eot_reg := false.B
        issuedLen_reg := 0.U
        addr_reg := Cat(io.req.bits.addr >> addrAlignBits, 0.U(addrAlignBits.W))
        state_reg := sAddrCompute
      }
    }

    is(sAddrCompute){
      when(isAlignedTo4KBoundry_wire){
        // 直接按 4K 处理
        state_reg := sWaitBuffer
      }.otherwise{
        // 先补齐到 4K
        state_reg := sHeadWaitBuffer
      }
    }

    is(sHeadWaitBuffer){
      when(eot_reg || buffer_module.io.count >= headLen_wire){
        state_reg := sHeadAddr
      }
    }

    is(sHeadAddr){
      io.axiWrite.aw.valid := true.B
      val burstLen_wire = Mux(buffer_module.io.count >= headLen_wire, headLen_wire, buffer_module.io.count)
      io.axiWrite.aw.bits.len := burstLen_wire - 1.U
      burstLen_reg := burstLen_wire
      when(io.axiWrite.aw.ready){
        state_reg := sHeadData
        issuedLen_reg := issuedLen_reg + burstLen_wire
      }
    }

    is(sHeadData){
      io.axiWrite.w.valid := buffer_module.io.deq.valid
      buffer_module.io.deq.ready := io.axiWrite.w.ready
      when(buffer_module.io.deq.fire){
        burstLen_reg := burstLen_reg - 1.U
        when(burstLast_wire){
          state_reg := sHeadResp
        }
      }
    }

    is(sHeadResp){
      io.axiWrite.b.ready := true.B
      when(io.axiWrite.b.valid){
        state_reg := sWaitBuffer
      }
    }

    is(sWaitBuffer){
      when(buffer_module.io.count >= BURST_LEN.U){
        state_reg := sAddr
      }.elsewhen(eot_reg){
        when(buffer_module.io.count > 0.U){
          state_reg := sAddr
        }.otherwise {
          state_reg := sIdle
        }
      }
    }

    is(sAddr){
      io.axiWrite.aw.valid := true.B
      val burstLen_wire = Mux(buffer_module.io.count >= BURST_LEN.U, BURST_LEN.U, buffer_module.io.count)
      io.axiWrite.aw.bits.len := burstLen_wire - 1.U
      burstLen_reg := burstLen_wire
      when(io.axiWrite.aw.ready){
        state_reg := sData
        issuedLen_reg := issuedLen_reg + burstLen_wire
      }
    }
    
    is(sData){
      io.axiWrite.w.valid := buffer_module.io.deq.valid
      buffer_module.io.deq.ready := io.axiWrite.w.ready
      when(buffer_module.io.deq.fire){
        burstLen_reg := burstLen_reg - 1.U
        when(burstLast_wire){
          state_reg := sResp
        }
      }
    }

    is(sResp){
      io.axiWrite.b.ready := true.B
      when(io.axiWrite.b.valid){
        state_reg := sWaitBuffer
      }
    }
  }

}
