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
