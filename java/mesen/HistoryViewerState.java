package mesen;

/** 历史查看器状态，对应 Core 的 {@code HistoryViewerState}。 */
public class HistoryViewerState
{
    /** 当前播放位置（帧）。 */
    public int position;

    /** 历史总长度（帧）。 */
    public int length;

    /** 音量（0-100）。 */
    public int volume;

    /** 录制时的帧率。 */
    public double fps = 60.0;

    /** 是否暂停。 */
    public boolean isPaused;

    /** 段落数（有效的是 {@code segments[0..segmentCount-1]}）。 */
    public int segmentCount;

    /** 段落起始位置（固定 1000 项）。 */
    public int[] segments = new int[1000];

    @Override
    public String toString()
    {
        return String.format("HistoryViewerState[position=%d length=%d volume=%d fps=%.3f paused=%b segments=%d]",
            position, length, volume, fps, isPaused, segmentCount);
    }
}
