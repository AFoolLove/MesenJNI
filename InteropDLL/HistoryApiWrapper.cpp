#include "Common.h"
#include "Core/Shared/Emulator.h"
#include "Core/Shared/EmuSettings.h"
#include "Core/Shared/RewindManager.h"
#include "Core/Shared/HistoryViewer.h"
#include "Core/Shared/Interfaces/IRenderingDevice.h"
#include "Core/Shared/Interfaces/IAudioDevice.h"
#include "Shared/Video/SoftwareRenderer.h"
#include "InteropNotificationListeners.h"

#ifdef _WIN32
	#include "Windows/Renderer.h"
	#include "Windows/SoundManager.h"
#elif __APPLE__
	#include "Sdl/SdlSoundManager.h"
#elif defined(__ANDROID__)
// Android headless: no native window/audio platform layer.
#else
	#include "Linux/LinuxOglRenderer.h"
	#include "Sdl/SdlSoundManager.h"
#endif

extern unique_ptr<Emulator> _emu;
extern bool _softwareRenderer;
extern bool _noAudio;

unique_ptr<Emulator> _historyPlayer;
unique_ptr<IRenderingDevice> _historyRenderer;
unique_ptr<IAudioDevice> _historySoundManager;

HistoryViewer* _historyViewer = nullptr;

static InteropNotificationListeners _listeners;

extern "C"
{
	DllExport bool __stdcall HistoryViewerEnabled()
	{
		return _emu->GetRewindManager()->HasHistory();
	}

	DllExport void __stdcall HistoryViewerRelease()
	{
		_historyPlayer->Release();
		_historyRenderer.reset();
		_historySoundManager.reset();
		_historyPlayer.reset();
		_historyViewer = nullptr;
	}

	DllExport void __stdcall HistoryViewerInitialize(void* windowHandle, void* viewerHandle)
	{
		_historyPlayer.reset(new Emulator());
		_historyPlayer->Initialize();
		_historyPlayer->GetSettings()->CopySettings(*_emu->GetSettings());

		_historyViewer = _historyPlayer->GetHistoryViewer();
		if(!_historyViewer->Initialize(_emu.get())) {
			HistoryViewerRelease();
			return;
		}

		_historyPlayer->GetSettings()->GetEmulationConfig().EmulationSpeed = 100;

		if(_softwareRenderer) {
			_historyRenderer.reset(new SoftwareRenderer(_historyPlayer.get()));
		} else {
#if defined(_WIN32)
			_historyRenderer.reset(new Renderer(_historyPlayer.get(), (HWND)viewerHandle));
#elif defined(__ANDROID__) || defined(__APPLE__)
			_historyRenderer.reset(new SoftwareRenderer(_historyPlayer.get()));
#else
			_historyRenderer.reset(new LinuxOglRenderer(_historyPlayer.get(), viewerHandle));
#endif
		}

		// Mirror the core's own noAudio handling: do not build the history viewer's
		// audio device when audio is disabled - its Emulator never gets the audio init
		// callback, and creating a native device here crashed (null read).
		if(!_noAudio) {
#ifdef _WIN32
			_historySoundManager.reset(SoundManager::Create(_historyPlayer.get(), (HWND)windowHandle));
#elif defined(__ANDROID__)
			// Android: no native sound manager for the history viewer.
#else
			_historySoundManager.reset(SdlSoundManager::Create(_historyPlayer.get()));
#endif
		}
	}

	DllExport HistoryViewerState __stdcall HistoryViewerGetState()
	{
		return _historyViewer ? _historyViewer->GetState() : HistoryViewerState {};
	}

	DllExport void __stdcall HistoryViewerSetOptions(HistoryViewerOptions options)
	{
		if(_historyViewer) {
			_historyViewer->SetOptions(options);
		}
	}

	DllExport bool __stdcall HistoryViewerCreateSaveState(const char* outputFile, uint32_t position)
	{
		return _historyViewer ? _historyViewer->CreateSaveState(outputFile, position) : false;
	}

	DllExport bool __stdcall HistoryViewerSaveMovie(const char* movieFile, uint32_t startPosition, uint32_t endPosition)
	{
		return _historyViewer ? _historyViewer->SaveMovie(movieFile, startPosition, endPosition) : false;
	}

	DllExport void __stdcall HistoryViewerResumeGameplay(uint32_t resumePosition)
	{
		if(_historyViewer) {
			_historyViewer->ResumeGameplay(resumePosition);
		}
	}

	DllExport void __stdcall HistoryViewerSetPosition(uint32_t seekPosition)
	{
		if(_historyViewer) {
			_historyViewer->SeekTo(seekPosition);
		}
	}

	DllExport INotificationListener* __stdcall HistoryViewerRegisterNotificationCallback(NotificationListenerCallback callback)
	{
		return _listeners.RegisterNotificationCallback(callback, _historyPlayer.get());
	}

	DllExport void __stdcall HistoryViewerUnregisterNotificationCallback(INotificationListener* listener)
	{
		_listeners.UnregisterNotificationCallback(listener);
	}
}