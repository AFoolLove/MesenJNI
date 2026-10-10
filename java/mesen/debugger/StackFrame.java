package mesen.debugger;

/**
 * 调用栈的一帧，对应 Core 的 {@code StackFrameInfo}。
 */
public class StackFrame
{
    /** 本帧的源地址。 */
    public int source;

    /** 源地址的绝对地址信息。 */
    public AddressInfo absSource = new AddressInfo();

    /** 跳转目标地址。 */
    public int target;

    /** 目标地址的绝对地址信息。 */
    public AddressInfo absTarget = new AddressInfo();

    /** 返回地址。 */
    public int returnAddress;

    /** 返回时的栈指针。 */
    public int returnStackPointer;

    /** 返回地址的绝对地址信息。 */
    public AddressInfo absReturn = new AddressInfo();

    /** 帧标志（{@code Mesen.STACK_FRAME_*}）。 */
    public int flags;

    @Override
    public String toString()
    {
        return String.format("StackFrame[$%X -> $%X, ret=$%X, flags=%d]",
            source, target, returnAddress, flags);
    }
}
