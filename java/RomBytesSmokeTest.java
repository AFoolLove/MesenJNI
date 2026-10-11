import java.nio.file.Files;
import java.nio.file.Paths;

import mesen.Mesen;
import mesen.MesenCore;
import mesen.RomInfo;

/** 从内存字节加载 ROM 的回归测试。 */
public class RomBytesSmokeTest
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
        int[] frame = new int[mesen.MesenVideo.getFrameWidth() * mesen.MesenVideo.getFrameHeight()];
        mesen.MesenVideo.copyFrameBuffer(frame);
        int nonBlack = 0;
        for(int pixel : frame) {
            if((pixel & 0xFFFFFF) != 0) {
                nonBlack++;
            }
        }
        return nonBlack;
    }
    /** 等到画面出现非黑像素（MetalMax 标题画面要一秒左右），最多等 5 秒。 */
    private static int renderNonBlackWaiting() throws Exception
    {
        int nonBlack = 0;
        for(int i = 0; i < 50 && nonBlack == 0; i++) {
            Thread.sleep(100);
            nonBlack = renderNonBlack();
        }
        return nonBlack;
    }

    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        byte[] romData = Files.readAllBytes(Paths.get("roms/MetalMax.nes"));
        System.out.println("      读到 " + romData.length + " 字节 ROM");

        // ---- 用路径加载（对照）----
        System.out.println("== 路径加载（对照）==");
        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom(path) 成功");
        RomInfo byPath = MesenCore.getRomInfo();
        check(byPath != null, "路径加载后 getRomInfo() -> " + byPath);

        MesenCore.resume();
        int pathPixels = renderNonBlackWaiting();
        check(pathPixels > 0, "路径加载渲染 -> " + pathPixels + " 个非黑像素");
        MesenCore.stop();
        Thread.sleep(200);

        // ---- 用字节加载 ----
        System.out.println("== 字节加载 ==");
        check(MesenCore.loadRomFromBytes(romData, "MetalMax.nes", null), "loadRomFromBytes() 成功");

        RomInfo byBytes = MesenCore.getRomInfo();
        check(byBytes != null, "字节加载后 getRomInfo() -> " + byBytes);
        if(byPath != null && byBytes != null) {
            check(byPath.format == byBytes.format && byPath.consoleType == byBytes.consoleType,
                "格式/机种与路径加载一致 -> format=" + byBytes.format
                    + ", consoleType=" + byBytes.consoleType
                    + ", romPath=" + byBytes.romPath);
        }

        MesenCore.resume();
        int bytesPixels = renderNonBlackWaiting();
        check(bytesPixels > 0, "字节加载渲染 -> " + bytesPixels + " 个非黑像素");

        // core 会拷贝数据：把传进去的数组清掉，游戏应当照常跑
        java.util.Arrays.fill(romData, (byte)0);
        MesenCore.resume();
        int afterWipePixels = renderNonBlackWaiting();
        check(afterWipePixels > 0,
            "清空传入数组后仍在渲染 -> " + afterWipePixels + " 个非黑像素（证明 core 已拷贝）");

        // ---- 边界情况 ----
        System.out.println("== 边界 ==");
        check(!MesenCore.loadRomFromBytes(new byte[0], "empty.nes", null), "空数组返回 false");
        check(!MesenCore.loadRomFromBytes(null, "null.nes", null), "null 返回 false");

        MesenCore.stop();
        MesenCore.release();

        if(failures == 0) {
            System.out.println("ROM BYTES SMOKE TEST PASSED");
        } else {
            System.out.println("ROM BYTES SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
