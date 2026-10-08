#pragma once
// ---------------------------------------------------------------------------
// MesenApi.h
//
// Declarations for the MesenCore (InteropDLL) C API used by the JNI bindings.
// These functions are exported from the MesenCore shared library.
// ---------------------------------------------------------------------------

#include <cstdint>

#include "Core/Shared/RomInfo.h"       // RomFormat, DipSwitchInfo
#include "Core/Shared/SettingTypes.h"  // ConsoleType, FrameInfo, EmulationFlags, ControllerType
#include "Core/Shared/CpuType.h"       // CpuType
#include "Core/Shared/TimingInfo.h"    // TimingInfo
#include "Core/Shared/MemoryType.h"    // MemoryType
#include "Core/Shared/Emulator.h"      // HashType

#ifdef _WIN32
	#define MESEN_CALL __stdcall
#else
	#define MESEN_CALL
#endif

#define MESEN_EXTERN extern "C"

// Must match InteropRomInfo defined in InteropDLL/EmuApiWrapper.cpp exactly.
struct InteropRomInfo
{
	char RomPath[2000];
	char PatchPath[2000];
	RomFormat Format;
	ConsoleType Console;
	DipSwitchInfo DipSwitches;
	CpuType CpuTypes[5];
	uint32_t CpuTypeCount;
};

MESEN_EXTERN
{
	// --- EmuApiWrapper ---
	bool MESEN_CALL TestDll();
	uint32_t MESEN_CALL GetMesenVersion();
	const char* MESEN_CALL GetMesenBuildDate();
	void MESEN_CALL InitDll();
	void MESEN_CALL InitializeEmu(const char* homeFolder, void* windowHandle, void* viewerHandle, bool softwareRenderer, bool noAudio, bool noVideo, bool noInput);
	bool MESEN_CALL LoadRom(char* filename, char* patchFile);
	void MESEN_CALL AddKnownGameFolder(char* folder);
	void MESEN_CALL GetRomInfo(InteropRomInfo& info);
	TimingInfo MESEN_CALL GetTimingInfo(CpuType cpuType);
	void MESEN_CALL TakeScreenshot();
	bool MESEN_CALL IsRunning();
	int32_t MESEN_CALL GetStopCode();
	void MESEN_CALL Stop();
	void MESEN_CALL Pause();
	void MESEN_CALL Resume();
	bool MESEN_CALL IsPaused();
	void MESEN_CALL Release();
	void MESEN_CALL DisplayMessage(char* title, char* message, char* param1);
	void MESEN_CALL GetLog(char* outBuffer, uint32_t maxLength);
	void MESEN_CALL SetRendererSize(uint32_t width, uint32_t height);
	double MESEN_CALL GetAspectRatio();
	FrameInfo MESEN_CALL GetBaseScreenSize();
	void MESEN_CALL GetFrameBuffer(uint32_t& width, uint32_t& height, uint32_t*& buffer);
	uint32_t MESEN_CALL GetGameMemorySize(MemoryType type);
	void MESEN_CALL GetRomHash(HashType hashType, char* outBuffer, uint32_t maxLength);
	void MESEN_CALL WriteLogEntry(char* message);
	void MESEN_CALL SaveState(uint32_t stateIndex);
	void MESEN_CALL LoadState(uint32_t stateIndex);
	void MESEN_CALL SaveStateFile(char* filepath);
	void MESEN_CALL LoadStateFile(char* filepath);
	void MESEN_CALL LoadRecentGame(char* filepath, bool resetGame);

	// --- ConfigApiWrapper ---
	void MESEN_CALL SetEmulationFlag(EmulationFlags flag, bool enabled);

	// --- DebugApiWrapper (memory access subset) ---
	uint32_t MESEN_CALL GetMemorySize(MemoryType type);
	uint8_t MESEN_CALL GetMemoryValue(MemoryType type, uint32_t address);
	void MESEN_CALL GetMemoryValues(MemoryType type, uint32_t start, uint32_t end, uint8_t* output);
	void MESEN_CALL SetMemoryValue(MemoryType type, uint32_t address, uint8_t value);
	void MESEN_CALL SetMemoryValues(MemoryType type, uint32_t address, uint8_t* data, int32_t length);

	// --- InputApiWrapper ---
	void MESEN_CALL SetMousePosition(double x, double y);
	void MESEN_CALL SetMouseMovement(int16_t x, int16_t y);
	void MESEN_CALL UpdateInputDevices();
	void MESEN_CALL DisableAllKeys(bool disabled);
	void MESEN_CALL SetKeyState(uint16_t scanCode, bool state);
	void MESEN_CALL ResetKeyState();
	void MESEN_CALL GetKeyName(uint16_t keyCode, char* outKeyName, uint32_t maxLength);
	uint16_t MESEN_CALL GetKeyCode(char* keyName);
	bool MESEN_CALL HasControlDevice(ControllerType type);
}
