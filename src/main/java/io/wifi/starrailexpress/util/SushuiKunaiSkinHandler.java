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
 * 塑水苦无特别皮肤处理器
 * <p>
 * 管理专属音效逻辑（仿照圣宣皮肤实现）：
 * - 切刀音效：切换刀时对持刀玩家播放专属音效，其他玩家听到正常切刀音效
 * - 击杀音效：击杀玩家时仅对手持玩家播放专属音效，其他玩家仍听见正常音效
 * </p>
 */
public final class SushuiKunaiSkinHandler {

    private SushuiKunaiSkinHandler() {}

    /** 皮肤ID常量（不含类型前缀，与装备系统存储格式一致） */
    public static final String SKIN_ID = "knife_sushuikunai";

    /**
     * 检查玩家是否装备了塑水苦无皮肤
     * <p>
     * 兼容两种来源：
     * 1. ItemStack 自身的 SKIN 数据组件（如指令/创造模式设置）
     * 2. 玩家装备系统中的皮肤（按皮肤类型查询，而非物品注册路径）
     * </p>
     * <p>客户端/服务端均可调用（装备查询自动走对应侧的数据源）。</p>
     */
    public static boolean hasKunaiSkinEquipped(Player player, ItemStack itemStack) {
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
     * 播放专属切刀音效（仅对持刀玩家播放）
     * 使用 ClientboundSoundPacket 确保只有持刀者本人能听到
     */
    public static void playSwitchSound(ServerPlayer holder, double x, double y, double z) {
        holder.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.ITEM_KNIFE_SUSHUIKUNAI_SWITCH),
                SoundSource.PLAYERS, x, y, z, 1.0f, 1.0f, holder.getRandom().nextLong()));
    }

    /**
     * 播放专属击杀音效（仅对持刀玩家播放）
     * 使用 ClientboundSoundPacket 确保只有击杀者本人能听到
     */
    public static void playKillSound(ServerPlayer killer, double x, double y, double z) {
        killer.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.ITEM_KNIFE_SUSHUIKUNAI_KILL),
                SoundSource.PLAYERS, x, y, z, 1.0f, 1.0f, killer.getRandom().nextLong()));
    }
}
