import mesen.MesenCore;
import mesen.NotificationListener;
import mesen.AudioDevice;
import mesen.KeyManager;
import mesen.MouseManager;
import mesen.RenderingDevice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Smoke test for the Java interface callbacks (all five Core/Shared/Interfaces). */
public class CallbackSmokeTest
{
    public static void main(String[] args) throws Exception
    {
        // 最小 NROM：开启方波通道发声并循环
        byte[] prg = new byte[16384];
        int[] code = {
            0xA9, 0x01, 0x8D, 0x15, 0x40,
            0xA9, 0x3F, 0x8D, 0x00, 0x40,
            0xA9, 0x08, 0x8D, 0x01, 0x40,
            0xA9, 0xFD, 0x8D, 0x02, 0x40,
            0xA9, 0x00, 0x8D, 0x03, 0x40,
            0x4C, 0x19, 0x80
        };
        for(int i = 0; i < code.length; i++) prg[i] = (byte)code[i];
        prg[0x3FFA] = 0x00; prg[0x3FFB] = (byte)0x80;
        prg[0x3FFC] = 0x00; prg[0x3FFD] = (byte)0x80;
        prg[0x3FFE] = 0x00; prg[0x3FFF] = (byte)0x80;
        byte[] chr = new byte[8192];
        byte[] header = new byte[16];
        header[0] = 'N'; header[1] = 'E'; header[2] = 'S'; header[3] = 0x1A;
        header[4] = 1; header[5] = 1;
        Path rom = Files.createTempFile("mesen-cb", ".nes");
        byte[] rb = new byte[header.length + prg.length + chr.length];
        System.arraycopy(header, 0, rb, 0, header.length);
        System.arraycopy(prg, 0, rb, header.length, prg.length);
        System.arraycopy(chr, 0, rb, header.length + prg.length, chr.length);
        Files.write(rom, rb);

        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger audioFrames = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        AtomicInteger keyUpdates = new AtomicInteger();

        MesenCore.initDll();
        MesenCore.initializeEmu("./cb-home", true, false, false, true);

        MesenCore.registerNotificationListener(new NotificationListener() {
            public void processNotification(int type) {
                notifications.incrementAndGet();
            }
        });

        MesenCore.registerAudioDevice(new AudioDevice() {
            public void playBuffer(short[] samples, int sampleRate, boolean stereo) { audioFrames.incrementAndGet(); }
            public void stop() {}
            public void pause() {}
            public void processEndOfFrame() {}
            public String getAvailableDevices() { return ""; }
            public void setAudioDevice(String name) {}
        });

        MesenCore.registerRenderingDevice(new RenderingDevice() {
            public void updateFrame(int width, int height, int[] argb, int frameNumber) { frames.incrementAndGet(); }
            public void clearFrame() {}
            public void render(int[] e, int ew, int eh, boolean ed, int[] s, int sw, int sh, boolean sd) {}
            public void reset() {}
            public void setFullscreenMode(int mode, int width, int height) {}
        });

        MesenCore.registerKeyManager(new KeyManager() {
            public void refreshState() {}
            public void updateDevices() { keyUpdates.incrementAndGet(); }
            public boolean isMouseButtonPressed(int button) { return false; }
            public boolean isKeyPressed(int keyCode) { return false; }
            public int[] getPressedKeys() { return new int[0]; }
            public String getKeyName(int keyCode) { return ""; }
            public int getKeyCode(String keyName) { return 0; }
            public boolean setKeyState(int scanCode, boolean state) { return false; }
            public void resetKeyState() {}
            public void setDisabled(boolean disabled) {}
        });

        MesenCore.registerMouseManager(new MouseManager() {
            public int[] getSystemMouseState() { return new int[] {0, 0, 0, 0, 0, 0, 0}; }
            public boolean captureMouse(int x, int y, int w, int h) { return false; }
            public void releaseMouse() {}
            public void setSystemMousePosition(int x, int y) {}
            public void setCursorImage(int cursor) {}
            public double getPixelScale() { return 1.0; }
        });

        MesenCore.loadRom(rom.toString(), null);
        MesenCore.resume();
        Thread.sleep(2000);
        MesenCore.pause();

        // 触发 KeyManager.updateDevices
        for(int i = 0; i < 3; i++) {
            MesenCore.updateInputDevices();
        }

        System.out.println("notifications=" + notifications.get()
            + " audioFrames=" + audioFrames.get()
            + " frames=" + frames.get()
            + " keyUpdates=" + keyUpdates.get());

        boolean ok = notifications.get() > 0 && audioFrames.get() > 0 && frames.get() > 0 && keyUpdates.get() > 0;
        System.out.println(ok ? "CALLBACK TEST PASSED" : "CALLBACK TEST PARTIAL");

        MesenCore.stop();
        MesenCore.release();
        Files.deleteIfExists(rom);
        System.exit(ok ? 0 : 1);
    }
}
