import mesen.AudioPlayerConfig;
import mesen.CheatCode;
import mesen.GameConfig;
import mesen.InternalCheatCode;
import mesen.Mesen;
import mesen.MesenCore;
import mesen.PreferencesConfig;
import mesen.ShortcutKeyInfo;
import mesen.ShaderConfig;
import mesen.ShaderParam;
import mesen.ShaderParamDefinition;

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

        // ---- 配置 ----
        System.out.println("== 配置 ==");
        GameConfig gameConfig = new GameConfig();
        gameConfig.dipSwitches = 3;
        gameConfig.overrideOverscan = true;
        gameConfig.overscanLeft = 8;
        MesenCore.setGameConfig(gameConfig);
        check(true, "setGameConfig() 无异常");

        PreferencesConfig preferences = new PreferencesConfig();
        preferences.ShowFps = true;
        preferences.AllowBackgroundInput = true;
        preferences.RewindBufferSize = 600;
        MesenCore.setPreferences(preferences);
        check(true, "setPreferences() 无异常");

        AudioPlayerConfig audioPlayerConfig = new AudioPlayerConfig();
        audioPlayerConfig.volume = 50;
        audioPlayerConfig.repeat = true;
        MesenCore.setAudioPlayerConfig(audioPlayerConfig);
        check(true, "setAudioPlayerConfig() 无异常");

        // 复位，并确认改完配置后游戏还能正常跑
        MesenCore.setPreferences(new PreferencesConfig());
        MesenCore.setGameConfig(new GameConfig());
        MesenCore.setAudioPlayerConfig(new AudioPlayerConfig());
        MesenCore.resume();
        Thread.sleep(300);
        int[] frame = new int[mesen.MesenVideo.getFrameWidth() * mesen.MesenVideo.getFrameHeight()];
        mesen.MesenVideo.copyFrameBuffer(frame);
        int nonBlack = 0;
        for(int pixel : frame) {
            if((pixel & 0xFFFFFF) != 0) nonBlack++;
        }
        check(nonBlack > 0, "改完配置后仍在正常渲染 -> " + nonBlack + " 个非黑像素");

        // ---- 快捷键 ----
        System.out.println("== 快捷键 ==");
        MesenCore.setShortcutKeys(new ShortcutKeyInfo[] {
            new ShortcutKeyInfo(Mesen.SHORTCUT_PAUSE, 0x20),
            new ShortcutKeyInfo(Mesen.SHORTCUT_FAST_FORWARD, 0x09)
        });
        check(true, "setShortcutKeys() 无异常");

        boolean allowed = MesenCore.isShortcutAllowed(Mesen.SHORTCUT_RESET, 0);
        check(true, "isShortcutAllowed(SHORTCUT_RESET) -> " + allowed);

        // executeShortcut 走完整通知链路：用 PAUSE 验证真的生效
        MesenCore.resume();
        Thread.sleep(250);
        check(!MesenCore.isPaused(), "执行前正在运行");
        MesenCore.executeShortcut(Mesen.SHORTCUT_PAUSE, 0);
        Thread.sleep(250);
        check(MesenCore.isPaused(), "executeShortcut(SHORTCUT_PAUSE) 真的暂停了");
        MesenCore.executeShortcut(Mesen.SHORTCUT_PAUSE, 0);
        Thread.sleep(250);
        check(!MesenCore.isPaused(), "再执行一次恢复运行");

        // ---- 着色器 ----
        System.out.println("== 着色器 ==");
        boolean shaderSupport = MesenCore.checkShaderSupport();
        check(true, "checkShaderSupport() -> " + shaderSupport);

        MesenCore.setShaderConfig(new ShaderConfig());
        check(true, "setShaderConfig(空配置) 无异常");

        ShaderConfig withParams = new ShaderConfig();
        withParams.configVersion = 1;
        withParams.shaderFile = "temp/none.slangp";
        withParams.params = new ShaderParam[] { new ShaderParam("SCALE", 2.0) };
        MesenCore.setShaderConfig(withParams);
        check(true, "setShaderConfig(带参数) 无异常");

        ShaderParamDefinition[] definitions = MesenCore.getShaderParams("temp/none.slangp");
        check(definitions != null, "getShaderParams(不存在的文件) -> "
            + (definitions == null ? "null" : definitions.length + " 个参数"));

        MesenCore.setShaderConfig(new ShaderConfig());
        check(true, "setShaderConfig() 已复位");

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
