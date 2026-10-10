package mesen;

import mesen.debugger.AddressCounters;
import mesen.debugger.AddressInfo;
import mesen.debugger.Breakpoint;
import mesen.debugger.CdlStatistics;
import mesen.debugger.CodeLine;
import mesen.debugger.DebuggerFeatures;
import mesen.debugger.EvalResult;
import mesen.debugger.InstructionProgress;
import mesen.debugger.StackFrame;

/**
 * 调试器 API（Core/Debugger 的核心子集）。
 *
 * <p>典型流程：</p>
 * <pre>
 * MesenCore.initDll();
 * MesenCore.initializeEmu(home, true, true, false, true);
 * MesenCore.loadRom(rom, null);
 *
 * MesenDebugger.initialize();
 * MesenCore.resume();
 * MesenDebugger.pause();                     // 用 MesenCore.pause() 让模拟停下来
 * while(!MesenDebugger.isExecutionStopped()) Thread.sleep(10);
 *
 * CodeLine[] lines = MesenDebugger.getDisassemblyOutput(Mesen.CPU_NES, pc, 20);
 * MesenDebugger.setBreakpoints(new Breakpoint[] { bp });
 * MesenDebugger.step(Mesen.CPU_NES, 1, Mesen.STEP_STEP);
 * </pre>
 *
 * <p><b>注意</b>：这些接口都要求先 {@link #initialize()} 且已加载 ROM；
 * 调试器内部会加锁暂停模拟线程，所以不要在回调线程里调用。</p>
 */
public final class MesenDebugger
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenDebugger()
    {
    }

    // ---- 调试器生命周期 --------------------------------------------------

    /** 初始化调试器（进入调试模式）。 */
    public static native void initialize();

    /** 释放调试器（退出调试模式）。 */
    public static native void release();

    /** 调试器是否在运行。 */
    public static native boolean isDebuggerRunning();

    /** 模拟是否已停在指令边界（断点/单步之后为 true）。 */
    public static native boolean isExecutionStopped();

    /** 从暂停状态继续执行。 */
    public static native void resumeExecution();

    /**
     * 单步。
     *
     * @param cpuType  CPU 类型（{@code Mesen.CPU_*}）
     * @param count    步数
     * @param stepType 步进类型（{@code Mesen.STEP_*}）
     */
    public static native void step(int cpuType, int count, int stepType);

    // ---- 反汇编 ----------------------------------------------------------

    /**
     * 从某个地址开始取一段反汇编。
     *
     * @param cpuType   CPU 类型
     * @param address   起始地址（CPU 地址空间）
     * @param rowCount  想要的行数
     * @return 实际取到的行
     */
    public static native CodeLine[] getDisassemblyOutput(int cpuType, int address, int rowCount);

    /** 取某个地址上下偏移若干行后的地址。 */
    public static native int getDisassemblyRowAddress(int cpuType, int address, int rowOffset);

    /**
     * 在反汇编里搜索文本，返回匹配处的地址（找不到返回 -1）。
     *
     * <p><b>坑</b>：{@code matchCase == false} 时，核是拿 {@code tolower(反汇编文本)} 和
     * <b>原样</b>的关键词比较的，所以关键词必须自己先转小写（.NET UI 也是这么做的）。
     * 想搜 "LDA" 要么传 {@code matchCase=true}，要么传 {@code "lda"}。</p>
     */
    public static native int searchDisassembly(int cpuType, String text, int startAddress,
        boolean matchCase, boolean matchWholeWord, boolean searchBackwards);

    /**
     * 找出所有出现的匹配行（最多 {@code maxResults} 条）。
     *
     * <p>同 {@link #searchDisassembly}：忽略大小写时关键词要自己先转小写。</p>
     */
    public static native CodeLine[] findOccurrences(int cpuType, String text,
        boolean matchCase, boolean matchWholeWord, boolean searchBackwards, int maxResults);

    // ---- 断点 ------------------------------------------------------------

    /**
     * 覆盖式设置断点：传入的就是当前全部断点。
     *
     * @param breakpoints 断点列表（null 或空数组 = 清空所有断点）
     */
    public static native void setBreakpoints(Breakpoint[] breakpoints);

    // ---- 表达式 ----------------------------------------------------------

    /**
     * 求值一个调试表达式（如 {@code "$10+5"}、{@code "[$2000]"}）。
     *
     * @param useCache 是否使用内部缓存
     */
    public static native EvalResult evaluateExpression(String expression, int cpuType, boolean useCache);

    /** 取表达式可用 token 列表（自动补全用）。 */
    public static native String[] getTokenList(int cpuType);

    // ---- 调用栈 / PC / 特性 ----------------------------------------------

    /** 取当前调用栈（不支持调用栈的 CPU 返回空数组）。 */
    public static native StackFrame[] getCallstack(int cpuType);

    /**
     * 取程序计数器。
     *
     * @param getInstructionPc true = 当前指令的地址，false = 下一条指令
     */
    public static native int getProgramCounter(int cpuType, boolean getInstructionPc);

    /** 修改程序计数器（先查 {@link #getDebuggerFeatures(int)} 的 changeProgramCounter）。 */
    public static native void setProgramCounter(int cpuType, int address);

    /** 取该 CPU 支持的调试能力。 */
    public static native DebuggerFeatures getDebuggerFeatures(int cpuType);

    /** 取当前指令的执行进度。 */
    public static native InstructionProgress getInstructionProgress(int cpuType);

    /**
     * 取调试器日志。
     *
     * <p>注意：这个日志目前只记录「读取未初始化内存」这类事件，需要先在调试配置里打开
     * {@code BreakOnUninitRead}；平时是空字符串属于正常。</p>
     */
    public static native String getDebuggerLog();

    // ---- 冻结地址 --------------------------------------------------------

    /** 冻结/解冻一段地址（被冻结的地址不会被写入）。 */
    public static native void updateFrozenAddresses(int cpuType, int start, int end, boolean freeze);

    /** 查询一段地址的冻结状态。 */
    public static native boolean[] getFrozenState(int cpuType, int start, int end);

    // ---- 标签 ------------------------------------------------------------

    /** 给某个地址设标签和注释（空字符串 = 清除）。 */
    public static native void setLabel(int address, int memoryType, String label, String comment);

    /** 清除所有标签。 */
    public static native void clearLabels();

    // ---- 撤销 / 地址换算 -------------------------------------------------

    /** 是否有可撤销的改动。 */
    public static native boolean hasUndoHistory();

    /** 撤销上一次内存/状态改动。 */
    public static native void performUndo();

    /** 相对地址（CPU 地址空间）换算成绝对地址。 */
    public static native AddressInfo getAbsoluteAddress(AddressInfo relativeAddress);

    /** 绝对地址换算成某个 CPU 的相对地址。 */
    public static native AddressInfo getRelativeAddress(AddressInfo absoluteAddress, int cpuType);

    // ---- 内存快照 --------------------------------------------------------

    /** 取整块内存（大小见 {@link MesenMemory#getMemorySize(int)}）。 */
    public static native byte[] getMemoryState(int memoryType);

    /** 写入整块内存（只写前 data.length 字节）。 */
    public static native void setMemoryState(int memoryType, byte[] data);

    // ---- CDL / 访问计数 --------------------------------------------------

    /** 取 CDL 统计。 */
    public static native CdlStatistics getCdlStatistics(int memoryType);

    /** 取 CDL 标记出的函数入口列表。 */
    public static native int[] getCdlFunctions(int memoryType, int maxCount);

    /** 重置某个内存类型的 CDL 数据。 */
    public static native void resetCdl(int memoryType);

    /** 取一段地址的访问计数。 */
    public static native AddressCounters[] getMemoryAccessCounts(int offset, int length, int memoryType);

    /** 重置所有访问计数。 */
    public static native void resetMemoryAccessCounts();
}
