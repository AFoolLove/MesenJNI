package mesen.debugger;

/**
 * 精灵列表渲染结果。
 *
 * <ul>
 *   <li>{@link #sprites}：每个精灵的元信息。</li>
 *   <li>{@link #spritePreviews}：所有精灵的缩略图，长度 = {@code spriteCount * width * height}
 *       （逐精灵连续排列）。</li>
 *   <li>{@link #screenPreview}：整屏合成预览，长度 = {@code width * height}。</li>
 * </ul>
 */
public class SpriteListResult
{
    /** 预览区信息。 */
    public SpritePreviewInfo info;

    /** 精灵列表。 */
    public SpriteInfo[] sprites = new SpriteInfo[0];

    /** 每个精灵的预览图（ARGB，逐精灵连续）。 */
    public int[] spritePreviews = new int[0];

    /** 整屏合成预览（ARGB）。 */
    public int[] screenPreview = new int[0];

    @Override
    public String toString()
    {
        return String.format("SpriteListResult[%s, %d sprites]",
            info, sprites == null ? 0 : sprites.length);
    }
}
