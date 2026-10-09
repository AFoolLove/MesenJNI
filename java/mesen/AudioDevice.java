package mesen;

/**
 * Java 实现的音频设备（对应 Core 的 IAudioDevice）。
 * 通过 MesenCore.registerAudioDevice 注册，Core 会把 PCM 数据回调到这里。
 */
public interface AudioDevice
{
    /**
     * 收到一帧音频；samples 为交错 int16 PCM（双声道时 L,R,L,R…）。
     * sampleCount 是本次有效的样本数（samples 数组长度可能更大，是复用的缓冲池）。
     * 注意：samples 是复用的缓冲，回调返回后内容会被覆盖，需在方法内消费掉。
     */
    void playBuffer(short[] samples, int sampleCount, int sampleRate, boolean stereo);

    void stop();
    void pause();
    void processEndOfFrame();
    String getAvailableDevices();
    void setAudioDevice(String name);
}
