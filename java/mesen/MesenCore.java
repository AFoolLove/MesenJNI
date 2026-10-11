package mesen;

import mesen.interfaces.*;

/**
 * JNI bindings for the Mesen core (MesenCore native library).
 *
 * <p>生命周期、ROM、存档、日志、回调注册。其它能力已拆到分类类：</p>
 * <ul>
 *   <li>{@link MesenMemory} 内存读写</li>
 *   <li>{@link MesenConfig} 配置/调色板/手柄</li>
 *   <li>{@link MesenInput} 键盘/鼠标输入</li>
 *   <li>{@link MesenVideo} 软件渲染帧</li>
 *   <li>{@link MesenAudio} 采集音频</li>
 *   <li>{@link MesenScript} Lua 脚本</li>
 *   <li>{@link MesenSettings} Java 侧 JSON 配置持久化</li>
 * </ul>
 *
 * <p>Headless usage example:</p>
 * <pre>
 *   MesenCore.initDll();
 *   MesenCore.initializeEmu("/path/to/home", true, true, false, true);
 *   if(MesenCore.loadRom("/path/to/game.nes", null)) {
 *       MesenCore.resume();
 *       while(MesenCore.isRunning()) {
 *           int w = MesenVideo.getFrameWidth();
 *           int h = MesenVideo.getFrameHeight();
 *           int[] frame = new int[w * h];
 *           MesenVideo.copyFrameBuffer(frame);
 *           // ... process frame ...
 *       }
 *       int stopCode = MesenCore.getStopCode();
 *   }
 *   MesenCore.release();
 * </pre>
 */
public final class MesenCore
{
    static {
        // JNI 胶水已并入 MesenCore，只加载这一个库。
        System.loadLibrary("MesenCore");
    }

    private MesenCore() {
    }

    // --- 回调注册（实现 Core/Shared/Interfaces） --------------------------

    /**
     * 注册通知监听器（实现 Core 的 INotificationListener）。
     * 事件类型见 {@link Mesen} 的 {@code NOTIFICATION_*} 常量。
     */
    public static native void registerNotificationListener(INotificationListener listener);

    /**
     * 注册音频设备（实现 Core 的 IAudioDevice）。
     * 注册后音频不再播放到声卡，而是以 PCM 形式回调到 Java。
     */
    public static native void registerAudioDevice(IAudioDevice device);

    /** 注册键盘/输入管理器（实现 Core 的 IKeyManager），用于程序化输入。 */
    public static native void registerKeyManager(IKeyManager manager);

    /** 注册鼠标管理器（实现 Core 的 IMouseManager）。 */
    public static native void registerMouseManager(IMouseManager manager);

    /**
     * 注册渲染设备（实现 Core 的 IRenderingDevice）。
     * 注册后帧以 32-bit ARGB 形式回调到 Java，而不是内部软件渲染器。
     */
    public static native void registerRenderingDevice(IRenderingDevice device);

    // --- Lifecycle / info --------------------------------------------------

    public static native boolean testDll();
    public static native int getMesenVersion();
    public static native String getMesenBuildDate();
    public static native void initDll();

    /**
     * Initializes the emulator. Handles are always null (headless); the
     * software renderer is created when {@code softwareRenderer} is true.
     *
     * @param softwareRenderer use the software renderer (enables frame output)
     * @param noAudio          disable audio output
     * @param noVideo          disable video output
     * @param noInput          disable OS input handling
     */
    public static native void initializeEmu(String homeFolder, boolean softwareRenderer, boolean noAudio, boolean noVideo, boolean noInput);

    public static native void release();
    public static native boolean isRunning();
    public static native void stop();
    public static native int getStopCode();
    public static native void pause();
    public static native void resume();
    public static native boolean isPaused();

    // --- ROM ---------------------------------------------------------------

    public static native boolean loadRom(String filename, String patchFile);
    public static native void loadRecentGame(String filepath, boolean resetGame);
    public static native void addKnownGameFolder(String folder);
    public static native RomInfo getRomInfo();
    public static native String getRomHash(int hashType);

    // --- State -------------------------------------------------------------

    public static native void saveState(int slot);
    public static native void loadState(int slot);
    public static native void saveStateFile(String filepath);
    public static native void loadStateFile(String filepath);
    public static native void takeScreenshot();

    // --- Log / messages ----------------------------------------------------

    public static native String getLog();
    public static native void writeLogEntry(String message);
    public static native void displayMessage(String title, String message, String param);

    // --- 录像：视频 / 音频 / 操作回放 ---------------------------------------

    /**
     * 开始录 AVI。
     *
     * @param codec           {@code Mesen.VIDEO_CODEC_*}（注意 {@code VIDEO_CODEC_GIF} 时
     *                        文件名要用 .gif）
     * @param compressionLevel 压缩级别（0 = 默认）
     * @param recordSystemHud 是否把系统 HUD 也录进去
     * @param recordInputHud  是否把按键显示也录进去
     */
    public static native void aviRecord(String filename, int codec, int compressionLevel,
        boolean recordSystemHud, boolean recordInputHud);

    /** 停止录 AVI。 */
    public static native void aviStop();

    /** 是否正在录 AVI。 */
    public static native boolean aviIsRecording();

    /** 开始录 WAV。 */
    public static native void waveRecord(String filename);

    /** 停止录 WAV。 */
    public static native void waveStop();

    /** 是否正在录 WAV。 */
    public static native boolean waveIsRecording();

    /**
     * 开始录制操作回放（.mmo）。
     *
     * @param recordFrom {@code Mesen.MOVIE_FROM_*}
     */
    public static native void movieRecord(String filename, String author, String description, int recordFrom);

    /** 播放操作回放。 */
    public static native void moviePlay(String filename);

    /** 停止回放/录制。 */
    public static native void movieStop();

    /** 是否正在播放回放。 */
    public static native boolean moviePlaying();

    /** 是否正在录制回放。 */
    public static native boolean movieRecording();

    // --- 作弊 ---------------------------------------------------------------

    /** 设置作弊码列表（会替换掉之前的）。 */
    public static native void setCheats(CheatCode[] codes);

    /** 清空作弊码。 */
    public static native void clearCheats();

    /**
     * 把作弊码转成 core 内部形式（调试/显示用）。
     *
     * @return 转换失败（码不合法）时返回 null
     */
    public static native InternalCheatCode getConvertedCheat(CheatCode input);

    // --- 存档 / ROM ---------------------------------------------------------

    /** 取存档的预览图（PNG 字节；没有预览或文件不存在时返回空数组）。 */
    public static native byte[] getSaveStatePreview(String saveStatePath);

    /** 列出压缩包（zip 等）里的 ROM 文件名。 */
    public static native String[] getArchiveRomList(String archivePath);

    // --- 显示 / 外设 --------------------------------------------------------

    /**
     * 切换全屏。
     *
     * @param windowHandle 原生窗口句柄（0 = 无）
     * @param mode         {@code Mesen.FULLSCREEN_*}
     */
    public static native void setFullscreenMode(long windowHandle, int width, int height, int mode);

    /** 给条码枪外设送一个条码。 */
    public static native void inputBarcode(long barcode, int digitCount);

    /**
     * 磁带机操作。
     *
     * @param action {@code Mesen.TAPE_*}
     */
    public static native void processTapeRecorderAction(int action, String filename);

    /**
     * 音频播放器操作（NSF/GBS 等）。
     *
     * @param action {@code Mesen.AUDIO_PLAYER_*}
     */
    public static native void processAudioPlayerAction(int action, int trackNumber);

    /** 延迟计数器归零。 */
    public static native void resetLagCounter();

    // --- 配置 ---------------------------------------------------------------

    /** 应用单游戏配置（DIP 开关、过扫描覆盖）。 */
    public static native void setGameConfig(GameConfig config);

    /** 应用偏好设置。 */
    public static native void setPreferences(PreferencesConfig config);

    /** 应用音频播放器配置（音量/循环/随机）。 */
    public static native void setAudioPlayerConfig(AudioPlayerConfig config);

    // --- 快捷键 -------------------------------------------------------------

    /**
     * 触发一个快捷键（等价于用户按下它）。
     *
     * @param shortcut {@code Mesen.SHORTCUT_*}
     * @param param    部分快捷键需要的参数（如存档槽号），不需要时传 0
     */
    public static native void executeShortcut(int shortcut, int param);

    /** 该快捷键当前是否可用（例如没在录像时「停止录像」就不可用）。 */
    public static native boolean isShortcutAllowed(int shortcut, int param);

    /** 设置快捷键绑定表（会替换掉之前的）。 */
    public static native void setShortcutKeys(ShortcutKeyInfo[] shortcuts);

    // --- 着色器 -------------------------------------------------------------

    /** 应用着色器配置（空 shaderFile = 关闭着色器）。 */
    public static native void setShaderConfig(ShaderConfig config);

    /** 当前渲染设备是否支持 librashader 着色器。 */
    public static native boolean checkShaderSupport();

    /**
     * 上游 PGO 性能剖析用的测试跑批。
     *
     * <p><b>不要在生产里调用</b>：core 会注册一个假输入设备、把 home 目录改成
     * {@code ../PGOMesenHome}，然后逐个 ROM 跑到底。</p>
     */
    public static native void pgoRunTest(String[] testRoms, boolean enableDebugger);
    // --- 网表对战 -----------------------------------------------------------

    /** 开服务器（0 = 由系统选端口）。 */
    public static native void startServer(int port, String password);

    /** 关服务器。 */
    public static native void stopServer();

    /** 服务器是否在跑。 */
    public static native boolean isServerRunning();

    /**
     * 连接服务器。
     *
     * @param spectator 是否以观战身份加入
     */
    public static native void connect(String host, int port, String password, boolean spectator);

    /** 断开连接。 */
    public static native void disconnect();

    /** 是否已连接。 */
    public static native boolean isConnected();

    /** 取控制器占用情况（服务器在跑时问服务器，否则问客户端）。 */
    public static native NetplayControllerUsageInfo[] netplayGetControllerList();

    /** 选择自己要用的控制器端口。 */
    public static native void netplaySelectController(NetplayControllerInfo controller);

    /** 取自己当前占用的控制器端口。 */
    public static native NetplayControllerInfo netplayGetControllerPort();
    // --- 自动化测试（上游 CI 用，需要 .mtp 测试 ROM）------------------------

    /**
     * 运行一个录制好的测试（.mtp）。
     *
     * @param inBackground 是否在独立的模拟器实例里跑
     */
    public static native RomTestResult runRecordedTest(String filename, boolean inBackground);

    /**
     * 运行一个测试 ROM 并读回结果。
     *
     * <p>跑到 500 帧后，从 {@code memoryType} 的 {@code address} 处读 8 个字节当作
     * 结果。注意它会<b>自己新建一个模拟器</b>加载该 ROM，不影响当前运行的游戏。</p>
     */
    public static native long runTest(String filename, int address, int memoryType);

    /** 开始录制测试（.mtp）。 */
    public static native void romTestRecord(String filename, boolean reset);

    /** 停止录制测试。 */
    public static native void romTestStop();

    /** 是否正在录制测试。 */
    public static native boolean romTestRecording();

    /**
     * 跑一整个目录的 CI 测试（递归 5 层找 .mtp）。
     *
     * <p><b>注意</b>：core 会把全局 home 目录改成
     * {@code <testFolder>/MesenHomeFolder}，调用后当前模拟器的存档目录也随之改变。</p>
     */
    public static native boolean runCiTests(String testFolder, boolean enableDebugger);
    /** 读取着色器文件定义的参数（文件不存在或解析失败时返回空数组）。 */
    public static native ShaderParamDefinition[] getShaderParams(String shaderFile);

    // --- 历史查看器（倒带时间轴回放）----------------------------------------

    /**
     * 是否有可回放的历史。
     *
     * <p>取决于 {@code PreferencesConfig.RewindBufferSize}——设成 0 就没有历史。</p>
     */
    public static native boolean historyViewerEnabled();

    /**
     * 初始化历史查看器。
     *
     * <p><b>注意</b>：core 会为此<b>再建一个 Emulator 实例</b>，并在 Windows 上创建
     * 原生音频设备（非 Windows 用 SDL）。headless 场景下传 0 句柄、并确保
     * {@code initializeEmu} 的 {@code softwareRenderer} 为 true，走软件渲染。</p>
     *
     * @param windowHandle 主窗口句柄（0 = 无）
     * @param viewerHandle 查看器窗口句柄（0 = 无）
     */
    public static native void historyViewerInitialize(long windowHandle, long viewerHandle);

    /** 释放历史查看器（会销毁它自己的那个 Emulator）。 */
    public static native void historyViewerRelease();

    /** 取当前状态（未初始化时返回全 0）。 */
    public static native HistoryViewerState historyViewerGetState();

    /** 应用选项（暂停/音量/渲染尺寸）。 */
    public static native void historyViewerSetOptions(HistoryViewerOptions options);

    /** 在指定位置导出一个存档。 */
    public static native boolean historyViewerCreateSaveState(String outputFile, int position);

    /** 把 [startPosition, endPosition] 区段导出成操作回放（.mmo）。 */
    public static native boolean historyViewerSaveMovie(String movieFile, int startPosition, int endPosition);

    /** 从指定位置恢复实际游戏（历史查看器结束）。 */
    public static native void historyViewerResumeGameplay(int resumePosition);

    /** 跳转到指定位置。 */
    public static native void historyViewerSetPosition(int seekPosition);

    /** 注册历史查看器的通知监听（对应 core 的 HistoryViewerRegisterNotificationCallback）。 */
    public static native void historyViewerRegisterNotificationListener(INotificationListener listener);

    /** 注销历史查看器的通知监听。 */
    public static native void historyViewerUnregisterNotificationListener();
}
