package mesen.debugger;

/** 长度计数器，对应 Core 的 {@code ApuLengthCounterState}。 */
public class ApuLengthCounterState
{
    public boolean Halt;
    public int Counter;
    public int ReloadValue;

    @Override
    public String toString()
    {
        return "ApuLengthCounterState[halt=" + Halt + ", counter=" + Counter + "]";
    }
}