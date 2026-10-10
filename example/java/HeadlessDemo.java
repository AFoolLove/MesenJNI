import mesen.KeyManager;
import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenCore;
import mesen.MesenMemory;
import mesen.NotificationListener;
import mesen.RenderingDevice;
import mesen.RomInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 无头示例：不开窗口跑 N 帧，导出 PNG、读内存、存/读存档。
 *
 * <p>适合做自动化：批量截图、跑测试、验证 ROM 行为。</p>
 *
 * <pre>
 * javac -encoding UTF-8 -cp "lib/gson-2.9.0.jar" -d build/classes \
 *       java/mesen/*.java java/mesen/interfaces/*.java example/java/*.java
 * java -Djava.library.path=bin/win-x64/Release \
 *      -cp "build/classes;lib/gson-2.9.0.jar" HeadlessDemo roms/game.nes 600 frame.png
 * </pre>
 */
public class HeadlessDemo
{
    public static void main(String[] args) throws Exception
    {
        if(args.length < 1) {
            System.out.println("用法: HeadlessDemo <rom> [帧数=300] [输出.png=frame.png]");
            return;
        }

        File rom = new File(args[0]);
        int targetFrame = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        File outPng = new File(args.length > 2 ? args[2] : "frame.png");
        String home = new File("./home").getAbsolutePath();

        // 1) 初始化：headless（无窗口），软件渲染，只要视频不要音频
        MesenCore.initDll();
        MesenCore.initializeEmu(home, /*softwareRenderer*/true,
            /*noAudio*/true, /*noVideo*/false, /*noInput*/true);

        // 2) 注册回调：帧 / 通知 / 输入
        RenderingDevice renderer = new RenderingDevice();
        NotificationListener events = new NotificationListener();
        KeyManager keys = new KeyManager();
        MesenCore.registerRenderingDevice(renderer);
        MesenCore.registerNotificationListener(events);
        MesenCore.registerKeyManager(keys);

        // 3) 配置输入：先类型、后映射（只设映射的话核不会创建手柄设备）
        MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_START, 1);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_A, 2);

        // 4) 加载 ROM
        if(!MesenCore.loadRom(rom.getAbsolutePath(), null)) {
            System.err.println("加载 ROM 失败: " + rom);
            MesenCore.release();
            System.exit(1);
        }

        RomInfo info = MesenCore.getRomInfo();
        System.out.println("ROM       : " + new File(info.romPath).getName());
        System.out.printf("机种/格式 : %d / %d%n", info.consoleType, info.format);
        System.out.println("SHA1      : " + MesenCore.getRomHash(Mesen.HASH_SHA1));
        System.out.println("CPU RAM   : " + MesenMemory.getMemorySize(Mesen.MEM_NES) + " 字节");

        MesenCore.resume();

        // 5) 跑到目标帧（顺带演示按键注入：前 0.5 秒按住 Start）
        keys.setKeyState(1, true);
        Thread.sleep(500);
        keys.setKeyState(1, false);

        long deadline = System.currentTimeMillis() + 60_000;
        while(renderer.getFrameNumber() < targetFrame && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        MesenCore.pause();
        System.out.printf("已跑 %d 帧（目标 %d）%n", renderer.getFrameNumber(), targetFrame);

        // 6) 导出当前帧为 PNG
        BufferedImage frame = renderer.getBufferedImage();
        if(frame != null) {
            ImageIO.write(frame, "png", outPng);
            System.out.printf("截图      : %s (%dx%d)%n",
                outPng.getAbsolutePath(), frame.getWidth(), frame.getHeight());
        } else {
            System.out.println("还没拿到帧");
        }

        // 7) 存档
        File state = new File(home, "quicksave.mst");
        MesenCore.saveStateFile(state.getAbsolutePath());
        System.out.println("存档      : " + state.getAbsolutePath());

        // 8) 读内存（CPU $0000-$000F）
        StringBuilder mem = new StringBuilder();
        for(long addr = 0; addr < 16; addr++) {
            mem.append(String.format("%02X ", MesenMemory.getMemoryValue(Mesen.MEM_NES, addr)));
        }
        System.out.println("RAM $00-0F: " + mem.toString().trim());

        // 9) 汇总
        System.out.println("通知      : " + events.getCount() + " 条（最后类型 " + events.getLastType() + "）");
        System.out.println("停止码    : " + MesenCore.getStopCode());

        MesenCore.stop();
        MesenCore.release();
        System.exit(0);
    }
}
