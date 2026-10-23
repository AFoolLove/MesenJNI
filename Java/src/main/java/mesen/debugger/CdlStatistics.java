package mesen.debugger;

/**
 * CDL（代码数据日志）统计，对应 Core 的 {@code CdlStatistics}。
 */
public class CdlStatistics
{
    /** 已标记为代码的字节数。 */
    public int codeBytes;

    /** 已标记为数据的字节数。 */
    public int dataBytes;

    /** 总字节数。 */
    public int totalBytes;

    /** 跳转目标数量。 */
    public int jumpTargetCount;

    /** 函数数量。 */
    public int functionCount;

    /** 已绘制的 CHR 字节数（NES 专用）。 */
    public int drawnChrBytes;

    /** CHR 总字节数（NES 专用）。 */
    public int totalChrBytes;

    @Override
    public String toString()
    {
        return String.format("CdlStatistics[code=%d, data=%d, total=%d, jumps=%d, funcs=%d]",
            codeBytes, dataBytes, totalBytes, jumpTargetCount, functionCount);
    }
}
