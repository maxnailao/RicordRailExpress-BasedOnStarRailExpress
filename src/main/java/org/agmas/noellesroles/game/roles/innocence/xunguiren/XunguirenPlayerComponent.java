package org.agmas.noellesroles.game.roles.innocence.xunguiren;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 寻鬼人组件 - 技能「寻鬼」
 *
 * <p>花费 125 金币，在 8 秒内持续感应布袋鬼（诡舍·缚灵）的方位：
 * <ul>
 * <li>追踪期间服务端周期性刷新 actionbar，以罗盘样式指明布袋鬼方向与距离</li>
 * <li>布袋鬼中途死亡则感应提前结束</li>
 * <li>冷却 90 秒由统一技能系统管理（见 ModRolesInitialEventRegister）</li>
 * </ul>
 */
public class XunguirenPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<XunguirenPlayerComponent> KEY = ModComponents.XUNGUIREN;
    public static final ResourceLocation SKILL_ID = Noellesroles.id("xunguiren_track");

    /** 技能花费（金币） */
    public static final int SKILL_COST = 125;
    /** 追踪持续时间（tick）：8 秒 */
    public static final int TRACK_TICKS = 8 * 20;
    /** actionbar 罗盘刷新间隔（tick） */
    private static final int UPDATE_INTERVAL = 4;
    /** 罗盘槽位数（覆盖正前方 ±90°，每槽 22.5°） */
    private static final int COMPASS_SLOTS = 9;

    private final Player player;
    /** 追踪剩余时间（tick），0 表示未在追踪 */
    public int trackTicks;

    public XunguirenPlayerComponent(Player player) {
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
        trackTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        trackTicks = 0;
        sync();
    }

    public boolean isTracking() {
        return trackTicks > 0;
    }

    /**
     * 技能入口：花费 125 金币开始追踪布袋鬼 8 秒。返回 true 才会消耗冷却。
     */
    public boolean useSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld == null || !gameWorld.isRole(sp, ModRoles.XUNGUIREN)) {
            return false;
        }
        // 本局没有存活的布袋鬼：提示后不扣钱、不进冷却
        if (findMaChenXu(sp) == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.xunguiren.no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < SKILL_COST) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.xunguiren.not_enough_money")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        shop.addToBalance(-SKILL_COST);
        trackTicks = TRACK_TICKS;
        sync();
        sp.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        sp.displayClientMessage(Component.translatable("message.noellesroles.xunguiren.start")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp) || trackTicks <= 0) {
            return;
        }
        trackTicks--;
        ServerPlayer target = findMaChenXu(sp);
        if (target == null) {
            // 布袋鬼中途死亡：提前结束感应
            trackTicks = 0;
            sp.displayClientMessage(Component.empty(), true);
            sync();
            return;
        }
        if (trackTicks % UPDATE_INTERVAL == 0) {
            renderCompass(sp, target);
        }
        if (trackTicks <= 0) {
            sp.displayClientMessage(Component.empty(), true);
            sp.displayClientMessage(Component.translatable("message.noellesroles.xunguiren.end")
                    .withStyle(ChatFormatting.GRAY), false);
            sync();
        }
    }

    /**
     * 查找本局存活的布袋鬼玩家。
     */
    private ServerPlayer findMaChenXu(ServerPlayer sp) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld == null) {
            return null;
        }
        for (UUID uuid : gameWorld.getAllWithRole(ModRoles.MA_CHEN_XU)) {
            if (sp.level().getPlayerByUUID(uuid) instanceof ServerPlayer target
                    && target != sp && GameUtils.isPlayerAliveAndSurvival(target)) {
                return target;
            }
        }
        return null;
    }

    /**
     * 在 actionbar 绘制罗盘样式方位条：
     * {@code 寻鬼 [ • • • • ◆ • • • • ] 23m}
     *
     * <p>9 个槽位覆盖正前方 ±90°（每槽 22.5°），目标在身后时标记停在边缘槽
     * 并以 ◀ / ▶ 提示"身后左 / 身后右"。
     */
    private void renderCompass(ServerPlayer sp, ServerPlayer target) {
        double dx = target.getX() - sp.getX();
        double dz = target.getZ() - sp.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        // 指向目标所需的 yaw（MC yaw：0° 朝 +Z，90° 朝 -X）
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(targetYaw - sp.getYRot());

        char marker = '◆';
        int slot;
        if (relative < -90.0F) {
            slot = 0;
            marker = '◀';
        } else if (relative > 90.0F) {
            slot = COMPASS_SLOTS - 1;
            marker = '▶';
        } else {
            slot = Mth.clamp(Math.round(relative / 22.5F) + (COMPASS_SLOTS - 1) / 2, 0, COMPASS_SLOTS - 1);
        }

        MutableComponent bar = Component.translatable("hud.noellesroles.xunguiren.tracking")
                .withStyle(ChatFormatting.GREEN);
        bar.append(Component.literal(" [ ").withStyle(ChatFormatting.DARK_GRAY));
        for (int i = 0; i < COMPASS_SLOTS; i++) {
            if (i == slot) {
                bar.append(Component.literal(String.valueOf(marker)).withStyle(ChatFormatting.GREEN));
            } else {
                bar.append(Component.literal("•").withStyle(ChatFormatting.GRAY));
            }
            if (i < COMPASS_SLOTS - 1) {
                bar.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        bar.append(Component.literal(" ] ").withStyle(ChatFormatting.DARK_GRAY));
        bar.append(Component.literal(Math.round(distance) + "m").withStyle(ChatFormatting.WHITE));
        sp.displayClientMessage(bar, true);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("trackTicks", trackTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        trackTicks = tag.getInt("trackTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
