// XiangShan/src/main/scala/xiangshan/XSCore.scala

package xiangshan

import org.chipsalliance.cde.config
import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._

abstract class XSModule(implicit val p: Parameters) extends Module
  with HasXSParameter
  with HasFPUParameters

abstract class XSBundle(implicit val p: Parameters) extends Bundle
  with HasXSParameter