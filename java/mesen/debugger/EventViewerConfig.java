package mesen.debugger;

/**
 * 事件查看器配置。
 *
 * <p>{@link #categoryNames} 由 {@link mesen.MesenDebugger#getEventViewerConfig(int)} 填好
 * （顺序与 core 的 struct 字段一致），你只需要按自己的需要改
 * {@link #visible} 和 {@link #colors}，再交给
 * {@link mesen.MesenDebugger#setEventViewerConfig(int, EventViewerConfig)}。</p>
 *
 * <p>注意 core 没有「读回配置」的接口——.NET 那边配置也是 UI 自己持有的，
 * 所以这里以 {@code getEventViewerConfig()} 给出的空配置为起点自己填。</p>
 */
public class EventViewerConfig
{
    /** 类别名（只读，JNI 填）。 */
    public String[] categoryNames = new String[0];

    /** 每个类别是否可见（默认全 false）。 */
    public boolean[] visible = new boolean[0];

    /** 每个类别的颜色（ARGB，默认全 0）。 */
    public int[] colors = new int[0];

    /** 是否显示上一帧的事件。 */
    public boolean showPreviousFrameEvents;

    @Override
    public String toString()
    {
        int visibleCount = 0;
        for(boolean v : visible) {
            if(v) visibleCount++;
        }
        return String.format("EventViewerConfig[%d 个类别, %d 个可见, prevFrame=%b]",
            categoryNames.length, visibleCount, showPreviousFrameEvents);
    }
}
