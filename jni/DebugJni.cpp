// ---------------------------------------------------------------------------
// DebugJni.cpp
//
// JNI bindings for the Core/Debugger subset exposed through
// InteropDLL/DebugApiWrapper.cpp (see jni/MesenApi.h for the declarations).
//
// Java side: mesen.MesenDebugger + mesen.debugger.* data classes.
// ---------------------------------------------------------------------------

#include "MesenApi.h"

#include "Core/Debugger/Breakpoint.h"
#include "Core/Debugger/ExpressionEvaluator.h"
#include "Core/Debugger/MemoryAccessCounter.h"
#include "Core/NES/NesTypes.h"
#include "Core/NES/Debugger/NesPpuTools.h"
#include "Core/SNES/SnesPpuTypes.h"
#include "Core/SNES/Debugger/SnesPpuTools.h"
#include "Core/Gameboy/GbTypes.h"
#include "Core/PCE/PceTypes.h"
#include "Core/SMS/SmsTypes.h"
#include "Core/GBA/GbaTypes.h"
#include "Core/WS/WsTypes.h"

#include "Core/NES/Debugger/NesEventManager.h"
#include "Core/SNES/Debugger/SnesEventManager.h"
#include "Core/Gameboy/Debugger/GbEventManager.h"
#include "Core/GBA/Debugger/GbaEventManager.h"
#include "Core/PCE/Debugger/PceEventManager.h"
#include "Core/SMS/Debugger/SmsEventManager.h"
#include "Core/SNES/SnesState.h"   // SnesState (whole-console state)
#include "Core/WS/Debugger/WsEventManager.h"

#include <jni.h>

#include <cstring>
#include <string>
#include <vector>

// InteropDLL global (same DLL): needed to check which console is running.
extern unique_ptr<Emulator> _emu;

namespace
{
	// -----------------------------------------------------------------------
	// Breakpoint mirror.
	//
	// Core's Breakpoint is a class with private members and no setters, yet the
	// C API takes a plain Breakpoint[] array. Mirror the exact member order here
	// and memcpy into real Breakpoints (this is what the .NET interop does too).
	// -----------------------------------------------------------------------
	struct InteropBreakpoint
	{
		int32_t Id;
		CpuType CpuTypeValue;
		MemoryType MemoryTypeValue;
		BreakpointTypeFlags Type;
		int32_t StartAddress;
		int32_t EndAddress;
		bool Enabled;
		bool MarkEvent;
		bool IgnoreDummyOperations;
		char Condition[1000];
	};

	static_assert(sizeof(InteropBreakpoint) == sizeof(Breakpoint),
		"InteropBreakpoint must mirror the memory layout of Core::Breakpoint");

	constexpr uint32_t CALLSTACK_CAPACITY = 512;
	constexpr uint32_t TOKEN_LIST_SIZE = 1000;
	constexpr uint32_t DEBUGGER_LOG_SIZE = 100000;
	constexpr uint32_t FIND_OCCURRENCES_CAPACITY = 500;
	constexpr uint32_t CDL_FUNCTION_CAPACITY = 50000;

	// -----------------------------------------------------------------------
	// Java class / field cache
	// -----------------------------------------------------------------------
	struct JniCache
	{
		bool ready = false;

		jclass addressInfo;
		jmethodID addressInfoCtor;
		jfieldID aiAddress, aiMemoryType;

		jclass effectiveAddress;
		jmethodID effectiveAddressCtor;
		jfieldID eaAddress, eaMemoryType, eaValueSize, eaShowAddress;

		jclass codeLine;
		jmethodID codeLineCtor;
		jfieldID clAddress, clAbsAddress, clOpSize, clFlags, clEffectiveAddress,
			clValue, clLineCpuType, clByteCode, clText, clComment;

		jclass breakpoint;
		jfieldID bpId, bpCpuType, bpMemoryType, bpType, bpStartAddress, bpEndAddress,
			bpEnabled, bpMarkEvent, bpIgnoreDummy, bpCondition;

		jclass stackFrame;
		jmethodID stackFrameCtor;
		jfieldID sfSource, sfAbsSource, sfTarget, sfAbsTarget, sfReturn,
			sfReturnStackPointer, sfAbsReturn, sfFlags;

		jclass cdlStatistics;
		jmethodID cdlStatisticsCtor;
		jfieldID csCodeBytes, csDataBytes, csTotalBytes, csJumpTargetCount,
			csFunctionCount, csDrawnChrBytes, csTotalChrBytes;

		jclass cpuVector;
		jmethodID cpuVectorCtor;
		jfieldID cvName, cvAddress, cvType;

		jclass debuggerFeatures;
		jmethodID debuggerFeaturesCtor;
		jfieldID dfRunToIrq, dfRunToNmi, dfStepOver, dfStepOut, dfStepBack,
			dfChangePc, dfCallStack, dfCpuCycleStep, dfIrqVectorOffset,
			dfCpuVectorCount, dfCpuVectors;

		jclass memoryOperation;
		jmethodID memoryOperationCtor;
		jfieldID moAddress, moValue, moOperationType, moMemoryType;

		jclass instructionProgress;
		jmethodID instructionProgressCtor;
		jfieldID ipStartCycle, ipCurrentCycle, ipLastOpCode, ipLastMemOperation;

		jclass evalResult;
		jmethodID evalResultCtor;
		jfieldID erValue, erType;

		jclass addressCounters;
		jmethodID addressCountersCtor;
		jfieldID acReadStamp, acWriteStamp, acExecStamp,
			acReadCounter, acWriteCounter, acExecCounter;

		// NES register state (the only console bound so far)
		jclass nesCpuState;
		jmethodID nesCpuStateCtor;
		jfieldID ncCycleCount, ncPc, ncSp, ncA, ncX, ncY, ncPs, ncIrqFlag, ncNmiFlag;

		jclass nesPpuState;
		jmethodID nesPpuStateCtor;
		jfieldID npSpriteOverflow, npSprite0Hit, npVerticalBlank,
			npGrayscale, npBackgroundMask, npSpriteMask, npBackgroundEnabled, npSpritesEnabled,
			npIntensifyRed, npIntensifyGreen, npIntensifyBlue,
			npBackgroundPatternAddr, npSpritePatternAddr, npVerticalWrite, npLargeSprites,
			npSecondaryPpu, npNmiOnVerticalBlank,
			npScanline, npCycle, npFrameCount, npNmiScanline, npScanlineCount, npSafeOamScanline,
			npBusAddress, npMemoryReadBuffer, npVideoRamAddr, npTmpVideoRamAddr, npScrollX,
			npWriteToggle, npSpriteRamAddr, npSecondaryOamAddr;

		// PPU tools
		jclass tileViewOptions;
		jfieldID tvoMemType, tvoFormat, tvoLayout, tvoFilter, tvoBackground,
			tvoWidth, tvoHeight, tvoStartAddress, tvoPalette, tvoGrayscale;

		jclass tileView;
		jmethodID tileViewCtor;
		jfieldID tvWidth, tvHeight, tvPixels;

		jclass tilemapOptions;
		jfieldID tmoLayer, tmoMasterClock, tmoTileHighlight, tmoAttrHighlight, tmoDisplayMode, tmoBackground;

		jclass tilemapInfo;
		jmethodID tilemapInfoCtor;
		jfieldID tiBpp, tiFormat, tiMirroring, tiTileWidth, tiTileHeight,
			tiScrollX, tiScrollWidth, tiScrollY, tiScrollHeight,
			tiRowCount, tiColumnCount, tiTilemapAddress, tiTilesetAddress, tiPriority;

		jclass tilemapResult;
		jmethodID tilemapResultCtor;
		jfieldID trInfo, trWidth, trHeight, trPixels;

		jclass tilemapTileInfo;
		jmethodID tilemapTileInfoCtor;
		jfieldID ttiRow, ttiColumn, ttiWidth, ttiHeight, ttiTileMapAddress, ttiTileIndex,
			ttiTileAddresses, ttiPixelData, ttiPaletteIndex, ttiPaletteAddress,
			ttiBasePaletteIndex, ttiAttributeAddress, ttiAttributeData,
			ttiHorizontalMirroring, ttiVerticalMirroring, ttiHighPriority;

		jclass spriteOptions;
		jfieldID soBackground, soFilter;

		jclass spritePreviewInfo;
		jmethodID spritePreviewInfoCtor;
		jfieldID spiWidth, spiHeight, spiSpriteCount, spiCoordOffsetX, spiCoordOffsetY,
			spiVisibleX, spiVisibleY, spiVisibleWidth, spiVisibleHeight,
			spiWrapBottomToTop, spiWrapRightToLeft;

		jclass spriteInfo;
		jmethodID spriteInfoCtor;
		jfieldID siTileIndex, siTileAddress, siPaletteAddress, siFormat, siSpriteIndex,
			siX, siY, siRawX, siRawY, siBpp, siPalette, siPriority, siMode,
			siWidth, siHeight, siHorizontalMirror, siVerticalMirror, siMosaicEnabled,
			siTransformEnabled, siDoubleSize, siUseSecondTable, siTransformParamIndex,
			siVisibility, siUseExtendedVram, siTileAddresses;

		jclass spriteListResult;
		jmethodID spriteListResultCtor;
		jfieldID slrInfo, slrSprites, slrSpritePreviews, slrScreenPreview;

		jclass paletteInfo;
		jmethodID paletteInfoCtor;
		jfieldID piPaletteMemType, piPaletteMemOffset, piHasMemType, piColorCount,
			piBgColorCount, piSpriteColorCount, piSpritePaletteOffset, piColorsPerPalette,
			piRawFormat, piRawPalette, piRgbPalette;

		// Event viewer
		jclass debugEventInfo;
		jmethodID debugEventInfoCtor;
		jfieldID deiOperation, deiType, deiProgramCounter, deiScanline, deiCycle,
			deiBreakpointId, deiDmaChannel, deiDmaSrcAddress, deiDmaTransferSize,
			deiDmaHdmaTableAddress, deiDmaSrcBank, deiDmaDestAddress, deiDmaActive,
			deiDmaInvertDirection, deiDmaDecrement, deiDmaFixedTransfer,
			deiDmaHdmaIndirectAddressing, deiDmaTransferMode, deiDmaHdmaBank,
			deiDmaHdmaLineCounter, deiDmaDoTransfer, deiDmaUnusedControlFlag,
			deiDmaUnusedRegister, deiFlags, deiRegisterId, deiTargetMemory, deiColor;

		jclass eventViewerConfig;
		jmethodID eventViewerConfigCtor;
		jfieldID evcCategoryNames, evcVisible, evcColors, evcShowPreviousFrame;

		jclass viewerSize;
		jmethodID viewerSizeCtor;
		jfieldID vsWidth, vsHeight;

		// Execution trace
		jclass traceRow;
		jmethodID traceRowCtor;
		jfieldID trProgramCounter, trType, trByteCode, trByteCodeSize, trLogOutput;

		jclass traceLoggerOptions;
		jmethodID traceLoggerOptionsCtor;
		jfieldID tloEnabled, tloIndentCode, tloUseLabels, tloCondition, tloFormat;

		// Profiler
		jclass profiledFunction;
		jmethodID profiledFunctionCtor;
		jfieldID pfExclusiveCycles, pfInclusiveCycles, pfCallCount, pfMinCycles,
			pfMaxCycles, pfAddress, pfFlags;

		// Input overrides
		jclass debugControllerState;
		jmethodID debugControllerStateCtor;
		jfieldID dcsA, dcsB, dcsX, dcsY, dcsL, dcsR, dcsU, dcsD,
			dcsUp, dcsDown, dcsLeft, dcsRight, dcsSelect, dcsStart;

		jclass debugConfig;		// DebugConfig (generated from Core/Shared/SettingTypes.h)
		jfieldID dcBreakOnUninitRead;
		jfieldID dcShowJumpLabels;
		jfieldID dcDrawPartialFrame;
		jfieldID dcShowVerifiedData;
		jfieldID dcDisassembleVerifiedData;
		jfieldID dcShowUnidentifiedData;
		jfieldID dcDisassembleUnidentifiedData;
		jfieldID dcUseLowerCaseDisassembly;
		jfieldID dcShowMemoryValues;
		jfieldID dcAutoResetCdl;
		jfieldID dcUsePredictiveBreakpoints;
		jfieldID dcSingleBreakpointPerInstruction;
		jfieldID dcSnesBreakOnBrk;
		jfieldID dcSnesBreakOnCop;
		jfieldID dcSnesBreakOnWdm;
		jfieldID dcSnesBreakOnStp;
		jfieldID dcSnesBreakOnInvalidPpuAccess;
		jfieldID dcSnesBreakOnReadDuringAutoJoy;
		jfieldID dcSnesUseAltSpcOpNames;
		jfieldID dcSnesIgnoreDspReadWrites;
		jfieldID dcSpcBreakOnBrk;
		jfieldID dcSpcBreakOnStpSleep;
		jfieldID dcGbBreakOnInvalidOamAccess;
		jfieldID dcGbBreakOnInvalidVramAccess;
		jfieldID dcGbBreakOnDisableLcdOutsideVblank;
		jfieldID dcGbBreakOnInvalidOpCode;
		jfieldID dcGbBreakOnNopLoad;
		jfieldID dcGbBreakOnOamCorruption;
		jfieldID dcNesBreakOnBrk;
		jfieldID dcNesBreakOnUnofficialOpCode;
		jfieldID dcNesBreakOnUnstableOpCode;
		jfieldID dcNesBreakOnCpuCrash;
		jfieldID dcNesBreakOnBusConflict;
		jfieldID dcNesBreakOnDecayedOamRead;
		jfieldID dcNesBreakOnPpuScrollGlitch;
		jfieldID dcNesBreakOnExtOutputMode;
		jfieldID dcNesBreakOnInvalidVramAccess;
		jfieldID dcNesBreakOnInvalidOamWrite;
		jfieldID dcNesBreakOnDmaInputRead;
		jfieldID dcPceBreakOnBrk;
		jfieldID dcPceBreakOnUnofficialOpCode;
		jfieldID dcPceBreakOnInvalidVramAddress;
		jfieldID dcSmsBreakOnNopLoad;
		jfieldID dcGbaBreakOnNopLoad;
		jfieldID dcGbaBreakOnInvalidOpCode;
		jfieldID dcGbaBreakOnUnalignedMemAccess;
		jfieldID dcGbaDisMode;
		jfieldID dcWsBreakOnInvalidOpCode;
		jfieldID dcScriptAllowIoOsAccess;
		jfieldID dcScriptAllowNetworkAccess;
		jfieldID dcScriptTimeout;
	};

	JniCache g_cache;

	bool InitCache(JNIEnv* env)
	{
		if(g_cache.ready) {
			return true;
		}

		jclass local;
		auto findClass = [&](const char* name) -> jclass {
			jclass c = env->FindClass(name);
			if(!c) {
				return nullptr;
			}
			jclass global = (jclass)env->NewGlobalRef(c);
			env->DeleteLocalRef(c);
			return global;
		};

		g_cache.addressInfo = findClass("mesen/debugger/AddressInfo");
		g_cache.effectiveAddress = findClass("mesen/debugger/EffectiveAddress");
		g_cache.codeLine = findClass("mesen/debugger/CodeLine");
		g_cache.breakpoint = findClass("mesen/debugger/Breakpoint");
		g_cache.stackFrame = findClass("mesen/debugger/StackFrame");
		g_cache.cdlStatistics = findClass("mesen/debugger/CdlStatistics");
		g_cache.cpuVector = findClass("mesen/debugger/CpuVector");
		g_cache.debuggerFeatures = findClass("mesen/debugger/DebuggerFeatures");
		g_cache.memoryOperation = findClass("mesen/debugger/MemoryOperation");
		g_cache.instructionProgress = findClass("mesen/debugger/InstructionProgress");
		g_cache.evalResult = findClass("mesen/debugger/EvalResult");
		g_cache.addressCounters = findClass("mesen/debugger/AddressCounters");

		g_cache.nesCpuState = findClass("mesen/debugger/NesCpuState");
		g_cache.nesPpuState = findClass("mesen/debugger/NesPpuState");

		g_cache.tileViewOptions = findClass("mesen/debugger/TileViewOptions");
		g_cache.tileView = findClass("mesen/debugger/TileView");
		g_cache.tilemapOptions = findClass("mesen/debugger/TilemapOptions");
		g_cache.tilemapInfo = findClass("mesen/debugger/TilemapInfo");
		g_cache.tilemapResult = findClass("mesen/debugger/TilemapResult");
		g_cache.tilemapTileInfo = findClass("mesen/debugger/TilemapTileInfo");
		g_cache.spriteOptions = findClass("mesen/debugger/SpriteOptions");
		g_cache.spritePreviewInfo = findClass("mesen/debugger/SpritePreviewInfo");
		g_cache.spriteInfo = findClass("mesen/debugger/SpriteInfo");
		g_cache.spriteListResult = findClass("mesen/debugger/SpriteListResult");
		g_cache.paletteInfo = findClass("mesen/debugger/PaletteInfo");

		g_cache.debugEventInfo = findClass("mesen/debugger/DebugEventInfo");
		g_cache.eventViewerConfig = findClass("mesen/debugger/EventViewerConfig");
		g_cache.viewerSize = findClass("mesen/debugger/ViewerSize");
		g_cache.traceRow = findClass("mesen/debugger/TraceRow");
		g_cache.traceLoggerOptions = findClass("mesen/debugger/TraceLoggerOptions");
		g_cache.profiledFunction = findClass("mesen/debugger/ProfiledFunction");
		g_cache.debugControllerState = findClass("mesen/debugger/DebugControllerState");
		g_cache.debugConfig = findClass("mesen/debugger/DebugConfig");

		if(!g_cache.addressInfo || !g_cache.effectiveAddress || !g_cache.codeLine ||
			!g_cache.breakpoint || !g_cache.stackFrame || !g_cache.cdlStatistics ||
			!g_cache.cpuVector || !g_cache.debuggerFeatures || !g_cache.memoryOperation ||
			!g_cache.instructionProgress || !g_cache.evalResult || !g_cache.addressCounters ||
			!g_cache.nesCpuState || !g_cache.nesPpuState ||
			!g_cache.tileViewOptions || !g_cache.tileView || !g_cache.tilemapOptions ||
			!g_cache.tilemapInfo || !g_cache.tilemapResult || !g_cache.tilemapTileInfo ||
			!g_cache.spriteOptions || !g_cache.spritePreviewInfo || !g_cache.spriteInfo ||
			!g_cache.spriteListResult || !g_cache.paletteInfo ||
			!g_cache.debugEventInfo || !g_cache.eventViewerConfig || !g_cache.viewerSize ||
			!g_cache.traceRow || !g_cache.traceLoggerOptions || !g_cache.profiledFunction ||
			!g_cache.debugControllerState || !g_cache.debugConfig) {
			return false;
		}

		local = nullptr;

		auto field = [&](jclass c, const char* name, const char* sig) -> jfieldID {
			return env->GetFieldID(c, name, sig);
		};

		g_cache.addressInfoCtor = env->GetMethodID(g_cache.addressInfo, "<init>", "()V");
		g_cache.aiAddress = field(g_cache.addressInfo, "address", "I");
		g_cache.aiMemoryType = field(g_cache.addressInfo, "memoryType", "I");

		g_cache.effectiveAddressCtor = env->GetMethodID(g_cache.effectiveAddress, "<init>", "()V");
		g_cache.eaAddress = field(g_cache.effectiveAddress, "address", "J");
		g_cache.eaMemoryType = field(g_cache.effectiveAddress, "memoryType", "I");
		g_cache.eaValueSize = field(g_cache.effectiveAddress, "valueSize", "I");
		g_cache.eaShowAddress = field(g_cache.effectiveAddress, "showAddress", "Z");

		g_cache.codeLineCtor = env->GetMethodID(g_cache.codeLine, "<init>", "()V");
		g_cache.clAddress = field(g_cache.codeLine, "address", "I");
		g_cache.clAbsAddress = field(g_cache.codeLine, "absoluteAddress", "Lmesen/debugger/AddressInfo;");
		g_cache.clOpSize = field(g_cache.codeLine, "opSize", "I");
		g_cache.clFlags = field(g_cache.codeLine, "flags", "I");
		g_cache.clEffectiveAddress = field(g_cache.codeLine, "effectiveAddress", "Lmesen/debugger/EffectiveAddress;");
		g_cache.clValue = field(g_cache.codeLine, "value", "I");
		g_cache.clLineCpuType = field(g_cache.codeLine, "lineCpuType", "I");
		g_cache.clByteCode = field(g_cache.codeLine, "byteCode", "[B");
		g_cache.clText = field(g_cache.codeLine, "text", "Ljava/lang/String;");
		g_cache.clComment = field(g_cache.codeLine, "comment", "Ljava/lang/String;");

		g_cache.bpId = field(g_cache.breakpoint, "id", "I");
		g_cache.bpCpuType = field(g_cache.breakpoint, "cpuType", "I");
		g_cache.bpMemoryType = field(g_cache.breakpoint, "memoryType", "I");
		g_cache.bpType = field(g_cache.breakpoint, "type", "I");
		g_cache.bpStartAddress = field(g_cache.breakpoint, "startAddress", "I");
		g_cache.bpEndAddress = field(g_cache.breakpoint, "endAddress", "I");
		g_cache.bpEnabled = field(g_cache.breakpoint, "enabled", "Z");
		g_cache.bpMarkEvent = field(g_cache.breakpoint, "markEvent", "Z");
		g_cache.bpIgnoreDummy = field(g_cache.breakpoint, "ignoreDummyOperations", "Z");
		g_cache.bpCondition = field(g_cache.breakpoint, "condition", "Ljava/lang/String;");

		g_cache.stackFrameCtor = env->GetMethodID(g_cache.stackFrame, "<init>", "()V");
		g_cache.sfSource = field(g_cache.stackFrame, "source", "I");
		g_cache.sfAbsSource = field(g_cache.stackFrame, "absSource", "Lmesen/debugger/AddressInfo;");
		g_cache.sfTarget = field(g_cache.stackFrame, "target", "I");
		g_cache.sfAbsTarget = field(g_cache.stackFrame, "absTarget", "Lmesen/debugger/AddressInfo;");
		g_cache.sfReturn = field(g_cache.stackFrame, "returnAddress", "I");
		g_cache.sfReturnStackPointer = field(g_cache.stackFrame, "returnStackPointer", "I");
		g_cache.sfAbsReturn = field(g_cache.stackFrame, "absReturn", "Lmesen/debugger/AddressInfo;");
		g_cache.sfFlags = field(g_cache.stackFrame, "flags", "I");

		g_cache.cdlStatisticsCtor = env->GetMethodID(g_cache.cdlStatistics, "<init>", "()V");
		g_cache.csCodeBytes = field(g_cache.cdlStatistics, "codeBytes", "I");
		g_cache.csDataBytes = field(g_cache.cdlStatistics, "dataBytes", "I");
		g_cache.csTotalBytes = field(g_cache.cdlStatistics, "totalBytes", "I");
		g_cache.csJumpTargetCount = field(g_cache.cdlStatistics, "jumpTargetCount", "I");
		g_cache.csFunctionCount = field(g_cache.cdlStatistics, "functionCount", "I");
		g_cache.csDrawnChrBytes = field(g_cache.cdlStatistics, "drawnChrBytes", "I");
		g_cache.csTotalChrBytes = field(g_cache.cdlStatistics, "totalChrBytes", "I");

		g_cache.cpuVectorCtor = env->GetMethodID(g_cache.cpuVector, "<init>", "()V");
		g_cache.cvName = field(g_cache.cpuVector, "name", "Ljava/lang/String;");
		g_cache.cvAddress = field(g_cache.cpuVector, "address", "I");
		g_cache.cvType = field(g_cache.cpuVector, "type", "I");

		g_cache.debuggerFeaturesCtor = env->GetMethodID(g_cache.debuggerFeatures, "<init>", "()V");
		g_cache.dfRunToIrq = field(g_cache.debuggerFeatures, "runToIrq", "Z");
		g_cache.dfRunToNmi = field(g_cache.debuggerFeatures, "runToNmi", "Z");
		g_cache.dfStepOver = field(g_cache.debuggerFeatures, "stepOver", "Z");
		g_cache.dfStepOut = field(g_cache.debuggerFeatures, "stepOut", "Z");
		g_cache.dfStepBack = field(g_cache.debuggerFeatures, "stepBack", "Z");
		g_cache.dfChangePc = field(g_cache.debuggerFeatures, "changeProgramCounter", "Z");
		g_cache.dfCallStack = field(g_cache.debuggerFeatures, "callStack", "Z");
		g_cache.dfCpuCycleStep = field(g_cache.debuggerFeatures, "cpuCycleStep", "Z");
		g_cache.dfIrqVectorOffset = field(g_cache.debuggerFeatures, "irqVectorOffset", "I");
		g_cache.dfCpuVectorCount = field(g_cache.debuggerFeatures, "cpuVectorCount", "I");
		g_cache.dfCpuVectors = field(g_cache.debuggerFeatures, "cpuVectors", "[Lmesen/debugger/CpuVector;");

		g_cache.memoryOperationCtor = env->GetMethodID(g_cache.memoryOperation, "<init>", "()V");
		g_cache.moAddress = field(g_cache.memoryOperation, "address", "I");
		g_cache.moValue = field(g_cache.memoryOperation, "value", "I");
		g_cache.moOperationType = field(g_cache.memoryOperation, "operationType", "I");
		g_cache.moMemoryType = field(g_cache.memoryOperation, "memoryType", "I");

		g_cache.instructionProgressCtor = env->GetMethodID(g_cache.instructionProgress, "<init>", "()V");
		g_cache.ipStartCycle = field(g_cache.instructionProgress, "startCycle", "J");
		g_cache.ipCurrentCycle = field(g_cache.instructionProgress, "currentCycle", "J");
		g_cache.ipLastOpCode = field(g_cache.instructionProgress, "lastOpCode", "I");
		g_cache.ipLastMemOperation = field(g_cache.instructionProgress, "lastMemOperation", "Lmesen/debugger/MemoryOperation;");

		g_cache.evalResultCtor = env->GetMethodID(g_cache.evalResult, "<init>", "()V");
		g_cache.erValue = field(g_cache.evalResult, "value", "J");
		g_cache.erType = field(g_cache.evalResult, "type", "I");

		g_cache.addressCountersCtor = env->GetMethodID(g_cache.addressCounters, "<init>", "()V");
		g_cache.acReadStamp = field(g_cache.addressCounters, "readStamp", "J");
		g_cache.acWriteStamp = field(g_cache.addressCounters, "writeStamp", "J");
		g_cache.acExecStamp = field(g_cache.addressCounters, "execStamp", "J");
		g_cache.acReadCounter = field(g_cache.addressCounters, "readCounter", "I");
		g_cache.acWriteCounter = field(g_cache.addressCounters, "writeCounter", "I");
		g_cache.acExecCounter = field(g_cache.addressCounters, "execCounter", "I");

		g_cache.nesCpuStateCtor = env->GetMethodID(g_cache.nesCpuState, "<init>", "()V");
		g_cache.ncCycleCount = field(g_cache.nesCpuState, "cycleCount", "J");
		g_cache.ncPc = field(g_cache.nesCpuState, "pc", "I");
		g_cache.ncSp = field(g_cache.nesCpuState, "sp", "I");
		g_cache.ncA = field(g_cache.nesCpuState, "a", "I");
		g_cache.ncX = field(g_cache.nesCpuState, "x", "I");
		g_cache.ncY = field(g_cache.nesCpuState, "y", "I");
		g_cache.ncPs = field(g_cache.nesCpuState, "ps", "I");
		g_cache.ncIrqFlag = field(g_cache.nesCpuState, "irqFlag", "I");
		g_cache.ncNmiFlag = field(g_cache.nesCpuState, "nmiFlag", "Z");

		g_cache.nesPpuStateCtor = env->GetMethodID(g_cache.nesPpuState, "<init>", "()V");
		g_cache.npSpriteOverflow = field(g_cache.nesPpuState, "spriteOverflow", "Z");
		g_cache.npSprite0Hit = field(g_cache.nesPpuState, "sprite0Hit", "Z");
		g_cache.npVerticalBlank = field(g_cache.nesPpuState, "verticalBlank", "Z");
		g_cache.npGrayscale = field(g_cache.nesPpuState, "grayscale", "Z");
		g_cache.npBackgroundMask = field(g_cache.nesPpuState, "backgroundMask", "Z");
		g_cache.npSpriteMask = field(g_cache.nesPpuState, "spriteMask", "Z");
		g_cache.npBackgroundEnabled = field(g_cache.nesPpuState, "backgroundEnabled", "Z");
		g_cache.npSpritesEnabled = field(g_cache.nesPpuState, "spritesEnabled", "Z");
		g_cache.npIntensifyRed = field(g_cache.nesPpuState, "intensifyRed", "Z");
		g_cache.npIntensifyGreen = field(g_cache.nesPpuState, "intensifyGreen", "Z");
		g_cache.npIntensifyBlue = field(g_cache.nesPpuState, "intensifyBlue", "Z");
		g_cache.npBackgroundPatternAddr = field(g_cache.nesPpuState, "backgroundPatternAddr", "I");
		g_cache.npSpritePatternAddr = field(g_cache.nesPpuState, "spritePatternAddr", "I");
		g_cache.npVerticalWrite = field(g_cache.nesPpuState, "verticalWrite", "Z");
		g_cache.npLargeSprites = field(g_cache.nesPpuState, "largeSprites", "Z");
		g_cache.npSecondaryPpu = field(g_cache.nesPpuState, "secondaryPpu", "Z");
		g_cache.npNmiOnVerticalBlank = field(g_cache.nesPpuState, "nmiOnVerticalBlank", "Z");
		g_cache.npScanline = field(g_cache.nesPpuState, "scanline", "I");
		g_cache.npCycle = field(g_cache.nesPpuState, "cycle", "I");
		g_cache.npFrameCount = field(g_cache.nesPpuState, "frameCount", "I");
		g_cache.npNmiScanline = field(g_cache.nesPpuState, "nmiScanline", "I");
		g_cache.npScanlineCount = field(g_cache.nesPpuState, "scanlineCount", "I");
		g_cache.npSafeOamScanline = field(g_cache.nesPpuState, "safeOamScanline", "I");
		g_cache.npBusAddress = field(g_cache.nesPpuState, "busAddress", "I");
		g_cache.npMemoryReadBuffer = field(g_cache.nesPpuState, "memoryReadBuffer", "I");
		g_cache.npVideoRamAddr = field(g_cache.nesPpuState, "videoRamAddr", "I");
		g_cache.npTmpVideoRamAddr = field(g_cache.nesPpuState, "tmpVideoRamAddr", "I");
		g_cache.npScrollX = field(g_cache.nesPpuState, "scrollX", "I");
		g_cache.npWriteToggle = field(g_cache.nesPpuState, "writeToggle", "Z");
		g_cache.npSpriteRamAddr = field(g_cache.nesPpuState, "spriteRamAddr", "I");
		g_cache.npSecondaryOamAddr = field(g_cache.nesPpuState, "secondaryOamAddr", "I");

		g_cache.tvoMemType = field(g_cache.tileViewOptions, "memoryType", "I");
		g_cache.tvoFormat = field(g_cache.tileViewOptions, "format", "I");
		g_cache.tvoLayout = field(g_cache.tileViewOptions, "layout", "I");
		g_cache.tvoFilter = field(g_cache.tileViewOptions, "filter", "I");
		g_cache.tvoBackground = field(g_cache.tileViewOptions, "background", "I");
		g_cache.tvoWidth = field(g_cache.tileViewOptions, "width", "I");
		g_cache.tvoHeight = field(g_cache.tileViewOptions, "height", "I");
		g_cache.tvoStartAddress = field(g_cache.tileViewOptions, "startAddress", "I");
		g_cache.tvoPalette = field(g_cache.tileViewOptions, "palette", "I");
		g_cache.tvoGrayscale = field(g_cache.tileViewOptions, "useGrayscalePalette", "Z");

		g_cache.tileViewCtor = env->GetMethodID(g_cache.tileView, "<init>", "()V");
		g_cache.tvWidth = field(g_cache.tileView, "width", "I");
		g_cache.tvHeight = field(g_cache.tileView, "height", "I");
		g_cache.tvPixels = field(g_cache.tileView, "pixels", "[I");

		g_cache.tmoLayer = field(g_cache.tilemapOptions, "layer", "I");
		g_cache.tmoMasterClock = field(g_cache.tilemapOptions, "masterClock", "J");
		g_cache.tmoTileHighlight = field(g_cache.tilemapOptions, "tileHighlightMode", "I");
		g_cache.tmoAttrHighlight = field(g_cache.tilemapOptions, "attributeHighlightMode", "I");
		g_cache.tmoDisplayMode = field(g_cache.tilemapOptions, "displayMode", "I");
		g_cache.tmoBackground = field(g_cache.tilemapOptions, "background", "I");

		g_cache.tilemapInfoCtor = env->GetMethodID(g_cache.tilemapInfo, "<init>", "()V");
		g_cache.tiBpp = field(g_cache.tilemapInfo, "bpp", "I");
		g_cache.tiFormat = field(g_cache.tilemapInfo, "format", "I");
		g_cache.tiMirroring = field(g_cache.tilemapInfo, "mirroring", "I");
		g_cache.tiTileWidth = field(g_cache.tilemapInfo, "tileWidth", "I");
		g_cache.tiTileHeight = field(g_cache.tilemapInfo, "tileHeight", "I");
		g_cache.tiScrollX = field(g_cache.tilemapInfo, "scrollX", "I");
		g_cache.tiScrollWidth = field(g_cache.tilemapInfo, "scrollWidth", "I");
		g_cache.tiScrollY = field(g_cache.tilemapInfo, "scrollY", "I");
		g_cache.tiScrollHeight = field(g_cache.tilemapInfo, "scrollHeight", "I");
		g_cache.tiRowCount = field(g_cache.tilemapInfo, "rowCount", "I");
		g_cache.tiColumnCount = field(g_cache.tilemapInfo, "columnCount", "I");
		g_cache.tiTilemapAddress = field(g_cache.tilemapInfo, "tilemapAddress", "I");
		g_cache.tiTilesetAddress = field(g_cache.tilemapInfo, "tilesetAddress", "I");
		g_cache.tiPriority = field(g_cache.tilemapInfo, "priority", "I");

		g_cache.tilemapResultCtor = env->GetMethodID(g_cache.tilemapResult, "<init>", "()V");
		g_cache.trInfo = field(g_cache.tilemapResult, "info", "Lmesen/debugger/TilemapInfo;");
		g_cache.trWidth = field(g_cache.tilemapResult, "width", "I");
		g_cache.trHeight = field(g_cache.tilemapResult, "height", "I");
		g_cache.trPixels = field(g_cache.tilemapResult, "pixels", "[I");

		g_cache.tilemapTileInfoCtor = env->GetMethodID(g_cache.tilemapTileInfo, "<init>", "()V");
		g_cache.ttiRow = field(g_cache.tilemapTileInfo, "row", "I");
		g_cache.ttiColumn = field(g_cache.tilemapTileInfo, "column", "I");
		g_cache.ttiWidth = field(g_cache.tilemapTileInfo, "width", "I");
		g_cache.ttiHeight = field(g_cache.tilemapTileInfo, "height", "I");
		g_cache.ttiTileMapAddress = field(g_cache.tilemapTileInfo, "tileMapAddress", "I");
		g_cache.ttiTileIndex = field(g_cache.tilemapTileInfo, "tileIndex", "I");
		g_cache.ttiTileAddresses = field(g_cache.tilemapTileInfo, "tileAddresses", "[I");
		g_cache.ttiPixelData = field(g_cache.tilemapTileInfo, "pixelData", "I");
		g_cache.ttiPaletteIndex = field(g_cache.tilemapTileInfo, "paletteIndex", "I");
		g_cache.ttiPaletteAddress = field(g_cache.tilemapTileInfo, "paletteAddress", "I");
		g_cache.ttiBasePaletteIndex = field(g_cache.tilemapTileInfo, "basePaletteIndex", "I");
		g_cache.ttiAttributeAddress = field(g_cache.tilemapTileInfo, "attributeAddress", "I");
		g_cache.ttiAttributeData = field(g_cache.tilemapTileInfo, "attributeData", "I");
		g_cache.ttiHorizontalMirroring = field(g_cache.tilemapTileInfo, "horizontalMirroring", "I");
		g_cache.ttiVerticalMirroring = field(g_cache.tilemapTileInfo, "verticalMirroring", "I");
		g_cache.ttiHighPriority = field(g_cache.tilemapTileInfo, "highPriority", "I");

		g_cache.soBackground = field(g_cache.spriteOptions, "background", "I");
		g_cache.soFilter = field(g_cache.spriteOptions, "filter", "I");

		g_cache.spritePreviewInfoCtor = env->GetMethodID(g_cache.spritePreviewInfo, "<init>", "()V");
		g_cache.spiWidth = field(g_cache.spritePreviewInfo, "width", "I");
		g_cache.spiHeight = field(g_cache.spritePreviewInfo, "height", "I");
		g_cache.spiSpriteCount = field(g_cache.spritePreviewInfo, "spriteCount", "I");
		g_cache.spiCoordOffsetX = field(g_cache.spritePreviewInfo, "coordOffsetX", "I");
		g_cache.spiCoordOffsetY = field(g_cache.spritePreviewInfo, "coordOffsetY", "I");
		g_cache.spiVisibleX = field(g_cache.spritePreviewInfo, "visibleX", "I");
		g_cache.spiVisibleY = field(g_cache.spritePreviewInfo, "visibleY", "I");
		g_cache.spiVisibleWidth = field(g_cache.spritePreviewInfo, "visibleWidth", "I");
		g_cache.spiVisibleHeight = field(g_cache.spritePreviewInfo, "visibleHeight", "I");
		g_cache.spiWrapBottomToTop = field(g_cache.spritePreviewInfo, "wrapBottomToTop", "Z");
		g_cache.spiWrapRightToLeft = field(g_cache.spritePreviewInfo, "wrapRightToLeft", "Z");

		g_cache.spriteInfoCtor = env->GetMethodID(g_cache.spriteInfo, "<init>", "()V");
		g_cache.siTileIndex = field(g_cache.spriteInfo, "tileIndex", "I");
		g_cache.siTileAddress = field(g_cache.spriteInfo, "tileAddress", "I");
		g_cache.siPaletteAddress = field(g_cache.spriteInfo, "paletteAddress", "I");
		g_cache.siFormat = field(g_cache.spriteInfo, "format", "I");
		g_cache.siSpriteIndex = field(g_cache.spriteInfo, "spriteIndex", "I");
		g_cache.siX = field(g_cache.spriteInfo, "x", "I");
		g_cache.siY = field(g_cache.spriteInfo, "y", "I");
		g_cache.siRawX = field(g_cache.spriteInfo, "rawX", "I");
		g_cache.siRawY = field(g_cache.spriteInfo, "rawY", "I");
		g_cache.siBpp = field(g_cache.spriteInfo, "bpp", "I");
		g_cache.siPalette = field(g_cache.spriteInfo, "palette", "I");
		g_cache.siPriority = field(g_cache.spriteInfo, "priority", "I");
		g_cache.siMode = field(g_cache.spriteInfo, "mode", "I");
		g_cache.siWidth = field(g_cache.spriteInfo, "width", "I");
		g_cache.siHeight = field(g_cache.spriteInfo, "height", "I");
		g_cache.siHorizontalMirror = field(g_cache.spriteInfo, "horizontalMirror", "I");
		g_cache.siVerticalMirror = field(g_cache.spriteInfo, "verticalMirror", "I");
		g_cache.siMosaicEnabled = field(g_cache.spriteInfo, "mosaicEnabled", "I");
		g_cache.siTransformEnabled = field(g_cache.spriteInfo, "transformEnabled", "I");
		g_cache.siDoubleSize = field(g_cache.spriteInfo, "doubleSize", "I");
		g_cache.siUseSecondTable = field(g_cache.spriteInfo, "useSecondTable", "I");
		g_cache.siTransformParamIndex = field(g_cache.spriteInfo, "transformParamIndex", "I");
		g_cache.siVisibility = field(g_cache.spriteInfo, "visibility", "I");
		g_cache.siUseExtendedVram = field(g_cache.spriteInfo, "useExtendedVram", "Z");
		g_cache.siTileAddresses = field(g_cache.spriteInfo, "tileAddresses", "[I");

		g_cache.spriteListResultCtor = env->GetMethodID(g_cache.spriteListResult, "<init>", "()V");
		g_cache.slrInfo = field(g_cache.spriteListResult, "info", "Lmesen/debugger/SpritePreviewInfo;");
		g_cache.slrSprites = field(g_cache.spriteListResult, "sprites", "[Lmesen/debugger/SpriteInfo;");
		g_cache.slrSpritePreviews = field(g_cache.spriteListResult, "spritePreviews", "[I");
		g_cache.slrScreenPreview = field(g_cache.spriteListResult, "screenPreview", "[I");

		g_cache.paletteInfoCtor = env->GetMethodID(g_cache.paletteInfo, "<init>", "()V");
		g_cache.piPaletteMemType = field(g_cache.paletteInfo, "paletteMemType", "I");
		g_cache.piPaletteMemOffset = field(g_cache.paletteInfo, "paletteMemOffset", "I");
		g_cache.piHasMemType = field(g_cache.paletteInfo, "hasMemType", "Z");
		g_cache.piColorCount = field(g_cache.paletteInfo, "colorCount", "I");
		g_cache.piBgColorCount = field(g_cache.paletteInfo, "bgColorCount", "I");
		g_cache.piSpriteColorCount = field(g_cache.paletteInfo, "spriteColorCount", "I");
		g_cache.piSpritePaletteOffset = field(g_cache.paletteInfo, "spritePaletteOffset", "I");
		g_cache.piColorsPerPalette = field(g_cache.paletteInfo, "colorsPerPalette", "I");
		g_cache.piRawFormat = field(g_cache.paletteInfo, "rawFormat", "I");
		g_cache.piRawPalette = field(g_cache.paletteInfo, "rawPalette", "[I");
		g_cache.piRgbPalette = field(g_cache.paletteInfo, "rgbPalette", "[I");

		g_cache.debugEventInfoCtor = env->GetMethodID(g_cache.debugEventInfo, "<init>", "()V");
		g_cache.deiOperation = field(g_cache.debugEventInfo, "operation", "Lmesen/debugger/MemoryOperation;");
		g_cache.deiType = field(g_cache.debugEventInfo, "type", "I");
		g_cache.deiProgramCounter = field(g_cache.debugEventInfo, "programCounter", "I");
		g_cache.deiScanline = field(g_cache.debugEventInfo, "scanline", "I");
		g_cache.deiCycle = field(g_cache.debugEventInfo, "cycle", "I");
		g_cache.deiBreakpointId = field(g_cache.debugEventInfo, "breakpointId", "I");
		g_cache.deiDmaChannel = field(g_cache.debugEventInfo, "dmaChannel", "I");
		g_cache.deiDmaSrcAddress = field(g_cache.debugEventInfo, "dmaSrcAddress", "I");
		g_cache.deiDmaTransferSize = field(g_cache.debugEventInfo, "dmaTransferSize", "I");
		g_cache.deiDmaHdmaTableAddress = field(g_cache.debugEventInfo, "dmaHdmaTableAddress", "I");
		g_cache.deiDmaSrcBank = field(g_cache.debugEventInfo, "dmaSrcBank", "I");
		g_cache.deiDmaDestAddress = field(g_cache.debugEventInfo, "dmaDestAddress", "I");
		g_cache.deiDmaActive = field(g_cache.debugEventInfo, "dmaActive", "Z");
		g_cache.deiDmaInvertDirection = field(g_cache.debugEventInfo, "dmaInvertDirection", "Z");
		g_cache.deiDmaDecrement = field(g_cache.debugEventInfo, "dmaDecrement", "Z");
		g_cache.deiDmaFixedTransfer = field(g_cache.debugEventInfo, "dmaFixedTransfer", "Z");
		g_cache.deiDmaHdmaIndirectAddressing = field(g_cache.debugEventInfo, "dmaHdmaIndirectAddressing", "Z");
		g_cache.deiDmaTransferMode = field(g_cache.debugEventInfo, "dmaTransferMode", "I");
		g_cache.deiDmaHdmaBank = field(g_cache.debugEventInfo, "dmaHdmaBank", "I");
		g_cache.deiDmaHdmaLineCounter = field(g_cache.debugEventInfo, "dmaHdmaLineCounterAndRepeat", "I");
		g_cache.deiDmaDoTransfer = field(g_cache.debugEventInfo, "dmaDoTransfer", "Z");
		g_cache.deiDmaUnusedControlFlag = field(g_cache.debugEventInfo, "dmaUnusedControlFlag", "Z");
		g_cache.deiDmaUnusedRegister = field(g_cache.debugEventInfo, "dmaUnusedRegister", "I");
		g_cache.deiFlags = field(g_cache.debugEventInfo, "flags", "I");
		g_cache.deiRegisterId = field(g_cache.debugEventInfo, "registerId", "I");
		g_cache.deiTargetMemory = field(g_cache.debugEventInfo, "targetMemory", "Lmesen/debugger/MemoryOperation;");
		g_cache.deiColor = field(g_cache.debugEventInfo, "color", "I");

		g_cache.eventViewerConfigCtor = env->GetMethodID(g_cache.eventViewerConfig, "<init>", "()V");
		g_cache.evcCategoryNames = field(g_cache.eventViewerConfig, "categoryNames", "[Ljava/lang/String;");
		g_cache.evcVisible = field(g_cache.eventViewerConfig, "visible", "[Z");
		g_cache.evcColors = field(g_cache.eventViewerConfig, "colors", "[I");
		g_cache.evcShowPreviousFrame = field(g_cache.eventViewerConfig, "showPreviousFrameEvents", "Z");

		g_cache.viewerSizeCtor = env->GetMethodID(g_cache.viewerSize, "<init>", "()V");
		g_cache.vsWidth = field(g_cache.viewerSize, "width", "I");
		g_cache.vsHeight = field(g_cache.viewerSize, "height", "I");
		g_cache.traceRowCtor = env->GetMethodID(g_cache.traceRow, "<init>", "()V");
		g_cache.trProgramCounter = field(g_cache.traceRow, "programCounter", "I");
		g_cache.trType = field(g_cache.traceRow, "type", "I");
		g_cache.trByteCode = field(g_cache.traceRow, "byteCode", "[B");
		g_cache.trByteCodeSize = field(g_cache.traceRow, "byteCodeSize", "I");
		g_cache.trLogOutput = field(g_cache.traceRow, "logOutput", "Ljava/lang/String;");

		g_cache.traceLoggerOptionsCtor = env->GetMethodID(g_cache.traceLoggerOptions, "<init>", "()V");
		g_cache.tloEnabled = field(g_cache.traceLoggerOptions, "enabled", "Z");
		g_cache.tloIndentCode = field(g_cache.traceLoggerOptions, "indentCode", "Z");
		g_cache.tloUseLabels = field(g_cache.traceLoggerOptions, "useLabels", "Z");
		g_cache.tloCondition = field(g_cache.traceLoggerOptions, "condition", "Ljava/lang/String;");
		g_cache.tloFormat = field(g_cache.traceLoggerOptions, "format", "Ljava/lang/String;");

		g_cache.profiledFunctionCtor = env->GetMethodID(g_cache.profiledFunction, "<init>", "()V");
		g_cache.pfExclusiveCycles = field(g_cache.profiledFunction, "exclusiveCycles", "J");
		g_cache.pfInclusiveCycles = field(g_cache.profiledFunction, "inclusiveCycles", "J");
		g_cache.pfCallCount = field(g_cache.profiledFunction, "callCount", "J");
		g_cache.pfMinCycles = field(g_cache.profiledFunction, "minCycles", "J");
		g_cache.pfMaxCycles = field(g_cache.profiledFunction, "maxCycles", "J");
		g_cache.pfAddress = field(g_cache.profiledFunction, "address", "Lmesen/debugger/AddressInfo;");
		g_cache.pfFlags = field(g_cache.profiledFunction, "flags", "I");

		g_cache.debugControllerStateCtor = env->GetMethodID(g_cache.debugControllerState, "<init>", "()V");
		g_cache.dcsA = field(g_cache.debugControllerState, "A", "Z");
		g_cache.dcsB = field(g_cache.debugControllerState, "B", "Z");
		g_cache.dcsX = field(g_cache.debugControllerState, "X", "Z");
		g_cache.dcsY = field(g_cache.debugControllerState, "Y", "Z");
		g_cache.dcsL = field(g_cache.debugControllerState, "L", "Z");
		g_cache.dcsR = field(g_cache.debugControllerState, "R", "Z");
		g_cache.dcsU = field(g_cache.debugControllerState, "U", "Z");
		g_cache.dcsD = field(g_cache.debugControllerState, "D", "Z");
		g_cache.dcsUp = field(g_cache.debugControllerState, "Up", "Z");
		g_cache.dcsDown = field(g_cache.debugControllerState, "Down", "Z");
		g_cache.dcsLeft = field(g_cache.debugControllerState, "Left", "Z");
		g_cache.dcsRight = field(g_cache.debugControllerState, "Right", "Z");
		g_cache.dcsSelect = field(g_cache.debugControllerState, "Select", "Z");
		g_cache.dcsStart = field(g_cache.debugControllerState, "Start", "Z");

		// DebugConfig
		g_cache.dcBreakOnUninitRead = field(g_cache.debugConfig, "BreakOnUninitRead", "Z");
		g_cache.dcShowJumpLabels = field(g_cache.debugConfig, "ShowJumpLabels", "Z");
		g_cache.dcDrawPartialFrame = field(g_cache.debugConfig, "DrawPartialFrame", "Z");
		g_cache.dcShowVerifiedData = field(g_cache.debugConfig, "ShowVerifiedData", "Z");
		g_cache.dcDisassembleVerifiedData = field(g_cache.debugConfig, "DisassembleVerifiedData", "Z");
		g_cache.dcShowUnidentifiedData = field(g_cache.debugConfig, "ShowUnidentifiedData", "Z");
		g_cache.dcDisassembleUnidentifiedData = field(g_cache.debugConfig, "DisassembleUnidentifiedData", "Z");
		g_cache.dcUseLowerCaseDisassembly = field(g_cache.debugConfig, "UseLowerCaseDisassembly", "Z");
		g_cache.dcShowMemoryValues = field(g_cache.debugConfig, "ShowMemoryValues", "Z");
		g_cache.dcAutoResetCdl = field(g_cache.debugConfig, "AutoResetCdl", "Z");
		g_cache.dcUsePredictiveBreakpoints = field(g_cache.debugConfig, "UsePredictiveBreakpoints", "Z");
		g_cache.dcSingleBreakpointPerInstruction = field(g_cache.debugConfig, "SingleBreakpointPerInstruction", "Z");
		g_cache.dcSnesBreakOnBrk = field(g_cache.debugConfig, "SnesBreakOnBrk", "Z");
		g_cache.dcSnesBreakOnCop = field(g_cache.debugConfig, "SnesBreakOnCop", "Z");
		g_cache.dcSnesBreakOnWdm = field(g_cache.debugConfig, "SnesBreakOnWdm", "Z");
		g_cache.dcSnesBreakOnStp = field(g_cache.debugConfig, "SnesBreakOnStp", "Z");
		g_cache.dcSnesBreakOnInvalidPpuAccess = field(g_cache.debugConfig, "SnesBreakOnInvalidPpuAccess", "Z");
		g_cache.dcSnesBreakOnReadDuringAutoJoy = field(g_cache.debugConfig, "SnesBreakOnReadDuringAutoJoy", "Z");
		g_cache.dcSnesUseAltSpcOpNames = field(g_cache.debugConfig, "SnesUseAltSpcOpNames", "Z");
		g_cache.dcSnesIgnoreDspReadWrites = field(g_cache.debugConfig, "SnesIgnoreDspReadWrites", "Z");
		g_cache.dcSpcBreakOnBrk = field(g_cache.debugConfig, "SpcBreakOnBrk", "Z");
		g_cache.dcSpcBreakOnStpSleep = field(g_cache.debugConfig, "SpcBreakOnStpSleep", "Z");
		g_cache.dcGbBreakOnInvalidOamAccess = field(g_cache.debugConfig, "GbBreakOnInvalidOamAccess", "Z");
		g_cache.dcGbBreakOnInvalidVramAccess = field(g_cache.debugConfig, "GbBreakOnInvalidVramAccess", "Z");
		g_cache.dcGbBreakOnDisableLcdOutsideVblank = field(g_cache.debugConfig, "GbBreakOnDisableLcdOutsideVblank", "Z");
		g_cache.dcGbBreakOnInvalidOpCode = field(g_cache.debugConfig, "GbBreakOnInvalidOpCode", "Z");
		g_cache.dcGbBreakOnNopLoad = field(g_cache.debugConfig, "GbBreakOnNopLoad", "Z");
		g_cache.dcGbBreakOnOamCorruption = field(g_cache.debugConfig, "GbBreakOnOamCorruption", "Z");
		g_cache.dcNesBreakOnBrk = field(g_cache.debugConfig, "NesBreakOnBrk", "Z");
		g_cache.dcNesBreakOnUnofficialOpCode = field(g_cache.debugConfig, "NesBreakOnUnofficialOpCode", "Z");
		g_cache.dcNesBreakOnUnstableOpCode = field(g_cache.debugConfig, "NesBreakOnUnstableOpCode", "Z");
		g_cache.dcNesBreakOnCpuCrash = field(g_cache.debugConfig, "NesBreakOnCpuCrash", "Z");
		g_cache.dcNesBreakOnBusConflict = field(g_cache.debugConfig, "NesBreakOnBusConflict", "Z");
		g_cache.dcNesBreakOnDecayedOamRead = field(g_cache.debugConfig, "NesBreakOnDecayedOamRead", "Z");
		g_cache.dcNesBreakOnPpuScrollGlitch = field(g_cache.debugConfig, "NesBreakOnPpuScrollGlitch", "Z");
		g_cache.dcNesBreakOnExtOutputMode = field(g_cache.debugConfig, "NesBreakOnExtOutputMode", "Z");
		g_cache.dcNesBreakOnInvalidVramAccess = field(g_cache.debugConfig, "NesBreakOnInvalidVramAccess", "Z");
		g_cache.dcNesBreakOnInvalidOamWrite = field(g_cache.debugConfig, "NesBreakOnInvalidOamWrite", "Z");
		g_cache.dcNesBreakOnDmaInputRead = field(g_cache.debugConfig, "NesBreakOnDmaInputRead", "Z");
		g_cache.dcPceBreakOnBrk = field(g_cache.debugConfig, "PceBreakOnBrk", "Z");
		g_cache.dcPceBreakOnUnofficialOpCode = field(g_cache.debugConfig, "PceBreakOnUnofficialOpCode", "Z");
		g_cache.dcPceBreakOnInvalidVramAddress = field(g_cache.debugConfig, "PceBreakOnInvalidVramAddress", "Z");
		g_cache.dcSmsBreakOnNopLoad = field(g_cache.debugConfig, "SmsBreakOnNopLoad", "Z");
		g_cache.dcGbaBreakOnNopLoad = field(g_cache.debugConfig, "GbaBreakOnNopLoad", "Z");
		g_cache.dcGbaBreakOnInvalidOpCode = field(g_cache.debugConfig, "GbaBreakOnInvalidOpCode", "Z");
		g_cache.dcGbaBreakOnUnalignedMemAccess = field(g_cache.debugConfig, "GbaBreakOnUnalignedMemAccess", "Z");
		g_cache.dcGbaDisMode = field(g_cache.debugConfig, "GbaDisMode", "I");
		g_cache.dcWsBreakOnInvalidOpCode = field(g_cache.debugConfig, "WsBreakOnInvalidOpCode", "Z");
		g_cache.dcScriptAllowIoOsAccess = field(g_cache.debugConfig, "ScriptAllowIoOsAccess", "Z");
		g_cache.dcScriptAllowNetworkAccess = field(g_cache.debugConfig, "ScriptAllowNetworkAccess", "Z");
		g_cache.dcScriptTimeout = field(g_cache.debugConfig, "ScriptTimeout", "I");

		if(env->ExceptionCheck()) {
			return false;
		}

		g_cache.ready = true;
		return true;
	}

	// -----------------------------------------------------------------------
	// Marshalling helpers
	// -----------------------------------------------------------------------

	jobject NewAddressInfo(JNIEnv* env, const AddressInfo& info)
	{
		jobject obj = env->NewObject(g_cache.addressInfo, g_cache.addressInfoCtor);
		env->SetIntField(obj, g_cache.aiAddress, (jint)info.Address);
		env->SetIntField(obj, g_cache.aiMemoryType, (jint)info.Type);
		return obj;
	}

	AddressInfo ReadAddressInfo(JNIEnv* env, jobject obj)
	{
		AddressInfo info = {};
		if(obj) {
			info.Address = env->GetIntField(obj, g_cache.aiAddress);
			info.Type = (MemoryType)env->GetIntField(obj, g_cache.aiMemoryType);
		}
		return info;
	}

	jobject NewCodeLine(JNIEnv* env, const CodeLineData& line)
	{
		jobject obj = env->NewObject(g_cache.codeLine, g_cache.codeLineCtor);

		env->SetIntField(obj, g_cache.clAddress, (jint)line.Address);
		env->SetIntField(obj, g_cache.clOpSize, (jint)line.OpSize);
		env->SetIntField(obj, g_cache.clFlags, (jint)line.Flags);
		env->SetIntField(obj, g_cache.clValue, (jint)line.Value);
		env->SetIntField(obj, g_cache.clLineCpuType, (jint)line.LineCpuType);

		jobject abs = NewAddressInfo(env, line.AbsoluteAddress);
		env->SetObjectField(obj, g_cache.clAbsAddress, abs);
		env->DeleteLocalRef(abs);

		jobject effective = env->NewObject(g_cache.effectiveAddress, g_cache.effectiveAddressCtor);
		env->SetLongField(effective, g_cache.eaAddress, (jlong)line.EffectiveAddress.Address);
		env->SetIntField(effective, g_cache.eaMemoryType, (jint)line.EffectiveAddress.Type);
		env->SetIntField(effective, g_cache.eaValueSize, (jint)line.EffectiveAddress.ValueSize);
		env->SetBooleanField(effective, g_cache.eaShowAddress, line.EffectiveAddress.ShowAddress ? JNI_TRUE : JNI_FALSE);
		env->SetObjectField(obj, g_cache.clEffectiveAddress, effective);
		env->DeleteLocalRef(effective);

		jbyteArray bytes = env->NewByteArray(8);
		env->SetByteArrayRegion(bytes, 0, 8, (const jbyte*)line.ByteCode);
		env->SetObjectField(obj, g_cache.clByteCode, bytes);
		env->DeleteLocalRef(bytes);

		jstring text = env->NewStringUTF(line.Text);
		env->SetObjectField(obj, g_cache.clText, text);
		env->DeleteLocalRef(text);

		jstring comment = env->NewStringUTF(line.Comment);
		env->SetObjectField(obj, g_cache.clComment, comment);
		env->DeleteLocalRef(comment);

		return obj;
	}

	jobjectArray NewCodeLineArray(JNIEnv* env, const CodeLineData* lines, uint32_t count)
	{
		jobjectArray result = env->NewObjectArray((jsize)count, g_cache.codeLine, nullptr);
		for(uint32_t i = 0; i < count; i++) {
			jobject line = NewCodeLine(env, lines[i]);
			env->SetObjectArrayElement(result, (jsize)i, line);
			env->DeleteLocalRef(line);
		}
		return result;
	}

	jobject NewStackFrame(JNIEnv* env, const StackFrameInfo& frame)
	{
		jobject obj = env->NewObject(g_cache.stackFrame, g_cache.stackFrameCtor);

		env->SetIntField(obj, g_cache.sfSource, (jint)frame.Source);
		env->SetIntField(obj, g_cache.sfTarget, (jint)frame.Target);
		env->SetIntField(obj, g_cache.sfReturn, (jint)frame.Return);
		env->SetIntField(obj, g_cache.sfReturnStackPointer, (jint)frame.ReturnStackPointer);
		env->SetIntField(obj, g_cache.sfFlags, (jint)frame.Flags);

		jobject absSource = NewAddressInfo(env, frame.AbsSource);
		env->SetObjectField(obj, g_cache.sfAbsSource, absSource);
		env->DeleteLocalRef(absSource);

		jobject absTarget = NewAddressInfo(env, frame.AbsTarget);
		env->SetObjectField(obj, g_cache.sfAbsTarget, absTarget);
		env->DeleteLocalRef(absTarget);

		jobject absReturn = NewAddressInfo(env, frame.AbsReturn);
		env->SetObjectField(obj, g_cache.sfAbsReturn, absReturn);
		env->DeleteLocalRef(absReturn);

		return obj;
	}

	DisassemblySearchOptions MakeSearchOptions(bool matchCase, bool matchWholeWord, bool searchBackwards)
	{
		DisassemblySearchOptions options = {};
		options.MatchCase = matchCase;
		options.MatchWholeWord = matchWholeWord;
		options.SearchBackwards = searchBackwards;
		options.SkipFirstLine = false;
		return options;
	}

	// --- NES register state ------------------------------------------------

	jobject NewNesCpuState(JNIEnv* env, const NesCpuState& state)
	{
		jobject obj = env->NewObject(g_cache.nesCpuState, g_cache.nesCpuStateCtor);
		env->SetLongField(obj, g_cache.ncCycleCount, (jlong)state.CycleCount);
		env->SetIntField(obj, g_cache.ncPc, (jint)state.PC);
		env->SetIntField(obj, g_cache.ncSp, (jint)state.SP);
		env->SetIntField(obj, g_cache.ncA, (jint)state.A);
		env->SetIntField(obj, g_cache.ncX, (jint)state.X);
		env->SetIntField(obj, g_cache.ncY, (jint)state.Y);
		env->SetIntField(obj, g_cache.ncPs, (jint)state.PS);
		env->SetIntField(obj, g_cache.ncIrqFlag, (jint)state.IrqFlag);
		env->SetBooleanField(obj, g_cache.ncNmiFlag, state.NmiFlag ? JNI_TRUE : JNI_FALSE);
		return obj;
	}

	void ReadNesCpuState(JNIEnv* env, jobject obj, NesCpuState& state)
	{
		state.CycleCount = (uint64_t)env->GetLongField(obj, g_cache.ncCycleCount);
		state.PC = (uint16_t)env->GetIntField(obj, g_cache.ncPc);
		state.SP = (uint8_t)env->GetIntField(obj, g_cache.ncSp);
		state.A = (uint8_t)env->GetIntField(obj, g_cache.ncA);
		state.X = (uint8_t)env->GetIntField(obj, g_cache.ncX);
		state.Y = (uint8_t)env->GetIntField(obj, g_cache.ncY);
		state.PS = (uint8_t)env->GetIntField(obj, g_cache.ncPs);
		state.IrqFlag = (uint8_t)env->GetIntField(obj, g_cache.ncIrqFlag);
		state.NmiFlag = env->GetBooleanField(obj, g_cache.ncNmiFlag) == JNI_TRUE;
	}

	jobject NewNesPpuState(JNIEnv* env, const NesPpuState& state)
	{
		jobject obj = env->NewObject(g_cache.nesPpuState, g_cache.nesPpuStateCtor);

		env->SetBooleanField(obj, g_cache.npSpriteOverflow, state.StatusFlags.SpriteOverflow ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npSprite0Hit, state.StatusFlags.Sprite0Hit ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npVerticalBlank, state.StatusFlags.VerticalBlank ? JNI_TRUE : JNI_FALSE);

		env->SetBooleanField(obj, g_cache.npGrayscale, state.Mask.Grayscale ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npBackgroundMask, state.Mask.BackgroundMask ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npSpriteMask, state.Mask.SpriteMask ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npBackgroundEnabled, state.Mask.BackgroundEnabled ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npSpritesEnabled, state.Mask.SpritesEnabled ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npIntensifyRed, state.Mask.IntensifyRed ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npIntensifyGreen, state.Mask.IntensifyGreen ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npIntensifyBlue, state.Mask.IntensifyBlue ? JNI_TRUE : JNI_FALSE);

		env->SetIntField(obj, g_cache.npBackgroundPatternAddr, (jint)state.Control.BackgroundPatternAddr);
		env->SetIntField(obj, g_cache.npSpritePatternAddr, (jint)state.Control.SpritePatternAddr);
		env->SetBooleanField(obj, g_cache.npVerticalWrite, state.Control.VerticalWrite ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npLargeSprites, state.Control.LargeSprites ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npSecondaryPpu, state.Control.SecondaryPpu ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.npNmiOnVerticalBlank, state.Control.NmiOnVerticalBlank ? JNI_TRUE : JNI_FALSE);

		env->SetIntField(obj, g_cache.npScanline, (jint)state.Scanline);
		env->SetIntField(obj, g_cache.npCycle, (jint)state.Cycle);
		env->SetIntField(obj, g_cache.npFrameCount, (jint)state.FrameCount);
		env->SetIntField(obj, g_cache.npNmiScanline, (jint)state.NmiScanline);
		env->SetIntField(obj, g_cache.npScanlineCount, (jint)state.ScanlineCount);
		env->SetIntField(obj, g_cache.npSafeOamScanline, (jint)state.SafeOamScanline);
		env->SetIntField(obj, g_cache.npBusAddress, (jint)state.BusAddress);
		env->SetIntField(obj, g_cache.npMemoryReadBuffer, (jint)state.MemoryReadBuffer);
		env->SetIntField(obj, g_cache.npVideoRamAddr, (jint)state.VideoRamAddr);
		env->SetIntField(obj, g_cache.npTmpVideoRamAddr, (jint)state.TmpVideoRamAddr);
		env->SetIntField(obj, g_cache.npScrollX, (jint)state.ScrollX);
		env->SetBooleanField(obj, g_cache.npWriteToggle, state.WriteToggle ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, g_cache.npSpriteRamAddr, (jint)state.SpriteRamAddr);
		env->SetIntField(obj, g_cache.npSecondaryOamAddr, (jint)state.SecondaryOamAddr);
		return obj;
	}

	void ReadNesPpuState(JNIEnv* env, jobject obj, NesPpuState& state)
	{
		state.StatusFlags.SpriteOverflow = env->GetBooleanField(obj, g_cache.npSpriteOverflow) == JNI_TRUE;
		state.StatusFlags.Sprite0Hit = env->GetBooleanField(obj, g_cache.npSprite0Hit) == JNI_TRUE;
		state.StatusFlags.VerticalBlank = env->GetBooleanField(obj, g_cache.npVerticalBlank) == JNI_TRUE;

		state.Mask.Grayscale = env->GetBooleanField(obj, g_cache.npGrayscale) == JNI_TRUE;
		state.Mask.BackgroundMask = env->GetBooleanField(obj, g_cache.npBackgroundMask) == JNI_TRUE;
		state.Mask.SpriteMask = env->GetBooleanField(obj, g_cache.npSpriteMask) == JNI_TRUE;
		state.Mask.BackgroundEnabled = env->GetBooleanField(obj, g_cache.npBackgroundEnabled) == JNI_TRUE;
		state.Mask.SpritesEnabled = env->GetBooleanField(obj, g_cache.npSpritesEnabled) == JNI_TRUE;
		state.Mask.IntensifyRed = env->GetBooleanField(obj, g_cache.npIntensifyRed) == JNI_TRUE;
		state.Mask.IntensifyGreen = env->GetBooleanField(obj, g_cache.npIntensifyGreen) == JNI_TRUE;
		state.Mask.IntensifyBlue = env->GetBooleanField(obj, g_cache.npIntensifyBlue) == JNI_TRUE;

		state.Control.BackgroundPatternAddr = (uint16_t)env->GetIntField(obj, g_cache.npBackgroundPatternAddr);
		state.Control.SpritePatternAddr = (uint16_t)env->GetIntField(obj, g_cache.npSpritePatternAddr);
		state.Control.VerticalWrite = env->GetBooleanField(obj, g_cache.npVerticalWrite) == JNI_TRUE;
		state.Control.LargeSprites = env->GetBooleanField(obj, g_cache.npLargeSprites) == JNI_TRUE;
		state.Control.SecondaryPpu = env->GetBooleanField(obj, g_cache.npSecondaryPpu) == JNI_TRUE;
		state.Control.NmiOnVerticalBlank = env->GetBooleanField(obj, g_cache.npNmiOnVerticalBlank) == JNI_TRUE;

		state.Scanline = (int32_t)env->GetIntField(obj, g_cache.npScanline);
		state.Cycle = (uint32_t)env->GetIntField(obj, g_cache.npCycle);
		state.FrameCount = (uint32_t)env->GetIntField(obj, g_cache.npFrameCount);
		state.NmiScanline = (uint32_t)env->GetIntField(obj, g_cache.npNmiScanline);
		state.ScanlineCount = (uint32_t)env->GetIntField(obj, g_cache.npScanlineCount);
		state.SafeOamScanline = (uint32_t)env->GetIntField(obj, g_cache.npSafeOamScanline);
		state.BusAddress = (uint16_t)env->GetIntField(obj, g_cache.npBusAddress);
		state.MemoryReadBuffer = (uint8_t)env->GetIntField(obj, g_cache.npMemoryReadBuffer);
		state.VideoRamAddr = (uint16_t)env->GetIntField(obj, g_cache.npVideoRamAddr);
		state.TmpVideoRamAddr = (uint16_t)env->GetIntField(obj, g_cache.npTmpVideoRamAddr);
		state.ScrollX = (uint8_t)env->GetIntField(obj, g_cache.npScrollX);
		state.WriteToggle = env->GetBooleanField(obj, g_cache.npWriteToggle) == JNI_TRUE;
		state.SpriteRamAddr = (uint8_t)env->GetIntField(obj, g_cache.npSpriteRamAddr);
		state.SecondaryOamAddr = (uint8_t)env->GetIntField(obj, g_cache.npSecondaryOamAddr);
	}
}

// ---------------------------------------------------------------------------
// Debugger lifecycle
// ---------------------------------------------------------------------------

extern "C"
{
	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_initialize(JNIEnv*, jclass)
	{
		InitializeDebugger();
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_release(JNIEnv*, jclass)
	{
		ReleaseDebugger();
	}

	JNIEXPORT jboolean JNICALL Java_mesen_MesenDebugger_isDebuggerRunning(JNIEnv*, jclass)
	{
		return IsDebuggerRunning() ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT jboolean JNICALL Java_mesen_MesenDebugger_isExecutionStopped(JNIEnv*, jclass)
	{
		return IsExecutionStopped() ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_resumeExecution(JNIEnv*, jclass)
	{
		ResumeExecution();
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_step(JNIEnv*, jclass, jint cpuType, jint count, jint stepType)
	{
		Step((CpuType)cpuType, (uint32_t)count, (StepType)stepType);
	}

	// -----------------------------------------------------------------------
	// Disassembly
	// -----------------------------------------------------------------------

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getDisassemblyOutput(
		JNIEnv* env, jclass, jint cpuType, jint address, jint rowCount)
	{
		if(!InitCache(env) || rowCount <= 0) {
			return nullptr;
		}

		std::vector<CodeLineData> buffer((size_t)rowCount);
		uint32_t count = GetDisassemblyOutput((CpuType)cpuType, (uint32_t)address,
			buffer.data(), (uint32_t)rowCount);
		if(count > (uint32_t)rowCount) {
			count = (uint32_t)rowCount;
		}
		return NewCodeLineArray(env, buffer.data(), count);
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_getDisassemblyRowAddress(
		JNIEnv*, jclass, jint cpuType, jint address, jint rowOffset)
	{
		return (jint)GetDisassemblyRowAddress((CpuType)cpuType, (uint32_t)address, (int32_t)rowOffset);
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_searchDisassembly(
		JNIEnv* env, jclass, jint cpuType, jstring text, jint startPosition,
		jboolean matchCase, jboolean matchWholeWord, jboolean searchBackwards)
	{
		if(!text) {
			return -1;
		}
		const char* chars = env->GetStringUTFChars(text, nullptr);
		int32_t result = SearchDisassembly((CpuType)cpuType, chars, (int32_t)startPosition,
			MakeSearchOptions(matchCase, matchWholeWord, searchBackwards));
		env->ReleaseStringUTFChars(text, chars);
		return (jint)result;
	}

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_findOccurrences(
		JNIEnv* env, jclass, jint cpuType, jstring text,
		jboolean matchCase, jboolean matchWholeWord, jboolean searchBackwards, jint maxResults)
	{
		if(!InitCache(env) || !text) {
			return nullptr;
		}

		uint32_t capacity = FIND_OCCURRENCES_CAPACITY;
		if(maxResults > 0 && (uint32_t)maxResults < capacity) {
			capacity = (uint32_t)maxResults;
		}

		std::vector<CodeLineData> buffer(capacity);
		const char* chars = env->GetStringUTFChars(text, nullptr);
		uint32_t count = FindOccurrences((CpuType)cpuType, chars,
			MakeSearchOptions(matchCase, matchWholeWord, searchBackwards),
			buffer.data(), capacity);
		env->ReleaseStringUTFChars(text, chars);

		if(count > capacity) {
			count = capacity;
		}
		return NewCodeLineArray(env, buffer.data(), count);
	}

	// -----------------------------------------------------------------------
	// Breakpoints
	// -----------------------------------------------------------------------

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setBreakpoints(JNIEnv* env, jclass, jobjectArray breakpoints)
	{
		if(!InitCache(env)) {
			return;
		}

		jsize length = breakpoints ? env->GetArrayLength(breakpoints) : 0;
		std::vector<InteropBreakpoint> mirror((size_t)length);
		memset(mirror.data(), 0, mirror.size() * sizeof(InteropBreakpoint));

		for(jsize i = 0; i < length; i++) {
			jobject bp = env->GetObjectArrayElement(breakpoints, i);
			if(!bp) {
				continue;
			}

			InteropBreakpoint& out = mirror[(size_t)i];
			out.Id = env->GetIntField(bp, g_cache.bpId);
			out.CpuTypeValue = (CpuType)env->GetIntField(bp, g_cache.bpCpuType);
			out.MemoryTypeValue = (MemoryType)env->GetIntField(bp, g_cache.bpMemoryType);
			out.Type = (BreakpointTypeFlags)env->GetIntField(bp, g_cache.bpType);
			out.StartAddress = env->GetIntField(bp, g_cache.bpStartAddress);
			out.EndAddress = env->GetIntField(bp, g_cache.bpEndAddress);
			out.Enabled = env->GetBooleanField(bp, g_cache.bpEnabled) == JNI_TRUE;
			out.MarkEvent = env->GetBooleanField(bp, g_cache.bpMarkEvent) == JNI_TRUE;
			out.IgnoreDummyOperations = env->GetBooleanField(bp, g_cache.bpIgnoreDummy) == JNI_TRUE;

			jstring condition = (jstring)env->GetObjectField(bp, g_cache.bpCondition);
			if(condition) {
				const char* chars = env->GetStringUTFChars(condition, nullptr);
				if(chars) {
					strncpy(out.Condition, chars, sizeof(out.Condition) - 1);
					env->ReleaseStringUTFChars(condition, chars);
				}
				env->DeleteLocalRef(condition);
			}

			env->DeleteLocalRef(bp);
		}

		// InteropBreakpoint mirrors Breakpoint's layout, so a straight copy gives
		// the core a valid Breakpoint array.
		std::vector<Breakpoint> converted((size_t)length);
		if(length > 0) {
			memcpy(converted.data(), mirror.data(), (size_t)length * sizeof(Breakpoint));
		}

		SetBreakpoints(converted.data(), (uint32_t)length);
	}

	// -----------------------------------------------------------------------
	// Expressions
	// -----------------------------------------------------------------------

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_evaluateExpression(
		JNIEnv* env, jclass, jstring expression, jint cpuType, jboolean useCache)
	{
		if(!InitCache(env) || !expression) {
			return nullptr;
		}

		const char* chars = env->GetStringUTFChars(expression, nullptr);
		EvalResultType resultType = EvalResultType::Numeric;
		int64_t value = EvaluateExpression(chars, (CpuType)cpuType, &resultType, useCache == JNI_TRUE);
		env->ReleaseStringUTFChars(expression, chars);

		jobject result = env->NewObject(g_cache.evalResult, g_cache.evalResultCtor);
		env->SetLongField(result, g_cache.erValue, (jlong)value);
		env->SetIntField(result, g_cache.erType, (jint)resultType);
		return result;
	}

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getTokenList(JNIEnv* env, jclass, jint cpuType)
	{
		char buffer[TOKEN_LIST_SIZE] = {};
		GetTokenList((CpuType)cpuType, buffer);

		std::vector<std::string> tokens;
		std::string current;
		for(size_t i = 0; i < sizeof(buffer) && buffer[i] != '\0'; i++) {
			if(buffer[i] == '|') {
				if(!current.empty()) {
					tokens.push_back(current);
					current.clear();
				}
			} else {
				current.push_back(buffer[i]);
			}
		}
		if(!current.empty()) {
			tokens.push_back(current);
		}

		jclass stringClass = env->FindClass("java/lang/String");
		jobjectArray result = env->NewObjectArray((jsize)tokens.size(), stringClass, nullptr);
		for(size_t i = 0; i < tokens.size(); i++) {
			jstring token = env->NewStringUTF(tokens[i].c_str());
			env->SetObjectArrayElement(result, (jsize)i, token);
			env->DeleteLocalRef(token);
		}
		env->DeleteLocalRef(stringClass);
		return result;
	}

	// -----------------------------------------------------------------------
	// Callstack / PC / features
	// -----------------------------------------------------------------------

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getCallstack(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		std::vector<StackFrameInfo> buffer(CALLSTACK_CAPACITY);
		uint32_t count = 0;
		GetCallstack((CpuType)cpuType, buffer.data(), count);
		if(count > CALLSTACK_CAPACITY) {
			count = CALLSTACK_CAPACITY;
		}

		jobjectArray result = env->NewObjectArray((jsize)count, g_cache.stackFrame, nullptr);
		for(uint32_t i = 0; i < count; i++) {
			jobject frame = NewStackFrame(env, buffer[i]);
			env->SetObjectArrayElement(result, (jsize)i, frame);
			env->DeleteLocalRef(frame);
		}
		return result;
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_getProgramCounter(
		JNIEnv*, jclass, jint cpuType, jboolean getInstructionPc)
	{
		return (jint)GetProgramCounter((CpuType)cpuType, getInstructionPc == JNI_TRUE);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setProgramCounter(JNIEnv*, jclass, jint cpuType, jint address)
	{
		SetProgramCounter((CpuType)cpuType, (uint32_t)address);
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getDebuggerFeatures(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		DebuggerFeatures features = GetDebuggerFeatures((CpuType)cpuType);

		jobject obj = env->NewObject(g_cache.debuggerFeatures, g_cache.debuggerFeaturesCtor);
		env->SetBooleanField(obj, g_cache.dfRunToIrq, features.RunToIrq ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfRunToNmi, features.RunToNmi ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfStepOver, features.StepOver ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfStepOut, features.StepOut ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfStepBack, features.StepBack ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfChangePc, features.ChangeProgramCounter ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfCallStack, features.CallStack ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.dfCpuCycleStep, features.CpuCycleStep ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, g_cache.dfIrqVectorOffset, (jint)features.IrqVectorOffset);
		env->SetIntField(obj, g_cache.dfCpuVectorCount, (jint)features.CpuVectorCount);

		uint32_t vectorCount = features.CpuVectorCount;
		if(vectorCount > 16) {
			vectorCount = 16;
		}
		jobjectArray vectors = env->NewObjectArray((jsize)vectorCount, g_cache.cpuVector, nullptr);
		for(uint32_t i = 0; i < vectorCount; i++) {
			jobject vector = env->NewObject(g_cache.cpuVector, g_cache.cpuVectorCtor);
			jstring name = env->NewStringUTF(features.CpuVectors[i].Name);
			env->SetObjectField(vector, g_cache.cvName, name);
			env->DeleteLocalRef(name);
			env->SetIntField(vector, g_cache.cvAddress, (jint)features.CpuVectors[i].Address);
			env->SetIntField(vector, g_cache.cvType, (jint)features.CpuVectors[i].Type);
			env->SetObjectArrayElement(vectors, (jsize)i, vector);
			env->DeleteLocalRef(vector);
		}
		env->SetObjectField(obj, g_cache.dfCpuVectors, vectors);
		env->DeleteLocalRef(vectors);

		return obj;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getInstructionProgress(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		CpuInstructionProgress progress = GetInstructionProgress((CpuType)cpuType);

		jobject obj = env->NewObject(g_cache.instructionProgress, g_cache.instructionProgressCtor);
		env->SetLongField(obj, g_cache.ipStartCycle, (jlong)progress.StartCycle);
		env->SetLongField(obj, g_cache.ipCurrentCycle, (jlong)progress.CurrentCycle);
		env->SetIntField(obj, g_cache.ipLastOpCode, (jint)progress.LastOpCode);

		jobject last = env->NewObject(g_cache.memoryOperation, g_cache.memoryOperationCtor);
		env->SetIntField(last, g_cache.moAddress, (jint)progress.LastMemOperation.Address);
		env->SetIntField(last, g_cache.moValue, (jint)progress.LastMemOperation.Value);
		env->SetIntField(last, g_cache.moOperationType, (jint)progress.LastMemOperation.Type);
		env->SetIntField(last, g_cache.moMemoryType, (jint)progress.LastMemOperation.MemType);
		env->SetObjectField(obj, g_cache.ipLastMemOperation, last);
		env->DeleteLocalRef(last);

		return obj;
	}

	JNIEXPORT jstring JNICALL Java_mesen_MesenDebugger_getDebuggerLog(JNIEnv* env, jclass)
	{
		std::vector<char> buffer(DEBUGGER_LOG_SIZE);
		GetDebuggerLog(buffer.data(), (uint32_t)buffer.size());
		buffer.back() = '\0';
		return env->NewStringUTF(buffer.data());
	}

	// -----------------------------------------------------------------------
	// Frozen addresses
	// -----------------------------------------------------------------------

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_updateFrozenAddresses(
		JNIEnv*, jclass, jint cpuType, jint start, jint end, jboolean freeze)
	{
		UpdateFrozenAddresses((CpuType)cpuType, (uint32_t)start, (uint32_t)end, freeze == JNI_TRUE);
	}

	JNIEXPORT jbooleanArray JNICALL Java_mesen_MesenDebugger_getFrozenState(
		JNIEnv* env, jclass, jint cpuType, jint start, jint end)
	{
		int32_t length = end - start + 1;
		if(length <= 0) {
			return env->NewBooleanArray(0);
		}

		std::vector<uint8_t> raw((size_t)length);
		GetFrozenState((CpuType)cpuType, (uint32_t)start, (uint32_t)end, (bool*)raw.data());

		jbooleanArray result = env->NewBooleanArray((jsize)length);
		std::vector<jboolean> converted((size_t)length);
		for(int32_t i = 0; i < length; i++) {
			converted[(size_t)i] = raw[(size_t)i] ? JNI_TRUE : JNI_FALSE;
		}
		env->SetBooleanArrayRegion(result, 0, (jsize)length, converted.data());
		return result;
	}

	// -----------------------------------------------------------------------
	// Labels
	// -----------------------------------------------------------------------

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setLabel(
		JNIEnv* env, jclass, jint address, jint memoryType, jstring label, jstring comment)
	{
		std::string labelText;
		std::string commentText;

		if(label) {
			const char* chars = env->GetStringUTFChars(label, nullptr);
			labelText = chars ? chars : "";
			if(chars) {
				env->ReleaseStringUTFChars(label, chars);
			}
		}
		if(comment) {
			const char* chars = env->GetStringUTFChars(comment, nullptr);
			commentText = chars ? chars : "";
			if(chars) {
				env->ReleaseStringUTFChars(comment, chars);
			}
		}

		SetLabel((uint32_t)address, (MemoryType)memoryType,
			const_cast<char*>(labelText.c_str()), const_cast<char*>(commentText.c_str()));
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_clearLabels(JNIEnv*, jclass)
	{
		ClearLabels();
	}

	// -----------------------------------------------------------------------
	// Undo / address conversion
	// -----------------------------------------------------------------------

	JNIEXPORT jboolean JNICALL Java_mesen_MesenDebugger_hasUndoHistory(JNIEnv*, jclass)
	{
		return HasUndoHistory() ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_performUndo(JNIEnv*, jclass)
	{
		PerformUndo();
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getAbsoluteAddress(JNIEnv* env, jclass, jobject relative)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		AddressInfo result = GetAbsoluteAddress(ReadAddressInfo(env, relative));
		return NewAddressInfo(env, result);
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getRelativeAddress(
		JNIEnv* env, jclass, jobject absolute, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		AddressInfo result = GetRelativeAddress(ReadAddressInfo(env, absolute), (CpuType)cpuType);
		return NewAddressInfo(env, result);
	}

	// -----------------------------------------------------------------------
	// NES register state
	// -----------------------------------------------------------------------

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getNesCpuState(JNIEnv* env, jclass)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		NesCpuState state = {};
		GetCpuState(static_cast<BaseState&>(state), CpuType::Nes);
		return NewNesCpuState(env, state);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setNesCpuState(JNIEnv* env, jclass, jobject obj)
	{
		if(!InitCache(env) || !obj) {
			return;
		}

		NesCpuState state = {};
		ReadNesCpuState(env, obj, state);
		SetCpuState(static_cast<BaseState&>(state), CpuType::Nes);
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getNesPpuState(JNIEnv* env, jclass)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		NesPpuState state = {};
		GetPpuState(static_cast<BaseState&>(state), CpuType::Nes);
		return NewNesPpuState(env, state);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setNesPpuState(JNIEnv* env, jclass, jobject obj)
	{
		if(!InitCache(env) || !obj) {
			return;
		}

		NesPpuState state = {};
		ReadNesPpuState(env, obj, state);
		SetPpuState(static_cast<BaseState&>(state), CpuType::Nes);
	}

	// -----------------------------------------------------------------------
	// PPU tools (NES)
	// -----------------------------------------------------------------------

	// Snapshots everything the PPU tools need for a given CPU: register state,
	// tool state, VRAM, sprite RAM and the RGB palette. Callers stay free of
	// that plumbing, and the state types never surface in Java.
	struct PpuContext
	{
		// State objects are opaque to Java, so one union per kind is enough.
		union StateStorage
		{
			SnesPpuState snes;
			GbPpuState gameboy;
			NesPpuState nes;
			PceVideoState pce;
			SmsVdpState sms;
			GbaPpuState gba;
			WsPpuState ws;
		} state = {};

		union ToolsStateStorage
		{
			SnesPpuToolsState snes;
			NesPpuToolsState nes;
			uint64_t padding[64];
		} toolsState = {};

		std::vector<uint8_t> vram;
		std::vector<uint8_t> spriteRam;
		std::vector<uint32_t> palette;
		bool loaded = false;

		// VRAM / sprite RAM memory types per console (same mapping the .NET
		// interop uses in CpuTypeExtensions).
		static MemoryType GetVramMemoryType(CpuType type)
		{
			switch(type) {
				case CpuType::Snes:
				case CpuType::Spc:
				case CpuType::NecDsp:
				case CpuType::Sa1:
				case CpuType::Gsu:
				case CpuType::Cx4:
				case CpuType::St018: return MemoryType::SnesVideoRam;
				case CpuType::Gameboy: return MemoryType::GbVideoRam;
				case CpuType::Nes: return MemoryType::NesPpuMemory;
				case CpuType::Pce: return MemoryType::PceVideoRam;
				case CpuType::Sms: return MemoryType::SmsVideoRam;
				case CpuType::Gba: return MemoryType::GbaVideoRam;
				case CpuType::Ws: return MemoryType::WsWorkRam;
				default: return MemoryType::None;
			}
		}

		static MemoryType GetSpriteRamMemoryType(CpuType type)
		{
			switch(type) {
				case CpuType::Snes:
				case CpuType::Spc:
				case CpuType::NecDsp:
				case CpuType::Sa1:
				case CpuType::Gsu:
				case CpuType::Cx4:
				case CpuType::St018: return MemoryType::SnesSpriteRam;
				case CpuType::Gameboy: return MemoryType::GbSpriteRam;
				case CpuType::Nes: return MemoryType::NesSpriteRam;
				case CpuType::Pce: return MemoryType::PceSpriteRam;
				case CpuType::Gba: return MemoryType::GbaSpriteRam;
				default: return MemoryType::None;   // SMS / WonderSwan have none
			}
		}

		static void ReadMemory(MemoryType type, std::vector<uint8_t>& out)
		{
			uint32_t size = type == MemoryType::None ? 0 : GetMemorySize(type);
			out.assign(size ? size : 1, 0);
			if(size > 0) {
				GetMemoryState(type, out.data());
			}
		}

		void Load(CpuType cpuType, TileFormat paletteFormat = TileFormat::Bpp2)
		{
			// Core hazard: Debugger::GetPpuState()/GetPpuToolsState() dereference the
			// per-CPU debugger without a null check, so calling them for a console
			// that isn't loaded is an access violation. The VRAM memory type only
			// exists once that console is running, so use it as the guard.
			loaded = false;
			MemoryType vramType = GetVramMemoryType(cpuType);
			if(vramType == MemoryType::None || GetMemorySize(vramType) == 0) {
				return;
			}

			// Only SNES and NES implement GetPpuToolsState; the base is a no-op.
			GetPpuState(StateRef(), cpuType);
			GetPpuToolsState(cpuType, *reinterpret_cast<BaseState*>(&toolsState));

			ReadMemory(vramType, vram);
			ReadMemory(GetSpriteRamMemoryType(cpuType), spriteRam);

			GetPaletteInfoOptions options = {};
			options.Format = paletteFormat;
			DebugPaletteInfo info = GetPaletteInfo(cpuType, options);
			palette.assign(info.RgbPalette, info.RgbPalette + 512);
			loaded = true;
		}

		// Hand the core a reference to the state object matching this CPU type.
		BaseState& StateRef()
		{
			return *reinterpret_cast<BaseState*>(&state);
		}

		BaseState& ToolsStateRef()
		{
			return *reinterpret_cast<BaseState*>(&toolsState);
		}
	};

	// Kept for backward compatibility with the NES-only code paths.
	using NesPpuContext = PpuContext;

	jintArray NewIntArrayFromUint32(JNIEnv* env, const std::vector<uint32_t>& values)
	{
		jintArray array = env->NewIntArray((jsize)values.size());
		if(!values.empty()) {
			env->SetIntArrayRegion(array, 0, (jsize)values.size(), (const jint*)values.data());
		}
		return array;
	}

	jobject NewTilemapInfo(JNIEnv* env, const DebugTilemapInfo& info)
	{
		jobject obj = env->NewObject(g_cache.tilemapInfo, g_cache.tilemapInfoCtor);
		env->SetIntField(obj, g_cache.tiBpp, (jint)info.Bpp);
		env->SetIntField(obj, g_cache.tiFormat, (jint)info.Format);
		env->SetIntField(obj, g_cache.tiMirroring, (jint)info.Mirroring);
		env->SetIntField(obj, g_cache.tiTileWidth, (jint)info.TileWidth);
		env->SetIntField(obj, g_cache.tiTileHeight, (jint)info.TileHeight);
		env->SetIntField(obj, g_cache.tiScrollX, (jint)info.ScrollX);
		env->SetIntField(obj, g_cache.tiScrollWidth, (jint)info.ScrollWidth);
		env->SetIntField(obj, g_cache.tiScrollY, (jint)info.ScrollY);
		env->SetIntField(obj, g_cache.tiScrollHeight, (jint)info.ScrollHeight);
		env->SetIntField(obj, g_cache.tiRowCount, (jint)info.RowCount);
		env->SetIntField(obj, g_cache.tiColumnCount, (jint)info.ColumnCount);
		env->SetIntField(obj, g_cache.tiTilemapAddress, (jint)info.TilemapAddress);
		env->SetIntField(obj, g_cache.tiTilesetAddress, (jint)info.TilesetAddress);
		env->SetIntField(obj, g_cache.tiPriority, (jint)info.Priority);
		return obj;
	}

	jobject NewSpritePreviewInfo(JNIEnv* env, const DebugSpritePreviewInfo& info)
	{
		jobject obj = env->NewObject(g_cache.spritePreviewInfo, g_cache.spritePreviewInfoCtor);
		env->SetIntField(obj, g_cache.spiWidth, (jint)info.Width);
		env->SetIntField(obj, g_cache.spiHeight, (jint)info.Height);
		env->SetIntField(obj, g_cache.spiSpriteCount, (jint)info.SpriteCount);
		env->SetIntField(obj, g_cache.spiCoordOffsetX, (jint)info.CoordOffsetX);
		env->SetIntField(obj, g_cache.spiCoordOffsetY, (jint)info.CoordOffsetY);
		env->SetIntField(obj, g_cache.spiVisibleX, (jint)info.VisibleX);
		env->SetIntField(obj, g_cache.spiVisibleY, (jint)info.VisibleY);
		env->SetIntField(obj, g_cache.spiVisibleWidth, (jint)info.VisibleWidth);
		env->SetIntField(obj, g_cache.spiVisibleHeight, (jint)info.VisibleHeight);
		env->SetBooleanField(obj, g_cache.spiWrapBottomToTop, info.WrapBottomToTop ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.spiWrapRightToLeft, info.WrapRightToLeft ? JNI_TRUE : JNI_FALSE);
		return obj;
	}

	jobject NewSpriteInfo(JNIEnv* env, const DebugSpriteInfo& sprite)
	{
		jobject obj = env->NewObject(g_cache.spriteInfo, g_cache.spriteInfoCtor);
		env->SetIntField(obj, g_cache.siTileIndex, (jint)sprite.TileIndex);
		env->SetIntField(obj, g_cache.siTileAddress, (jint)sprite.TileAddress);
		env->SetIntField(obj, g_cache.siPaletteAddress, (jint)sprite.PaletteAddress);
		env->SetIntField(obj, g_cache.siFormat, (jint)sprite.Format);
		env->SetIntField(obj, g_cache.siSpriteIndex, (jint)sprite.SpriteIndex);
		env->SetIntField(obj, g_cache.siX, (jint)sprite.X);
		env->SetIntField(obj, g_cache.siY, (jint)sprite.Y);
		env->SetIntField(obj, g_cache.siRawX, (jint)sprite.RawX);
		env->SetIntField(obj, g_cache.siRawY, (jint)sprite.RawY);
		env->SetIntField(obj, g_cache.siBpp, (jint)sprite.Bpp);
		env->SetIntField(obj, g_cache.siPalette, (jint)sprite.Palette);
		env->SetIntField(obj, g_cache.siPriority, (jint)sprite.Priority);
		env->SetIntField(obj, g_cache.siMode, (jint)sprite.Mode);
		env->SetIntField(obj, g_cache.siWidth, (jint)sprite.Width);
		env->SetIntField(obj, g_cache.siHeight, (jint)sprite.Height);
		env->SetIntField(obj, g_cache.siHorizontalMirror, (jint)sprite.HorizontalMirror);
		env->SetIntField(obj, g_cache.siVerticalMirror, (jint)sprite.VerticalMirror);
		env->SetIntField(obj, g_cache.siMosaicEnabled, (jint)sprite.MosaicEnabled);
		env->SetIntField(obj, g_cache.siTransformEnabled, (jint)sprite.TransformEnabled);
		env->SetIntField(obj, g_cache.siDoubleSize, (jint)sprite.DoubleSize);
		env->SetIntField(obj, g_cache.siUseSecondTable, (jint)sprite.UseSecondTable);
		env->SetIntField(obj, g_cache.siTransformParamIndex, (jint)sprite.TransformParamIndex);
		env->SetIntField(obj, g_cache.siVisibility, (jint)sprite.Visibility);
		env->SetBooleanField(obj, g_cache.siUseExtendedVram, sprite.UseExtendedVram ? JNI_TRUE : JNI_FALSE);

		uint32_t tileCount = sprite.TileCount;
		if(tileCount > 8 * 8) {
			tileCount = 8 * 8;
		}
		jintArray addresses = env->NewIntArray((jsize)tileCount);
		if(tileCount > 0) {
			std::vector<jint> converted(tileCount);
			for(uint32_t i = 0; i < tileCount; i++) {
				converted[i] = (jint)sprite.TileAddresses[i];
			}
			env->SetIntArrayRegion(addresses, 0, (jsize)tileCount, converted.data());
		}
		env->SetObjectField(obj, g_cache.siTileAddresses, addresses);
		env->DeleteLocalRef(addresses);
		return obj;
	}

	GetTilemapOptions ReadTilemapOptions(JNIEnv* env, jobject obj)
	{
		GetTilemapOptions options = {};
		if(!obj) {
			return options;
		}
		options.Layer = (uint8_t)env->GetIntField(obj, g_cache.tmoLayer);
		options.MasterClock = (uint64_t)env->GetLongField(obj, g_cache.tmoMasterClock);
		options.TileHighlightMode = (TilemapHighlightMode)env->GetIntField(obj, g_cache.tmoTileHighlight);
		options.AttributeHighlightMode = (TilemapHighlightMode)env->GetIntField(obj, g_cache.tmoAttrHighlight);
		options.DisplayMode = (TilemapDisplayMode)env->GetIntField(obj, g_cache.tmoDisplayMode);
		options.Background = (TilemapBackground)env->GetIntField(obj, g_cache.tmoBackground);
		return options;
	}

	GetSpritePreviewOptions ReadSpriteOptions(JNIEnv* env, jobject obj)
	{
		GetSpritePreviewOptions options = {};
		if(!obj) {
			return options;
		}
		options.Background = (SpriteBackground)env->GetIntField(obj, g_cache.soBackground);
		options.Filter = (SpriteViewerFilter)env->GetIntField(obj, g_cache.soFilter);
		return options;
	}

	// -----------------------------------------------------------------------
	// PPU tools: JNI entry points
	// -----------------------------------------------------------------------

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getTileView(JNIEnv* env, jclass, jint cpuType, jobject optionsObj)
	{
		if(!InitCache(env) || !optionsObj) {
			return nullptr;
		}

		GetTileViewOptions options = {};
		options.MemType = (MemoryType)env->GetIntField(optionsObj, g_cache.tvoMemType);
		options.Format = (TileFormat)env->GetIntField(optionsObj, g_cache.tvoFormat);
		options.Layout = (TileLayout)env->GetIntField(optionsObj, g_cache.tvoLayout);
		options.Filter = (TileFilter)env->GetIntField(optionsObj, g_cache.tvoFilter);
		options.Background = (TileBackground)env->GetIntField(optionsObj, g_cache.tvoBackground);
		options.Width = env->GetIntField(optionsObj, g_cache.tvoWidth);
		options.Height = env->GetIntField(optionsObj, g_cache.tvoHeight);
		options.StartAddress = env->GetIntField(optionsObj, g_cache.tvoStartAddress);
		options.Palette = env->GetIntField(optionsObj, g_cache.tvoPalette);
		options.UseGrayscalePalette = env->GetBooleanField(optionsObj, g_cache.tvoGrayscale) == JNI_TRUE;

		if(options.Width <= 0 || options.Height <= 0) {
			return nullptr;
		}

		uint32_t srcSize = GetMemorySize(options.MemType);
		std::vector<uint8_t> source(srcSize ? srcSize : 1, 0);
		if(srcSize > 0) {
			GetMemoryState(options.MemType, source.data());
		}

		GetPaletteInfoOptions paletteOptions = {};
		paletteOptions.Format = options.Format;
		DebugPaletteInfo palette = GetPaletteInfo((CpuType)cpuType, paletteOptions);

		// Every tile is 8x8 for the formats bound here (the PCE 16x16 sprite
		// formats halve Width/Height internally, so the pixel size is the same).
		int pixelWidth = options.Width * 8;
		int pixelHeight = options.Height * 8;
		std::vector<uint32_t> buffer((size_t)pixelWidth * pixelHeight, 0);

		GetTileView((CpuType)cpuType, options, source.data(), srcSize, palette.RgbPalette, buffer.data());

		jobject result = env->NewObject(g_cache.tileView, g_cache.tileViewCtor);
		env->SetIntField(result, g_cache.tvWidth, pixelWidth);
		env->SetIntField(result, g_cache.tvHeight, pixelHeight);
		jintArray pixels = NewIntArrayFromUint32(env, buffer);
		env->SetObjectField(result, g_cache.tvPixels, pixels);
		env->DeleteLocalRef(pixels);
		return result;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getTilemap(JNIEnv* env, jclass, jint cpuType, jobject optionsObj)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		GetTilemapOptions options = ReadTilemapOptions(env, optionsObj);

		PpuContext context;
		context.Load((CpuType)cpuType);
		if(!context.loaded) {
			return nullptr;
		}

		FrameInfo size = GetTilemapSize((CpuType)cpuType, options, context.StateRef());
		if(size.Width == 0 || size.Height == 0) {
			return nullptr;
		}

		std::vector<uint32_t> buffer((size_t)size.Width * size.Height, 0);
		DebugTilemapInfo info = GetTilemap((CpuType)cpuType, options, context.StateRef(), context.ToolsStateRef(),
			context.vram.data(), context.palette.data(), buffer.data());

		jobject result = env->NewObject(g_cache.tilemapResult, g_cache.tilemapResultCtor);
		jobject infoObj = NewTilemapInfo(env, info);
		env->SetObjectField(result, g_cache.trInfo, infoObj);
		env->DeleteLocalRef(infoObj);
		env->SetIntField(result, g_cache.trWidth, (jint)size.Width);
		env->SetIntField(result, g_cache.trHeight, (jint)size.Height);
		jintArray pixels = NewIntArrayFromUint32(env, buffer);
		env->SetObjectField(result, g_cache.trPixels, pixels);
		env->DeleteLocalRef(pixels);
		return result;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getTilemapTileInfo(
		JNIEnv* env, jclass, jint cpuType, jint x, jint y, jobject optionsObj)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		GetTilemapOptions options = ReadTilemapOptions(env, optionsObj);

		PpuContext context;
		context.Load((CpuType)cpuType);
		if(!context.loaded) {
			return nullptr;
		}

		DebugTilemapTileInfo info = GetTilemapTileInfo((uint32_t)x, (uint32_t)y, (CpuType)cpuType,
			options, context.vram.data(), context.StateRef(), context.ToolsStateRef());

		jobject obj = env->NewObject(g_cache.tilemapTileInfo, g_cache.tilemapTileInfoCtor);
		env->SetIntField(obj, g_cache.ttiRow, (jint)info.Row);
		env->SetIntField(obj, g_cache.ttiColumn, (jint)info.Column);
		env->SetIntField(obj, g_cache.ttiWidth, (jint)info.Width);
		env->SetIntField(obj, g_cache.ttiHeight, (jint)info.Height);
		env->SetIntField(obj, g_cache.ttiTileMapAddress, (jint)info.TileMapAddress);
		env->SetIntField(obj, g_cache.ttiTileIndex, (jint)info.TileIndex);
		env->SetIntField(obj, g_cache.ttiPixelData, (jint)info.PixelData);
		env->SetIntField(obj, g_cache.ttiPaletteIndex, (jint)info.PaletteIndex);
		env->SetIntField(obj, g_cache.ttiPaletteAddress, (jint)info.PaletteAddress);
		env->SetIntField(obj, g_cache.ttiBasePaletteIndex, (jint)info.BasePaletteIndex);
		env->SetIntField(obj, g_cache.ttiAttributeAddress, (jint)info.AttributeAddress);
		env->SetIntField(obj, g_cache.ttiAttributeData, (jint)info.AttributeData);
		env->SetIntField(obj, g_cache.ttiHorizontalMirroring, (jint)info.HorizontalMirroring);
		env->SetIntField(obj, g_cache.ttiVerticalMirroring, (jint)info.VerticalMirroring);
		env->SetIntField(obj, g_cache.ttiHighPriority, (jint)info.HighPriority);

		uint32_t tileCount = info.TileCount;
		if(tileCount > 4) {
			tileCount = 4;
		}
		jintArray addresses = env->NewIntArray((jsize)tileCount);
		if(tileCount > 0) {
			std::vector<jint> converted(tileCount);
			for(uint32_t i = 0; i < tileCount; i++) {
				converted[i] = (jint)info.TileAddresses[i];
			}
			env->SetIntArrayRegion(addresses, 0, (jsize)tileCount, converted.data());
		}
		env->SetObjectField(obj, g_cache.ttiTileAddresses, addresses);
		env->DeleteLocalRef(addresses);
		return obj;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getSpritePreviewInfo(JNIEnv* env, jclass, jint cpuType, jobject optionsObj)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		GetSpritePreviewOptions options = ReadSpriteOptions(env, optionsObj);

		PpuContext context;
		context.Load((CpuType)cpuType);
		if(!context.loaded) {
			return nullptr;
		}

		DebugSpritePreviewInfo info = GetSpritePreviewInfo((CpuType)cpuType, options,
			context.StateRef(), context.ToolsStateRef());
		return NewSpritePreviewInfo(env, info);
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getSpriteList(JNIEnv* env, jclass, jint cpuType, jobject optionsObj)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		GetSpritePreviewOptions options = ReadSpriteOptions(env, optionsObj);

		PpuContext context;
		context.Load((CpuType)cpuType);
		if(!context.loaded) {
			return nullptr;
		}

		DebugSpritePreviewInfo info = GetSpritePreviewInfo((CpuType)cpuType, options,
			context.StateRef(), context.ToolsStateRef());

		uint32_t spriteCount = info.SpriteCount;
		uint32_t previewSize = (uint32_t)info.Width * info.Height;

		std::vector<DebugSpriteInfo> sprites(spriteCount ? spriteCount : 1);
		std::vector<uint32_t> previews((size_t)(spriteCount ? spriteCount : 1) * previewSize, 0);
		std::vector<uint32_t> screen(previewSize ? previewSize : 1, 0);

		// SMS/WonderSwan have no sprite RAM; the core expects null there.
		uint8_t* spriteRam = PpuContext::GetSpriteRamMemoryType((CpuType)cpuType) == MemoryType::None
			? nullptr : context.spriteRam.data();

		GetSpriteList((CpuType)cpuType, options, context.StateRef(), context.ToolsStateRef(),
			context.vram.data(), spriteRam, context.palette.data(),
			sprites.data(), previews.data(), screen.data());

		jobject result = env->NewObject(g_cache.spriteListResult, g_cache.spriteListResultCtor);

		jobject infoObj = NewSpritePreviewInfo(env, info);
		env->SetObjectField(result, g_cache.slrInfo, infoObj);
		env->DeleteLocalRef(infoObj);

		jobjectArray spriteArray = env->NewObjectArray((jsize)spriteCount, g_cache.spriteInfo, nullptr);
		for(uint32_t i = 0; i < spriteCount; i++) {
			jobject sprite = NewSpriteInfo(env, sprites[i]);
			env->SetObjectArrayElement(spriteArray, (jsize)i, sprite);
			env->DeleteLocalRef(sprite);
		}
		env->SetObjectField(result, g_cache.slrSprites, spriteArray);
		env->DeleteLocalRef(spriteArray);

		std::vector<uint32_t> previewSlice(previews.begin(), previews.begin() + (size_t)spriteCount * previewSize);
		jintArray previewArray = NewIntArrayFromUint32(env, previewSlice);
		env->SetObjectField(result, g_cache.slrSpritePreviews, previewArray);
		env->DeleteLocalRef(previewArray);

		jintArray screenArray = NewIntArrayFromUint32(env, screen);
		env->SetObjectField(result, g_cache.slrScreenPreview, screenArray);
		env->DeleteLocalRef(screenArray);

		return result;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getPaletteInfo(JNIEnv* env, jclass, jint cpuType, jint tileFormat)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		GetPaletteInfoOptions options = {};
		options.Format = (TileFormat)tileFormat;
		DebugPaletteInfo info = GetPaletteInfo((CpuType)cpuType, options);

		uint32_t count = info.ColorCount;
		if(count > 512) {
			count = 512;
		}

		jobject obj = env->NewObject(g_cache.paletteInfo, g_cache.paletteInfoCtor);
		env->SetIntField(obj, g_cache.piPaletteMemType, (jint)info.PaletteMemType);
		env->SetIntField(obj, g_cache.piPaletteMemOffset, (jint)info.PaletteMemOffset);
		env->SetBooleanField(obj, g_cache.piHasMemType, info.HasMemType ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, g_cache.piColorCount, (jint)info.ColorCount);
		env->SetIntField(obj, g_cache.piBgColorCount, (jint)info.BgColorCount);
		env->SetIntField(obj, g_cache.piSpriteColorCount, (jint)info.SpriteColorCount);
		env->SetIntField(obj, g_cache.piSpritePaletteOffset, (jint)info.SpritePaletteOffset);
		env->SetIntField(obj, g_cache.piColorsPerPalette, (jint)info.ColorsPerPalette);
		env->SetIntField(obj, g_cache.piRawFormat, (jint)info.RawFormat);

		std::vector<uint32_t> raw(info.RawPalette, info.RawPalette + count);
		std::vector<uint32_t> rgb(info.RgbPalette, info.RgbPalette + count);

		jintArray rawArray = NewIntArrayFromUint32(env, raw);
		env->SetObjectField(obj, g_cache.piRawPalette, rawArray);
		env->DeleteLocalRef(rawArray);

		jintArray rgbArray = NewIntArrayFromUint32(env, rgb);
		env->SetObjectField(obj, g_cache.piRgbPalette, rgbArray);
		env->DeleteLocalRef(rgbArray);

		return obj;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setPaletteColor(
		JNIEnv*, jclass, jint cpuType, jint colorIndex, jint color)
	{
		SetPaletteColor((CpuType)cpuType, (int32_t)colorIndex, (uint32_t)color);
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_getTilePixel(
		JNIEnv* env, jclass, jobject addressObj, jint tileFormat, jint x, jint y)
	{
		if(!InitCache(env)) {
			return -1;
		}
		AddressInfo address = ReadAddressInfo(env, addressObj);
		return (jint)GetTilePixel(address, (TileFormat)tileFormat, (int32_t)x, (int32_t)y);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setTilePixel(
		JNIEnv* env, jclass, jobject addressObj, jint tileFormat, jint x, jint y, jint color)
	{
		if(!InitCache(env)) {
			return;
		}
		AddressInfo address = ReadAddressInfo(env, addressObj);
		SetTilePixel(address, (TileFormat)tileFormat, (int32_t)x, (int32_t)y, (int32_t)color);
	}

	// -----------------------------------------------------------------------
	// Assembly / ROM export
	// -----------------------------------------------------------------------

	JNIEXPORT jshortArray JNICALL Java_mesen_MesenDebugger_assembleCode(
		JNIEnv* env, jclass, jint cpuType, jstring code, jint startAddress)
	{
		if(!code) {
			return nullptr;
		}

		const char* chars = env->GetStringUTFChars(code, nullptr);
		if(!chars) {
			return nullptr;
		}

		// Same buffer convention the .NET interop uses.
		std::vector<int16_t> buffer(100000, 0);
		uint32_t count = AssembleCode((CpuType)cpuType, const_cast<char*>(chars),
			(uint32_t)startAddress, buffer.data());
		env->ReleaseStringUTFChars(code, chars);

		if(count > buffer.size()) {
			count = (uint32_t)buffer.size();
		}

		jshortArray result = env->NewShortArray((jsize)count);
		if(count > 0) {
			env->SetShortArrayRegion(result, 0, (jsize)count, buffer.data());
		}
		return result;
	}

	JNIEXPORT jbyteArray JNICALL Java_mesen_MesenDebugger_getRomHeader(JNIEnv* env, jclass)
	{
		uint32_t size = 0x1000;
		std::vector<uint8_t> buffer(size, 0);
		GetRomHeader(buffer.data(), size);

		if(size > buffer.size()) {
			size = (uint32_t)buffer.size();
		}

		jbyteArray result = env->NewByteArray((jsize)size);
		if(size > 0) {
			env->SetByteArrayRegion(result, 0, (jsize)size, (const jbyte*)buffer.data());
		}
		return result;
	}

	JNIEXPORT jboolean JNICALL Java_mesen_MesenDebugger_saveRomToDisk(
		JNIEnv* env, jclass, jstring filename, jboolean saveAsIps, jint cdlStripOption)
	{
		if(!filename) {
			return JNI_FALSE;
		}

		const char* chars = env->GetStringUTFChars(filename, nullptr);
		if(!chars) {
			return JNI_FALSE;
		}

		bool result = SaveRomToDisk(const_cast<char*>(chars), saveAsIps == JNI_TRUE,
			(CdlStripOption)cdlStripOption);
		env->ReleaseStringUTFChars(filename, chars);
		return result ? JNI_TRUE : JNI_FALSE;
	}

	// Java DebugConfig -> Core DebugConfig (field names match the struct).
	void ReadDebugConfig(JNIEnv* env, jobject obj, DebugConfig& config)
	{
		config.BreakOnUninitRead = env->GetBooleanField(obj, g_cache.dcBreakOnUninitRead) == JNI_TRUE;
		config.ShowJumpLabels = env->GetBooleanField(obj, g_cache.dcShowJumpLabels) == JNI_TRUE;
		config.DrawPartialFrame = env->GetBooleanField(obj, g_cache.dcDrawPartialFrame) == JNI_TRUE;
		config.ShowVerifiedData = env->GetBooleanField(obj, g_cache.dcShowVerifiedData) == JNI_TRUE;
		config.DisassembleVerifiedData = env->GetBooleanField(obj, g_cache.dcDisassembleVerifiedData) == JNI_TRUE;
		config.ShowUnidentifiedData = env->GetBooleanField(obj, g_cache.dcShowUnidentifiedData) == JNI_TRUE;
		config.DisassembleUnidentifiedData = env->GetBooleanField(obj, g_cache.dcDisassembleUnidentifiedData) == JNI_TRUE;
		config.UseLowerCaseDisassembly = env->GetBooleanField(obj, g_cache.dcUseLowerCaseDisassembly) == JNI_TRUE;
		config.ShowMemoryValues = env->GetBooleanField(obj, g_cache.dcShowMemoryValues) == JNI_TRUE;
		config.AutoResetCdl = env->GetBooleanField(obj, g_cache.dcAutoResetCdl) == JNI_TRUE;
		config.UsePredictiveBreakpoints = env->GetBooleanField(obj, g_cache.dcUsePredictiveBreakpoints) == JNI_TRUE;
		config.SingleBreakpointPerInstruction = env->GetBooleanField(obj, g_cache.dcSingleBreakpointPerInstruction) == JNI_TRUE;
		config.SnesBreakOnBrk = env->GetBooleanField(obj, g_cache.dcSnesBreakOnBrk) == JNI_TRUE;
		config.SnesBreakOnCop = env->GetBooleanField(obj, g_cache.dcSnesBreakOnCop) == JNI_TRUE;
		config.SnesBreakOnWdm = env->GetBooleanField(obj, g_cache.dcSnesBreakOnWdm) == JNI_TRUE;
		config.SnesBreakOnStp = env->GetBooleanField(obj, g_cache.dcSnesBreakOnStp) == JNI_TRUE;
		config.SnesBreakOnInvalidPpuAccess = env->GetBooleanField(obj, g_cache.dcSnesBreakOnInvalidPpuAccess) == JNI_TRUE;
		config.SnesBreakOnReadDuringAutoJoy = env->GetBooleanField(obj, g_cache.dcSnesBreakOnReadDuringAutoJoy) == JNI_TRUE;
		config.SnesUseAltSpcOpNames = env->GetBooleanField(obj, g_cache.dcSnesUseAltSpcOpNames) == JNI_TRUE;
		config.SnesIgnoreDspReadWrites = env->GetBooleanField(obj, g_cache.dcSnesIgnoreDspReadWrites) == JNI_TRUE;
		config.SpcBreakOnBrk = env->GetBooleanField(obj, g_cache.dcSpcBreakOnBrk) == JNI_TRUE;
		config.SpcBreakOnStpSleep = env->GetBooleanField(obj, g_cache.dcSpcBreakOnStpSleep) == JNI_TRUE;
		config.GbBreakOnInvalidOamAccess = env->GetBooleanField(obj, g_cache.dcGbBreakOnInvalidOamAccess) == JNI_TRUE;
		config.GbBreakOnInvalidVramAccess = env->GetBooleanField(obj, g_cache.dcGbBreakOnInvalidVramAccess) == JNI_TRUE;
		config.GbBreakOnDisableLcdOutsideVblank = env->GetBooleanField(obj, g_cache.dcGbBreakOnDisableLcdOutsideVblank) == JNI_TRUE;
		config.GbBreakOnInvalidOpCode = env->GetBooleanField(obj, g_cache.dcGbBreakOnInvalidOpCode) == JNI_TRUE;
		config.GbBreakOnNopLoad = env->GetBooleanField(obj, g_cache.dcGbBreakOnNopLoad) == JNI_TRUE;
		config.GbBreakOnOamCorruption = env->GetBooleanField(obj, g_cache.dcGbBreakOnOamCorruption) == JNI_TRUE;
		config.NesBreakOnBrk = env->GetBooleanField(obj, g_cache.dcNesBreakOnBrk) == JNI_TRUE;
		config.NesBreakOnUnofficialOpCode = env->GetBooleanField(obj, g_cache.dcNesBreakOnUnofficialOpCode) == JNI_TRUE;
		config.NesBreakOnUnstableOpCode = env->GetBooleanField(obj, g_cache.dcNesBreakOnUnstableOpCode) == JNI_TRUE;
		config.NesBreakOnCpuCrash = env->GetBooleanField(obj, g_cache.dcNesBreakOnCpuCrash) == JNI_TRUE;
		config.NesBreakOnBusConflict = env->GetBooleanField(obj, g_cache.dcNesBreakOnBusConflict) == JNI_TRUE;
		config.NesBreakOnDecayedOamRead = env->GetBooleanField(obj, g_cache.dcNesBreakOnDecayedOamRead) == JNI_TRUE;
		config.NesBreakOnPpuScrollGlitch = env->GetBooleanField(obj, g_cache.dcNesBreakOnPpuScrollGlitch) == JNI_TRUE;
		config.NesBreakOnExtOutputMode = env->GetBooleanField(obj, g_cache.dcNesBreakOnExtOutputMode) == JNI_TRUE;
		config.NesBreakOnInvalidVramAccess = env->GetBooleanField(obj, g_cache.dcNesBreakOnInvalidVramAccess) == JNI_TRUE;
		config.NesBreakOnInvalidOamWrite = env->GetBooleanField(obj, g_cache.dcNesBreakOnInvalidOamWrite) == JNI_TRUE;
		config.NesBreakOnDmaInputRead = env->GetBooleanField(obj, g_cache.dcNesBreakOnDmaInputRead) == JNI_TRUE;
		config.PceBreakOnBrk = env->GetBooleanField(obj, g_cache.dcPceBreakOnBrk) == JNI_TRUE;
		config.PceBreakOnUnofficialOpCode = env->GetBooleanField(obj, g_cache.dcPceBreakOnUnofficialOpCode) == JNI_TRUE;
		config.PceBreakOnInvalidVramAddress = env->GetBooleanField(obj, g_cache.dcPceBreakOnInvalidVramAddress) == JNI_TRUE;
		config.SmsBreakOnNopLoad = env->GetBooleanField(obj, g_cache.dcSmsBreakOnNopLoad) == JNI_TRUE;
		config.GbaBreakOnNopLoad = env->GetBooleanField(obj, g_cache.dcGbaBreakOnNopLoad) == JNI_TRUE;
		config.GbaBreakOnInvalidOpCode = env->GetBooleanField(obj, g_cache.dcGbaBreakOnInvalidOpCode) == JNI_TRUE;
		config.GbaBreakOnUnalignedMemAccess = env->GetBooleanField(obj, g_cache.dcGbaBreakOnUnalignedMemAccess) == JNI_TRUE;
		config.GbaDisMode = (GbaDisassemblyMode)env->GetIntField(obj, g_cache.dcGbaDisMode);
		config.WsBreakOnInvalidOpCode = env->GetBooleanField(obj, g_cache.dcWsBreakOnInvalidOpCode) == JNI_TRUE;
		config.ScriptAllowIoOsAccess = env->GetBooleanField(obj, g_cache.dcScriptAllowIoOsAccess) == JNI_TRUE;
		config.ScriptAllowNetworkAccess = env->GetBooleanField(obj, g_cache.dcScriptAllowNetworkAccess) == JNI_TRUE;
		config.ScriptTimeout = (uint32_t)env->GetIntField(obj, g_cache.dcScriptTimeout);
	}

	// -----------------------------------------------------------------------
	// Execution trace / CDL files / profiler / input overrides / debug config
	// -----------------------------------------------------------------------

	void CopyStringField(JNIEnv* env, jobject obj, jfieldID fieldId, char* buffer, size_t bufferSize)
	{
		buffer[0] = 0;
		jstring str = (jstring)env->GetObjectField(obj, fieldId);
		if(!str) {
			return;
		}

		const char* chars = env->GetStringUTFChars(str, nullptr);
		if(chars) {
			strncpy(buffer, chars, bufferSize - 1);
			buffer[bufferSize - 1] = 0;
			env->ReleaseStringUTFChars(str, chars);
		}
		env->DeleteLocalRef(str);
	}

	jobject NewTraceRow(JNIEnv* env, const TraceRow& row)
	{
		jobject obj = env->NewObject(g_cache.traceRow, g_cache.traceRowCtor);
		env->SetIntField(obj, g_cache.trProgramCounter, (jint)row.ProgramCounter);
		env->SetIntField(obj, g_cache.trType, (jint)row.Type);
		env->SetIntField(obj, g_cache.trByteCodeSize, (jint)row.ByteCodeSize);

		jbyteArray byteCode = env->NewByteArray(8);
		env->SetByteArrayRegion(byteCode, 0, 8, (const jbyte*)row.ByteCode);
		env->SetObjectField(obj, g_cache.trByteCode, byteCode);
		env->DeleteLocalRef(byteCode);

		// LogOutput may not be null-terminated; LogSize says how much is valid.
		char text[501];
		uint32_t length = row.LogSize < 500 ? row.LogSize : 500;
		memcpy(text, row.LogOutput, length);
		text[length] = 0;
		jstring logOutput = env->NewStringUTF(text);
		env->SetObjectField(obj, g_cache.trLogOutput, logOutput);
		env->DeleteLocalRef(logOutput);
		return obj;
	}

	jobject NewProfiledFunction(JNIEnv* env, const ProfiledFunction& fn)
	{
		jobject obj = env->NewObject(g_cache.profiledFunction, g_cache.profiledFunctionCtor);
		env->SetLongField(obj, g_cache.pfExclusiveCycles, (jlong)fn.ExclusiveCycles);
		env->SetLongField(obj, g_cache.pfInclusiveCycles, (jlong)fn.InclusiveCycles);
		env->SetLongField(obj, g_cache.pfCallCount, (jlong)fn.CallCount);
		env->SetLongField(obj, g_cache.pfMinCycles, (jlong)fn.MinCycles);
		env->SetLongField(obj, g_cache.pfMaxCycles, (jlong)fn.MaxCycles);
		env->SetIntField(obj, g_cache.pfFlags, (jint)fn.Flags);

		jobject address = env->NewObject(g_cache.addressInfo, g_cache.addressInfoCtor);
		env->SetIntField(address, g_cache.aiAddress, (jint)fn.Address.Address);
		env->SetIntField(address, g_cache.aiMemoryType, (jint)fn.Address.Type);
		env->SetObjectField(obj, g_cache.pfAddress, address);
		env->DeleteLocalRef(address);
		return obj;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setTraceOptions(JNIEnv* env, jclass, jint cpuType, jobject optionsObj)
	{
		if(!InitCache(env) || !optionsObj) {
			return;
		}

		TraceLoggerOptions options = {};
		options.Enabled = env->GetBooleanField(optionsObj, g_cache.tloEnabled) == JNI_TRUE;
		options.IndentCode = env->GetBooleanField(optionsObj, g_cache.tloIndentCode) == JNI_TRUE;
		options.UseLabels = env->GetBooleanField(optionsObj, g_cache.tloUseLabels) == JNI_TRUE;
		CopyStringField(env, optionsObj, g_cache.tloCondition, options.Condition, sizeof(options.Condition));
		CopyStringField(env, optionsObj, g_cache.tloFormat, options.Format, sizeof(options.Format));

		SetTraceOptions((CpuType)cpuType, options);
	}

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getExecutionTrace(JNIEnv* env, jclass, jint startOffset, jint lineCount)
	{
		if(!InitCache(env)) {
			return nullptr;
		}
		if(lineCount <= 0) {
			return env->NewObjectArray(0, g_cache.traceRow, nullptr);
		}

		std::vector<TraceRow> rows((size_t)lineCount);
		uint32_t count = GetExecutionTrace(rows.data(), (uint32_t)startOffset, (uint32_t)lineCount);
		if(count > (uint32_t)lineCount) {
			count = (uint32_t)lineCount;
		}

		jobjectArray result = env->NewObjectArray((jsize)count, g_cache.traceRow, nullptr);
		for(uint32_t i = 0; i < count; i++) {
			jobject row = NewTraceRow(env, rows[i]);
			env->SetObjectArrayElement(result, (jsize)i, row);
			env->DeleteLocalRef(row);
		}
		return result;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_clearExecutionTrace(JNIEnv*, jclass)
	{
		ClearExecutionTrace();
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_startLogTraceToFile(JNIEnv* env, jclass, jstring filename)
	{
		if(!filename) {
			return;
		}

		const char* chars = env->GetStringUTFChars(filename, nullptr);
		if(chars) {
			StartLogTraceToFile(chars);
			env->ReleaseStringUTFChars(filename, chars);
		}
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_stopLogTraceToFile(JNIEnv*, jclass)
	{
		StopLogTraceToFile();
	}

	JNIEXPORT jbyteArray JNICALL Java_mesen_MesenDebugger_getCdlData(JNIEnv* env, jclass, jint memoryType, jint offset, jint length)
	{
		if(length <= 0) {
			return env->NewByteArray(0);
		}

		std::vector<uint8_t> data((size_t)length, 0);
		GetCdlData((uint32_t)offset, (uint32_t)length, (MemoryType)memoryType, data.data());

		jbyteArray result = env->NewByteArray(length);
		env->SetByteArrayRegion(result, 0, length, (const jbyte*)data.data());
		return result;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setCdlData(JNIEnv* env, jclass, jint memoryType, jbyteArray data)
	{
		if(!data) {
			return;
		}

		jsize length = env->GetArrayLength(data);
		if(length <= 0) {
			return;
		}

		std::vector<uint8_t> buffer((size_t)length, 0);
		env->GetByteArrayRegion(data, 0, length, (jbyte*)buffer.data());
		SetCdlData((MemoryType)memoryType, buffer.data(), (uint32_t)length);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_markBytesAs(JNIEnv*, jclass, jint memoryType, jint start, jint end, jint flags)
	{
		MarkBytesAs((MemoryType)memoryType, (uint32_t)start, (uint32_t)end, (uint8_t)flags);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_saveCdlFile(JNIEnv* env, jclass, jint memoryType, jstring filename)
	{
		if(!filename) {
			return;
		}

		const char* chars = env->GetStringUTFChars(filename, nullptr);
		if(chars) {
			SaveCdlFile((MemoryType)memoryType, const_cast<char*>(chars));
			env->ReleaseStringUTFChars(filename, chars);
		}
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_loadCdlFile(JNIEnv* env, jclass, jint memoryType, jstring filename)
	{
		if(!filename) {
			return;
		}

		const char* chars = env->GetStringUTFChars(filename, nullptr);
		if(chars) {
			LoadCdlFile((MemoryType)memoryType, const_cast<char*>(chars));
			env->ReleaseStringUTFChars(filename, chars);
		}
	}

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getProfilerData(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		// Profiler::GetProfilerData ignores the value passed in as a capacity and
		// writes up to 100000 entries, so the buffer must be that large.
		std::vector<ProfiledFunction> functions(100000);
		uint32_t count = 0;
		GetProfilerData((CpuType)cpuType, functions.data(), count);
		if(count > (uint32_t)functions.size()) {
			count = (uint32_t)functions.size();
		}

		jobjectArray result = env->NewObjectArray((jsize)count, g_cache.profiledFunction, nullptr);
		for(uint32_t i = 0; i < count; i++) {
			jobject fn = NewProfiledFunction(env, functions[i]);
			env->SetObjectArrayElement(result, (jsize)i, fn);
			env->DeleteLocalRef(fn);
		}
		return result;
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_getProfilerCpuUsage(JNIEnv*, jclass, jint cpuType)
	{
		return (jint)GetProfilerCpuUsage((CpuType)cpuType);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_resetProfiler(JNIEnv*, jclass, jint cpuType)
	{
		ResetProfiler((CpuType)cpuType);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setInputOverrides(JNIEnv* env, jclass, jint index, jobject stateObj)
	{
		if(!InitCache(env) || !stateObj) {
			return;
		}

		DebugControllerState state = {};
		state.A = env->GetBooleanField(stateObj, g_cache.dcsA) == JNI_TRUE;
		state.B = env->GetBooleanField(stateObj, g_cache.dcsB) == JNI_TRUE;
		state.X = env->GetBooleanField(stateObj, g_cache.dcsX) == JNI_TRUE;
		state.Y = env->GetBooleanField(stateObj, g_cache.dcsY) == JNI_TRUE;
		state.L = env->GetBooleanField(stateObj, g_cache.dcsL) == JNI_TRUE;
		state.R = env->GetBooleanField(stateObj, g_cache.dcsR) == JNI_TRUE;
		state.U = env->GetBooleanField(stateObj, g_cache.dcsU) == JNI_TRUE;
		state.D = env->GetBooleanField(stateObj, g_cache.dcsD) == JNI_TRUE;
		state.Up = env->GetBooleanField(stateObj, g_cache.dcsUp) == JNI_TRUE;
		state.Down = env->GetBooleanField(stateObj, g_cache.dcsDown) == JNI_TRUE;
		state.Left = env->GetBooleanField(stateObj, g_cache.dcsLeft) == JNI_TRUE;
		state.Right = env->GetBooleanField(stateObj, g_cache.dcsRight) == JNI_TRUE;
		state.Select = env->GetBooleanField(stateObj, g_cache.dcsSelect) == JNI_TRUE;
		state.Start = env->GetBooleanField(stateObj, g_cache.dcsStart) == JNI_TRUE;

		SetInputOverrides((uint32_t)index, state);
	}

	JNIEXPORT jbyteArray JNICALL Java_mesen_MesenDebugger_getAvailableInputOverrides(JNIEnv* env, jclass)
	{
		// Debugger::GetAvailableInputOverrides always fills 8 entries.
		std::vector<uint8_t> available(8, 0);
		GetAvailableInputOverrides(available.data());

		jbyteArray result = env->NewByteArray(8);
		env->SetByteArrayRegion(result, 0, 8, (const jbyte*)available.data());
		return result;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setDebugConfig(JNIEnv* env, jclass, jobject configObj)
	{
		if(!InitCache(env) || !configObj) {
			return;
		}

		DebugConfig config = {};
		ReadDebugConfig(env, configObj, config);
		SetDebugConfig(config);
	}
	// -----------------------------------------------------------------------
	// Event viewer
	//
	// The per-console config structs are a contiguous run of EventViewerCategoryCfg
	// starting at offset 0 (verified by the static_asserts below), followed by a
	// ShowPreviousFrameEvents flag. That lets one generic table-driven fill serve
	// every console instead of marshalling ~166 named fields by hand.
	// -----------------------------------------------------------------------

	using CategoryCfg = EventViewerCategoryCfg;

	static_assert(sizeof(CategoryCfg) == 8, "EventViewerCategoryCfg layout changed");
	static_assert(offsetof(NesEventViewerConfig, Irq) == 0, "NES event categories moved");
	static_assert(offsetof(SnesEventViewerConfig, Irq) == 0, "SNES event categories moved");
	static_assert(offsetof(GbEventViewerConfig, Irq) == 0, "GB event categories moved");
	static_assert(offsetof(GbaEventViewerConfig, Irq) == 0, "GBA event categories moved");
	static_assert(offsetof(PceEventViewerConfig, Irq) == 0, "PCE event categories moved");
	static_assert(offsetof(SmsEventViewerConfig, Irq) == 0, "SMS event categories moved");
	static_assert(offsetof(WsEventViewerConfig, PpuPaletteRead) == 0, "WS event categories moved");

	struct EventCategoryTable
	{
		uint32_t count;
		const char* const* names;
	};

	const char* const NES_EVENT_CATEGORIES[] = {
		"Irq", "Nmi", "MarkedBreakpoints",
		"MapperRegisterWrites", "MapperRegisterReads",
		"ApuRegisterWrites", "ApuRegisterReads",
		"ControlRegisterWrites", "ControlRegisterReads",
		"Ppu2000Write", "Ppu2001Write", "Ppu2003Write", "Ppu2004Write",
		"Ppu2005Write", "Ppu2006Write", "Ppu2007Write",
		"Ppu2002Read", "Ppu2004Read", "Ppu2007Read",
		"DmcDmaReads", "OtherDmaReads", "SpriteZeroHit"
	};

	const char* const SNES_EVENT_CATEGORIES[] = {
		"Irq", "Nmi", "MarkedBreakpoints",
		"PpuRegisterReads", "PpuRegisterCgramWrites", "PpuRegisterVramWrites",
		"PpuRegisterOamWrites", "PpuRegisterMode7Writes", "PpuRegisterBgOptionWrites",
		"PpuRegisterBgScrollWrites", "PpuRegisterWindowWrites", "PpuRegisterOtherWrites",
		"ApuRegisterReads", "ApuRegisterWrites",
		"CpuRegisterReads", "CpuRegisterWrites",
		"WorkRamRegisterReads", "WorkRamRegisterWrites"
	};

	const char* const GB_EVENT_CATEGORIES[] = {
		"Irq", "MarkedBreakpoints",
		"PpuRegisterCgramWrites", "PpuRegisterVramWrites", "PpuRegisterOamWrites",
		"PpuRegisterBgScrollWrites", "PpuRegisterWindowWrites", "PpuRegisterOtherWrites",
		"PpuRegisterCgramReads", "PpuRegisterVramReads", "PpuRegisterOamReads",
		"PpuRegisterBgScrollReads", "PpuRegisterWindowReads", "PpuRegisterOtherReads",
		"ApuRegisterReads", "ApuRegisterWrites",
		"SerialReads", "SerialWrites",
		"TimerReads", "TimerWrites",
		"InputReads", "InputWrites",
		"OtherRegisterReads", "OtherRegisterWrites"
	};

	const char* const GBA_EVENT_CATEGORIES[] = {
		"Irq", "MarkedBreakpoints",
		"PaletteReads", "PaletteWrites", "VramReads", "VramWrites", "OamReads", "OamWrites",
		"PpuRegisterBgScrollReads", "PpuRegisterBgScrollWrites",
		"PpuRegisterWindowReads", "PpuRegisterWindowWrites",
		"PpuRegisterOtherReads", "PpuRegisterOtherWrites",
		"DmaRegisterReads", "DmaRegisterWrites",
		"ApuRegisterReads", "ApuRegisterWrites",
		"SerialReads", "SerialWrites",
		"TimerReads", "TimerWrites",
		"InputReads", "InputWrites",
		"OtherRegisterReads", "OtherRegisterWrites"
	};

	const char* const PCE_EVENT_CATEGORIES[] = {
		"Irq", "MarkedBreakpoints",
		"VdcStatusReads", "VdcVramWrites", "VdcVramReads",
		"VdcRegSelectWrites", "VdcControlWrites", "VdcRcrWrites", "VdcHvConfigWrites",
		"VdcMemoryWidthWrites", "VdcScrollWrites", "VdcDmaWrites",
		"VceWrites", "VceReads",
		"PsgWrites", "PsgReads",
		"TimerWrites", "TimerReads",
		"IoWrites", "IoReads",
		"IrqControlWrites", "IrqControlReads",
		"CdRomWrites", "CdRomReads",
		"AdpcmWrites", "AdpcmReads",
		"ArcadeCardWrites", "ArcadeCardReads",
		"VpcWrites", "VpcReads"
	};

	const char* const SMS_EVENT_CATEGORIES[] = {
		"Irq", "Nmi", "MarkedBreakpoints",
		"VdpPaletteWrite", "VdpVramWrite",
		"VdpVCounterRead", "VdpHCounterRead", "VdpVramRead",
		"VdpControlPortRead", "VdpControlPortWrite",
		"PsgWrite", "IoWrite", "IoRead", "MemoryControlWrite",
		"GameGearPortWrite", "GameGearPortRead"
	};

	const char* const WS_EVENT_CATEGORIES[] = {
		"PpuPaletteRead", "PpuPaletteWrite", "PpuVramRead", "PpuVramWrite",
		"PpuVCounterRead", "PpuScrollRead", "PpuScrollWrite",
		"PpuWindowRead", "PpuWindowWrite", "PpuOtherRead", "PpuOtherWrite",
		"AudioRead", "AudioWrite", "SerialRead", "SerialWrite",
		"DmaRead", "DmaWrite", "InputRead", "InputWrite",
		"IrqRead", "IrqWrite", "TimerRead", "TimerWrite",
		"EepromRead", "EepromWrite", "CartRead", "CartWrite",
		"OtherRead", "OtherWrite", "Irq", "MarkedBreakpoints"
	};

	EventCategoryTable GetEventCategoryTable(CpuType type)
	{
		switch(type) {
			case CpuType::Snes:
			case CpuType::Spc:
			case CpuType::NecDsp:
			case CpuType::Sa1:
			case CpuType::Gsu:
			case CpuType::Cx4:
			case CpuType::St018:
				return { (uint32_t)(sizeof(SNES_EVENT_CATEGORIES) / sizeof(char*)), SNES_EVENT_CATEGORIES };
			case CpuType::Gameboy:
				return { (uint32_t)(sizeof(GB_EVENT_CATEGORIES) / sizeof(char*)), GB_EVENT_CATEGORIES };
			case CpuType::Nes:
				return { (uint32_t)(sizeof(NES_EVENT_CATEGORIES) / sizeof(char*)), NES_EVENT_CATEGORIES };
			case CpuType::Pce:
				return { (uint32_t)(sizeof(PCE_EVENT_CATEGORIES) / sizeof(char*)), PCE_EVENT_CATEGORIES };
			case CpuType::Sms:
				return { (uint32_t)(sizeof(SMS_EVENT_CATEGORIES) / sizeof(char*)), SMS_EVENT_CATEGORIES };
			case CpuType::Gba:
				return { (uint32_t)(sizeof(GBA_EVENT_CATEGORIES) / sizeof(char*)), GBA_EVENT_CATEGORIES };
			case CpuType::Ws:
				return { (uint32_t)(sizeof(WS_EVENT_CATEGORIES) / sizeof(char*)), WS_EVENT_CATEGORIES };
			default:
				return { 0, nullptr };
		}
	}

	union EventConfigStorage
	{
		NesEventViewerConfig nes;
		SnesEventViewerConfig snes;
		GbEventViewerConfig gb;
		GbaEventViewerConfig gba;
		PceEventViewerConfig pce;
		SmsEventViewerConfig sms;
		WsEventViewerConfig ws;
	};

	jobject NewMemoryOperation(JNIEnv* env, const MemoryOperationInfo& info)
	{
		jobject obj = env->NewObject(g_cache.memoryOperation, g_cache.memoryOperationCtor);
		env->SetIntField(obj, g_cache.moAddress, (jint)info.Address);
		env->SetIntField(obj, g_cache.moValue, (jint)info.Value);
		env->SetIntField(obj, g_cache.moOperationType, (jint)info.Type);
		env->SetIntField(obj, g_cache.moMemoryType, (jint)info.MemType);
		return obj;
	}

	jobject NewDebugEventInfo(JNIEnv* env, const DebugEventInfo& event)
	{
		jobject obj = env->NewObject(g_cache.debugEventInfo, g_cache.debugEventInfoCtor);

		jobject operation = NewMemoryOperation(env, event.Operation);
		env->SetObjectField(obj, g_cache.deiOperation, operation);
		env->DeleteLocalRef(operation);

		jobject target = NewMemoryOperation(env, event.TargetMemory);
		env->SetObjectField(obj, g_cache.deiTargetMemory, target);
		env->DeleteLocalRef(target);

		env->SetIntField(obj, g_cache.deiType, (jint)event.Type);
		env->SetIntField(obj, g_cache.deiProgramCounter, (jint)event.ProgramCounter);
		env->SetIntField(obj, g_cache.deiScanline, (jint)event.Scanline);
		env->SetIntField(obj, g_cache.deiCycle, (jint)event.Cycle);
		env->SetIntField(obj, g_cache.deiBreakpointId, (jint)event.BreakpointId);
		env->SetIntField(obj, g_cache.deiDmaChannel, (jint)event.DmaChannel);

		const DmaChannelConfig& dma = event.DmaChannelInfo;
		env->SetIntField(obj, g_cache.deiDmaSrcAddress, (jint)dma.SrcAddress);
		env->SetIntField(obj, g_cache.deiDmaTransferSize, (jint)dma.TransferSize);
		env->SetIntField(obj, g_cache.deiDmaHdmaTableAddress, (jint)dma.HdmaTableAddress);
		env->SetIntField(obj, g_cache.deiDmaSrcBank, (jint)dma.SrcBank);
		env->SetIntField(obj, g_cache.deiDmaDestAddress, (jint)dma.DestAddress);
		env->SetBooleanField(obj, g_cache.deiDmaActive, dma.DmaActive ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.deiDmaInvertDirection, dma.InvertDirection ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.deiDmaDecrement, dma.Decrement ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.deiDmaFixedTransfer, dma.FixedTransfer ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.deiDmaHdmaIndirectAddressing, dma.HdmaIndirectAddressing ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, g_cache.deiDmaTransferMode, (jint)dma.TransferMode);
		env->SetIntField(obj, g_cache.deiDmaHdmaBank, (jint)dma.HdmaBank);
		env->SetIntField(obj, g_cache.deiDmaHdmaLineCounter, (jint)dma.HdmaLineCounterAndRepeat);
		env->SetBooleanField(obj, g_cache.deiDmaDoTransfer, dma.DoTransfer ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, g_cache.deiDmaUnusedControlFlag, dma.UnusedControlFlag ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, g_cache.deiDmaUnusedRegister, (jint)dma.UnusedRegister);

		env->SetIntField(obj, g_cache.deiFlags, (jint)event.Flags);
		env->SetIntField(obj, g_cache.deiRegisterId, (jint)event.RegisterId);
		env->SetIntField(obj, g_cache.deiColor, (jint)event.Color);
		return obj;
	}

	// Fills the contiguous category run. ShowPreviousFrameEvents is set by the
	// caller (a plain function, not a template: this sits inside extern "C").
	void ApplyEventConfigCategories(void* config, const EventCategoryTable& table,
		const std::vector<jboolean>& visible, const std::vector<jint>& colors)
	{
		CategoryCfg* categories = reinterpret_cast<CategoryCfg*>(config);
		for(uint32_t i = 0; i < table.count; i++) {
			categories[i].Visible = i < visible.size() && visible[i] == JNI_TRUE;
			categories[i].Color = i < colors.size() ? (uint32_t)colors[i] : 0;
		}
	}

	// -----------------------------------------------------------------------
	// Event viewer: JNI entry points
	// -----------------------------------------------------------------------

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getEventViewerConfig(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		EventCategoryTable table = GetEventCategoryTable((CpuType)cpuType);
		jobject obj = env->NewObject(g_cache.eventViewerConfig, g_cache.eventViewerConfigCtor);
		if(table.count == 0) {
			return obj;
		}

		jclass stringClass = env->FindClass("java/lang/String");
		jobjectArray names = env->NewObjectArray((jsize)table.count, stringClass, nullptr);
		for(uint32_t i = 0; i < table.count; i++) {
			jstring name = env->NewStringUTF(table.names[i]);
			env->SetObjectArrayElement(names, (jsize)i, name);
			env->DeleteLocalRef(name);
		}
		env->DeleteLocalRef(stringClass);
		env->SetObjectField(obj, g_cache.evcCategoryNames, names);
		env->DeleteLocalRef(names);

		std::vector<jboolean> visible(table.count, JNI_FALSE);
		jbooleanArray visibleArray = env->NewBooleanArray((jsize)table.count);
		env->SetBooleanArrayRegion(visibleArray, 0, (jsize)table.count, visible.data());
		env->SetObjectField(obj, g_cache.evcVisible, visibleArray);
		env->DeleteLocalRef(visibleArray);

		std::vector<jint> colors(table.count, 0);
		jintArray colorArray = env->NewIntArray((jsize)table.count);
		env->SetIntArrayRegion(colorArray, 0, (jsize)table.count, colors.data());
		env->SetObjectField(obj, g_cache.evcColors, colorArray);
		env->DeleteLocalRef(colorArray);

		return obj;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setEventViewerConfig(
		JNIEnv* env, jclass, jint cpuType, jobject configObj)
	{
		if(!InitCache(env) || !configObj) {
			return;
		}

		CpuType type = (CpuType)cpuType;
		EventCategoryTable table = GetEventCategoryTable(type);
		if(table.count == 0) {
			return;
		}

		std::vector<jboolean> visible;
		jbooleanArray visibleArray = (jbooleanArray)env->GetObjectField(configObj, g_cache.evcVisible);
		if(visibleArray) {
			jsize length = env->GetArrayLength(visibleArray);
			visible.resize((size_t)length);
			if(length > 0) {
				env->GetBooleanArrayRegion(visibleArray, 0, length, visible.data());
			}
			env->DeleteLocalRef(visibleArray);
		}

		std::vector<jint> colors;
		jintArray colorArray = (jintArray)env->GetObjectField(configObj, g_cache.evcColors);
		if(colorArray) {
			jsize length = env->GetArrayLength(colorArray);
			colors.resize((size_t)length);
			if(length > 0) {
				env->GetIntArrayRegion(colorArray, 0, length, colors.data());
			}
			env->DeleteLocalRef(colorArray);
		}

		bool showPreviousFrame = env->GetBooleanField(configObj, g_cache.evcShowPreviousFrame) == JNI_TRUE;

		EventConfigStorage storage = {};
		switch(type) {
			case CpuType::Snes:
			case CpuType::Spc:
			case CpuType::NecDsp:
			case CpuType::Sa1:
			case CpuType::Gsu:
			case CpuType::Cx4:
			case CpuType::St018:
				ApplyEventConfigCategories(&storage.snes, table, visible, colors);
				storage.snes.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.snes);
				break;

			case CpuType::Gameboy:
				ApplyEventConfigCategories(&storage.gb, table, visible, colors);
				storage.gb.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.gb);
				break;

			case CpuType::Nes:
				ApplyEventConfigCategories(&storage.nes, table, visible, colors);
				storage.nes.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.nes);
				break;

			case CpuType::Pce:
				ApplyEventConfigCategories(&storage.pce, table, visible, colors);
				storage.pce.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.pce);
				break;

			case CpuType::Sms:
				ApplyEventConfigCategories(&storage.sms, table, visible, colors);
				storage.sms.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.sms);
				break;

			case CpuType::Gba:
				ApplyEventConfigCategories(&storage.gba, table, visible, colors);
				storage.gba.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.gba);
				break;

			case CpuType::Ws:
				ApplyEventConfigCategories(&storage.ws, table, visible, colors);
				storage.ws.ShowPreviousFrameEvents = showPreviousFrame;
				SetEventViewerConfig(type, storage.ws);
				break;
		}
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_getDebugEventCount(JNIEnv*, jclass, jint cpuType)
	{
		return (jint)GetDebugEventCount((CpuType)cpuType);
	}

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getDebugEvents(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		CpuType type = (CpuType)cpuType;
		uint32_t count = GetDebugEventCount(type);
		if(count == 0) {
			return env->NewObjectArray(0, g_cache.debugEventInfo, nullptr);
		}

		std::vector<DebugEventInfo> events(count);
		uint32_t capacity = count;
		GetDebugEvents(type, events.data(), capacity);
		if(capacity > count) {
			capacity = count;
		}

		jobjectArray result = env->NewObjectArray((jsize)capacity, g_cache.debugEventInfo, nullptr);
		for(uint32_t i = 0; i < capacity; i++) {
			jobject event = NewDebugEventInfo(env, events[i]);
			env->SetObjectArrayElement(result, (jsize)i, event);
			env->DeleteLocalRef(event);
		}
		return result;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getEventViewerDisplaySize(JNIEnv* env, jclass, jint cpuType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		FrameInfo size = GetEventViewerDisplaySize((CpuType)cpuType);
		jobject obj = env->NewObject(g_cache.viewerSize, g_cache.viewerSizeCtor);
		env->SetIntField(obj, g_cache.vsWidth, (jint)size.Width);
		env->SetIntField(obj, g_cache.vsHeight, (jint)size.Height);
		return obj;
	}

	JNIEXPORT jintArray JNICALL Java_mesen_MesenDebugger_getEventViewerOutput(JNIEnv* env, jclass, jint cpuType)
	{
		FrameInfo size = GetEventViewerDisplaySize((CpuType)cpuType);
		uint32_t pixelCount = size.Width * size.Height;

		jintArray result = env->NewIntArray((jsize)pixelCount);
		if(pixelCount == 0) {
			return result;
		}

		std::vector<uint32_t> buffer(pixelCount, 0);
		// GetDisplayBuffer compares bufferSize against width*height*sizeof(uint32_t),
		// i.e. it wants BYTES, not pixels - passing the pixel count made it bail out
		// and return an all-zero buffer.
		GetEventViewerOutput((CpuType)cpuType, buffer.data(), pixelCount * (uint32_t)sizeof(uint32_t));
		env->SetIntArrayRegion(result, 0, (jsize)pixelCount, (const jint*)buffer.data());
		return result;
	}

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getEventViewerEvent(
		JNIEnv* env, jclass, jint cpuType, jint scanline, jint cycle)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		DebugEventInfo event = GetEventViewerEvent((CpuType)cpuType, (uint16_t)scanline, (uint16_t)cycle);
		return NewDebugEventInfo(env, event);
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_takeEventSnapshot(
		JNIEnv*, jclass, jint cpuType, jboolean forAutoRefresh)
	{
		return (jint)TakeEventSnapshot((CpuType)cpuType, forAutoRefresh == JNI_TRUE);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setViewerUpdateTiming(
		JNIEnv*, jclass, jint viewerId, jint scanline, jint cycle, jint cpuType)
	{
		SetViewerUpdateTiming((uint32_t)viewerId, (uint16_t)scanline, (uint16_t)cycle, (CpuType)cpuType);
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_removeViewerId(
		JNIEnv*, jclass, jint viewerId, jint cpuType)
	{
		RemoveViewerId((uint32_t)viewerId, (CpuType)cpuType);
	}

	// -----------------------------------------------------------------------
	// Bulk memory
	// -----------------------------------------------------------------------

	JNIEXPORT jbyteArray JNICALL Java_mesen_MesenDebugger_getMemoryState(JNIEnv* env, jclass, jint memoryType)
	{
		uint32_t size = GetMemorySize((MemoryType)memoryType);
		jbyteArray result = env->NewByteArray((jsize)size);
		if(size == 0) {
			return result;
		}

		std::vector<uint8_t> buffer(size);
		GetMemoryState((MemoryType)memoryType, buffer.data());
		env->SetByteArrayRegion(result, 0, (jsize)size, (const jbyte*)buffer.data());
		return result;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_setMemoryState(JNIEnv* env, jclass, jint memoryType, jbyteArray data)
	{
		if(!data) {
			return;
		}

		jsize length = env->GetArrayLength(data);
		if(length <= 0) {
			return;
		}

		std::vector<uint8_t> buffer((size_t)length);
		env->GetByteArrayRegion(data, 0, length, (jbyte*)buffer.data());
		SetMemoryState((MemoryType)memoryType, buffer.data(), (int32_t)length);
	}

	// -----------------------------------------------------------------------
	// CDL / access counters
	// -----------------------------------------------------------------------

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getCdlStatistics(JNIEnv* env, jclass, jint memoryType)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		CdlStatistics stats = GetCdlStatistics((MemoryType)memoryType);

		jobject obj = env->NewObject(g_cache.cdlStatistics, g_cache.cdlStatisticsCtor);
		env->SetIntField(obj, g_cache.csCodeBytes, (jint)stats.CodeBytes);
		env->SetIntField(obj, g_cache.csDataBytes, (jint)stats.DataBytes);
		env->SetIntField(obj, g_cache.csTotalBytes, (jint)stats.TotalBytes);
		env->SetIntField(obj, g_cache.csJumpTargetCount, (jint)stats.JumpTargetCount);
		env->SetIntField(obj, g_cache.csFunctionCount, (jint)stats.FunctionCount);
		env->SetIntField(obj, g_cache.csDrawnChrBytes, (jint)stats.DrawnChrBytes);
		env->SetIntField(obj, g_cache.csTotalChrBytes, (jint)stats.TotalChrBytes);
		return obj;
	}

	JNIEXPORT jintArray JNICALL Java_mesen_MesenDebugger_getCdlFunctions(
		JNIEnv* env, jclass, jint memoryType, jint maxCount)
	{
		uint32_t capacity = CDL_FUNCTION_CAPACITY;
		if(maxCount > 0 && (uint32_t)maxCount < capacity) {
			capacity = (uint32_t)maxCount;
		}

		std::vector<uint32_t> functions(capacity);
		uint32_t count = GetCdlFunctions((MemoryType)memoryType, functions.data(), capacity);
		if(count > capacity) {
			count = capacity;
		}

		jintArray result = env->NewIntArray((jsize)count);
		if(count == 0) {
			return result;
		}

		std::vector<jint> converted(count);
		for(uint32_t i = 0; i < count; i++) {
			converted[i] = (jint)functions[i];
		}
		env->SetIntArrayRegion(result, 0, (jsize)count, converted.data());
		return result;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_resetCdl(JNIEnv*, jclass, jint memoryType)
	{
		ResetCdl((MemoryType)memoryType);
	}

	JNIEXPORT jobjectArray JNICALL Java_mesen_MesenDebugger_getMemoryAccessCounts(
		JNIEnv* env, jclass, jint offset, jint length, jint memoryType)
	{
		if(!InitCache(env) || length <= 0) {
			return nullptr;
		}

		std::vector<AddressCounters> counters((size_t)length);
		GetMemoryAccessCounts((uint32_t)offset, (uint32_t)length, (MemoryType)memoryType, counters.data());

		jobjectArray result = env->NewObjectArray((jsize)length, g_cache.addressCounters, nullptr);
		for(jint i = 0; i < length; i++) {
			const AddressCounters& src = counters[(size_t)i];
			jobject obj = env->NewObject(g_cache.addressCounters, g_cache.addressCountersCtor);
			env->SetLongField(obj, g_cache.acReadStamp, (jlong)src.ReadStamp);
			env->SetLongField(obj, g_cache.acWriteStamp, (jlong)src.WriteStamp);
			env->SetLongField(obj, g_cache.acExecStamp, (jlong)src.ExecStamp);
			env->SetIntField(obj, g_cache.acReadCounter, (jint)src.ReadCounter);
			env->SetIntField(obj, g_cache.acWriteCounter, (jint)src.WriteCounter);
			env->SetIntField(obj, g_cache.acExecCounter, (jint)src.ExecCounter);
			env->SetObjectArrayElement(result, i, obj);
			env->DeleteLocalRef(obj);
		}
		return result;
	}

	JNIEXPORT void JNICALL Java_mesen_MesenDebugger_resetMemoryAccessCounts(JNIEnv*, jclass)
	{
		ResetMemoryAccessCounts();
	}
	// -----------------------------------------------------------------------
	// Whole-console state (GetConsoleState)
	//
	// Field lookups are local rather than going through g_cache: this state is
	// huge (NES alone is ~20KB) and is queried for diagnostics, not per frame.
	// -----------------------------------------------------------------------

	jobject NewApuLengthCounterState(JNIEnv* env, const ApuLengthCounterState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuLengthCounterState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetBooleanField(obj, env->GetFieldID(cls, "Halt", "Z"), s.Halt ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "Counter", "I"), (jint)s.Counter);
		env->SetIntField(obj, env->GetFieldID(cls, "ReloadValue", "I"), (jint)s.ReloadValue);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuEnvelopeState(JNIEnv* env, const ApuEnvelopeState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuEnvelopeState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetBooleanField(obj, env->GetFieldID(cls, "StartFlag", "Z"), s.StartFlag ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, env->GetFieldID(cls, "Loop", "Z"), s.Loop ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, env->GetFieldID(cls, "ConstantVolume", "Z"), s.ConstantVolume ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "Divider", "I"), (jint)s.Divider);
		env->SetIntField(obj, env->GetFieldID(cls, "Counter", "I"), (jint)s.Counter);
		env->SetIntField(obj, env->GetFieldID(cls, "Volume", "I"), (jint)s.Volume);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuSquareState(JNIEnv* env, const ApuSquareState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuSquareState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetIntField(obj, env->GetFieldID(cls, "Duty", "I"), (jint)s.Duty);
		env->SetIntField(obj, env->GetFieldID(cls, "DutyPosition", "I"), (jint)s.DutyPosition);
		env->SetIntField(obj, env->GetFieldID(cls, "Period", "I"), (jint)s.Period);
		env->SetIntField(obj, env->GetFieldID(cls, "Timer", "I"), (jint)s.Timer);
		env->SetBooleanField(obj, env->GetFieldID(cls, "SweepEnabled", "Z"), s.SweepEnabled ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, env->GetFieldID(cls, "SweepNegate", "Z"), s.SweepNegate ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "SweepPeriod", "I"), (jint)s.SweepPeriod);
		env->SetIntField(obj, env->GetFieldID(cls, "SweepShift", "I"), (jint)s.SweepShift);
		env->SetBooleanField(obj, env->GetFieldID(cls, "Enabled", "Z"), s.Enabled ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "OutputVolume", "I"), (jint)s.OutputVolume);
		env->SetDoubleField(obj, env->GetFieldID(cls, "Frequency", "D"), (jdouble)s.Frequency);
		jobject LengthCounter = NewApuLengthCounterState(env, s.LengthCounter);
		env->SetObjectField(obj, env->GetFieldID(cls, "LengthCounter", "Lmesen/debugger/ApuLengthCounterState;"), LengthCounter);
		env->DeleteLocalRef(LengthCounter);
		jobject Envelope = NewApuEnvelopeState(env, s.Envelope);
		env->SetObjectField(obj, env->GetFieldID(cls, "Envelope", "Lmesen/debugger/ApuEnvelopeState;"), Envelope);
		env->DeleteLocalRef(Envelope);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuTriangleState(JNIEnv* env, const ApuTriangleState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuTriangleState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetIntField(obj, env->GetFieldID(cls, "Period", "I"), (jint)s.Period);
		env->SetIntField(obj, env->GetFieldID(cls, "Timer", "I"), (jint)s.Timer);
		env->SetIntField(obj, env->GetFieldID(cls, "SequencePosition", "I"), (jint)s.SequencePosition);
		env->SetBooleanField(obj, env->GetFieldID(cls, "Enabled", "Z"), s.Enabled ? JNI_TRUE : JNI_FALSE);
		env->SetDoubleField(obj, env->GetFieldID(cls, "Frequency", "D"), (jdouble)s.Frequency);
		env->SetIntField(obj, env->GetFieldID(cls, "OutputVolume", "I"), (jint)s.OutputVolume);
		env->SetIntField(obj, env->GetFieldID(cls, "LinearCounter", "I"), (jint)s.LinearCounter);
		env->SetIntField(obj, env->GetFieldID(cls, "LinearCounterReload", "I"), (jint)s.LinearCounterReload);
		env->SetBooleanField(obj, env->GetFieldID(cls, "LinearReloadFlag", "Z"), s.LinearReloadFlag ? JNI_TRUE : JNI_FALSE);
		jobject LengthCounter = NewApuLengthCounterState(env, s.LengthCounter);
		env->SetObjectField(obj, env->GetFieldID(cls, "LengthCounter", "Lmesen/debugger/ApuLengthCounterState;"), LengthCounter);
		env->DeleteLocalRef(LengthCounter);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuNoiseState(JNIEnv* env, const ApuNoiseState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuNoiseState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetIntField(obj, env->GetFieldID(cls, "Period", "I"), (jint)s.Period);
		env->SetIntField(obj, env->GetFieldID(cls, "Timer", "I"), (jint)s.Timer);
		env->SetIntField(obj, env->GetFieldID(cls, "ShiftRegister", "I"), (jint)s.ShiftRegister);
		env->SetBooleanField(obj, env->GetFieldID(cls, "ModeFlag", "Z"), s.ModeFlag ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, env->GetFieldID(cls, "Enabled", "Z"), s.Enabled ? JNI_TRUE : JNI_FALSE);
		env->SetDoubleField(obj, env->GetFieldID(cls, "Frequency", "D"), (jdouble)s.Frequency);
		env->SetIntField(obj, env->GetFieldID(cls, "OutputVolume", "I"), (jint)s.OutputVolume);
		jobject LengthCounter = NewApuLengthCounterState(env, s.LengthCounter);
		env->SetObjectField(obj, env->GetFieldID(cls, "LengthCounter", "Lmesen/debugger/ApuLengthCounterState;"), LengthCounter);
		env->DeleteLocalRef(LengthCounter);
		jobject Envelope = NewApuEnvelopeState(env, s.Envelope);
		env->SetObjectField(obj, env->GetFieldID(cls, "Envelope", "Lmesen/debugger/ApuEnvelopeState;"), Envelope);
		env->DeleteLocalRef(Envelope);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuDmcState(JNIEnv* env, const ApuDmcState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuDmcState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetDoubleField(obj, env->GetFieldID(cls, "SampleRate", "D"), (jdouble)s.SampleRate);
		env->SetIntField(obj, env->GetFieldID(cls, "SampleAddr", "I"), (jint)s.SampleAddr);
		env->SetIntField(obj, env->GetFieldID(cls, "NextSampleAddr", "I"), (jint)s.NextSampleAddr);
		env->SetIntField(obj, env->GetFieldID(cls, "SampleLength", "I"), (jint)s.SampleLength);
		env->SetBooleanField(obj, env->GetFieldID(cls, "Loop", "Z"), s.Loop ? JNI_TRUE : JNI_FALSE);
		env->SetBooleanField(obj, env->GetFieldID(cls, "IrqEnabled", "Z"), s.IrqEnabled ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "Period", "I"), (jint)s.Period);
		env->SetIntField(obj, env->GetFieldID(cls, "Timer", "I"), (jint)s.Timer);
		env->SetIntField(obj, env->GetFieldID(cls, "BytesRemaining", "I"), (jint)s.BytesRemaining);
		env->SetIntField(obj, env->GetFieldID(cls, "BitsRemaining", "I"), (jint)s.BitsRemaining);
		env->SetIntField(obj, env->GetFieldID(cls, "OutputVolume", "I"), (jint)s.OutputVolume);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuFrameCounterState(JNIEnv* env, const ApuFrameCounterState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuFrameCounterState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));
		env->SetBooleanField(obj, env->GetFieldID(cls, "FiveStepMode", "Z"), s.FiveStepMode ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "SequencePosition", "I"), (jint)s.SequencePosition);
		env->SetBooleanField(obj, env->GetFieldID(cls, "IrqEnabled", "Z"), s.IrqEnabled ? JNI_TRUE : JNI_FALSE);
		env->DeleteLocalRef(cls);
		return obj;
	}

	jobject NewApuState(JNIEnv* env, const ApuState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/ApuState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));

		jobject Square1 = NewApuSquareState(env, s.Square1);
		env->SetObjectField(obj, env->GetFieldID(cls, "Square1", "Lmesen/debugger/ApuSquareState;"), Square1);
		env->DeleteLocalRef(Square1);

		jobject Square2 = NewApuSquareState(env, s.Square2);
		env->SetObjectField(obj, env->GetFieldID(cls, "Square2", "Lmesen/debugger/ApuSquareState;"), Square2);
		env->DeleteLocalRef(Square2);

		jobject Triangle = NewApuTriangleState(env, s.Triangle);
		env->SetObjectField(obj, env->GetFieldID(cls, "Triangle", "Lmesen/debugger/ApuTriangleState;"), Triangle);
		env->DeleteLocalRef(Triangle);

		jobject Noise = NewApuNoiseState(env, s.Noise);
		env->SetObjectField(obj, env->GetFieldID(cls, "Noise", "Lmesen/debugger/ApuNoiseState;"), Noise);
		env->DeleteLocalRef(Noise);

		jobject Dmc = NewApuDmcState(env, s.Dmc);
		env->SetObjectField(obj, env->GetFieldID(cls, "Dmc", "Lmesen/debugger/ApuDmcState;"), Dmc);
		env->DeleteLocalRef(Dmc);

		jobject FrameCounter = NewApuFrameCounterState(env, s.FrameCounter);
		env->SetObjectField(obj, env->GetFieldID(cls, "FrameCounter", "Lmesen/debugger/ApuFrameCounterState;"), FrameCounter);
		env->DeleteLocalRef(FrameCounter);

		env->DeleteLocalRef(cls);
		return obj;
	}

	void SetIntArrayField(JNIEnv* env, jclass cls, jobject obj, const char* name, const int32_t* source, int count)
	{
		std::vector<jint> values((size_t)count);
		for(int i = 0; i < count; i++) {
			values[i] = (jint)source[i];
		}

		jintArray array = env->NewIntArray(count);
		env->SetIntArrayRegion(array, 0, count, values.data());
		env->SetObjectField(obj, env->GetFieldID(cls, name, "[I"), array);
		env->DeleteLocalRef(array);
	}



	jobject NewNesCartridgeState(JNIEnv* env, const CartridgeState& s)
	{
		jclass cls = env->FindClass("mesen/debugger/NesCartridgeState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));

		env->SetIntField(obj, env->GetFieldID(cls, "PrgRomSize", "I"), (jint)s.PrgRomSize);
		env->SetIntField(obj, env->GetFieldID(cls, "ChrRomSize", "I"), (jint)s.ChrRomSize);
		env->SetIntField(obj, env->GetFieldID(cls, "ChrRamSize", "I"), (jint)s.ChrRamSize);
		env->SetIntField(obj, env->GetFieldID(cls, "PrgPageCount", "I"), (jint)s.PrgPageCount);
		env->SetIntField(obj, env->GetFieldID(cls, "PrgPageSize", "I"), (jint)s.PrgPageSize);
		env->SetIntField(obj, env->GetFieldID(cls, "ChrPageCount", "I"), (jint)s.ChrPageCount);
		env->SetIntField(obj, env->GetFieldID(cls, "ChrPageSize", "I"), (jint)s.ChrPageSize);
		env->SetIntField(obj, env->GetFieldID(cls, "ChrRamPageSize", "I"), (jint)s.ChrRamPageSize);
		env->SetIntField(obj, env->GetFieldID(cls, "WorkRamPageSize", "I"), (jint)s.WorkRamPageSize);
		env->SetIntField(obj, env->GetFieldID(cls, "SaveRamPageSize", "I"), (jint)s.SaveRamPageSize);
		env->SetIntField(obj, env->GetFieldID(cls, "Mirroring", "I"), (jint)s.Mirroring);
		env->SetBooleanField(obj, env->GetFieldID(cls, "HasBattery", "Z"), s.HasBattery ? JNI_TRUE : JNI_FALSE);
		env->SetIntField(obj, env->GetFieldID(cls, "CustomEntryCount", "I"), (jint)s.CustomEntryCount);

		SetIntArrayField(env, cls, obj, "PrgMemoryOffset", s.PrgMemoryOffset, 0x100);
		SetIntArrayField(env, cls, obj, "ChrMemoryOffset", s.ChrMemoryOffset, 0x40);

		// Enum arrays are widened to int32 here (they are 1-byte enums in the core,
		// and a template helper cannot live inside extern "C").
		std::vector<int32_t> prgType(0x100), prgAccess(0x100), chrType(0x40), chrAccess(0x40);
		for(int i = 0; i < 0x100; i++) {
			prgType[i] = (int32_t)s.PrgType[i];
			prgAccess[i] = (int32_t)s.PrgMemoryAccess[i];
		}
		for(int i = 0; i < 0x40; i++) {
			chrType[i] = (int32_t)s.ChrType[i];
			chrAccess[i] = (int32_t)s.ChrMemoryAccess[i];
		}
		SetIntArrayField(env, cls, obj, "PrgType", prgType.data(), 0x100);
		SetIntArrayField(env, cls, obj, "PrgMemoryAccess", prgAccess.data(), 0x100);
		SetIntArrayField(env, cls, obj, "ChrType", chrType.data(), 0x40);
		SetIntArrayField(env, cls, obj, "ChrMemoryAccess", chrAccess.data(), 0x40);

		jclass entryCls = env->FindClass("mesen/debugger/NesMapperStateEntry");
		jmethodID entryCtor = env->GetMethodID(entryCls, "<init>", "()V");
		jfieldID rawValueField = env->GetFieldID(entryCls, "rawValue", "J");
		jfieldID typeField = env->GetFieldID(entryCls, "type", "I");
		jfieldID addressField = env->GetFieldID(entryCls, "address", "[B");
		jfieldID nameField = env->GetFieldID(entryCls, "name", "[B");

		jobjectArray entries = env->NewObjectArray(200, entryCls, nullptr);
		for(int i = 0; i < 200; i++) {
			const MapperStateEntry& source = s.CustomEntries[i];
			jobject entry = env->NewObject(entryCls, entryCtor);
			env->SetLongField(entry, rawValueField, (jlong)source.RawValue);
			env->SetIntField(entry, typeField, (jint)source.Type);

			jbyteArray address = env->NewByteArray(MapperStateEntry::MaxLength);
			env->SetByteArrayRegion(address, 0, MapperStateEntry::MaxLength, (const jbyte*)source.Address);
			env->SetObjectField(entry, addressField, address);
			env->DeleteLocalRef(address);

			jbyteArray name = env->NewByteArray(MapperStateEntry::MaxLength);
			env->SetByteArrayRegion(name, 0, MapperStateEntry::MaxLength, (const jbyte*)source.Name);
			env->SetObjectField(entry, nameField, name);
			env->DeleteLocalRef(name);

			env->SetObjectArrayElement(entries, i, entry);
			env->DeleteLocalRef(entry);
		}
		env->SetObjectField(obj, env->GetFieldID(cls, "CustomEntries", "[Lmesen/debugger/NesMapperStateEntry;"), entries);
		env->DeleteLocalRef(entries);
		env->DeleteLocalRef(entryCls);

		env->DeleteLocalRef(cls);
		return obj;
	}

	// Defined with the raw console-state helpers further down.
	bool IsRunningConsole(ConsoleType type);

	JNIEXPORT jobject JNICALL Java_mesen_MesenDebugger_getNesConsoleState(JNIEnv* env, jclass)
	{
		if(!InitCache(env)) {
			return nullptr;
		}

		NesState state = {};
		if(IsRunningConsole(ConsoleType::Nes)) {
			GetConsoleState(*reinterpret_cast<BaseState*>(&state), ConsoleType::Nes);
		}

		jclass cls = env->FindClass("mesen/debugger/NesConsoleState");
		jobject obj = env->NewObject(cls, env->GetMethodID(cls, "<init>", "()V"));

		jobject cpu = NewNesCpuState(env, state.Cpu);
		env->SetObjectField(obj, env->GetFieldID(cls, "cpu", "Lmesen/debugger/NesCpuState;"), cpu);
		env->DeleteLocalRef(cpu);

		jobject ppu = NewNesPpuState(env, state.Ppu);
		env->SetObjectField(obj, env->GetFieldID(cls, "ppu", "Lmesen/debugger/NesPpuState;"), ppu);
		env->DeleteLocalRef(ppu);

		jobject cartridge = NewNesCartridgeState(env, state.Cartridge);
		env->SetObjectField(obj, env->GetFieldID(cls, "cartridge", "Lmesen/debugger/NesCartridgeState;"), cartridge);
		env->DeleteLocalRef(cartridge);

		jobject apu = NewApuState(env, state.Apu);
		env->SetObjectField(obj, env->GetFieldID(cls, "apu", "Lmesen/debugger/ApuState;"), apu);
		env->DeleteLocalRef(apu);

		env->SetIntField(obj, env->GetFieldID(cls, "clockRate", "I"), (jint)state.ClockRate);
		env->DeleteLocalRef(cls);
		return obj;
	}	// -----------------------------------------------------------------------
	// Whole-console state for every console (raw bytes)
	//
	// Only NES gets a typed Java class: the other states are far larger (SNES
	// carries six coprocessor states, GBA/WS have 9-13 sub-structs each), and
	// there are no ROMs for them in this workspace to verify typed marshalling.
	// The raw snapshot below is one code path that covers all of them.
	// -----------------------------------------------------------------------

	size_t GetConsoleStateByteSize(ConsoleType type)
	{
		switch(type) {
			case ConsoleType::Snes: return sizeof(SnesState);
			case ConsoleType::Gameboy: return sizeof(GbState);
			case ConsoleType::Nes: return sizeof(NesState);
			case ConsoleType::PcEngine: return sizeof(PceState);
			case ConsoleType::Sms: return sizeof(SmsState);
			case ConsoleType::Gba: return sizeof(GbaState);
			case ConsoleType::Ws: return sizeof(WsState);
			default: return 0;
		}
	}

	// The largest state we might be handed. Console implementations ignore the
	// consoleType argument and write their own struct unconditionally
	// (NesConsole::GetConsoleState is a plain NesState assignment), so a buffer
	// sized for the *requested* console can be overflowed by a bigger running
	// one - that produced heap corruption before this was fixed.
	size_t GetMaxConsoleStateByteSize()
	{
		const size_t sizes[] = {
			sizeof(SnesState), sizeof(GbState), sizeof(NesState), sizeof(PceState),
			sizeof(SmsState), sizeof(GbaState), sizeof(WsState)
		};

		size_t maxSize = 0;
		for(size_t size : sizes) {
			if(size > maxSize) {
				maxSize = size;
			}
		}
		return maxSize;
	}

	// True when the requested console is the one actually running; the core
	// only guarantees a correct result in that case.
	bool IsRunningConsole(ConsoleType type)
	{
		if(!_emu) {
			return false;
		}

		IConsole* console = _emu->GetConsole().get();
		return console != nullptr && console->GetConsoleType() == type;
	}

	JNIEXPORT jint JNICALL Java_mesen_MesenDebugger_getConsoleStateSize(JNIEnv*, jclass, jint consoleType)
	{
		return (jint)GetConsoleStateByteSize((ConsoleType)consoleType);
	}

	JNIEXPORT jbyteArray JNICALL Java_mesen_MesenDebugger_getConsoleStateBytes(JNIEnv* env, jclass, jint consoleType)
	{
		ConsoleType type = (ConsoleType)consoleType;
		size_t size = GetConsoleStateByteSize(type);
		if(size == 0) {
			return env->NewByteArray(0);
		}

		// uint64_t storage keeps the buffer 8-byte aligned for the core's memcpy.
		std::vector<uint64_t> storage((GetMaxConsoleStateByteSize() + sizeof(uint64_t) - 1) / sizeof(uint64_t), 0);
		if(IsRunningConsole(type)) {
			GetConsoleState(*reinterpret_cast<BaseState*>(storage.data()), type);
		}

		jbyteArray result = env->NewByteArray((jsize)size);
		env->SetByteArrayRegion(result, 0, (jsize)size, (const jbyte*)storage.data());
		return result;
	}
}
