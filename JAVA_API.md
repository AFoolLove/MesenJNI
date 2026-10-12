# Java API（JNI 绑定层）

这个仓库把 Mesen 的**无界面模拟器核心**编译成一个共享库，并额外提供一层 JNI 绑定，
让 Java / Android 直接调用。

* 一个库搞定：`MesenCore.dll` / `libMesenCore.so` / `libMesenCore.dylib`
  —— 核心（InteropDLL）和 JNI 胶水编在一起，没有第二个依赖库。
* **不包含** .NET/Avalonia 界面（那是上游的 `MesenCE-clone/`，本仓库保持原样）。
* 目标是「Java 侧只依赖这一个 so/dll」。

---

## 1. 快速开始

```java
MesenCore.initDll();
MesenCore.initializeEmu(homeFolder, /*softwareRenderer*/ true, /*noAudio*/ false,
                       /*noVideo*/ false, /*noInput*/ false);

MesenCore.registerRenderingDevice(new MyRenderer());   // 见 §5
MesenCore.registerAudioDevice(new MyAudio());
MesenCore.registerKeyManager(new MyKeys());
MesenCore.registerNotificationListener(new MyNotifications());

MesenCore.loadRom("/path/to/game.nes", null);
MesenCore.resume();      // 核心在自己的线程里跑
```

也可以直接从内存字节加载（不用先落盘）：

```java
byte[] romData = readAllBytes(...);           // 下载 / AssetManager / InputStream
MesenCore.loadRomFromBytes(romData, "game.nes", null);
```

> `romName` **必须带真实扩展名**（core 按它挑机种和加载器），但**不会**去磁盘读文件。
> core 会拷贝数据，返回后可以直接丢掉数组。

完整的可运行示例：

* [`example/java/`](example/java/) —— 桌面 Swing 播放器（含事件查看器）
* [`example/android/`](example/android/) —— Android 最小可用集成

---

## 2. 构建

### Windows (x64)

需要 CMake 3.21+（VS 2022 自带的就行）和 MSVC 2022：

```
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
```

产物：`bin/win-x64/Release/MesenCore.dll`

### Android（NDK + JDK）

```
cmake -S . -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release -DJAVA_HOME=/path/to/jdk
cmake --build build-android
```

产物：`bin/android-arm64-v8a/Release/libMesenCore.so`

`ANDROID_ABI` 可换 `armeabi-v7a` / `x86` / `x86_64`。

> Android 上**没有**原生窗口/音频/输入层，全部靠 Java 回调提供（见 §5）。
> 已验证的 NDK 版本：28.2.13676358。

### Linux / macOS

见 [COMPILING.md](COMPILING.md)。

---

## 3. 运行测试

测试都在 `java/` 下，是普通 `main()` 程序（不需要 JUnit）：

```bash
javac -encoding UTF-8 -cp "lib/gson-2.9.0.jar" -d build/classes \
      java/mesen/*.java java/mesen/interfaces/*.java java/mesen/debugger/*.java \
      java/*.java example/java/*.java

java -Djava.library.path=bin/win-x64/Release \
     -cp "build/classes;lib/gson-2.9.0.jar" DebugSmokeTest
```

**回归套件**（17 个）：

| 测试 | 覆盖 |
|---|---|
| `SmokeTest` | 库加载、版本、帧缓冲 |
| `CallbackSmokeTest` | 5 个回调接口的注册与调用 |
| `ConfigSmokeTest` / `ConfigFileSmokeTest` | 配置读写、配置文件 |
| `AudioSmokeTest` / `ToneSmokeTest` | 音频回调、采样数据 |
| `LuaSmokeTest` | Lua 脚本 |
| `ImplSmokeTest` | 回调接口的默认实现类 |
| `DebugSmokeTest` | 调试 API 全家桶（断点/反汇编/内存/CDL/PPU 工具/事件查看器/整机状态/汇编导出） |
| `ReinitTest` | `release()` 后重新 `initDll()` |
| `RecordSmokeTest` | AVI/WAV/操作回放录制 |
| `MiscSmokeTest` | 作弊码、存档预览、压缩包列表、配置、快捷键、着色器 |
| `HistorySmokeTest` | 历史查看器（倒带时间轴） |
| `TestApiSmokeTest` | 录制测试回放、CI 跑测 |
| `NetplaySmokeTest` | 网表对战（真开本地服务器） |
| `RomBytesSmokeTest` | 从内存字节加载 ROM |
| `ConsoleSmokeTest` | **多机种表驱动**：每个机种跑同一套通用接口 |
| `ApiCoverageTest` | 补齐「已绑但没测过」的方法，重点是**写路径**的「写进去再读出来」往返 |

另有 3 个诊断工具（非回归，手动跑）：

| 工具 | 用法 |
|---|---|
| `CoreRateTest` | 实测核心帧率（应稳定 60fps） |
| `FrameVerifyTest` | 验证帧内容非零。**无参数 = 轮询模式**（`frames=0` 是正常的：那两个计数器只在回调模式用）；**`cb` = 回调模式**，实测 `frames=180 nonZeroFrames=180` |
| `InputVerifyTest` | 验证按键注入能传到游戏 |

**测试素材**（都是 gitignore 的，缺了会跳过、不算失败）：

* ROM 放 `roms/`（`.nes` / `.smc` / `.gba` / `.gb` / `.pce` / `.sms` / `.ws` …）
* GBA 需要 BIOS（16384 字节）。默认放 `home/Firmware/gba_bios.bin`，
  也可以用 `MesenConfig.setFolderOverrides(null, null, null, "<目录>")` 指到别处
  （`ConsoleSmokeTest` 就指向了 `roms/bios`）—— 必须在 `loadRom` 之前设
* home 目录是 `./home`

---

## 4. API 总览

8 个带 native 方法的类，共 **210 个方法**；另有 44 个 `mesen.debugger` 数据类、
25 个顶层数据类、**530 个常量**（都在 `Mesen.java`）。

| 类 | 方法数 | 用途 |
|---|---|---|
| `MesenCore` | 88 | 生命周期、ROM、存档、录像、作弊、网表、快捷点、着色器、历史查看器、录制测试 |
| `MesenDebugger` | 79 | 断点、反汇编、内存、CDL、表达式、PPU 工具、事件查看器、整机状态、执行跟踪、性能分析 |
| `MesenConfig` | 21 | 视频/音频/输入/机种配置 |
| `MesenInput` | 9 | 输入设备 |
| `MesenMemory` | 5 | 内存读写 |
| `MesenScript` | 3 | Lua 脚本 |
| `MesenVideo` | 3 | 帧缓冲 |
| `MesenAudio` | 2 | 音频采样 |

### 核心约定

**① 一进程一实例。** 核心是进程级单例，`MesenCore` 的方法都是 `static native`，
没有实例句柄。要同时跑多个游戏就起多个进程（各加载一份库，互不干扰）。

`release()` 之后可以再次 `initDll()` 重新开始 —— 早期这里会崩，已修。

**② 回调用「注册 Java 接口」，不是 C 函数指针。**

| Java 接口 | 注册方法 |
|---|---|
| `INotificationListener` | `registerNotificationListener` |
| `IAudioDevice` | `registerAudioDevice` |
| `IKeyManager` | `registerKeyManager` |
| `IMouseManager` | `registerMouseManager` |
| `IRenderingDevice` | `registerRenderingDevice` |

每个接口都有现成的默认实现（`NotificationListener`、`AudioDevice`、`KeyManager`、
`MouseManager`、`RenderingDevice`），只想用其中几个方法时继承它们即可。

**③ 数据类用 public 扁平字段**，没有 getter/setter；JNI 按字段名/签名直接读写，
所以**改字段名会静默失败**（JNI 侧是按名字找的）。

**④ 常量都在 `Mesen.java`**，命名与 core 的枚举对应（`Mesen.CPU_*`、`MEM_*`、
`CONSOLE_*`、`TILE_FORMAT_*` …）。

---

## 5. 已知坑 / core 的注意事项

这些都是实际踩出来的，写下来省得再踩一遍。

### 机种开关与「未加载就崩」

* **对没在跑的机种调用状态类接口会让 core 崩**：`GetCpuState` / `GetPpuState` /
  `GetPpuToolsState` 内部解引用「该机种的调试器」时不做空检查。
  JNI 里已经加了守卫，这些方法在机种没跑时返回 `null`（或全 0 状态）而不是崩。
* **`GetConsoleState` 的约定和别的不一样**：它收的是 **`ConsoleType`**（不是 `CpuType`），
  而且 **state 参数在第一个**。
* **`GetConsoleState` 的机种参数会被 core 忽略**：`NesConsole::GetConsoleState` 无条件写入
  整个 `NesState`（31352 字节）。所以 JNI 一律按**所有机种里最大的那个尺寸**分配缓冲，
  并且只在「请求的机种 == 正在跑的机种」时才真的去取；否则返回全 0。
  （早期没这么做，向 SNES 的 1440 字节缓冲写 31352 字节 → 堆损坏崩溃。）

### 调试器的怪癖

* **`getDisassemblyOutput(cpuType, address, rowCount)` 的第二个参数是地址**，不是行号。
  有些行会返回空（`opSize=0`）：core 是逐 bank 反汇编的，部分表项没数据，
  而且**哪些行有数据取决于已经执行过什么**（走 CDL 代码分析），所以同一地址在不同时刻
  结果可能不同。
* **`searchDisassembly` 的大小写**：core 只把**正文**转小写，不转你传的搜索串。
  所以 `matchCase=false` 时要自己传小写（`"lda"`），或者用 `matchCase=true`。
* **`getDebuggerLog()` 为空是正常的**：`_debuggerLog` 只记录「读取未初始化内存」，
  要先在 `DebugConfig` 里打开 `BreakOnUninitRead`。
* **CDL 不在 CPU RAM 上**：要查 `MEM_NES_PRG_ROM` 这类内存类型。
  另外 **PRG ROM 的头几 KB 常常是填充区**（全 0），扫全片才能看到标记。
* **写内存要选「有实际缓冲」的内存类型**：`MesenDebugger.setMemoryState()` 与
  `setTilePixel()` 内部都是在 `_emu->GetMemory(type).Memory` 上直接 `memcpy`，
  而 `MEM_NES`、`MEM_NES_PPU` 这类是**映射地址空间、没有缓冲** —— 传进去会**静默跳过**
  （不报错、也不生效，很难查）。NES 上请用 `MEM_NES_INTERNAL_RAM`、`MEM_NES_CHR_ROM`
  这类真实缓冲。只写单个字节的 `MesenMemory.setMemoryValue()` 没这个问题（走 CPU 写处理）。
* **`getProfilerData` 的容量**：`Profiler::GetProfilerData` 忽略传入的容量，
  固定最多写 100000 条 —— JNI 已按 100000 分配。`GetAvailableInputOverrides` 固定填 8 项。

### 配置结构体

* **`DebugConfig`（51 字段）、`EventViewerConfig` 没有「读回」接口** —— 和 .NET 一样，
  配置由调用方自己持有，提交时是**整份覆盖**。没设的字段会写成默认值
  （`DebugConfig` 绝大多数默认 `false`，`ScriptTimeout` 默认 1）。
* **`PreferencesConfig` 的三个目录覆盖项没有暴露**：它们是 `const char*`，
  而 `SetPreferences` 只做整体赋值不拷贝字符串，从 Java 传指针会**悬空**。
  要改目录用 `MesenConfig`。

### 事件查看器

* **类别必须勾上**才会被 core 记进事件表 —— 全不勾就什么都看不到。
* **`getDebugEventCount()` / `getDebugEvents()` 读的是快照**，每次刷新前都要先调
  `takeEventSnapshot()`，否则永远是 0。`getEventViewerOutput()` 同理（不先拍快照会返回全 0）。

### 历史查看器

* 它会**再建一个 Emulator 实例**。`release()` 前重复 `initialize()` 会先销毁旧的。
* **它需要音频设备**：core 里 `HistoryViewerInitialize` 无视 `noAudio`，在 Windows 上
  无条件创建原生音频设备 —— 用 `noAudio=true` 初始化核心时会崩。已改成尊重该标志。
* ⚠️ **已知未修复问题**：调过 `historyViewerResumeGameplay()` 之后再调
  `historyViewerRelease()` 会**死锁**（卡在 `_historyPlayer->Release()`，两个线程都 0 CPU）。
  只 `initialize`/`release`（不 resume）是正常的，所以触发条件是 resume ——
  对照 `HistorySmokeTest`（不 resume，通过）和 `ApiCoverageTest`（resume，因此跳过 release）。
  复现：加载 ROM → 跑一会儿让 `historyViewerEnabled()` 为 true → `historyViewerInitialize(0,0)`
  → `historyViewerResumeGameplay(任意位置)` → `historyViewerRelease()`。
  已排除的原因：不是「空历史越界」（有历史时同样死锁）、不是 `ResumeGameplay` 持主锁调
  `LoadRom` 导致（那处已挪出锁外，问题依旧）。尚未定位。

### 其他

* **`GetDisplayBuffer` 的 `bufferSize` 要的是字节数**（不是像素数）—— JNI 已处理。
* **`MovieRecorder::Stop()` 曾被调用两次**（析构函数又调了一次），导致每次导出 .mmo
  刷 3 条 `mz_zip_writer_add_file() failed!`。已修。
* **`PgoRunTest` 别在生产里调**：它注册假输入设备、把 home 目录改成 `../PGOMesenHome`、
  把每个 ROM 跑到底。
* **`runCiTests` 会改全局 home 目录**（改成 `<testFolder>/MesenHomeFolder`），
  所以它单独一个测试文件。

---

## 6. C API 覆盖情况

| | 数量 |
|---|---|
| InteropDLL 导出函数 | 222 |
| **已通过 JNI 暴露** | **210** |
| 未暴露 | 12 |

未暴露的 12 个都是**设计上由 Java 回调替代**的，绑过来 Java 也用不了：

| C API | Java 对应 |
|---|---|
| `RegisterNotificationCallback` / `UnregisterNotificationCallback` | `registerNotificationListener(INotificationListener)` |
| `HistoryViewerRegister/UnregisterNotificationCallback` | `historyViewerRegisterNotificationListener(...)` |
| `GetPressedKeys` | `IKeyManager.getPressedKeys()` |
| `GetSystemMouseState` / `SetSystemMousePosition` / `SetCursorImage` / `GetPixelScale` / `CaptureMouse` / `ReleaseMouse` | `IMouseManager` 的 6 个方法 |
| `GetAudioDevices` | `IAudioDevice.getAvailableDevices()` |

### 各机种验证状态

`ConsoleSmokeTest` 用一张表驱动，把同一套通用接口（PPU 工具、整机状态字节、
程序计数器、反汇编、调试器特性）跑遍每个机种。

| 机种 | 状态 |
|---|---|
| NES | ✅ 31352 字节状态，32 色调色板 |
| SNES | ✅ 1440 字节状态，256 色调色板 |
| GBA | ✅ 960 字节状态，512 色调色板（需 `home/Firmware/gba_bios.bin`） |
| GameBoy / PCE / SMS / WonderSwan | ⏸ 缺 ROM（放 `roms/` 即自动开始验证） |

> **SMS 和 WonderSwan 值得优先补**：它们**没有 sprite RAM**，是泛化的 PPU 代码里
> 唯一走 `nullptr` 分支的机种，这条路径还没被执行过。

---

## 7. 目录结构

```
jni/                  JNI 绑定层
  MesenApi.h            C API 声明（JNI 侧用的子集）
  MesenJni.cpp          MesenCore / MesenConfig / ... 的绑定
  DebugJni.cpp          MesenDebugger 的绑定
  JavaAdapters.h        回调接口的 C++ 适配器
java/
  mesen/*.java          8 个 API 类 + 顶层数据类 + Mesen.java 常量
  mesen/interfaces/     5 个回调接口
  mesen/debugger/       44 个调试数据类
  *Test.java            测试
example/
  java/                 桌面 Swing 示例
  android/              Android 示例
InteropDLL/             C API 实现（上游）
Core/                   模拟器核心（上游）
Utilities/              工具（含 ZipWriter、VirtualFile）
```
