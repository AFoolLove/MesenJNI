// ---------------------------------------------------------------------------
// MesenJni.cpp
//
// JNI bindings for the MesenCore (InteropDLL) C API.
// Implements the native methods declared in mesen.MesenCore.
// ---------------------------------------------------------------------------

#include <jni.h>
#include <cstring>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "MesenApi.h"
#include "JavaAdapters.h"
#include "Core/Shared/KeyManager.h"
#include "Core/Shared/NotificationManager.h"
#include "Core/Shared/Audio/SoundMixer.h"
#include "Core/Shared/Video/VideoRenderer.h"

using std::string;
using std::shared_ptr;
using std::unique_ptr;

// InteropDLL internal globals (same DLL as the JNI glue)
extern unique_ptr<Emulator> _emu;
extern unique_ptr<IKeyManager> _keyManager;
extern unique_ptr<IMouseManager> _mouseManager;

// Keep Java-registered adapters alive (the core only holds raw/weak pointers)
static shared_ptr<JavaNotificationListener> _javaNotificationListener;
static shared_ptr<JavaAudioDevice> _javaAudioDevice;
static shared_ptr<JavaRenderingDevice> _javaRenderingDevice;

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

// --- Interface callback registration (Core/Shared/Interfaces) ------------

JNIEXPORT void JNICALL Java_mesen_MesenCore_registerNotificationListener(JNIEnv* env, jclass, jobject listener)
{
	if(!listener || !_emu) return;
	_javaNotificationListener = shared_ptr<JavaNotificationListener>(new JavaNotificationListener(env, listener));
	_emu->GetNotificationManager()->RegisterNotificationListener(_javaNotificationListener);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_registerAudioDevice(JNIEnv* env, jclass, jobject device)
{
	if(!device || !_emu) return;
	_javaAudioDevice = shared_ptr<JavaAudioDevice>(new JavaAudioDevice(env, device));
	_emu->GetSoundMixer()->RegisterAudioDevice(_javaAudioDevice.get());
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_registerKeyManager(JNIEnv* env, jclass, jobject manager)
{
	if(!manager || !_emu) return;
	JavaKeyManager* km = new JavaKeyManager(env, manager);
	_keyManager.reset(km);
	KeyManager::RegisterKeyManager(km);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_registerMouseManager(JNIEnv* env, jclass, jobject manager)
{
	if(!manager || !_emu) return;
	_mouseManager.reset(new JavaMouseManager(env, manager));
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_registerRenderingDevice(JNIEnv* env, jclass, jobject device)
{
	if(!device || !_emu) return;
	_javaRenderingDevice = shared_ptr<JavaRenderingDevice>(new JavaRenderingDevice(env, device));
	_emu->GetVideoRenderer()->RegisterRenderingDevice(_javaRenderingDevice.get());
}

// --- Lifecycle / info -----------------------------------------------------

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_testDll(JNIEnv*, jclass)
{
	return TestDll() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_mesen_MesenCore_getMesenVersion(JNIEnv*, jclass)
{
	return (jint)GetMesenVersion();
}

JNIEXPORT jstring JNICALL Java_mesen_MesenCore_getMesenBuildDate(JNIEnv* env, jclass)
{
	return Utf8ToJString(env, GetMesenBuildDate());
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_initDll(JNIEnv*, jclass)
{
	InitDll();
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_initializeEmu(JNIEnv* env, jclass, jstring homeFolder, jboolean softwareRenderer, jboolean noAudio, jboolean noVideo, jboolean noInput)
{
	string home = JStringToUtf8(env, homeFolder);
	// Headless: null window/viewer handles. The software renderer is created
	// when softwareRenderer is true (see InitializeEmu).
	InitializeEmu(home.c_str(), nullptr, nullptr, softwareRenderer, noAudio, noVideo, noInput);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_release(JNIEnv*, jclass)
{
	Release();
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_isRunning(JNIEnv*, jclass)
{
	return IsRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_stop(JNIEnv*, jclass)
{
	Stop();
}

JNIEXPORT jint JNICALL Java_mesen_MesenCore_getStopCode(JNIEnv*, jclass)
{
	return (jint)GetStopCode();
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_pause(JNIEnv*, jclass)
{
	Pause();
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_resume(JNIEnv*, jclass)
{
	Resume();
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_isPaused(JNIEnv*, jclass)
{
	return IsPaused() ? JNI_TRUE : JNI_FALSE;
}

// --- ROM ------------------------------------------------------------------

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_loadRom(JNIEnv* env, jclass, jstring filename, jstring patchFile)
{
	string fn = JStringToUtf8(env, filename);
	string pf = JStringToUtf8(env, patchFile);
	return LoadRom(fn.empty() ? nullptr : fn.data(), pf.empty() ? nullptr : pf.data()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_loadRecentGame(JNIEnv* env, jclass, jstring filepath, jboolean resetGame)
{
	string fp = JStringToUtf8(env, filepath);
	LoadRecentGame(fp.empty() ? nullptr : fp.data(), resetGame);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_addKnownGameFolder(JNIEnv* env, jclass, jstring folder)
{
	string f = JStringToUtf8(env, folder);
	AddKnownGameFolder(f.empty() ? nullptr : f.data());
}

JNIEXPORT jobject JNICALL Java_mesen_MesenCore_getRomInfo(JNIEnv* env, jclass)
{
	InteropRomInfo info;
	memset(&info, 0, sizeof(info));
	GetRomInfo(info);

	jclass cls = env->FindClass("mesen/RomInfo");
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

JNIEXPORT jstring JNICALL Java_mesen_MesenCore_getRomHash(JNIEnv* env, jclass, jint hashType)
{
	std::vector<char> buf(1000000, 0);
	GetRomHash((HashType)hashType, buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

// --- State ----------------------------------------------------------------

JNIEXPORT void JNICALL Java_mesen_MesenCore_saveState(JNIEnv*, jclass, jint slot)
{
	SaveState((uint32_t)slot);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_loadState(JNIEnv*, jclass, jint slot)
{
	LoadState((uint32_t)slot);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_saveStateFile(JNIEnv* env, jclass, jstring filepath)
{
	string fp = JStringToUtf8(env, filepath);
	SaveStateFile(fp.empty() ? nullptr : fp.data());
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_loadStateFile(JNIEnv* env, jclass, jstring filepath)
{
	string fp = JStringToUtf8(env, filepath);
	LoadStateFile(fp.empty() ? nullptr : fp.data());
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_takeScreenshot(JNIEnv*, jclass)
{
	TakeScreenshot();
}

// --- Memory ---------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_mesen_MesenMemory_getMemorySize(JNIEnv*, jclass, jint memoryType)
{
	return (jlong)GetMemorySize((MemoryType)memoryType);
}

JNIEXPORT jint JNICALL Java_mesen_MesenMemory_getMemoryValue(JNIEnv*, jclass, jint memoryType, jlong address)
{
	return (jint)GetMemoryValue((MemoryType)memoryType, (uint32_t)address);
}

JNIEXPORT jbyteArray JNICALL Java_mesen_MesenMemory_getMemoryValues(JNIEnv* env, jclass, jint memoryType, jlong start, jlong end)
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

JNIEXPORT void JNICALL Java_mesen_MesenMemory_setMemoryValue(JNIEnv*, jclass, jint memoryType, jlong address, jint value)
{
	SetMemoryValue((MemoryType)memoryType, (uint32_t)address, (uint8_t)value);
}

JNIEXPORT void JNICALL Java_mesen_MesenMemory_setMemoryValues(JNIEnv* env, jclass, jint memoryType, jlong address, jbyteArray data)
{
	jsize len = env->GetArrayLength(data);
	jbyte* bytes = env->GetByteArrayElements(data, nullptr);
	if(bytes) {
		SetMemoryValues((MemoryType)memoryType, (uint32_t)address, (uint8_t*)bytes, (int32_t)len);
		env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
	}
}

// --- Lua scripting --------------------------------------------------------

JNIEXPORT jint JNICALL Java_mesen_MesenScript_loadScript(JNIEnv* env, jclass, jstring name, jstring path, jstring content, jint scriptId)
{
	string n = JStringToUtf8(env, name);
	string p = JStringToUtf8(env, path);
	string c = JStringToUtf8(env, content);
	return (jint)LoadScript(n.data(), p.data(), c.data(), scriptId);
}

JNIEXPORT void JNICALL Java_mesen_MesenScript_removeScript(JNIEnv*, jclass, jint scriptId)
{
	RemoveScript(scriptId);
}

JNIEXPORT jstring JNICALL Java_mesen_MesenScript_getScriptLog(JNIEnv* env, jclass, jint scriptId)
{
	std::vector<char> buf(100000, 0);
	GetScriptLog(scriptId, buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

// --- Config ---------------------------------------------------------------

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setEmulationFlag(JNIEnv*, jclass, jint flag, jboolean enabled)
{
	SetEmulationFlag((EmulationFlags)flag, enabled);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setDebuggerFlag(JNIEnv*, jclass, jint flag, jboolean enabled)
{
	SetDebuggerFlag((DebuggerFlags)flag, enabled);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setEmulationConfig(JNIEnv*, jclass, jint emulationSpeed, jint turboSpeed, jint rewindSpeed, jint runAheadFrames)
{
	EmulationConfig cfg = GetEmulationConfig();
	cfg.EmulationSpeed = (uint32_t)emulationSpeed;
	cfg.TurboSpeed = (uint32_t)turboSpeed;
	cfg.RewindSpeed = (uint32_t)rewindSpeed;
	cfg.RunAheadFrames = (uint32_t)runAheadFrames;
	SetEmulationConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setVideoConfig(JNIEnv*, jclass, jint videoFilter, jint aspectRatio, jdouble customAspectRatio, jint screenRotation)
{
	VideoConfig cfg = GetVideoConfig();
	cfg.VideoFilter = (VideoFilterType)videoFilter;
	cfg.AspectRatio = (VideoAspectRatio)aspectRatio;
	cfg.CustomAspectRatio = customAspectRatio;
	cfg.ScreenRotation = (uint32_t)screenRotation;
	SetVideoConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setAudioConfig(JNIEnv*, jclass, jboolean enableAudio, jint masterVolume, jint sampleRate, jint audioLatency)
{
	AudioConfig cfg = GetAudioConfig();
	cfg.EnableAudio = enableAudio;
	cfg.MasterVolume = (uint32_t)masterVolume;
	cfg.SampleRate = (uint32_t)sampleRate;
	cfg.AudioLatency = (uint32_t)audioLatency;
	SetAudioConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setControllerType(JNIEnv*, jclass, jint consoleType, jint port, jint controllerType)
{
	ApplyControllerConfig(consoleType, port, [&](ControllerConfig& cfg) {
		cfg.Type = (ControllerType)controllerType;
	});
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setControllerKey(JNIEnv*, jclass, jint consoleType, jint port, jint button, jint keyCode)
{
	ApplyControllerConfig(consoleType, port, [&](ControllerConfig& cfg) {
		uint16_t* field = GetKeyMappingField(cfg.Keys.Mapping1, button);
		if(field) {
			*field = (uint16_t)keyCode;
		}
	});
}

// --- Palette config -------------------------------------------------------

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setNesCustomPalette(JNIEnv* env, jclass, jintArray palette)
{
	NesConfig cfg = GetNesConfig();
	jsize len = env->GetArrayLength(palette);
	if(len > 512) len = 512;
	jint* data = env->GetIntArrayElements(palette, nullptr);
	if(data && len > 0) {
		memcpy(cfg.UserPalette, data, len * sizeof(uint32_t));
	}
	if(data) env->ReleaseIntArrayElements(palette, data, JNI_ABORT);
	SetNesConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setNesFullColorPalette(JNIEnv*, jclass, jboolean enabled)
{
	NesConfig cfg = GetNesConfig();
	cfg.IsFullColorPalette = enabled;
	SetNesConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setNesUseCustomVsPalette(JNIEnv*, jclass, jboolean enabled)
{
	NesConfig cfg = GetNesConfig();
	cfg.UseCustomVsPalette = enabled;
	SetNesConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setNesRegion(JNIEnv*, jclass, jint region)
{
	NesConfig cfg = GetNesConfig();
	cfg.Region = (ConsoleRegion)region;
	SetNesConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setNesRemoveSpriteLimit(JNIEnv*, jclass, jboolean enabled)
{
	NesConfig cfg = GetNesConfig();
	cfg.RemoveSpriteLimit = enabled;
	SetNesConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setPceCustomPalette(JNIEnv* env, jclass, jintArray palette)
{
	PcEngineConfig cfg = GetPcEngineConfig();
	jsize len = env->GetArrayLength(palette);
	if(len > 512) len = 512;
	jint* data = env->GetIntArrayElements(palette, nullptr);
	if(data && len > 0) {
		memcpy(cfg.Palette, data, len * sizeof(uint32_t));
	}
	if(data) env->ReleaseIntArrayElements(palette, data, JNI_ABORT);
	SetPcEngineConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setGameboyPalette(JNIEnv* env, jclass, jintArray bg, jintArray obj0, jintArray obj1)
{
	GameboyConfig cfg = GetGameboyConfig();

	auto copy4 = [&](jintArray arr, uint32_t* dest) {
		if(!arr) return;
		jsize len = env->GetArrayLength(arr);
		if(len > 4) len = 4;
		jint* data = env->GetIntArrayElements(arr, nullptr);
		if(data) {
			for(jsize i = 0; i < len; i++) {
				dest[i] = (uint32_t)data[i];
			}
			env->ReleaseIntArrayElements(arr, data, JNI_ABORT);
		}
	};

	copy4(bg, cfg.BgColors);
	copy4(obj0, cfg.Obj0Colors);
	copy4(obj1, cfg.Obj1Colors);
	SetGameboyConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setSmsUseSgPalette(JNIEnv*, jclass, jboolean enabled)
{
	SmsConfig cfg = GetSmsConfig();
	cfg.UseSgPalette = enabled;
	SetSmsConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setGbaAdjustColors(JNIEnv*, jclass, jboolean enabled)
{
	GbaConfig cfg = GetGbaConfig();
	cfg.GbaAdjustColors = enabled;
	SetGbaConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setWsLcdAdjustColors(JNIEnv*, jclass, jboolean enabled)
{
	WsConfig cfg = GetWsConfig();
	cfg.LcdAdjustColors = enabled;
	SetWsConfig(cfg);
}

JNIEXPORT void JNICALL Java_mesen_MesenConfig_setRendererSize(JNIEnv*, jclass, jint width, jint height)
{
	SetRendererSize((uint32_t)width, (uint32_t)height);
}

JNIEXPORT jdouble JNICALL Java_mesen_MesenConfig_getAspectRatio(JNIEnv*, jclass)
{
	return (jdouble)GetAspectRatio();
}

JNIEXPORT jobject JNICALL Java_mesen_MesenConfig_getBaseScreenSize(JNIEnv* env, jclass)
{
	FrameInfo f = GetBaseScreenSize();
	jclass cls = env->FindClass("mesen/FrameInfo");
	jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
	jobject obj = env->NewObject(cls, ctor);
	env->SetIntField(obj, env->GetFieldID(cls, "width", "I"), (jint)f.Width);
	env->SetIntField(obj, env->GetFieldID(cls, "height", "I"), (jint)f.Height);
	return obj;
}

JNIEXPORT jobject JNICALL Java_mesen_MesenConfig_getTimingInfo(JNIEnv* env, jclass, jint cpuType)
{
	TimingInfo t = GetTimingInfo((CpuType)cpuType);
	jclass cls = env->FindClass("mesen/TimingInfo");
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

JNIEXPORT void JNICALL Java_mesen_MesenInput_setKeyState(JNIEnv*, jclass, jint scanCode, jboolean state)
{
	SetKeyState((uint16_t)scanCode, state);
}

JNIEXPORT void JNICALL Java_mesen_MesenInput_resetKeyState(JNIEnv*, jclass)
{
	ResetKeyState();
}

JNIEXPORT void JNICALL Java_mesen_MesenInput_disableAllKeys(JNIEnv*, jclass, jboolean disabled)
{
	DisableAllKeys(disabled);
}

JNIEXPORT void JNICALL Java_mesen_MesenInput_updateInputDevices(JNIEnv*, jclass)
{
	UpdateInputDevices();
}

JNIEXPORT jint JNICALL Java_mesen_MesenInput_getKeyCode(JNIEnv* env, jclass, jstring keyName)
{
	string kn = JStringToUtf8(env, keyName);
	return (jint)GetKeyCode(kn.empty() ? nullptr : kn.data());
}

JNIEXPORT jstring JNICALL Java_mesen_MesenInput_getKeyName(JNIEnv* env, jclass, jint keyCode)
{
	std::vector<char> buf(256, 0);
	GetKeyName((uint16_t)keyCode, buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

JNIEXPORT void JNICALL Java_mesen_MesenInput_setMousePosition(JNIEnv*, jclass, jdouble x, jdouble y)
{
	SetMousePosition(x, y);
}

JNIEXPORT void JNICALL Java_mesen_MesenInput_setMouseMovement(JNIEnv*, jclass, jint x, jint y)
{
	SetMouseMovement((int16_t)x, (int16_t)y);
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenInput_hasControlDevice(JNIEnv*, jclass, jint controllerType)
{
	return HasControlDevice((ControllerType)controllerType) ? JNI_TRUE : JNI_FALSE;
}

// --- Log / messages -------------------------------------------------------

JNIEXPORT jstring JNICALL Java_mesen_MesenCore_getLog(JNIEnv* env, jclass)
{
	std::vector<char> buf(100000, 0);
	GetLog(buf.data(), (uint32_t)buf.size());
	return Utf8ToJString(env, buf.data());
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_writeLogEntry(JNIEnv* env, jclass, jstring message)
{
	string m = JStringToUtf8(env, message);
	WriteLogEntry(m.empty() ? nullptr : m.data());
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_displayMessage(JNIEnv* env, jclass, jstring title, jstring message, jstring param)
{
	string t = JStringToUtf8(env, title);
	string m = JStringToUtf8(env, message);
	string p = JStringToUtf8(env, param);
	DisplayMessage(t.empty() ? nullptr : t.data(), m.empty() ? nullptr : m.data(), p.empty() ? nullptr : p.data());
}

// --- Software-rendered frame ----------------------------------------------

JNIEXPORT jint JNICALL Java_mesen_MesenVideo_getFrameWidth(JNIEnv*, jclass)
{
	uint32_t width = 0, height = 0;
	uint32_t* buffer = nullptr;
	GetFrameBuffer(width, height, buffer);
	return (jint)width;
}

JNIEXPORT jint JNICALL Java_mesen_MesenVideo_getFrameHeight(JNIEnv*, jclass)
{
	uint32_t width = 0, height = 0;
	uint32_t* buffer = nullptr;
	GetFrameBuffer(width, height, buffer);
	return (jint)height;
}

JNIEXPORT void JNICALL Java_mesen_MesenVideo_copyFrameBuffer(JNIEnv* env, jclass, jintArray argb)
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

// --- Captured audio -------------------------------------------------------

JNIEXPORT jint JNICALL Java_mesen_MesenAudio_getAudioSampleRate(JNIEnv*, jclass)
{
	return (jint)GetAudioSampleRate();
}

JNIEXPORT jint JNICALL Java_mesen_MesenAudio_readAudioSamples(JNIEnv* env, jclass, jshortArray out)
{
	jsize capacity = env->GetArrayLength(out);
	if(capacity <= 0) {
		return 0;
	}

	jshort* dest = env->GetShortArrayElements(out, nullptr);
	if(!dest) {
		return 0;
	}

	uint32_t count = ReadAudioSamples((int16_t*)dest, (uint32_t)capacity);
	env->ReleaseShortArrayElements(out, dest, 0);
	return (jint)count;
}

// ---------------------------------------------------------------------------
// Recording: AVI / WAV / movie files
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL Java_mesen_MesenCore_aviRecord(JNIEnv* env, jclass, jstring filename,
	jint codec, jint compressionLevel, jboolean recordSystemHud, jboolean recordInputHud)
{
	if(!filename) {
		return;
	}

	const char* chars = env->GetStringUTFChars(filename, nullptr);
	if(chars) {
		RecordAviOptions options = {};
		options.Codec = (VideoCodec)codec;
		options.CompressionLevel = (uint32_t)compressionLevel;
		options.RecordSystemHud = recordSystemHud == JNI_TRUE;
		options.RecordInputHud = recordInputHud == JNI_TRUE;
		AviRecord(const_cast<char*>(chars), options);
		env->ReleaseStringUTFChars(filename, chars);
	}
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_aviStop(JNIEnv*, jclass)
{
	AviStop();
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_aviIsRecording(JNIEnv*, jclass)
{
	return AviIsRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_waveRecord(JNIEnv* env, jclass, jstring filename)
{
	if(!filename) {
		return;
	}

	const char* chars = env->GetStringUTFChars(filename, nullptr);
	if(chars) {
		WaveRecord(const_cast<char*>(chars));
		env->ReleaseStringUTFChars(filename, chars);
	}
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_waveStop(JNIEnv*, jclass)
{
	WaveStop();
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_waveIsRecording(JNIEnv*, jclass)
{
	return WaveIsRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_movieRecord(JNIEnv* env, jclass, jstring filename,
	jstring author, jstring description, jint recordFrom)
{
	if(!filename) {
		return;
	}

	const char* fileChars = env->GetStringUTFChars(filename, nullptr);
	if(!fileChars) {
		return;
	}

	RecordMovieOptions options = {};
	strncpy(options.Filename, fileChars, sizeof(options.Filename) - 1);
	env->ReleaseStringUTFChars(filename, fileChars);

	if(author) {
		const char* chars = env->GetStringUTFChars(author, nullptr);
		if(chars) {
			strncpy(options.Author, chars, sizeof(options.Author) - 1);
			env->ReleaseStringUTFChars(author, chars);
		}
	}

	if(description) {
		const char* chars = env->GetStringUTFChars(description, nullptr);
		if(chars) {
			strncpy(options.Description, chars, sizeof(options.Description) - 1);
			env->ReleaseStringUTFChars(description, chars);
		}
	}

	options.RecordFrom = (RecordMovieFrom)recordFrom;
	MovieRecord(options);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_moviePlay(JNIEnv* env, jclass, jstring filename)
{
	if(!filename) {
		return;
	}

	const char* chars = env->GetStringUTFChars(filename, nullptr);
	if(chars) {
		MoviePlay(const_cast<char*>(chars));
		env->ReleaseStringUTFChars(filename, chars);
	}
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_movieStop(JNIEnv*, jclass)
{
	MovieStop();
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_moviePlaying(JNIEnv*, jclass)
{
	return MoviePlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_movieRecording(JNIEnv*, jclass)
{
	return MovieRecording() ? JNI_TRUE : JNI_FALSE;
}
// ---------------------------------------------------------------------------
// Cheats / save state preview / archive listing / misc
// ---------------------------------------------------------------------------

void ReadCheatCode(JNIEnv* env, jobject obj, jclass cls, CheatCode& out)
{
	out = {};
	if(!obj) {
		return;
	}

	out.Type = (CheatType)env->GetIntField(obj, env->GetFieldID(cls, "type", "I"));
	jstring code = (jstring)env->GetObjectField(obj, env->GetFieldID(cls, "code", "Ljava/lang/String;"));
	if(code) {
		const char* chars = env->GetStringUTFChars(code, nullptr);
		if(chars) {
			strncpy(out.Code, chars, sizeof(out.Code) - 1);
			env->ReleaseStringUTFChars(code, chars);
		}
		env->DeleteLocalRef(code);
	}
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_setCheats(JNIEnv* env, jclass, jobjectArray codes)
{
	jsize count = codes ? env->GetArrayLength(codes) : 0;
	if(count <= 0) {
		ClearCheats();
		return;
	}

	jclass cheatClass = env->FindClass("mesen/CheatCode");
	std::vector<CheatCode> cheats((size_t)count);
	for(jsize i = 0; i < count; i++) {
		jobject item = env->GetObjectArrayElement(codes, i);
		ReadCheatCode(env, item, cheatClass, cheats[i]);
		if(item) {
			env->DeleteLocalRef(item);
		}
	}

	SetCheats(cheats.data(), (uint32_t)count);
	env->DeleteLocalRef(cheatClass);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_clearCheats(JNIEnv*, jclass)
{
	ClearCheats();
}

JNIEXPORT jobject JNICALL Java_mesen_MesenCore_getConvertedCheat(JNIEnv* env, jclass, jobject input)
{
	jclass cheatClass = env->FindClass("mesen/CheatCode");
	CheatCode cheat = {};
	ReadCheatCode(env, input, cheatClass, cheat);
	env->DeleteLocalRef(cheatClass);

	InternalCheatCode converted = {};
	if(!GetConvertedCheat(cheat, converted)) {
		return nullptr;
	}

	jclass cls = env->FindClass("mesen/InternalCheatCode");
	jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
	jobject obj = env->NewObject(cls, ctor);
	env->SetIntField(obj, env->GetFieldID(cls, "memoryType", "I"), (jint)converted.MemType);
	env->SetIntField(obj, env->GetFieldID(cls, "address", "I"), (jint)converted.Address);
	env->SetIntField(obj, env->GetFieldID(cls, "compare", "I"), (jint)converted.Compare);
	env->SetIntField(obj, env->GetFieldID(cls, "value", "I"), (jint)converted.Value);
	env->SetIntField(obj, env->GetFieldID(cls, "cheatType", "I"), (jint)converted.Type);
	env->SetIntField(obj, env->GetFieldID(cls, "cpuType", "I"), (jint)converted.Cpu);
	env->SetBooleanField(obj, env->GetFieldID(cls, "isRamCode", "Z"), converted.IsRamCode ? JNI_TRUE : JNI_FALSE);
	env->SetBooleanField(obj, env->GetFieldID(cls, "isAbsoluteAddress", "Z"), converted.IsAbsoluteAddress ? JNI_TRUE : JNI_FALSE);
	env->DeleteLocalRef(cls);
	return obj;
}

JNIEXPORT jbyteArray JNICALL Java_mesen_MesenCore_getSaveStatePreview(JNIEnv* env, jclass, jstring path)
{
	if(!path) {
		return env->NewByteArray(0);
	}

	const char* chars = env->GetStringUTFChars(path, nullptr);
	if(!chars) {
		return env->NewByteArray(0);
	}

	// Same buffer size the .NET interop uses.
	std::vector<uint8_t> buffer(512 * 478 * 4, 0);
	int32_t size = GetSaveStatePreview(const_cast<char*>(chars), buffer.data());
	env->ReleaseStringUTFChars(path, chars);

	if(size <= 0) {
		return env->NewByteArray(0);
	}
	if((size_t)size > buffer.size()) {
		size = (int32_t)buffer.size();
	}

	jbyteArray result = env->NewByteArray(size);
	env->SetByteArrayRegion(result, 0, size, (const jbyte*)buffer.data());
	return result;
}

JNIEXPORT jobjectArray JNICALL Java_mesen_MesenCore_getArchiveRomList(JNIEnv* env, jclass, jstring path)
{
	if(!path) {
		return nullptr;
	}

	const char* chars = env->GetStringUTFChars(path, nullptr);
	if(!chars) {
		return nullptr;
	}

	std::vector<char> buffer(1024 * 1024, 0);
	GetArchiveRomList(const_cast<char*>(chars), buffer.data(), (uint32_t)buffer.size());
	env->ReleaseStringUTFChars(path, chars);

	// The core writes a newline-separated list.
	std::vector<std::string> entries;
	std::string current;
	for(char c : buffer) {
		if(c == 0) {
			break;
		}
		if(c == '\n' || c == '\r') {
			if(!current.empty()) {
				entries.push_back(current);
				current.clear();
			}
		} else {
			current += c;
		}
	}
	if(!current.empty()) {
		entries.push_back(current);
	}

	jclass stringClass = env->FindClass("java/lang/String");
	jobjectArray result = env->NewObjectArray((jsize)entries.size(), stringClass, nullptr);
	for(size_t i = 0; i < entries.size(); i++) {
		jstring item = env->NewStringUTF(entries[i].c_str());
		env->SetObjectArrayElement(result, (jsize)i, item);
		env->DeleteLocalRef(item);
	}
	env->DeleteLocalRef(stringClass);
	return result;
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_setFullscreenMode(JNIEnv*, jclass, jlong windowHandle, jint width, jint height, jint mode)
{
	FullscreenSettings settings = {};
	settings.WindowHandle = (void*)(intptr_t)windowHandle;
	settings.Width = (uint32_t)width;
	settings.Height = (uint32_t)height;
	settings.Mode = (FullscreenMode)mode;
	SetFullscreenMode(settings);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_inputBarcode(JNIEnv*, jclass, jlong barcode, jint digitCount)
{
	InputBarcode((uint64_t)barcode, (uint32_t)digitCount);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_processTapeRecorderAction(JNIEnv* env, jclass, jint action, jstring filename)
{
	const char* chars = filename ? env->GetStringUTFChars(filename, nullptr) : nullptr;
	ProcessTapeRecorderAction((TapeRecorderAction)action, const_cast<char*>(chars ? chars : ""));
	if(chars) {
		env->ReleaseStringUTFChars(filename, chars);
	}
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_processAudioPlayerAction(JNIEnv*, jclass, jint action, jint trackNumber)
{
	AudioPlayerActionParams params = {};
	params.Action = (AudioPlayerAction)action;
	params.TrackNumber = (uint32_t)trackNumber;
	ProcessAudioPlayerAction(params);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_resetLagCounter(JNIEnv*, jclass)
{
	ResetLagCounter();
}
// ---------------------------------------------------------------------------
// Game / preferences / audio player config
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL Java_mesen_MesenCore_setGameConfig(JNIEnv* env, jclass, jobject obj)
{
	if(!obj) {
		return;
	}

	jclass cls = env->FindClass("mesen/GameConfig");
	GameConfig config = {};
	config.DipSwitches = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "dipSwitches", "I"));
	config.OverrideOverscan = env->GetBooleanField(obj, env->GetFieldID(cls, "overrideOverscan", "Z")) == JNI_TRUE;
	config.Overscan.Left = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "overscanLeft", "I"));
	config.Overscan.Right = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "overscanRight", "I"));
	config.Overscan.Top = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "overscanTop", "I"));
	config.Overscan.Bottom = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "overscanBottom", "I"));
	SetGameConfig(config);
	env->DeleteLocalRef(cls);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_setPreferences(JNIEnv* env, jclass, jobject obj)
{
	if(!obj) {
		return;
	}

	jclass cls = env->FindClass("mesen/PreferencesConfig");
	PreferencesConfig config = {};

	config.ShowFps = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowFps", "Z")) == JNI_TRUE;
	config.ShowFrameCounter = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowFrameCounter", "Z")) == JNI_TRUE;
	config.ShowGameTimer = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowGameTimer", "Z")) == JNI_TRUE;
	config.ShowLagCounter = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowLagCounter", "Z")) == JNI_TRUE;
	config.ShowDebugInfo = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowDebugInfo", "Z")) == JNI_TRUE;
	config.DisableOsd = env->GetBooleanField(obj, env->GetFieldID(cls, "DisableOsd", "Z")) == JNI_TRUE;
	config.AllowBackgroundInput = env->GetBooleanField(obj, env->GetFieldID(cls, "AllowBackgroundInput", "Z")) == JNI_TRUE;
	config.PauseOnMovieEnd = env->GetBooleanField(obj, env->GetFieldID(cls, "PauseOnMovieEnd", "Z")) == JNI_TRUE;
	config.ShowMovieIcons = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowMovieIcons", "Z")) == JNI_TRUE;
	config.ShowTurboRewindIcons = env->GetBooleanField(obj, env->GetFieldID(cls, "ShowTurboRewindIcons", "Z")) == JNI_TRUE;
	config.DisableGameSelectionScreen = env->GetBooleanField(obj, env->GetFieldID(cls, "DisableGameSelectionScreen", "Z")) == JNI_TRUE;

	config.HudSize = (HudDisplaySize)env->GetIntField(obj, env->GetFieldID(cls, "HudSize", "I"));
	config.AutoSaveStateDelay = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "AutoSaveStateDelay", "I"));
	config.RewindBufferSize = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "RewindBufferSize", "I"));

	// The three folder-override fields are const char* and SetPreferences only
	// assigns the struct without copying, so they stay null (= defaults).
	config.SaveFolderOverride = nullptr;
	config.SaveStateFolderOverride = nullptr;
	config.ScreenshotFolderOverride = nullptr;

	SetPreferences(config);
	env->DeleteLocalRef(cls);
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_setAudioPlayerConfig(JNIEnv* env, jclass, jobject obj)
{
	if(!obj) {
		return;
	}

	jclass cls = env->FindClass("mesen/AudioPlayerConfig");
	AudioPlayerConfig config = {};
	config.Volume = (uint32_t)env->GetIntField(obj, env->GetFieldID(cls, "volume", "I"));
	config.Repeat = env->GetBooleanField(obj, env->GetFieldID(cls, "repeat", "Z")) == JNI_TRUE;
	config.Shuffle = env->GetBooleanField(obj, env->GetFieldID(cls, "shuffle", "Z")) == JNI_TRUE;
	SetAudioPlayerConfig(config);
	env->DeleteLocalRef(cls);
}
// ---------------------------------------------------------------------------
// Shortcuts
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL Java_mesen_MesenCore_executeShortcut(JNIEnv*, jclass, jint shortcut, jint param)
{
	ExecuteShortcutParams params = {};
	params.Shortcut = (EmulatorShortcut)shortcut;
	params.Param = (uint32_t)param;
	params.ParamPtr = nullptr;
	ExecuteShortcut(params);
}

JNIEXPORT jboolean JNICALL Java_mesen_MesenCore_isShortcutAllowed(JNIEnv*, jclass, jint shortcut, jint param)
{
	return IsShortcutAllowed((EmulatorShortcut)shortcut, (uint32_t)param) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_mesen_MesenCore_setShortcutKeys(JNIEnv* env, jclass, jobjectArray shortcuts)
{
	jsize count = shortcuts ? env->GetArrayLength(shortcuts) : 0;
	if(count <= 0) {
		return;
	}

	jclass cls = env->FindClass("mesen/ShortcutKeyInfo");
	jfieldID shortcutField = env->GetFieldID(cls, "shortcut", "I");
	jfieldID key1Field = env->GetFieldID(cls, "key1", "I");
	jfieldID key2Field = env->GetFieldID(cls, "key2", "I");
	jfieldID key3Field = env->GetFieldID(cls, "key3", "I");

	std::vector<ShortcutKeyInfo> infos((size_t)count);
	for(jsize i = 0; i < count; i++) {
		jobject item = env->GetObjectArrayElement(shortcuts, i);
		if(item) {
			infos[i].Shortcut = (EmulatorShortcut)env->GetIntField(item, shortcutField);
			infos[i].Keys.Key1 = (uint32_t)env->GetIntField(item, key1Field);
			infos[i].Keys.Key2 = (uint32_t)env->GetIntField(item, key2Field);
			infos[i].Keys.Key3 = (uint32_t)env->GetIntField(item, key3Field);
			env->DeleteLocalRef(item);
		}
	}

	SetShortcutKeys(infos.data(), (uint32_t)count);
	env->DeleteLocalRef(cls);
}
} // extern "C"
