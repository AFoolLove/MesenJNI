#pragma once
// ---------------------------------------------------------------------------
// JavaAdapters.h
//
// C++ adapter classes that let Java implement the Core/Shared/Interfaces
// interfaces. Each adapter holds a global reference to a Java object and
// forwards the C++ virtual calls back into Java.
// ---------------------------------------------------------------------------

#include <jni.h>
#include <string>

#include "Core/Shared/Interfaces/INotificationListener.h"
#include "Core/Shared/Interfaces/IAudioDevice.h"
#include "Core/Shared/Interfaces/IKeyManager.h"
#include "Core/Shared/Interfaces/IMouseManager.h"
#include "Core/Shared/Interfaces/IRenderingDevice.h"
#include "Core/Shared/RenderedFrame.h"

using std::string;

// Holds a global reference to a Java object and provides thread attach/env.
class JniCallback
{
public:
	JavaVM* vm = nullptr;
	jobject obj = nullptr;
	jclass cls = nullptr;

	JniCallback(JNIEnv* env, jobject o)
	{
		obj = env->NewGlobalRef(o);
		cls = (jclass)env->NewGlobalRef(env->GetObjectClass(o));
		env->GetJavaVM(&vm);
	}

	virtual ~JniCallback()
	{
		JNIEnv* env = getEnv();
		if(env) {
			env->DeleteGlobalRef(obj);
			env->DeleteGlobalRef(cls);
		}
	}

	JNIEnv* getEnv()
	{
		JNIEnv* env = nullptr;
		vm->AttachCurrentThread((void**)&env, nullptr);
		return env;
	}
};

class JavaNotificationListener : public JniCallback, public INotificationListener
{
private:
	jmethodID _process;
public:
	JavaNotificationListener(JNIEnv* env, jobject o) : JniCallback(env, o)
	{
		_process = env->GetMethodID(cls, "processNotification", "(I)V");
	}

	void ProcessNotification(ConsoleNotificationType type, void* parameter) override
	{
		JNIEnv* env = getEnv();
		if(env && _process) {
			env->CallVoidMethod(obj, _process, (jint)type);
		}
	}
};

class JavaAudioDevice : public JniCallback, public IAudioDevice
{
private:
	jmethodID _play, _stop, _pause, _endFrame, _devices, _setDevice;
public:
	JavaAudioDevice(JNIEnv* env, jobject o) : JniCallback(env, o)
	{
		_play = env->GetMethodID(cls, "playBuffer", "([SIZ)V");
		_stop = env->GetMethodID(cls, "stop", "()V");
		_pause = env->GetMethodID(cls, "pause", "()V");
		_endFrame = env->GetMethodID(cls, "processEndOfFrame", "()V");
		_devices = env->GetMethodID(cls, "getAvailableDevices", "()Ljava/lang/String;");
		_setDevice = env->GetMethodID(cls, "setAudioDevice", "(Ljava/lang/String;)V");
	}

	void PlayBuffer(int16_t* soundBuffer, uint32_t bufferSize, uint32_t sampleRate, bool isStereo) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_play) return;
		jint count = (jint)(bufferSize * (isStereo ? 2 : 1));
		jshortArray arr = env->NewShortArray(count);
		env->SetShortArrayRegion(arr, 0, count, (const jshort*)soundBuffer);
		env->CallVoidMethod(obj, _play, arr, (jint)sampleRate, (jboolean)isStereo);
		env->DeleteLocalRef(arr);
	}

	void Stop() override
	{
		JNIEnv* env = getEnv();
		if(env && _stop) env->CallVoidMethod(obj, _stop);
	}

	void Pause() override
	{
		JNIEnv* env = getEnv();
		if(env && _pause) env->CallVoidMethod(obj, _pause);
	}

	void ProcessEndOfFrame() override
	{
		JNIEnv* env = getEnv();
		if(env && _endFrame) env->CallVoidMethod(obj, _endFrame);
	}

	string GetAvailableDevices() override
	{
		JNIEnv* env = getEnv();
		if(!env || !_devices) return "";
		jstring s = (jstring)env->CallObjectMethod(obj, _devices);
		if(!s) return "";
		const char* c = env->GetStringUTFChars(s, nullptr);
		string r = c ? c : "";
		if(c) env->ReleaseStringUTFChars(s, c);
		env->DeleteLocalRef(s);
		return r;
	}

	void SetAudioDevice(string deviceName) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_setDevice) return;
		jstring s = env->NewStringUTF(deviceName.c_str());
		env->CallVoidMethod(obj, _setDevice, s);
		env->DeleteLocalRef(s);
	}

	AudioStatistics GetStatistics() override { return {}; }
};

class JavaKeyManager : public JniCallback, public IKeyManager
{
private:
	jmethodID _refresh, _update, _mouseBtn, _keyPressed, _pressedKeys;
	jmethodID _keyName, _keyCode, _setKey, _reset, _disabled;
public:
	JavaKeyManager(JNIEnv* env, jobject o) : JniCallback(env, o)
	{
		_refresh = env->GetMethodID(cls, "refreshState", "()V");
		_update = env->GetMethodID(cls, "updateDevices", "()V");
		_mouseBtn = env->GetMethodID(cls, "isMouseButtonPressed", "(I)Z");
		_keyPressed = env->GetMethodID(cls, "isKeyPressed", "(I)Z");
		_pressedKeys = env->GetMethodID(cls, "getPressedKeys", "()[I");
		_keyName = env->GetMethodID(cls, "getKeyName", "(I)Ljava/lang/String;");
		_keyCode = env->GetMethodID(cls, "getKeyCode", "(Ljava/lang/String;)I");
		_setKey = env->GetMethodID(cls, "setKeyState", "(IZ)Z");
		_reset = env->GetMethodID(cls, "resetKeyState", "()V");
		_disabled = env->GetMethodID(cls, "setDisabled", "(Z)V");
	}

	void RefreshState() override
	{
		JNIEnv* env = getEnv();
		if(env && _refresh) env->CallVoidMethod(obj, _refresh);
	}

	void UpdateDevices() override
	{
		JNIEnv* env = getEnv();
		if(env && _update) env->CallVoidMethod(obj, _update);
	}

	bool IsMouseButtonPressed(MouseButton button) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_mouseBtn) return false;
		return env->CallBooleanMethod(obj, _mouseBtn, (jint)button) == JNI_TRUE;
	}

	bool IsKeyPressed(uint16_t keyCode) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_keyPressed) return false;
		return env->CallBooleanMethod(obj, _keyPressed, (jint)keyCode) == JNI_TRUE;
	}

	vector<uint16_t> GetPressedKeys() override
	{
		vector<uint16_t> result;
		JNIEnv* env = getEnv();
		if(!env || !_pressedKeys) return result;
		jintArray arr = (jintArray)env->CallObjectMethod(obj, _pressedKeys);
		if(!arr) return result;
		jsize len = env->GetArrayLength(arr);
		jint* data = env->GetIntArrayElements(arr, nullptr);
		if(data) {
			for(jsize i = 0; i < len; i++) result.push_back((uint16_t)data[i]);
			env->ReleaseIntArrayElements(arr, data, JNI_ABORT);
		}
		env->DeleteLocalRef(arr);
		return result;
	}

	string GetKeyName(uint16_t keyCode) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_keyName) return "";
		jstring s = (jstring)env->CallObjectMethod(obj, _keyName, (jint)keyCode);
		if(!s) return "";
		const char* c = env->GetStringUTFChars(s, nullptr);
		string r = c ? c : "";
		if(c) env->ReleaseStringUTFChars(s, c);
		env->DeleteLocalRef(s);
		return r;
	}

	uint16_t GetKeyCode(string keyName) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_keyCode) return 0;
		jstring s = env->NewStringUTF(keyName.c_str());
		jint r = env->CallIntMethod(obj, _keyCode, s);
		env->DeleteLocalRef(s);
		return (uint16_t)r;
	}

	bool SetKeyState(uint16_t scanCode, bool state) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_setKey) return false;
		return env->CallBooleanMethod(obj, _setKey, (jint)scanCode, (jboolean)state) == JNI_TRUE;
	}

	void ResetKeyState() override
	{
		JNIEnv* env = getEnv();
		if(env && _reset) env->CallVoidMethod(obj, _reset);
	}

	void SetDisabled(bool disabled) override
	{
		JNIEnv* env = getEnv();
		if(env && _disabled) env->CallVoidMethod(obj, _disabled, (jboolean)disabled);
	}
};

class JavaMouseManager : public JniCallback, public IMouseManager
{
private:
	jmethodID _state, _capture, _release, _setPos, _cursor, _pixelScale;
public:
	JavaMouseManager(JNIEnv* env, jobject o) : JniCallback(env, o)
	{
		_state = env->GetMethodID(cls, "getSystemMouseState", "()[I");
		_capture = env->GetMethodID(cls, "captureMouse", "(IIII)Z");
		_release = env->GetMethodID(cls, "releaseMouse", "()V");
		_setPos = env->GetMethodID(cls, "setSystemMousePosition", "(II)V");
		_cursor = env->GetMethodID(cls, "setCursorImage", "(I)V");
		_pixelScale = env->GetMethodID(cls, "getPixelScale", "()D");
	}

	SystemMouseState GetSystemMouseState(void* rendererHandle) override
	{
		SystemMouseState s = {};
		JNIEnv* env = getEnv();
		if(!env || !_state) return s;
		jintArray arr = (jintArray)env->CallObjectMethod(obj, _state);
		if(!arr) return s;
		jint data[7] = {};
		env->GetIntArrayRegion(arr, 0, 7, data);
		env->DeleteLocalRef(arr);
		s.XPosition = data[0];
		s.YPosition = data[1];
		s.LeftButton = data[2] != 0;
		s.RightButton = data[3] != 0;
		s.MiddleButton = data[4] != 0;
		s.Button4 = data[5] != 0;
		s.Button5 = data[6] != 0;
		return s;
	}

	bool CaptureMouse(int32_t x, int32_t y, int32_t width, int32_t height, void* rendererHandle) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_capture) return false;
		return env->CallBooleanMethod(obj, _capture, x, y, width, height) == JNI_TRUE;
	}

	void ReleaseMouse() override
	{
		JNIEnv* env = getEnv();
		if(env && _release) env->CallVoidMethod(obj, _release);
	}

	void SetSystemMousePosition(int32_t x, int32_t y) override
	{
		JNIEnv* env = getEnv();
		if(env && _setPos) env->CallVoidMethod(obj, _setPos, x, y);
	}

	void SetCursorImage(CursorImage cursor) override
	{
		JNIEnv* env = getEnv();
		if(env && _cursor) env->CallVoidMethod(obj, _cursor, (jint)cursor);
	}

	double GetPixelScale() override
	{
		JNIEnv* env = getEnv();
		if(!env || !_pixelScale) return 1.0;
		return env->CallDoubleMethod(obj, _pixelScale);
	}
};

class JavaRenderingDevice : public JniCallback, public IRenderingDevice
{
private:
	jmethodID _update, _clear, _render, _reset, _fullscreen;
public:
	JavaRenderingDevice(JNIEnv* env, jobject o) : JniCallback(env, o)
	{
		_update = env->GetMethodID(cls, "updateFrame", "(II[II)V");
		_clear = env->GetMethodID(cls, "clearFrame", "()V");
		_render = env->GetMethodID(cls, "render", "([IIIZ[IIIZ)V");
		_reset = env->GetMethodID(cls, "reset", "()V");
		_fullscreen = env->GetMethodID(cls, "setFullscreenMode", "(III)V");
	}

	void UpdateFrame(RenderedFrame& frame) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_update) return;
		jintArray arr = env->NewIntArray((jsize)(frame.Width * frame.Height));
		env->SetIntArrayRegion(arr, 0, (jsize)(frame.Width * frame.Height), (const jint*)frame.FrameBuffer);
		env->CallVoidMethod(obj, _update, (jint)frame.Width, (jint)frame.Height, arr, (jint)frame.FrameNumber);
		env->DeleteLocalRef(arr);
	}

	void ClearFrame() override
	{
		JNIEnv* env = getEnv();
		if(env && _clear) env->CallVoidMethod(obj, _clear);
	}

	void Render(RenderSurfaceInfo& emuHud, RenderSurfaceInfo& scriptHud) override
	{
		JNIEnv* env = getEnv();
		if(!env || !_render) return;

		jintArray emuArr = env->NewIntArray((jsize)(emuHud.Width * emuHud.Height));
		if(emuHud.Buffer) {
			env->SetIntArrayRegion(emuArr, 0, (jsize)(emuHud.Width * emuHud.Height), (const jint*)emuHud.Buffer);
		}
		jintArray scriptArr = env->NewIntArray((jsize)(scriptHud.Width * scriptHud.Height));
		if(scriptHud.Buffer) {
			env->SetIntArrayRegion(scriptArr, 0, (jsize)(scriptHud.Width * scriptHud.Height), (const jint*)scriptHud.Buffer);
		}

		env->CallVoidMethod(obj, _render,
			emuArr, (jint)emuHud.Width, (jint)emuHud.Height, (jboolean)emuHud.IsDirty,
			scriptArr, (jint)scriptHud.Width, (jint)scriptHud.Height, (jboolean)scriptHud.IsDirty);
		env->DeleteLocalRef(emuArr);
		env->DeleteLocalRef(scriptArr);
	}

	void Reset() override
	{
		JNIEnv* env = getEnv();
		if(env && _reset) env->CallVoidMethod(obj, _reset);
	}

	void SetFullscreenMode(FullscreenSettings settings) override
	{
		JNIEnv* env = getEnv();
		if(env && _fullscreen) env->CallVoidMethod(obj, _fullscreen, (jint)settings.Mode, (jint)settings.Width, (jint)settings.Height);
	}
};
