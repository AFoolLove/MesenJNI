package mesen;

/**
 * 内存读写（对应 Core 的内存访问 API）。
 *
 * <p>{@code memoryType} 取值见 {@link Mesen} 的 {@code MEMORY_*} 常量。</p>
 */
public final class MesenMemory
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenMemory() {
    }

    public static native long getMemorySize(int memoryType);
    public static native int getMemoryValue(int memoryType, long address);
    public static native byte[] getMemoryValues(int memoryType, long start, long end);
    public static native void setMemoryValue(int memoryType, long address, int value);
    public static native void setMemoryValues(int memoryType, long address, byte[] data);
}
