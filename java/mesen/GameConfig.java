package mesen;

/** 单游戏配置，对应 Core 的 {@code GameConfig}。 */
public class GameConfig
{
    /** DIP 开关位（街机类机种用）。 */
    public int dipSwitches;

    /** 是否覆盖过扫描区域。 */
    public boolean overrideOverscan;

    public int overscanLeft;
    public int overscanRight;
    public int overscanTop;
    public int overscanBottom;

    @Override
    public String toString()
    {
        return "GameConfig[dip=" + dipSwitches + ", overrideOverscan=" + overrideOverscan + "]";
    }
}
