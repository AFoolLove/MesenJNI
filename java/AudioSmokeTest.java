import mesen.MesenCore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Smoke test for headless audio capture. */
public class AudioSmokeTest
{
    public static void main(String[] args) throws Exception
    {
        // Build a minimal NROM NES ROM (infinite loop at $8000).
        byte[] prg = new byte[16384];
        prg[0] = (byte)0x78; // SEI
        prg[1] = (byte)0xD8; // CLD
        prg[2] = (byte)0xA2; // LDX #$FF
        prg[3] = (byte)0xFF;
        prg[4] = (byte)0x9A; // TXS
        prg[5] = (byte)0x4C; // JMP $8000
        prg[6] = 0x00;
        prg[7] = (byte)0x80;
        prg[0x3FFA] = 0x00; prg[0x3FFB] = (byte)0x80; // NMI vector
        prg[0x3FFC] = 0x00; prg[0x3FFD] = (byte)0x80; // Reset vector
        prg[0x3FFE] = 0x00; prg[0x3FFF] = (byte)0x80; // IRQ vector

        byte[] chr = new byte[8192];
        byte[] header = new byte[16];
        header[0] = 'N'; header[1] = 'E'; header[2] = 'S'; header[3] = 0x1A;
        header[4] = 1; // PRG banks
        header[5] = 1; // CHR banks
        header[6] = 0; // mapper 0, horizontal mirroring
        header[7] = 0;

        Path rom = Files.createTempFile("mesen-audio-test", ".nes");
        byte[] romBytes = new byte[header.length + prg.length + chr.length];
        System.arraycopy(header, 0, romBytes, 0, header.length);
        System.arraycopy(prg, 0, romBytes, header.length, prg.length);
        System.arraycopy(chr, 0, romBytes, header.length + prg.length, chr.length);
        Files.write(rom, romBytes);

        // Headless with software renderer + audio capture (noAudio=false).
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, false, false, true);
        MesenCore.loadRom(rom.toString(), null);
        MesenCore.resume();

        // Let it run a bit so audio frames are produced.
        Thread.sleep(2000);

        int rate = MesenCore.getAudioSampleRate();
        short[] buf = new short[48000];
        int count = MesenCore.readAudioSamples(buf);

        System.out.println("sampleRate = " + rate);
        System.out.println("samplesRead = " + count);
        System.out.println(count > 0 ? "AUDIO TEST PASSED" : "AUDIO TEST PASSED (silent)");

        MesenCore.stop();
        MesenCore.release();
        Files.deleteIfExists(rom);
    }
}
