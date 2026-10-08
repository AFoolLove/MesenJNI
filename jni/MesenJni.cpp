// ---------------------------------------------------------------------------
// MesenJni.cpp
//
// JNI bindings for the MesenCore (InteropDLL) C API.
// Implements the native methods declared in com.mesen.MesenCore.
// ---------------------------------------------------------------------------

#include <jni.h>
#include <cstring>
#include <functional>
#include <string>
#include <vector>

#include "MesenApi.h"

using std::string;

namespace
{
	string JStringToUtf8(JNIEnv* env, jstring str)
	{
		if(!str) {
			return "";
		}
		const char* chars = env->GetStringUTFChars(str, nullptr);
		string result = chars ? chars : "";
		if(chars) {
			env->ReleaseStringUTFChars(str, chars);
		}
		return result;
	}

	jstring Utf8ToJString(JNIEnv* env, const char* str)
	{
		return env->NewStringUTF(str ? str : "");
	}

	// Returns a pointer to the KeyMapping field identified by a button id
	// (see Mesen.BUTTON_* constants), or nullptr for an invalid id.
	uint16_t* GetKeyMappingField(KeyMapping& mapping, int button)
	{
		switch(button) {
			case 0: return &mapping.A;
			case 1: return &mapping.B;
			case 2: return &mapping.X;
			case 3: return &mapping.Y;
			case 4: return &mapping.L;
			case 5: return &mapping.R;
			case 6: return &mapping.Up;
			case 7: return &mapping.Down;
			case 8: return &mapping.Left;
			case 9: return &mapping.Right;
			case 10: return &mapping.Start;
			case 11: return &mapping.Select;
			case 12: return &mapping.U;
			case 13: return &mapping.D;
			case 14: return &mapping.TurboA;
			case 15: return &mapping.TurboB;
			case 16: return &mapping.TurboX;
			case 17: return &mapping.TurboY;
			case 18: return &mapping.TurboL;
			case 19: return &mapping.TurboR;
			case 20: return &mapping.TurboSelect;
			case 21: return &mapping.TurboStart;
			default: return nullptr;
		}
	}

	// Gets a per-console config, applies `fn` to the requested controller port,
	// and writes the config back. `port` is 0-based.
	void ApplyControllerConfig(int consoleType, int port, const std::function<void(ControllerConfig&)>& fn)
	{
		switch((ConsoleType)consoleType) {
			case ConsoleType::Snes: {
				SnesConfig cfg = GetSnesConfig();
				fn(port == 0 ? cfg.Port1 : cfg.Port2);
				SetSnesConfig(cfg);
				break;
			}
			case ConsoleType::Gameboy: {
				GameboyConfig cfg = GetGameboyConfig();
				fn(port == 0 ? cfg.Controller : cfg.LinkedController);
				SetGameboyConfig(cfg);
				break;
			}
			case ConsoleType::Nes: {
				NesConfig cfg = GetNesConfig();
				ControllerConfig* c = port == 0 ? &cfg.Port1 : (port == 1 ? &cfg.Port2 : &cfg.ExpPort);
				fn(*c);
				SetNesConfig(cfg);
				break;
			}
			case ConsoleType::PcEngine: {
				PcEngineConfig cfg = GetPcEngineConfig();
				fn(cfg.Port1);
				SetPcEngineConfig(cfg);
				break;
			}
			case ConsoleType::Sms: {
				SmsConfig cfg = GetSmsConfig();
				fn(port == 0 ? cfg.Port1 : cfg.Port2);
				SetSmsConfig(cfg);
				break;
			}
			case ConsoleType::Gba: {
				GbaConfig cfg = GetGbaConfig();
				fn(cfg.Controller);
				SetGbaConfig(cfg);
				break;
			}
			case ConsoleType::Ws: {
				WsConfig cfg = GetWsConfig();
				ControllerConfig* c = port == 0 ? &cfg.ControllerHorizontal
					: (port == 1 ? &cfg.ControllerVertical : &cfg.ControllerPcv2);
				fn(*c);
				SetWsConfig(cfg);
				break;
			}
			default:
				break;
		}
	}
}

extern "C"
{

// --- Lifecycle / info -----------------------------------------------------

JNIEXPORT jboolean JNICALL Java_com_mesen_MesenCore_testDll(JNIEnv*, jclass)
{
	return TestDll() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_com_mesen_MesenCore_getMesenVersion(JNIEnv*, jclass)
{
	return (jint)GetMesenVersion();
}

JNIEXPORT jstring JNICALL Java_com_mesen_MesenCore_getMesenBuildDate(JNIEnv* env, jclass)
{
	return Utf8ToJString(env, GetMesenBuildDate());
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_initDll(JNIEnv*, jclass)
{
	InitDll();
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_initializeEmu(JNIEnv* env, jclass, jstring homeFolder, jboolean softwareRenderer, jboolean noAudio, jboolean noVideo, jboolean noInput)
{
	string home = JStringToUtf8(env, homeFolder);
	// Headless: null window/viewer handles. The software renderer is created
	// when softwareRenderer is true (see InitializeEmu).
	InitializeEmu(home.c_str(), nullptr, nullptr, softwareRenderer, noAudio, noVideo, noInput);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_release(JNIEnv*, jclass)
{
	Release();
}

JNIEXPORT jboolean JNICALL Java_com_mesen_MesenCore_isRunning(JNIEnv*, jclass)
{
	return IsRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_stop(JNIEnv*, jclass)
{
	Stop();
}

JNIEXPORT jint JNICALL Java_com_mesen_MesenCore_getStopCode(JNIEnv*, jclass)
{
	return (jint)GetStopCode();
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_pause(JNIEnv*, jclass)
{
	Pause();
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_resume(JNIEnv*, jclass)
{
	Resume();
}

JNIEXPORT jboolean JNICALL Java_com_mesen_MesenCore_isPaused(JNIEnv*, jclass)
{
	return IsPaused() ? JNI_TRUE : JNI_FALSE;
}

// --- ROM ------------------------------------------------------------------

JNIEXPORT jboolean JNICALL Java_com_mesen_MesenCore_loadRom(JNIEnv* env, jclass, jstring filename, jstring patchFile)
{
	string fn = JStringToUtf8(env, filename);
	string pf = JStringToUtf8(env, patchFile);
	return LoadRom(fn.empty() ? nullptr : fn.data(), pf.empty() ? nullptr : pf.data()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_loadRecentGame(JNIEnv* env, jclass, jstring filepath, jboolean resetGame)
{
	string fp = JStringToUtf8(env, filepath);
	LoadRecentGame(fp.empty() ? nullptr : fp.data(), resetGame);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_addKnownGameFolder(JNIEnv* env, jclass, jstring folder)
{
	string f = JStringToUtf8(env, folder);
	AddKnownGameFolder(f.empty() ? nullptr : f.data());
}

JNIEXPORT jobject JNICALL Java_com_mesen_MesenCore_getRomInfo(JNIEnv* env, jclass)
{
	InteropRomInfo info;
	memset(&info, 0, sizeof(info));
	GetRomInfo(info);

	jclass cls = env->FindClass("com/mesen/RomInfo");
	jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
	jobject obj = env->NewObject(cls, ctor);

	env->SetObjectField(obj, env->GetFieldID(cls, "romPath", "Ljava/lang/String;"), Utf8ToJString(env, info.RomPath));
	env->SetObjectField(obj, env->GetFieldID(cls, "patchPath", "Ljava/lang/String;"), Utf8ToJString(env, info.PatchPath));
	env->SetIntField(obj, env->GetFieldID(cls, "format", "I"), (jint)info.Format);
	env->SetIntField(obj, env->GetFieldID(cls, "consoleType", "I"), (jint)info.Console);
	env->SetLongField(obj, env->GetFieldID(cls, "databaseId", "J"), (jlong)info.DipSwitches.DatabaseId);
	env->SetIntField(obj, env->GetFieldID(cls, "dipSwitchCount", "I"), (jint)info.DipSwitches.DipSwitchCount);

	jintArray cpuTypes = env->NewIntArray((jsize)info.CpuTypeCount);
	if(info.CpuTypeCount > 0) {
		jint tmp[5];
		for(uint32_t i = 0; i < info.CpuTypeCount && i < 5; i++) {
			tmp[i] = (jint)info.CpuTypes[i];
		}
		env->SetIntArrayRegion(cpuTypes, 0, (jsize)info.CpuTypeCount, tmp);
	}
	env->SetObjectField(obj, env->GetFieldID(cls, "cpuTypes", "[I"), cpuTypes);

	return obj;
}

JNIEXPORT jstring JNICALL Java_com_mesen_MesenCore_getRomHash(JNIEnv* env, jclass, jint hashType)
{
	std::vector<char> buf(1000000, 0);
	GetRomHash((HashType)hashType, buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

// --- State ----------------------------------------------------------------

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_saveState(JNIEnv*, jclass, jint slot)
{
	SaveState((uint32_t)slot);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_loadState(JNIEnv*, jclass, jint slot)
{
	LoadState((uint32_t)slot);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_saveStateFile(JNIEnv* env, jclass, jstring filepath)
{
	string fp = JStringToUtf8(env, filepath);
	SaveStateFile(fp.empty() ? nullptr : fp.data());
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_loadStateFile(JNIEnv* env, jclass, jstring filepath)
{
	string fp = JStringToUtf8(env, filepath);
	LoadStateFile(fp.empty() ? nullptr : fp.data());
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_takeScreenshot(JNIEnv*, jclass)
{
	TakeScreenshot();
}

// --- Memory ---------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_com_mesen_MesenCore_getMemorySize(JNIEnv*, jclass, jint memoryType)
{
	return (jlong)GetMemorySize((MemoryType)memoryType);
}

JNIEXPORT jint JNICALL Java_com_mesen_MesenCore_getMemoryValue(JNIEnv*, jclass, jint memoryType, jlong address)
{
	return (jint)GetMemoryValue((MemoryType)memoryType, (uint32_t)address);
}

JNIEXPORT jbyteArray JNICALL Java_com_mesen_MesenCore_getMemoryValues(JNIEnv* env, jclass, jint memoryType, jlong start, jlong end)
{
	uint32_t s = (uint32_t)start;
	uint32_t e = (uint32_t)end;
	jsize count = (e >= s) ? (jsize)(e - s + 1) : 0;

	std::vector<uint8_t> buffer(count > 0 ? count : 1, 0);
	if(count > 0) {
		GetMemoryValues((MemoryType)memoryType, s, e, buffer.data());
	}

	jbyteArray result = env->NewByteArray(count);
	if(count > 0) {
		env->SetByteArrayRegion(result, 0, count, (const jbyte*)buffer.data());
	}
	return result;
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setMemoryValue(JNIEnv*, jclass, jint memoryType, jlong address, jint value)
{
	SetMemoryValue((MemoryType)memoryType, (uint32_t)address, (uint8_t)value);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setMemoryValues(JNIEnv* env, jclass, jint memoryType, jlong address, jbyteArray data)
{
	jsize len = env->GetArrayLength(data);
	jbyte* bytes = env->GetByteArrayElements(data, nullptr);
	if(bytes) {
		SetMemoryValues((MemoryType)memoryType, (uint32_t)address, (uint8_t*)bytes, (int32_t)len);
		env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
	}
}

// --- Config ---------------------------------------------------------------

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setEmulationFlag(JNIEnv*, jclass, jint flag, jboolean enabled)
{
	SetEmulationFlag((EmulationFlags)flag, enabled);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setDebuggerFlag(JNIEnv*, jclass, jint flag, jboolean enabled)
{
	SetDebuggerFlag((DebuggerFlags)flag, enabled);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setEmulationConfig(JNIEnv*, jclass, jint emulationSpeed, jint turboSpeed, jint rewindSpeed, jint runAheadFrames)
{
	EmulationConfig cfg = GetEmulationConfig();
	cfg.EmulationSpeed = (uint32_t)emulationSpeed;
	cfg.TurboSpeed = (uint32_t)turboSpeed;
	cfg.RewindSpeed = (uint32_t)rewindSpeed;
	cfg.RunAheadFrames = (uint32_t)runAheadFrames;
	SetEmulationConfig(cfg);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setVideoConfig(JNIEnv*, jclass, jint videoFilter, jint aspectRatio, jdouble customAspectRatio, jint screenRotation)
{
	VideoConfig cfg = GetVideoConfig();
	cfg.VideoFilter = (VideoFilterType)videoFilter;
	cfg.AspectRatio = (VideoAspectRatio)aspectRatio;
	cfg.CustomAspectRatio = customAspectRatio;
	cfg.ScreenRotation = (uint32_t)screenRotation;
	SetVideoConfig(cfg);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setAudioConfig(JNIEnv*, jclass, jboolean enableAudio, jint masterVolume, jint sampleRate, jint audioLatency)
{
	AudioConfig cfg = GetAudioConfig();
	cfg.EnableAudio = enableAudio;
	cfg.MasterVolume = (uint32_t)masterVolume;
	cfg.SampleRate = (uint32_t)sampleRate;
	cfg.AudioLatency = (uint32_t)audioLatency;
	SetAudioConfig(cfg);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setControllerType(JNIEnv*, jclass, jint consoleType, jint port, jint controllerType)
{
	ApplyControllerConfig(consoleType, port, [&](ControllerConfig& cfg) {
		cfg.Type = (ControllerType)controllerType;
	});
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setControllerKey(JNIEnv*, jclass, jint consoleType, jint port, jint button, jint keyCode)
{
	ApplyControllerConfig(consoleType, port, [&](ControllerConfig& cfg) {
		uint16_t* field = GetKeyMappingField(cfg.Keys.Mapping1, button);
		if(field) {
			*field = (uint16_t)keyCode;
		}
	});
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setRendererSize(JNIEnv*, jclass, jint width, jint height)
{
	SetRendererSize((uint32_t)width, (uint32_t)height);
}

JNIEXPORT jdouble JNICALL Java_com_mesen_MesenCore_getAspectRatio(JNIEnv*, jclass)
{
	return (jdouble)GetAspectRatio();
}

JNIEXPORT jobject JNICALL Java_com_mesen_MesenCore_getBaseScreenSize(JNIEnv* env, jclass)
{
	FrameInfo f = GetBaseScreenSize();
	jclass cls = env->FindClass("com/mesen/FrameInfo");
	jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
	jobject obj = env->NewObject(cls, ctor);
	env->SetIntField(obj, env->GetFieldID(cls, "width", "I"), (jint)f.Width);
	env->SetIntField(obj, env->GetFieldID(cls, "height", "I"), (jint)f.Height);
	return obj;
}

JNIEXPORT jobject JNICALL Java_com_mesen_MesenCore_getTimingInfo(JNIEnv* env, jclass, jint cpuType)
{
	TimingInfo t = GetTimingInfo((CpuType)cpuType);
	jclass cls = env->FindClass("com/mesen/TimingInfo");
	jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
	jobject obj = env->NewObject(cls, ctor);
	env->SetDoubleField(obj, env->GetFieldID(cls, "fps", "D"), t.Fps);
	env->SetLongField(obj, env->GetFieldID(cls, "masterClock", "J"), (jlong)t.MasterClock);
	env->SetLongField(obj, env->GetFieldID(cls, "masterClockRate", "J"), (jlong)t.MasterClockRate);
	env->SetLongField(obj, env->GetFieldID(cls, "frameCount", "J"), (jlong)t.FrameCount);
	env->SetLongField(obj, env->GetFieldID(cls, "scanlineCount", "J"), (jlong)t.ScanlineCount);
	env->SetIntField(obj, env->GetFieldID(cls, "firstScanline", "I"), t.FirstScanline);
	env->SetLongField(obj, env->GetFieldID(cls, "cycleCount", "J"), (jlong)t.CycleCount);
	return obj;
}

// --- Input ----------------------------------------------------------------

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setKeyState(JNIEnv*, jclass, jint scanCode, jboolean state)
{
	SetKeyState((uint16_t)scanCode, state);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_resetKeyState(JNIEnv*, jclass)
{
	ResetKeyState();
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_disableAllKeys(JNIEnv*, jclass, jboolean disabled)
{
	DisableAllKeys(disabled);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_updateInputDevices(JNIEnv*, jclass)
{
	UpdateInputDevices();
}

JNIEXPORT jint JNICALL Java_com_mesen_MesenCore_getKeyCode(JNIEnv* env, jclass, jstring keyName)
{
	string kn = JStringToUtf8(env, keyName);
	return (jint)GetKeyCode(kn.empty() ? nullptr : kn.data());
}

JNIEXPORT jstring JNICALL Java_com_mesen_MesenCore_getKeyName(JNIEnv* env, jclass, jint keyCode)
{
	std::vector<char> buf(256, 0);
	GetKeyName((uint16_t)keyCode, buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setMousePosition(JNIEnv*, jclass, jdouble x, jdouble y)
{
	SetMousePosition(x, y);
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_setMouseMovement(JNIEnv*, jclass, jint x, jint y)
{
	SetMouseMovement((int16_t)x, (int16_t)y);
}

JNIEXPORT jboolean JNICALL Java_com_mesen_MesenCore_hasControlDevice(JNIEnv*, jclass, jint controllerType)
{
	return HasControlDevice((ControllerType)controllerType) ? JNI_TRUE : JNI_FALSE;
}

// --- Log / messages -------------------------------------------------------

JNIEXPORT jstring JNICALL Java_com_mesen_MesenCore_getLog(JNIEnv* env, jclass)
{
	std::vector<char> buf(100000, 0);
	GetLog(buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_writeLogEntry(JNIEnv* env, jclass, jstring message)
{
	string m = JStringToUtf8(env, message);
	WriteLogEntry(m.empty() ? nullptr : m.data());
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_displayMessage(JNIEnv* env, jclass, jstring title, jstring message, jstring param)
{
	string t = JStringToUtf8(env, title);
	string m = JStringToUtf8(env, message);
	string p = JStringToUtf8(env, param);
	DisplayMessage(t.empty() ? nullptr : t.data(), m.empty() ? nullptr : m.data(), p.empty() ? nullptr : p.data());
}

// --- Software-rendered frame ----------------------------------------------

JNIEXPORT jint JNICALL Java_com_mesen_MesenCore_getFrameWidth(JNIEnv*, jclass)
{
	uint32_t width = 0, height = 0;
	uint32_t* buffer = nullptr;
	GetFrameBuffer(width, height, buffer);
	return (jint)width;
}

JNIEXPORT jint JNICALL Java_com_mesen_MesenCore_getFrameHeight(JNIEnv*, jclass)
{
	uint32_t width = 0, height = 0;
	uint32_t* buffer = nullptr;
	GetFrameBuffer(width, height, buffer);
	return (jint)height;
}

JNIEXPORT void JNICALL Java_com_mesen_MesenCore_copyFrameBuffer(JNIEnv* env, jclass, jintArray argb)
{
	uint32_t width = 0, height = 0;
	uint32_t* buffer = nullptr;
	GetFrameBuffer(width, height, buffer);
	if(!buffer) {
		return;
	}

	jsize capacity = env->GetArrayLength(argb);
	jsize count = (jsize)(width * height);
	if(count > capacity) {
		count = capacity;
	}

	jint* dest = env->GetIntArrayElements(argb, nullptr);
	if(dest) {
		memcpy(dest, buffer, count * sizeof(uint32_t));
		env->ReleaseIntArrayElements(argb, dest, 0);
	}
}

} // extern "C"
