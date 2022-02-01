package vitisrtlkernel.interface

import chisel3._
import chisel3.util._

class VitisAXIReadMaster(val addrWidth:Int, val dataWidth:Int) extends Bundle {
    val ar = Decoupled(new Bundle{
        val addr = UInt(addrWidth.W)
        val len = UInt(8.W)
    })

    val r = Flipped(Decoupled(new Bundle{
        val data = UInt(dataWidth.W)
        val last = Bool()
    }))
}

class VitisAXIWriteMaster(val addrWidth:Int, val dataWidth:Int) extends Bundle {
    val aw = Decoupled(new Bundle{
        val addr = UInt(addrWidth.W)
        val len = UInt(8.W)
    })

    val w = Decoupled(new Bundle{
        val data = UInt(dataWidth.W)
        val strb = UInt((dataWidth/8).W)
        val last = Bool()
    })

    val b = Flipped(Decoupled(new Bundle{}))
}