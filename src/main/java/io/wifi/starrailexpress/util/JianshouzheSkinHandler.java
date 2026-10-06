package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.JianshouzheFormS2CPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 坚守者之怒特别皮肤处理器（神话双形态）
 * - 形态1 击杀后切换为形态2（贴图 坚守者之怒2）
 * - 形态2 击杀后切回形态1
 * - 开枪音效为原版监守者（Warden）咆哮声，仅射手本人听见，他人听普通枪声
 */
public final class JianshouzheSkinHandler {

    private JianshouzheSkinHandler() {}

    public static final String SKIN_ID = "revolver_jianshouzhe";

    private static final String FORM_1_SUFFIX = "_1";
    private static final String FORM_2_SUFFIX = "_2";

    private static final Map<UUID, Integer> playerForms = new HashMap<>();

    public static boolean hasJianshouzheSkinEquipped(Player player, ItemStack itemStack) {
        String compSkin = itemStack.get(SREDataComponentTypes.SKIN);
        if (SKIN_ID.equals(compSkin)) {
            return true;
        }
        String skinType;
        if (itemStack.getItem() instanceof SkinableItem skinable && skinable.getItemSkinType() != null) {
            skinType = skinable.getItemSkinType();
        } else {
            skinType = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        }
        String equippedSkin = PlayerEconomyManager.getEquippedSkinForItemType(player, skinType);
        return SKIN_ID.equals(equippedSkin);
    }

    public static int getCurrentForm(Player player) {
        return playerForms.getOrDefault(player.getUUID(), 1);
    }

    public static void switchForm(Player player) {
        UUID uuid = player.getUUID();
        int currentForm = playerForms.getOrDefault(uuid, 1);
        int newForm = (currentForm == 1) ? 2 : 1;
        playerForms.put(uuid, newForm);
        SRE.LOGGER.info("[坚守者之怒] 玩家 {} 形态切换: {} -> {}", player.getName().getString(), currentForm, newForm);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketTracker.sendToClient(serverPlayer, new JianshouzheFormS2CPayload(newForm));
        }
    }

    public static void setClientForm(Player player, int form) {
        playerForms.put(player.getUUID(), form);
    }

    public static String getCurrentFormSuffix(Player player) {
        return getCurrentForm(player) == 1 ? FORM_1_SUFFIX : FORM_2_SUFFIX;
    }

    /** 开枪音效：仅对射手本人播放原版监守者「音爆」，短促爆裂、瞬发感强 */
    public static void playShootSound(ServerPlayer shooter, double x, double y, double z) {
        // 略抬音高让爆裂更干脆；音调微随机避免连听疲劳
        float pitch = 1.15f + shooter.getRandom().nextFloat() * 0.10f - 0.05f;
        shooter.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_SONIC_BOOM),
                SoundSource.PLAYERS, x, y, z, 1.5f, pitch, shooter.getRandom().nextLong()));
    }

    public static void resetForm(Player player) {
        playerForms.remove(player.getUUID());
    }

    public static void clearAll() {
        playerForms.clear();
    }
}