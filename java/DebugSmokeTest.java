import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenCore;
import mesen.MesenDebugger;
import mesen.MesenMemory;
import mesen.debugger.AddressCounters;
import mesen.debugger.AddressInfo;
import mesen.debugger.Breakpoint;
import mesen.debugger.CdlStatistics;
import mesen.debugger.CodeLine;
import mesen.debugger.CpuVector;
import mesen.debugger.DebuggerFeatures;
import mesen.debugger.EvalResult;
import mesen.debugger.InstructionProgress;
import mesen.debugger.StackFrame;

/** 调试 API（MesenDebugger）回归测试。 */
public class DebugSmokeTest
{
    private static int failures = 0;

    private static void check(boolean ok, String what)
    {
        System.out.printf("  [%s] %s%n", ok ? "OK" : "FAIL", what);
        if(!ok) {
            failures++;
        }
    }

    private static void waitStopped(long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while(!MesenDebugger.isExecutionStopped() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }

    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);
        MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);

        if(!MesenCore.loadRom("roms/MetalMax.nes", null)) {
            System.out.println("加载 ROM 失败");
            System.exit(1);
        }

        // ---- 初始化 ----
        System.out.println("== 调试器生命周期 ==");
        MesenDebugger.initialize();
        check(MesenDebugger.isDebuggerRunning(), "isDebuggerRunning()");

        MesenCore.resume();
        Thread.sleep(800);
        MesenCore.pause();
        waitStopped(5000);
        check(MesenDebugger.isExecutionStopped(), "pause 后 isExecutionStopped()");

        // ---- CPU 特性 ----
        System.out.println("== CPU 特性 / PC ==");
        DebuggerFeatures features = MesenDebugger.getDebuggerFeatures(Mesen.CPU_NES);
        check(features != null, "getDebuggerFeatures() -> " + features);
        if(features != null) {
            check(features.cpuVectorCount > 0, "cpuVectorCount = " + features.cpuVectorCount);
            for(int i = 0; i < features.cpuVectors.length; i++) {
                CpuVector v = features.cpuVectors[i];
                System.out.printf("      向量[%d] %s @ $%04X type=%d%n", i, v.name, v.address, v.type);
            }
        }

        int pc = MesenDebugger.getProgramCounter(Mesen.CPU_NES, true);
        System.out.printf("      PC = $%04X%n", pc);
        check(pc > 0, "getProgramCounter()");

        // ---- 反汇编 ----
        System.out.println("== 反汇编 ==");
        CodeLine[] lines = MesenDebugger.getDisassemblyOutput(Mesen.CPU_NES, pc, 8);
        check(lines != null && lines.length > 0, "getDisassemblyOutput() -> "
            + (lines == null ? "null" : lines.length + " 行"));
        if(lines != null) {
            for(CodeLine line : lines) {
                System.out.println("      " + line);
            }
            boolean hasText = lines[0].text != null && !lines[0].text.isEmpty();
            check(hasText, "第一行有反汇编文本");
        }

        int rowAddress = MesenDebugger.getDisassemblyRowAddress(Mesen.CPU_NES, pc, 3);
        check(rowAddress != 0, String.format("getDisassemblyRowAddress(+3) = $%04X", rowAddress));

        int found = MesenDebugger.searchDisassembly(Mesen.CPU_NES, "LDA", pc, true, false, false);
        check(found != -1, String.format("searchDisassembly(\"LDA\", 区分大小写) = $%04X", found));

        int foundLower = MesenDebugger.searchDisassembly(Mesen.CPU_NES, "lda", pc, false, false, false);
        check(foundLower != -1, String.format("searchDisassembly(\"lda\", 忽略大小写) = $%04X", foundLower));

        CodeLine[] occurrences = MesenDebugger.findOccurrences(Mesen.CPU_NES, "LDA", true, false, false, 16);
        check(occurrences != null && occurrences.length > 0, "findOccurrences(\"LDA\") -> "
            + (occurrences == null ? "null" : occurrences.length + " 处"));

        // ---- 表达式 ----
        System.out.println("== 表达式 ==");
        EvalResult eval = MesenDebugger.evaluateExpression("$10+5", Mesen.CPU_NES, false);
        check(eval != null && eval.isValid() && eval.value == 21,
            "evaluateExpression(\"$10+5\") -> " + eval);

        EvalResult bad = MesenDebugger.evaluateExpression("!!!", Mesen.CPU_NES, false);
        check(bad != null && !bad.isValid(), "非法表达式返回 invalid -> " + bad);

        String[] tokens = MesenDebugger.getTokenList(Mesen.CPU_NES);
        check(tokens != null && tokens.length > 0, "getTokenList() -> "
            + (tokens == null ? "null" : tokens.length + " 个 token"));

        // ---- 调用栈 / 指令进度 ----
        System.out.println("== 调用栈 / 指令进度 ==");
        StackFrame[] stack = MesenDebugger.getCallstack(Mesen.CPU_NES);
        check(stack != null, "getCallstack() -> " + (stack == null ? "null" : stack.length + " 帧"));

        InstructionProgress progress = MesenDebugger.getInstructionProgress(Mesen.CPU_NES);
        check(progress != null, "getInstructionProgress() -> " + progress);

        // ---- 断点 ----
        System.out.println("== 断点 ==");
        Breakpoint bp = new Breakpoint();
        bp.id = 1;
        bp.cpuType = Mesen.CPU_NES;
        bp.memoryType = Mesen.MEM_NES;
        bp.type = Mesen.BREAK_EXECUTE;
        bp.startAddress = pc;
        bp.endAddress = pc;
        MesenDebugger.setBreakpoints(new Breakpoint[] { bp });

        MesenDebugger.resumeExecution();
        waitStopped(8000);
        boolean hit = MesenDebugger.isExecutionStopped();
        check(hit, "断点命中后 isExecutionStopped()");
        if(hit) {
            int stoppedPc = MesenDebugger.getProgramCounter(Mesen.CPU_NES, true);
            System.out.printf("      停在 $%04X（断点 $%04X）%n", stoppedPc, pc);
        }

        String log = MesenDebugger.getDebuggerLog();
        check(log != null, "getDebuggerLog() -> " + (log == null ? "null" : log.length() + " 字符"));
        if(log != null && !log.isEmpty()) {
            System.out.println("      日志尾部: " + log.substring(Math.max(0, log.length() - 200)).trim());
        }

        // ---- 单步 ----
        System.out.println("== 单步 ==");
        int beforeStep = MesenDebugger.getProgramCounter(Mesen.CPU_NES, true);
        MesenDebugger.step(Mesen.CPU_NES, 1, Mesen.STEP_STEP);

        // step() 是异步的：轮询 PC 直到它变化（或超时）
        int afterStep = beforeStep;
        long stepDeadline = System.currentTimeMillis() + 5000;
        while(afterStep == beforeStep && System.currentTimeMillis() < stepDeadline) {
            Thread.sleep(10);
            afterStep = MesenDebugger.getProgramCounter(Mesen.CPU_NES, true);
        }
        check(afterStep != beforeStep, String.format("单步 $%04X -> $%04X", beforeStep, afterStep));

        // ---- 内存快照 ----
        System.out.println("== 内存快照 ==");
        long memorySize = MesenMemory.getMemorySize(Mesen.MEM_NES);
        byte[] state = MesenDebugger.getMemoryState(Mesen.MEM_NES);
        check(state != null && state.length == memorySize,
            "getMemoryState() -> " + (state == null ? "null" : state.length + " 字节"));

        // ---- CDL / 访问计数 ----
        // CDL 挂在 PRG ROM 上（不是 CPU RAM）
        System.out.println("== CDL / 访问计数 ==");
        CdlStatistics cdl = MesenDebugger.getCdlStatistics(Mesen.MEM_NES_PRG_ROM);
        check(cdl != null, "getCdlStatistics(PRG ROM) -> " + cdl);
        if(cdl != null) {
            check(cdl.totalBytes > 0, "CDL 已记录 " + cdl.codeBytes + " 字节代码");
        }

        int[] functions = MesenDebugger.getCdlFunctions(Mesen.MEM_NES_PRG_ROM, 256);
        check(functions != null, "getCdlFunctions() -> "
            + (functions == null ? "null" : functions.length + " 个"));

        AddressCounters[] counters = MesenDebugger.getMemoryAccessCounts(0, 16, Mesen.MEM_NES);
        check(counters != null && counters.length == 16, "getMemoryAccessCounts(0,16) -> "
            + (counters == null ? "null" : counters.length + " 项"));
        if(counters != null && counters.length > 0) {
            System.out.println("      $00: " + counters[0]);
        }

        // ---- 冻结地址 ----
        System.out.println("== 冻结地址 ==");
        MesenDebugger.updateFrozenAddresses(Mesen.CPU_NES, 0x10, 0x1F, true);
        boolean[] frozen = MesenDebugger.getFrozenState(Mesen.CPU_NES, 0x10, 0x1F);
        check(frozen != null && frozen.length == 16, "getFrozenState() -> "
            + (frozen == null ? "null" : frozen.length + " 项"));
        if(frozen != null && frozen.length > 0) {
            check(frozen[0], "冻结标志已置位");
        }
        MesenDebugger.updateFrozenAddresses(Mesen.CPU_NES, 0x10, 0x1F, false);

        // ---- 地址换算 / 标签 / 撤销 ----
        System.out.println("== 地址换算 / 标签 / 撤销 ==");
        AddressInfo abs = MesenDebugger.getAbsoluteAddress(new AddressInfo(0x10, Mesen.MEM_NES));
        check(abs != null, "getAbsoluteAddress() -> " + abs);
        if(abs != null) {
            AddressInfo rel = MesenDebugger.getRelativeAddress(abs, Mesen.CPU_NES);
            check(rel != null, "getRelativeAddress() -> " + rel);
        }

        MesenDebugger.setLabel(0x10, Mesen.MEM_NES, "debugTestLabel", "smoke test");
        MesenDebugger.clearLabels();
        check(true, "setLabel()/clearLabels() 无异常");

        System.out.println("      hasUndoHistory() = " + MesenDebugger.hasUndoHistory());

        // ---- 收尾 ----
        System.out.println("== 收尾 ==");
        MesenDebugger.setBreakpoints(new Breakpoint[0]);
        MesenDebugger.release();
        check(!MesenDebugger.isDebuggerRunning(), "release 后 isDebuggerRunning() == false");

        MesenCore.stop();
        MesenCore.release();

        System.out.println(failures == 0
            ? "DEBUG SMOKE TEST PASSED"
            : "DEBUG SMOKE TEST FAILED (" + failures + " 项)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
