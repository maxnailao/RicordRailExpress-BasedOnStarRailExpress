package org.agmas.noellesroles.game.roles.neutral.convict;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.item.ConvictHandcuffsItem;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ConvictChoiceOpenS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 重刑犯开局流程管理器。
 *
 * <p>游戏正式开始（{@link OnGameTrueStarted}）时，对每名重刑犯：</p>
 * <ol>
 *   <li>扫描游戏区域定位 {@link ModBlocks#CONVICT_SPAWN_BLOCK 重刑犯生成方块}，将其传送到方块上方；</li>
 *   <li>用 {@link HandCuffsItem#putOnHandCuff 静态佩戴 API} 戴上
 *       {@link ModItems#CONVICT_HANDCUFFS 重刑犯手铐}（无限耐久、无法挣脱）；</li>
 *   <li>发送 {@link ConvictChoiceOpenS2CPacket} 开启「做出你的抉择」GUI，并在
 *       {@link ConvictPlayerComponent} 上启动倒计时（超时默认「毁灭一切」，见组件 serverTick）。</li>
 * </ol>
 *
 * <p>抉择结果仅记录分支；三分支的具体玩法在解除手铐时触发（见 {@link #applyBranch}）。</p>
 */
public class ConvictChoiceManager {

    /** 引导式解铐所需累计 tick（20 = 1 秒）。 */
    private static final int UNLOCK_CHANNEL_TICKS = 20;
    /** 引导式解铐的有效距离（格）。 */
    private static final double UNLOCK_REACH = 3.0D;
    /** 正在引导解铐的玩家 → 其目标重刑犯。 */
    private static final Map<UUID, UUID> CHANNEL_TARGETS = new HashMap<>();
    /** 正在引导解铐的玩家 → 已累计 tick。 */
    private static final Map<UUID, Integer> CHANNEL_TICKS = new HashMap<>();

    /** 注册开局钩子与解铐交互。由 {@code ModEventsRegister.registerEvents()} 调用。 */
    public static void register() {
        OnGameTrueStarted.EVENT.register(ConvictChoiceManager::onGameTrueStarted);
        // 解铐方式一：合格阵营成员对被铐重刑犯「蹲下 + 空手右键」→ 立即解除
        // （原版在「蹲下且手持任意物品」时不会发出实体交互包，故此路径仅空手时可达）
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            return tryUnlockByInteraction(player, entity);
        });
        // 解铐方式二（兜底）：蹲下并注视被铐重刑犯约 1 秒 → 解除；手持任意物品同样有效
        ServerTickEvents.END_WORLD_TICK.register(ConvictChoiceManager::tickUnlockChannels);
    }

    private static void onGameTrueStarted(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        BlockPos spawnPos = null;
        boolean scanned = false;
        for (ServerPlayer player : serverLevel.players()) {
            if (!gameWorld.isRole(player, ModRoles.CONVICT) || !GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (!scanned) {
                // 每局最多 1 名重刑犯，生成方块只扫描一次
                spawnPos = findConvictSpawnBlock(serverLevel);
                scanned = true;
            }
            setupConvict(player, spawnPos);
        }
    }

    /** 传送 + 戴手铐 + 开启抉择 GUI + 启动计时。 */
    private static void setupConvict(ServerPlayer convict, BlockPos spawnPos) {
        if (spawnPos != null) {
            convict.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
        } else {
            Noellesroles.LOGGER.warn("[Convict] 未找到重刑犯生成方块，跳过传送：" + convict.getName().getString());
        }

        if (!ConvictHandcuffsItem.hasConvictHandCuff(convict)) {
            HandCuffsItem.putOnHandCuff(convict, new ItemStack(ModItems.CONVICT_HANDCUFFS));
        }

        ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.get(convict);
        int seconds = Math.max(1, NoellesRolesConfig.instance().convictChoiceSeconds);
        comp.choice = ConvictPlayerComponent.Choice.NONE;
        comp.handcuffRemoved = false;
        comp.choiceGuiOpened = true;
        comp.choiceTimeLeftTicks = seconds * 20;
        comp.sync();

        ServerPlayNetworking.send(convict, new ConvictChoiceOpenS2CPacket(seconds));
    }

    /** 扫描游戏区域，返回第一个重刑犯生成方块的位置（找不到返回 null）。 */
    private static BlockPos findConvictSpawnBlock(ServerLevel serverLevel) {
        AABB playArea = AreasWorldComponent.KEY.get(serverLevel).getPlayArea();
        if (playArea == null) {
            return null;
        }
        BlockPos min = BlockPos.containing(playArea.minX, playArea.minY, playArea.minZ);
        BlockPos max = BlockPos.containing(playArea.maxX, playArea.maxY, playArea.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (serverLevel.getBlockState(pos).is(ModBlocks.CONVICT_SPAWN_BLOCK)) {
                return pos.immutable();
            }
        }
        return null;
    }

    /**
     * 处理客户端提交的抉择（C2S）。
     *
     * @param choiceIndex 0=改过自新，1=毁灭一切，2=加入组织
     */
    public static void selectChoice(ServerPlayer player, int choiceIndex) {
        ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.get(player);
        // 仅在 GUI 已开启且尚未选择时接受提交，忽略重复/非法提交
        if (!comp.choiceGuiOpened || comp.choice != ConvictPlayerComponent.Choice.NONE) {
            return;
        }
        applyChoice(player, choiceFromIndex(choiceIndex));
    }

    /** 应用抉择：记录分支、结束计时、标记 GUI 关闭并同步。分支玩法于解除手铐时触发。 */
    public static void applyChoice(ServerPlayer player, ConvictPlayerComponent.Choice choice) {
        ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.get(player);
        comp.choice = choice;
        comp.choiceGuiOpened = false;
        comp.choiceTimeLeftTicks = 0;
        comp.sync();
    }

    private static ConvictPlayerComponent.Choice choiceFromIndex(int index) {
        return switch (index) {
            case 0 -> ConvictPlayerComponent.Choice.REFORM;
            case 2 -> ConvictPlayerComponent.Choice.JOIN;
            // 1 或任何非法值 → 毁灭一切（与超时默认一致）
            default -> ConvictPlayerComponent.Choice.DESTROY;
        };
    }

    /**
     * 解除手铐：杀手阵营 / 杀手方中立 / 警长阵营成员对被铐重刑犯解铐 →
     * 手铐消失（不入包）+ 置 {@code handcuffRemoved}，并按抉择触发对应分支。
     *
     * <p>两条触发路径共用此方法：蹲下空手右键（即时）与蹲下注视约 1 秒（引导式兜底）。</p>
     *
     * @return 成功解除返回 {@link InteractionResult#SUCCESS}，否则 {@link InteractionResult#PASS}
     */
    public static InteractionResult tryUnlockByInteraction(Player removerRaw, Entity targetRaw) {
        if (!(removerRaw instanceof ServerPlayer remover) || !(targetRaw instanceof ServerPlayer convict)
                || remover == convict) {
            return InteractionResult.PASS;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(remover.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(convict, ModRoles.CONVICT)) {
            return InteractionResult.PASS;
        }
        ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.get(convict);
        // 目标必须仍被铐住且尚未解铐
        if (comp.handcuffRemoved || !ConvictHandcuffsItem.hasConvictHandCuff(convict)) {
            return InteractionResult.PASS;
        }
        // 解除者阵营：杀手阵营 / 杀手方中立 / 警长阵营
        if (!isEligibleRemover(gameWorld, remover)) {
            return InteractionResult.PASS;
        }
        boolean killerSide = gameWorld.isKillerTeam(remover) || gameWorld.isNeutralForKiller(remover);

        // 解除手铐（消失、不入包）
        ConvictHandcuffsItem.removeConvictHandcuff(convict);
        comp.handcuffRemoved = true;
        comp.sync();

        applyBranch(convict, remover, comp, killerSide);
        return InteractionResult.SUCCESS;
    }

    /** 按当前抉择触发分支效果（解铐时调用）。分支的捡枪门禁见 {@code ModRoles.CONVICT}。 */
    private static void applyBranch(ServerPlayer convict, ServerPlayer remover, ConvictPlayerComponent comp,
            boolean removerKillerSide) {
        switch (comp.choice) {
            case REFORM ->
                // 改过自新：解铐后可捡左轮/巡警手枪转职狱警（见 ModRoles CONVICT.onPickUpItem）
                convict.displayClientMessage(Component
                        .translatable("message.noellesroles.convict.reform_unlocked").withStyle(ChatFormatting.GREEN),
                        true);
            case DESTROY -> {
                // 毁灭一切：解锁全局透视（颜色同双枪客）；个人商店按 choice 门禁开启
                comp.espUnlocked = true;
                comp.sync();
                convict.displayClientMessage(Component
                        .translatable("message.noellesroles.convict.destroy_unlocked").withStyle(ChatFormatting.RED),
                        true);
            }
            case JOIN -> {
                // 加入组织：被杀手/杀手方中立解铐 → 变为解铐者职业；被警长阵营解铐 → 变为普通杀手
                SRERole newRole = removerKillerSide
                        ? SREGameWorldComponent.KEY.get(remover.level()).getRole(remover)
                        : TMMRoles.KILLER;
                if (newRole == null) {
                    newRole = TMMRoles.KILLER;
                }
                RoleUtils.changeRole(convict, newRole);
                convict.displayClientMessage(Component
                        .translatable("message.noellesroles.convict.join_unlocked").withStyle(ChatFormatting.GOLD),
                        true);
            }
            default -> {
                // NONE：未抉择即被解铐，仅解除手铐，无分支效果
                // （若之后超时默认为毁灭一切，组件 serverTick 会补解锁透视）
            }
        }
    }

    /**
     * 引导式解铐（兜底路径）：蹲下并持续注视被铐重刑犯约 1 秒即解除手铐。
     *
     * <p>原版客户端在「蹲下且主手/副手持有任意物品」时不会发送实体交互包，
     * 因此 {@link UseEntityCallback} 的蹲下右键只在空手时可达；而游戏中解除者
     * （杀手持刀 / 警长持枪 / 狱警持钥匙）几乎必然手持物品。此路径不依赖交互包，
     * 保证解铐在任何手持状态下都能完成，进而保证改过自新可捡枪、毁灭一切可解锁透视。</p>
     */
    private static void tickUnlockChannels(ServerLevel level) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRunning()) {
            CHANNEL_TARGETS.clear();
            CHANNEL_TICKS.clear();
            return;
        }
        for (ServerPlayer remover : level.players()) {
            UUID id = remover.getUUID();
            boolean channeling = CHANNEL_TARGETS.containsKey(id);
            ServerPlayer target = channeling
                    ? level.getServer().getPlayerList().getPlayer(CHANNEL_TARGETS.get(id))
                    : null;
            // 中断：起身 / 死亡 / 阵营不再合格 / 目标丢失或不再是准星下的被铐重刑犯
            if (channeling && !isChannelValid(gameWorld, remover, target)) {
                CHANNEL_TARGETS.remove(id);
                CHANNEL_TICKS.remove(id);
                target = null;
            }
            if (target == null) {
                if (!canStartChannel(gameWorld, remover)) {
                    continue;
                }
                target = findCuffedConvictInCrosshair(remover, gameWorld);
                if (target == null) {
                    continue;
                }
                CHANNEL_TARGETS.put(id, target.getUUID());
                CHANNEL_TICKS.put(id, 0);
            }
            int progress = CHANNEL_TICKS.merge(id, 1, Integer::sum);
            if (progress >= UNLOCK_CHANNEL_TICKS) {
                CHANNEL_TARGETS.remove(id);
                CHANNEL_TICKS.remove(id);
                tryUnlockByInteraction(remover, target);
                continue;
            }
            showChannelProgress(level, remover, target, progress);
        }
        // 清理已断开连接的记录
        if (!CHANNEL_TARGETS.isEmpty()) {
            CHANNEL_TARGETS.keySet()
                    .removeIf(uuid -> level.getServer().getPlayerList().getPlayer(uuid) == null);
            CHANNEL_TICKS.keySet().retainAll(CHANNEL_TARGETS.keySet());
        }
    }

    /** 引导是否仍然有效：保持蹲下、双方存活、阵营合格，且准星下仍是同一被铐重刑犯。 */
    private static boolean isChannelValid(SREGameWorldComponent gameWorld, ServerPlayer remover,
            ServerPlayer target) {
        return target != null
                && remover.isShiftKeyDown()
                && GameUtils.isPlayerAliveAndSurvival(remover)
                && GameUtils.isPlayerAliveAndSurvival(target)
                && isEligibleRemover(gameWorld, remover)
                && findCuffedConvictInCrosshair(remover, gameWorld) == target;
    }

    /** 是否允许开始引导：蹲下、存活、阵营合格，且未手持押运拴绳（避免押运途中误解铐）。 */
    private static boolean canStartChannel(SREGameWorldComponent gameWorld, ServerPlayer remover) {
        return remover.isShiftKeyDown()
                && GameUtils.isPlayerAliveAndSurvival(remover)
                && isEligibleRemover(gameWorld, remover)
                && !remover.getMainHandItem().is(ModItems.CONVICT_ESCORT_LEASH);
    }

    /** 解除者阵营是否合格：杀手阵营 / 杀手方中立 / 警长阵营。 */
    private static boolean isEligibleRemover(SREGameWorldComponent gameWorld, ServerPlayer remover) {
        return gameWorld.isKillerTeam(remover) || gameWorld.isNeutralForKiller(remover)
                || gameWorld.isVigilanteTeam(remover);
    }

    /** 该玩家是否为「仍被铐住的重刑犯」。 */
    private static boolean isCuffedConvict(SREGameWorldComponent gameWorld, ServerPlayer player) {
        if (!gameWorld.isRole(player, ModRoles.CONVICT)) {
            return false;
        }
        ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.get(player);
        return !comp.handcuffRemoved && ConvictHandcuffsItem.hasConvictHandCuff(player);
    }

    /** 取准星下 3 格内、仍被铐住的重刑犯（无则 null）。 */
    private static ServerPlayer findCuffedConvictInCrosshair(ServerPlayer remover,
            SREGameWorldComponent gameWorld) {
        Vec3 eye = remover.getEyePosition();
        Vec3 look = remover.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(UNLOCK_REACH));
        AABB box = remover.getBoundingBox().expandTowards(look.scale(UNLOCK_REACH)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(remover, eye, end, box,
                entity -> entity instanceof ServerPlayer sp && sp != remover && isCuffedConvict(gameWorld, sp),
                UNLOCK_REACH * UNLOCK_REACH);
        return hit != null && hit.getEntity() instanceof ServerPlayer sp ? sp : null;
    }

    /** 引导进度提示：动作栏倒计时 + 目标身上的粒子。 */
    private static void showChannelProgress(ServerLevel level, ServerPlayer remover, ServerPlayer target,
            int progress) {
        double leftSeconds = Math.max(0, UNLOCK_CHANNEL_TICKS - progress) / 20.0D;
        remover.displayClientMessage(Component
                .translatable("message.noellesroles.convict.unlock_progress",
                        String.format(java.util.Locale.ROOT, "%.1f", leftSeconds))
                .withStyle(ChatFormatting.YELLOW), true);
        Vec3 pos = target.position();
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.0D, pos.z, 3, 0.15D, 0.15D, 0.15D, 0.02D);
    }
}
