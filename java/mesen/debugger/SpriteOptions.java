package mesen.debugger;

/**
 * 精灵预览选项，对应 Core 的 {@code GetSpritePreviewOptions}。
 */
public class SpriteOptions
{
    /** 背景色（{@code Mesen.SPRITE_BG_*}）。 */
    public int background = mesen.Mesen.SPRITE_BG_GRAY;

    /** 过滤（{@code Mesen.SPRITE_FILTER_*}）。 */
    public int filter = mesen.Mesen.SPRITE_FILTER_BOTH;
}
