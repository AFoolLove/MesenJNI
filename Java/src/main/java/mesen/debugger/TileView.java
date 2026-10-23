package mesen.debugger;

/**
 * 瓦片视图渲染结果：{@code pixels} 是 {@code width x height} 的 ARGB 数组。
 */
public class TileView
{
    /** 图像宽度（像素）。 */
    public int width;

    /** 图像高度（像素）。 */
    public int height;

    /** ARGB 像素（行优先，长度 = width * height）。 */
    public int[] pixels = new int[0];

    @Override
    public String toString()
    {
        return String.format("TileView[%dx%d]", width, height);
    }
}
