// XiangShan/src/main/scala/xiangshan/backend/issue/WakeupQueue.scala

package xiangshan.backend.issue

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._
import xiangshan._
import utils._
import utility._

class WakeupQueue(number: Int)(implicit p: Parameters) extends XSModule {
  val io = IO(new Bundle {
/* 
    type ValidIO[+T <: Data] = Valid[T]
    Synonyms, moved from main package object - maintain scope.
    https://github.com/chipsalliance/chisel/blob/main/src/main/scala/chisel3/util/util.scala
*/
    val in  = Flipped(ValidIO(new MicroOp))
    val out = ValidIO(new MicroOp)
    val redirect = Flipped(ValidIO(new Redirect))
  })
  if (number < 0) {
    io.out.valid := false.B
    io.out.bits := DontCare
  } else if(number == 0) {
    io.in <> io.out
    io.out.valid := io.in.valid
    // NOTE: no delay bypass don't care redirect
  } else {
    val queue = Seq.fill(number)(RegInit(0.U.asTypeOf(new Bundle{
      val valid = Bool()
      val bits = new MicroOp
    })))
    queue(0).valid := io.in.valid && !io.in.bits.robIdx.needFlush(io.redirect)
    queue(0).bits  := io.in.bits
    (0 until (number-1)).map{i =>
      queue(i+1) := queue(i)
      queue(i+1).valid := queue(i).valid && !queue(i).bits.robIdx.needFlush(io.redirect)
    }
    io.out.valid := queue(number-1).valid
    io.out.bits := queue(number-1).bits

    // debug

    // for (i <- 0 until number) {
    //   XSDebug(queue(i).valid, p"BPQue(${i.U}): pc:${Hexadecimal(queue(i).bits.cf.pc)} robIdx:${queue(i).bits.robIdx}" +
    //     p" pdest:${queue(i).bits.pdest} rfWen:${queue(i).bits.ctrl.rfWen} fpWen:${queue(i).bits.ctrl.fpWen} vecWen:${queue(i).bits.ctrl.vecWen}\n")
    // }
  }
}

