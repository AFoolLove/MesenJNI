package mesen.debugger;

/**
 * 一次内存访问操作，对应 Core 的 {@code MemoryOperationInfo}。
 */
public class MemoryOperation
{
    /** 地址。 */
    public int address;

    /** 值。 */
    public int value;

    /** 操作类型（读/写/取指/操作数…）。 */
    public int operationType;

    /** 内存类型（{@code Mesen.MEM_*}）。 */
    public int memoryType;

    @Override
    public String toString()
    {
        return String.format("MemoryOperation[$%X, val=$%X, op=%d, mem=%d]",
            address, value, operationType, memoryType);
    }
}
