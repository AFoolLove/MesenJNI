package mesen;

/**
 * 配置/调色板/手柄等 native 设置方法。
 *
 * <p>注意：本类是「原生配置方法」；Java 侧 JSON 配置持久化在 {@link MesenSettings}。</p>
 */
public final class MesenConfig
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenConfig() {
    }

    public static native void setEmulationFlag(int flag, boolean enabled);
    public static native void setDebuggerFlag(int flag, boolean enabled);

    /** Sets the emulation config (speed / run-ahead). */
    public static native void setEmulationConfig(int emulationSpeed, int turboSpeed, int rewindSpeed, int runAheadFrames);

    /** Sets key video options. */
    public static native void setVideoConfig(int videoFilter, int aspectRatio, double customAspectRatio, int screenRotation);

    /** Sets key audio options. */
    public static native void setAudioConfig(boolean enableAudio, int masterVolume, int sampleRate, int audioLatency);

    /** Sets the controller type for a console port (see Mesen.CONTROLLER_*). */
    public static native void setControllerType(int consoleType, int port, int controllerType);

    /** Maps a controller button to a key scan code (see Mesen.BUTTON_*). */
    public static native void setControllerKey(int consoleType, int port, int button, int keyCode);

    /** Sets the NES custom palette; array of up to 512 entries (0xRRGGBB). */
    public static native void setNesCustomPalette(int[] palette);

    /** Enables the NES full 512-color palette. */
    public static native void setNesFullColorPalette(boolean enabled);

    /** Enables the custom VS System palette. */
    public static native void setNesUseCustomVsPalette(boolean enabled);

    /** Sets the NES console region (see Mesen.REGION_*). */
    public static native void setNesRegion(int region);

    /** Enables/disables the NES sprite limit. */
    public static native void setNesRemoveSpriteLimit(boolean enabled);

    /** Sets the PCE custom palette. */
    public static native void setPceCustomPalette(int[] palette);

    /** Sets the GB (DMG) 4-color palettes. */
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
}
