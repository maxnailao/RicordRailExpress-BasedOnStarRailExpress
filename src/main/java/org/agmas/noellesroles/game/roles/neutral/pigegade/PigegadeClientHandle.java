package org.agmas.noellesroles.game.roles.neutral.pigegade;

import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 皮革嘎的客户端猪模型处理
 * - 玩家本身隐身
 * - 召唤一只猪跟随玩家
 * - 猪的视角与玩家视角同步
 * - 玩家保持第一人称
 */
public class PigegadeClientHandle {
    public static Map<UUID, Pig> pigMap = new HashMap<>();

    public static void getOrCreatePig(Player player, ClientLevel clientLevel) {
        UUID uuid = player.getUUID();
        if (!pigMap.containsKey(uuid)) {
            Pig pig = new Pig(EntityType.PIG, clientLevel);
            pig.setPos(player.getX(), player.getY(), player.getZ());
            pig.setNoAi(true);
            pig.setNoGravity(false);
            pig.setYHeadRot(player.getYHeadRot());
            pig.setYBodyRot(player.getYRot());
            // 给予抗性提升，防止猪被原版机制意外击杀（隐藏粒子）
            pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false, false));
            pigMap.put(uuid, pig);
            clientLevel.addEntity(pig);
        }
    }

    /**
     * 每 tick 更新猪的位置和视角，并让玩家隐身
     */
    public static void tickPig(Player player) {
        UUID uuid = player.getUUID();
        Pig pig = pigMap.get(uuid);
        if (pig == null) return;

        // 玩家死亡时，猪跟随死亡（原版死亡动画）
        if (player.isSpectator() || player.isDeadOrDying()) {
            if (pig.isAlive()) {
                pig.kill();
            }
            return;
        }

        // 同步位置（猪站在玩家位置）
        pig.setPos(player.getX(), player.getY(), player.getZ());
        // 同步视角（猪头朝向玩家视角方向）
        pig.setYHeadRot(player.getYHeadRot());
        pig.setYBodyRot(player.getYRot());
        pig.setXRot(player.getXRot());
        pig.setNoAi(true);

        // 保障：确保抗性效果始终存在
        if (!pig.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false, false));
        }
    }

    public static void removePig(UUID uuid) {
        Pig pig = pigMap.remove(uuid);
        if (pig != null) {
            pig.discard();
        }
    }

    static {
        OnGameStartedClient.EVENT.register(() -> {
            pigMap.values().forEach(Pig::discard);
            pigMap.clear();
        });
        OnGameFinishedClient.EVENT.register(() -> {
            pigMap.values().forEach(Pig::discard);
            pigMap.clear();
        });
    }
}
