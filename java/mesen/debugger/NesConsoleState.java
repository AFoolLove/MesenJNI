package mesen.debugger;

/**
 * NES 整机状态，对应 Core 的 {@code NesState}。
 *
 * <p>{@code cpu} / {@code ppu} 复用已有的 {@link NesCpuState} / {@link NesPpuState}
 * （那两个类本来就用于寄存器状态读写）。</p>
 */
public class NesConsoleState
{
    public NesCpuState cpu = new NesCpuState();
    public NesPpuState ppu = new NesPpuState();
    public NesCartridgeState cartridge = new NesCartridgeState();
    public ApuState apu = new ApuState();

    /** 主时钟频率（Hz）。 */
    public int clockRate;

    @Override
    public String toString()
    {
        return "NesConsoleState[clock=" + clockRate + "Hz, " + cartridge + "]";
    }
}