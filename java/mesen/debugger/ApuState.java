package mesen.debugger;

/** NES APU 整机状态，对应 Core 的 {@code ApuState}。 */
public class ApuState
{
    public ApuSquareState Square1 = new ApuSquareState();
    public ApuSquareState Square2 = new ApuSquareState();
    public ApuTriangleState Triangle = new ApuTriangleState();
    public ApuNoiseState Noise = new ApuNoiseState();
    public ApuDmcState Dmc = new ApuDmcState();
    public ApuFrameCounterState FrameCounter = new ApuFrameCounterState();

    @Override
    public String toString()
    {
        return "ApuState[S1=" + Square1 + ", Tri=" + Triangle + ", Noise=" + Noise + ", Dmc=" + Dmc + "]";
    }
}