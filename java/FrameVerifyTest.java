import mesen.MesenCore;
import mesen.MesenVideo;
import mesen.interfaces.IRenderingDevice;

import java.util.concurrent.atomic.AtomicInteger;

/** 验证默认配置下帧内容是否非零（调色板初始化修复）。 */
public class FrameVerifyTest
{
    public static void main(String[] args) throws Exception
    {
        boolean useCallback = args.length > 0 && args[0].equals("cb");
        AtomicInteger frames = new AtomicInteger();
        AtomicInteger nonZeroFrames = new AtomicInteger();

        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        if(useCallback) {
            MesenCore.registerRenderingDevice(new IRenderingDevice() {
                public void updateFrame(int w, int h, int[] argb, int fn) {
                    int nz = 0;
                    for(int v : argb) {
                        if(v != 0) nz++;
                    }
                    if(nz > 0) nonZeroFrames.incrementAndGet();
                    if(fn == 60 || fn == 180) {
                        System.out.println("cb fn=" + fn + " w=" + w + " h=" + h
                            + " p0=" + Integer.toHexString(argb[0]) + " nonzero=" + nz);
                    }
                    frames.incrementAndGet();
                }
                public void clearFrame() {}
                public void render(int[] e, int a, int b, boolean c, int[] s, int d, int f, boolean g) {}
                public void reset() {}
                public void setFullscreenMode(int m, int w, int h) {}
            });
        }

        MesenCore.loadRom("roms/MetalMax.nes", null);
        MesenCore.resume();
        Thread.sleep(3000);
        MesenCore.pause();

        if(!useCallback) {
            int w = MesenVideo.getFrameWidth();
            int h = MesenVideo.getFrameHeight();
            if(w > 0 && h > 0) {
                int[] px = new int[w * h];
                MesenVideo.copyFrameBuffer(px);
                int nz = 0;
                for(int v : px) {
                    if(v != 0) nz++;
                }
                System.out.println("poll w=" + w + " h=" + h
                    + " p0=" + Integer.toHexString(px[0]) + " nonzero=" + nz);
            } else {
                System.out.println("poll w=0 h=0");
            }
        }

        System.out.println("mode=" + (useCallback ? "callback" : "polling")
            + " frames=" + frames.get() + " nonZeroFrames=" + nonZeroFrames.get());
        MesenCore.stop();
        MesenCore.release();
        System.exit(0);
    }
}
