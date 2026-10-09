import mesen.MesenCore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Smoke test for the JNI Lua scripting methods. */
public class LuaSmokeTest
{
    public static void main(String[] args) throws Exception
    {
        // 最小 NROM：进入 0x8000 处死循环
        byte[] prg = new byte[16384];
        int[] code = { 0x4C, 0x00, 0x80 };
        for(int i = 0; i < code.length; i++) prg[i] = (byte)code[i];
        prg[0x3FFC] = 0x00; prg[0x3FFD] = (byte)0x80;
        prg[0x3FFE] = 0x00; prg[0x3FFF] = (byte)0x80;
        byte[] chr = new byte[8192];
        byte[] header = new byte[16];
        header[0] = 'N'; header[1] = 'E'; header[2] = 'S'; header[3] = 0x1A;
        header[4] = 1; header[5] = 1;
        Path rom = Files.createTempFile("mesen-lua", ".nes");
        byte[] rb = new byte[header.length + prg.length + chr.length];
        System.arraycopy(header, 0, rb, 0, header.length);
        System.arraycopy(prg, 0, rb, header.length, prg.length);
        System.arraycopy(chr, 0, rb, header.length + prg.length, chr.length);
        Files.write(rom, rb);

        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, true, true);
        MesenCore.loadRom(rom.toString(), null);

        String src = "emu.log('hello from lua')";
        int id = MesenCore.loadScript("test.lua", "./", src, -1);
        System.out.println("scriptId = " + id);

        Thread.sleep(300);
        String log = MesenCore.getScriptLog(id);
        System.out.println("log = [" + log + "]");

        MesenCore.removeScript(id);

        boolean ok = id >= 0 && log.contains("hello from lua");
        System.out.println(ok ? "LUA TEST PASSED" : "LUA TEST PARTIAL");

        MesenCore.stop();
        MesenCore.release();
        Files.deleteIfExists(rom);
        System.exit(ok ? 0 : 1);
    }
}
