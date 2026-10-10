package mesen;

import mesen.interfaces.IAudioDevice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * {@link IAudioDevice} 的默认实现：把 PCM 播放到系统的 {@link SourceDataLine}。
 */
public class AudioDevice implements IAudioDevice
{
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
        if(line != null && line.isOpen() && !stopped) {
            line.drain();
        }
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
                line.open(fmt, rate * 4);
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
