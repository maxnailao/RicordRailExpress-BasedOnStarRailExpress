package org.agmas.noellesroles.game.roles.neutral.pigegade;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 皮革嘎的角色组件
 * - 模型变为猪
 * - 每20s获得50金币
 * - 铁剑命中坠木3次击杀
 * - 击杀坠木（或坠木死亡）即跟随胜利
 */
public class PigegadePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<PigegadePlayerComponent> KEY = ModComponents.PIGE;

    private final Player player;
    public int coinTimer;
    public int swordHitCount;
    public boolean isPig;
    public boolean zhuimuDead;

    public PigegadePlayerComponent(Player player) {
        this.player = player;
    }

    static {
        // 监听坠木死亡事件
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(victim.level());
            if (!gwc.isRole(victim, ModRoles.ZHUIMU)) return;
            // 坠木死亡，通知所有皮革嘎的
            for (Player p : victim.level().players()) {
                if (gwc.isRole(p, ModRoles.PIGE)) {
                    PigegadePlayerComponent comp = KEY.get(p);
                    comp.zhuimuDead = true;
                    comp.sync();
                }
            }
        });
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return true; // isPig 需要对所有玩家可见
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        coinTimer = 0;
        swordHitCount = 0;
        isPig = true;
        zhuimuDead = false;
        // 服务端给予隐身效果（同黑白逻辑），使玩家本体对所有玩家不可见，只显示猪
        // 被动：抗性255，防止鞘翅飞行中因动能（碰撞/摔落）导致原版死亡
        if (player instanceof ServerPlayer) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 255, true, false, false));
        }
        sync();
    }

    @Override
    public void clear() {
        if (this.player.level().isClientSide) {
            if (isPig) {
                PigegadeClientHandle.removePig(this.getPlayer().getUUID());
            }
        }
        // 移除隐身效果和抗性效果
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        if (player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
        coinTimer = 0;
        swordHitCount = 0;
        isPig = false;
        zhuimuDead = false;
        sync();
    }

    public boolean isZhuimuDead() {
        return zhuimuDead;
    }

    /**
     * 铁剑命中坠木时调用
     */
    public void onSwordHitZhuimu() {
        swordHitCount++;
        sync();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        if (!gwc.isRole(player, ModRoles.PIGE)) return;
        if (!gwc.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        coinTimer++;
        if (coinTimer >= 400) { // 20s = 400 ticks
            coinTimer = 0;
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.addToBalance(50);
        }

        // 保障：维持服务端隐身效果和抗性255效果（同黑白逻辑）
        if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, true, false, false));
        }
        if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 255, true, false, false));
        }
    }

    @Override
    public void clientTick() {
        if (!io.wifi.starrailexpress.client.SREClient.gameComponent.isRunning()) {
            if (!PigegadeClientHandle.pigMap.isEmpty()) {
                PigegadeClientHandle.pigMap.values().forEach(p -> p.discard());
                PigegadeClientHandle.pigMap.clear();
            }
            return;
        }
        if (isPig) {
            if (player.isSpectator()) {
                PigegadeClientHandle.removePig(this.getPlayer().getUUID());
                return;
            }
            // 方案1：本地玩家自己的客户端不显示猪（猪始终位于自身坐标，
            // 鞘翅飞行时会遮挡第一人称视野）；其他玩家的客户端正常显示
            if (player == net.minecraft.client.Minecraft.getInstance().player) {
                PigegadeClientHandle.removePig(this.getPlayer().getUUID());
                return;
            }
            PigegadeClientHandle.getOrCreatePig(this.getPlayer(),
                    net.minecraft.client.Minecraft.getInstance().level);
            PigegadeClientHandle.tickPig(this.getPlayer());
        } else {
            PigegadeClientHandle.removePig(this.getPlayer().getUUID());
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("coinTimer", coinTimer);
        tag.putInt("swordHitCount", swordHitCount);
        tag.putBoolean("isPig", isPig);
        tag.putBoolean("zhuimuDead", zhuimuDead);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        coinTimer = tag.getInt("coinTimer");
        swordHitCount = tag.getInt("swordHitCount");
        isPig = tag.contains("isPig") && tag.getBoolean("isPig");
        zhuimuDead = tag.contains("zhuimuDead") && tag.getBoolean("zhuimuDead");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
