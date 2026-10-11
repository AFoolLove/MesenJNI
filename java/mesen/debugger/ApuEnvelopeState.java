package mesen.debugger;

/** 包络发生器，对应 Core 的 {@code ApuEnvelopeState}。 */
public class ApuEnvelopeState
{
    public boolean StartFlag;
    public boolean Loop;
    public boolean ConstantVolume;
    public int Divider;
    public int Counter;
    public int Volume;

    @Override
    public String toString()
    {
        return "ApuEnvelopeState[volume=" + Volume + ", divider=" + Divider + "]";
    }
}