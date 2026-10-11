package mesen.debugger;

/** NES 卡带 / mapper 状态，对应 Core 的 {@code CartridgeState}。 */
public class NesCartridgeState
{
    public int PrgRomSize;
    public int ChrRomSize;
    public int ChrRamSize;

    public int PrgPageCount;
    public int PrgPageSize;

    /** PRG 页映射：每页的内存偏移（-1 = 未映射）。 */
    public int[] PrgMemoryOffset = new int[0x100];

    /** PRG 页映射：页类型（{@code Mesen.MEM_*}）。 */
    public int[] PrgType = new int[0x100];

    /** PRG 页映射：访问权限。 */
    public int[] PrgMemoryAccess = new int[0x100];

    public int ChrPageCount;
    public int ChrPageSize;
    public int ChrRamPageSize;

    public int[] ChrMemoryOffset = new int[0x40];
    public int[] ChrType = new int[0x40];
    public int[] ChrMemoryAccess = new int[0x40];

    public int WorkRamPageSize;
    public int SaveRamPageSize;

    /** 镜像方式（{@code Mesen.MIRRORING_*}）。 */
    public int Mirroring;

    public boolean HasBattery;

    /** 有效的自定义项数（{@link #CustomEntries} 里前这么多项有效）。 */
    public int CustomEntryCount;

    public NesMapperStateEntry[] CustomEntries = new NesMapperStateEntry[200];

    public NesCartridgeState()
    {
        for(int i = 0; i < CustomEntries.length; i++) {
            CustomEntries[i] = new NesMapperStateEntry();
        }
    }

    @Override
    public String toString()
    {
        return String.format("NesCartridgeState[PRG=%dKB CHR=%dKB mirroring=%d battery=%b custom=%d]",
            PrgRomSize / 1024, ChrRomSize / 1024, Mirroring, HasBattery, CustomEntryCount);
    }
}