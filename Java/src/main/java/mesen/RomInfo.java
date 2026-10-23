package mesen;

/** Information about the currently loaded ROM. */
public class RomInfo
{
    public String romPath = "";
    public String patchPath = "";
    public int format = Mesen.ROM_FORMAT_UNKNOWN;
    public int consoleType = Mesen.CONSOLE_SNES;
    public long databaseId = 0;
    public int dipSwitchCount = 0;
    public int[] cpuTypes = new int[0];
}
