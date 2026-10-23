package mesen.debugger;

/**
 * 调色板信息，对应 Core 的 {@code DebugPaletteInfo}。
 */
public class PaletteInfo
{
    /** 原始调色板所在内存（{@code Mesen.MEM_*}）。 */
    public int paletteMemType;

    /** 原始调色板在该内存里的偏移。 */
    public int paletteMemOffset;

    /** {@link #paletteMemType}/{@link #paletteMemOffset} 是否有效。 */
    public boolean hasMemType;

    /** 总颜色数。 */
    public int colorCount;

    /** 背景色数。 */
    public int bgColorCount;

    /** 精灵色数。 */
    public int spriteColorCount;

    /** 精灵调色板起始偏移。 */
    public int spritePaletteOffset;

    /** 每个调色板包含多少颜色。 */
    public int colorsPerPalette;

    /** 原始格式（{@code Mesen.RAW_PALETTE_*}）。 */
    public int rawFormat;

    /** 原始调色板值。 */
    public int[] rawPalette = new int[0];

    /** 转成 ARGB 的调色板。 */
    public int[] rgbPalette = new int[0];

    @Override
    public String toString()
    {
        return String.format("PaletteInfo[%d colors (%d bg + %d sprite), perPalette=%d, raw=%d]",
            colorCount, bgColorCount, spriteColorCount, colorsPerPalette, rawFormat);
    }
}
