import mesen.MesenCore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies that the NES audio actually produces non-zero samples (tone ROM). */
public class ToneSmokeTest
{
    public static void main(String[] args) throws Exception
    {
        // Minimal NROM that enables the square-1 channel and loops.
        byte[] prg = new byte[16384];
        int[] code = {
            0xA9, 0x01, 0x8D, 0x15, 0x40, // LDA #$01, STA $4015 (enable square1)
            0xA9, 0x3F, 0x8D, 0x00, 0x40, // LDA #$3F, STA $4000 (volume 15, duty)
            0xA9, 0x08, 0x8D, 0x01, 0x40, // LDA #$08, STA $4001 (disable sweep)
            0xA9, 0xFD, 0x8D, 0x02, 0x40, // LDA #$FD, STA $4002 (timer low)
            0xA9, 0x00, 0x8D, 0x03, 0x40, // LDA #$00, STA $4003 (timer high)
            0x4C, 0x19, 0x80              // JMP $8019 (loop)
        };
        for(int i = 0; i < code.length; i++) prg[i] = (byte)code[i];
        prg[0x3FFA] = 0x00; prg[0x3FFB] = (byte)0x80; // NMI
        prg[0x3FFC] = 0x00; prg[0x3FFD] = (byte)0x80; // Reset -> $8000
        prg[0x3FFE] = 0x00; prg[0x3FFF] = (byte)0x80; // IRQ

        byte[] chr = new byte[8192];
        byte[] header = new byte[16];
        header[0] = 'N'; header[1] = 'E'; header[2] = 'S'; header[3] = 0x1A;
        header[4] = 1; header[5] = 1;

        Path rom = Files.createTempFile("mesen-tone-test", ".nes");
        byte[] romBytes = new byte[header.length + prg.length + chr.length];
        System.arraycopy(header, 0, romBytes, 0, header.length);
        System.arraycopy(prg, 0, romBytes, header.length, prg.length);
        System.arraycopy(chr, 0, romBytes, header.length + prg.length, chr.length);
        Files.write(rom, romBytes);

        MesenCore.initDll();
        MesenCore.initializeEmu("./tone-home", true, false, false, true);
        MesenCore.loadRom(rom.toString(), null);
        MesenCore.resume();

        Thread.sleep(2000);

        short[] buf = new short[48000];
        int n = MesenCore.readAudioSamples(buf);
        int peak = 0;
        for(int i = 0; i < n; i++) {
            int s = buf[i] < 0 ? -buf[i] : buf[i];
            if(s > peak) peak = s;
        }

        System.out.println("samplesRead = " + n);
        System.out.println("peak = " + peak);
        System.out.println(peak > 100 ? "TONE TEST PASSED" : "TONE TEST FAILED (still silent)");

        MesenCore.stop();
        MesenCore.release();
        Files.deleteIfExists(rom);
    }
}
