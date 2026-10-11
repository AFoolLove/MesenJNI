package mesen.debugger;

/**
 * 性能分析里的一个函数，对应 Core 的 {@code ProfiledFunction}。
 */
public class ProfiledFunction
{
    /** 独占周期数（不含被调用函数）。 */
    public long exclusiveCycles;

    /** 包含周期数（含被调用函数）。 */
    public long inclusiveCycles;

    /** 调用次数。 */
    public long callCount;

    /** 最短一次调用耗时。 */
    public long minCycles;

    /** 最长一次调用耗时。 */
    public long maxCycles;

    /** 函数入口地址。 */
    public AddressInfo address = new AddressInfo();

    /** 帧标志（{@code Mesen.STACK_FRAME_*}）。 */
    public int flags;

    @Override
    public String toString()
    {
        return String.format("ProfiledFunction[$%X calls=%d exclusive=%d inclusive=%d]",
            address == null ? -1 : address.address, callCount, exclusiveCycles, inclusiveCycles);
    }
}
