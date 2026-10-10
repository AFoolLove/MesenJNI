package mesen.debugger;

/**
 * 反汇编里的「有效地址」信息，对应 Core 的 {@code EffectiveAddressInfo}。
 *
 * <p>{@link #address} 为 {@code -1} 表示没有有效地址。</p>
 */
public class EffectiveAddress
{
    /** 有效地址；-1 = 无。 */
    public long address = -1;

    /** 内存类型（{@code Mesen.MEM_*}）。 */
    public int memoryType;

    /** 该地址处的值宽度（字节）。 */
    public int valueSize;

    /** 是否显示该地址。 */
    public boolean showAddress;

    @Override
    public String toString()
    {
        return String.format("EffectiveAddress[$%X, size=%d, show=%b]", address, valueSize, showAddress);
    }
}
