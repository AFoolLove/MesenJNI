package com.mesen;

/** Emulation timing information for a CPU. */
public class TimingInfo
{
    public double fps = 0.0;
    public long masterClock = 0;
    public long masterClockRate = 0;
    public long frameCount = 0;
    public long scanlineCount = 0;
    public int firstScanline = 0;
    public long cycleCount = 0;
}
