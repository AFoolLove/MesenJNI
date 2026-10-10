package mesen.interfaces;

/**
 * 音频设备，对应 Core 的 {@code IAudioDevice}。
 *
 * <p>注册后模拟器把混音后的 PCM 推送到这里。格式：交错 int16 PCM（双声道 L,R,L,R…）。
 * {@code playBuffer} 传入的 {@code samples} 是复用缓冲，回调返回后会被覆盖，
 * 必须在方法内消费完毕。</p>
 */
public interface IAudioDevice
{
    /**
     * 收到一帧混音后的音频。
     *
     * @param samples     交错 int16 PCM（只保证前 sampleCount 个有效）
     * @param sampleCount 本次有效样本数
     * @param sampleRate  采样率
     * @param stereo      是否双声道
     */
    void playBuffer(short[] samples, int sampleCount, int sampleRate, boolean stereo);

    /** 停止播放。 */
    void stop();

    /** 暂停播放。 */
    void pause();

    /** 一帧处理结束。 */
    void processEndOfFrame();

    /** 返回可用音频设备列表。 */
    String getAvailableDevices();

    /** 切换到指定音频设备。 */
    void setAudioDevice(String name);
}
