package Sodor

import chisel3._
import chisel3.util._

import Constants._
import Common._
import Common.Util._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap


class Sodor1Stage extends Module {
	val FuzzDebug = false
	implicit val conf = SodorConfiguration()

	val io = IO(new Bundle {
		val imem = new MemPortIo(conf.xprlen)
		// if(FuzzDebug) {
		// 	val dmi = Flipped(new DMIIO())
		// }
		val reset = Input(Bool())
	})

	val debug = Module(new DebugModule())
	val core = Module(new Core())

	// we only use a memory for data, the instructions are fuzzed directly
	val memory = Module(new AsyncScratchPadMemory(num_core_ports = 1))
	val dmem = memory.io.core_ports(0)
	val debugmem = memory.io.debug_port

	core.io.dmem <> dmem
	core.io.imem <> io.imem

	debug.io.debugmem <> debugmem
	debug.io.ddpath <> core.io.ddpath
	debug.io.dcpath <> core.io.dcpath

	// if(FuzzDebug) {
	// 	core.reset := debug.io.resetcore | reset.toBool
	// 	debug.io.dmi <> io.dmi
	// } else {
		core.reset := reset.toBool
		core.io.reset := reset.toBool
		val dummy = Module(new DummyDMI)
		debug.io.dmi <> dummy.io
	// }
}


class Top extends Module
{
   val io = IO(new Bundle{
      val success = Output(Bool())
    })

   implicit val sodor_conf = SodorConfiguration()
   val tile = Module(new SodorTile)
   val dtm = Module(new SimDTM).connect(clock, reset.toBool, tile.io.dmi, io.success)
}

object elaborate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Sodor1Stage)
  }
}
