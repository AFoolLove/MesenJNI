package com.mesen;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Mesen 无头模式的配置（Java 侧持久化，纯 JDK 实现，无第三方依赖）。
 *
 * 使用方式：
 * <pre>
 *   MesenConfig cfg = MesenConfig.load("mesen-config.properties"); // 不存在则用默认值
 *   cfg.apply();                                                    // 写入 MesenCore
 * </pre>
 *
 * 生成默认配置文件：
 * <pre>
 *   MesenConfig.createDefaultFile("mesen-config.properties");
 * </pre>
 */
public class MesenConfig
{
    // ---- 仿真配置（setEmulationConfig）-----------------------------------
    public int emulationSpeed = 100;
    public int turboSpeed = 300;
    public int rewindSpeed = 100;
    public int runAheadFrames = 0;

    // ---- 视频配置（setVideoConfig）---------------------------------------
    public int videoFilter = Mesen.VIDEO_FILTER_NONE;
    public int aspectRatio = Mesen.ASPECT_RATIO_NO_STRETCHING;
    public double customAspectRatio = 1.0;
    public int screenRotation = 0;

    // ---- 音频配置（setAudioConfig）---------------------------------------
    public boolean enableAudio = true;
    public int masterVolume = 100;   // 默认音量 100
    public int sampleRate = 48000;
    public int audioLatency = 60;

    /**
     * 类似 Config.InitializeDefaults()：把所有字段重置为默认值
     * （包括恢复默认音量 masterVolume = 100）。
     */
    public void initializeDefaults()
    {
        emulationSpeed = 100;
        turboSpeed = 300;
        rewindSpeed = 100;
        runAheadFrames = 0;

        videoFilter = Mesen.VIDEO_FILTER_NONE;
        aspectRatio = Mesen.ASPECT_RATIO_NO_STRETCHING;
        customAspectRatio = 1.0;
        screenRotation = 0;

        enableAudio = true;
        masterVolume = 100;   // 恢复默认音量
        sampleRate = 48000;
        audioLatency = 60;
    }

    /** 把当前配置写入 MesenCore（通过 JNI setter）。 */
    public void apply()
    {
        MesenCore.setEmulationConfig(emulationSpeed, turboSpeed, rewindSpeed, runAheadFrames);
        MesenCore.setVideoConfig(videoFilter, aspectRatio, customAspectRatio, screenRotation);
        MesenCore.setAudioConfig(enableAudio, masterVolume, sampleRate, audioLatency);
    }

    /** 保存到文件（Properties 键值格式）。 */
    public void save(Path path) throws IOException
    {
        Properties p = toProperties();
        if(path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try(OutputStream out = Files.newOutputStream(path)) {
            p.store(out, "Mesen headless config");
        }
    }

    /** 从文件加载；文件不存在或读取失败时保持默认值。 */
    public static MesenConfig load(Path path)
    {
        MesenConfig cfg = new MesenConfig();
        cfg.initializeDefaults();
        if(path == null || !Files.exists(path)) {
            return cfg;
        }
        try(InputStream in = Files.newInputStream(path)) {
            Properties p = new Properties();
            p.load(in);
            cfg.fromProperties(p);
        } catch(IOException e) {
            // 忽略，保持默认值
        }
        return cfg;
    }

    /** 生成一份默认配置文件（先 initializeDefaults 再落盘）。 */
    public static void createDefaultFile(Path path) throws IOException
    {
        MesenConfig cfg = new MesenConfig();
        cfg.initializeDefaults();
        cfg.save(path);
    }

    private Properties toProperties()
    {
        Properties p = new Properties();
        p.setProperty("emulationSpeed", String.valueOf(emulationSpeed));
        p.setProperty("turboSpeed", String.valueOf(turboSpeed));
        p.setProperty("rewindSpeed", String.valueOf(rewindSpeed));
        p.setProperty("runAheadFrames", String.valueOf(runAheadFrames));

        p.setProperty("videoFilter", String.valueOf(videoFilter));
        p.setProperty("aspectRatio", String.valueOf(aspectRatio));
        p.setProperty("customAspectRatio", String.valueOf(customAspectRatio));
        p.setProperty("screenRotation", String.valueOf(screenRotation));

        p.setProperty("enableAudio", String.valueOf(enableAudio));
        p.setProperty("masterVolume", String.valueOf(masterVolume));
        p.setProperty("sampleRate", String.valueOf(sampleRate));
        p.setProperty("audioLatency", String.valueOf(audioLatency));
        return p;
    }

    private void fromProperties(Properties p)
    {
        emulationSpeed = intValue(p, "emulationSpeed", emulationSpeed);
        turboSpeed = intValue(p, "turboSpeed", turboSpeed);
        rewindSpeed = intValue(p, "rewindSpeed", rewindSpeed);
        runAheadFrames = intValue(p, "runAheadFrames", runAheadFrames);

        videoFilter = intValue(p, "videoFilter", videoFilter);
        aspectRatio = intValue(p, "aspectRatio", aspectRatio);
        customAspectRatio = doubleValue(p, "customAspectRatio", customAspectRatio);
        screenRotation = intValue(p, "screenRotation", screenRotation);

        enableAudio = booleanValue(p, "enableAudio", enableAudio);
        masterVolume = intValue(p, "masterVolume", masterVolume);
        sampleRate = intValue(p, "sampleRate", sampleRate);
        audioLatency = intValue(p, "audioLatency", audioLatency);
    }

    private static int intValue(Properties p, String key, int def)
    {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)));
        } catch(NumberFormatException e) {
            return def;
        }
    }

    private static double doubleValue(Properties p, String key, double def)
    {
        try {
            return Double.parseDouble(p.getProperty(key, String.valueOf(def)));
        } catch(NumberFormatException e) {
            return def;
        }
    }

    private static boolean booleanValue(Properties p, String key, boolean def)
    {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }
}
