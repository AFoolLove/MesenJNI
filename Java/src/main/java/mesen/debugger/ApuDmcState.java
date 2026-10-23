package mesen.debugger;

/** DMC 通道，对应 Core 的 {@code ApuDmcState}。 */
public class ApuDmcState
{
    public double SampleRate;
    public int SampleAddr;
    public int NextSampleAddr;
    public int SampleLength;

    public boolean Loop;
    public boolean IrqEnabled;
    public int Period;
    public int Timer;
    public int BytesRemaining;
    public int BitsRemaining;

    public int OutputVolume;

    @Override
    public String toString()
    {
        return String.format("ApuDmcState[sampleRate=%.1f addr=$%04X remaining=%d vol=%d]",
            SampleRate, SampleAddr, BytesRemaining, OutputVolume);
    }
}