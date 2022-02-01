package vitisrtlkernel.mmstream

import scala.util.Random

class MemSim(val length: Int) {
  private val mem = Array.fill[Byte](length)(0)

  def read(addr: Int, nBytes: Int): BigInt = {
    var res = BigInt(0)
    for (i <- 0 until nBytes) {
      res += (BigInt(mem((addr + i) % length)) << (i * 8))
    }
    res
  }

  def write(addr: Int, data: BigInt, nBytes: Int = 0): Unit = {
    if (nBytes > 0) {
      for (i <- 0 until nBytes) {
        mem.update((addr + i) % length, (data >> (i * 8) & 0xff).byteValue)
      }
    } else {
      var remainData = data
      var addrBias   = 0
      while (remainData > 0) {
        mem.update((addr + addrBias) % length, (remainData & 0xff).byteValue)
        addrBias += 1
        remainData >>= 8
      }
    }
  }

  def randomInit() = {
    val rand = new Random()
    for (i <- 0 until length) {
      mem(i) = rand.nextInt(256).byteValue()
    }
  }

  def modInit(m: Int) = {
    for (i <- 0 until length) {
      mem(i) = (i % m).byteValue()
    }
  }
}