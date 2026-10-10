import mesen.MesenCore;
import mesen.NotificationListener;
import mesen.KeyManager;
import mesen.MouseManager;
import mesen.AudioDevice;
import mesen.RenderingDevice;

/** 验证回调接口的实现类（非空默认实现）。 */
public class ImplSmokeTest
{
    public static void main(String[] args)
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, true, true);

        NotificationListener nl = new NotificationListener();
        MesenCore.registerNotificationListener(nl);

        KeyManager km = new KeyManager();
        MesenCore.registerKeyManager(km);
        km.setKeyState(10, true);

        MouseManager mm = new MouseManager();
        MesenCore.registerMouseManager(mm);
        mm.setSystemMousePosition(5, 6);

        RenderingDevice rd = new RenderingDevice();
        MesenCore.registerRenderingDevice(rd);

        AudioDevice ad = new AudioDevice();
        MesenCore.registerAudioDevice(ad);

        System.out.println("IMPL SMOKE TEST PASSED");
        MesenCore.release();
        System.exit(0);
    }
}
