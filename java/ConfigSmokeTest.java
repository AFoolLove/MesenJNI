import com.mesen.MesenCore;
import com.mesen.Mesen;

/** Smoke test for the JNI config APIs. */
public class ConfigSmokeTest
{
    public static void main(String[] args)
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./config-home", true, true, false, true);

        // Emulation / video / audio config
        MesenCore.setEmulationConfig(100, 300, 100, 1);
        MesenCore.setVideoConfig(Mesen.VIDEO_FILTER_NONE, Mesen.ASPECT_RATIO_NO_STRETCHING, 1.0, 0);
        MesenCore.setAudioConfig(true, 80, 48000, 60);

        // Controller config: NES port 0 = NES controller, Start -> key 10
        MesenCore.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);
        MesenCore.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_START, 10);
        MesenCore.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_A, 5);

        // Debugger flag
        MesenCore.setDebuggerFlag(Mesen.DEBUGGER_FLAG_NES, false);

        System.out.println("CONFIG TEST PASSED");
        MesenCore.release();
    }
}
