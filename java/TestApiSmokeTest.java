import mesen.Mesen;
import mesen.MesenCore;
import mesen.RomTestResult;

/**
 * 自动化测试接口（录制回放 / CI 跑测）回归测试。
 *
 * <p>单独一个文件：{@code runCiTests} 会把全局 home 目录改成测试目录下的
 * MesenHomeFolder，跑完就不再适合跑别的用例了。</p>
 */
public class TestApiSmokeTest
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
            System.out.println("TEST API SMOKE TEST SKIPPED (缺少 roms/MetalMax.nes)");
            System.exit(0);
            return;
        }
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);
        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom()");
        MesenCore.resume();
        Thread.sleep(300);

        // ---- 录制测试（.mtp）----
        System.out.println("== 录制测试 ==");
        check(!MesenCore.romTestRecording(), "初始未在录制测试");

        MesenCore.romTestRecord("temp/test.mtp", true);
        check(MesenCore.romTestRecording(), "romTestRecord() 后 recording = true");

        MesenCore.resume();
        Thread.sleep(600);
        MesenCore.pause();
        Thread.sleep(200);

        MesenCore.romTestStop();
        check(!MesenCore.romTestRecording(), "romTestStop() 后 recording = false");

        java.io.File mtp = new java.io.File("temp/test.mtp");
        check(mtp.exists() && mtp.length() > 0, "MTP 文件 -> "
            + (mtp.exists() ? mtp.length() + " 字节" : "未生成"));

        // ---- 回放刚录的测试 ----
        System.out.println("== 回放录制测试 ==");
        RomTestResult result = MesenCore.runRecordedTest("temp/test.mtp", false);
        check(result != null, "runRecordedTest() -> " + result);
        if(result != null) {
            check(result.state == Mesen.ROM_TEST_PASSED
                    || result.state == Mesen.ROM_TEST_PASSED_WITH_WARNINGS,
                "回放通过 -> state=" + result.state + " errorCode=" + result.errorCode
                    + " hash=" + result.lastFrameHash);
        }

        // ---- runTest：跑一个测试 ROM 并读回结果 ----
        System.out.println("== runTest ==");
        long value = MesenCore.runTest("roms/MetalMax.nes", 0, Mesen.MEM_NES_PRG_ROM);
        check(true, "runTest() 读回 -> 0x" + Long.toHexString(value));

        MesenCore.stop();
        MesenCore.release();

        // ---- runCiTests：空目录应报 0 个测试且不失败 ----
        System.out.println("== runCiTests ==");
        java.io.File emptyFolder = new java.io.File("temp/citests");
        emptyFolder.mkdirs();
        boolean ciOk = MesenCore.runCiTests(emptyFolder.getAbsolutePath(), false);
        check(ciOk, "runCiTests(空目录) -> " + ciOk + "（0 个测试，无失败）");

        if(failures == 0) {
            System.out.println("TEST API SMOKE TEST PASSED");
        } else {
            System.out.println("TEST API SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
