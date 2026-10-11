package mesen;

/**
 * 转换后的作弊码（core 实际执行的地址/值形式），对应 Core 的 {@code InternalCheatCode}。
 */
public class InternalCheatCode
{
    /** 目标内存类型（{@code Mesen.MEM_*}）。 */
    public int memoryType;

    /** 地址。 */
    public int address;

    /** 比较值（-1 = 不比较）。 */
    public int compare = -1;

    /** 写入值。 */
    public int value;

    /** 作弊码类型（{@code Mesen.CHEAT_*}）。 */
    public int cheatType;

    /** 适用 CPU（{@code Mesen.CPU_*}）。 */
    public int cpuType;

    /** 是否是 RAM 码。 */
    public boolean isRamCode;

    /** 是否是绝对地址。 */
    public boolean isAbsoluteAddress;

    @Override
    public String toString()
    {
        return String.format("InternalCheatCode[mem=%d addr=$%X value=$%02X compare=%d ram=%b abs=%b]",
            memoryType, address, value, compare, isRamCode, isAbsoluteAddress);
    }
}
