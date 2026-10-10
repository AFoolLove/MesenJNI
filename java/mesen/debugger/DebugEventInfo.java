package mesen.debugger;

/**
 * 事件查看器里的一条事件，对应 Core 的 {@code DebugEventInfo}。
 *
 * <p>{@link #type} 取 {@code Mesen.EVENT_*}，{@link #flags} 是
 * {@code Mesen.EVENT_FLAG_*} 的按位或。</p>
 */
public class DebugEventInfo
{
    /** 内存操作（地址 / 值 / 操作类型 / 内存类型）。 */
    public MemoryOperation operation = new MemoryOperation();

    /** 事件类型（{@code Mesen.EVENT_*}）。 */
    public int type;

    /** 触发时的程序计数器。 */
    public int programCounter;

    /** 扫描线。 */
    public int scanline;

    /** 周期。 */
    public int cycle;

    /** 命中的断点 ID（-1 = 不是断点触发）。 */
    public int breakpointId = -1;

    /** DMA 通道号（-1 = 无 DMA）。 */
    public int dmaChannel = -1;

    // --- DMA 通道配置（SNES 才有意义）------------------------------------
    public int dmaSrcAddress;
    public int dmaTransferSize;
    public int dmaHdmaTableAddress;
    public int dmaSrcBank;
    public int dmaDestAddress;
    public boolean dmaActive;
    public boolean dmaInvertDirection;
    public boolean dmaDecrement;
    public boolean dmaFixedTransfer;
    public boolean dmaHdmaIndirectAddressing;
    public int dmaTransferMode;
    public int dmaHdmaBank;
    public int dmaHdmaLineCounterAndRepeat;
    public boolean dmaDoTransfer;
    public boolean dmaUnusedControlFlag;
    public int dmaUnusedRegister;

    /** 事件标志（{@code Mesen.EVENT_FLAG_*}）。 */
    public int flags;

    /** 寄存器 ID（-1 = 不适用）。 */
    public int registerId = -1;

    /** 目标内存操作（当 {@code flags} 含 {@code EVENT_FLAG_WITH_TARGET_MEMORY} 时有效）。 */
    public MemoryOperation targetMemory = new MemoryOperation();

    /** 显示颜色（ARGB）。 */
    public int color;

    @Override
    public String toString()
    {
        return String.format("DebugEventInfo[type=%d pc=$%04X scanline=%d cycle=%d op=%s]",
            type, programCounter, scanline, cycle, operation);
    }
}
