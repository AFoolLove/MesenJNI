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
}
