package org.agmas.noellesroles.game.roles.neutral.convict;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.item.ConvictHandcuffsItem;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ConvictChoiceOpenS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 重刑犯开局流程管理器（阶段 4）。
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
 * <p>抉择结果仅记录分支；三分支的具体玩法（解铐触发）在阶段 5 实现。</p>
 */
public class ConvictChoiceManager {

    /** 注册开局钩子与解铐交互。由 {@code ModEventsRegister.registerEvents()} 调用。 */
    public static void register() {
        OnGameTrueStarted.EVENT.register(ConvictChoiceManager::onGameTrueStarted);
        // 解铐交互（阶段 5）：杀手阵营/杀手方中立/警长阵营成员对重刑犯蹲下右键 → 解除手铐并触发分支
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            return tryUnlockByInteraction(player, entity);
        });
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

    /** 应用抉择：记录分支、结束计时、标记 GUI 关闭并同步。分支玩法在阶段 5 于解铐时触发。 */
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
     * 解铐交互（阶段 5）：杀手阵营 / 杀手方中立 / 警长阵营成员对被铐重刑犯蹲下右键 →
     * 解除手铐（消失、不入包）+ 置 {@code handcuffRemoved}，并按抉择触发对应分支。
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
        boolean killerSide = gameWorld.isKillerTeam(remover) || gameWorld.isNeutralForKiller(remover);
        boolean vigilante = gameWorld.isVigilanteTeam(remover);
        if (!killerSide && !vigilante) {
            return InteractionResult.PASS;
        }

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
                // 毁灭一切：解锁全局透视（颜色同双枪客）；个人商店由阶段 6 按 choice 门禁开启
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
            }
        }
    }
}
