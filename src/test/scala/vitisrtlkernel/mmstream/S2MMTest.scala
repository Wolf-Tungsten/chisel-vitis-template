package vitisrtlkernel.mmstream

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

import java.util.Random
import scala.collection.mutable.ArrayBuffer
import vitisrtlkernel.util.PrintInt
import chiseltest.simulator.{VcsBackendAnnotation, VcsFlags, WriteFsdbAnnotation, WriteVcdAnnotation}
import vitisrtlkernel.interface.VitisAXIWriteMaster
import chisel3.util.DecoupledIO

class SS2MTest extends AnyFreeSpec with ChiselScalatestTester {

  class AXIWriteSlaveSim(
    val mem:         MemSim,
    val writeMaster: VitisAXIWriteMaster,
    val clock:       Clock,
    val startAddr:   Int,
    val writeLen:    Int,
    val gap:         Boolean = false,
    val log:         Boolean = true) {
    private var terminated = false
    private val rand       = new Random()

    private def insertGap() = {
      if (gap) {
        clock.step(rand.nextInt(5))
      }
    }

    def reset() = {
      terminated = false
    }

    def serve() = {
      val aw   = writeMaster.aw
      val w    = writeMaster.w
      val b    = writeMaster.b
      var addr = 0
      var len  = 0
      while (!terminated) {
        // serve aw
        timescope {
          insertGap()
          aw.ready.poke(true.B)
          while (!aw.valid.peek().litToBoolean) {
            clock.step(1)
          }
          addr = aw.bits.addr.peek().litValue.intValue
          len  = aw.bits.len.peek().litValue.intValue + 1
          clock.step(1)
        }

        // serve w
        for (burstNr <- 0 until len) {
          timescope {
            insertGap()
            w.ready.poke(true.B)
            while (!w.valid.peek().litToBoolean) {
              clock.step(1)
            }
            mem.write(addr + burstNr * writeMaster.dataWidth / 8, w.bits.data.peek().litValue)
            if (burstNr == len - 1) {
              w.bits.last.expect(true.B)
            }
            clock.step(1)
          }
        }

        // serve b
        timescope {
          insertGap()
          b.valid.poke(true.B)
          while (!b.ready.peek().litToBoolean) {
            clock.step(1)
          }
          clock.step(1)
          if (
            addr + len * writeMaster.dataWidth / 8 >=
              startAddr + writeLen * writeMaster.dataWidth / 8
          ) {
            // 写完所有地址
            terminated = true
          }
        }
      }
    }

  }

  class StreamSlaveSim(
    val master: DecoupledIO[Bundle { val data: UInt; val last: Bool }],
    val clock:  Clock,
    val gap:    Boolean = false,
    val log:    Boolean = true) {
    private var last    = false
    private val dataBuf = ArrayBuffer.empty[BigInt]

    def isLast(): Boolean = {
      return last
    }

    def getDataBuf(): ArrayBuffer[BigInt] = {
      return dataBuf
    }

    private val rand = new Random()

    private def insertGap() = {
      if (gap) {
        clock.step(rand.nextInt(5))
      }
    }

    def reset() = {
      last = false
      dataBuf.clear()
    }

    def serve() = {
      while (!last) {
        timescope {
          insertGap()
          master.ready.poke(true.B)
          while (!master.valid.peek().litToBoolean) {
            clock.step(1)
          }
          dataBuf.append(master.bits.data.peek().litValue)
          last = master.bits.last.peek().litToBoolean
          clock.step(1)
        }
      }
    }
  }

  def issueRequest(dut: MM2S, addr: Int, len: Int) = {
    timescope {
      dut.io.req.bits.addr.poke(addr.U)
      dut.io.req.bits.len.poke(len.U)
      dut.io.req.valid.poke(true.B)
      while (!dut.io.req.ready.peek().litToBoolean) {
        dut.clock.step(1)
      }
      dut.clock.step(1)
    }
  }

  def singleTranscation(dut: MM2S, _addr: Int, _lenBytes: Int) = {
    var addr     = _addr
    var lenBytes = _lenBytes
    addr     = addr - addr % (dut.DATA_WIDTH / 8)
    lenBytes = lenBytes + ((dut.DATA_WIDTH / 8) - (lenBytes % (dut.DATA_WIDTH / 8)))
    val len = lenBytes / (dut.DATA_WIDTH / 8)
    println(len)
    val mem = new MemSim(8 * 1024 * 1024)
    mem.modInit(47)
    val axiReadSlave   = new AXIReadSlaveSim(mem, dut.io.axiRead, dut.clock, true, false)
    val axiStreamSlave = new StreamSlaveSim(dut.io.streamOut, dut.clock, true, false)
    // issueRequest
    issueRequest(dut, addr, len)
    // serve AXIRead and AXIStream
    fork {
      axiReadSlave.serve()
    }.fork {
      axiStreamSlave.serve()
    }.fork {
      while (!axiStreamSlave.isLast()) {
        dut.clock.step(1)
      }
      axiReadSlave.terminate()
    }.join()
    // check result
    for (i <- 0 until len) {
      val memResult  = mem.read(addr + i * dut.DATA_WIDTH / 8, dut.DATA_WIDTH / 8)
      val axisResult = axiStreamSlave.getDataBuf()(i)
      print(s">>>>>>> ${i} <<<<<<<<\n")
      PrintInt.printBigIntLSB(memResult)
      PrintInt.printBigIntLSB(axisResult)
      assert(memResult == axisResult)
    }
  }

  //WriteFsdbAnnotation
  "Multiple Transaction" in {
    test(new MM2S(64, 512))
      .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
        val rand = new Random
        for (i <- 0 until 10) {
          singleTranscation(dut, 64 * rand.nextInt(100), 64 * rand.nextInt(1024))
        }
      }
  }
}
