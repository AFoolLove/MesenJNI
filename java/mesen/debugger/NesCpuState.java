package mesen.debugger;

/**
 * NES CPU 寄存器状态，对应 Core 的 {@code NesCpuState}。
 *
 * <p>用 {@link mesen.MesenDebugger#getNesCpuState()} 读、
 * {@link mesen.MesenDebugger#setNesCpuState(NesCpuState)} 写回。</p>
 */
public class NesCpuState
{
    /** CPU 周期计数。 */
    public long cycleCount;

    /** 程序计数器（PC）。 */
    public int pc;

    /** 栈指针（S）。 */
    public int sp;

    /** 累加器（A）。 */
    public int a;

    /** 变址寄存器 X。 */
    public int x;

    /** 变址寄存器 Y。 */
    public int y;

    /** 状态寄存器（P）。 */
    public int ps;

    /** IRQ 标志（uint8）。 */
    public int irqFlag;

    /** NMI 挂起标志。 */
    public boolean nmiFlag;

    @Override
    public String toString()
    {
        return String.format("NesCpuState[PC=$%04X A=$%02X X=$%02X Y=$%02X SP=$%02X PS=$%02X cycles=%d nmi=%b]",
            pc, a, x, y, sp, ps, cycleCount, nmiFlag);
    }
}
