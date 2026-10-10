package mesen.debugger;

/**
 * 当前指令的执行进度，对应 Core 的 {@code CpuInstructionProgress}。
 */
public class InstructionProgress
{
    /** 本条指令的起始周期。 */
    public long startCycle;

    /** 当前周期。 */
    public long currentCycle;

    /** 最后执行的 opcode。 */
    public int lastOpCode;

    /** 最后一次内存访问。 */
    public MemoryOperation lastMemOperation = new MemoryOperation();

    @Override
    public String toString()
    {
        return String.format("InstructionProgress[op=$%02X, cycle=%d/%d, last=%s]",
            lastOpCode, currentCycle, startCycle, lastMemOperation);
    }
}
