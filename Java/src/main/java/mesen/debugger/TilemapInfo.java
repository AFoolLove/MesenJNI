package mesen.debugger;

/**
 * 瓦片地图信息，对应 Core 的 {@code DebugTilemapInfo}。
 */
public class TilemapInfo
{
    public int bpp;
    public int format;

    /** 镜像方式（{@code Mesen.TILEMAP_MIRRORING_*}）。 */
    public int mirroring;

    public int tileWidth;
    public int tileHeight;

    public int scrollX;
    public int scrollWidth;
    public int scrollY;
    public int scrollHeight;

    public int rowCount;
    public int columnCount;
    public int tilemapAddress;
    public int tilesetAddress;

    /** 优先级（-1 = 不适用）。 */
    public int priority = -1;

    @Override
    public String toString()
    {
        return String.format("TilemapInfo[%dx%d tiles, tile %dx%d, mirroring=%d, addr=$%X]",
            columnCount, rowCount, tileWidth, tileHeight, mirroring, tilemapAddress);
    }
}
