package mesen.debugger;

/**
 * 瓦片地图里单个瓦片的详细信息，对应 Core 的 {@code DebugTilemapTileInfo}。
 *
 * <p>三个 {@code nullable*} 字段取值 {@code Mesen.NULLABLE_*}
 * （-1 = 不适用，0 = false，1 = true）。</p>
 */
public class TilemapTileInfo
{
    public int row = -1;
    public int column = -1;
    public int width = -1;
    public int height = -1;

    public int tileMapAddress = -1;
    public int tileIndex = -1;

    /** 组成该瓦片的地址（1~4 个）。 */
    public int[] tileAddresses = new int[0];

    public int pixelData = -1;

    public int paletteIndex = -1;
    public int paletteAddress = -1;
    public int basePaletteIndex = -1;

    public int attributeAddress = -1;
    public int attributeData = -1;

    public int horizontalMirroring = mesen.Mesen.NULLABLE_UNDEFINED;
    public int verticalMirroring = mesen.Mesen.NULLABLE_UNDEFINED;
    public int highPriority = mesen.Mesen.NULLABLE_UNDEFINED;

    @Override
    public String toString()
    {
        return String.format("TilemapTileInfo[col=%d row=%d tile=$%X palette=%d mirrorH=%d]",
            column, row, tileIndex, paletteIndex, horizontalMirroring);
    }
}
