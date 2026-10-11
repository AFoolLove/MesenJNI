package mesen.debugger;

/** 噪声通道，对应 Core 的 {@code ApuNoiseState}。 */
public class ApuNoiseState
{
    public int Period;
    public int Timer;
    public int ShiftRegister;
    public boolean ModeFlag;

    public boolean Enabled;
    public double Frequency;
    public int OutputVolume;

    public ApuLengthCounterState LengthCounter = new ApuLengthCounterState();
    public ApuEnvelopeState Envelope = new ApuEnvelopeState();

    @Override
    public String toString()
    {
        return String.format("ApuNoiseState[period=%d shift=$%04X vol=%d enabled=%b]",
            Period, ShiftRegister, OutputVolume, Enabled);
    }
}