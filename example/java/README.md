# 桌面 Java 示例

| 文件 | 说明 |
|---|---|
| `MesenPlayer.java` | Swing 窗口：实时画面 + 键盘操作 + 音频 + 暂停/存档/截图 |
| `HeadlessDemo.java` | 无头脚本：跑 N 帧 → 导出 PNG、读内存、存/读存档 |

## 编译 / 运行

在工程根目录（`MesenJNI/`）执行：

```powershell
# 编译（含核心绑定 + 示例）
javac -encoding UTF-8 -cp "lib/gson-2.9.0.jar" -d build/classes `
      java/mesen/*.java java/mesen/interfaces/*.java example/java/*.java

# 有窗口的播放器（不带 ROM 参数会弹文件选择框）
java -Djava.library.path=bin/win-x64/Release `
     -cp "build/classes;lib/gson-2.9.0.jar" MesenPlayer roms/game.nes

# 无头：跑 600 帧后导出 PNG
java -Djava.library.path=bin/win-x64/Release `
     -cp "build/classes;lib/gson-2.9.0.jar" HeadlessDemo roms/game.nes 600 frame.png
```

> Linux / macOS 把 `;` 换成 `:`，`bin/win-x64/Release` 换成对应产物目录。

## MesenPlayer 操作

| 按键 | 手柄 |
|---|---|
| ↑ ↓ ← → | 十字键 |
| X | A |
| Z | B |
| Enter | Start |
| Shift | Select |

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

## 相关文件

- 回调接口定义：`java/mesen/interfaces/`
- 核心绑定：`java/mesen/MesenCore.java`、`MesenConfig.java`、`MesenVideo.java`、`MesenAudio.java`、`MesenMemory.java`、`MesenInput.java`、`MesenScript.java`
- Android 版本：`../android/`
