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

#include <jni.h>

#include <cstring>
#include <string>
#include <vector>

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

		if(!g_cache.addressInfo || !g_cache.effectiveAddress || !g_cache.codeLine ||
			!g_cache.breakpoint || !g_cache.stackFrame || !g_cache.cdlStatistics ||
			!g_cache.cpuVector || !g_cache.debuggerFeatures || !g_cache.memoryOperation ||
			!g_cache.instructionProgress || !g_cache.evalResult || !g_cache.addressCounters ||
			!g_cache.nesCpuState || !g_cache.nesPpuState) {
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
}
