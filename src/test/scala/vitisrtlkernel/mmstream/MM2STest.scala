package vitisrtlkernel.mmstream

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

import java.util.Random
import scala.collection.mutable.ArrayBuffer
import vitisrtlkernel.util.PrintInt
import chiseltest.simulator.{VcsBackendAnnotation, VcsFlags, WriteFsdbAnnotation, WriteVcdAnnotation}
import vitisrtlkernel.interface.VitisAXIReadMaster
import chisel3.util.DecoupledIO

class MM2STest extends AnyFreeSpec with ChiselScalatestTester {

  class AXIReadSlaveSim(
                         val mem: MemSim,
                         val readMaster: VitisAXIReadMaster,
                         val clock: Clock,
                         val gap: Boolean = false,
                         val log: Boolean = true) {
    private var terminated = false
    private val rand = new Random()

    private def insertGap() = {
      if (gap) {
        clock.step(rand.nextInt(5))
      }
    }

    def reset() = {
      terminated = false
    }

    def terminate() = {
      terminated = true
    }

    def serve() = {
      val ar = readMaster.ar
      val r = readMaster.r
      while (!terminated) {
        var addr = 0
        var len = 0
        // 先地址
        timescope {
          ar.ready.poke(true.B)
          while (!terminated && !ar.valid.peek().litToBoolean) {
            clock.step(1)
          }
          addr = ar.bits.addr.peek().litValue.intValue
          len = ar.bits.len.peek().litValue.intValue + 1
          clock.step(1)
          if (log) {
            println(s"[AXIReadSlave] request addr=${addr} len=${len}")
          }
        }
        // 再数据
        while (!terminated && len > 0) {
          timescope {
            insertGap()
            r.valid.poke(true.B)
            r.bits.data.poke(mem.read(addr, readMaster.dataWidth / 8).U)
            r.bits.last.poke((len == 1).B)
            while (!terminated && !r.ready.peek().litToBoolean) {
              clock.step(1)
            }
            clock.step(1)
            if (log) {
              println(s"[AXIReadSlave] serve addr=${addr}")
            }
            addr = addr + readMaster.dataWidth / 8
            len -= 1
          }
        }
      }
    }
  }

  class StreamSlaveSim(
                           val master: DecoupledIO[Bundle{val data:UInt; val last:Bool}],
                           val clock: Clock,
                           val gap: Boolean = false,
                           val log: Boolean = true) {
    private var last = false
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
    var addr = _addr
    var lenBytes = _lenBytes
    addr = addr - addr % (dut.DATA_WIDTH / 8)
    lenBytes = lenBytes + ((dut.DATA_WIDTH / 8) - (lenBytes % (dut.DATA_WIDTH / 8)))
    val len = lenBytes / (dut.DATA_WIDTH / 8)
    println(len)
    val mem = new MemSim(8 * 1024 * 1024)
    mem.modInit(47)
    val axiReadSlave = new AXIReadSlaveSim(mem, dut.io.axiRead, dut.clock, true, false)
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
      val memResult = mem.read(addr + i * dut.DATA_WIDTH / 8, dut.DATA_WIDTH / 8)
      val axisResult = axiStreamSlave.getDataBuf()(i)
      print(s">>>>>>> ${i} <<<<<<<<\n")
      PrintInt.printBigIntLSB(memResult)
      PrintInt.printBigIntLSB(axisResult)
      assert(memResult == axisResult)
    }
  }

  // "4K-Aligned NOT-Across-4K" in {
  //   test(new MM2S(64, 512))
  //     .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //       val rand = new Random
  //       singleTranscation(dut, 4096 * rand.nextInt(100), rand.nextInt(4096))
  //     }
  // }

  // "4K-Aligned Across-4K" in {
  //   test(new MM2S(64, 512))
  //     .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //       val rand = new Random
  //       singleTranscation(dut, 4096 * rand.nextInt(100), rand.nextInt(10) * 4096  + 64 * rand.nextInt(100))
  //     }
  // }

  // "NOT-4K-Aligned NOT-Across-4K" in {
  //   test(new MM2S(64, 512))
  //     .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //       val rand = new Random
  //       singleTranscation(dut, 64 * rand.nextInt(100), rand.nextInt(4096))
  //     }
  // }

  // "NOT-4K-Aligned Across-4K" in {
  //   test(new MM2S(64, 512))
  //     .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //       val rand = new Random
  //       singleTranscation(dut, 64 * rand.nextInt(100), rand.nextInt(10) * 4096  + 64 * rand.nextInt(100))
  //     }
  // }

  //WriteFsdbAnnotation
  "Multiple Transaction" in {
    test(new MM2S(64, 512))
      .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
        val rand = new Random
        for(i <- 0 until 10){
          singleTranscation(dut, 64 * rand.nextInt(100),  64 * rand.nextInt(1024))
        }
      }
  }
}