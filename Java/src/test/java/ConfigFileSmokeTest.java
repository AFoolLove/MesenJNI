import mesen.*;
import mesen.*;
import mesen.NesSettings;

import java.nio.file.Files;
import java.nio.file.Path;

/** Smoke test for the Java-side MesenSettings (global + NES section + file reference). */
public class ConfigFileSmokeTest
{
    public static void main(String[] args) throws Exception
    {
        Path dir = Files.createTempDirectory("mesen-cfg-test");
        Path file = dir.resolve("mesen.json");

        // 1) 生成默认配置文件
        MesenSettings.createDefaultFile(file);
        System.out.println("--- 默认配置 JSON ---");
        System.out.println(Files.readString(file));

        // 2) 读回并校验默认值
        MesenSettings cfg = MesenSettings.load(file);
        if(cfg.masterVolume != 100) {
            throw new AssertionError("默认音量应为 100");
        }
        NesSettings nes = cfg.getNesSettings();
        if(nes.region != 0 || nes.controllerType != 10) {
            throw new AssertionError("NES 默认配置不对: region=" + nes.region + " controllerType=" + nes.controllerType);
        }
        System.out.println("masterVolume = " + cfg.masterVolume + ", nes.region = " + nes.region + ", nes.controllerType = " + nes.controllerType);

        // 3) 测试文件引用：把 NES 配置导出到单独文件
        Path nesFile = dir.resolve("nes.json");
        cfg.exportNes(nesFile);
        cfg.save(file);
        System.out.println("--- 引用后的主配置 JSON ---");
        System.out.println(Files.readString(file));

        // 重新加载，跟随文件引用解析
        MesenSettings cfg2 = MesenSettings.load(file);
        NesSettings nes2 = cfg2.getNesSettings();
        if(nes2.region != 0 || nes2.controllerType != 10) {
            throw new AssertionError("文件引用解析失败");
        }
        System.out.println("文件引用解析成功: nes.region = " + nes2.region + ", keys = " + nes2.controllerKeys);

        // 4) 应用到 core（含默认音量 + NES 配置）
        MesenCore.initDll();
        MesenCore.initializeEmu(dir.resolve("home").toString(), true, false, false, true);
        cfg2.apply();

        System.out.println("CONFIG FILE TEST PASSED");
        MesenCore.release();

        // 清理
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch(Exception ignored) {}
        });
    }
}
