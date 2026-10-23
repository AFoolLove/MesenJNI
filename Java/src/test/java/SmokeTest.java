import mesen.*;
import mesen.Mesen;

/** Minimal smoke test: loads the native library and calls a few bindings. */
public class SmokeTest
{
    public static void main(String[] args)
    {
        System.out.println("testDll = " + MesenCore.testDll());

        MesenCore.initDll();
        System.out.println("version = " + MesenCore.getMesenVersion());
        System.out.println("buildDate = " + MesenCore.getMesenBuildDate());

        MesenCore.initializeEmu("./home", true, true, false, true);
        int w = MesenVideo.getFrameWidth();
        int h = MesenVideo.getFrameHeight();
        System.out.println("frame " + w + "x" + h);
        int[] frame = new int[w * h];
        MesenVideo.copyFrameBuffer(frame);
        System.out.println("copied " + frame.length + " ARGB pixels");
        MesenCore.release();

        System.out.println("SMOKE TEST PASSED");
    }
}
