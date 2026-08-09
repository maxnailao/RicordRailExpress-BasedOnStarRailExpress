package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 塑水刃特别皮肤处理器
 * <p>
 * 管理专属击打音效逻辑（仿照圣宣皮肤实现）：
 * - 专属击打音效仅对皮肤拥有者播放，其他玩家听到正常球棒击打音效
 * </p>
 */
public final class SushuirenSkinHandler {

    private SushuirenSkinHandler() {}

    /** 皮肤ID常量（不含类型前缀，与装备系统存储格式一致） */
    public static final String SKIN_ID = "bat_sushuiren";

    /**
     * 检查玩家是否装备了塑水刃皮肤
     * <p>
     * 兼容两种来源：
     * 1. ItemStack 自身的 SKIN 数据组件（如指令/创造模式设置）
     * 2. 玩家装备系统中的皮肤（按皮肤类型查询，而非物品注册路径）
     * </p>
     */
    public static boolean hasSushuirenSkinEquipped(Player player, ItemStack itemStack) {
        // 1. 优先检查 ItemStack 自身的皮肤数据组件
        if (itemStack.has(SREDataComponentTypes.SKIN)) {
            return SKIN_ID.equals(itemStack.get(SREDataComponentTypes.SKIN));
        }
        // 2. 获取物品的皮肤类型（如 "bat"），而非注册路径
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
     * 播放专属击打音效（仅对皮肤拥有者播放）
     * 使用 ClientboundSoundPacket 确保只有攻击者本人能听到
     *
     * @param attacker 攻击者
     * @param x 击打位置X
     * @param y 击打位置Y
     * @param z 击打位置Z
     */
    public static void playHitSound(ServerPlayer attacker, double x, double y, double z) {
        attacker.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.ITEM_BAT_SUSHUIREN),
                SoundSource.PLAYERS, x, y, z, 3f, 1f, attacker.getRandom().nextLong()));
    }
}
