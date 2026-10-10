# 示例

两套调用 MesenJNI 的完整示例，Java 侧共用同一套 `mesen` 包，只是原生库和渲染/音频实现不同。

| 目录 | 平台 | 内容 |
|---|---|---|
| [java/](java/) | 桌面 JVM | Swing 窗口 + 键盘操作 + 音频；另有无头批量脚本示例 |
| [android/](android/) | Android | 帧 → Bitmap、触摸按键、AudioTrack 播放 |

## 共同前提

原生库（同一个核心，只是产物不同）+ Java 绑定：

| | 原生库位置 |
|---|---|
| Windows | `bin/win-x64/Release/MesenCore.dll` |
| Android | `app/src/main/jniLibs/<abi>/libMesenCore.so` |

```
工程/
  java/mesen/               ← 核心绑定（MesenCore / MesenConfig / MesenVideo ...）
  java/mesen/interfaces/    ← 回调接口（I*，自己实现用这层）
```

## 三个必踩的坑（都已修，但写代码时要知道）

1. **输入要两步**：`setControllerType` 指定手柄类型 **+** `setControllerKey` 建立映射。
   只设映射的话，游戏不在内置数据库里时核不会装配手柄，按键完全没反应。

2. **`mesen.AudioDevice` / `mesen.RenderingDevice` 在 Android 上用不了**——
   它们内部用 Java SE 的 `javax.sound.sampled` / `java.awt.image`。
   桌面可以直接用，Android 要自己实现 `IAudioDevice`（`AudioTrack`）和
   `IRenderingDevice`（`Bitmap`），见 `android/MesenEmulator.java`。

3. **帧缓冲是复用的**：`updateFrame` 的 `int[] argb`、`playBuffer` 的 `short[]`
   都是核里反复使用的同一块内存，**必须当场拷走**，不能存引用。

## 回调 vs 轮询

两条路都能拿帧，各有取舍：

| | 回调（回调接口） | 轮询 |
|---|---|---|
| 接口 | `registerRenderingDevice(IRenderingDevice)` | `MesenVideo.getFrameWidth/Height` + `copyFrameBuffer` |
| 音频 | `registerAudioDevice(IAudioDevice)` | `MesenAudio.readAudioSamples` |
| 时机 | 核渲染完立刻推给你（渲染线程） | 自己按 60fps 拉 |
| 适合 | 实时显示、Android | 简单集成、测试脚本 |

桌面两个示例分别覆盖：`MesenPlayer` 走回调，`HeadlessDemo` 也走回调（再配轮询式的等待）。

## 构建

**桌面（Windows）**

```powershell
javac -encoding UTF-8 -cp "lib/gson-2.9.0.jar" -d build/classes `
      java/mesen/*.java java/mesen/interfaces/*.java example/java/*.java

java -Djava.library.path=bin/win-x64/Release `
     -cp "build/classes;lib/gson-2.9.0.jar" MesenPlayer roms/game.nes
```

**Android**：见 [android/README.md](android/README.md)。
