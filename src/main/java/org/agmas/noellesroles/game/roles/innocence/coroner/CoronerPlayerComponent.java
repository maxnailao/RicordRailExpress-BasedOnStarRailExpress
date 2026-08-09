package org.agmas.noellesroles.game.roles.innocence.coroner;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 验尸官玩家组件
 *
 * 乘客阵营
 *
 * 技能（搬尸，参照葬仪曳柩）：
 * - 对着尸体按下技能键，搬起尸体举过头顶，再次按下放下
 * - 搬起后10秒自动落下
 * - 放下（手动或自动）后进入90秒冷却
 */
public class CoronerPlayerComponent extends SREAbilityPlayerComponent {

    /** 组件键 */
    public static final org.ladysnake.cca.api.v3.component.ComponentKey<CoronerPlayerComponent> KEY = org.ladysnake.cca.api.v3.component.ComponentRegistry
            .getOrCreate(
                    ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "coroner"),
                    CoronerPlayerComponent.class);

    // 技能参数
    public static final int CARRY_COOLDOWN = 90 * 20; // 技能CD：90秒
    public static final int CARRY_DURATION = 10 * 20; // 搬起后10秒自动落下
    public static final double CARRY_RANGE = 4.0;

    private final Player player;

    /** 技能冷却 */
    public int cooldown = 0;

    /** 已搬起的时长（tick，用于10秒自动落下） */
    public int carryTicks = 0;

    /** 正在搬起的尸体UUID */
    public UUID carriedBodyUuid = null;

    /** 正在搬起的尸体实体（瞬态） */
    private transient PlayerBodyEntity carriedBody = null;

    public CoronerPlayerComponent(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.cooldown = 0;
        this.carryTicks = 0;
        this.carriedBodyUuid = null;
        this.carriedBody = null;
        this.sync();
    }

    @Override
    public void clear() {
        // 放下尸体并恢复重力
        if (this.carriedBody != null && this.carriedBody.isAlive()) {
            this.carriedBody.setNoGravity(false);
            this.carriedBody = null;
        }
        this.carriedBodyUuid = null;
        this.init();
    }

    @Override
    public int getCooldown() {
        return cooldown;
    }

    @Override
    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
        this.sync();
    }

    /**
     * 使用搬尸技能：搬起/放下尸体
     */
    @Override
    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            return false;
        }

        // 检查是否为验尸官角色
        if (!gameWorldComponent.isRole(player, ModRoles.CORONER)) {
            return false;
        }

        // 检查冷却
        if (this.cooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.coroner.carry.cooldown",
                                    (this.cooldown + 19) / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 如果正在搬起尸体，放下它
        if (this.carriedBody != null && this.carriedBody.isAlive()) {
            releaseBody(serverPlayer, "message.noellesroles.coroner.carry.release");
            return true;
        }

        // 尝试搬起尸体
        PlayerBodyEntity targetBody = findLookedAtBody(serverPlayer);
        if (targetBody == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.coroner.carry.no_body")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 不进入冷却
        }

        // 播放穿上盔甲的音效
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0f, 1.0f);

        // 取消尸体重力，开始搬起
        targetBody.setNoGravity(true);
        this.carriedBody = targetBody;
        this.carriedBodyUuid = targetBody.getUUID();
        this.carryTicks = 0;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.coroner.carry.start")
                        .withStyle(ChatFormatting.GRAY),
                true);
        return true;
    }

    /**
     * 放下尸体（将尸体放在玩家前方一格），进入冷却
     */
    private void releaseBody(ServerPlayer serverPlayer, String messageKey) {
        Vec3 lookVec = serverPlayer.getLookAngle();
        Vec3 dropPos = serverPlayer.position().add(lookVec.x, 0, lookVec.z);
        this.carriedBody.setPos(dropPos.x, dropPos.y, dropPos.z);
        // 恢复尸体重力
        this.carriedBody.setNoGravity(false);
        this.carriedBody = null;
        this.carriedBodyUuid = null;
        this.carryTicks = 0;

        // 进入90秒冷却
        this.cooldown = CARRY_COOLDOWN;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable(messageKey).withStyle(ChatFormatting.GRAY),
                true);
    }

    /**
     * 查找当前看向的尸体
     */
    private PlayerBodyEntity findLookedAtBody(ServerPlayer serverPlayer) {
        double maxDistance = CARRY_RANGE;
        PlayerBodyEntity closestBody = null;
        double closestDistance = maxDistance;

        Vec3 eyePos = serverPlayer.getEyePosition();
        Vec3 lookVec = serverPlayer.getViewVector(1.0f);

        for (PlayerBodyEntity body : serverPlayer.level().getEntitiesOfClass(PlayerBodyEntity.class,
                new AABB(eyePos.x - maxDistance, eyePos.y - maxDistance, eyePos.z - maxDistance,
                        eyePos.x + maxDistance, eyePos.y + maxDistance, eyePos.z + maxDistance))) {
            if (org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                continue;
            }

            Vec3 bodyPos = body.position();
            Vec3 toBody = bodyPos.subtract(eyePos);
            double dot = toBody.normalize().dot(lookVec);

            if (dot > 0.9) { // 大约25度角
                double distance = eyePos.distanceTo(bodyPos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestBody = body;
                }
            }
        }

        return closestBody;
    }

    /**
     * 服务端tick
     */
    @Override
    public void serverTick() {
        var gwc = SREGameWorldComponent.KEY.get(player.level());
        if (!gwc.isRole(player, ModRoles.CORONER)) {
            // 不再是验尸官角色，强制解除搬起状态并恢复重力
            if (this.carriedBody != null) {
                if (this.carriedBody.isAlive())
                    this.carriedBody.setNoGravity(false);
                this.carriedBody = null;
                this.carriedBodyUuid = null;
                this.carryTicks = 0;
                this.sync();
            }
            return;
        }

        // 旁观者模式立刻解除搬起
        if (player.isSpectator()) {
            if (this.carriedBody != null) {
                if (this.carriedBody.isAlive())
                    this.carriedBody.setNoGravity(false);
                this.carriedBody = null;
                this.carriedBodyUuid = null;
                this.carryTicks = 0;
                this.sync();
            }
            return;
        }

        // 玩家死亡时立即解除搬起
        if (!player.isAlive()) {
            if (this.carriedBody != null) {
                if (this.carriedBody.isAlive())
                    this.carriedBody.setNoGravity(false);
                this.carriedBody = null;
                this.carriedBodyUuid = null;
                this.carryTicks = 0;
                this.sync();
            }
            return;
        }

        if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME)) {
            return;
        }

        // 减少技能冷却
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 60 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 更新搬起的尸体位置（举在头顶2.1格高处）
        if (this.carriedBody != null && this.carriedBody.isAlive()) {
            Vec3 playerPos = player.position();
            this.carriedBody.setPos(playerPos.x, playerPos.y + 2.1, playerPos.z);
            this.carriedBody.setYRot(player.getYRot());
            this.carriedBody.setYHeadRot(player.getYRot());
            this.carriedBody.yBodyRot = player.getYRot();
            // 通用物证·拖痕：记录该尸体被拖动过（首次记录即同步）
            if (this.carriedBody.getPlayerUuid() != null) {
                gwc.markCorpseDragged(this.carriedBody.getPlayerUuid());
            }

            // 搬起时长累计，10秒后自动落下
            this.carryTicks++;
            if (this.carryTicks >= CARRY_DURATION && player instanceof ServerPlayer serverPlayer) {
                releaseBody(serverPlayer, "message.noellesroles.coroner.carry.auto_drop");
            }
        } else if (this.carriedBody != null) {
            // 尸体已消失，自动解除搬起并使技能进入冷却
            this.carriedBody = null;
            this.carriedBodyUuid = null;
            this.carryTicks = 0;
            this.cooldown = CARRY_COOLDOWN;
            this.sync();
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("Cooldown", this.cooldown);
        tag.putInt("CarryTicks", this.carryTicks);
        if (this.carriedBodyUuid != null) {
            tag.putUUID("CarriedBodyUuid", this.carriedBodyUuid);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.cooldown = tag.getInt("Cooldown");
        this.carryTicks = tag.getInt("CarryTicks");
        if (tag.contains("CarriedBodyUuid")) {
            this.carriedBodyUuid = tag.getUUID("CarriedBodyUuid");
            if (player.level() != null) {
                List<PlayerBodyEntity> bodies = player.level().getEntitiesOfClass(PlayerBodyEntity.class,
                        new AABB(player.getX() - 5, player.getY() - 5, player.getZ() - 5,
                                player.getX() + 5, player.getY() + 5, player.getZ() + 5));
                for (PlayerBodyEntity body : bodies) {
                    if (org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                        continue;
                    }
                    if (body.getUUID().equals(this.carriedBodyUuid)) {
                        this.carriedBody = body;
                        break;
                    }
                }
            }
        } else {
            this.carriedBodyUuid = null;
            this.carriedBody = null;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
