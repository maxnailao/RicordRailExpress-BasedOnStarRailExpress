/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 地图介绍 / 轮抽 / 投票界面展示数据（{@link MapDisplayInfo}）的服务端来源。
 *
 * <p>只收集投票配置（{@code <world>/train_vote_maps.json}）里登记过的地图；
 * 管理员请求 {@code includeAll} 时才临时收集磁盘上的全部地图（临时构建、不写缓存）。
 * 缓存在每次 {@link MapManager#loadMap} 时重建（每局一次）。
 *
 * <p>与上游实现相比，这里直接从地图 JSON 的 {@code settings} 子对象按字段名取值，
 * 不依赖 {@code AreasSettings} 类；旧格式写在地图 JSON 根级的键会作为兜底来源。
 * 这样本 mod 私有的地图字段（暴风雪等）无需改动 {@code AreasSettings} 也能被展示。
 *
 * <p>地图特性 = 地图自身 {@code settings.customMapFeatures} ∪ 旧的 NoellesRolesConfig 地图名单
 * （含本 mod 特有的雪地 / 沙漠 / 魔女监牢 / 监狱名单）。
 */
public final class MapIntroData {

    /** 只含投票配置里的地图；未构建过时为 null */
    private static volatile List<MapDisplayInfo> cache;

    private MapIntroData() {
    }

    /** 每次 loadMap 时调用（本局唯一权威刷新点）；任何异常都不允许影响地图加载。 */
    public static void rebuild(MinecraftServer server, String loadedMapId) {
        if (server == null) {
            return;
        }
        try {
            List<MapDisplayInfo> collected = collect(server, false);
            cache = collected;
            SRE.LOGGER.info("[MapIntro] display cache rebuilt: {} maps (current map: {})", collected.size(),
                    loadedMapId == null ? "-" : loadedMapId);
        } catch (Exception e) {
            SRE.LOGGER.warn("[MapIntro] failed to rebuild display cache", e);
        }
    }

    /** 取展示数据。includeAll 为管理员视图：临时收集磁盘上全部地图（不写缓存）。 */
    public static List<MapDisplayInfo> get(MinecraftServer server, boolean includeAll) {
        if (server == null) {
            return List.of();
        }
        if (includeAll) {
            return collect(server, true);
        }
        List<MapDisplayInfo> local = cache;
        if (local == null) {
            // 还没加载过任何地图（缓存未构建）时惰性构建一次
            rebuild(server, null);
            local = cache;
        }
        return local == null ? List.of() : local;
    }

    /** 轮抽开关：就地更新缓存里这一张图的 canSelect，避免同一局内显示过期。 */
    public static void updateCanSelect(String mapId, boolean canSelect) {
        List<MapDisplayInfo> local = cache;
        if (local == null || mapId == null) {
            return;
        }
        List<MapDisplayInfo> updated = new ArrayList<>(local.size());
        for (MapDisplayInfo info : local) {
            updated.add(info.id().equals(mapId) ? info.withCanSelect(canSelect) : info);
        }
        cache = List.copyOf(updated);
    }

    /** 强制失效（例如投票配置重载后想立刻刷新）。 */
    public static void invalidate() {
        cache = null;
    }

    // ==================== 收集 ====================

    private static List<MapDisplayInfo> collect(MinecraftServer server, boolean includeAll) {
        ServerLevel overworld = server.overworld();
        Map<String, MapConfig.MapEntry> voteEntries = new LinkedHashMap<>();
        for (MapConfig.MapEntry entry : ServerMapConfig.getInstance(server).getMaps()) {
            if (entry != null && entry.id != null) {
                voteEntries.put(entry.id, entry);
            }
        }

        List<String> ids = new ArrayList<>();
        if (includeAll) {
            ids.addAll(MapManager.getAvailableMaps(overworld, true));
        } else {
            ids.addAll(voteEntries.keySet());
        }

        Path mapsDir = server.getWorldPath(LevelResource.ROOT).resolve("train_maps");
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        List<MapDisplayInfo> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Path path = mapsDir.resolve(id + ".json").normalize();
            if (!Files.isRegularFile(path)) {
                SRE.LOGGER.warn("[MapIntro] map config not found, skipped: {}", id);
                continue;
            }
            JsonObject root;
            try {
                root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                SRE.LOGGER.warn("[MapIntro] failed to read map config: {}", id, e);
                continue;
            }
            result.add(build(id, root, voteEntries.get(id), config));
        }
        return List.copyOf(result);
    }

    private static MapDisplayInfo build(String id, JsonObject root, MapConfig.MapEntry voteEntry,
            NoellesRolesConfig config) {
        JsonObject settings = childObject(root, "settings");
        boolean hasVoteConfig = voteEntry != null;
        return new MapDisplayInfo(
                id,
                hasVoteConfig && voteEntry.displayName != null ? voteEntry.displayName : "",
                hasVoteConfig && voteEntry.description != null ? voteEntry.description : "",
                hasVoteConfig ? voteEntry.getColor() : 0,
                featureNames(settings, root, id, config),
                hasVoteConfig,
                hasVoteConfig ? Math.max(0, voteEntry.weight) : 0,
                hasVoteConfig ? voteEntry.minCount : -1,
                hasVoteConfig ? voteEntry.maxCount : -1,
                hasVoteConfig && voteEntry.canSelect,
                hasVoteConfig && voteEntry.gameModes != null ? List.copyOf(voteEntry.gameModes) : List.of(),
                intValue(settings, root, "roomCount", 1),
                stringList(settings, root, "disabledTasks"),
                stringList(settings, root, "disabledRoles"),
                stringList(root, null, "enableSceneTask"),
                boolValue(settings, root, "minigameQuestEnabled", false),
                boolValue(settings, root, "meetingEnabled", false),
                boolValue(settings, root, "meetingVoteEnabled", false),
                boolValue(settings, root, "bellMeetingEnabled", false),
                stringValue(settings, root, "mapStatusBar", "NONE"),
                boolValue(settings, root, "canJump", false),
                boolValue(settings, root, "canSimpleSwim", true),
                boolValue(settings, root, "canUnderWater", true),
                boolValue(settings, root, "allowInDeepWater", true),
                boolValue(settings, root, "canSwim", false),
                boolValue(settings, root, "enableOxygenDrowning", false),
                boolValue(settings, root, "snowEnabled", false),
                boolValue(settings, root, "sandEnabled", false),
                boolValue(settings, root, "planeCrashEventEnabled", false),
                boolValue(settings, root, "fogEnabled", true),
                floatValue(settings, root, "fogEnd", 200.0f),
                stringValue(settings, root, "weather", "clear"),
                doubleValue(settings, root, "gravityModifier", 0.0D),
                longValue(settings, root, "time", 18000L),
                boolValue(settings, root, "daylightCycle", false),
                boolValue(settings, root, "weatherCycle", false),
                mobEffects(settings, root),
                stringList(settings, root, "initialItems"));
    }

    /** 药水效果：新版键 mobEffects 优先，旧版键 effect 兜底。 */
    private static List<String> mobEffects(JsonObject settings, JsonObject root) {
        List<String> modern = stringList(settings, root, "mobEffects");
        if (!modern.isEmpty()) {
            return modern;
        }
        return stringList(settings, root, "effect");
    }

    /**
     * 地图特性（枚举名列表）：地图自己声明的 customMapFeatures 为主，
     * 旧的 NoellesRolesConfig 名单作为兼容来源一并并入。
     */
    private static List<String> featureNames(JsonObject settings, JsonObject root, String mapId,
            NoellesRolesConfig config) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String custom : stringList(settings, root, "customMapFeatures")) {
            if (custom != null && !custom.isBlank()) {
                names.add(custom);
            }
        }
        if (config != null) {
            addFeature(names, "QIYUCUN", config.maChenXuMaps, mapId);
            addFeature(names, "BIGMAP", config.swastMaps, mapId);
            addFeature(names, "UNDERWATER", config.underwaterRolesMaps, mapId);
            addFeature(names, "FLY", config.airRolesMaps, mapId);
            addFeature(names, "TRAP", config.trapRolesMaps, mapId);
            addFeature(names, "SNOW", config.snowRolesMaps, mapId);
            addFeature(names, "DESERT", config.desertRolesMaps, mapId);
            addFeature(names, "PRISON", config.prisonRolesMaps, mapId);
            addFeature(names, "WITCH_PRISON", config.witchPrisonRolesMaps, mapId);
        }
        return List.copyOf(names);
    }

    private static void addFeature(LinkedHashSet<String> names, String feature, List<String> maps, String mapId) {
        if (maps != null && mapId != null && maps.contains(mapId)) {
            names.add(feature);
        }
    }

    // ==================== JSON 取值工具 ====================

    private static JsonObject childObject(JsonObject root, String key) {
        if (root != null && root.has(key) && root.get(key).isJsonObject()) {
            return root.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private static JsonElement lookup(JsonObject primary, JsonObject fallback, String key) {
        if (primary != null && primary.has(key)) {
            JsonElement element = primary.get(key);
            if (element != null && !element.isJsonNull()) {
                return element;
            }
        }
        if (fallback != null && fallback.has(key)) {
            JsonElement element = fallback.get(key);
            if (element != null && !element.isJsonNull()) {
                return element;
            }
        }
        return null;
    }

    private static boolean boolValue(JsonObject primary, JsonObject fallback, String key, boolean fallbackValue) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallbackValue;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    private static int intValue(JsonObject primary, JsonObject fallback, String key, int fallbackValue) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallbackValue;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    private static long longValue(JsonObject primary, JsonObject fallback, String key, long fallbackValue) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallbackValue;
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    private static double doubleValue(JsonObject primary, JsonObject fallback, String key, double fallbackValue) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallbackValue;
        }
        try {
            return element.getAsDouble();
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    private static float floatValue(JsonObject primary, JsonObject fallback, String key, float fallbackValue) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallbackValue;
        }
        try {
            return element.getAsFloat();
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    private static String stringValue(JsonObject primary, JsonObject fallback, String key, String fallbackValue) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallbackValue;
        }
        try {
            return element.getAsString();
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    private static List<String> stringList(JsonObject primary, JsonObject fallback, String key) {
        JsonElement element = lookup(primary, fallback, key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonArray()) {
            List<String> out = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child != null && child.isJsonPrimitive()) {
                    out.add(child.getAsString());
                }
            }
            return List.copyOf(out);
        }
        if (element.isJsonPrimitive()) {
            return List.of(element.getAsString());
        }
        return List.of();
    }
}
