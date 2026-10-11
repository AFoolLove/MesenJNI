package mesen.debugger;

/**
 * 调试器配置，对应 Core 的 {@code DebugConfig}。
 *
 * <p>字段名与 core 结构体一致。core 没有「读回配置」的接口，所以这里给的全是默认值；
 * 用 {@link mesen.MesenDebugger#setDebugConfig(DebugConfig)} 整份提交（未改的字段按默认值写入）。</p>
 */
public class DebugConfig
{
    public boolean BreakOnUninitRead;
    public boolean ShowJumpLabels;
    public boolean DrawPartialFrame;
    public boolean ShowVerifiedData;
    public boolean DisassembleVerifiedData;
    public boolean ShowUnidentifiedData;
    public boolean DisassembleUnidentifiedData;
    public boolean UseLowerCaseDisassembly;
    public boolean ShowMemoryValues;
    public boolean AutoResetCdl;
    public boolean UsePredictiveBreakpoints;
    public boolean SingleBreakpointPerInstruction;
    public boolean SnesBreakOnBrk;
    public boolean SnesBreakOnCop;
    public boolean SnesBreakOnWdm;
    public boolean SnesBreakOnStp;
    public boolean SnesBreakOnInvalidPpuAccess;
    public boolean SnesBreakOnReadDuringAutoJoy;
    public boolean SnesUseAltSpcOpNames;
    public boolean SnesIgnoreDspReadWrites;
    public boolean SpcBreakOnBrk;
    public boolean SpcBreakOnStpSleep;
    public boolean GbBreakOnInvalidOamAccess;
    public boolean GbBreakOnInvalidVramAccess;
    public boolean GbBreakOnDisableLcdOutsideVblank;
    public boolean GbBreakOnInvalidOpCode;
    public boolean GbBreakOnNopLoad;
    public boolean GbBreakOnOamCorruption;
    public boolean NesBreakOnBrk;
    public boolean NesBreakOnUnofficialOpCode;
    public boolean NesBreakOnUnstableOpCode;
    public boolean NesBreakOnCpuCrash;
    public boolean NesBreakOnBusConflict;
    public boolean NesBreakOnDecayedOamRead;
    public boolean NesBreakOnPpuScrollGlitch;
    public boolean NesBreakOnExtOutputMode;
    public boolean NesBreakOnInvalidVramAccess;
    public boolean NesBreakOnInvalidOamWrite;
    public boolean NesBreakOnDmaInputRead;
    public boolean PceBreakOnBrk;
    public boolean PceBreakOnUnofficialOpCode;
    public boolean PceBreakOnInvalidVramAddress;
    public boolean SmsBreakOnNopLoad;
    public boolean GbaBreakOnNopLoad;
    public boolean GbaBreakOnInvalidOpCode;
    public boolean GbaBreakOnUnalignedMemAccess;
    public int GbaDisMode;
    public boolean WsBreakOnInvalidOpCode;
    public boolean ScriptAllowIoOsAccess;
    public boolean ScriptAllowNetworkAccess;
    /** core 默认 1，其余字段默认 false/0。 */
    public int ScriptTimeout = 1;

    @Override
    public String toString()
    {
        return String.format("DebugConfig[BreakOnUninitRead=%b, AutoResetCdl=%b, ScriptTimeout=%d]",
            BreakOnUninitRead, AutoResetCdl, ScriptTimeout);
    }
}
