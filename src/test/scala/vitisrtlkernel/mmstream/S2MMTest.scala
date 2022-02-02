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

class S2MMTest extends AnyFreeSpec with ChiselScalatestTester {

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
            mem.write(addr + burstNr * writeMaster.dataWidth / 8, w.bits.data.peek().litValue, writeMaster.dataWidth / 8)
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

  class StreamMasterSim(
    val mem:       MemSim,
    val slave:     DecoupledIO[Bundle { val data: UInt; val last: Bool }],
    val clock:     Clock,
    val dataWidth: Int,
    val startAddr: Int,
    val writeLen:  Int,
    val gap:       Boolean = false,
    val log:       Boolean = true) {

    private val rand = new Random()

    private def insertGap() = {
      if (gap) {
        clock.step(rand.nextInt(5))
      }
    }

    def serve() = {
      println(writeLen)
      for (burstNr <- 0 until writeLen) {
        timescope {
          insertGap()
          slave.valid.poke(true.B)
          slave.bits.last.poke((burstNr == writeLen - 1).B)
          slave.bits.data.poke(mem.read(startAddr + burstNr * dataWidth / 8, dataWidth / 8).U)
          while (!slave.ready.peek().litToBoolean) {
            clock.step(1)
          }
          clock.step(1)
        }
      }
    }
  }

  def issueRequest(dut: S2MM, startAddr: Int, writeLen: Int) = {
    timescope {
      dut.io.req.bits.addr.poke(startAddr.U)
      dut.io.req.valid.poke(true.B)
      while (!dut.io.req.ready.peek().litToBoolean) {
        dut.clock.step(1)
      }
      dut.clock.step(1)
    }
  }

  def singleTranscation(dut: S2MM, startAddr: Int, writeLen: Int) = {
    val inMem  = new MemSim(8 * 1024 * 1024)
    val outMem = new MemSim(8 * 1024 * 1024)
    inMem.modInit(47)
    outMem.randomInit()
    val streamMasterSim =
      new StreamMasterSim(inMem, dut.io.streamIn, dut.clock, dut.DATA_WIDTH, startAddr, writeLen, true, true)
    val axiWriteSlaveSim = new AXIWriteSlaveSim(outMem, dut.io.axiWrite, dut.clock, startAddr, writeLen, true, true)
    // issueRequest
    issueRequest(dut, startAddr, writeLen)
    // serve AXIRead and AXIStream
    fork {
      streamMasterSim.serve()
    }.fork {
      axiWriteSlaveSim.serve()
    }.join()
    // check result
    for (i <- 0 until writeLen) {
      val exp = inMem.read(startAddr + i * dut.DATA_WIDTH / 8, dut.DATA_WIDTH / 8)
      val act = outMem.read(startAddr + i * dut.DATA_WIDTH / 8, dut.DATA_WIDTH / 8)
      print(s">>>>>>> ${i} <<<<<<<<\n")
      PrintInt.printBigIntLSB(exp)
      PrintInt.printBigIntLSB(act)
      assert(exp == act)
    }
  }

  //WriteFsdbAnnotation
  "Multiple Transaction" in {
    test(new S2MM(64, 512))
      .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
        val rand = new Random
        for (i <- 0 until 10) {
          singleTranscation(dut, 64 * rand.nextInt(100), rand.nextInt(1024))
        }
      }
  }
}
