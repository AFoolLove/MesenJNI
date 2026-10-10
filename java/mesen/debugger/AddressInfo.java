package mesen.debugger;

/**
 * 地址 + 内存类型，对应 Core 的 {@code AddressInfo}。
 */
public class AddressInfo
{
    /** 地址。 */
    public int address;

    /** 内存类型，取值为 {@link mesen.Mesen} 的 {@code MEM_*} 常量。 */
    public int memoryType;

    public AddressInfo()
    {
    }

    public AddressInfo(int address, int memoryType)
    {
        this.address = address;
        this.memoryType = memoryType;
    }

    @Override
    public String toString()
    {
        return String.format("AddressInfo[$%X, mem=%d]", address, memoryType);
    }
}
