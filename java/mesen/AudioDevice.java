package mesen;

/**
 * 音频设备，对应 Core 的 {@code IAudioDevice}。
 *
 * <p>通过 {@link MesenCore#registerAudioDevice(AudioDevice)} 注册后，模拟器会把混音后的
 * PCM 数据推送到这里，而不是播放到声卡。可用于把音频写入文件、走网络、或交给自定义播放器。</p>
 *
 * <p>除 {@link #playBuffer(short[], int, int, boolean)} 外，其余方法都有默认的空实现，
 * 只需覆盖你关心的方法即可。</p>
 *
 * <p><b>格式</b>：交错 int16 PCM。双声道时按 {@code L,R,L,R,…} 排列；采样率由
 * {@link MesenCore#getAudioSampleRate()} 决定（默认 48000，可用配置改）。</p>
 *
 * <p><b>缓冲复用</b>：{@link #playBuffer(short[], int, int, boolean)} 传入的
 * {@code samples} 是复用的缓冲池数组，其长度可能大于本次有效样本数 {@code sampleCount}，
 * 且<b>回调返回后内容会被覆盖</b>。必须在方法内消费（拷贝/处理）完毕，不要持有引用。</p>
 *
 * <p><b>线程</b>：回调运行在 Core 的模拟线程（非 EDT）。</p>
 */
public interface AudioDevice
{
    /**
     * 收到一帧混音后的音频。
     *
     * @param samples     交错 int16 PCM（复用缓冲，只保证前 sampleCount 个元素有效）
     * @param sampleCount 本次有效的 int16 样本个数（双声道时为帧数×2）
     * @param sampleRate  采样率，如 48000
     * @param stereo      是否双声道
     */
    void playBuffer(short[] samples, int sampleCount, int sampleRate, boolean stereo);

    /** 停止播放（清空缓冲）。 */
    default void stop() {}

    /** 暂停播放。 */
    default void pause() {}

    /** 一帧处理结束。 */
    default void processEndOfFrame() {}

    /** 返回可用音频设备列表（可返回空字符串）。 */
    default String getAvailableDevices() { return ""; }

    /** 切换到指定音频设备。 */
    default void setAudioDevice(String name) {}
}
