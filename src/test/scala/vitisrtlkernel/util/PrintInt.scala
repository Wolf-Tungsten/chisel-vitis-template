package vitisrtlkernel.util

import chisel3._

object PrintInt {
    def printBigIntLSB(_n:BigInt) = {
        var n = _n
        while(n > 0){
            print((n & 0xff).intValue)
            print(" ")
            n >>= 8
        }
        println()
    }
    def printUIntLSB(_n:UInt) = {
        val v = Wire(Vec(_n.getWidth / 8, UInt(8.W)))
        v := _n.asTypeOf(v)
        v.foreach((n) => {
            printf(p"${_n} ")
        })
        printf("\n")
    }
}