package mesen.debugger;

/** 方波通道，对应 Core 的 {@code ApuSquareState}。 */
public class ApuSquareState
{
    public int Duty;
    public int DutyPosition;
    public int Period;
    public int Timer;

    public boolean SweepEnabled;
    public boolean SweepNegate;
    public int SweepPeriod;
    public int SweepShift;

    public boolean Enabled;
    public int OutputVolume;
    public double Frequency;

    public ApuLengthCounterState LengthCounter = new ApuLengthCounterState();
    public ApuEnvelopeState Envelope = new ApuEnvelopeState();

    @Override
    public String toString()
    {
        return String.format("ApuSquareState[duty=%d period=%d vol=%d freq=%.1f enabled=%b]",
            Duty, Period, OutputVolume, Frequency, Enabled);
    }
}