package mesen;

/** 音频播放器配置，对应 Core 的 {@code AudioPlayerConfig}（NSF/GBS 等音乐文件）。 */
public class AudioPlayerConfig
{
    /** 音量（0-100）。 */
    public int volume = 100;

    /** 是否循环。 */
    public boolean repeat;

    /** 是否随机播放。 */
    public boolean shuffle;

    @Override
    public String toString()
    {
        return "AudioPlayerConfig[volume=" + volume + ", repeat=" + repeat + ", shuffle=" + shuffle + "]";
    }
}
