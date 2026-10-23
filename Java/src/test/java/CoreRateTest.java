import mesen.AudioDevice;
import mesen.MesenCore;
import mesen.RenderingDevice;

/** 测核心实际帧率（无 UI），用于区分「核心慢」还是「Swing 刷新慢」。 */
public class CoreRateTest
{
    public static void main(String[] args) throws Exception
    {
        if(!new java.io.File("roms/MetalMax.nes").isFile()) {
            System.out.println("CORE RATE TEST SKIPPED (缺少 roms/MetalMax.nes)");
            System.exit(0);
            return;
        }
        boolean withAudio = args.length > 0 && args[0].equals("audio");

        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, !withAudio, false, true);

        RenderingDevice rd = new RenderingDevice();
        MesenCore.registerRenderingDevice(rd);
        if(withAudio) {
            MesenCore.registerAudioDevice(new AudioDevice());
        }

        MesenCore.loadRom("roms/MetalMax.nes", null);
        MesenCore.resume();

        int last = 0;
        for(int i = 1; i <= 5; i++) {
            Thread.sleep(1000);
            int now = rd.getFrameNumber();
            System.out.printf("t=%ds  frames=%d  (+%d)  %.1f fps%n", i, now, now - last, (now - last) / 1.0);
            last = now;
        }

        MesenCore.stop();
        MesenCore.release();
        System.exit(0);
    }
}
