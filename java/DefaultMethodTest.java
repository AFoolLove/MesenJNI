import mesen.MesenCore;
import mesen.RenderingDevice;
import mesen.KeyManager;
import mesen.MouseManager;
import mesen.AudioDevice;

/** 验证默认实现：只覆盖关心的方法，其余用默认空实现。 */
public class DefaultMethodTest
{
    public static void main(String[] args)
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, true, true);

        // RenderingDevice：只实现 updateFrame，其余用默认
        MesenCore.registerRenderingDevice(new RenderingDevice() {
            public void updateFrame(int w, int h, int[] argb, int fn) {
                System.out.println("updateFrame " + w + "x" + h);
            }
        });

        // KeyManager / MouseManager：全部用默认
        MesenCore.registerKeyManager(new KeyManager() {});
        MesenCore.registerMouseManager(new MouseManager() {});

        // AudioDevice：只实现 playBuffer，其余用默认
        MesenCore.registerAudioDevice(new AudioDevice() {
            public void playBuffer(short[] s, int n, int rate, boolean stereo) {}
        });

        System.out.println("DEFAULT METHOD TEST PASSED");
        MesenCore.release();
        System.exit(0);
    }
}
