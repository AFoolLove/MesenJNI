package mesen.debugger;

/**
 * NES PPU 状态，对应 Core 的 {@code NesPpuState}。
 *
 * <p>字段按 core 结构体的三个分组（Status / Mask / Control）平铺，方便 JNI 直接映射。
 * 用 {@link mesen.MesenDebugger#getNesPpuState()} 读、
 * {@link mesen.MesenDebugger#setNesPpuState(NesPpuState)} 写回。</p>
 */
public class NesPpuState
{
    // --- PPUStatusFlags ---------------------------------------------------
    /** 精灵溢出。 */
    public boolean spriteOverflow;

    /** 精灵 0 命中。 */
    public boolean sprite0Hit;

    /** 垂直消隐（VBlank）。 */
    public boolean verticalBlank;

    // --- PpuMaskFlags -----------------------------------------------------
    /** 灰度模式。 */
    public boolean grayscale;

    /** 背景左 8 像素遮罩。 */
    public boolean backgroundMask;

    /** 精灵左 8 像素遮罩。 */
    public boolean spriteMask;

    /** 背景渲染开关。 */
    public boolean backgroundEnabled;

    /** 精灵渲染开关。 */
    public boolean spritesEnabled;

    /** 强调红色。 */
    public boolean intensifyRed;

    /** 强调绿色。 */
    public boolean intensifyGreen;

    /** 强调蓝色。 */
    public boolean intensifyBlue;

    // --- PpuControlFlags --------------------------------------------------
    /** 背景图案表地址（$0000 或 $1000）。 */
    public int backgroundPatternAddr;

    /** 精灵图案表地址（$0000 或 $1000）。 */
    public int spritePatternAddr;

    /** VRAM 地址自增方向（false=+1, true=+32）。 */
    public boolean verticalWrite;

    /** 8x16 精灵模式。 */
    public boolean largeSprites;

    /** 第二 PPU（VS 双机）。 */
    public boolean secondaryPpu;

    /** VBlank 时触发 NMI。 */
    public boolean nmiOnVerticalBlank;

    // --- 时序 / 内部寄存器 -------------------------------------------------
    /** 当前扫描线。 */
    public int scanline;

    /** 当前周期。 */
    public int cycle;

    /** 帧计数。 */
    public int frameCount;

    /** NMI 触发的扫描线。 */
    public int nmiScanline;

    /** 每帧扫描线总数。 */
    public int scanlineCount;

    /** OAM 安全扫描线。 */
    public int safeOamScanline;

    /** PPU 总线地址。 */
    public int busAddress;

    /** 读取缓冲。 */
    public int memoryReadBuffer;

    /** VRAM 地址（v）。 */
    public int videoRamAddr;

    /** 临时 VRAM 地址（t）。 */
    public int tmpVideoRamAddr;

    /** 细部 X 滚动（x）。 */
    public int scrollX;

    /** 写锁存（w）。 */
    public boolean writeToggle;

    /** OAM 地址。 */
    public int spriteRamAddr;

    /** 二级 OAM 地址。 */
    public int secondaryOamAddr;

    @Override
    public String toString()
    {
        return String.format("NesPpuState[scanline=%d cycle=%d frame=%d VRAM=$%04X TMP=$%04X X=%d W=%b vblank=%b bg=%b sprites=%b]",
            scanline, cycle, frameCount, videoRamAddr, tmpVideoRamAddr, scrollX, writeToggle,
            verticalBlank, backgroundEnabled, spritesEnabled);
    }
}
