import mesen.Mesen;
import mesen.MesenCore;
import mesen.MesenDebugger;
import mesen.MesenVideo;
import mesen.RomInfo;
import mesen.debugger.CodeLine;
import mesen.debugger.DebuggerFeatures;
import mesen.debugger.PaletteInfo;
import mesen.debugger.SpriteOptions;
import mesen.debugger.SpritePreviewInfo;
import mesen.debugger.TileView;
import mesen.debugger.TileViewOptions;
import mesen.debugger.TilemapOptions;
import mesen.debugger.TilemapResult;

/**
 * 多机种端到端冒烟：对每个机种跑同一套通用接口。
 *
 * <p>这些路径（PPU 工具泛化、整机状态字节、通用调试接口）不依赖具体机种，
 * 所以用一张表驱动——以后拿到新机种的 ROM，加一行就行。</p>
 *
 * <p>ROM 是 gitignore 的，缺哪个机种只跳过、不算失败。</p>
 */
public class ConsoleSmokeTest
{
    private static int failures = 0;
    private static int checked = 0;

    /** 一个机种的验证参数。 */
    private static class ConsoleCase
    {
        String name;
        int cpuType;
        int consoleType;
        String romPath;
        String missingHint;
        int tileMemoryType;
        int tileFormat;

        ConsoleCase(String name, int cpuType, int consoleType, String romPath, String hint,
            int tileMemoryType, int tileFormat)
        {
            this.name = name;
            this.cpuType = cpuType;
            this.consoleType = consoleType;
            this.romPath = romPath;
            this.missingHint = hint;
            this.tileMemoryType = tileMemoryType;
            this.tileFormat = tileFormat;
        }
    }

    private static final ConsoleCase[] CASES = {
        new ConsoleCase("NES", Mesen.CPU_NES, Mesen.CONSOLE_NES, "roms/MetalMax.nes",
            "把任意 .nes 放进去", Mesen.MEM_NES_CHR_ROM, Mesen.TILE_FORMAT_NES_BPP2),
        new ConsoleCase("SNES", Mesen.CPU_SNES, Mesen.CONSOLE_SNES, "roms/MetalMaxR.smc",
            "把任意 .sfc/.smc 放进去", Mesen.MEM_SNES_VIDEO_RAM, Mesen.TILE_FORMAT_BPP4),
        new ConsoleCase("GBA", Mesen.CPU_GBA, Mesen.CONSOLE_GBA, "roms/MetalMax2Kai (J) (V1.1).gba",
            "把任意 .gba 放进去，并把 gba_bios.bin 放到 home/Firmware/",
            Mesen.MEM_GBA_VIDEO_RAM, Mesen.TILE_FORMAT_BPP4),
        // 下面这些还没有 ROM；拿到后在 roms/ 放好即可自动开始验证。
        new ConsoleCase("GameBoy", Mesen.CPU_GAMEBOY, Mesen.CONSOLE_GAMEBOY, "roms/game.gb",
            "把任意 .gb/.gbc 放进去", 0, Mesen.TILE_FORMAT_BPP2),
        new ConsoleCase("SMS", Mesen.CPU_SMS, Mesen.CONSOLE_SMS, "roms/game.sms",
            "把任意 .sms/.gg 放进去（Mesen 上它没有 sprite RAM，走 nullptr 分支）",
            Mesen.MEM_SMS_VIDEO_RAM, Mesen.TILE_FORMAT_BPP4),
        new ConsoleCase("PCE", Mesen.CPU_PCE, Mesen.CONSOLE_PC_ENGINE, "roms/game.pce",
            "把任意 .pce 放进去", 0, Mesen.TILE_FORMAT_PCE_BACKGROUND_BPP2_CG0),
        new ConsoleCase("WonderSwan", Mesen.CPU_WS, Mesen.CONSOLE_WS, "roms/game.ws",
            "把任意 .ws/.wsc 放进去（它也没有 sprite RAM）", Mesen.MEM_WS_WORK_RAM, Mesen.TILE_FORMAT_BPP4),
    };

    private static void check(boolean ok, String message)
    {
        System.out.println((ok ? "    [OK] " : "    [FAIL] ") + message);
        if(!ok) {
            failures++;
        }
    }

    private static int renderNonBlack()
    {
        int[] frame = new int[MesenVideo.getFrameWidth() * MesenVideo.getFrameHeight()];
        MesenVideo.copyFrameBuffer(frame);
        int nonBlack = 0;
        for(int pixel : frame) {
            if((pixel & 0xFFFFFF) != 0) {
                nonBlack++;
            }
        }
        return nonBlack;
    }

    private static int renderNonBlackWaiting() throws Exception
    {
        int nonBlack = 0;
        for(int i = 0; i < 80 && nonBlack == 0; i++) {
            Thread.sleep(100);
            nonBlack = renderNonBlack();
        }
        return nonBlack;
    }

    private static void runCase(ConsoleCase testCase) throws Exception
    {
        java.io.File rom = new java.io.File(testCase.romPath);
        if(!rom.isFile()) {
            System.out.println("== " + testCase.name + " == 跳过（无 ROM：" + testCase.romPath
                + "；" + testCase.missingHint + "）");
            return;
        }

        System.out.println("== " + testCase.name + " ==  (" + testCase.romPath
            + ", " + rom.length() + " 字节)");
        checked++;

        check(MesenCore.loadRom(testCase.romPath, null), "loadRom 成功");

        RomInfo info = MesenCore.getRomInfo();
        if(info != null) {
            check(info.consoleType == testCase.consoleType,
                "识别为 " + testCase.name + " -> consoleType=" + info.consoleType
                    + " (期望 " + testCase.consoleType + "), format=" + info.format);
        } else {
            check(false, "getRomInfo() 返回 null");
        }

        MesenCore.resume();
        int pixels = renderNonBlackWaiting();
        check(pixels > 0, "渲染 -> " + pixels + " 个非黑像素（" + MesenVideo.getFrameWidth()
            + "x" + MesenVideo.getFrameHeight() + "）");

        // 整机状态字节：按运行中机种的尺寸分配、且只在机种匹配时才填充
        int stateSize = MesenDebugger.getConsoleStateSize(testCase.consoleType);
        byte[] stateBytes = MesenDebugger.getConsoleStateBytes(testCase.consoleType);
        int nonZero = 0;
        for(byte b : stateBytes) {
            if(b != 0) nonZero++;
        }
        check(stateSize > 0 && stateBytes.length == stateSize && nonZero > 0,
            "getConsoleStateBytes -> " + stateSize + " 字节, " + nonZero + " 个非 0");

        // 通用调试接口
        int pc = MesenDebugger.getProgramCounter(testCase.cpuType, true);
        check(pc >= 0, "getProgramCounter -> $" + Integer.toHexString(pc).toUpperCase());

        DebuggerFeatures features = MesenDebugger.getDebuggerFeatures(testCase.cpuType);
        check(features != null, "getDebuggerFeatures -> " + features);

        CodeLine[] lines = MesenDebugger.getDisassemblyOutput(testCase.cpuType, pc, 4);
        check(lines != null, "getDisassemblyOutput -> " + (lines == null ? "null" : lines.length + " 行"));

        // PPU 工具（全机种泛化的核心）
        PaletteInfo palette = MesenDebugger.getPaletteInfo(testCase.cpuType, testCase.tileFormat);
        check(palette != null && palette.colorCount > 0, "getPaletteInfo -> "
            + (palette == null ? "null" : palette.colorCount + " 色（背景 " + palette.bgColorCount
                + " / 精灵 " + palette.spriteColorCount + "）"));

        TileViewOptions tileOptions = new TileViewOptions();
        tileOptions.memoryType = testCase.tileMemoryType;
        tileOptions.format = testCase.tileFormat;
        tileOptions.width = 16;
        tileOptions.height = 16;
        TileView tileView = MesenDebugger.getTileView(testCase.cpuType, tileOptions);
        if(tileView != null) {
            int litPixels = 0;
            for(int pixel : tileView.pixels) {
                if((pixel & 0xFFFFFF) != 0) litPixels++;
            }
            check(litPixels > 0, "getTileView -> " + tileView.width + "x" + tileView.height
                + "，有内容 " + litPixels + " / " + tileView.pixels.length);
        } else {
            check(false, "getTileView 返回 null");
        }

        TilemapResult tilemap = MesenDebugger.getTilemap(testCase.cpuType, new TilemapOptions());
        check(tilemap != null, "getTilemap -> " + (tilemap == null ? "null"
            : tilemap.width + "x" + tilemap.height + "  " + tilemap.info));

        SpritePreviewInfo sprites = MesenDebugger.getSpritePreviewInfo(testCase.cpuType,
            new SpriteOptions());
        check(sprites != null, "getSpritePreviewInfo -> " + sprites);

        MesenCore.stop();
        Thread.sleep(200);
    }

    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        for(ConsoleCase testCase : CASES) {
            runCase(testCase);
        }

        MesenCore.release();

        System.out.println();
        if(checked == 0) {
            System.out.println("CONSOLE SMOKE TEST SKIPPED（一个 ROM 都没有）");
            System.exit(1);
        }
        if(failures == 0) {
            System.out.println("CONSOLE SMOKE TEST PASSED（验证了 " + checked + " 个机种）");
        } else {
            System.out.println("CONSOLE SMOKE TEST FAILED（" + failures + " 项，验证了 "
                + checked + " 个机种）");
            System.exit(1);
        }
        System.exit(0);
    }
}
