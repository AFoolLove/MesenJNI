package mesen;

/**
 * 软件渲染帧（32-bit ARGB）。
 */
public final class MesenVideo
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenVideo() {
    }

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
