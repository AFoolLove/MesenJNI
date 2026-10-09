package mesen;

/**
 * Java 实现的音频设备（对应 Core 的 IAudioDevice）。
 * 通过 MesenCore.registerAudioDevice 注册，Core 会把 PCM 数据回调到这里。
 */
public interface AudioDevice
{
    /** 收到一帧音频；samples 为交错 int16 PCM（双声道时 L,R,L,R…）。 */
    void playBuffer(short[] samples, int sampleRate, boolean stereo);

    void stop();
    void pause();
    void processEndOfFrame();
    String getAvailableDevices();
    void setAudioDevice(String name);
}
