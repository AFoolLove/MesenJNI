package mesen;

/** 历史查看器选项，对应 Core 的 {@code HistoryViewerOptions}。 */
public class HistoryViewerOptions
{
    /** 是否暂停播放。 */
    public boolean isPaused;

    /** 音量（0-100）。 */
    public int volume = 100;

    /** 渲染宽度。 */
    public int width = 256;

    /** 渲染高度。 */
    public int height = 240;

    @Override
    public String toString()
    {
        return "HistoryViewerOptions[paused=" + isPaused + ", volume=" + volume
            + ", " + width + "x" + height + "]";
    }
}
