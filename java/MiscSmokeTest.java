import mesen.CheatCode;
import mesen.InternalCheatCode;
import mesen.Mesen;
import mesen.MesenCore;

/** 作弊码 / 存档预览 / 压缩包列表 / 杂项接口回归测试。 */
public class MiscSmokeTest
{
    private static int failures = 0;

    private static void check(boolean ok, String message)
    {
        System.out.println((ok ? "  [OK] " : "  [FAIL] ") + message);
        if(!ok) {
            failures++;
        }
    }

    private static boolean isPng(byte[] data)
    {
        return data != null && data.length > 8
            && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }

    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);
        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom()");
        MesenCore.resume();
        Thread.sleep(300);

        // ---- 作弊码 ----
        System.out.println("== 作弊码 ==");
        MesenCore.clearCheats();
        check(true, "clearCheats() 无异常");

        CheatCode genie = new CheatCode(Mesen.CHEAT_NES_GAME_GENIE, "SXIOPO");
        InternalCheatCode converted = MesenCore.getConvertedCheat(genie);
        check(converted != null, "getConvertedCheat(NES Game Genie) -> " + converted);

        CheatCode bogus = new CheatCode(Mesen.CHEAT_NES_GAME_GENIE, "!!!!!!");
        check(MesenCore.getConvertedCheat(bogus) == null, "非法作弊码返回 null");

        MesenCore.setCheats(new CheatCode[] { genie });
        check(true, "setCheats() 无异常");
        MesenCore.clearCheats();
        check(true, "clearCheats() 再次无异常");

        // ---- 存档预览 ----
        System.out.println("== 存档预览 ==");
        MesenCore.saveStateFile("temp/preview.mst");
        byte[] preview = MesenCore.getSaveStatePreview("temp/preview.mst");
        check(isPng(preview), "getSaveStatePreview() -> "
            + (preview == null ? "null" : preview.length + " 字节" + (isPng(preview) ? " (PNG)" : " (非 PNG)")));

        byte[] missing = MesenCore.getSaveStatePreview("temp/does-not-exist.mst");
        check(missing != null && missing.length == 0, "不存在的存档返回空数组");

        // ---- 压缩包 ROM 列表 ----
        System.out.println("== 压缩包 ROM 列表 ==");
        String[] roms = MesenCore.getArchiveRomList("temp/roms.zip");
        StringBuilder names = new StringBuilder();
        if(roms != null) {
            for(String name : roms) {
                names.append(name).append(' ');
            }
        }
        check(roms != null && roms.length > 0, "getArchiveRomList(temp/roms.zip) -> ["
            + names.toString().trim() + "]");

        // ---- 杂项（这些在 NES 上多为空操作，重点是不崩）--------------------
        System.out.println("== 杂项 ==");
        MesenCore.setFullscreenMode(0, 0, 0, Mesen.FULLSCREEN_DISABLED);
        check(true, "setFullscreenMode(DISABLED) 无异常");

        MesenCore.inputBarcode(1234567890123L, 13);
        check(true, "inputBarcode() 无异常");

        MesenCore.processTapeRecorderAction(Mesen.TAPE_STOP_RECORD, "");
        check(true, "processTapeRecorderAction() 无异常");

        MesenCore.processAudioPlayerAction(Mesen.AUDIO_PLAYER_NEXT_TRACK, 2);
        check(true, "processAudioPlayerAction() 无异常");

        MesenCore.resetLagCounter();
        check(true, "resetLagCounter() 无异常");

        MesenCore.stop();
        MesenCore.release();

        if(failures == 0) {
            System.out.println("MISC SMOKE TEST PASSED");
        } else {
            System.out.println("MISC SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
