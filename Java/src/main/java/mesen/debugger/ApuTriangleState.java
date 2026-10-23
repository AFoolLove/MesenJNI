package mesen.debugger;

/** 三角波通道，对应 Core 的 {@code ApuTriangleState}。 */
public class ApuTriangleState
{
    public int Period;
    public int Timer;
    public int SequencePosition;

    public boolean Enabled;
    public double Frequency;
    public int OutputVolume;

    public int LinearCounter;
    public int LinearCounterReload;
    public boolean LinearReloadFlag;

    public ApuLengthCounterState LengthCounter = new ApuLengthCounterState();

    @Override
    public String toString()
    {
        return String.format("ApuTriangleState[period=%d vol=%d freq=%.1f enabled=%b]",
            Period, OutputVolume, Frequency, Enabled);
    }
}