package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShengxuanFormS2CPayload;
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
 * 圣宣特别皮肤处理器
 * <p>
 * 管理双形态切换和专属枪声逻辑：
 * - 形态1（黑枪）击杀后切换为形态2（白枪）
 * - 形态2（白枪）击杀后切换为形态1（黑枪）
 * - 专属枪声仅对皮肤拥有者播放，其他玩家听到普通枪声
 * </p>
 */
public final class ShengxuanSkinHandler {

    private ShengxuanSkinHandler() {}

    /** 皮肤ID常量（不含类型前缀，与装备系统存储格式一致） */
    public static final String SKIN_ID = "revolver_shengxuan";

    /** 形态1贴图后缀 */
    private static final String FORM_1_SUFFIX = "_1";
    /** 形态2贴图后缀 */
    private static final String FORM_2_SUFFIX = "_2";

    /** 玩家当前形态缓存 (UUID -> currentForm: 1 or 2) */
    private static final Map<UUID, Integer> playerForms = new HashMap<>();

    /**
     * 检查玩家是否装备了圣宣皮肤
     * <p>
     * 兼容两种来源：
     * 1. ItemStack 自身的 SKIN 数据组件（如指令/创造模式设置）
     * 2. 玩家装备系统中的皮肤（按皮肤类型查询，而非物品注册路径，
     *    因为 sheriff_revolver/standard_revolver/desert_eagle 等枪械的皮肤类型均为 "revolver"）
     * </p>
     */
    public static boolean hasShengxuanSkinEquipped(Player player, ItemStack itemStack) {
        // 1. 优先检查 ItemStack 自身的皮肤数据组件
        if (itemStack.has(SREDataComponentTypes.SKIN)) {
            String compSkin = itemStack.get(SREDataComponentTypes.SKIN);
            boolean result = SKIN_ID.equals(compSkin);
            SRE.LOGGER.debug("[圣宣] 检测到ItemStack皮肤组件: {}, 匹配结果: {}", compSkin, result);
            return result;
        }
        // 2. 获取物品的皮肤类型（如 "revolver"），而非注册路径
        String skinType;
        if (itemStack.getItem() instanceof SkinableItem skinable && skinable.getItemSkinType() != null) {
            skinType = skinable.getItemSkinType();
        } else {
            skinType = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        }
        // 3. 按皮肤类型查询玩家装备的皮肤
        String equippedSkin = PlayerEconomyManager.getEquippedSkinForItemType(player, skinType);
        boolean result = SKIN_ID.equals(equippedSkin);
        SRE.LOGGER.debug("[圣宣] 皮肤类型: {}, 装备皮肤: {}, 匹配结果: {}", skinType, equippedSkin, result);
        return result;
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
        SRE.LOGGER.info("[圣宣] 玩家 {} 形态切换: {} -> {}", player.getName().getString(), currentForm, newForm);
        // 同步到客户端，确保渲染显示正确形态
        if (player instanceof ServerPlayer serverPlayer) {
            PacketTracker.sendToClient(serverPlayer, new ShengxuanFormS2CPayload(newForm));
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
     * 播放枪声（仅对皮肤拥有者播放专属枪声）
     * 使用 ClientboundSoundPacket 确保只有射击者本人能听到
     * 
     * @param shooter 射击者
     * @param x 射击位置X
     * @param y 射击位置Y
     * @param z 射击位置Z
     */
    public static void playShootSound(ServerPlayer shooter, double x, double y, double z) {
        // 使用 ClientboundSoundPacket 仅对射击者本人播放专属枪声
        float pitch = 1f + shooter.getRandom().nextFloat() * .1f - .05f;
        shooter.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.ITEM_REVOLVER_SHENGXUAN),
                SoundSource.PLAYERS, x, y, z, 5f, pitch, shooter.getRandom().nextLong()));
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
