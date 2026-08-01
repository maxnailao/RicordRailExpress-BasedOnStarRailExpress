package org.agmas.noellesroles.game.roles.innocence.jiahao;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 嘉豪角色组件
 *
 * 主动技能（G键）：选择歌曲播放，播放期间皮肤变为嘉豪史蒂夫贴图，持续30秒，CD100秒
 * 副技能（Shift+G）：花费300金币，使半径8格玩家视角注视于自己，自身高亮15秒，皮肤变为嘉豪史蒂夫
 *
 * 嘉豪为好人阵营（乘客阵营）
 */
public class JiahaoPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 */
    public static final ComponentKey<JiahaoPlayerComponent> KEY = ModComponents.JIAHAO;

    // ==================== 常量定义 ====================

    /** 歌曲数量 */
    public static final int MUSIC_COUNT = 3;

    /** 歌曲播放列表 */
    public static final SoundEvent[] MUSIC_DISCS = {
            NRSounds.JIAHAO_MUSIC_1,
            NRSounds.JIAHAO_MUSIC_2,
            NRSounds.JIAHAO_MUSIC_3
    };

    /** 皮肤持续时间（30秒 = 600 tick） */
    public static final int SKIN_DURATION = 600;

    /** 副技能高亮持续时间（15秒 = 300 tick） */
    public static final int SPOTLIGHT_DURATION = 300;

    /** 副技能范围（8格） */
    public static final double SPOTLIGHT_RANGE = 8.0;

    /** 副技能费用（300金币） */
    public static final int SPOTLIGHT_COST = 300;

    /** 音乐播放音量（原始音量 * 0.5，避免炸耳） */
    public static final float MUSIC_VOLUME = 0.5F;

    // ==================== 状态字段 ====================

    private final Player player;

    /** 是否已激活（角色分配后） */
    public boolean isActive = false;

    /** 当前选择的歌曲索引（0-2） */
    public int currentMusicIndex = 0;

    /** 皮肤剩余持续 tick */
    public int skinRemainingTicks = 0;

    /** 副技能高亮剩余 tick */
    public int spotlightRemainingTicks = 0;

    // ==================== 构造函数 ====================

    public JiahaoPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(net.minecraft.server.level.ServerPlayer p) {
        // 同步给所有玩家，以便其他玩家能看到皮肤变化
        return true;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== 生命周期 ====================

    @Override
    public void init() {
        this.isActive = true;
        this.currentMusicIndex = 0;
        this.skinRemainingTicks = 0;
        this.spotlightRemainingTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.isActive = false;
        this.currentMusicIndex = 0;
        this.skinRemainingTicks = 0;
        this.spotlightRemainingTicks = 0;
        if (player != null) {
            player.removeEffect(MobEffects.GLOWING);
        }
        this.sync();
    }

    // ==================== 技能逻辑 ====================

    /**
     * 检查是否为激活的嘉豪角色
     */
    public boolean isActiveJiahao() {
        if (!isActive || player == null || player.level().isClientSide())
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.JIAHAO);
    }

    /**
     * 主技能：播放指定歌曲
     * 播放期间皮肤变为嘉豪史蒂夫，持续30秒
     *
     * @param songIndex 歌曲索引（0-2）
     * @return 是否成功使用
     */
    public boolean usePlayMusic(int songIndex) {
        if (!isActiveJiahao())
            return false;
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        ServerLevel world = serverPlayer.serverLevel();

        // 设置当前歌曲
        this.currentMusicIndex = songIndex;

        // 播放指定音乐（音量50%）
        SoundEvent music = MUSIC_DISCS[songIndex];
        world.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                music,
                SoundSource.RECORDS,
                MUSIC_VOLUME,
                1.0F
        );

        // 设置皮肤持续时间
        this.skinRemainingTicks = SKIN_DURATION;

        // 发送消息给嘉豪玩家
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.jiahao.music_played", songIndex + 1)
                        .withStyle(ChatFormatting.DARK_GRAY),
                true);

        this.sync();
        return true;
    }


    /**
     * 副技能：花费300金币，使半径8格玩家视角注视于自己
     * 自身高亮15秒，皮肤变为嘉豪史蒂夫，同时播放当前歌曲
     *
     * @return 是否成功使用
     */
    public boolean useSpotlight() {
        if (!isActiveJiahao())
            return false;
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        // 检查金币是否足够
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(serverPlayer);
        if (shop.balance < SPOTLIGHT_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.jiahao.not_enough_coins", SPOTLIGHT_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 扣除金币
        shop.addToBalance(-SPOTLIGHT_COST);
        shop.sync();

        ServerLevel world = serverPlayer.serverLevel();
        int affectedCount = 0;

        // 遍历范围内的所有玩家，让他们看向嘉豪
        for (Player target : world.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;

            double distance = target.distanceToSqr(player);
            if (distance > SPOTLIGHT_RANGE * SPOTLIGHT_RANGE)
                continue;

            if (target instanceof ServerPlayer serverTarget) {
                // 计算目标应该看向的方向
                double dx = player.getX() - target.getX();
                double dy = (player.getY() + player.getEyeHeight(player.getPose()))
                        - (target.getY() + target.getEyeHeight(target.getPose()));
                double dz = player.getZ() - target.getZ();

                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));

                // 设置玩家视角
                serverTarget.connection.teleport(
                        target.getX(), target.getY(), target.getZ(),
                        yaw, pitch);

                affectedCount++;
            }
        }

        // 播放当前歌曲（音量50%）
        SoundEvent music = MUSIC_DISCS[currentMusicIndex];
        world.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                music,
                SoundSource.RECORDS,
                MUSIC_VOLUME,
                1.0F
        );

        // 自身高亮15秒
        this.spotlightRemainingTicks = SPOTLIGHT_DURATION;
        player.addEffect(new MobEffectInstance(
                MobEffects.GLOWING,
                SPOTLIGHT_DURATION + 5,
                0,
                false,
                false,
                true
        ));

        // 皮肤变为嘉豪史蒂夫（与高亮同步）
        this.skinRemainingTicks = Math.max(this.skinRemainingTicks, SPOTLIGHT_DURATION);

        // 发送消息
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.jiahao.spotlight_used", affectedCount)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
                true);

        this.sync();
        return true;
    }

    /**
     * 检查皮肤是否激活中
     */
    public boolean isSkinActive() {
        return skinRemainingTicks > 0;
    }

    // ==================== 每 Tick 逻辑 ====================

    @Override
    public void serverTick() {
        if (!isActiveJiahao())
            return;
        if (player.isSpectator())
            return;

        // 皮肤倒计时
        if (this.skinRemainingTicks > 0) {
            this.skinRemainingTicks--;
            if (this.skinRemainingTicks % 20 == 0 || this.skinRemainingTicks == 0) {
                this.sync();
            }
        }

        // 高亮倒计时
        if (this.spotlightRemainingTicks > 0) {
            this.spotlightRemainingTicks--;
            if (this.spotlightRemainingTicks == 0) {
                player.removeEffect(MobEffects.GLOWING);
                this.sync();
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isActive", isActive);
        tag.putInt("currentMusicIndex", currentMusicIndex);
        tag.putInt("skinRemainingTicks", skinRemainingTicks);
        tag.putInt("spotlightRemainingTicks", spotlightRemainingTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isActive = tag.getBoolean("isActive");
        this.currentMusicIndex = tag.getInt("currentMusicIndex");
        this.skinRemainingTicks = tag.getInt("skinRemainingTicks");
        this.spotlightRemainingTicks = tag.getInt("spotlightRemainingTicks");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
