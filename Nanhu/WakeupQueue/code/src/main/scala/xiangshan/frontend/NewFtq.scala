// XiangShan/src/main/scala/xiangshan/frontend/NewFtq.scala

package xiangshan.frontend

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._
import utils._
import utility._
import xiangshan._

class FtqPtr(entries: Int) extends CircularQueuePtr[FtqPtr](
  entries
){
  def this()(implicit p: Parameters) = this(p(XSCoreParamsKey).FtqSize)
}

object FtqPtr {
  def apply(f: Bool, v: UInt)(implicit p: Parameters): FtqPtr = {
    val ptr = Wire(new FtqPtr)
    ptr.flag := f
    ptr.value := v
    ptr
  }
  def inverse(ptr: FtqPtr)(implicit p: Parameters): FtqPtr = {
    apply(!ptr.flag, ptr.value)
  }
}

class Ftq_Redirect_SRAMEntry(implicit p: Parameters) extends SpeculativeInfo {
  val sc_disagree = if (!env.FPGAPlatform) Some(Vec(numBr, Bool())) else None
}

class SpeculativeInfo(implicit p: Parameters) extends XSBundle
  with HasBPUConst with BPUUtils {
  val histPtr = new CGHPtr
  val ssp = UInt(log2Up(RasSize).W)
  val sctr = UInt(RasCtrSize.W)
  val TOSW = new RASPtr
  val TOSR = new RASPtr
  val NOS = new RASPtr
  val topAddr = UInt(VAddrBits.W)
}