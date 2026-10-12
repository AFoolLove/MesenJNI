import java.awt.Frame;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenDebugger;
import mesen.MesenCore;
import mesen.MesenInput;
import mesen.MesenMemory;
import mesen.NotificationListener;

/**
 * 排查历史查看器「ResumeGameplay 之后 Release 死锁」的触发条件。
 *
 * <p>参数 1：{@code headless} 或 {@code native}（传真实 HWND，上游 .NET 的配置）。</p>
 * <p>参数 2：逗号分隔的额外步骤，用来 bisect —— 哪个步骤加进去才开始死锁，
 * 它就是触发条件。{@code all} 表示全加（等价于 ApiCoverageTest 的那一串）。</p>
 */
public class HistoryLockProbe
{
    private static final Set<String> ALL = new HashSet<>(Arrays.asList(
        "pause", "save", "memwrite", "ppuwrite", "cdl", "undo",
        "config", "input", "log", "recent", "listener", "state"));

    private static void step(String message)
    {
        System.out.println("  [step] " + message);
    }

    /** 从 AWT 组件拿真实 HWND（JDK 内部 API，需要 --add-opens）。 */
    private static long getHwnd(Frame frame) throws Exception
    {
        frame.setSize(320, 240);
        frame.setVisible(true);
        frame.addNotify();

        Method getPeer = java.awt.Component.class.getMethod("getPeer");
        Object peer = getPeer.invoke(frame);
        if(peer == null) {
            return 0;
        }
        Method getHWnd = peer.getClass().getMethod("getHWnd");
        Object handle = getHWnd.invoke(peer);
        return handle instanceof Number ? ((Number)handle).longValue() : 0;
    }

    public static void main(String[] args) throws Exception
    {
        String mode = args.length > 0 ? args[0] : "headless";
        boolean native_ = mode.equals("native");

        Set<String> extras = new HashSet<>();
        if(args.length > 1 && !args[1].isEmpty()) {
            if(args[1].equals("all")) {
                extras.addAll(ALL);
            } else {
                extras.addAll(Arrays.asList(args[1].split(",")));
            }
        }

        long hwnd = 0;
        Frame frame = null;
        if(native_) {
            frame = new Frame("HistoryLockProbe");
            hwnd = getHwnd(frame);
            System.out.println("mode=native  hwnd=0x" + Long.toHexString(hwnd));
            if(hwnd == 0) {
                System.out.println("拿不到 HWND，无法做 native 对比，退出");
                System.exit(2);
            }
        } else {
            System.out.println("mode=headless（句柄 0）");
        }
        System.out.println("extras=" + extras);

        MesenCore.initDll();
        step("initDll");

        if(native_) {
            MesenCore.initializeEmuWithHandles("./home", hwnd, hwnd, false, false, false, true);
        } else {
            MesenCore.initializeEmu("./home", true, true, false, true);
        }
        step("initializeEmu");

        MesenCore.loadRom("roms/MetalMax.nes", null);
        step("loadRom");

        MesenCore.resume();
        Thread.sleep(800);
        step("resume + 800ms");

        // ---- 额外步骤（bisect 用）----
        if(extras.contains("pause")) {
            MesenCore.pause();
            Thread.sleep(300);
            step("pause");
        }
        if(extras.contains("save")) {
            MesenCore.saveState(3);
            MesenCore.loadState(3);
            MesenCore.saveStateFile("temp/probe.mst");
            MesenCore.loadStateFile("temp/probe.mst");
            step("save/load 槽位与文件");
        }
        if(extras.contains("memwrite")) {
            MesenMemory.setMemoryValue(Mesen.MEM_NES, 0x10, 0x11);
            MesenMemory.setMemoryValues(Mesen.MEM_NES, 0x14, new byte[] { 1, 2, 3, 4 });
            byte[] ram = MesenDebugger.getMemoryState(Mesen.MEM_NES_INTERNAL_RAM);
            MesenDebugger.setMemoryState(Mesen.MEM_NES_INTERNAL_RAM, ram);
            MesenDebugger.setProgramCounter(Mesen.CPU_NES, 0x8123);
            step("内存写 + setProgramCounter");
        }
        if(extras.contains("ppuwrite")) {
            MesenDebugger.setPaletteColor(Mesen.CPU_NES, 0, 0x0F);
            MesenDebugger.getPaletteInfo(Mesen.CPU_NES, Mesen.TILE_FORMAT_NES_BPP2);
            step("PPU 工具写入");
        }
        if(extras.contains("cdl")) {
            MesenDebugger.resetMemoryAccessCounts();
            MesenDebugger.resetCdl(Mesen.MEM_NES_PRG_ROM);
            step("resetCdl + resetMemoryAccessCounts");
        }
        if(extras.contains("undo")) {
            MesenDebugger.performUndo();
            step("performUndo");
        }
        if(extras.contains("config")) {
            MesenConfig.setEmulationFlag(Mesen.FLAG_TURBO, false);
            MesenConfig.setNesRegion(Mesen.REGION_NTSC);
            MesenConfig.setNesRemoveSpriteLimit(false);
            MesenConfig.setRendererSize(512, 480);
            step("配置写入（含 setRendererSize）");
        }
        if(extras.contains("input")) {
            MesenInput.disableAllKeys(true);
            MesenInput.disableAllKeys(false);
            MesenInput.setMousePosition(100.5, 50.25);
            MesenInput.setMouseMovement(3, -2);
            step("输入");
        }
        if(extras.contains("log")) {
            MesenCore.writeLogEntry("probe");
            MesenCore.getLog();
            MesenCore.displayMessage("Probe", "x", "");
            MesenCore.addKnownGameFolder(".");
            step("日志与消息");
        }
        if(extras.contains("recent")) {
            MesenCore.loadRecentGame(
                new java.io.File("home/RecentGames/MetalMax.rgd").getAbsolutePath(), true);
            Thread.sleep(500);
            step("loadRecentGame（重载了主模拟器）");
        }

        // ---- 让历史攒起来 ----
        MesenCore.resume();
        for(int i = 0; i < 60 && !MesenCore.historyViewerEnabled(); i++) {
            Thread.sleep(100);
        }
        step("historyViewerEnabled = " + MesenCore.historyViewerEnabled());

        MesenCore.historyViewerInitialize(hwnd, hwnd);
        step("historyViewerInitialize");

        NotificationListener listener = null;
        if(extras.contains("listener")) {
            listener = new NotificationListener();
            MesenCore.historyViewerRegisterNotificationListener(listener);
            step("historyViewerRegisterNotificationListener");
        }

        int position = 30;
        if(extras.contains("state")) {
            mesen.HistoryViewerState state = MesenCore.historyViewerGetState();
            position = state == null ? 30 : Math.max(0, state.length / 2);
            step("historyViewerGetState -> length=" + (state == null ? "null" : state.length));
        }
        MesenCore.historyViewerResumeGameplay(position);
        step("historyViewerResumeGameplay(" + position + ")");

        if(listener != null) {
            MesenCore.historyViewerUnregisterNotificationListener();
            step("historyViewerUnregisterNotificationListener");
        }

        MesenCore.historyViewerRelease();
        step("historyViewerRelease  ← 卡在这里就是复现");

        MesenCore.stop();
        MesenCore.release();
        step("全部完成");

        if(frame != null) {
            frame.dispose();
        }
        System.out.println("RESULT mode=" + mode + " extras=" + extras + " NO_DEADLOCK");
        System.exit(0);
    }
}
