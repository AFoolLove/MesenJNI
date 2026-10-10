package mesen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mesen 无头模式的配置（Java 侧持久化，JSON 格式，依赖 Gson）。
 *
 * 结构：
 * <pre>
 * {
 *   "emulationSpeed": 100, ...,          // 全局仿真/视频/音频
 *   "nes": { ... }                        // NES 平台配置（内联）
 *   // 或者引用外部文件：
 *   "nes": { "type": "file", "path": "./nes.json" }
 * }
 * </pre>
 *
 * 编译/运行时需把 Gson 加进 classpath（lib/gson-2.9.0.jar）。
 *
 * 原生配置方法见 {@link MesenConfig}。
 */
public class MesenSettings
{
    // ---- 全局：仿真配置（setEmulationConfig）------------------------------
    public int emulationSpeed = 100;
    public int turboSpeed = 300;
    public int rewindSpeed = 100;
    public int runAheadFrames = 0;

    // ---- 全局：视频配置（setVideoConfig）----------------------------------
    public int videoFilter = Mesen.VIDEO_FILTER_NONE;
    public int aspectRatio = Mesen.ASPECT_RATIO_NO_STRETCHING;
    public double customAspectRatio = 1.0;
    public int screenRotation = 0;

    // ---- 全局：音频配置（setAudioConfig）----------------------------------
    public boolean enableAudio = true;
    public int masterVolume = 100;   // 默认音量 100
    public int sampleRate = 48000;
    public int audioLatency = 60;

    // ---- NES 平台配置 -----------------------------------------------------
    // 磁盘上既可以是内联对象，也可以是 {"type":"file","path":"..."} 引用。
    public JsonObject nes;

    // 主配置文件所在目录（用于解析相对路径引用，不参与序列化）
    private transient Path baseDir;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
        .create();

    /** 类似 Config.InitializeDefaults()：所有字段重置为默认值（含默认音量）。 */
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

        NesSettings n = new NesSettings();
        n.initializeDefaults();
        nes = n.toJsonObject();
    }

    /** 把当前配置写入 core（全局 + NES）。 */
    public void apply()
    {
        MesenConfig.setEmulationConfig(emulationSpeed, turboSpeed, rewindSpeed, runAheadFrames);
        MesenConfig.setVideoConfig(videoFilter, aspectRatio, customAspectRatio, screenRotation);
        MesenConfig.setAudioConfig(enableAudio, masterVolume, sampleRate, audioLatency);

        getNesSettings().apply();
    }

    // ---- NES 平台配置的读写 -----------------------------------------------

    /** 解析并返回 NES 配置（若 nes 是文件引用，则跟随加载）。 */
    public NesSettings getNesSettings()
    {
        JsonObject o = nes;
        if(o != null && "file".equals(optString(o, "type"))) {
            JsonObject loaded = loadNesFromFile(optString(o, "path"));
            if(loaded != null) {
                o = loaded;
            }
        }
        return NesSettings.fromJson(o);
    }

    /** 设置 NES 配置为内联形式。 */
    public void setNesSettings(NesSettings s)
    {
        nes = (s == null) ? null : s.toJsonObject();
    }

    /** 把 NES 配置导出到单独文件，并在主配置里改为文件引用。 */
    public void exportNes(Path path) throws IOException
    {
        if(path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, GSON.toJson(getNesSettings().toJsonObject()), StandardCharsets.UTF_8);

        JsonObject ref = new JsonObject();
        ref.addProperty("type", "file");
        ref.addProperty("path", path.toString());
        nes = ref;
    }

    private JsonObject loadNesFromFile(String path)
    {
        if(path == null) {
            return null;
        }
        try {
            Path p = Path.of(path);
            if(!p.isAbsolute() && baseDir != null) {
                p = baseDir.resolve(p);
            }
            String json = Files.readString(p, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch(Exception e) {
            return null;
        }
    }

    // ---- 保存 / 加载 ------------------------------------------------------

    /** 保存为 JSON 文件（美化格式）。 */
    public void save(Path path) throws IOException
    {
        if(path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    /** 从 JSON 文件加载；文件不存在、字段缺失或读取失败时保持默认值。 */
    public static MesenSettings load(Path path)
    {
        MesenSettings cfg = new MesenSettings();
        cfg.initializeDefaults();
        if(path == null || !Files.exists(path)) {
            return cfg;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            cfg.baseDir = path.toAbsolutePath().getParent();

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

            cfg.nes = (o.has("nes") && o.get("nes").isJsonObject()) ? o.getAsJsonObject("nes") : cfg.nes;
        } catch(Exception e) {
            // 忽略，保持默认值
        }
        return cfg;
    }

    /** 生成一份默认配置文件（先 initializeDefaults 再落盘）。 */
    public static void createDefaultFile(Path path) throws IOException
    {
        MesenSettings cfg = new MesenSettings();
        cfg.initializeDefaults();
        cfg.save(path);
    }

    // ---- 工具 -------------------------------------------------------------

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

    private static String optString(JsonObject o, String key)
    {
        return (o.has(key) && o.get(key).isJsonPrimitive()) ? o.get(key).getAsString() : null;
    }
}
