package com.mesen;

/**
 * JNI bindings for the Mesen core (MesenCore native library).
 *
 * The native implementation lives in the MesenJNI shared library
 * (MesenJNI.dll / libMesenJNI.so), which links against MesenCore.
 *
 * Headless usage example:
 * <pre>
 *   MesenCore.initDll();
 *   MesenCore.initializeEmu("/path/to/home", true, true, false, true);
 *   MesenCore.setEmulationFlag(Mesen.FLAG_MAXIMUM_SPEED, true);
 *   if(MesenCore.loadRom("/path/to/game.nes", null)) {
 *       MesenCore.resume();
 *       while(MesenCore.isRunning()) {
 *           int w = MesenCore.getFrameWidth();
 *           int h = MesenCore.getFrameHeight();
 *           int[] frame = new int[w * h];
 *           MesenCore.copyFrameBuffer(frame);
 *           // ... process frame ...
 *       }
 *       int stopCode = MesenCore.getStopCode();
 *   }
 *   MesenCore.release();
 * </pre>
 */
public final class MesenCore
{
    static {
        // Load the core first so the JNI library can resolve its MesenCore
        // dependency (Windows does not search the JNI DLL's own directory).
        System.loadLibrary("MesenCore");
        System.loadLibrary("MesenJNI");
    }

    private MesenCore() {
    }

    // --- Lifecycle / info --------------------------------------------------

    public static native boolean testDll();
    public static native int getMesenVersion();
    public static native String getMesenBuildDate();
    public static native void initDll();

    /**
     * Initializes the emulator. Handles are always null (headless); the
     * software renderer is created when {@code softwareRenderer} is true.
     *
     * @param softwareRenderer use the software renderer (enables frame output)
     * @param noAudio          disable audio output
     * @param noVideo          disable video output
     * @param noInput          disable OS input handling
     */
    public static native void initializeEmu(String homeFolder, boolean softwareRenderer, boolean noAudio, boolean noVideo, boolean noInput);

    public static native void release();
    public static native boolean isRunning();
    public static native void stop();
    public static native int getStopCode();
    public static native void pause();
    public static native void resume();
    public static native boolean isPaused();

    // --- ROM ---------------------------------------------------------------

    public static native boolean loadRom(String filename, String patchFile);
    public static native void loadRecentGame(String filepath, boolean resetGame);
    public static native void addKnownGameFolder(String folder);
    public static native RomInfo getRomInfo();
    public static native String getRomHash(int hashType);

    // --- State -------------------------------------------------------------

    public static native void saveState(int slot);
    public static native void loadState(int slot);
    public static native void saveStateFile(String filepath);
    public static native void loadStateFile(String filepath);
    public static native void takeScreenshot();

    // --- Memory ------------------------------------------------------------

    public static native long getMemorySize(int memoryType);
    public static native int getMemoryValue(int memoryType, long address);
    public static native byte[] getMemoryValues(int memoryType, long start, long end);
    public static native void setMemoryValue(int memoryType, long address, int value);
    public static native void setMemoryValues(int memoryType, long address, byte[] data);

    // --- Config ------------------------------------------------------------

    public static native void setEmulationFlag(int flag, boolean enabled);
    public static native void setDebuggerFlag(int flag, boolean enabled);

    /** Sets the emulation config (speed / run-ahead). */
    public static native void setEmulationConfig(int emulationSpeed, int turboSpeed, int rewindSpeed, int runAheadFrames);

    /** Sets key video options; pass a value you don't want to change as-is. */
    public static native void setVideoConfig(int videoFilter, int aspectRatio, double customAspectRatio, int screenRotation);

    /** Sets key audio options. */
    public static native void setAudioConfig(boolean enableAudio, int masterVolume, int sampleRate, int audioLatency);

    /** Sets the controller type for a console port (see Mesen.CONTROLLER_*). */
    public static native void setControllerType(int consoleType, int port, int controllerType);

    /** Maps a controller button to a key scan code (see Mesen.BUTTON_*). */
    public static native void setControllerKey(int consoleType, int port, int button, int keyCode);

    // --- Palette config ----------------------------------------------------

    /** Sets the NES custom palette; array of up to 512 entries (0xRRGGBB). */
    public static native void setNesCustomPalette(int[] palette);

    /** Enables the NES full 512-color palette (instead of the classic 64 colors). */
    public static native void setNesFullColorPalette(boolean enabled);

    /** Enables the custom VS System palette. */
    public static native void setNesUseCustomVsPalette(boolean enabled);

    /** Sets the PCE custom palette; array of up to 512 entries (0xRRGGBB). */
    public static native void setPceCustomPalette(int[] palette);

    /** Sets the GB (DMG) 4-color palettes; each array has 4 entries (0xRRGGBB). */
    public static native void setGameboyPalette(int[] background, int[] obj0, int[] obj1);

    /** Enables the SMS SG palette. */
    public static native void setSmsUseSgPalette(boolean enabled);

    /** Enables GBA color correction. */
    public static native void setGbaAdjustColors(boolean enabled);

    /** Enables WonderSwan LCD color adjustment. */
    public static native void setWsLcdAdjustColors(boolean enabled);

    public static native void setRendererSize(int width, int height);
    public static native double getAspectRatio();
    public static native FrameInfo getBaseScreenSize();
    public static native TimingInfo getTimingInfo(int cpuType);

    // --- Input -------------------------------------------------------------

    public static native void setKeyState(int scanCode, boolean state);
    public static native void resetKeyState();
    public static native void disableAllKeys(boolean disabled);
    public static native void updateInputDevices();
    public static native int getKeyCode(String keyName);
    public static native String getKeyName(int keyCode);
    public static native void setMousePosition(double x, double y);
    public static native void setMouseMovement(int x, int y);
    public static native boolean hasControlDevice(int controllerType);

    // --- Log / messages ----------------------------------------------------

    public static native String getLog();
    public static native void writeLogEntry(String message);
    public static native void displayMessage(String title, String message, String param);

    // --- Software-rendered frame ------------------------------------------

    /** Width of the current software-rendered frame (ARGB). */
    public static native int getFrameWidth();

    /** Height of the current software-rendered frame (ARGB). */
    public static native int getFrameHeight();

    /**
     * Copies the current software-rendered frame (32-bit ARGB, one int per
     * pixel) into {@code argb}. The array must be at least
     * {@code width * height} ints long.
     */
    public static native void copyFrameBuffer(int[] argb);
}
