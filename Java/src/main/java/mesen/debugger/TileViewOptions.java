package mesen.debugger;

/**
 * 瓦片视图选项，对应 Core 的 {@code GetTileViewOptions}。
 *
 * <p>{@link #width}/{@link #height} 单位是<b>瓦片数</b>（每瓦片 8x8 像素），
 * 渲染出来的图像尺寸是 {@code width*8 x height*8}。</p>
 */
public class TileViewOptions
{
    /** 瓦片数据所在内存（{@code Mesen.MEM_*}），例如 CHR ROM / CHR RAM。 */
    public int memoryType = mesen.Mesen.MEM_NES_CHR_ROM;

    /** 瓦片格式（{@code Mesen.TILE_FORMAT_*}）；NES 用 {@code TILE_FORMAT_NES_BPP2}。 */
    public int format = mesen.Mesen.TILE_FORMAT_NES_BPP2;

    /** 排布（{@code Mesen.TILE_LAYOUT_*}）。 */
    public int layout = mesen.Mesen.TILE_LAYOUT_NORMAL;

    /** 过滤（{@code Mesen.TILE_FILTER_*}）。 */
    public int filter = mesen.Mesen.TILE_FILTER_NONE;

    /** 背景色（{@code Mesen.TILE_BG_*}）。 */
    public int background = mesen.Mesen.TILE_BG_DEFAULT;

    /** 每行瓦片数。 */
    public int width = 16;

    /** 行数。 */
    public int height = 16;

    /** 起始地址（内存内的偏移）。 */
    public int startAddress;

    /** 调色板索引。 */
    public int palette;

    /** 是否用灰度调色板。 */
    public boolean useGrayscalePalette;
}
