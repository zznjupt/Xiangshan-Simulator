// BreadcrumbsXiangShan/src/main/scala/xiangshan/frontend/newRAS.scala

package xiangshan.frontend

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._
import utils._
import utility._
import xiangshan._
import xiangshan.frontend._

class RASPtr(implicit p: Parameters) extends CircularQueuePtr[RASPtr](
  p => p(XSCoreParamsKey).RasSpecSize
){
}

object RASPtr {
  def apply(f: Bool, v: UInt)(implicit p: Parameters): RASPtr = {
    val ptr = Wire(new RASPtr)
    ptr.flag := f
    ptr.value := v
    ptr
  }
  def inverse(ptr: RASPtr)(implicit p: Parameters): RASPtr = {
    apply(!ptr.flag, ptr.value)
  }
}