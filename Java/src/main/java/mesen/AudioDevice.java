package mesen;

import mesen.interfaces.IAudioDevice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * {@link IAudioDevice} 的默认实现：把 PCM 播放到系统的 {@link SourceDataLine}。
 *
 * <p>缓冲区大小可用系统属性 {@code -Dmesen.audioBufferMs=N} 调（默认 500ms）。
 * 太小会导致每次 write 阻塞的开销被放大、反而拖慢模拟速度；太大会增加音频延迟。</p>
 */
public class AudioDevice implements IAudioDevice
{
    private static final int BUFFER_MS = Integer.getInteger("mesen.audioBufferMs", 500);

    private SourceDataLine line;
    private volatile boolean stopped = false;

    @Override
    public void playBuffer(short[] samples, int sampleCount, int sampleRate, boolean stereo)
    {
        if(sampleCount <= 0) {
            return;
        }
        int channels = stereo ? 2 : 1;

        if(line == null || !line.isOpen()
            || (int)line.getFormat().getSampleRate() != sampleRate
            || line.getFormat().getChannels() != channels) {
            close();
            open(sampleRate, channels);
        }

        if(line == null || !line.isOpen()) {
            return; // 无可用音频设备，静默丢弃
        }

        byte[] bytes = new byte[sampleCount * 2];
        for(int i = 0; i < sampleCount; i++) {
            int v = samples[i];
            bytes[i * 2] = (byte)(v & 0xFF);
            bytes[i * 2 + 1] = (byte)((v >> 8) & 0xFF);
        }
        line.write(bytes, 0, bytes.length);
    }

    @Override
    public void stop()
    {
        stopped = true;
        close();
    }

    @Override
    public void pause()
    {
        if(line != null && line.isOpen()) {
            line.stop();
        }
    }

    @Override
    public void processEndOfFrame()
    {
        // 关键：这里绝对不能调 line.drain()。
        // SoundMixer 每帧都是 PlayBuffer() + ProcessEndOfFrame() 同步调用，
        // drain() 会一直等到缓冲区里排队的音频全部播完为止，等于把模拟线程
        // 按音频缓冲深度硬节流（实测 60fps 掉到 ~7fps）。
        // 音频线路自己会按实时速度消费，write() 满了自然会阻塞，这就够了。
    }

    @Override
    public String getAvailableDevices()
    {
        StringBuilder sb = new StringBuilder();
        for(javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
            if(sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(info.getName());
        }
        return sb.toString();
    }

    @Override
    public void setAudioDevice(String name)
    {
        // 默认实现不按名字切换设备；重开当前默认设备即可
        close();
        line = null;
    }

    private void open(int sampleRate, int channels)
    {
        int[] rates = { sampleRate, 44100, 22050, 16000 };
        for(int rate : rates) {
            try {
                AudioFormat fmt = new AudioFormat(rate, 16, channels, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                line = (SourceDataLine)AudioSystem.getLine(info);
                // 核是按实时速度产音频的：write() 满了会自然阻塞，把模拟速度钉在
                // 实时上。缓冲太小 → 每次阻塞的开销被放大，反而掉帧。
                int bytesPerFrame = 2 * channels;   // 16bit * 声道数
                line.open(fmt, rate * bytesPerFrame * BUFFER_MS / 1000);
                line.start();
                stopped = false;
                return;
            } catch(Exception e) {
                line = null;
            }
        }
    }

    /** 关闭并释放音频线路。 */
    public void close()
    {
        if(line != null) {
            line.stop();
            line.close();
            line = null;
        }
    }
}
