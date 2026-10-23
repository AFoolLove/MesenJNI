# 桌面 Java 示例

| 文件 | 说明 |
|---|---|
| `MesenPlayer.java` | Swing 窗口：实时画面 + 键盘操作 + 音频 + 暂停/存档/截图 |
| `HeadlessDemo.java` | 无头脚本：跑 N 帧 → 导出 PNG、读内存、存/读存档 |

## 编译 / 运行

在工程根目录（`MesenJNI/`）执行。需要 **JDK 17+** 和 Gson（只有 `MesenSettings` 用；
仓库里不提供 jar，用 `com.google.code.gson:gson:2.10`）。Linux / macOS 把 `;` 换成 `:`：

```powershell
# 编译（含核心绑定 + 示例）
javac -encoding UTF-8 -cp "<path>/gson-2.10.jar" -d build/classes `
      java/mesen/*.java java/mesen/interfaces/*.java java/mesen/debugger/*.java `
      java/*.java example/java/*.java

# 有窗口的播放器（不带 ROM 参数会弹文件选择框）
java -Djava.library.path=bin/win-x64/Release `
     -cp "build/classes;<path>/gson-2.10.jar" MesenPlayer roms/game.nes

# 无头：跑 600 帧后导出 PNG
java -Djava.library.path=bin/win-x64/Release `
     -cp "build/classes;<path>/gson-2.10.jar" HeadlessDemo roms/game.nes 600 frame.png
```

> 把 Java 侧放进自己的 Gradle 工程时，依赖写
> `implementation("com.google.code.gson:gson:2.10")`，源目录是 `java/` + `example/java`。

## MesenPlayer 操作

| 按键 | 手柄 |
|---|---|
| ↑ ↓ ← → | 十字键 |
| X | A |
| Z | B |
| Enter | Start |
| Shift | Select |

界面上还有个 **「事件查看器」** 按钮，打开后是：

- 左边：事件查看器渲染结果 —— PPU 画面作背景，事件点按类别着色叠加；
- 右边：该机种的全部事件类别勾选框（默认全开）；
- 下面：当前帧的事件列表（类型 / PC / 扫描线 / 周期 / 地址 / 值）。

两个要注意的点（都来自实际踩坑）：

1. **类别必须勾上**才会被 core 记进事件表，全不勾就什么都看不到；
2. `getDebugEventCount()` / `getDebugEvents()` 读的是**快照**，每次刷新都要先调
   `takeEventSnapshot()`，否则永远是 0。

## 代码要点

**1. 初始化 + 注册回调**

```java
MesenCore.initDll();
MesenCore.initializeEmu(homeFolder, true /*softwareRenderer*/, false /*noAudio*/,
                        false /*noVideo*/, true /*noInput*/);

MesenCore.registerRenderingDevice(renderer);      // 帧 -> BufferedImage
MesenCore.registerAudioDevice(audio);             // PCM -> SourceDataLine
MesenCore.registerKeyManager(keys);               // 输入
MesenCore.registerNotificationListener(events);   // 事件
```

四个实现类都是 `mesen` 包里现成的（桌面可直接用）：

| 类 | 实现接口 | 行为 |
|---|---|---|
| `RenderingDevice` | `IRenderingDevice` | 帧写进 `BufferedImage`，`getBufferedImage()` 取 |
| `AudioDevice` | `IAudioDevice` | 用 `SourceDataLine` 播放（自动试 48k/44.1k/22k/16k） |
| `KeyManager` | `IKeyManager` | 程序化按键表，`setKeyState` 注入 |
| `NotificationListener` | `INotificationListener` | 收集事件，`getCount()` / `getLastType()` |

**2. 输入必须两步**

```java
MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);  // ① 类型
MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_A, KeyEvent.VK_X); // ② 映射
...
keys.setKeyState(KeyEvent.VK_X, true);   // ③ 按下
```

漏掉 ① 的话核不会创建手柄设备，`IKeyManager` 永远不会被调用，按键毫无反应。

**3. 帧缓冲复用**

`RenderingDevice` 内部已经当场拷进 `BufferedImage` 了；如果你自己实现 `IRenderingDevice`，
`updateFrame` 的 `argb` 是核里复用的缓冲，必须在回调里马上 `System.arraycopy` 走。

**4. 音频别阻塞模拟线程**

`SoundMixer` 是在**模拟线程**上同步调 `playBuffer()` + `processEndOfFrame()` 的，
所以 `IAudioDevice` 的实现必须快速返回：

- **不要在 `processEndOfFrame()` 里 `line.drain()`**：那会等缓冲区里排队的音频全部播完，
  等于按音频缓冲深度节流模拟器（实测 60fps → 7fps）。
- `SourceDataLine.write()` 满了会自然阻塞，这本身就够把速度钉在实时上了。
- 缓冲区默认 500ms，可用 `-Dmesen.audioBufferMs=N` 调整（太小反而掉帧，太大增加延迟）。

## 相关文件

- 回调接口定义：`java/mesen/interfaces/`
- 核心绑定：`java/mesen/MesenCore.java`、`MesenConfig.java`、`MesenVideo.java`、`MesenAudio.java`、`MesenMemory.java`、`MesenInput.java`、`MesenScript.java`
- Android 版本：`../android/`
