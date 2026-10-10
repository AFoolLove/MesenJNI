# Android 调用示例

把 `MesenJNI` 的 native 核心（`libMesenCore.so`）跑在 Android 上的最小示例。

## 1. 放好原生库

用 CMake 交叉编译出 `libMesenCore.so`（`ANDROID_ABI=arm64-v8a`），放到：

```
app/src/main/jniLibs/arm64-v8a/libMesenCore.so
```

（`armeabi-v7a` / `x86_64` 同理，各编一份放对应目录。）

> 名称必须是 `libMesenCore.so`——Java 的 `System.loadLibrary("MesenCore")` 会找带 `lib` 前缀的名字。

## 2. 抄这些 Java 类

本目录的三个类直接放进你的 app（包名改成你自己的）：

| 文件 | 作用 |
|---|---|
| `MesenEmulator.java` | 核心封装：初始化、渲染回调、音频、按键注入 |
| `MesenView.java` | 等比例居中显示帧的 View |
| `MainActivity.java` | 示例 Activity（含底部手柄按钮） |

`mesen` 包本身（`MesenCore`、`Mesen`、`MesenConfig`、`KeyManager`、`NotificationListener`、`mesen.interfaces.*` 等）编译进工程即可。

**Gson 不是必需的**（只有 `MesenSettings` 用；不用 JSON 配置就不需要加依赖）。

## 3. 最小用法

```java
MesenEmulator emu = new MesenEmulator();
emu.setFrameListener((bitmap, frameNumber) -> view.setFrame(bitmap));

File home = new File(getFilesDir(), "mesen-home");
home.mkdirs();
emu.start(home.getAbsolutePath(), romPath, /*enableAudio*/true);

emu.setButton(Mesen.BUTTON_START, true);   // 按下
emu.setButton(Mesen.BUTTON_START, false);  // 松开
```

## 4. 关键 API

```java
// 生命周期
MesenCore.initDll();
MesenCore.initializeEmu(homeFolder, /*softwareRenderer*/true,
                       /*noAudio*/false, /*noVideo*/false, /*noInput*/true);
MesenCore.loadRom(romPath, null);
MesenCore.resume();  MesenCore.pause();  MesenCore.stop();  MesenCore.release();

// 注册回调（实现类或匿名实现 interfaces.*）
MesenCore.registerNotificationListener(INotificationListener);
MesenCore.registerRenderingDevice(IRenderingDevice);
MesenCore.registerAudioDevice(IAudioDevice);
MesenCore.registerKeyManager(IKeyManager);
MesenCore.registerMouseManager(IMouseManager);

// 输入
MesenConfig.setControllerKey(Mesen.CONSOLE_NES, port, Mesen.BUTTON_A, keyCode);
keyManager.setKeyState(keyCode, true);

// 其它分类
MesenMemory.getMemoryValue(...);      // 内存
MesenConfig.setEmulationFlag(...);    // 配置
MesenInput.setKeyState(...);          // 输入
MesenScript.loadScript(...);          // Lua
```

## 5. 三个接口的 Android 实现要点

- **`IRenderingDevice`**：`updateFrame` 的 `int[] argb` 是 **32-bit ARGB** 且 **复用缓冲**，必须当场拷走（`Bitmap.setPixels`）。回调跑在 core 的渲染线程，切 UI 用 `View.post` / `postInvalidateOnAnimation`。
- **`IAudioDevice`**：`playBuffer` 给的是**交错 int16 PCM**（复用缓冲），写 `AudioTrack` 即可；采样率变了要重建 `AudioTrack`。
- **`IKeyManager`**：`mesen.KeyManager` 是纯 Java 实现，Android 直接可用；按键要先 `MesenConfig.setControllerKey` 建立映射。

> `mesen.AudioDevice` / `mesen.RenderingDevice` 这两个自带实现类依赖 Java SE 的
> `javax.sound.sampled` / `java.awt.image`，**Android 上没有**，所以在 Android 上要像
> `MesenEmulator` 里那样自己实现。

## 6. 注意 / 可优化点

- 示例里 `AndroidRenderer` **每帧新建一个 Bitmap**（安全、无竞争，但有 GC 压力）。追求性能可改成
  `SurfaceView` + `lockCanvas`，或 OpenGL ES，或双缓冲复用 Bitmap。
- 音频用 `AudioTrack` 流式模式；延迟敏感可以调 `bufSize`，或用 Oboe/AAudio。
- 示例把 ROM 放 `assets/game.nes`，首次运行时拷到 `filesDir`；正式发行注意版权。
- `homeFolder` 用来存配置和存档，建议放 `getFilesDir()`。
