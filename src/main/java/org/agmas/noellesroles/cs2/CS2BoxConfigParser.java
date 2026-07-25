package org.agmas.noellesroles.cs2;

import com.google.gson.*;
import org.agmas.noellesroles.Noellesroles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CS2 箱子配置解析器
 * <p>
 * 从指定目录加载所有 .json 箱子配置文件。
 * </p>
 */
public class CS2BoxConfigParser {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 从目录加载所有箱子配置
     *
     * @param dir 箱子配置目录（如 CS2_box/）
     * @return 解析后的箱子配置列表
     */
    public static List<CS2BoxConfig> loadAll(Path dir) {
        List<CS2BoxConfig> configs = new ArrayList<>();
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                Noellesroles.LOGGER.info("[CS2Box] Created box config directory: {}", dir);
            } catch (IOException e) {
                Noellesroles.LOGGER.error("[CS2Box] Failed to create box config directory: {}", dir, e);
            }
            return configs;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            CS2BoxConfig config = parse(p);
                            if (config != null) {
                                configs.add(config);
                                Noellesroles.LOGGER.info("[CS2Box] Loaded box config: {} ({})",
                                        config.getBoxId(), config.getBoxName());
                            }
                        } catch (Exception e) {
                            Noellesroles.LOGGER.error("[CS2Box] Failed to parse box config: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[CS2Box] Failed to list box config directory: {}", dir, e);
        }

        return configs;
    }

    /**
     * 解析单个箱子配置文件
     */
    public static CS2BoxConfig parse(Path file) {
        try {
            String json = Files.readString(file);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            String fileName = file.getFileName().toString();
            String boxId = fileName.substring(0, fileName.lastIndexOf('.'));

            String boxName = getStringOrDefault(obj, "box_name", boxId);
            String keyName = getStringOrDefault(obj, "key_name", "");

            double common = getDoubleOrDefault(obj, "common", 0.0);
            double uncommon = getDoubleOrDefault(obj, "uncommon", 0.0);
            double rare = getDoubleOrDefault(obj, "rare", 0.0);
            double epic = getDoubleOrDefault(obj, "epic", 0.0);
            double legendary = getDoubleOrDefault(obj, "legendary", 0.0);
            double unbelievable = getDoubleOrDefault(obj, "unbelievable", 0.0);

            List<String> commonSkins = getStringList(obj, "common_skins");
            List<String> uncommonSkins = getStringList(obj, "uncommon_skins");
            List<String> rareSkins = getStringList(obj, "rare_skins");
            List<String> epicSkins = getStringList(obj, "epic_skins");
            List<String> legendarySkins = getStringList(obj, "legendary_skins");
            List<String> unbelievableSkins = getStringList(obj, "unbelievable_skins");

            // 验证概率总和
            double sum = common + uncommon + rare + epic + legendary + unbelievable;
            if (sum < 0.999 || sum > 1.001) {
                Noellesroles.LOGGER.error("[CS2Box] Box '{}' probability sum ({}) is not equal to 1.0", boxId, sum);
                return null;
            }

            return new CS2BoxConfig(boxId, boxName, keyName,
                    common, uncommon, rare, epic, legendary, unbelievable,
                    commonSkins, uncommonSkins, rareSkins, epicSkins,
                    legendarySkins, unbelievableSkins);

        } catch (Exception e) {
            Noellesroles.LOGGER.error("[CS2Box] Failed to parse box config file: {}", file, e);
            return null;
        }
    }

    /**
     * 从 JSON 字符串解析箱子配置（客户端网络接收用）
     *
     * @param json  JSON 字符串
     * @param boxId 箱子 ID
     * @return 解析后的箱子配置，失败返回 null
     */
    public static CS2BoxConfig parseFromJson(String json, String boxId) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String boxName = getStringOrDefault(obj, "box_name", boxId);
            String keyName = getStringOrDefault(obj, "key_name", "");
            double common = getDoubleOrDefault(obj, "common", 0.0);
            double uncommon = getDoubleOrDefault(obj, "uncommon", 0.0);
            double rare = getDoubleOrDefault(obj, "rare", 0.0);
            double epic = getDoubleOrDefault(obj, "epic", 0.0);
            double legendary = getDoubleOrDefault(obj, "legendary", 0.0);
            double unbelievable = getDoubleOrDefault(obj, "unbelievable", 0.0);
            List<String> commonSkins = getStringList(obj, "common_skins");
            List<String> uncommonSkins = getStringList(obj, "uncommon_skins");
            List<String> rareSkins = getStringList(obj, "rare_skins");
            List<String> epicSkins = getStringList(obj, "epic_skins");
            List<String> legendarySkins = getStringList(obj, "legendary_skins");
            List<String> unbelievableSkins = getStringList(obj, "unbelievable_skins");
            return new CS2BoxConfig(boxId, boxName, keyName,
                    common, uncommon, rare, epic, legendary, unbelievable,
                    commonSkins, uncommonSkins, rareSkins, epicSkins,
                    legendarySkins, unbelievableSkins);
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[CS2Box] Failed to parse box config from JSON: {}", boxId, e);
            return null;
        }
    }

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    private static double getDoubleOrDefault(JsonObject obj, String key, double defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsDouble();
        }
        return defaultValue;
    }

    private static List<String> getStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (JsonElement elem : arr) {
                if (elem.isJsonPrimitive()) {
                    list.add(elem.getAsString());
                }
            }
        }
        return list;
    }
}
