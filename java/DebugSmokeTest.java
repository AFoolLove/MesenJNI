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
import mesen.debugger.DebugEventInfo;
import mesen.debugger.DebuggerFeatures;
import mesen.debugger.EvalResult;
import mesen.debugger.EventViewerConfig;
import mesen.debugger.InstructionProgress;
import mesen.debugger.NesCpuState;
import mesen.debugger.NesConsoleState;
import mesen.debugger.NesPpuState;
import mesen.debugger.PaletteInfo;
import mesen.debugger.SpriteListResult;
import mesen.debugger.SpriteOptions;
import mesen.debugger.SpritePreviewInfo;
import mesen.debugger.StackFrame;
import mesen.debugger.TileView;
import mesen.debugger.TileViewOptions;
import mesen.debugger.TilemapOptions;
import mesen.debugger.TilemapResult;
import mesen.debugger.TilemapTileInfo;

import mesen.debugger.DebugConfig;
import mesen.debugger.DebugControllerState;
import mesen.debugger.ProfiledFunction;
import mesen.debugger.TraceLoggerOptions;
import mesen.debugger.TraceRow;
import mesen.debugger.ViewerSize;

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

    private static void writePng(int[] argb, int width, int height, String path) throws Exception
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        javax.imageio.ImageIO.write(image, "png", new java.io.File(path));
        System.out.println("      已导出 " + path + " (" + width + "x" + height + ")");
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

        // ---- NES 寄存器状态 ----
        System.out.println("== NES 寄存器状态 ==");
        NesCpuState cpu = MesenDebugger.getNesCpuState();
        check(cpu != null, "getNesCpuState() -> " + cpu);
        if(cpu != null) {
            check(cpu.pc == pc, String.format("CPU PC $%04X == getProgramCounter() $%04X", cpu.pc, pc));
        }

        NesPpuState ppu = MesenDebugger.getNesPpuState();
        check(ppu != null, "getNesPpuState() -> " + ppu);
        if(ppu != null) {
            check(ppu.scanlineCount > 0, "scanlineCount = " + ppu.scanlineCount);
            check(ppu.nmiScanline > 0, "nmiScanline = " + ppu.nmiScanline);
            System.out.printf("      VRAM=$%04X TMP=$%04X X=%d pattern bg=$%04X sp=$%04X%n",
                ppu.videoRamAddr, ppu.tmpVideoRamAddr, ppu.scrollX,
                ppu.backgroundPatternAddr, ppu.spritePatternAddr);
        }

        // 写回回环：改 A / VRAM 地址，再读回来
        if(cpu != null) {
            int originalA = cpu.a;
            cpu.a = (originalA + 1) & 0xFF;
            MesenDebugger.setNesCpuState(cpu);

            NesCpuState readBack = MesenDebugger.getNesCpuState();
            check(readBack != null && readBack.a == cpu.a,
                String.format("setNesCpuState 回环: A $%02X -> $%02X", originalA, cpu.a));

            cpu.a = originalA;
            MesenDebugger.setNesCpuState(cpu);
        }

        if(ppu != null) {
            int originalVram = ppu.videoRamAddr;
            ppu.videoRamAddr = (originalVram ^ 0x10) & 0x3FFF;
            MesenDebugger.setNesPpuState(ppu);

            NesPpuState ppuBack = MesenDebugger.getNesPpuState();
            check(ppuBack != null && ppuBack.videoRamAddr == ppu.videoRamAddr,
                String.format("setNesPpuState 回环: VRAM $%04X -> $%04X", originalVram, ppu.videoRamAddr));

            ppu.videoRamAddr = originalVram;
            MesenDebugger.setNesPpuState(ppu);
        }

        // ---- PPU 工具 ----
        System.out.println("== PPU 工具 ==");

        PaletteInfo palette = MesenDebugger.getPaletteInfo(Mesen.CPU_NES, Mesen.TILE_FORMAT_NES_BPP2);
        check(palette != null && palette.colorCount > 0, "getPaletteInfo() -> " + palette);
        if(palette != null && palette.rgbPalette.length > 1) {
            System.out.printf("      颜色 [0]=%08X [1]=%08X [2]=%08X%n",
                palette.rgbPalette[0], palette.rgbPalette[1], palette.rgbPalette[2]);
        }

        TileViewOptions tvOptions = new TileViewOptions();
        tvOptions.memoryType = Mesen.MEM_NES_CHR_ROM;
        tvOptions.format = Mesen.TILE_FORMAT_NES_BPP2;
        tvOptions.width = 16;
        tvOptions.height = 8;
        TileView view = MesenDebugger.getTileView(Mesen.CPU_NES, tvOptions);
        check(view != null && view.width == 128 && view.height == 64, "getTileView() -> " + view);
        if(view != null && view.pixels.length > 0) {
            int nonBlack = 0;
            for(int p : view.pixels) {
                if((p & 0xFFFFFF) != 0) nonBlack++;
            }
            check(nonBlack > 0, "瓦片图非黑像素 = " + nonBlack + " / " + view.pixels.length);
        }

        TilemapOptions tmOptions = new TilemapOptions();
        TilemapResult tilemap = MesenDebugger.getTilemap(Mesen.CPU_NES, tmOptions);
        check(tilemap != null && tilemap.pixels.length > 0, "getTilemap() -> " + tilemap);
        if(tilemap != null && tilemap.pixels.length > 0) {
            int nonBlack = 0;
            for(int p : tilemap.pixels) {
                if((p & 0xFFFFFF) != 0) nonBlack++;
            }
            System.out.printf("      瓦片地图非黑像素 %d / %d%n", nonBlack, tilemap.pixels.length);
        }

        TilemapTileInfo tileInfo = MesenDebugger.getTilemapTileInfo(Mesen.CPU_NES, 8, 8, tmOptions);
        check(tileInfo != null, "getTilemapTileInfo(8,8) -> " + tileInfo);

        // 导出 PNG 便于肉眼确认（temp/ 已 gitignore）
        try {
            new java.io.File("temp").mkdirs();
            if(view != null && view.pixels.length > 0) {
                writePng(view.pixels, view.width, view.height, "temp/tiles.png");
            }
            if(tilemap != null && tilemap.pixels.length > 0) {
                writePng(tilemap.pixels, tilemap.width, tilemap.height, "temp/tilemap.png");
            }
        } catch(Exception e) {
            System.out.println("      导出 PNG 失败: " + e);
        }

        int tilePixel = MesenDebugger.getTilePixel(
            new AddressInfo(0, Mesen.MEM_NES_CHR_ROM), Mesen.TILE_FORMAT_NES_BPP2, 0, 0);
        check(tilePixel >= 0, "getTilePixel($0, 0, 0) = " + tilePixel);

        SpritePreviewInfo spritePreview = MesenDebugger.getSpritePreviewInfo(Mesen.CPU_NES, new SpriteOptions());
        check(spritePreview != null && spritePreview.spriteCount > 0,
            "getSpritePreviewInfo() -> " + spritePreview);

        SpriteListResult sprites = MesenDebugger.getSpriteList(Mesen.CPU_NES, new SpriteOptions());
        check(sprites != null && sprites.sprites.length > 0 && sprites.screenPreview.length > 0,
            "getSpriteList() -> " + sprites);
        if(sprites != null && sprites.sprites.length > 0) {
            System.out.println("      精灵[0] " + sprites.sprites[0]);
            System.out.printf("      预览图 %d ints, 整屏 %d ints%n",
                sprites.spritePreviews.length, sprites.screenPreview.length);
        }

        // 泛化路径：未加载的机种应安全返回，不能崩
        TilemapResult otherTilemap = MesenDebugger.getTilemap(Mesen.CPU_SNES, new TilemapOptions());
        check(otherTilemap == null, "未加载 SNES 时 getTilemap() 安全返回 null");

        SpritePreviewInfo otherSprites = MesenDebugger.getSpritePreviewInfo(Mesen.CPU_SNES, new SpriteOptions());
        check(otherSprites == null, "未加载 SNES 时 getSpritePreviewInfo() 安全返回 null");

        // ---- 事件查看器 ----
        System.out.println("== 事件查看器 ==");
        EventViewerConfig evConfig = MesenDebugger.getEventViewerConfig(Mesen.CPU_NES);
        check(evConfig != null && evConfig.categoryNames.length > 0,
            "getEventViewerConfig() -> " + evConfig);
        if(evConfig != null && evConfig.categoryNames.length > 0) {
            System.out.println("      类别数 " + evConfig.categoryNames.length + "，前 3 个: "
                + String.join(", ", java.util.Arrays.copyOfRange(evConfig.categoryNames, 0, 3)));

            evConfig.visible[0] = true;
            evConfig.visible[1] = true;
            evConfig.colors[0] = 0xFFFF0000;
            evConfig.colors[1] = 0xFF00FF00;
            // 实验：把全部类别都打开（寄存器读写事件最频繁）
            for(int i = 0; i < evConfig.visible.length; i++) {
                evConfig.visible[i] = true;
                evConfig.colors[i] = 0xFF808080 + (i * 0x00070707);
            }
            evConfig.showPreviousFrameEvents = true;
            MesenDebugger.setEventViewerConfig(Mesen.CPU_NES, evConfig);
            check(true, "setEventViewerConfig() 无异常");
        }

        // 事件是按帧记录的：注册一个查看器 + 跑一小段再停下来，当帧才有内容
        MesenDebugger.setViewerUpdateTiming(1, 100, 0, Mesen.CPU_NES);
        MesenCore.resume();

        int maxObservedEvents = 0;
        for(int i = 0; i < 20; i++) {
            Thread.sleep(25);
            // 必须先拍快照：GetEventCount()/GetDebugEvents() 读的是快照，不是实时事件表
            MesenDebugger.takeEventSnapshot(Mesen.CPU_NES, false);
            maxObservedEvents = Math.max(maxObservedEvents, MesenDebugger.getDebugEventCount(Mesen.CPU_NES));
        }

        MesenCore.pause();
        waitStopped(5000);
        System.out.println("      运行中观察到的最多事件数: " + maxObservedEvents);
        check(maxObservedEvents > 0, "运行中能记录到事件 -> " + maxObservedEvents);

        int snapshot = MesenDebugger.takeEventSnapshot(Mesen.CPU_NES, false);
        check(snapshot >= 0, "takeEventSnapshot() -> " + snapshot);

        int eventCount = MesenDebugger.getDebugEventCount(Mesen.CPU_NES);
        System.out.println("      当前帧事件数: " + eventCount);

        DebugEventInfo[] debugEvents = MesenDebugger.getDebugEvents(Mesen.CPU_NES);
        check(debugEvents != null, "getDebugEvents() -> "
            + (debugEvents == null ? "null" : debugEvents.length + " 条"));
        if(debugEvents != null && debugEvents.length > 0) {
            System.out.println("      事件[0] " + debugEvents[0]);
        }

        ViewerSize viewerSize = MesenDebugger.getEventViewerDisplaySize(Mesen.CPU_NES);
        check(viewerSize != null && viewerSize.width > 0, "getEventViewerDisplaySize() -> " + viewerSize);

        if(viewerSize != null && viewerSize.width > 0) {
            int[] viewerOutput = MesenDebugger.getEventViewerOutput(Mesen.CPU_NES);
            check(viewerOutput != null && viewerOutput.length == viewerSize.width * viewerSize.height,
                "getEventViewerOutput() -> "
                    + (viewerOutput == null ? "null" : viewerOutput.length + " 像素"));

            if(viewerOutput != null && viewerOutput.length > 0) {
                // 无内容时整个缓冲是全 0（GetDisplayBuffer 提前返回）；有内容时会先填
                // 背景色 0xFF555555，再叠加屏幕画面和事件点。
                boolean hasContent = false;
                for(int p : viewerOutput) {
                    if(p != 0) {
                        hasContent = true;
                        break;
                    }
                }
                check(hasContent, "事件查看器有渲染内容");
                try {
                    writePng(viewerOutput, viewerSize.width, viewerSize.height, "temp/eventviewer.png");
                } catch(Exception e) {
                    System.out.println("      导出 PNG 失败: " + e);
                }
            }
        }

        DebugEventInfo singleEvent = MesenDebugger.getEventViewerEvent(Mesen.CPU_NES, 100, 0);
        check(singleEvent != null, "getEventViewerEvent(100,0) -> " + singleEvent);

        MesenDebugger.setViewerUpdateTiming(1, 100, 0, Mesen.CPU_NES);
        MesenDebugger.removeViewerId(1, Mesen.CPU_NES);
        check(true, "setViewerUpdateTiming()/removeViewerId() 无异常");

        // ---- 执行跟踪 ----
        System.out.println("== 执行跟踪 ==");
        TraceLoggerOptions traceOptions = new TraceLoggerOptions();
        traceOptions.enabled = true;
        traceOptions.format = "[PC,h] [Disassembly] [ByteCode]";
        MesenDebugger.setTraceOptions(Mesen.CPU_NES, traceOptions);

        MesenCore.resume();
        Thread.sleep(300);
        MesenCore.pause();
        waitStopped(5000);

        TraceRow[] traceRows = MesenDebugger.getExecutionTrace(0, 8);
        check(traceRows != null && traceRows.length > 0,
            "getExecutionTrace() -> " + (traceRows == null ? "null" : traceRows.length + " 行"));
        if(traceRows != null) {
            for(int i = 0; i < Math.min(traceRows.length, 4); i++) {
                System.out.println("      " + traceRows[i]);
            }
        }

        MesenDebugger.startLogTraceToFile("temp/trace.log");
        MesenCore.resume();
        Thread.sleep(250);
        MesenCore.pause();
        waitStopped(5000);
        MesenDebugger.stopLogTraceToFile();
        java.io.File traceFile = new java.io.File("temp/trace.log");
        check(traceFile.exists() && traceFile.length() > 0, "startLogTraceToFile()/stopLogTraceToFile() -> "
            + (traceFile.exists() ? traceFile.length() + " 字节" : "文件未生成"));

        MesenDebugger.clearExecutionTrace();
        TraceRow[] clearedRows = MesenDebugger.getExecutionTrace(0, 8);
        check(clearedRows != null && clearedRows.length == 0, "clearExecutionTrace() 生效");

        traceOptions.enabled = false;
        MesenDebugger.setTraceOptions(Mesen.CPU_NES, traceOptions);

        // ---- CDL 文件读写 ----
        System.out.println("== CDL 文件读写 ==");
        // MetalMax 的 PRG ROM 是 512KB；起始 4KB 是填充区，所以要扫全片
        byte[] cdlData = MesenDebugger.getCdlData(Mesen.MEM_NES_PRG_ROM, 0, 524288);
        int flagged = 0;
        int firstFlagged = -1;
        for(int i = 0; i < cdlData.length; i++) {
            if(cdlData[i] != 0) {
                flagged++;
                if(firstFlagged < 0) firstFlagged = i;
            }
        }
        check(cdlData != null && cdlData.length == 524288 && flagged > 0,
            "getCdlData() -> " + cdlData.length + " 字节, " + flagged + " 个有标记, 首个 @$"
                + Integer.toHexString(firstFlagged));

        MesenDebugger.saveCdlFile(Mesen.MEM_NES_PRG_ROM, "temp/test.cdl");
        java.io.File cdlFile = new java.io.File("temp/test.cdl");
        check(cdlFile.exists() && cdlFile.length() > 0, "saveCdlFile() -> "
            + (cdlFile.exists() ? cdlFile.length() + " 字节" : "文件未生成"));

        byte[] cdlBefore = MesenDebugger.getCdlData(Mesen.MEM_NES_PRG_ROM, 0, 32);
        MesenDebugger.setCdlData(Mesen.MEM_NES_PRG_ROM, new byte[4096]);
        MesenDebugger.loadCdlFile(Mesen.MEM_NES_PRG_ROM, "temp/test.cdl");
        byte[] cdlAfter = MesenDebugger.getCdlData(Mesen.MEM_NES_PRG_ROM, 0, 32);
        check(java.util.Arrays.equals(cdlBefore, cdlAfter), "saveCdlFile()/loadCdlFile() 往返一致");

        MesenDebugger.markBytesAs(Mesen.MEM_NES_PRG_ROM, 0x100, 0x10F,
            Mesen.CDL_CODE | Mesen.CDL_JUMP_TARGET);
        byte[] marked = MesenDebugger.getCdlData(Mesen.MEM_NES_PRG_ROM, 0x100, 16);
        check((marked[0] & 0xFF) == (Mesen.CDL_CODE | Mesen.CDL_JUMP_TARGET),
            "markBytesAs() -> $" + String.format("%02X", marked[0] & 0xFF));

        // ---- 性能分析 ----
        System.out.println("== 性能分析 ==");
        MesenDebugger.resetProfiler(Mesen.CPU_NES);
        MesenCore.resume();
        Thread.sleep(300);
        MesenCore.pause();
        waitStopped(5000);

        ProfiledFunction[] profiled = MesenDebugger.getProfilerData(Mesen.CPU_NES);
        check(profiled != null, "getProfilerData() -> "
            + (profiled == null ? "null" : profiled.length + " 个函数"));
        if(profiled != null && profiled.length > 0) {
            System.out.println("      " + profiled[0]);
        }

        int cpuUsage = MesenDebugger.getProfilerCpuUsage(Mesen.CPU_NES);
        check(cpuUsage >= 0, "getProfilerCpuUsage() -> " + cpuUsage + "%");

        // ---- 输入覆盖 ----
        System.out.println("== 输入覆盖 ==");
        byte[] availableOverrides = MesenDebugger.getAvailableInputOverrides();
        StringBuilder overridePorts = new StringBuilder();
        for(int i = 0; i < availableOverrides.length; i++) {
            if(availableOverrides[i] != 0) {
                overridePorts.append(i).append(' ');
            }
        }
        check(availableOverrides.length == 8, "getAvailableInputOverrides() -> 可用端口 ["
            + overridePorts.toString().trim() + "]");

        DebugControllerState overrideState = new DebugControllerState();
        overrideState.A = true;
        overrideState.Start = true;
        MesenDebugger.setInputOverrides(0, overrideState);
        check(true, "setInputOverrides() 无异常");

        // 复位，避免影响后面的测试
        MesenDebugger.setInputOverrides(0, new DebugControllerState());
        check(true, "setInputOverrides() 已复位");

        // ---- 调试器配置 ----
        System.out.println("== 调试器配置 ==");
        DebugConfig debugConfig = new DebugConfig();
        debugConfig.UseLowerCaseDisassembly = true;
        MesenDebugger.setDebugConfig(debugConfig);

        CodeLine[] lowerLines = MesenDebugger.getDisassemblyOutput(Mesen.CPU_NES, pc, 1);
        boolean isLowerCase = lowerLines != null && lowerLines.length > 0
            && lowerLines[0].text.equals(lowerLines[0].text.toLowerCase());
        check(isLowerCase, "setDebugConfig(UseLowerCaseDisassembly) -> "
            + (lowerLines != null && lowerLines.length > 0 ? lowerLines[0].text.trim() : "?"));

        MesenDebugger.setDebugConfig(new DebugConfig());
        CodeLine[] normalLines = MesenDebugger.getDisassemblyOutput(Mesen.CPU_NES, pc, 1);
        check(normalLines != null && normalLines.length > 0, "setDebugConfig() 已复位 -> "
            + normalLines[0].text.trim());

        // ---- NES 整机状态 ----
        System.out.println("== NES 整机状态 ==");
        NesConsoleState consoleState = MesenDebugger.getNesConsoleState();
        check(consoleState != null, "getNesConsoleState() -> " + consoleState);
        if(consoleState != null && consoleState.clockRate > 0) {
            System.out.println("      主频: " + consoleState.clockRate + " Hz");
            System.out.println("      PRG " + consoleState.cartridge.PrgPageCount + " 页 × "
                + consoleState.cartridge.PrgPageSize + " 字节, CHR "
                + consoleState.cartridge.ChrPageCount + " 页 × "
                + consoleState.cartridge.ChrPageSize + " 字节, 镜像="
                + consoleState.cartridge.Mirroring);

            int mapped = 0;
            for(int offset : consoleState.cartridge.PrgMemoryOffset) {
                if(offset >= 0) mapped++;
            }
            check(mapped > 0, "PRG 页映射 -> " + mapped + " / 256 项有映射");

            System.out.println("      CPU: PC=$" + String.format("%04X", consoleState.cpu.pc)
                + " A=$" + String.format("%02X", consoleState.cpu.a & 0xFF)
                + "  PPU: scanline=" + consoleState.ppu.scanline);
            System.out.println("      APU: " + consoleState.apu);
            System.out.println("      APU 方波1: " + consoleState.apu.Square1);
            System.out.println("      APU 三角: " + consoleState.apu.Triangle);
        }

        // ---- 汇编 / ROM 导出 ----
        System.out.println("== 汇编 / ROM 导出 ==");
        short[] assembled = MesenDebugger.assembleCode(Mesen.CPU_NES, "LDA #$10\nSTA $20", 0x8000);
        check(assembled != null && assembled.length > 0, "assembleCode() -> "
            + (assembled == null ? "null" : assembled.length + " 条"));
        if(assembled != null && assembled.length > 0) {
            StringBuilder sb = new StringBuilder();
            for(short s : assembled) {
                sb.append(String.format("%04X ", s & 0xFFFF));
            }
            System.out.println("      编码: " + sb.toString().trim());
        }

        byte[] header = MesenDebugger.getRomHeader();
        check(header != null && header.length > 0, "getRomHeader() -> "
            + (header == null ? "null" : header.length + " 字节"));
        if(header != null && header.length >= 16) {
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < 16; i++) {
                sb.append(String.format("%02X ", header[i]));
            }
            System.out.println("      头 16 字节: " + sb.toString().trim());
        }

        boolean saved = MesenDebugger.saveRomToDisk("temp/exported.nes", false, Mesen.CDL_STRIP_NONE);
        check(saved, "saveRomToDisk() -> " + saved);

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
