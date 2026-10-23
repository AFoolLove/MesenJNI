package mesen.debugger;

/**
 * CPU 向量定义（复位/NMI/IRQ 等），对应 Core 的 {@code CpuVectorDefinition}。
 */
public class CpuVector
{
    /** 向量名（如 "NMI"）。 */
    public String name = "";

    /** 向量地址。 */
    public int address;

    /** 向量类型（0=Indirect, 1=Direct, 2=x86, 3=x86WithOffset）。 */
    public int type;

    @Override
    public String toString()
    {
        return String.format("CpuVector[%s @ $%X, type=%d]", name, address, type);
    }
}
