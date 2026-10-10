package mesen.debugger;

/**
 * 反汇编输出的一行，对应 Core 的 {@code CodeLineData}。
 */
public class CodeLine
{
    /** 行对应的地址（相对当前 CPU 的地址空间）。 */
    public int address;

    /** 绝对地址（跨内存类型）。 */
    public AddressInfo absoluteAddress = new AddressInfo();

    /** 指令长度（字节）。 */
    public int opSize;

    /** 反汇编标志位（内部使用）。 */
    public int flags;

    /** 有效地址信息（操作数指向的地址）。 */
    public EffectiveAddress effectiveAddress = new EffectiveAddress();

    /** 该行对应的值（读到的字节/字）。 */
    public int value;

    /** 本行所属的 CPU 类型（{@code Mesen.CPU_*}）。 */
    public int lineCpuType;

    /** 指令字节（最多 8 字节）。 */
    public byte[] byteCode = new byte[8];

    /** 反汇编文本。 */
    public String text = "";

    /** 注释（标签、跳转目标等）。 */
    public String comment = "";

    @Override
    public String toString()
    {
        return String.format("%04X: %-32s %s", address, text, comment);
    }
}
