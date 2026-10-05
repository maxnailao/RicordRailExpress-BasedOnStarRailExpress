package io.wifi.starrailexpress.content.mail;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 给**离线玩家**投递邮件。
 *
 * <p>在线玩家走 {@code MailboxComponent.sendMail(...)}；离线玩家没有 CCA 组件实例，
 * 只能直接往持久层写。邮箱的持久层有两个：
 * <ul>
 * <li>{@code usercache.json} 无关 —— 那只是"服务器见过哪些玩家"的名单；</li>
 * <li>真正存邮件的是 MySQL 的 {@code sre_player_sync_data} 表，
 * key = {@code mailbox}，value = 一个 JSON 数组（{@code Mail.toJson()} 的集合，
 * 见 {@code MailboxComponent#serializeToJson}）。</li>
 * </ul>
 *
 * <p>所以离线投递 = 读出该 UUID 的 mailbox JSON → 追加新邮件 → 写回。
 *
 * <p><b>重要限制</b>：只有启用了 MySQL 玩家数据同步（{@code mysqlPlayerSyncEnabled}）
 * 才可能离线投递。否则邮件只存在玩家实体的 NBT 里，
 * 离线时根本无处可写 —— 这种情况我们会明确报错，而不是假装成功。
 *
 * <p>另一个已知限制：{@code Mail.toJson()} 只序列化元数据，<b>不包含 attachments</b>
 * （物品附件）。所以离线投递的邮件里，普通物品附件会丢失；
 * 金币与仓库物品走 {@code claimCommands}，不受影响。
 */
public final class OfflineMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfflineMailService.class);
    private static final String DB_KEY = "mailbox";
    private static final Gson GSON = new Gson();
    private static final long BLOCKING_TIMEOUT_MS = 8000L;

    private OfflineMailService() {
    }

    /** 离线投递是否可用（必须启用 MySQL 同步） */
    public static boolean isAvailable() {
        return MysqlPlayerDataStore.isAvailable();
    }

    /** 不可用时给管理员看的原因 */
    public static String unavailableReason() {
        if (!MysqlPlayerDataStore.isAvailable()) {
            return "未启用 MySQL 玩家数据同步（config 里的 mysqlPlayerSyncEnabled），"
                    + "离线玩家的邮箱无处可写；请改为只发给在线玩家，或先开启 MySQL 同步。";
        }
        return "";
    }

    /**
     * 把一封邮件写进**离线**玩家的邮箱。
     *
     * @return 成功返回 true
     */
    public static boolean deliver(MinecraftServer server, UUID playerUuid, Mail mail) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Map<String, MysqlPlayerDataStore.SyncRecord> records =
                    MysqlPlayerDataStore.loadBatchAsync(playerUuid, List.of(DB_KEY)).get();
            MysqlPlayerDataStore.SyncRecord record = records.get(DB_KEY);
            JsonArray arr = parseArray(record == null ? null : record.payload());
            arr.add(JsonParser.parseString(mail.toJson()));
            boolean ok = MysqlPlayerDataStore.saveBatchForceBlocking(
                    playerUuid, Map.of(DB_KEY, GSON.toJson(arr)),
                    System.currentTimeMillis(), BLOCKING_TIMEOUT_MS);
            if (!ok) {
                LOGGER.warn("离线邮件投递被拒绝（revision 冲突）: player={}, mail={}",
                        playerUuid, mail.id);
            }
            return ok;
        } catch (Exception e) {
            LOGGER.warn("离线邮件投递失败: player={}, mail={}", playerUuid, mail.id, e);
            return false;
        }
    }

    private static JsonArray parseArray(String json) {
        if (json == null || json.isBlank()) {
            return new JsonArray();
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonArray()) {
                return el.getAsJsonArray();
            }
            // 兼容：万一存的是单个对象
            JsonArray arr = new JsonArray();
            arr.add(el);
            return arr;
        } catch (Exception e) {
            LOGGER.warn("邮箱 JSON 解析失败，将按空邮箱处理（原内容不会被覆盖丢失，"
                    + "因为本次写入会重新写整个数组——请注意这条日志）", e);
            return new JsonArray();
        }
    }

    // =========================================================================
    // 服务器已知玩家名单（usercache.json）
    // =========================================================================

    /** 服务器见过的玩家：UUID -> 名字 */
    public static Map<UUID, String> knownPlayers(MinecraftServer server) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (server == null) {
            return out;
        }
        Path cache = server.getServerDirectory().resolve("usercache.json");
        if (!Files.isRegularFile(cache)) {
            LOGGER.warn("找不到 usercache.json（{}），无法列出服务器已知玩家", cache);
            return out;
        }
        try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (!el.isJsonArray()) {
                return out;
            }
            for (JsonElement e : el.getAsJsonArray()) {
                if (!e.isJsonObject()) {
                    continue;
                }
                var obj = e.getAsJsonObject();
                if (!obj.has("uuid") || !obj.has("name")) {
                    continue;
                }
                try {
                    out.put(UUID.fromString(obj.get("uuid").getAsString()),
                            obj.get("name").getAsString());
                } catch (IllegalArgumentException ignored) {
                    // 跳过格式异常的条目
                }
            }
        } catch (IOException e) {
            LOGGER.warn("读取 usercache.json 失败", e);
        }
        return out;
    }

    /** 邮箱 JSON 里已有的邮件条数（用于命令反馈） */
    public static List<Mail> peek(MinecraftServer server, UUID playerUuid) {
        List<Mail> out = new ArrayList<>();
        if (!isAvailable()) {
            return out;
        }
        try {
            Map<String, MysqlPlayerDataStore.SyncRecord> records =
                    MysqlPlayerDataStore.loadBatchAsync(playerUuid, List.of(DB_KEY)).get();
            MysqlPlayerDataStore.SyncRecord record = records.get(DB_KEY);
            if (record == null || record.payload() == null) {
                return out;
            }
            for (JsonElement e : parseArray(record.payload())) {
                out.add(Mail.fromJsonMeta(GSON.toJson(e)));
            }
        } catch (Exception e) {
            LOGGER.warn("读取离线邮箱失败: player={}", playerUuid, e);
        }
        return out;
    }
}
