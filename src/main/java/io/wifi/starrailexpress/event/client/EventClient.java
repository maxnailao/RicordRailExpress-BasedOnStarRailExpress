package io.wifi.starrailexpress.event.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.EventEntry;
import io.wifi.starrailexpress.event.network.EventClaimC2SPayload;
import io.wifi.starrailexpress.event.network.EventImageRequestC2SPayload;
import io.wifi.starrailexpress.event.network.EventImageS2CPayload;
import io.wifi.starrailexpress.event.network.EventProgressS2CPayload;
import io.wifi.starrailexpress.event.network.EventRequestC2SPayload;
import io.wifi.starrailexpress.event.network.EventSyncS2CPayload;
import net.exmo.sre.EXSREClient;
import net.exmo.sre.loading.texture.ConfigTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

/** 活动系统客户端缓存：列表 JSON + 图片按需拉取 + 击杀进度 + 奖励/条件汉化显示。 */
@Environment(EnvType.CLIENT)
public final class EventClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("sre-events-client");

    private static final List<EventEntry> ENTRIES = new ArrayList<>();
    private static final Map<String, int[]> PROGRESS = new HashMap<>();
    /** 图片 id → 已注册纹理，注册失败时 null（避免每帧重复注册刷屏） */
    private static final Map<String, ResourceLocation> TEX_CACHE = new java.util.HashMap<>();
    /** 图片 id → 原始宽高（等比绘制用） */
    private static final Map<String, int[]> DIMS = Collections.synchronizedMap(new HashMap<>());
    private static final Set<String> PENDING = new HashSet<>();

    private EventClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EventSyncS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> applyList(payload.listJson())));
        ClientPlayNetworking.registerGlobalReceiver(EventImageS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> storeImage(payload.imageId(), payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(EventProgressS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    synchronized (PROGRESS) {
                        int[] c = payload.counts() == null ? new int[0] : payload.counts();
                        int[] arr = new int[2 + c.length];
                        arr[0] = payload.claimed() ? 1 : 0;
                        arr[1] = payload.claimable() ? 1 : 0;
                        System.arraycopy(c, 0, arr, 2, c.length);
                        PROGRESS.put(payload.packKey(), arr);
                    }
                }));
    }

    public static void requestSync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            ClientPlayNetworking.send(new EventRequestC2SPayload());
        }
    }

    /** 是否已经请求过服务端同步（列表非空或已拉过图片均视为已请求） */
    public static boolean hasRequestedSync() {
        return !ENTRIES.isEmpty() || !PENDING.isEmpty();
    }

    public static List<EventEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /** 当前日期是否处于活动起止窗口内（起止可空，空侧不限制） */
    public static boolean inWindow(EventEntry e) {
        String today = java.time.LocalDate.now().toString();
        if (e.startDate() != null && !e.startDate().isEmpty() && today.compareTo(e.startDate()) < 0) return false;
        if (e.endDate() != null && !e.endDate().isEmpty() && today.compareTo(e.endDate()) > 0) return false;
        return true;
    }

    /** 距结束还剩几天；无结束日期返回 Long.MIN_VALUE，已过期返回负数 */
    public static long remainingDays(EventEntry e) {
        if (e.endDate() == null || e.endDate().isEmpty()) return Long.MIN_VALUE;
        try {
            return java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(), java.time.LocalDate.parse(e.endDate()));
        } catch (Exception ex) {
            return Long.MIN_VALUE;
        }
    }

    /** 任务包进度 {cur, target, claimed}；键 = eventId#包号，无数据返回 null（按 0 处理） */
    public static int[] progress(String packKey) {
        synchronized (PROGRESS) {
            return PROGRESS.get(packKey);
        }
    }

    public static java.util.List<io.wifi.starrailexpress.event.EventPack> packs(EventEntry e) {
        return io.wifi.starrailexpress.event.EventPack.parse(e.condition(), e.rewards());
    }

    /** 打包前的原始奖励串（';' 分隔）直接转显示文本 */
    public static String packRewardDisplayOf(String raw) {
        return displayRewards(raw);
    }

    /** 单包奖励的中文显示 */
    public static String packRewardDisplay(io.wifi.starrailexpress.event.EventPack pack) {
        List<String> parts = new ArrayList<>();
        for (String r : pack.rewards()) parts.add(displayRewards(r));
        String s = String.join(" · ", parts);
        return s.isEmpty() ? "无" : s;
    }

    /** 结构化奖励串 → 中文显示（旧自由文本原样透传；' | ' 任务包分隔与 ' ; ' 同权） */
    public static String displayRewards(String raw) {
        if (raw == null || raw.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (String t : raw.split("[;|]")) {
            String tok = t.trim();
            if (tok.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            int c = tok.indexOf(':');
            String k = c < 0 ? tok : tok.substring(0, c);
            String v = c < 0 ? "" : tok.substring(c + 1).trim();
            switch (k) {
                case "coin" -> sb.append("货币 ×").append(v);
                case "shard" -> sb.append("神话碎片 ×").append(v);
                case "draw" -> sb.append("抽数 ×").append(v);
                case "skin" -> sb.append(skinName(v));
                default -> sb.append(tok);
            }
        }
        return sb.toString();
    }

    /** 条件串显示：支持多条件（; 分隔），逐条转中文，用 “ 且 ” 连接 */
    public static String displayCondition(String cond) {
        if (cond == null || cond.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : cond.split(";")) {
            String tok = raw.trim();
            if (tok.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" 且 ");
            sb.append(condDisplay(io.wifi.starrailexpress.event.EventCondition.parse(tok)));
        }
        return sb.toString();
    }

    /** 单条条件的中文显示 */
    public static String condDisplay(io.wifi.starrailexpress.event.EventCondition c) {
        if (c == null) return "";
        switch (c.kind()) {
            case io.wifi.starrailexpress.event.EventCondition.ANY:
                return "击杀 " + c.target() + " 人";
            case io.wifi.starrailexpress.event.EventCondition.ROLE:
                return "以" + roleDisplay(c.arg()) + "击杀 " + c.target() + " 人";
            case io.wifi.starrailexpress.event.EventCondition.WEAPON:
                return "以" + weaponDisplay(c.arg()) + "击杀 " + c.target() + " 人";
            default:
                return "";
        }
    }

    /** 条件串每条的目标值数组（顺序与包内 conditions/counts 一致；不可计数条件为 0） */
    public static int[] condTargets(String cond) {
        if (cond == null || cond.isEmpty()) return new int[0];
        String[] parts = cond.split(";");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = io.wifi.starrailexpress.event.EventCondition.parse(parts[i].trim()).target();
        }
        return out;
    }

    /** 条件串每条的显示名数组（顺序与 counts 向量一致，供详情页逐条渲染） */
    public static String[] condDisplays(String cond) {
        if (cond == null || cond.isEmpty()) return new String[0];
        String[] parts = cond.split(";");
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = condDisplay(io.wifi.starrailexpress.event.EventCondition.parse(parts[i].trim()));
        }
        return out;
    }

    /** 职业 id（namespace:path）→ 中文职业名，失败回退原 id */
    private static String roleDisplay(String roleId) {
        if (roleId == null || roleId.isEmpty()) return "指定职业";
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(roleId);
            if (rl != null) {
                String n = org.agmas.noellesroles.utils.RoleUtils.getRoleName(rl).getString();
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Throwable ignored) {}
        return roleId;
    }

    /** 武器类别 → 中文，与 EventManager.weaponTypeOf 的取值对齐 */
    private static String weaponDisplay(String type) {
        if (type == null) return "指定武器";
        switch (type) {
            case "knife": return "刀";
            case "gun": return "枪";
            case "bat": return "棍棒";
            case "grenade": return "手雷";
            case "bow": return "弓箭";
            case "trident": return "三叉戟";
            default: return type;
        }
    }

    private static String skinName(String id) {
        try {
            String n = org.agmas.noellesroles.cs2.CS2SkinInfo.getName(id);
            return (n == null || n.isEmpty()) ? id : n;
        } catch (Exception e) {
            return id;
        }
    }

    /** 配置界面本地预览：写入缓存并注册纹理后立即可以绘制 */
    public static void putLocalImage(String imageId, byte[] data) {
        storeImage(imageId, data);
    }

    /** 在指定框内等比居中绘制活动图片；纹理不可用时触发补拉并返回 false */
    public static boolean drawImage(GuiGraphics g, String imageId, int x, int y, int boxW, int boxH) {
        ResourceLocation tex = ensureTexture(imageId);
        if (tex == null) return false;
        int[] d = DIMS.getOrDefault(imageId, new int[]{boxW, boxH});
        double s = Math.min(boxW / (double) Math.max(1, d[0]), boxH / (double) Math.max(1, d[1]));
        int dw = Math.max(1, (int) (d[0] * s));
        int dh = Math.max(1, (int) (d[1] * s));
        int dx = x + (boxW - dw) / 2;
        int dy = y + (boxH - dh) / 2;
        g.blit(tex, dx, dy, 0, 0, dw, dh, d[0], d[1]);
        return true;
    }

    private static ResourceLocation ensureTexture(String id) {
        ResourceLocation cached = TEX_CACHE.get(id);
        if (cached != null) return cached;
        String locStr = "sre_events/images/" + sanitize(id) + ".png";
        File f = imageDir().resolve(locStr).toFile();
        if (!f.exists()) {
            if (PENDING.add(id)) {
                try {
                    ClientPlayNetworking.send(new EventImageRequestC2SPayload(id));
                } catch (Exception ignored) {
                    PENDING.remove(id);
                }
            }
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            ResourceLocation loc = SRE.id(locStr);
            Minecraft.getInstance().getTextureManager().register(loc, new ConfigTexture(loc));
            TEX_CACHE.put(id, loc);
            DIMS.put(id, readDims(bytes));
            return loc;
        } catch (Exception e) {
            LOGGER.error("[Events] 注册图片纹理失败 {}", f, e);
            return null;
        }
    }

    private static int[] readDims(byte[] data) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception ignored) {}
        return new int[]{64, 64};
    }

    private static void storeImage(String imageId, byte[] data) {
        try {
            Files.createDirectories(imageDir());
            String safe = sanitize(imageId);
            if (safe.isEmpty()) return;
            Files.write(imageDir().resolve(safe + ".png"), data);
            DIMS.remove(safe);
            ensureTexture(safe);   // 立即注册纹理，配置界面预览同款逻辑
        } catch (Exception ignored) {}
    }

    private static Path imageDir() {
        return Paths.get(EXSREClient.CONFIG_PATH + "/sre_events/images");
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^A-Za-z0-9_.\\-]", "");
    }

    private static synchronized void applyList(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;
            List<EventEntry> parsed = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                parsed.add(new EventEntry(
                        str(o, "id"), str(o, "title"), str(o, "content"), str(o, "rewards"),
                        str(o, "condition"), str(o, "startDate"), str(o, "endDate"),
                        o.has("featured") && o.get("featured").getAsBoolean(),
                        str(o, "imageId"),
                        o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L));
            }
            ENTRIES.clear();
            ENTRIES.addAll(parsed);
            Set<String> ids = new HashSet<>();
            for (EventEntry e : parsed) ids.add(e.id());
            synchronized (PROGRESS) {
                PROGRESS.keySet().retainAll(ids);
            }
        } catch (Exception ignored) {
        }
    }

    /** 领取任务键（服务端校验后回推新进度，界面自动变"已领取"） */
    public static void requestClaim(String packKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            ClientPlayNetworking.send(new EventClaimC2SPayload(packKey));
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}