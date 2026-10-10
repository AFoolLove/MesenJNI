package mesen.debugger;

/**
 * 单个精灵的信息，对应 Core 的 {@code DebugSpriteInfo}。
 *
 * <p>{@code nullable*} 字段取值 {@code Mesen.NULLABLE_*}（-1 = 不适用）。</p>
 */
public class SpriteInfo
{
    public int tileIndex = -1;
    public int tileAddress = -1;
    public int paletteAddress = -1;
    public int format;

    public int spriteIndex = -1;

    public int x = -1;
    public int y = -1;
    public int rawX = -1;
    public int rawY = -1;

    public int bpp = 2;
    public int palette = -1;

    /** 优先级（{@code Mesen.SPRITE_PRIORITY_*}）。 */
    public int priority = mesen.Mesen.SPRITE_PRIORITY_UNDEFINED;

    /** 模式（{@code Mesen.SPRITE_MODE_*}）。 */
    public int mode = mesen.Mesen.SPRITE_MODE_UNDEFINED;

    public int width;
    public int height;

    public int horizontalMirror = mesen.Mesen.NULLABLE_UNDEFINED;
    public int verticalMirror = mesen.Mesen.NULLABLE_UNDEFINED;
    public int mosaicEnabled = mesen.Mesen.NULLABLE_UNDEFINED;
    public int transformEnabled = mesen.Mesen.NULLABLE_UNDEFINED;
    public int doubleSize = mesen.Mesen.NULLABLE_UNDEFINED;
    public int useSecondTable = mesen.Mesen.NULLABLE_UNDEFINED;

    public int transformParamIndex = -1;

    /** 可见性（{@code Mesen.SPRITE_VISIBILITY_*}）。 */
    public int visibility = mesen.Mesen.SPRITE_VISIBILITY_OFFSCREEN;

    public boolean useExtendedVram;

    /** 组成该精灵的瓦片地址。 */
    public int[] tileAddresses = new int[0];

    @Override
    public String toString()
    {
        return String.format("SpriteInfo[#%d tile=$%X @(%d,%d) %dx%d palette=%d vis=%d]",
            spriteIndex, tileIndex, x, y, width, height, palette, visibility);
    }
}
