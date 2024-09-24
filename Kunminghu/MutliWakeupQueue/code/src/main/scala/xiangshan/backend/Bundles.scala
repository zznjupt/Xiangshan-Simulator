// XiangShan/src/main/scala/xiangshan/backend/Bundles.scala

package xiangshan.backend

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.BitPat.bitPatToUInt
import chisel3.util._
import utils.BundleUtils.makeValid
import utils.OptionWrapper
import xiangshan._
import xiangshan.backend.datapath.DataConfig._
import xiangshan.backend.datapath.DataSource
import xiangshan.backend.datapath.WbConfig.PregWB
import xiangshan.backend.decode.{ImmUnion, XDecode}
import xiangshan.backend.exu.ExeUnitParams
import xiangshan.backend.fu.FuType
import xiangshan.backend.fu.fpu.Bundles.Frm
import xiangshan.backend.fu.vector.Bundles._
import xiangshan.backend.issue.{IssueBlockParams, IssueQueueDeqRespBundle, SchedulerType}
import xiangshan.backend.issue.EntryBundles._
import xiangshan.backend.regfile.{RfReadPortWithConfig, RfWritePortWithConfig}
import xiangshan.backend.rob.RobPtr
import xiangshan.frontend._
import xiangshan.mem.{LqPtr, SqPtr}
import yunsuan.vector.VIFuParam
import xiangshan.backend.trace._

object Bundles {
  /**
   * Connect Same Name Port like bundleSource := bundleSinkBudle.
   *
   * There is no limit to the number of ports on both sides.
   *
   * Don't forget to connect the remaining ports!
   */
  def connectSamePort (bundleSource: Bundle, bundleSink: Bundle):Unit = {
    bundleSource.elements.foreach { case (name, data) =>
      if (bundleSink.elements.contains(name))
        data := bundleSink.elements(name)
    }
  }

//   // frontend -> backend
//   class StaticInst(implicit p: Parameters) extends XSBundle {
//     val instr           = UInt(32.W)
//     val pc              = UInt(VAddrBits.W)
//     val foldpc          = UInt(MemPredPCWidth.W)
//     val exceptionVec    = ExceptionVec()
//     val isFetchMalAddr  = Bool()
//     val trigger         = TriggerAction()
//     val preDecodeInfo   = new PreDecodeInfo
//     val pred_taken      = Bool()
//     val crossPageIPFFix = Bool()
//     val ftqPtr          = new FtqPtr
//     val ftqOffset       = UInt(log2Up(PredictWidth).W)

//     def connectCtrlFlow(source: CtrlFlow): Unit = {
//       this.instr            := source.instr
//       this.pc               := source.pc
//       this.foldpc           := source.foldpc
//       this.exceptionVec     := source.exceptionVec
//       this.isFetchMalAddr   := source.exceptionFromBackend
//       this.trigger          := source.trigger
//       this.preDecodeInfo    := source.pd
//       this.pred_taken       := source.pred_taken
//       this.crossPageIPFFix  := source.crossPageIPFFix
//       this.ftqPtr           := source.ftqPtr
//       this.ftqOffset        := source.ftqOffset
//     }
//   }

//   // StaticInst --[Decode]--> DecodedInst
//   class DecodedInst(implicit p: Parameters) extends XSBundle {
//     def numSrc = backendParams.numSrc
//     // passed from StaticInst
//     val instr           = UInt(32.W)
//     val pc              = UInt(VAddrBits.W)
//     val foldpc          = UInt(MemPredPCWidth.W)
//     val exceptionVec    = ExceptionVec()
//     val isFetchMalAddr  = Bool()
//     val trigger         = TriggerAction()
//     val preDecodeInfo   = new PreDecodeInfo
//     val pred_taken      = Bool()
//     val crossPageIPFFix = Bool()
//     val ftqPtr          = new FtqPtr
//     val ftqOffset       = UInt(log2Up(PredictWidth).W)
//     // decoded
//     val srcType         = Vec(numSrc, SrcType())
//     val lsrc            = Vec(numSrc, UInt(LogicRegsWidth.W))
//     val ldest           = UInt(LogicRegsWidth.W)
//     val fuType          = FuType()
//     val fuOpType        = FuOpType()
//     val rfWen           = Bool()
//     val fpWen           = Bool()
//     val vecWen          = Bool()
//     val v0Wen           = Bool()
//     val vlWen           = Bool()
//     val isXSTrap        = Bool()
//     val waitForward     = Bool() // no speculate execution
//     val blockBackward   = Bool()
//     val flushPipe       = Bool() // This inst will flush all the pipe when commit, like exception but can commit
//     val canRobCompress  = Bool()
//     val selImm          = SelImm()
//     val imm             = UInt(ImmUnion.maxLen.W)
//     val fpu             = new FPUCtrlSignals
//     val vpu             = new VPUCtrlSignals
//     val vlsInstr        = Bool()
//     val wfflags         = Bool()
//     val isMove          = Bool()
//     val uopIdx          = UopIdx()
//     val uopSplitType    = UopSplitType()
//     val isVset          = Bool()
//     val firstUop        = Bool()
//     val lastUop         = Bool()
//     val numUops         = UInt(log2Up(MaxUopSize).W) // rob need this
//     val numWB           = UInt(log2Up(MaxUopSize).W) // rob need this
//     val commitType      = CommitType() // Todo: remove it
//     val needFrm         = new NeedFrmBundle

//     val debug_fuType    = OptionWrapper(backendParams.debugEn, FuType())

//     private def allSignals = srcType.take(3) ++ Seq(fuType, fuOpType, rfWen, fpWen, vecWen,
//       isXSTrap, waitForward, blockBackward, flushPipe, canRobCompress, uopSplitType, selImm)

//     def decode(inst: UInt, table: Iterable[(BitPat, List[BitPat])]): DecodedInst = {
//       val decoder: Seq[UInt] = ListLookup(
//         inst, XDecode.decodeDefault.map(bitPatToUInt),
//         table.map{ case (pat, pats) => (pat, pats.map(bitPatToUInt)) }.toArray
//       )
//       allSignals zip decoder foreach { case (s, d) => s := d }
//       debug_fuType.foreach(_ := fuType)
//       this
//     }

//     def isSoftPrefetch: Bool = {
//       fuType === FuType.alu.U && fuOpType === ALUOpType.or && selImm === SelImm.IMM_I && ldest === 0.U
//     }

//     def connectStaticInst(source: StaticInst): Unit = {
//       for ((name, data) <- this.elements) {
//         if (source.elements.contains(name)) {
//           data := source.elements(name)
//         }
//       }
//     }
//   }

//   class TrapInstInfo(implicit p: Parameters) extends XSBundle {
//     val instr = UInt(32.W)
//     val ftqPtr = new FtqPtr
//     val ftqOffset = UInt(log2Up(PredictWidth).W)

//     def needFlush(ftqPtr: FtqPtr, ftqOffset: UInt): Bool ={
//       val sameFlush = this.ftqPtr === ftqPtr && this.ftqOffset > ftqOffset
//       sameFlush || isAfter(this.ftqPtr, ftqPtr)
//     }

//     def fromDecodedInst(decodedInst: DecodedInst): this.type = {
//       this.instr     := decodedInst.instr
//       this.ftqPtr    := decodedInst.ftqPtr
//       this.ftqOffset := decodedInst.ftqOffset
//       this
//     }
//   }

//   // DecodedInst --[Rename]--> DynInst
//   class DynInst(implicit p: Parameters) extends XSBundle {
//     def numSrc          = backendParams.numSrc
//     // passed from StaticInst
//     val instr           = UInt(32.W)
//     val pc              = UInt(VAddrBits.W)
//     val foldpc          = UInt(MemPredPCWidth.W)
//     val exceptionVec    = ExceptionVec()
//     val isFetchMalAddr  = Bool()
//     val hasException    = Bool()
//     val trigger         = TriggerAction()
//     val preDecodeInfo   = new PreDecodeInfo
//     val pred_taken      = Bool()
//     val crossPageIPFFix = Bool()
//     val ftqPtr          = new FtqPtr
//     val ftqOffset       = UInt(log2Up(PredictWidth).W)
//     // passed from DecodedInst
//     val srcType         = Vec(numSrc, SrcType())
//     val ldest           = UInt(LogicRegsWidth.W)
//     val fuType          = FuType()
//     val fuOpType        = FuOpType()
//     val rfWen           = Bool()
//     val fpWen           = Bool()
//     val vecWen          = Bool()
//     val v0Wen           = Bool()
//     val vlWen           = Bool()
//     val isXSTrap        = Bool()
//     val waitForward     = Bool() // no speculate execution
//     val blockBackward   = Bool()
//     val flushPipe       = Bool() // This inst will flush all the pipe when commit, like exception but can commit
//     val canRobCompress  = Bool()
//     val selImm          = SelImm()
//     val imm             = UInt(32.W)
//     val fpu             = new FPUCtrlSignals
//     val vpu             = new VPUCtrlSignals
//     val vlsInstr        = Bool()
//     val wfflags         = Bool()
//     val isMove          = Bool()
//     val uopIdx          = UopIdx()
//     val isVset          = Bool()
//     val firstUop        = Bool()
//     val lastUop         = Bool()
//     val numUops         = UInt(log2Up(MaxUopSize).W) // rob need this
//     val numWB           = UInt(log2Up(MaxUopSize).W) // rob need this
//     val commitType      = CommitType()
//     // rename
//     val srcState        = Vec(numSrc, SrcState())
//     val srcLoadDependency  = Vec(numSrc, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
//     val psrc            = Vec(numSrc, UInt(PhyRegIdxWidth.W))
//     val pdest           = UInt(PhyRegIdxWidth.W)
//     // reg cache
//     val useRegCache     = Vec(backendParams.numIntRegSrc, Bool())
//     val regCacheIdx     = Vec(backendParams.numIntRegSrc, UInt(RegCacheIdxWidth.W))
//     val robIdx          = new RobPtr
//     val instrSize       = UInt(log2Ceil(RenameWidth + 1).W)
//     val dirtyFs         = Bool()
//     val dirtyVs         = Bool()
//     val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))

//     val eliminatedMove  = Bool()
//     // Take snapshot at this CFI inst
//     val snapshot        = Bool()
//     val debugInfo       = new PerfDebugInfo
//     val storeSetHit     = Bool() // inst has been allocated an store set
//     val waitForRobIdx   = new RobPtr // store set predicted previous store robIdx
//     // Load wait is needed
//     // load inst will not be executed until former store (predicted by mdp) addr calcuated
//     val loadWaitBit     = Bool()
//     // If (loadWaitBit && loadWaitStrict), strict load wait is needed
//     // load inst will not be executed until ALL former store addr calcuated
//     val loadWaitStrict  = Bool()
//     val ssid            = UInt(SSIDWidth.W)
//     // Todo
//     val lqIdx = new LqPtr
//     val sqIdx = new SqPtr
//     // debug module
//     val singleStep      = Bool()
//     // schedule
//     val replayInst      = Bool()

//     val debug_fuType    = OptionWrapper(backendParams.debugEn, FuType())

//     val numLsElem       = NumLsElem()

//     def getDebugFuType: UInt = debug_fuType.getOrElse(fuType)

//     def isLUI: Bool = this.fuType === FuType.alu.U && (this.selImm === SelImm.IMM_U || this.selImm === SelImm.IMM_LUI32)
//     def isLUI32: Bool = this.selImm === SelImm.IMM_LUI32
//     def isWFI: Bool = this.fuType === FuType.csr.U && fuOpType === CSROpType.wfi

//     def isSvinvalBegin(flush: Bool) = FuType.isFence(fuType) && fuOpType === FenceOpType.nofence && !flush
//     def isSvinval(flush: Bool) = FuType.isFence(fuType) &&
//       Cat(Seq(FenceOpType.sfence, FenceOpType.hfence_v, FenceOpType.hfence_g).map(_ === fuOpType)).orR && !flush
//     def isSvinvalEnd(flush: Bool) = FuType.isFence(fuType) && fuOpType === FenceOpType.nofence && flush
//     def isNotSvinval = !FuType.isFence(fuType)

//     def isHls: Bool = {
//       fuType === FuType.ldu.U && LSUOpType.isHlv(fuOpType) || fuType === FuType.stu.U && LSUOpType.isHsv(fuOpType)
//     }

//     def srcIsReady: Vec[Bool] = {
//       VecInit(this.srcType.zip(this.srcState).map {
//         case (t, s) => SrcType.isNotReg(t) || SrcState.isReady(s)
//       })
//     }

//     def clearExceptions(
//       exceptionBits: Seq[Int] = Seq(),
//       flushPipe    : Boolean = false,
//       replayInst   : Boolean = false
//     ): DynInst = {
//       this.exceptionVec.zipWithIndex.filterNot(x => exceptionBits.contains(x._2)).foreach(_._1 := false.B)
//       if (!flushPipe) { this.flushPipe := false.B }
//       if (!replayInst) { this.replayInst := false.B }
//       this
//     }

//     def needWriteRf: Bool = rfWen || fpWen || vecWen || v0Wen || vlWen
//   }

//   trait BundleSource {
//     var wakeupSource = "undefined"
//     var idx = 0
//   }

  /**
    *
    * @param pregIdxWidth index width of preg
    * @param exuIndices exu indices of wakeup bundle
    */
  sealed abstract class IssueQueueWakeUpBaseBundle(pregIdxWidth: Int, val exuIndices: Seq[Int])(implicit p: Parameters) extends XSBundle {
    val rfWen = Bool()
    val fpWen = Bool()
    val vecWen = Bool()
    val v0Wen = Bool()
    val vlWen = Bool()
    val pdest = UInt(pregIdxWidth.W)

    /**
      * @param successor Seq[(psrc, srcType)]
      * @return Seq[if wakeup psrc]
      */
    def wakeUp(successor: Seq[(UInt, UInt)], valid: Bool): Seq[Bool] = {
      successor.map { case (thatPsrc, srcType) =>
        val pdestMatch = pdest === thatPsrc
        pdestMatch && (
          SrcType.isFp(srcType) && this.fpWen ||
            SrcType.isXp(srcType) && this.rfWen ||
            SrcType.isVp(srcType) && this.vecWen
          ) && valid
      }
    }
    def wakeUpV0(successor: (UInt, UInt), valid: Bool): Bool = {
      val (thatPsrc, srcType) = successor
      val pdestMatch = pdest === thatPsrc
      pdestMatch && (
        SrcType.isV0(srcType) && this.v0Wen
      ) && valid
    }
    def wakeUpVl(successor: (UInt, UInt), valid: Bool): Bool = {
      val (thatPsrc, srcType) = successor
      val pdestMatch = pdest === thatPsrc
      pdestMatch && (
        SrcType.isVp(srcType) && this.vlWen
      ) && valid
    }
    def wakeUpFromIQ(successor: Seq[(UInt, UInt)]): Seq[Bool] = {
      successor.map { case (thatPsrc, srcType) =>
        val pdestMatch = pdest === thatPsrc
        pdestMatch && (
          SrcType.isFp(srcType) && this.fpWen ||
            SrcType.isXp(srcType) && this.rfWen ||
            SrcType.isVp(srcType) && this.vecWen
          )
      }
    }
    def wakeUpV0FromIQ(successor: (UInt, UInt)): Bool = {
      val (thatPsrc, srcType) = successor
      val pdestMatch = pdest === thatPsrc
      pdestMatch && (
        SrcType.isV0(srcType) && this.v0Wen
      )
    }
    def wakeUpVlFromIQ(successor: (UInt, UInt)): Bool = {
      val (thatPsrc, srcType) = successor
      val pdestMatch = pdest === thatPsrc
      pdestMatch && (
        SrcType.isVp(srcType) && this.vlWen
      )
    }

    def hasOnlyOneSource: Boolean = exuIndices.size == 1

    def hasMultiSources: Boolean = exuIndices.size > 1

    def isWBWakeUp = this.isInstanceOf[IssueQueueWBWakeUpBundle]

    def isIQWakeUp = this.isInstanceOf[IssueQueueIQWakeUpBundle]

    def exuIdx: Int = {
      require(hasOnlyOneSource)
      this.exuIndices.head
    }
  }

  class IssueQueueWBWakeUpBundle(exuIndices: Seq[Int], backendParams: BackendParams)(implicit p: Parameters) extends IssueQueueWakeUpBaseBundle(backendParams.pregIdxWidth, exuIndices) {

  }

  class IssueQueueIQWakeUpBundle(
    exuIdx: Int,
    backendParams: BackendParams,
    copyWakeupOut: Boolean = false,
    copyNum: Int = 0
  )(implicit p: Parameters) extends IssueQueueWakeUpBaseBundle(backendParams.pregIdxWidth, Seq(exuIdx)) {
    val loadDependency = Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))
    val is0Lat = Bool()
    val params = backendParams.allExuParams.filter(_.exuIdx == exuIdx).head
    val rcDest = OptionWrapper(params.needWriteRegCache, UInt(RegCacheIdxWidth.W))
    val pdestCopy  = OptionWrapper(copyWakeupOut, Vec(copyNum, UInt(params.wbPregIdxWidth.W)))
    val rfWenCopy  = OptionWrapper(copyWakeupOut && params.needIntWen, Vec(copyNum, Bool()))
    val fpWenCopy  = OptionWrapper(copyWakeupOut && params.needFpWen, Vec(copyNum, Bool()))
    val vecWenCopy = OptionWrapper(copyWakeupOut && params.needVecWen, Vec(copyNum, Bool()))
    val v0WenCopy = OptionWrapper(copyWakeupOut && params.needV0Wen, Vec(copyNum, Bool()))
    val vlWenCopy = OptionWrapper(copyWakeupOut && params.needVlWen, Vec(copyNum, Bool()))
    val loadDependencyCopy = OptionWrapper(copyWakeupOut && params.isIQWakeUpSink, Vec(copyNum, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))))

    def fromExuInput(exuInput: ExuInput): Unit = {
      this.rfWen := exuInput.rfWen.getOrElse(false.B)
      this.fpWen := exuInput.fpWen.getOrElse(false.B)
      this.vecWen := exuInput.vecWen.getOrElse(false.B)
      this.v0Wen := exuInput.v0Wen.getOrElse(false.B)
      this.vlWen := exuInput.vlWen.getOrElse(false.B)
      this.pdest := exuInput.pdest
    }
  }

  class VPUCtrlSignals(implicit p: Parameters) extends XSBundle {
    // vtype
    val vill      = Bool()
    val vma       = Bool()    // 1: agnostic, 0: undisturbed
    val vta       = Bool()    // 1: agnostic, 0: undisturbed
    val vsew      = VSew()
    val vlmul     = VLmul()   // 1/8~8      --> -3~3

    // spec vtype
    val specVill  = Bool()
    val specVma   = Bool()    // 1: agnostic, 0: undisturbed
    val specVta   = Bool()    // 1: agnostic, 0: undisturbed
    val specVsew  = VSew()
    val specVlmul = VLmul()   // 1/8~8      --> -3~3

    val vm        = Bool()    // 0: need v0.t
    val vstart    = Vl()

    // float rounding mode
    val frm       = Frm()
    // scalar float instr and vector float reduction
    val fpu       = Fpu()
    // vector fix int rounding mode
    val vxrm      = Vxrm()
    // vector uop index, exclude other non-vector uop
    val vuopIdx   = UopIdx()
    val lastUop   = Bool()
    // maybe used if data dependancy
    val vmask     = UInt(V0Data().dataWidth.W)
    val vl        = Vl()

    // vector load/store
    val nf        = Nf()
    val veew      = VEew()

    val isReverse = Bool() // vrsub, vrdiv
    val isExt     = Bool()
    val isNarrow  = Bool()
    val isDstMask = Bool() // vvm, vvvm, mmm
    val isOpMask  = Bool() // vmand, vmnand
    val isMove    = Bool() // vmv.s.x, vmv.v.v, vmv.v.x, vmv.v.i

    val isDependOldvd = Bool() // some instruction's computation depends on oldvd
    val isWritePartVd = Bool() // some instruction's computation writes part of vd, such as vredsum

    def vtype: VType = {
      val res = Wire(VType())
      res.illegal := this.vill
      res.vma     := this.vma
      res.vta     := this.vta
      res.vsew    := this.vsew
      res.vlmul   := this.vlmul
      res
    }

    def specVType: VType = {
      val res = Wire(VType())
      res.illegal := this.specVill
      res.vma     := this.specVma
      res.vta     := this.specVta
      res.vsew    := this.specVsew
      res.vlmul   := this.specVlmul
      res
    }

    def vconfig: VConfig = {
      val res = Wire(VConfig())
      res.vtype := this.vtype
      res.vl    := this.vl
      res
    }

    def connectVType(source: VType): Unit = {
      this.vill  := source.illegal
      this.vma   := source.vma
      this.vta   := source.vta
      this.vsew  := source.vsew
      this.vlmul := source.vlmul
    }
  }

  class NeedFrmBundle(implicit p: Parameters) extends XSBundle {
    val scalaNeedFrm = Bool()
    val vectorNeedFrm = Bool()
  }

  // DynInst --[IssueQueue]--> DataPath
  class IssueQueueIssueBundle(
    iqParams: IssueBlockParams,
    val exuParams: ExeUnitParams,
  )(implicit
    p: Parameters
  ) extends XSBundle {
    private val rfReadDataCfgSet: Seq[Set[DataConfig]] = exuParams.getRfReadDataCfgSet

    val rf: MixedVec[MixedVec[RfReadPortWithConfig]] = Flipped(MixedVec(
      rfReadDataCfgSet.map((set: Set[DataConfig]) =>
        MixedVec(set.map((x: DataConfig) => new RfReadPortWithConfig(x, exuParams.rdPregIdxWidth)).toSeq)
      )
    ))

    val srcType = Vec(exuParams.numRegSrc, SrcType()) // used to select imm or reg data
    val rcIdx = OptionWrapper(exuParams.needReadRegCache, Vec(exuParams.numRegSrc, UInt(RegCacheIdxWidth.W))) // used to select regcache data
    val immType = SelImm()                         // used to select imm extractor
    val common = new ExuInput(exuParams)
    val addrOH = UInt(iqParams.numEntries.W)

    def exuIdx = exuParams.exuIdx
    def getSource: SchedulerType = exuParams.getWBSource

    def getRfReadValidBundle(issueValid: Bool): Seq[ValidIO[RfReadPortWithConfig]] = {
      rf.zip(srcType).map {
        case (rfRd: MixedVec[RfReadPortWithConfig], t: UInt) =>
          makeValid(issueValid, rfRd.head)
      }.toSeq
    }
  }

  class OGRespBundle(implicit p:Parameters, params: IssueBlockParams) extends XSBundle {
    val issueQueueParams = this.params
    val og0resp = Valid(new EntryDeqRespBundle)
    val og1resp = Valid(new EntryDeqRespBundle)
  }

  class WbFuBusyTableWriteBundle(val params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
    private val intCertainLat = params.intLatencyCertain
    private val fpCertainLat = params.fpLatencyCertain
    private val vfCertainLat = params.vfLatencyCertain
    private val v0CertainLat = params.v0LatencyCertain
    private val vlCertainLat = params.vlLatencyCertain
    private val intLat = params.intLatencyValMax
    private val fpLat = params.fpLatencyValMax
    private val vfLat = params.vfLatencyValMax
    private val v0Lat = params.v0LatencyValMax
    private val vlLat = params.vlLatencyValMax

    val intWbBusyTable = OptionWrapper(intCertainLat, UInt((intLat + 1).W))
    val fpWbBusyTable = OptionWrapper(fpCertainLat, UInt((fpLat + 1).W))
    val vfWbBusyTable = OptionWrapper(vfCertainLat, UInt((vfLat + 1).W))
    val v0WbBusyTable = OptionWrapper(v0CertainLat, UInt((v0Lat + 1).W))
    val vlWbBusyTable = OptionWrapper(vlCertainLat, UInt((vlLat + 1).W))
    val intDeqRespSet = OptionWrapper(intCertainLat, UInt((intLat + 1).W))
    val fpDeqRespSet = OptionWrapper(fpCertainLat, UInt((fpLat + 1).W))
    val vfDeqRespSet = OptionWrapper(vfCertainLat, UInt((vfLat + 1).W))
    val v0DeqRespSet = OptionWrapper(v0CertainLat, UInt((v0Lat + 1).W))
    val vlDeqRespSet = OptionWrapper(vlCertainLat, UInt((vlLat + 1).W))
  }

//   class WbFuBusyTableReadBundle(val params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
//     private val intCertainLat = params.intLatencyCertain
//     private val fpCertainLat = params.fpLatencyCertain
//     private val vfCertainLat = params.vfLatencyCertain
//     private val v0CertainLat = params.v0LatencyCertain
//     private val vlCertainLat = params.vlLatencyCertain
//     private val intLat = params.intLatencyValMax
//     private val fpLat = params.fpLatencyValMax
//     private val vfLat = params.vfLatencyValMax
//     private val v0Lat = params.v0LatencyValMax
//     private val vlLat = params.vlLatencyValMax

//     val intWbBusyTable = OptionWrapper(intCertainLat, UInt((intLat + 1).W))
//     val fpWbBusyTable = OptionWrapper(fpCertainLat, UInt((fpLat + 1).W))
//     val vfWbBusyTable = OptionWrapper(vfCertainLat, UInt((vfLat + 1).W))
//     val v0WbBusyTable = OptionWrapper(v0CertainLat, UInt((v0Lat + 1).W))
//     val vlWbBusyTable = OptionWrapper(vlCertainLat, UInt((vlLat + 1).W))
//   }

//   class WbConflictBundle(val params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
//     private val intCertainLat = params.intLatencyCertain
//     private val fpCertainLat = params.fpLatencyCertain
//     private val vfCertainLat = params.vfLatencyCertain
//     private val v0CertainLat = params.v0LatencyCertain
//     private val vlCertainLat = params.vlLatencyCertain

//     val intConflict = OptionWrapper(intCertainLat, Bool())
//     val fpConflict = OptionWrapper(fpCertainLat, Bool())
//     val vfConflict = OptionWrapper(vfCertainLat, Bool())
//     val v0Conflict = OptionWrapper(v0CertainLat, Bool())
//     val vlConflict = OptionWrapper(vlCertainLat, Bool())
//   }

//   class ImmInfo extends Bundle {
//     val imm = UInt(32.W)
//     val immType = SelImm()
//   }

//   // DataPath --[ExuInput]--> Exu
  class ExuInput(val params: ExeUnitParams, copyWakeupOut:Boolean = false, copyNum:Int = 0)(implicit p: Parameters) extends XSBundle {
    val fuType        = FuType()
    val fuOpType      = FuOpType()
    val src           = Vec(params.numRegSrc, UInt(params.srcDataBitsMax.W))
    val imm           = UInt(32.W)
    val robIdx        = new RobPtr
    val iqIdx         = UInt(log2Up(MemIQSizeMax).W)// Only used by store yet
    val isFirstIssue  = Bool()                      // Only used by store yet
    val pdestCopy  = OptionWrapper(copyWakeupOut, Vec(copyNum, UInt(params.wbPregIdxWidth.W)))
    val rfWenCopy  = OptionWrapper(copyWakeupOut && params.needIntWen, Vec(copyNum, Bool()))
    val fpWenCopy  = OptionWrapper(copyWakeupOut && params.needFpWen, Vec(copyNum, Bool()))
    val vecWenCopy = OptionWrapper(copyWakeupOut && params.needVecWen, Vec(copyNum, Bool()))
    val v0WenCopy  = OptionWrapper(copyWakeupOut && params.needV0Wen, Vec(copyNum, Bool()))
    val vlWenCopy  = OptionWrapper(copyWakeupOut && params.needVlWen, Vec(copyNum, Bool()))
    val loadDependencyCopy = OptionWrapper(copyWakeupOut && params.isIQWakeUpSink, Vec(copyNum, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))))
    val pdest         = UInt(params.wbPregIdxWidth.W)
    val rfWen         = if (params.needIntWen)    Some(Bool())                        else None
    val fpWen         = if (params.needFpWen)     Some(Bool())                        else None
    val vecWen        = if (params.needVecWen)    Some(Bool())                        else None
    val v0Wen         = if (params.needV0Wen)     Some(Bool())                        else None
    val vlWen         = if (params.needVlWen)     Some(Bool())                        else None
    val fpu           = if (params.writeFflags)   Some(new FPUCtrlSignals)            else None
    val vpu           = if (params.needVPUCtrl)   Some(new VPUCtrlSignals)            else None
    val flushPipe     = if (params.flushPipe)     Some(Bool())                        else None
    val pc            = if (params.needPc)        Some(UInt(VAddrData().dataWidth.W)) else None
    val preDecode     = if (params.hasPredecode)  Some(new PreDecodeInfo)             else None
    val ftqIdx        = if (params.needPc || params.replayInst || params.hasStoreAddrFu || params.hasCSR)
                                                  Some(new FtqPtr)                    else None
    val ftqOffset     = if (params.needPc || params.replayInst || params.hasStoreAddrFu || params.hasCSR)
                                                  Some(UInt(log2Up(PredictWidth).W))  else None
    val predictInfo   = if (params.needPdInfo)  Some(new Bundle {
      val target = UInt(VAddrData().dataWidth.W)
      val taken = Bool()
    }) else None
    val loadWaitBit    = OptionWrapper(params.hasLoadExu, Bool())
    val waitForRobIdx  = OptionWrapper(params.hasLoadExu, new RobPtr) // store set predicted previous store robIdx
    val storeSetHit    = OptionWrapper(params.hasLoadExu, Bool()) // inst has been allocated an store set
    val loadWaitStrict = OptionWrapper(params.hasLoadExu, Bool()) // load inst will not be executed until ALL former store addr calcuated
    val ssid           = OptionWrapper(params.hasLoadExu, UInt(SSIDWidth.W))
    // only vector load store need
    val numLsElem      = OptionWrapper(params.hasVecLsFu, NumLsElem())

    val sqIdx = if (params.hasMemAddrFu || params.hasStdFu) Some(new SqPtr) else None
    val lqIdx = if (params.hasMemAddrFu) Some(new LqPtr) else None
    val dataSources = Vec(params.numRegSrc, DataSource())
    val l1ExuOH = OptionWrapper(params.isIQWakeUpSink, Vec(params.numRegSrc, ExuVec()))
    val srcTimer = OptionWrapper(params.isIQWakeUpSink, Vec(params.numRegSrc, UInt(3.W)))
    val loadDependency = OptionWrapper(params.needLoadDependency, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))

    val perfDebugInfo = new PerfDebugInfo()

    def exuIdx = this.params.exuIdx

    def needCancel(og0CancelOH: UInt, og1CancelOH: UInt) : Bool = {
      if (params.isIQWakeUpSink) {
        require(
          og0CancelOH.getWidth == l1ExuOH.get.head.getWidth,
          s"cancelVecSize: {og0: ${og0CancelOH.getWidth}, og1: ${og1CancelOH.getWidth}}"
        )
        val l1Cancel: Bool = l1ExuOH.get.zip(srcTimer.get).map {
          case(exuOH: Vec[Bool], srcTimer: UInt) =>
            (exuOH.asUInt & og0CancelOH).orR && srcTimer === 1.U
        }.reduce(_ | _)
        l1Cancel
      } else {
        false.B
      }
    }
  }
}