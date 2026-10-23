import mesen.*;
import mesen.Mesen;

/** Smoke test for the JNI config APIs. */
public class ConfigSmokeTest
{
    public static void main(String[] args)
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        // Emulation / video / audio config
        MesenConfig.setEmulationConfig(100, 300, 100, 1);
        MesenConfig.setVideoConfig(Mesen.VIDEO_FILTER_NONE, Mesen.ASPECT_RATIO_NO_STRETCHING, 1.0, 0);
        MesenConfig.setAudioConfig(true, 80, 48000, 60);

        // Controller config: NES port 0 = NES controller, Start -> key 10
        MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_START, 10);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_A, 5);

        // Debugger flag
        MesenConfig.setDebuggerFlag(Mesen.DEBUGGER_FLAG_NES, false);

        // Palette config
        MesenConfig.setNesFullColorPalette(true);
        MesenConfig.setNesUseCustomVsPalette(true);
        MesenConfig.setNesCustomPalette(new int[512]);
        MesenConfig.setPceCustomPalette(new int[512]);
        MesenConfig.setGameboyPalette(new int[4], new int[4], new int[4]);
        MesenConfig.setSmsUseSgPalette(true);
        MesenConfig.setGbaAdjustColors(false);
        MesenConfig.setWsLcdAdjustColors(true);

        System.out.println("CONFIG TEST PASSED");
        MesenCore.release();
    }
}
