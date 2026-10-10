package mesen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NES 平台的配置（区域、控制器映射、调色板、常用开关）。
 */
public class NesSettings
{
    // ---- 区域 -------------------------------------------------------------
    public int region = Mesen.REGION_AUTO;

    // ---- 控制器 -----------------------------------------------------------
    public int controllerType = Mesen.CONTROLLER_NES;
    /** 按钮名 -> 键码（如 "Start" -> 10, "A" -> 5）。 */
    public Map<String, Integer> controllerKeys = new LinkedHashMap<>();

    // ---- 调色板 -----------------------------------------------------------
    /** 512 色自定义调色板（0xRRGGBB），null = 使用默认调色板。 */
    public int[] palette = null;
    public boolean fullColorPalette = false;
    public boolean useCustomVsPalette = false;

    // ---- 其它常用开关 -----------------------------------------------------
    public boolean removeSpriteLimit = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 重置为默认值。 */
    public void initializeDefaults()
    {
        region = Mesen.REGION_AUTO;
        controllerType = Mesen.CONTROLLER_NES;
        controllerKeys = new LinkedHashMap<>();
        controllerKeys.put("Start", 10);
        controllerKeys.put("A", 5);
        palette = null;
        fullColorPalette = false;
        useCustomVsPalette = false;
        removeSpriteLimit = false;
    }

    /** 应用到 MesenCore（通过 JNI）。 */
    public void apply()
    {
        MesenConfig.setNesRegion(region);
        MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, controllerType);

        for(Map.Entry<String, Integer> e : controllerKeys.entrySet()) {
            int button = Mesen.buttonId(e.getKey());
            if(button >= 0 && e.getValue() != null) {
                MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, button, e.getValue());
            }
        }

        if(palette != null && palette.length > 0) {
            MesenConfig.setNesCustomPalette(palette);
        }
        MesenConfig.setNesFullColorPalette(fullColorPalette);
        MesenConfig.setNesUseCustomVsPalette(useCustomVsPalette);
        MesenConfig.setNesRemoveSpriteLimit(removeSpriteLimit);
    }

    /** 转成 JsonObject（用于内联写入主配置文件）。 */
    public JsonObject toJsonObject()
    {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    /** 从 JsonObject 读取；缺失字段保持默认值。 */
    public static NesSettings fromJson(JsonObject o)
    {
        NesSettings s = new NesSettings();
        s.initializeDefaults();
        if(o == null) {
            return s;
        }

        if(o.has("region")) s.region = o.get("region").getAsInt();
        if(o.has("controllerType")) s.controllerType = o.get("controllerType").getAsInt();

        if(o.has("controllerKeys") && o.get("controllerKeys").isJsonObject()) {
            JsonObject keys = o.getAsJsonObject("controllerKeys");
            for(String name : keys.keySet()) {
                s.controllerKeys.put(name, keys.get(name).getAsInt());
            }
        }

        if(o.has("palette") && o.get("palette").isJsonArray()) {
            var arr = o.getAsJsonArray("palette");
            int[] p = new int[arr.size()];
            for(int i = 0; i < arr.size(); i++) {
                p[i] = arr.get(i).getAsInt();
            }
            s.palette = p;
        }

        if(o.has("fullColorPalette")) s.fullColorPalette = o.get("fullColorPalette").getAsBoolean();
        if(o.has("useCustomVsPalette")) s.useCustomVsPalette = o.get("useCustomVsPalette").getAsBoolean();
        if(o.has("removeSpriteLimit")) s.removeSpriteLimit = o.get("removeSpriteLimit").getAsBoolean();
        return s;
    }
}
