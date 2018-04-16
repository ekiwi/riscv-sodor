// sparse memory (using a CAM based hashtable)


package Common
{

import chisel3._
import chisel3.util._
import chisel3.experimental._

// Thrid Try:

// WARNING: non-functional debug (hw) write port
class SparseAsyncReadMem(val addrWidth : Int) extends Module {
	val dataWidth = 32
	val entries = 256
	val idxWidth = log2Ceil(entries)

	val io = IO(new d2h2i1(addrWidth))

	// hw_port is not implemented because wrapping my mind around how to reosolve
	// write conflicts is not going to happen tonight....
	// TODO: set DoNotCare or something...

	val address = Reg(Vec(entries, UInt(addrWidth.W)))
	// TODO: how can we add a reset to a vector register?
	val address_valid = Reg(Vec(entries, Bool()))
	val data = Mem(entries, UInt(dataWidth.W))

	// search for addresses
	case class MatchOption(valid: Bool, idx: UInt)
	def addrMatch(addr: UInt) = {
		val vector = Cat(address.zip(address_valid).map{
			case (a, v) => a === addr && v})
		MatchOption(vector =/= 0.U, OHToUInt(vector))
	}

	// read
	def read(addr: UInt) = {
		val mm = addrMatch(addr)
		Mux(mm.valid, data(mm.idx), 0.U)
	}
	io.dataInstr(0).data := read(io.dataInstr(0).addr)
	io.dataInstr(1).data := read(io.dataInstr(1).addr)
	io.hr.data := read(io.hr.addr)

	///// insertion /////
	val dw_match = addrMatch(io.dw.addr)

	// we need a new entry if the user wants to write and no existing entry was found
	val new_dw = io.dw.en && !dw_match.valid

	// replacement policy: fifo
	val insertPtr = RegInit(0.U(idxWidth.W))
	val nextInsertPtr = Mux(insertPtr === (entries-1).U, 0.U, insertPtr + 1.U)
	when(new_dw) { insertPtr := nextInsertPtr }

	val dw_insert: UInt = Mux(dw_match.valid, dw_match.idx, insertPtr)

	// dw
	when(io.dw.en) {
		address(dw_insert) := io.dw.addr
		address_valid(dw_insert) := true.B
		// TODO: obey mask!
		//val mask : Seq[Bool] = io.dw.mask.toBools
		//val dd = Vec(for(ii <- mask.size until 0) yield io.dw.data(ii*8-1, (ii-1)*8))
		//data.write(dw_insert, dd, mask)

		when(io.dw.mask =/= 0.U) {
			data(dw_insert) := io.dw.data
		}
	}
}

// WARNING: non-functional debug (hw) write port
class SparseSyncMem(val addrWidth : Int) extends Module {
	val io = IO(new d2h2i1(addrWidth))
	val async = Module(new SparseAsyncReadMem(addrWidth))

	// buffered connection for read ports
	async.io.dataInstr(0).addr := RegNext(io.dataInstr(0).addr, 0.U)
	io.dataInstr(0).data := async.io.dataInstr(0).data
	async.io.dataInstr(1).addr := RegNext(io.dataInstr(1).addr, 0.U)
	io.dataInstr(1).data := async.io.dataInstr(1).data
	async.io.hr.addr := RegNext(io.hr.addr, 0.U)
	io.hr.data := async.io.hr.data

	// write ports are always synchronous
	async.io.hw <> io.hw
	async.io.dw <> io.dw
}

// Second Try:


// class SparseAsyncReadMem(val addrWidth : Int) extends Module {
// 	val dataWidth = 32
// 	val entries = 256

// 	val io = IO(new d2h2i1(addrWidth))


// 	val address = Reg(Vec(entries, UInt(addrWidth.W)))
// 	val address_valid = Reg(Vec(entries, Bool()))
// 	val data = Mem(entries, UInt(dataWidth.W))

	// search for addresses
// 	case class MatchOption(valid: Bool, idx: UInt)
// 	def addrMatch(addr: UInt) = {
// 		val vector = address.zip(address_valid).map(_ === addr && _).toUInt
// 		MatchOption(vector =/= 0.U, OHToUInt(vector))
// 	}
// 	val hw_match = addrMatch(io.hw_addr)
// 	val dw_match = addrMatch(io.dw_addr)
// 	val dataInstr_0_match = addrMatch(io.dataInstr_0_addr)
// 	val dataInstr_1_match = addrMatch(io.dataInstr_1_addr)


	///// insertion /////
	// we need a new entry if the user wants to write and no existing entry was found
// 	val new_hw = io.hw_en && !hw_match.valid
// 	val new_dw = io.dw_en && !dw_match.valid
// 	val hw_insert = Mux(hw_match.valid, hw_match.idx, insertPtr)
// 	val dw_insert = Mux(dw_match.valid, dw_match.idx, insertPtr)


// 	val hw_dw_clash = (io.hw_en && io.dw_en) && (io.hw_addr === io.dw_addr)
// 	val dual_write = (io.hw_en && io.dw_en) && (io.hw_addr =/= io.dw_addr)
// 	val single_write = (io.hw_en ^ io.dw_en) || hw_dw_clash

	// replacement policy: fifo
// 	val insertPtr = Reg(0.U(idxWidth.W))
// 	val nextInsertPtr = Mux(insertPtr === (entries-1).U, 0, insertPtr + 1.U)
// 	val secondNextInsertPtr = Mux(nextInsertPtr === (entries-1).U, 0, nextInsertPtr + 1.U)
// 	when(single_write) { insertPtr := nextInsertPtr }
// 	when(dual_write) { insertPtr := secondNextInsertPtr }

	// hw
// 	val hw_hit = addrMatch(io.hw_addr)
// 	val hw_insert = Mux(hw_hit._1, hw_hit._2, insertPtr)
// 	when(io.hw_en && !hw_dw_clash) {
// 		address(hw_insert) := io.hw_addr
// 		address_valid(hw_insert) := true.B
// 		data.write(hw_insert, io.hw_data, io.hw_mask)
// 	}

	// dw
// 	val dw_insert = Mux(dual_write, nextInsertPtr, insertPtr)
// 	when(io.dw_en) {
// 		address(dw_insert) := io.dw_addr
// 		address_valid(dw_insert) := true.B
// 		data.write(dw_insert, io.dw_data, io.dw_mask)
// 	}


// First Try:


// class TagStore(val tagWidth: Int, val entries: Int) extends Module {
// 	val idxWidth = log2Ceil(entries)
// 	val io = IO(new Bundle{
// 		val do_insert = Input(Bool())
// 		val insert_tag = Input(UInt(tagWidth.W))
// 		val match_tag1 = Input(UInt(tagWidth.w))
// 		val hit1 = Output(Bool())
// 		val idx1 = Output(UInt(idxWidth.W))
// 		val match_tag2 = Input(UInt(tagWidth.w))
// 		val hit2 = Output(Bool())
// 		val idx2 = Output(UInt(idxWidth.W))
// 	})

	// replacement policy: fifo
// 	val insertPtr = Reg(0.U(idxWidth.W))
// 	when(io.do_insert) {
// 		insertPtr := Mux(insertPtr === (entries-1).U, 0, insertPtr + 1.U)
// 	}

	// tag storage, does not need to be reset since we have the allValid + insertPtr mechanism
// 	val tags = Reg(Vec(entries, UInt(tagWidth.W)))
// 	val tag_valid = Reg(Vec(entries, false.B))
// 	def hit(tag: UInt) = {
// 		val vector = tags.zip(tag_valid).map(_ === io.insert_tag && _).toUInt
// 		val is_hit = vector =/= 0.U
// 		val idx = OHToUInt(vector)
// 		(is_hit, idx)
// 	}

	// read

// 	val match_hit_bits = tags.map(_ === io.match_tag).toUInt



// }

// a sparse version of AsyncReadMem
// class SparseAsyncReadMem(val addrWidth : Int) extends Module {
// 	val dataWidth = 32
// 	val entries = 256

// 	val io = IO(new d2h2i1(addrWidth))

// 	val address = Module(new TagStore(addrWidth, entries))
// 	val content = Reg(Vec(entries, UInt(width=dataWidth)))


// }


}