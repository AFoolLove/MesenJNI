package mesen;

/**
 * 采集音频（交错 int16 PCM）。
 */
public final class MesenAudio
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenAudio() {
    }

    /** Sample rate of the captured audio (e.g. 48000). */
    public static native int getAudioSampleRate();

    /**
     * Reads captured audio samples (interleaved stereo int16 PCM) into
     * {@code out}. Returns the number of int16 values written (0 if none).
     * Requires headless audio: initializeEmu(..., noAudio=false, ...).
     */
    public static native int readAudioSamples(short[] out);
}
