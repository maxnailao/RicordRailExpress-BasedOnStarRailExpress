package org.agmas.noellesroles.game.roles.killer.banyanzhe;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 扮演者玩家组件
 * - 一个不知道自己是杀手的杀手：开局随机扮演一个平民职业（退伍军人/拳击手/巡警/搜救员/工人/监察员/乌鸦/运动员）
 * - 未回忆阶段拥有扮演职业的商店，可像平民一样做任务（+金币）、捡枪，小脑惩罚不会死亡（掉枪+扣san）
 * - 回忆方式一：聊天栏发送"我想起来了"
 * - 回忆方式二：自身半径5格内仅存在杀手阵营玩家并持续10秒
 * - 回忆成功后转变为模仿者
 */
public class BanyanzhePlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<BanyanzhePlayerComponent> KEY = ModComponents.BANYANZHE;

    /** 小脑惩罚的死亡原因（XiaoNaoHandler 对误杀者使用） */
    private static final ResourceLocation XIAONAO_DEATH_REASON = Noellesroles.id("shot_innocent");

    /** 可扮演的平民职业池 */
    public static final List<ResourceLocation> DISGUISE_POOL = List.of(
            ModRoles.VETERAN_ID, // 退伍军人
            ModRoles.FIGHTER_ID, // 拳击手（斗士）
            ModRoles.PATROLLER_ID, // 巡警
            ModRoles.RESCUER_ID, // 搜救员
            ModRoles.WORKER_ID, // 工人
            ModRoles.MONITOR_ID, // 监察员
            ModRoles.WUYAGE_NANBANJIUUBIEBAN_ID, // 乌鸦
            ModRoles.ATHLETE_ID // 运动员
    );

    static {
        // 回忆方式一：聊天栏发送"我想起来了"（容忍空白/全角空格与前后多余文字）
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            try {
                String text = message.signedContent();
                // 去掉所有空白字符（含全角空格）后再判断，避免输入法带入不可见字符导致判定失败
                String normalized = text.replaceAll("[\\s\\u3000]+", "");
                if (!normalized.contains("我想起来了") && !normalized.contains("我想起來了")) {
                    return true;
                }
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sender.level());
                if (game == null || !game.isRunning()) {
                    Noellesroles.LOGGER.info("[扮演者] 回忆短语命中但游戏未运行，忽略: {}", sender.getGameProfile().getName());
                    return true;
                }
                if (!game.isRole(sender, ModRoles.BANYANZHE) || !GameUtils.isPlayerAliveAndSurvival(sender)) {
                    return true;
                }
                BanyanzhePlayerComponent comp = ModComponents.BANYANZHE.maybeGet(sender).orElse(null);
                if (comp != null && !comp.recalled) {
                    Noellesroles.LOGGER.info("[扮演者] {} 通过聊天回忆成功", sender.getGameProfile().getName());
                    comp.recallSuccess(sender);
                }
            } catch (Exception e) {
                Noellesroles.LOGGER.error("[扮演者] 聊天回忆处理异常", e);
            }
            return true;
        });

        // 小脑惩罚拦截：扮演者误杀平民时不死亡，改为掉枪并扣san
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!XIAONAO_DEATH_REASON.equals(deathReason))
                return true;
            if (!(victim instanceof ServerPlayer sp))
                return true;
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
            if (game == null || !game.isRunning())
                return true;
            if (!game.isRole(sp, ModRoles.BANYANZHE))
                return true;
            BanyanzhePlayerComponent comp = ModComponents.BANYANZHE.maybeGet(sp).orElse(null);
            if (comp == null || comp.recalled)
                return true;
            // 不会死亡：掉落所有枪械并扣除san
            RoleUtils.dropAndClearAllSatisfiedItems(sp, TMMItemTags.GUNS);
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(sp);
            if (mood != null) {
                mood.setMood(mood.getMood() - NoellesRolesConfig.HANDLER.instance().banyanzheXiaoNaoSanLoss);
            }
            sp.displayClientMessage(Component.translatable("message.noellesroles.banyanzhe.xiaonao")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        });
    }

    private final Player player;

    /** 当前扮演的职业ID（仅服务端写入，同步给本人客户端用于U键介绍伪装） */
    public ResourceLocation disguiseRoleId = null;
    /** 是否已回忆起真实身份 */
    public boolean recalled = false;
    /** 周围仅存杀手阵营的累计tick（回忆方式二） */
    private int killerOnlyProximityTicks = 0;

    public BanyanzhePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return sp == this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 若尚未选择扮演职业，则随机挑选一个并挂载该职业的商店。
     * 允许重复调用（幂等）。
     */
    public void pickDisguiseIfAbsent() {
        if (player.level().isClientSide)
            return;
        if (disguiseRoleId != null)
            return;
        ArrayList<ResourceLocation> pool = new ArrayList<>(DISGUISE_POOL);
        Collections.shuffle(pool);
        disguiseRoleId = pool.getFirst();
        // 将扮演职业的商店挂载给扮演者（扮演者每局至多1人，全局表安全）；
        // 扮演职业无商店时挂载空列表，避免回退到杀手默认刀具商店暴露身份
        List<ShopEntry> entries = ShopContent.getShopEntries(disguiseRoleId);
        ShopContent.customEntries.put(ModRoles.BANYANZHE_ID,
                entries == null ? new ArrayList<>() : new ArrayList<>(entries));
        sync();
    }

    /**
     * 回忆成功：转变为模仿者并发出模仿者登车报幕。
     */
    public void recallSuccess(ServerPlayer sp) {
        if (recalled)
            return;
        try {
            recalled = true;
            sync();
            RoleUtils.changeRole(sp, ModRoles.IMITATOR);
            ModComponents.IMITATOR.get(sp).init();
            RoleUtils.sendWelcomeAnnouncement(sp);
            sp.displayClientMessage(Component.translatable("message.noellesroles.banyanzhe.remembered")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[扮演者] 回忆转变异常", e);
        }
    }

    /**
     * 客户端展示用：扮演者未回忆前，任何展示给本人的职业（物品栏左上角、U键介绍等）
     * 都应替换为其扮演的职业，不能让其知晓自己是扮演者。
     */
    public static io.wifi.starrailexpress.api.SRERole getDisplayedRole(Player player,
            io.wifi.starrailexpress.api.SRERole actualRole) {
        if (player == null || actualRole == null)
            return actualRole;
        if (!actualRole.identifier().equals(ModRoles.BANYANZHE_ID))
            return actualRole;
        BanyanzhePlayerComponent comp = ModComponents.BANYANZHE.maybeGet(player).orElse(null);
        if (comp != null && !comp.recalled && comp.disguiseRoleId != null) {
            io.wifi.starrailexpress.api.SRERole disguise = RoleUtils.getRole(comp.disguiseRoleId);
            if (disguise != null)
                return disguise;
        }
        return actualRole;
    }

    @Override
    public void serverTick() {
        if (recalled)
            return;
        if (!(player instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (game == null || !game.isRunning())
            return;
        if (!game.isRole(sp, ModRoles.BANYANZHE))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp))
            return;
        // 兼容非谋杀模式等未经过报幕 Mixin 的路径：兜底保证伪装职业选定（幂等）
        if (disguiseRoleId == null) {
            pickDisguiseIfAbsent();
        }
        // 周期性重同步伪装数据，避免开局同步包丢失导致客户端展示真实身份（仅同步给本人，开销极小）
        if (disguiseRoleId != null && sp.level().getGameTime() % 200 == 0) {
            sync();
        }
        // 回忆方式二：半径内仅存在杀手阵营并持续指定时长
        double radius = NoellesRolesConfig.HANDLER.instance().banyanzheRecallRadius;
        double radiusSqr = radius * radius;
        boolean hasKiller = false;
        boolean onlyKillers = true;
        for (ServerPlayer other : sp.serverLevel().players()) {
            if (other == sp)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(other))
                continue;
            if (other.distanceToSqr(sp) > radiusSqr)
                continue;
            if (game.isKillerTeam(other)) {
                hasKiller = true;
            } else {
                onlyKillers = false;
                break;
            }
        }
        if (hasKiller && onlyKillers) {
            killerOnlyProximityTicks++;
            int requiredTicks = GameConstants.getInTicks(0,
                    NoellesRolesConfig.HANDLER.instance().banyanzheRecallSeconds);
            if (killerOnlyProximityTicks >= requiredTicks) {
                recallSuccess(sp);
            }
        } else {
            killerOnlyProximityTicks = 0;
        }
    }

    @Override
    public void init() {
        disguiseRoleId = null;
        recalled = false;
        killerOnlyProximityTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
        ShopContent.customEntries.remove(ModRoles.BANYANZHE_ID);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (disguiseRoleId != null) {
            tag.putString("DisguiseRoleId", disguiseRoleId.toString());
        }
        tag.putBoolean("Recalled", recalled);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("DisguiseRoleId")) {
            disguiseRoleId = ResourceLocation.tryParse(tag.getString("DisguiseRoleId"));
        } else {
            disguiseRoleId = null;
        }
        recalled = tag.getBoolean("Recalled");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
