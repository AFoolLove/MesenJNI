package com.mesen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mesen 无头模式的配置（Java 侧持久化，JSON 格式，依赖 Gson）。
 *
 * 使用方式：
 * <pre>
 *   MesenConfig cfg = MesenConfig.load("mesen-config.json"); // 不存在则用默认值
 *   cfg.apply();                                             // 写入 MesenCore
 * </pre>
 *
 * 生成默认配置文件：
 * <pre>
 *   MesenConfig.createDefaultFile("mesen-config.json");
 * </pre>
 *
 * 编译/运行时需把 Gson 加进 classpath，例如：
 * <pre>
 *   javac -cp lib/gson-2.9.0.jar -d out com/mesen/*.java
 *   java  -cp "out;lib/gson-2.9.0.jar" ...
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    /** 保存为 JSON 文件（美化格式）。 */
    public void save(Path path) throws IOException
    {
        if(path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    /** 从 JSON 文件加载；文件不存在、字段缺失或读取失败时保持默认值。 */
    public static MesenConfig load(Path path)
    {
        MesenConfig cfg = new MesenConfig();
        cfg.initializeDefaults();
        if(path == null || !Files.exists(path)) {
            return cfg;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();

            cfg.emulationSpeed = getInt(o, "emulationSpeed", cfg.emulationSpeed);
            cfg.turboSpeed = getInt(o, "turboSpeed", cfg.turboSpeed);
            cfg.rewindSpeed = getInt(o, "rewindSpeed", cfg.rewindSpeed);
            cfg.runAheadFrames = getInt(o, "runAheadFrames", cfg.runAheadFrames);

            cfg.videoFilter = getInt(o, "videoFilter", cfg.videoFilter);
            cfg.aspectRatio = getInt(o, "aspectRatio", cfg.aspectRatio);
            cfg.customAspectRatio = getDouble(o, "customAspectRatio", cfg.customAspectRatio);
            cfg.screenRotation = getInt(o, "screenRotation", cfg.screenRotation);

            cfg.enableAudio = getBool(o, "enableAudio", cfg.enableAudio);
            cfg.masterVolume = getInt(o, "masterVolume", cfg.masterVolume);
            cfg.sampleRate = getInt(o, "sampleRate", cfg.sampleRate);
            cfg.audioLatency = getInt(o, "audioLatency", cfg.audioLatency);
        } catch(Exception e) {
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

    private static int getInt(JsonObject o, String key, int def)
    {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject o, String key, double def)
    {
        return o.has(key) ? o.get(key).getAsDouble() : def;
    }

    private static boolean getBool(JsonObject o, String key, boolean def)
    {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }
}
