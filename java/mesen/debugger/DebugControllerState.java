package mesen.debugger;

/**
 * 输入覆盖状态，对应 Core 的 {@code DebugControllerState}。
 *
 * <p>配合 {@link mesen.MesenDebugger#setInputOverrides(int, DebugControllerState)}
 * 使用：先用 {@link mesen.MesenDebugger#getAvailableInputOverrides()} 查哪些端口可用，
 * 再给对应端口设置要强制按下的键。</p>
 */
public class DebugControllerState
{
    public boolean A;
    public boolean B;
    public boolean X;
    public boolean Y;
    public boolean L;
    public boolean R;
    public boolean U;
    public boolean D;
    public boolean Up;
    public boolean Down;
    public boolean Left;
    public boolean Right;
    public boolean Select;
    public boolean Start;

    /** 是否有任意键被按下。 */
    public boolean hasPressedButton()
    {
        return A || B || X || Y || L || R || U || D
            || Up || Down || Left || Right || Select || Start;
    }

    @Override
    public String toString()
    {
        return String.format("DebugControllerState[A=%b B=%b X=%b Y=%b Up=%b Down=%b Left=%b Right=%b Start=%b]",
            A, B, X, Y, Up, Down, Left, Right, Start);
    }
}
