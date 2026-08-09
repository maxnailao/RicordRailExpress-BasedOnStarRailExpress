package org.agmas.noellesroles.dialog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.DialogNpcEntity;
import org.agmas.noellesroles.packet.OpenDialogNpcScreenS2CPacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话配置数据管理器。
 * <p>
 * 对话文件存放于 {@code <world>/train_dialogs/<dialogId>.json}（参照 train_maps 的目录约定），
 * 服务端加载并缓存，玩家右键对话 NPC 时把整份 JSON 原文通过 S2C 包发给客户端渲染。
 * <p>
 * 对话 JSON 格式：
 * <pre>
 * {
 *   "name": "NPC 显示名",
 *   "skin": "贴图名（对应 textures/entity/dialog_npc/&lt;skin&gt;.png）",
 *   "start": "起始节点 id",
 *   "nodes": {
 *     "节点id": {
 *       "text": "NPC 台词",
 *       "options": [
 *         { "text": "选项文字", "next": "下一节点id" },
 *         { "text": "选项文字", "command": "无斜杠命令", "next": "下一节点id" },
 *         { "text": "告别", "end": true }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
public class DialogDataManager {

    /** 对话配置目录名（位于存档根目录下） */
    public static final String DIR_NAME = "train_dialogs";
    /** 默认对话 id（JiaLe 列车引导员） */
    public static final String DEFAULT_DIALOG_ID = "jiale";

    private static final Map<String, JsonObject> JSON_CACHE = new HashMap<>();
    private static final Map<String, String> RAW_CACHE = new HashMap<>();

    /** 获取对话配置目录（不存在则创建） */
    public static Path getDialogsDir(MinecraftServer server) {
        Path dir = server.getWorldPath(LevelResource.ROOT)
                .resolve(DIR_NAME)
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("无法创建对话配置目录 {}", dir, e);
        }
        return dir;
    }

    /**
     * 按优先级查找对话文件：
     * <ol>
     * <li>存档目录 {@code <world>/train_dialogs/}；</li>
     * <li>服务器工作目录 {@code <serverDir>/train_dialogs/}（兼容单机/不同 level-name）；</li>
     * <li>服务器工作目录下的默认存档 {@code <serverDir>/world/train_dialogs/}。</li>
     * </ol>
     */
    private static Path findDialogFile(MinecraftServer server, String dialogId) {
        if (dialogId == null || dialogId.isBlank()) {
            return null;
        }
        List<Path> dirs = new ArrayList<>();
        dirs.add(getDialogsDir(server));
        try {
            Path serverDir = server.getServerDirectory().toAbsolutePath().normalize();
            dirs.add(serverDir.resolve(DIR_NAME));
            dirs.add(serverDir.resolve("world").resolve(DIR_NAME));
        } catch (Exception ignored) {
        }
        for (Path dir : dirs) {
            try {
                Path path = dir.resolve(dialogId + ".json").normalize();
                if (path.startsWith(dir) && Files.isRegularFile(path)) {
                    return path;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 加载对话 JSON（带缓存），缺失或非法返回 null */
    public static JsonObject load(MinecraftServer server, String dialogId) {
        JsonObject cached = JSON_CACHE.get(dialogId);
        if (cached != null) {
            return cached;
        }
        Path path = findDialogFile(server, dialogId);
        if (path == null) {
            // JiaLe 对话内置在 jar 资源中，外部文件缺失时直接读内置版本，保证开箱即用
            if (DEFAULT_DIALOG_ID.equals(dialogId)) {
                return loadBuiltin(dialogId);
            }
            return null;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(raw);
            if (!element.isJsonObject()) {
                Noellesroles.LOGGER.error("对话配置 {} 不是合法的 JSON 对象", path);
                return null;
            }
            JsonObject json = element.getAsJsonObject();
            JSON_CACHE.put(dialogId, json);
            RAW_CACHE.put(dialogId, raw);
            return json;
        } catch (Exception e) {
            Noellesroles.LOGGER.error("解析对话配置 {} 失败", path, e);
            return null;
        }
    }

    /** 获取对话 JSON 原文（带缓存），用于 S2C 发送 */
    public static String loadRaw(MinecraftServer server, String dialogId) {
        load(server, dialogId);
        return RAW_CACHE.get(dialogId);
    }

    /** 清空缓存（/sre:dialognpc reload 使用） */
    public static void clearCache() {
        JSON_CACHE.clear();
        RAW_CACHE.clear();
    }

    /** 递归列出所有可用对话 id（不含 .json 后缀，多目录去重） */
    public static List<String> getAvailableDialogs(MinecraftServer server) {
        List<String> result = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        dirs.add(getDialogsDir(server));
        try {
            Path serverDir = server.getServerDirectory().toAbsolutePath().normalize();
            dirs.add(serverDir.resolve(DIR_NAME));
            dirs.add(serverDir.resolve("world").resolve(DIR_NAME));
        } catch (Exception ignored) {
        }
        for (Path dir : dirs) {
            if (Files.isDirectory(dir)) {
                collectRecursively(dir, dir, result);
            }
        }
        List<String> distinct = result.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new ArrayList<>(distinct);
    }

    private static void collectRecursively(Path root, Path dir, List<String> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    collectRecursively(root, entry, result);
                } else if (name.endsWith(".json")) {
                    Path relative = root.relativize(entry);
                    String id = relative.toString()
                            .replace('\\', '/')
                            .substring(0, relative.toString().length() - 5);
                    result.add(id);
                }
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.error("遍历对话配置目录失败", e);
        }
    }

    /** 服务端定期调用：根据对话配置刷新实体的名字与皮肤 */
    public static void refreshEntityFromConfig(MinecraftServer server, DialogNpcEntity npc) {
        if (server == null || npc == null) {
            return;
        }
        JsonObject json = load(server, npc.getDialogId());
        if (json == null) {
            return;
        }
        if (!npc.hasCustomName() && json.has("name")) {
            npc.setCustomName(Component.literal(json.get("name").getAsString()));
        }
        String skin = json.has("skin") ? json.get("skin").getAsString() : "";
        if (!skin.equals(npc.getSkinId())) {
            npc.setSkinId(skin);
        }
        boolean slim = json.has("slim") && json.get("slim").getAsBoolean();
        if (slim != npc.isSlim()) {
            npc.setSlim(slim);
        }
    }

    /** 玩家右键 NPC：读取配置并发送 S2C 包打开对话界面 */
    public static void openDialog(ServerPlayer player, DialogNpcEntity npc) {
        String dialogId = npc.getDialogId();
        JsonObject json = load(player.server, dialogId);
        if (json == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.noellesroles.dialog_npc.not_found", dialogId,
                    getDialogsDir(player.server).toString())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String raw = RAW_CACHE.getOrDefault(dialogId, "{}");
        String name = npc.getCustomName() != null
                ? npc.getCustomName().getString()
                : (json.has("name") ? json.get("name").getAsString() : dialogId);
        String skin = json.has("skin") ? json.get("skin").getAsString() : "";
        ServerPlayNetworking.send(player,
                new OpenDialogNpcScreenS2CPacket(npc.getId(), name, skin, raw));
    }

    /** 从 jar 内置资源加载对话（{@code data/noellesroles/dialogs/<id>.json}） */
    private static JsonObject loadBuiltin(String dialogId) {
        try (var stream = DialogDataManager.class
                .getResourceAsStream("/data/noellesroles/dialogs/" + dialogId + ".json")) {
            if (stream == null) {
                Noellesroles.LOGGER.error("内置对话资源缺失：{}", dialogId);
                return null;
            }
            String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(raw);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject json = element.getAsJsonObject();
            JSON_CACHE.put(dialogId, json);
            RAW_CACHE.put(dialogId, raw);
            return json;
        } catch (Exception e) {
            Noellesroles.LOGGER.error("加载内置对话 {} 失败", dialogId, e);
            return null;
        }
    }
}
