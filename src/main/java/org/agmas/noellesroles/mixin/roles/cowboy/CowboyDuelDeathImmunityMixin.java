package org.agmas.noellesroles.mixin.roles.cowboy;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.vigilante.cowboy.CowboyDuelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 西部牛仔「决斗领域」内的环境致死免疫。
 *
 * <p>决斗场地是手工搭建在远处（{@link CowboyDuelManager#ARENA_ORIGIN}，远离主地图活动区），
 * 而 {@code SREGameWorldComponent.isPlayerOutGameAreas} <b>每 tick</b> 检查
 * {@code player.getY() < areas.playArea.minY}，一旦成立就用
 * {@code FELL_OUT_OF_TRAIN} 先 {@code killPlayer}、若还活着再补一次
 * {@code forceKillPlayer} —— 所以两人一被拉进领域就双双"被列车碾压"。
 *
 * <p>领域本应只死于战斗（决斗的胜负判定）。这里在所有死亡的唯一入口
 * {@link GameUtils#killPlayer} HEAD 兜底：若受害者享有决斗领域的环境免疫
 * （见 {@link CowboyDuelManager#hasDuelEnvironmentImmunity}）且死因属于
 * 「出界 / 落水 / 岩浆」这类环境判定，则取消这次死亡。
 * 战斗死因（左轮 / 决斗处决等）不在名单内，照常结算。
 *
 * <p>强制死亡会绕过 {@code AllowPlayerDeath*} 等否决事件，所以必须走 mixin
 * —— 与 {@code WarlockDomainDeathImmunityMixin} 完全同一套做法。
 */
@Mixin(GameUtils.class)
public abstract class CowboyDuelDeathImmunityMixin {

    /** 决斗领域内应被免疫的「环境 / 区域判定」死因（与咒术师领域保持同一口径）。 */
    private static final Set<ResourceLocation> DUEL_IMMUNE = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.CANNOT_SWIM,
            GameConstants.DeathReasons.LAVA);

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;Z"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void noellesroles$cowboyDuelEnvironmentImmunity(
            Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (deathReason == null || victim == null) {
            return;
        }
        if (!DUEL_IMMUNE.contains(deathReason)) {
            return; // 战斗死因照常结算（含 cowboy_duel 处决）
        }
        if (CowboyDuelManager.hasDuelEnvironmentImmunity(victim)) {
            ci.cancel();
        }
    }
}
