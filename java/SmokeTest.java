import mesen.MesenCore;
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

        MesenCore.initializeEmu("./smoke-home", true, true, false, true);
        int w = MesenCore.getFrameWidth();
        int h = MesenCore.getFrameHeight();
        System.out.println("frame " + w + "x" + h);
        int[] frame = new int[w * h];
        MesenCore.copyFrameBuffer(frame);
        System.out.println("copied " + frame.length + " ARGB pixels");
        MesenCore.release();

        System.out.println("SMOKE TEST PASSED");
    }
}
