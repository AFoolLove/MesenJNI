import mesen.MesenCore;
import mesen.Mesen;

/** Smoke test for the JNI config APIs. */
public class ConfigSmokeTest
{
    public static void main(String[] args)
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

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

        // Palette config
        MesenCore.setNesFullColorPalette(true);
        MesenCore.setNesUseCustomVsPalette(true);
        MesenCore.setNesCustomPalette(new int[512]);
        MesenCore.setPceCustomPalette(new int[512]);
        MesenCore.setGameboyPalette(new int[4], new int[4], new int[4]);
        MesenCore.setSmsUseSgPalette(true);
        MesenCore.setGbaAdjustColors(false);
        MesenCore.setWsLcdAdjustColors(true);

        System.out.println("CONFIG TEST PASSED");
        MesenCore.release();
    }
}
