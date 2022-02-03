package vitisrtlkernel.mmstream

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

import java.util.Random
import scala.collection.mutable.ArrayBuffer
import vitisrtlkernel.util.PrintInt
import vitisrtlkernel.interface.{VitisAXIReadMaster, VitisAXIWriteMaster}
import chisel3.util.DecoupledIO

class AXIReadSlaveSim(
  val mem:        MemSim,
  val readMaster: VitisAXIReadMaster,
  val clock:      Clock,
  val done:       Bool,
  val gap:        Boolean = false,
  val log:        Boolean = true) {

  private val rand = new Random()

  private def insertGap() = {
    if (gap) {
      clock.step(rand.nextInt(5))
    }
  }

  private def isDone(): Boolean = {
    return done.peek().litToBoolean
  }

  def serve() = {
    val ar = readMaster.ar
    val r  = readMaster.r
    while (!isDone) {
      var addr = 0
      var len  = 0
      // 先地址
      timescope {
        ar.ready.poke(true.B)
        while (!isDone && !ar.valid.peek().litToBoolean) {
          clock.step(1)
        }
        addr = ar.bits.addr.peek().litValue.intValue
        len  = ar.bits.len.peek().litValue.intValue + 1
        clock.step(1)
        if (log) {
          println(s"[AXIReadSlave] request addr=${addr} len=${len}")
        }
      }
      // 再数据
      while (!isDone && len > 0) {
        timescope {
          insertGap()
          r.valid.poke(true.B)
          r.bits.data.poke(mem.read(addr, readMaster.dataWidth / 8).U)
          r.bits.last.poke((len == 1).B)
          while (!isDone && !r.ready.peek().litToBoolean) {
            clock.step(1)
          }
          clock.step(1)
          if (log) {
            println(s"[AXIReadSlave] read addr=${addr}")
          }
          addr = addr + readMaster.dataWidth / 8
          len -= 1
        }
      }
    }
  }
}

class AXIWriteSlaveSim(
  val mem:         MemSim,
  val writeMaster: VitisAXIWriteMaster,
  val clock:       Clock,
  val done:        Bool,
  val gap:         Boolean = false,
  val log:         Boolean = true) {
  private var terminated = false
  private val rand       = new Random()

  private def insertGap() = {
    if (gap) {
      clock.step(rand.nextInt(5))
    }
  }

  private def isDone(): Boolean = {
    return done.peek().litToBoolean
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
    while (!isDone()) {
      // serve aw
      timescope {
        insertGap()
        aw.ready.poke(true.B)
        while (!isDone && !aw.valid.peek().litToBoolean) {
          clock.step(1)
        }
        addr = aw.bits.addr.peek().litValue.intValue
        len  = aw.bits.len.peek().litValue.intValue + 1
        if (log) {
          println(s"[AXIWriteSlave] request addr=${addr} len=${len}")
        }
        clock.step(1)
      }

      // serve w
      for (burstNr <- 0 until len) {
        timescope {
          insertGap()
          w.ready.poke(true.B)
          while (!isDone && !w.valid.peek().litToBoolean) {
            clock.step(1)
          }
          if (log) {
            println(s"[AXIWriteSlave] write addr=${addr + burstNr * writeMaster.dataWidth / 8}")
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
        while (!isDone && !b.ready.peek().litToBoolean) {
          clock.step(1)
        }
        clock.step(1)
      }
    }
  }
}
