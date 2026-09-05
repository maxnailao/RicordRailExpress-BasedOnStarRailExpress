package org.agmas.noellesroles.mixin.roles.warlock;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockDomainManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 咒术师「灰髓之境」领域内的环境致死免疫。
 *
 * <p>领域位于高空虚空（{@link WarlockDomainManager#DOMAIN_Y} = 200，Z = -20000 与愚者会议室分处两端），
 * {@code SREGameWorldComponent#isPlayerOutGameAreas} 会在玩家低于 {@code playArea.minY}、落水或入岩浆时
 * 用 {@code FELL_OUT_OF_TRAIN} / {@code CANNOT_SWIM} / {@code LAVA} 强制处死——一旦地图配置把 minY 抬高，
 * 或玩家在域内失足，被拉入领域的人就会被环境判定秒杀。
 *
 * <p>领域本应是一处独立祭场，域内只应死于战斗（咒术师 / 彼此）。这里在所有死亡的唯一入口
 * {@link GameUtils#killPlayer} HEAD 处兜底：若受害者正处于领域内、且死因属于「出界 / 落水 / 岩浆」
 * 等环境判定，则取消这次死亡（软硬死亡都拦，强制死亡会绕过否决事件故必须走 mixin）。
 * 战斗死因（枪 / 刀等）不在名单内，照常结算。
 *
 * <p>镜像 {@code AdventurerDeathImmunityMixin} 的做法。
 */
@Mixin(GameUtils.class)
public abstract class WarlockDomainDeathImmunityMixin {

    /** 领域内应被免疫的「环境 / 区域判定」死因。 */
    private static final Set<ResourceLocation> DOMAIN_IMMUNE = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.CANNOT_SWIM,
            GameConstants.DeathReasons.LAVA);

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;Z"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void noellesroles$warlockDomainEnvironmentImmunity(
            Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (deathReason == null || victim == null) {
            return;
        }
        if (!DOMAIN_IMMUNE.contains(deathReason)) {
            return; // 战斗死因照常结算
        }
        if (WarlockDomainManager.isInDomain(victim.getUUID())) {
            ci.cancel();
        }
    }
}
