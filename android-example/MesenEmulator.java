package com.example.mesenjni;

import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import mesen.KeyManager;
import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenCore;
import mesen.NotificationListener;
import mesen.interfaces.IAudioDevice;
import mesen.interfaces.IKeyManager;
import mesen.interfaces.IRenderingDevice;

/**
 * Android 上使用 MesenJNI（无头核心）的封装示例。
 *
 * <p>要点：</p>
 * <ul>
 *   <li>渲染：{@link IRenderingDevice} 把帧写成 {@link Bitmap}（Core 回调在渲染线程）。</li>
 *   <li>音频：{@link IAudioDevice} 用 {@link AudioTrack} 播放交错 int16 PCM。</li>
 *   <li>输入：{@link IKeyManager} 用 {@link KeyManager} 注入按键。</li>
 * </ul>
 *
 * <p>注意：{@code mesen.AudioDevice} / {@code mesen.RenderingDevice} 这两个自带实现类
 * 用的是 Java SE 的 {@code javax.sound.sampled} / {@code java.awt.image}，
 * <b>Android 上没有</b>，所以这里自己实现（见下方的 AndroidRenderer / AndroidAudio）。</p>
 */
public final class MesenEmulator
{
    /** 每帧回调；运行在 core 的渲染线程，切 UI 请用 View.post / runOnUiThread。 */
    public interface FrameListener {
        void onFrame(Bitmap frame, int frameNumber);
    }

    private final KeyManager keys = new KeyManager();
    private final NotificationListener events = new NotificationListener();
    private final AndroidRenderer renderer = new AndroidRenderer();
    private final AndroidAudio audio = new AndroidAudio();

    private volatile boolean running = false;

    // ---- 生命周期 --------------------------------------------------------

    /**
     * 初始化并加载 ROM。
     *
     * @param homeFolder  配置/存档目录（建议 {@code context.getFilesDir()}）
     * @param romPath     ROM 文件绝对路径
     * @param enableAudio 是否输出声音
     * @return 是否加载成功
     */
    public boolean start(String homeFolder, String romPath, boolean enableAudio)
    {
        MesenCore.initDll();
        // softwareRenderer=true 才有帧输出；noInput=true 表示输入由 Java 侧提供
        MesenCore.initializeEmu(homeFolder, /*softwareRenderer*/true,
            /*noAudio*/!enableAudio, /*noVideo*/false, /*noInput*/true);

        MesenCore.registerNotificationListener(events);
        MesenCore.registerKeyManager(keys);
        MesenCore.registerRenderingDevice(renderer);
        if(enableAudio) {
            MesenCore.registerAudioDevice(audio);
        }

        mapControllerKeys();

        if(!MesenCore.loadRom(romPath, null)) {
            return false;
        }
        MesenCore.resume();
        running = true;
        return true;
    }

    public void pause()
    {
        if(running) {
            MesenCore.pause();
        }
    }

    public void resume()
    {
        if(running) {
            MesenCore.resume();
        }
    }

    public boolean isPaused()
    {
        return MesenCore.isPaused();
    }

    /** 停止并释放核心（音频线路也一起释放）。 */
    public void stop()
    {
        if(running) {
            MesenCore.stop();
            running = false;
        }
        audio.release();
        MesenCore.release();
    }

    // ---- 输入 ------------------------------------------------------------

    // 控制器按键 -> 虚拟键码（headless 下只有 KeyManager 提供按键，不会有冲突）
    private static final int[][] KEY_MAP = {
        { Mesen.BUTTON_A, 1 }, { Mesen.BUTTON_B, 2 },
        { Mesen.BUTTON_START, 3 }, { Mesen.BUTTON_SELECT, 4 },
        { Mesen.BUTTON_UP, 5 }, { Mesen.BUTTON_DOWN, 6 },
        { Mesen.BUTTON_LEFT, 7 }, { Mesen.BUTTON_RIGHT, 8 },
    };

    private static void mapControllerKeys()
    {
        for(int[] m : KEY_MAP) {
            MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, m[0], m[1]);
        }
    }

    /** 按下/松开按钮，buttonId 取 {@link Mesen} 的 BUTTON_* 常量。 */
    public void setButton(int buttonId, boolean pressed)
    {
        for(int[] m : KEY_MAP) {
            if(m[0] == buttonId) {
                keys.setKeyState(m[1], pressed);
                return;
            }
        }
    }

    /** 松开全部按键。 */
    public void resetButtons()
    {
        keys.resetKeyState();
    }

    // ---- 访问器 ----------------------------------------------------------

    public void setFrameListener(FrameListener l)
    {
        renderer.listener = l;
    }

    /** 最新一帧（可能为 null），用于手动重绘。 */
    public Bitmap getFrame()
    {
        return renderer.frame;
    }

    /** 收集到的通知（getCount / getLastType / getEvents）。 */
    public NotificationListener getEvents()
    {
        return events;
    }

    // ---- 渲染实现：帧 -> Bitmap ------------------------------------------

    private static final class AndroidRenderer implements IRenderingDevice
    {
        volatile Bitmap frame;
        volatile FrameListener listener;
        private int[] pixels;

        @Override
        public void updateFrame(int width, int height, int[] argb, int frameNumber)
        {
            if(frame == null || frame.getWidth() != width || frame.getHeight() != height) {
                frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                pixels = new int[width * height];
            }
            // argb 是复用缓冲：必须在这里马上拷走
            System.arraycopy(argb, 0, pixels, 0, width * height);
            frame.setPixels(pixels, 0, width, 0, 0, width, height);

            FrameListener l = listener;
            if(l != null) {
                l.onFrame(frame, frameNumber);
            }
        }

        @Override public void clearFrame() {}

        @Override public void render(int[] emuHud, int emuHudWidth, int emuHudHeight, boolean emuHudDirty,
                                     int[] scriptHud, int scriptHudWidth, int scriptHudHeight, boolean scriptHudDirty) {}

        @Override public void reset() {}

        @Override public void setFullscreenMode(int mode, int width, int height) {}
    }

    // ---- 音频实现：PCM -> AudioTrack -------------------------------------

    private static final class AndroidAudio implements IAudioDevice
    {
        private AudioTrack track;
        private int sampleRate;
        private boolean stereo;

        @Override
        public void playBuffer(short[] samples, int sampleCount, int rate, boolean isStereo)
        {
            if(sampleCount <= 0) {
                return;
            }
            if(track == null || rate != sampleRate || isStereo != stereo) {
                open(rate, isStereo);
            }
            if(track != null) {
                track.write(samples, 0, sampleCount);
            }
        }

        private void open(int rate, boolean isStereo)
        {
            release();
            sampleRate = rate;
            stereo = isStereo;

            int channelMask = isStereo
                ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
            int minBuf = AudioTrack.getMinBufferSize(
                rate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, rate / 2 * (isStereo ? 4 : 2));

            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(channelMask)
                    .build())
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            track.play();
        }

        @Override public void stop() { release(); }

        @Override public void pause() { if(track != null) track.pause(); }

        @Override public void processEndOfFrame() {}

        @Override public String getAvailableDevices() { return ""; }

        @Override public void setAudioDevice(String name) {}

        synchronized void release()
        {
            if(track != null) {
                try {
                    track.stop();
                } catch(Exception ignored) {
                }
                track.release();
                track = null;
            }
        }
    }
}
