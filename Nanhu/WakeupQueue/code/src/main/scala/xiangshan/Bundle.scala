// XiangShan/src/main/scala/xiangshan/Bundle.scala

package xiangshan

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._

import utils._
import xiangshan.backend.decode.XDecode
import xiangshan.backend.fu.FuType
import xiangshan.backend.rob.RobPtr
import xiangshan.frontend._
import xiangshan.mem.{LqPtr, SqPtr}
import xiangshan.backend.Bundles.UopIdx

import xiangshan.frontend.{AllAheadFoldedHistoryOldestBits, AllFoldedHistories, CGHPtr, FtqPtr}
import xiangshan.frontend.{Ftq_Redirect_SRAMEntry, HasBPUParameter, PreDecodeInfo, RASPtr}

import utility._

import org.chipsalliance.cde.config.Parameters
import chisel3.util.BitPat.bitPatToUInt
import chisel3.util.experimental.decode.EspressoMinimizer


class CfiUpdateInfo(implicit p: Parameters) extends XSBundle with HasBPUParameter {
  // from backend
  val pc = UInt(VAddrBits.W)
  // frontend -> backend -> frontend
  val pd = new PreDecodeInfo
  val ssp = UInt(log2Up(RasSize).W)
  val sctr = UInt(RasCtrSize.W)
  val TOSW = new RASPtr
  val TOSR = new RASPtr
  val NOS = new RASPtr
  val topAddr = UInt(VAddrBits.W)
  // val hist = new ShiftingGlobalHistory
  val folded_hist = new AllFoldedHistories(foldedGHistInfos)
  val afhob = new AllAheadFoldedHistoryOldestBits(foldedGHistInfos)
  val lastBrNumOH = UInt((numBr+1).W)
  val ghr = UInt(UbtbGHRLength.W)
  val histPtr = new CGHPtr
  val specCnt = Vec(numBr, UInt(10.W))
  // need pipeline update
  val br_hit = Bool() // if in ftb entry
  val jr_hit = Bool() // if in ftb entry
  val sc_hit = Bool() // if used in ftb entry, invalid if !br_hit
  val predTaken = Bool()
  val target = UInt(VAddrBits.W)
  val taken = Bool()
  val isMisPred = Bool()
  val shift = UInt((log2Ceil(numBr)+1).W)
  val addIntoHist = Bool()
  // raise exceptions from backend
  val backendIGPF = Bool() // instruction guest page fault
  val backendIPF = Bool() // instruction page fault
  val backendIAF = Bool() // instruction access fault

  def fromFtqRedirectSram(entry: Ftq_Redirect_SRAMEntry) = {
    // this.hist := entry.ghist
    this.histPtr := entry.histPtr
    this.ssp := entry.ssp
    this.sctr := entry.sctr
    this.TOSW := entry.TOSW
    this.TOSR := entry.TOSR
    this.NOS := entry.NOS
    this.topAddr := entry.topAddr
    this
  }

  def hasBackendFault = backendIGPF || backendIPF || backendIAF
}

// Dequeue DecodeWidth insts from Ibuffer
class CtrlFlow(implicit p: Parameters) extends XSBundle {
  val instr = UInt(32.W)
  val pc = UInt(VAddrBits.W)
  val foldpc = UInt(MemPredPCWidth.W)
  val exceptionVec = ExceptionVec()
  val exceptionFromBackend = Bool()
  val trigger = TriggerAction()
  val pd = new PreDecodeInfo
  val pred_taken = Bool()
  val crossPageIPFFix = Bool()
  val storeSetHit = Bool() // inst has been allocated an store set
  val waitForRobIdx = new RobPtr // store set predicted previous store robIdx
  // Load wait is needed
  // load inst will not be executed until former store (predicted by mdp) addr calcuated
  val loadWaitBit = Bool()
  // If (loadWaitBit && loadWaitStrict), strict load wait is needed
  // load inst will not be executed until ALL former store addr calcuated
  val loadWaitStrict = Bool()
  val ssid = UInt(SSIDWidth.W)
  val ftqPtr = new FtqPtr
  val ftqOffset = UInt(log2Up(PredictWidth).W)
}

class FPUCtrlSignals(implicit p: Parameters) extends XSBundle {
  val isAddSub = Bool() // swap23
  val typeTagIn = UInt(1.W)
  val typeTagOut = UInt(1.W)
  val fromInt = Bool()
  val wflags = Bool()
  val fpWen = Bool()
  val fmaCmd = UInt(2.W)
  val div = Bool()
  val sqrt = Bool()
  val fcvt = Bool()
  val typ = UInt(2.W)
  val fmt = UInt(2.W)
  val ren3 = Bool() //TODO: remove SrcType.fp
  val rm = UInt(3.W)
}

// Decode DecodeWidth insts at Decode Stage
class CtrlSignals(implicit p: Parameters) extends XSBundle {
  // val debug_globalID = UInt(XLEN.W)
  val srcType = Vec(4, SrcType())
  val lsrc = Vec(4, UInt(LogicRegsWidth.W))
  val ldest = UInt(LogicRegsWidth.W)
  val fuType = FuType()
  val fuOpType = FuOpType()
  val rfWen = Bool()
  val fpWen = Bool()
  val vecWen = Bool()
  val isXSTrap = Bool()
  val noSpecExec = Bool() // wait forward
  val blockBackward = Bool() // block backward
  val flushPipe = Bool() // This inst will flush all the pipe when commit, like exception but can commit
  val uopSplitType = UopSplitType()
  val selImm = SelImm()
  val imm = UInt(32.W)
  val commitType = CommitType()
  val fpu = new FPUCtrlSignals
  val uopIdx = UopIdx()
  val isMove = Bool()
  val vm = Bool()
  val singleStep = Bool()
  // This inst will flush all the pipe when it is the oldest inst in ROB,
  // then replay from this inst itself
  val replayInst = Bool()
  val canRobCompress = Bool()

  private def allSignals = srcType.take(3) ++ Seq(fuType, fuOpType, rfWen, fpWen, vecWen,
    isXSTrap, noSpecExec, blockBackward, flushPipe, canRobCompress, uopSplitType, selImm)

  def decode(inst: UInt, table: Iterable[(BitPat, List[BitPat])]): CtrlSignals = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(inst, XDecode.decodeDefault, table, EspressoMinimizer)
    allSignals zip decoder foreach { case (s, d) => s := d }
    commitType := DontCare
    this
  }

  def decode(bit: List[BitPat]): CtrlSignals = {
    allSignals.zip(bit.map(bitPatToUInt(_))).foreach{ case (s, d) => s := d }
    this
  }

  def isWFI: Bool = fuType === FuType.csr.U && fuOpType === CSROpType.wfi
  def isSoftPrefetch: Bool = {
    fuType === FuType.alu.U && fuOpType === ALUOpType.or && selImm === SelImm.IMM_I && ldest === 0.U
  }
  def needWriteRf: Bool = rfWen || fpWen || vecWen
  def isHyperInst: Bool = {
    fuType === FuType.ldu.U && LSUOpType.isHlv(fuOpType) || fuType === FuType.stu.U && LSUOpType.isHsv(fuOpType)
  }
}

class CfCtrl(implicit p: Parameters) extends XSBundle {
  val cf = new CtrlFlow
  val ctrl = new CtrlSignals
}

// CfCtrl -> MicroOp at Rename Stage
class MicroOp(implicit p: Parameters) extends CfCtrl {
  val srcState = Vec(4, SrcState())
  val psrc = Vec(4, UInt(PhyRegIdxWidth.W))
  val pdest = UInt(PhyRegIdxWidth.W)
  val robIdx = new RobPtr
  val instrSize = UInt(log2Ceil(RenameWidth + 1).W)
  val lqIdx = new LqPtr
  val sqIdx = new SqPtr
  val eliminatedMove = Bool()
  val snapshot = Bool()
  val debugInfo = new PerfDebugInfo
  def needRfRPort(index: Int, isFp: Boolean, ignoreState: Boolean = true) : Bool = {
    val stateReady = srcState(index) === SrcState.rdy || ignoreState.B
    val readReg = if (isFp) {
      ctrl.srcType(index) === SrcType.fp
    } else {
      ctrl.srcType(index) === SrcType.reg && ctrl.lsrc(index) =/= 0.U
    }
    readReg && stateReady
  }
  def srcIsReady: Vec[Bool] = {
    VecInit(ctrl.srcType.zip(srcState).map{ case (t, s) => SrcType.isPcOrImm(t) || s === SrcState.rdy })
  }
  def clearExceptions(
    exceptionBits: Seq[Int] = Seq(),
    flushPipe: Boolean = false,
    replayInst: Boolean = false
  ): MicroOp = {
    cf.exceptionVec.zipWithIndex.filterNot(x => exceptionBits.contains(x._2)).foreach(_._1 := false.B)
    if (!flushPipe) { ctrl.flushPipe := false.B }
    if (!replayInst) { ctrl.replayInst := false.B }
    this
  }
}

class Redirect(implicit p: Parameters) extends XSBundle {
  val isRVC = Bool()
  val robIdx = new RobPtr
  val ftqIdx = new FtqPtr
  val ftqOffset = UInt(log2Up(PredictWidth).W)
  val level = RedirectLevel()
  val interrupt = Bool()
  val cfiUpdate = new CfiUpdateInfo
  val fullTarget = UInt(XLEN.W) // only used for tval storage in backend

  val stFtqIdx = new FtqPtr // for load violation predict
  val stFtqOffset = UInt(log2Up(PredictWidth).W)

  val debug_runahead_checkpoint_id = UInt(64.W)
  val debugIsCtrl = Bool()
  val debugIsMemVio = Bool()

  def flushItself() = RedirectLevel.flushItself(level)
}

object Redirect extends HasCircularQueuePtrHelper {

  def selectOldestRedirect(xs: Seq[Valid[Redirect]]): Vec[Bool] = {
    val compareVec = (0 until xs.length).map(i => (0 until i).map(j => isAfter(xs(j).bits.robIdx, xs(i).bits.robIdx)))
    val resultOnehot = VecInit((0 until xs.length).map(i => Cat((0 until xs.length).map(j =>
      (if (j < i) !xs(j).valid || compareVec(i)(j)
      else if (j == i) xs(i).valid
      else !xs(j).valid || !compareVec(j)(i))
    )).andR))
    resultOnehot
  }
}

object TriggerAction extends NamedUInt(4) {
  // Put breakpoint Exception gererated by trigger in ExceptionVec[3].
  def BreakpointExp = 0.U(width.W)  // raise breakpoint exception
  def DebugMode     = 1.U(width.W)  // enter debug mode
  def TraceOn       = 2.U(width.W)
  def TraceOff      = 3.U(width.W)
  def TraceNotify   = 4.U(width.W)
  def None          = 15.U(width.W) // use triggerAction = 15.U to express that action is None;

  def isExp(action: UInt)   = action === BreakpointExp
  def isDmode(action: UInt) = action === DebugMode
  def isNone(action: UInt)  = action === None
}