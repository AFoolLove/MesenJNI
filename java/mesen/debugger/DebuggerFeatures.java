package mesen.debugger;

/**
 * 某个 CPU 支持的调试能力，对应 Core 的 {@code DebuggerFeatures}。
 *
 * <p>用来判断「这个 CPU 能不能单步跳过 / 能不能显示调用栈 / 能不能改 PC」等，
 * 写通用调试器前先查一下。</p>
 */
public class DebuggerFeatures
{
    public boolean runToIrq;
    public boolean runToNmi;
    public boolean stepOver;
    public boolean stepOut;
    public boolean stepBack;
    public boolean changeProgramCounter;
    public boolean callStack;
    public boolean cpuCycleStep;

    /** IRQ 向量在 {@link #cpuVectors} 里的偏移。 */
    public int irqVectorOffset;

    /** 有效向量的个数。 */
    public int cpuVectorCount;

    /** CPU 向量表。 */
    public CpuVector[] cpuVectors = new CpuVector[0];

    @Override
    public String toString()
    {
        return String.format("DebuggerFeatures[stepOver=%b, stepOut=%b, stepBack=%b, callStack=%b, vectors=%d]",
            stepOver, stepOut, stepBack, callStack, cpuVectorCount);
    }
}
