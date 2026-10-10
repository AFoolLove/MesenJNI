package mesen.debugger;

/**
 * 精灵预览区信息，对应 Core 的 {@code DebugSpritePreviewInfo}。
 */
public class SpritePreviewInfo
{
    public int width;
    public int height;

    /** 精灵数量。 */
    public int spriteCount;

    public int coordOffsetX;
    public int coordOffsetY;

    public int visibleX;
    public int visibleY;
    public int visibleWidth;
    public int visibleHeight;

    public boolean wrapBottomToTop;
    public boolean wrapRightToLeft;

    @Override
    public String toString()
    {
        return String.format("SpritePreviewInfo[%dx%d, sprites=%d, visible=(%d,%d %dx%d)]",
            width, height, spriteCount, visibleX, visibleY, visibleWidth, visibleHeight);
    }
}
