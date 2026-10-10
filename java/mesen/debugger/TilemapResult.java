package mesen.debugger;

/**
 * 瓦片地图渲染结果：信息 + {@code width x height} 的 ARGB 像素。
 */
public class TilemapResult
{
    /** 渲染信息（可能为 null，例如该机种没有 PPU 工具）。 */
    public TilemapInfo info;

    /** 图像宽度（像素）。 */
    public int width;

    /** 图像高度（像素）。 */
    public int height;

    /** ARGB 像素（行优先，长度 = width * height）。 */
    public int[] pixels = new int[0];

    @Override
    public String toString()
    {
        return String.format("TilemapResult[%dx%d, %s]", width, height, info);
    }
}
