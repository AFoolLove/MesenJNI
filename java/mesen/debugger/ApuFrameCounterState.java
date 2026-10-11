package mesen.debugger;

/** 帧计数器，对应 Core 的 {@code ApuFrameCounterState}。 */
public class ApuFrameCounterState
{
    public boolean FiveStepMode;
    public int SequencePosition;
    public boolean IrqEnabled;

    @Override
    public String toString()
    {
        return "ApuFrameCounterState[fiveStep=" + FiveStepMode + ", seq=" + SequencePosition + "]";
    }
}