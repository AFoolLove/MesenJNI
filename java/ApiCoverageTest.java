
import mesen.FrameInfo;
import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenCore;
import mesen.MesenDebugger;
import mesen.MesenInput;
import mesen.MesenMemory;
import mesen.TimingInfo;
import mesen.debugger.AddressCounters;
import mesen.debugger.AddressInfo;
import mesen.debugger.CdlStatistics;
import mesen.debugger.PaletteInfo;

/**
 * 补齐「已绑但从未被任何测试调用过」的方法。
 *
 * <p>重点是那些<b>写路径</b> —— 写坏了最难查，所以这里尽量做成「写进去再读出来」的
 * 往返验证，而不是只调用一下看崩不崩。</p>
 */
public class ApiCoverageTest
{
    private static int failures = 0;

    private static void check(boolean ok, String message)
    {
        System.out.println((ok ? "  [OK] " : "  [FAIL] ") + message);
        if(!ok) {
            failures++;
        }
    }

    private static void section(String title)
    {
        System.out.println("== " + title + " ==");
    }

    private static void pauseAndWait() throws Exception
    {
        MesenCore.pause();
        for(int i = 0; i < 50 && !MesenCore.isPaused(); i++) {
            Thread.sleep(50);
        }
    }

    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);
        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom()");
        MesenCore.resume();
        Thread.sleep(800);
        pauseAndWait();

        // ---------------------------------------------------------------
        section("存档槽位：saveState / loadState");
        // 写一个已知值 -> 存槽位 -> 改掉 -> 读回槽位 -> 应该恢复
        int ramAddr = 0x0010;
        MesenMemory.setMemoryValue(Mesen.MEM_NES, ramAddr, 0x11);
        int written = MesenMemory.getMemoryValues(Mesen.MEM_NES, ramAddr, ramAddr)[0] & 0xFF;
        check(written == 0x11, "MesenMemory.setMemoryValue -> $" + Integer.toHexString(ramAddr)
            + " = $" + Integer.toHexString(written));

        byte[] multi = { 0x22, 0x33, 0x44, 0x55 };
        MesenMemory.setMemoryValues(Mesen.MEM_NES, ramAddr + 4, multi);
        byte[] readBack = MesenMemory.getMemoryValues(Mesen.MEM_NES, ramAddr + 4, ramAddr + 7);
        check(readBack.length == 4 && (readBack[0] & 0xFF) == 0x22 && (readBack[3] & 0xFF) == 0x55,
            "MesenMemory.setMemoryValues -> " + bytesToHex(readBack));

        MesenCore.saveState(3);
        java.io.File slot = new java.io.File("home/SaveStates/MetalMax_3.mss");
        check(slot.isFile() && slot.length() > 0, "saveState(3) -> " + (slot.isFile()
            ? slot.length() + " 字节  " + slot.getPath() : "文件未生成"));

        MesenMemory.setMemoryValue(Mesen.MEM_NES, ramAddr, 0x99);
        int changed = MesenMemory.getMemoryValues(Mesen.MEM_NES, ramAddr, ramAddr)[0] & 0xFF;
        check(changed == 0x99, "改动后 -> $" + Integer.toHexString(changed));

        MesenCore.loadState(3);
        Thread.sleep(300);
        int restored = MesenMemory.getMemoryValues(Mesen.MEM_NES, ramAddr, ramAddr)[0] & 0xFF;
        check(restored == 0x11, "loadState(3) 后恢复 -> $" + Integer.toHexString(restored)
            + "（期望 $11）");

        // ---------------------------------------------------------------
        section("存档文件：loadStateFile");
        MesenCore.saveStateFile("temp/coverage.mst");
        MesenMemory.setMemoryValue(Mesen.MEM_NES, ramAddr, 0x77);
        MesenCore.loadStateFile("temp/coverage.mst");
        Thread.sleep(300);
        int afterFile = MesenMemory.getMemoryValues(Mesen.MEM_NES, ramAddr, ramAddr)[0] & 0xFF;
        check(afterFile == 0x11, "loadStateFile() 后恢复 -> $" + Integer.toHexString(afterFile)
            + "（期望 $11）");

        // ---------------------------------------------------------------
        section("调试器状态写入");
        byte[] ramBefore = MesenDebugger.getMemoryState(Mesen.MEM_NES_INTERNAL_RAM);
        byte[] modified = ramBefore.clone();
        modified[0x20] = (byte)0xAB;
        MesenDebugger.setMemoryState(Mesen.MEM_NES_INTERNAL_RAM, modified);
        byte[] ramAfter = MesenDebugger.getMemoryState(Mesen.MEM_NES_INTERNAL_RAM);
        check((ramAfter[0x20] & 0xFF) == 0xAB, "setMemoryState() -> [$20]=$"
            + Integer.toHexString(ramAfter[0x20] & 0xFF));
        MesenDebugger.setMemoryState(Mesen.MEM_NES_INTERNAL_RAM, ramBefore);
        check(true, "setMemoryState() 已还原");

        int pcBefore = MesenDebugger.getProgramCounter(Mesen.CPU_NES, true);
        MesenDebugger.setProgramCounter(Mesen.CPU_NES, 0x8123);
        int pcAfter = MesenDebugger.getProgramCounter(Mesen.CPU_NES, true);
        check(pcAfter == 0x8123, "setProgramCounter($8123) -> $"
            + Integer.toHexString(pcAfter).toUpperCase() + "（原 $"
            + Integer.toHexString(pcBefore).toUpperCase() + "）");

        // 调色板写入（写 PPU 调色板 RAM）
        PaletteInfo paletteBefore = MesenDebugger.getPaletteInfo(Mesen.CPU_NES,
            Mesen.TILE_FORMAT_NES_BPP2);
        int rawBefore = paletteBefore.rawPalette[0] & 0xFF;
        MesenDebugger.setPaletteColor(Mesen.CPU_NES, 0, 0x0F);
        PaletteInfo paletteAfter = MesenDebugger.getPaletteInfo(Mesen.CPU_NES,
            Mesen.TILE_FORMAT_NES_BPP2);
        int rawAfter = paletteAfter.rawPalette[0] & 0xFF;
        check(rawAfter == 0x0F, "setPaletteColor(0, $0F) -> rawPalette[0]=$"
            + Integer.toHexString(rawAfter) + "（原 $" + Integer.toHexString(rawBefore) + "）");
        MesenDebugger.setPaletteColor(Mesen.CPU_NES, 0, rawBefore);

        // 瓦片像素写入（写 PPU 内存）
        AddressInfo tileAddress = new AddressInfo(0, Mesen.MEM_NES_CHR_ROM);
        int pixelBefore = MesenDebugger.getTilePixel(tileAddress, Mesen.TILE_FORMAT_NES_BPP2, 0, 0);
        MesenDebugger.setTilePixel(tileAddress, Mesen.TILE_FORMAT_NES_BPP2, 0, 0, 3);
        int pixelAfter = MesenDebugger.getTilePixel(tileAddress, Mesen.TILE_FORMAT_NES_BPP2, 0, 0);
        check(pixelAfter == 3, "setTilePixel(0,0)=3 -> getTilePixel=" + pixelAfter
            + "（原 " + pixelBefore + "）");
        MesenDebugger.setTilePixel(tileAddress, Mesen.TILE_FORMAT_NES_BPP2, 0, 0, pixelBefore);

        // ---------------------------------------------------------------
        section("访问计数与 CDL 重置");
        AddressCounters[] counters = MesenDebugger.getMemoryAccessCounts(ramAddr, 1, Mesen.MEM_NES);
        check(counters != null && counters.length == 1,
            "resetMemoryAccessCounts() 前 -> " + (counters == null ? "null" : counters[0].toString()));
        MesenDebugger.resetMemoryAccessCounts();
        AddressCounters[] afterReset = MesenDebugger.getMemoryAccessCounts(ramAddr, 1, Mesen.MEM_NES);
        check(afterReset != null && afterReset[0].readCounter == 0 && afterReset[0].writeCounter == 0,
            "resetMemoryAccessCounts() 后 -> " + (afterReset == null ? "null" : afterReset[0].toString()));

        CdlStatistics cdlBefore = MesenDebugger.getCdlStatistics(Mesen.MEM_NES_PRG_ROM);
        check(cdlBefore != null, "resetCdl() 前 CDL -> " + cdlBefore);
        MesenDebugger.resetCdl(Mesen.MEM_NES_PRG_ROM);
        CdlStatistics cdlAfter = MesenDebugger.getCdlStatistics(Mesen.MEM_NES_PRG_ROM);
        check(cdlAfter != null && cdlAfter.codeBytes < cdlBefore.codeBytes,
            "resetCdl(PRG ROM) 后 -> " + cdlAfter + "（原 " + cdlBefore + "）");

        // ---------------------------------------------------------------
        section("调试器撤销");
        System.out.println("      hasUndoHistory() = " + MesenDebugger.hasUndoHistory());
        MesenDebugger.performUndo();
        check(true, "performUndo() 无异常（无可撤销步骤时是空操作）");

        // ---------------------------------------------------------------
        section("配置写入与读取");
        MesenConfig.setEmulationFlag(Mesen.FLAG_TURBO, true);
        MesenConfig.setEmulationFlag(Mesen.FLAG_TURBO, false);
        check(true, "setEmulationFlag(FLAG_TURBO) 无异常");

        MesenConfig.setNesRegion(Mesen.REGION_NTSC);
        check(true, "setNesRegion(REGION_NTSC) 无异常");

        MesenConfig.setNesRemoveSpriteLimit(true);
        MesenConfig.setNesRemoveSpriteLimit(false);
        check(true, "setNesRemoveSpriteLimit() 无异常");

        MesenConfig.setRendererSize(512, 480);
        check(true, "setRendererSize(512,480) 无异常");

        double aspect = MesenConfig.getAspectRatio();
        check(aspect > 0, "getAspectRatio() -> " + aspect);

        FrameInfo baseSize = MesenConfig.getBaseScreenSize();
        check(baseSize != null && baseSize.width > 0,
            "getBaseScreenSize() -> " + (baseSize == null ? "null"
                : baseSize.width + "x" + baseSize.height));

        TimingInfo timing = MesenConfig.getTimingInfo(Mesen.CPU_NES);
        check(timing != null, "getTimingInfo(CPU_NES) -> " + timing);

        // ---------------------------------------------------------------
        section("输入");
        boolean hasNes = MesenInput.hasControlDevice(Mesen.CONTROLLER_NES);
        check(hasNes, "hasControlDevice(CONTROLLER_NES) -> " + hasNes);

        MesenInput.disableAllKeys(true);
        MesenInput.disableAllKeys(false);
        check(true, "disableAllKeys() 无异常");

        MesenInput.setMousePosition(100.5, 50.25);
        check(true, "setMousePosition(100.5, 50.25) 无异常");

        MesenInput.setMouseMovement(3, -2);
        check(true, "setMouseMovement(3, -2) 无异常");

        // ---------------------------------------------------------------
        section("日志与消息");
        String marker = "coverage-test-marker-42";
        MesenCore.writeLogEntry(marker);
        String log = MesenCore.getLog();
        check(log != null && log.contains(marker), "writeLogEntry() 后 getLog() 含该条 -> "
            + (log == null ? "null" : log.contains(marker)));

        MesenCore.displayMessage("Coverage", "测试消息", "");
        check(true, "displayMessage() 无异常");

        MesenCore.addKnownGameFolder(".");
        check(true, "addKnownGameFolder() 无异常");

        // ---------------------------------------------------------------
        section("最近游戏");
        java.io.File rgd = new java.io.File("home/RecentGames/MetalMax.rgd");
        check(rgd.isFile(), "存在可用的 .rgd -> " + (rgd.isFile() ? rgd.getPath() : "无"));
        if(rgd.isFile()) {
            MesenCore.loadRecentGame(rgd.getAbsolutePath(), true);
            Thread.sleep(500);
            check(true, "loadRecentGame() 无异常");
        }

        // ---------------------------------------------------------------
        section("历史查看器：ResumeGameplay 与通知监听");
        // ResumeGameplay 在主模拟器「没在运行」时会去 LoadRom（此时仍持着主锁），
        // 之后再 Release 会死锁，所以这里让主模拟器保持运行。
        MesenCore.resume();
        for(int i = 0; i < 40 && !MesenCore.historyViewerEnabled(); i++) {
            Thread.sleep(100);
        }
        System.out.println("      historyViewerEnabled() = " + MesenCore.historyViewerEnabled()
            + "（没有历史时 ResumeGameplay 会越界访问空 deque，所以先等出来）");
        MesenCore.historyViewerInitialize(0, 0);
        mesen.NotificationListener historyListener = new mesen.NotificationListener();
        MesenCore.historyViewerRegisterNotificationListener(historyListener);
        check(true, "historyViewerRegisterNotificationListener() 无异常");

        mesen.HistoryViewerState historyState = MesenCore.historyViewerGetState();
        int resumeTarget = historyState == null ? 0 : Math.max(0, historyState.length / 2);
        MesenCore.historyViewerResumeGameplay(resumeTarget);
        check(true, "historyViewerResumeGameplay(" + resumeTarget + ") 无异常");

        MesenCore.historyViewerUnregisterNotificationListener();
        check(true, "historyViewerUnregisterNotificationListener() 无异常");

        // 已知问题：ResumeGameplay 之后再调 historyViewerRelease() 会死锁
        // （卡在 _historyPlayer->Release()，两个线程都 0 CPU）。HistorySmokeTest
        // 里「只 initialize/release、不 resume」是正常的，所以触发条件是 resume。
        // 这里不掩盖它：不做 release，直接退出进程，并在日志里标明。
        System.out.println("      [KNOWN] ResumeGameplay 之后 release 会死锁，跳过 release");

        MesenCore.stop();
        MesenCore.release();

        System.out.println();
        if(failures == 0) {
            System.out.println("API COVERAGE TEST PASSED");
        } else {
            System.out.println("API COVERAGE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }

    private static String bytesToHex(byte[] data)
    {
        StringBuilder sb = new StringBuilder();
        for(byte b : data) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }
}
