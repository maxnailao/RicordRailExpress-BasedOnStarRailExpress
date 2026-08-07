package org.agmas.noellesroles.game.roles.neutral.zhuimu;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 坠木角色组件
 * - 每20s获得50金币
 * - 存活到游戏结束即跟随胜利
 */
public class ZhuimuPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<ZhuimuPlayerComponent> KEY = ModComponents.ZHUIMU;

    private final Player player;
    public int coinTimer;
    public final List<BlockPos> trapPositions = new ArrayList<>();

    public ZhuimuPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        coinTimer = 0;
        trapPositions.clear();
        // 被动：抗性255，防止鞘翅飞行中因动能（碰撞/摔落）导致原版死亡
        if (player instanceof ServerPlayer) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 255, true, false, false));
        }
        sync();
    }

    @Override
    public void clear() {
        // 移除抗性效果
        if (player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
        coinTimer = 0;
        trapPositions.clear();
        sync();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        if (!gwc.isRole(player, ModRoles.ZHUIMU)) return;
        if (!gwc.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        coinTimer++;
        if (coinTimer >= 400) { // 20s = 400 ticks
            coinTimer = 0;
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.addToBalance(50);
        }

        // 保障：维持抗性255效果
        if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 255, true, false, false));
        }

        // 检查陷阱触发：皮革嘎的踩到陷阱位置
        if (!trapPositions.isEmpty()) {
            for (Player p : player.level().players()) {
                if (!gwc.isRole(p, ModRoles.PIGE)) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
                BlockPos pPos = p.blockPosition();
                var it = trapPositions.iterator();
                while (it.hasNext()) {
                    BlockPos trapPos = it.next();
                    if (trapPos.distSqr(pPos) <= 2.0) {
                        // 触发陷阱：缓慢2 + 失明 5秒
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                        it.remove();
                        sync();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("coinTimer", coinTimer);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        coinTimer = tag.getInt("coinTimer");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
