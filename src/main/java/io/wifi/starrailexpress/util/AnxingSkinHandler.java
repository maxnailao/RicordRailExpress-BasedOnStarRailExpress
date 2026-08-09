package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.AnxingFormS2CPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 暗星特别皮肤处理器
 * <p>
 * 管理双形态切换和专属切刀音效逻辑（仿照圣宣/塑水苦无实现）：
 * - 形态1（天使）击杀后切换为形态2（恶魔），形态2（恶魔）击杀后切换为形态1（天使）
 * - 切刀音效仅对皮肤拥有者播放，其他玩家听到正常切刀音效
 * </p>
 */
public final class AnxingSkinHandler {

    private AnxingSkinHandler() {}

    /** 皮肤ID常量（不含类型前缀，与装备系统存储格式一致） */
    public static final String SKIN_ID = "knife_anxing";

    /** 形态1贴图后缀（天使） */
    private static final String FORM_1_SUFFIX = "_1";
    /** 形态2贴图后缀（恶魔） */
    private static final String FORM_2_SUFFIX = "_2";

    /** 玩家当前形态缓存 (UUID -> currentForm: 1 or 2) */
    private static final Map<UUID, Integer> playerForms = new HashMap<>();

    /**
     * 检查玩家是否装备了暗星皮肤
     * <p>
     * 兼容两种来源：
     * 1. ItemStack 自身的 SKIN 数据组件（如指令/创造模式设置）
     * 2. 玩家装备系统中的皮肤（按皮肤类型查询，而非物品注册路径）
     * </p>
     * <p>客户端/服务端均可调用（装备查询自动走对应侧的数据源）。</p>
     */
    public static boolean hasAnxingSkinEquipped(Player player, ItemStack itemStack) {
        // 1. 优先检查 ItemStack 自身的皮肤数据组件
        if (itemStack.has(SREDataComponentTypes.SKIN)) {
            return SKIN_ID.equals(itemStack.get(SREDataComponentTypes.SKIN));
        }
        // 2. 获取物品的皮肤类型（如 "knife"），而非注册路径
        String skinType;
        if (itemStack.getItem() instanceof SkinableItem skinable && skinable.getItemSkinType() != null) {
            skinType = skinable.getItemSkinType();
        } else {
            skinType = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        }
        // 3. 按皮肤类型查询玩家装备的皮肤
        String equippedSkin = PlayerEconomyManager.getEquippedSkinForItemType(player, skinType);
        return SKIN_ID.equals(equippedSkin);
    }

    /**
     * 获取玩家当前形态（1或2）
     */
    public static int getCurrentForm(Player player) {
        return playerForms.getOrDefault(player.getUUID(), 1);
    }

    /**
     * 切换形态（击杀后调用），并同步到客户端
     */
    public static void switchForm(Player player) {
        UUID uuid = player.getUUID();
        int currentForm = playerForms.getOrDefault(uuid, 1);
        int newForm = (currentForm == 1) ? 2 : 1;
        playerForms.put(uuid, newForm);
        SRE.LOGGER.info("[暗星] 玩家 {} 形态切换: {} -> {}", player.getName().getString(), currentForm, newForm);
        // 同步到客户端，确保渲染显示正确形态
        if (player instanceof ServerPlayer serverPlayer) {
            PacketTracker.sendToClient(serverPlayer, new AnxingFormS2CPayload(newForm));
        }
    }

    /**
     * 设置客户端本地形态（由客户端接收同步包后调用）
     */
    public static void setClientForm(Player player, int form) {
        playerForms.put(player.getUUID(), form);
    }

    /**
     * 获取当前形态的贴图后缀
     */
    public static String getCurrentFormSuffix(Player player) {
        int form = getCurrentForm(player);
        return form == 1 ? FORM_1_SUFFIX : FORM_2_SUFFIX;
    }

    /**
     * 播放专属切刀音效（仅对持刀玩家播放）
     * 使用 ClientboundSoundPacket 确保只有持刀者本人能听到
     */
    public static void playSwitchSound(ServerPlayer holder, double x, double y, double z) {
        holder.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.ITEM_KNIFE_ANXING_SWITCH),
                SoundSource.PLAYERS, x, y, z, 1.0f, 1.0f, holder.getRandom().nextLong()));
    }

    /**
     * 重置玩家形态（玩家退出或卸下皮肤时调用）
     */
    public static void resetForm(Player player) {
        playerForms.remove(player.getUUID());
    }

    /**
     * 清理所有缓存（服务器关闭时调用）
     */
    public static void clearAll() {
        playerForms.clear();
    }
}
