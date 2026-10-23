import mesen.MesenCore;

/** 验证「释放后重新初始化」能否工作（多实例/Activity 重建都依赖它）。 */
public class ReinitTest
{
    public static void main(String[] args)
    {
        if(!new java.io.File("roms/MetalMax.nes").isFile()) {
            System.out.println("REINIT TEST SKIPPED (缺少 roms/MetalMax.nes)");
            System.exit(0);
            return;
        }
        MesenCore.initDll();
        System.out.println("1) initDll OK");
        MesenCore.initializeEmu("./home", true, true, true, true);
        System.out.println("2) initializeEmu OK -> " + MesenCore.getMesenVersion() + " / " + MesenCore.getMesenBuildDate());
        System.out.println("3) loadRom -> " + MesenCore.loadRom("roms/MetalMax.nes", null));
        MesenCore.resume();
        try {
            Thread.sleep(200);
        } catch(Exception ignored) {
        }
        MesenCore.stop();
        MesenCore.release();
        System.out.println("4) release OK");

        MesenCore.initDll();
        System.out.println("5) 第二次 initDll OK");
        MesenCore.initializeEmu("./home", true, true, true, true);
        System.out.println("6) 第二次 initializeEmu OK");
        System.out.println("7) 第二次 loadRom -> " + MesenCore.loadRom("roms/MetalMax.nes", null));
        MesenCore.resume();
        System.out.println("8) 第二次 resume OK");
        try {
            Thread.sleep(200);
        } catch(Exception ignored) {
        }
        MesenCore.stop();
        System.out.println("9) 第二次 stop OK");
        MesenCore.release();
        System.out.println("REINIT TEST PASSED");
        System.exit(0);
    }
}
