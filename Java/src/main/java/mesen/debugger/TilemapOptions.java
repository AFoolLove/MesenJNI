package mesen.debugger;

/**
 * 瓦片地图（tilemap）渲染选项，对应 Core 的 {@code GetTilemapOptions}。
 *
 * <p>VRAM 与调色板由 JNI 内部按机种取（NES 用 {@code MEM_NES_PPU}），
 * Java 侧不用自己准备缓冲。</p>
 */
public class TilemapOptions
{
    /** 层号（多背景层机种用；NES 用 0）。 */
    public int layer;

    /** 主时钟（用于高亮「本帧之后」的变化；0 = 不比较）。 */
    public long masterClock;

    /** 瓦片高亮模式（{@code Mesen.TILEMAP_HIGHLIGHT_*}）。 */
    public int tileHighlightMode = mesen.Mesen.TILEMAP_HIGHLIGHT_NONE;

    /** 属性高亮模式（{@code Mesen.TILEMAP_HIGHLIGHT_*}）。 */
    public int attributeHighlightMode = mesen.Mesen.TILEMAP_HIGHLIGHT_NONE;

    /** 显示模式（{@code Mesen.TILEMAP_DISPLAY_*}）。 */
    public int displayMode = mesen.Mesen.TILEMAP_DISPLAY_DEFAULT;

    /** 背景色（{@code Mesen.TILEMAP_BG_*}）。 */
    public int background = mesen.Mesen.TILEMAP_BG_DEFAULT;
}
