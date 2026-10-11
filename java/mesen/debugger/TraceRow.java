package mesen.debugger;

/**
 * 执行跟踪的一行，对应 Core 的 {@code TraceRow}。
 */
public class TraceRow
{
    /** 该行的程序计数器。 */
    public int programCounter;

    /** CPU 类型（{@code Mesen.CPU_*}）。 */
    public int type;

    /** 指令字节（最多 8 字节）。 */
    public byte[] byteCode = new byte[8];

    /** 指令字节数。 */
    public int byteCodeSize;

    /** 跟踪文本（由 {@link TraceLoggerOptions} 的 Format 生成）。 */
    public String logOutput = "";

    @Override
    public String toString()
    {
        return String.format("TraceRow[$%04X] %s", programCounter, logOutput);
    }
}
