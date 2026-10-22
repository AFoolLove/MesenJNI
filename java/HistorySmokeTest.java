import mesen.HistoryViewerOptions;
import mesen.HistoryViewerState;
import mesen.Mesen;
import mesen.MesenCore;

/**
 * 历史查看器（倒带时间轴）回归测试。
 *
 * <p>注意：core 会为它再建一个 Emulator 实例，并在 Windows 上创建原生音频设备，
 * 所以这个测试单独跑，避免影响其它测试。</p>
 */
public class HistorySmokeTest
{
    private static int failures = 0;

    private static void check(boolean ok, String message)
    {
        System.out.println((ok ? "  [OK] " : "  [FAIL] ") + message);
        if(!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception
    {
        if(!new java.io.File("roms/MetalMax.nes").isFile()) {
            System.out.println("HISTORY SMOKE TEST SKIPPED (缺少 roms/MetalMax.nes)");
            System.exit(0);
            return;
        }
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);
        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom()");

        // 跑一会儿让倒带缓冲攒下历史
        MesenCore.resume();
        Thread.sleep(1500);
        MesenCore.pause();
        Thread.sleep(200);

        check(MesenCore.historyViewerEnabled(), "historyViewerEnabled() -> true（有历史可回放）");

        System.out.println("== 初始化 ==");
        MesenCore.historyViewerInitialize(0, 0);
        check(true, "historyViewerInitialize(0, 0) 无异常");

        HistoryViewerState state = MesenCore.historyViewerGetState();
        check(state != null, "historyViewerGetState() -> " + state);

        System.out.println("== 选项 / 跳转 ==");
        HistoryViewerOptions options = new HistoryViewerOptions();
        options.volume = 50;
        options.isPaused = true;
        options.width = 256;
        options.height = 240;
        MesenCore.historyViewerSetOptions(options);
        check(true, "historyViewerSetOptions() 无异常");

        int seekTarget = state == null ? 0 : Math.max(0, state.length / 2);
        MesenCore.historyViewerSetPosition(seekTarget);
        Thread.sleep(300);
        check(true, "historyViewerSetPosition(" + seekTarget + ") 无异常");

        HistoryViewerState afterSeek = MesenCore.historyViewerGetState();
        check(afterSeek != null, "跳转后 getState() -> " + afterSeek);

        System.out.println("== 导出 ==");
        boolean savedState = MesenCore.historyViewerCreateSaveState("temp/history.mst", seekTarget);
        check(savedState, "historyViewerCreateSaveState() -> " + savedState);

        boolean savedMovie = MesenCore.historyViewerSaveMovie("temp/history.mmo", 0, seekTarget);
        check(savedMovie, "historyViewerSaveMovie() -> " + savedMovie);

        System.out.println("== 释放 ==");
        MesenCore.historyViewerRelease();
        check(true, "historyViewerRelease() 无异常");

        // 没初始化时 release 也不该崩（core 那边会解引用空指针，JNI 里挡住了）
        MesenCore.historyViewerRelease();
        check(true, "重复 release 安全");

        HistoryViewerState afterRelease = MesenCore.historyViewerGetState();
        check(afterRelease != null, "释放后 getState() 仍安全 -> " + afterRelease);

        MesenCore.stop();
        MesenCore.release();

        if(failures == 0) {
            System.out.println("HISTORY SMOKE TEST PASSED");
        } else {
            System.out.println("HISTORY SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
