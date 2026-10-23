package mesen.debugger;

/**
 * 断点，对应 Core 的 {@code Breakpoint}。
 *
 * <p>用 {@link mesen.MesenDebugger#setBreakpoints(Breakpoint[])} 提交；
 * 每次提交都要把「全部」断点一起传过去（是覆盖式设置，不是增量添加）。</p>
 */
public class Breakpoint
{
    /** 断点 ID（用于自己标识，覆盖式设置时保持一致即可）。 */
    public int id;

    /** CPU 类型（{@code Mesen.CPU_*}）。 */
    public int cpuType = mesen.Mesen.CPU_NES;

    /** 内存类型（{@code Mesen.MEM_*}）。 */
    public int memoryType = mesen.Mesen.MEM_NES;

    /** 断点类型位标志（{@code Mesen.BREAK_*} 的按位或）。 */
    public int type = mesen.Mesen.BREAK_EXECUTE;

    /** 起始地址。 */
    public int startAddress;

    /** 结束地址（等于起始地址表示单地址断点）。 */
    public int endAddress;

    /** 是否启用。 */
    public boolean enabled = true;

    /** 命中时是否在事件查看器里标记。 */
    public boolean markEvent;

    /** 是否忽略 dummy 读写。 */
    public boolean ignoreDummyOperations;

    /** 条件表达式（空 = 无条件）。 */
    public String condition = "";

    @Override
    public String toString()
    {
        return String.format("Breakpoint[id=%d, cpu=%d, mem=%d, type=%d, $%X-$%X, enabled=%b, cond=%s]",
            id, cpuType, memoryType, type, startAddress, endAddress, enabled, condition);
    }
}
