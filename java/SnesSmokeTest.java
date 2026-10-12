import mesen.Mesen;
import mesen.MesenCore;
import mesen.MesenDebugger;
import mesen.MesenVideo;
import mesen.RomInfo;

/**
 * SNES 端到端冒烟：验证之前只能用 NES 验证的那些通用路径。
 *
 * <p>重点是 PPU 工具的「全机种泛化」和整机状态字节 API —— 这两块此前因为没有
 * SNES ROM 一直没测过。</p>
 */
public class SnesSmokeTest
{
    private static int failures = 0;

    private static void check(boolean ok, String message)
    {
        System.out.println((ok ? "  [OK] " : "  [FAIL] ") + message);
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
        for(int i = 0; i < 60 && nonBlack == 0; i++) {
            Thread.sleep(100);
            nonBlack = renderNonBlack();
        }
        return nonBlack;
    }

    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        System.out.println("== 加载 SNES ROM（.smc，带 512 字节 copier 头）==");
        check(MesenCore.loadRom("roms/MetalMaxR.smc", null), "loadRom(MetalMaxR.smc) 成功");

        RomInfo info = MesenCore.getRomInfo();
        check(info != null, "getRomInfo() -> " + info);
        if(info != null) {
            System.out.println("      consoleType=" + info.consoleType + " (0=SNES), format=" + info.format
                + ", romPath=" + info.romPath);
            check(info.consoleType == Mesen.CONSOLE_SNES,
                "识别为 SNES -> consoleType=" + info.consoleType);
        }

        MesenCore.resume();
        int pixels = renderNonBlackWaiting();
        check(pixels > 0, "渲染 -> " + pixels + " 个非黑像素（画面 " + MesenVideo.getFrameWidth()
            + "x" + MesenVideo.getFrameHeight() + "）");

        // ---- 整机状态字节：此前 SNES 未运行，只能返回全 0 ----
        System.out.println("== SNES 整机状态（字节级）==");
        int size = MesenDebugger.getConsoleStateSize(Mesen.CONSOLE_SNES);
        byte[] stateBytes = MesenDebugger.getConsoleStateBytes(Mesen.CONSOLE_SNES);
        int nonZero = 0;
        for(byte b : stateBytes) {
            if(b != 0) nonZero++;
        }
        check(size > 0 && stateBytes.length == size && nonZero > 0,
            "getConsoleStateBytes(SNES) -> " + size + " 字节, " + nonZero + " 个非 0（真的填上了）");

        // ---- 通用反汇编 / 调试器特性 ----
        System.out.println("== 通用调试接口在 SNES 上 ==");
        int pc = MesenDebugger.getProgramCounter(Mesen.CPU_SNES, true);
        System.out.println("      SNES PC = $" + Integer.toHexString(pc).toUpperCase());
        check(pc >= 0, "getProgramCounter(CPU_SNES) -> $" + Integer.toHexString(pc).toUpperCase());

        mesen.debugger.DebuggerFeatures features = MesenDebugger.getDebuggerFeatures(Mesen.CPU_SNES);
        check(features != null, "getDebuggerFeatures(CPU_SNES) -> " + features);

        mesen.debugger.CodeLine[] lines = MesenDebugger.getDisassemblyOutput(Mesen.CPU_SNES, pc, 4);
        check(lines != null && lines.length > 0, "getDisassemblyOutput(CPU_SNES) -> "
            + (lines == null ? "null" : lines.length + " 行"));
        if(lines != null) {
            for(int i = 0; i < Math.min(lines.length, 4); i++) {
                System.out.println("      " + lines[i].text.trim());
            }
        }

        // ---- PPU 工具：泛化路径首次在 NES 之外验证 ----
        System.out.println("== SNES PPU 工具（泛化的关键验证）==");
        mesen.debugger.PaletteInfo palette = MesenDebugger.getPaletteInfo(Mesen.CPU_SNES,
            Mesen.TILE_FORMAT_BPP4);
        check(palette != null, "getPaletteInfo(CPU_SNES) -> "
            + (palette == null ? "null" : palette.colorCount + " 色"));
        if(palette != null) {
            System.out.println("      颜色数=" + palette.colorCount + " 背景=" + palette.bgColorCount
                + " 精灵=" + palette.spriteColorCount + " 每调色板=" + palette.colorsPerPalette
                + " 原始格式=" + palette.rawFormat);
        }

        mesen.debugger.TileViewOptions tileOptions = new mesen.debugger.TileViewOptions();
        tileOptions.memoryType = Mesen.MEM_SNES_VIDEO_RAM;
        tileOptions.format = Mesen.TILE_FORMAT_BPP4;
        tileOptions.width = 16;
        tileOptions.height = 16;
        mesen.debugger.TileView tileView = MesenDebugger.getTileView(Mesen.CPU_SNES, tileOptions);
        check(tileView != null, "getTileView(CPU_SNES) -> "
            + (tileView == null ? "null" : tileView.width + "x" + tileView.height));
        if(tileView != null) {
            int nonBlackTiles = 0;
            for(int pixel : tileView.pixels) {
                if((pixel & 0xFFFFFF) != 0) nonBlackTiles++;
            }
            check(nonBlackTiles > 0, "SNES 瓦片图有内容 -> " + nonBlackTiles + " / " + tileView.pixels.length);
        }

        mesen.debugger.TilemapOptions tilemapOptions = new mesen.debugger.TilemapOptions();
        mesen.debugger.TilemapResult tilemap = MesenDebugger.getTilemap(Mesen.CPU_SNES, tilemapOptions);
        check(tilemap != null, "getTilemap(CPU_SNES) -> "
            + (tilemap == null ? "null" : tilemap.width + "x" + tilemap.height));
        if(tilemap != null && tilemap.info != null) {
            System.out.println("      " + tilemap.info);
        }

        mesen.debugger.SpriteOptions spriteOptions = new mesen.debugger.SpriteOptions();
        mesen.debugger.SpritePreviewInfo spritePreview =
            MesenDebugger.getSpritePreviewInfo(Mesen.CPU_SNES, spriteOptions);
        check(spritePreview != null, "getSpritePreviewInfo(CPU_SNES) -> " + spritePreview);

        MesenCore.stop();
        MesenCore.release();

        if(failures == 0) {
            System.out.println("SNES SMOKE TEST PASSED");
        } else {
            System.out.println("SNES SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
