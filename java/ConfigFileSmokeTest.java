import com.mesen.MesenConfig;
import com.mesen.MesenCore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Smoke test for the Java-side MesenConfig class. */
public class ConfigFileSmokeTest
{
    public static void main(String[] args) throws Exception
    {
        Path file = Files.createTempFile("mesen-config", ".properties");

        // 1) 生成默认配置文件
        MesenConfig.createDefaultFile(file);

        // 2) 读回
        MesenConfig cfg = MesenConfig.load(file);

        // 3) 校验默认值（重点是默认音量 = 100）
        if(cfg.masterVolume != 100) {
            throw new AssertionError("默认音量应为 100，实际 " + cfg.masterVolume);
        }
        if(!cfg.enableAudio || cfg.sampleRate != 48000) {
            throw new AssertionError("默认音频配置不对");
        }

        // 4) 应用到 core（含恢复默认音量）
        MesenCore.initDll();
        MesenCore.initializeEmu("./cfg-home", true, false, false, true);
        cfg.apply();

        System.out.println("masterVolume = " + cfg.masterVolume);
        System.out.println("sampleRate = " + cfg.sampleRate);
        System.out.println("CONFIG FILE TEST PASSED");

        MesenCore.release();
        Files.deleteIfExists(file);
    }
}
