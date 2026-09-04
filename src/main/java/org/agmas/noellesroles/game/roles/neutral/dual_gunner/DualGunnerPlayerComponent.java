package org.agmas.noellesroles.game.roles.neutral.dual_gunner;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.OptionalInt;

/**
 * 双枪客角色组件 - 中立独立胜利
 * - 刷新时必定获得黄油手修饰符（开局修饰符重随后每 40 tick 补回）
 * - 开局向全体玩家播报入场公告“空气中弥漫着左轮的火药味”（同其他中立角色，见 OnGameTrueStarted）；此后双枪客存活期间每 60s 循环播报一次（见 serverTick）
 * - 场上剩余 总人数/2 人时：获得双枪-右手，解锁透视
 * - 场上剩余 总人数/3 - 2 人时：获得双枪-左手，自动装配到副手
 * - 在场时游戏不会结束；胜利条件为除坠木/皮革嘎的外独自存活（判定见 CustomWinnerClass）
 */
public class DualGunnerPlayerComponent implements RoleComponent, ServerTickingComponent {

    static {
        // 双枪客击杀玩家不掉枪（含误杀平民）：双枪客与双枪绑定，
        // 掉枪会破坏其核心玩法；该监听同时覆盖双枪与原左轮的射击掉落判定。
        // 组件类被 ModComponents 引用时即加载，static 块随之执行（同 Fool 模式）。
        AllowShootRevolverDrop.EVENT.register((shooter, target) -> {
            if (shooter == null || shooter.level() == null) {
                return TrueFalseResult.PASS;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(shooter.level());
            if (gameWorld != null && gameWorld.isRole(shooter, ModRoles.DUAL_GUNNER)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }

    public static final ComponentKey<DualGunnerPlayerComponent> KEY = ModComponents.DUAL_GUNNER;

    private final Player player;

    /** 是否已发放双枪-右手（同时解锁透视） */
    public boolean rightGunGiven = false;
    /** 是否已发放双枪-左手 */
    public boolean leftGunGiven = false;
    /** 透视是否已解锁（同步到客户端供 InstinctRenderer 使用） */
    public boolean espUnlocked = false;

    /** 入场提示循环播报间隔（tick）- 每 60s 一次 */
    private static final int ENTRY_BROADCAST_INTERVAL = 60 * 20;
    /** 下一次入场提示播报的游戏时间；-1 表示尚未安排 */
    private long nextEntryBroadcastTime = -1L;

    public DualGunnerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        rightGunGiven = false;
        leftGunGiven = false;
        espUnlocked = false;
        nextEntryBroadcastTime = -1L;

        // 强制赋予黄油手修饰符
        applyButterFingers();
        sync();
    }

    @Override
    public void clear() {
        rightGunGiven = false;
        leftGunGiven = false;
        espUnlocked = false;
        nextEntryBroadcastTime = -1L;

        // 移除黄油手修饰符
        if (player != null && player.level() != null) {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
            if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.BUTTER_FINGERS)) {
                modifiers.removeModifier(player.getUUID(), TraitorAndModifiers.BUTTER_FINGERS);
            }
        }
        sync();
    }

    public void sync() {
        ModComponents.DUAL_GUNNER.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    /** 强制赋予黄油手修饰符（幂等） */
    private void applyButterFingers() {
        if (!(player instanceof ServerPlayer)) return;
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (!modifiers.isModifier(player.getUUID(), TraitorAndModifiers.BUTTER_FINGERS)) {
            modifiers.addModifier(player.getUUID(), TraitorAndModifiers.BUTTER_FINGERS);
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return;
        if (!gameWorld.isRole(player, ModRoles.DUAL_GUNNER)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // 入场提示循环播报：双枪客存活期间每 60s 向全体玩家播报一次
        long gameTime = serverLevel.getGameTime();
        if (nextEntryBroadcastTime < 0L) {
            // 开局入场公告已由 OnGameTrueStarted 播报，这里从 60s 后开始循环
            nextEntryBroadcastTime = gameTime + ENTRY_BROADCAST_INTERVAL;
        } else if (gameTime >= nextEntryBroadcastTime) {
            broadcastEntry(serverLevel);
            nextEntryBroadcastTime = gameTime + ENTRY_BROADCAST_INTERVAL;
        }

        // 强制保持黄油手修饰符：开局 assignModifiers 会清空全部修饰符再随机分配，
        // init() 中加上的黄油手会被清掉，这里每 40 tick 补回，确保双枪客局内始终持有
        if (player.level().getGameTime() % 40 == 0) {
            applyButterFingers();
        }

        // 人数阈值判定：总人数以开局人数为准
        int totalPlayers = gameWorld.getStartingPlayerCount();
        if (totalPlayers <= 0) {
            totalPlayers = gameWorld.getPlayerCount();
        }
        int aliveCount = 0;
        for (ServerPlayer p : serverLevel.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p)) {
                aliveCount++;
            }
        }

        // 剩余 总人数/2 人：获得双枪-右手 + 解锁透视
        if (!rightGunGiven && aliveCount <= totalPlayers / 2) {
            rightGunGiven = true;
            espUnlocked = true;
            giveRightGun(sp);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.dual_gunner.right_gun")
                            .withStyle(ChatFormatting.GOLD),
                    true);
            sync();
        }

        // 剩余 总人数/3 - 2 人：获得双枪-左手，自动装配到副手
        if (!leftGunGiven && aliveCount <= totalPlayers / 3 - 2) {
            leftGunGiven = true;
            giveLeftGunToOffhand(sp);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.dual_gunner.left_gun")
                            .withStyle(ChatFormatting.GOLD),
                    true);
            sync();
        }
    }

    /** 向全体玩家循环播报双枪客入场提示 */
    private void broadcastEntry(ServerLevel serverLevel) {
        Component message = Component
                .translatable("message.noellesroles.dual_gunner.entry")
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : serverLevel.players()) {
            BroadcastCommand.BroadcastMessage(p, message);
        }
    }

    /** 发放双枪-右手：主手为空则直接装到主手，否则放入背包 */
    private void giveRightGun(ServerPlayer sp) {
        ItemStack gun = new ItemStack(ModItems.DUAL_PISTOL_RIGHT);
        if (sp.getMainHandItem().isEmpty()) {
            sp.setItemInHand(InteractionHand.MAIN_HAND, gun);
        } else if (!sp.getInventory().add(gun)) {
            sp.drop(gun, false);
        }
    }

    /** 发放双枪-左手：自动装配到副手，原副手物品尝试移入背包，放不下则掉落 */
    private void giveLeftGunToOffhand(ServerPlayer sp) {
        ItemStack oldOffhand = sp.getOffhandItem();
        if (!oldOffhand.isEmpty()) {
            ItemStack toMove = oldOffhand.copy();
            sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (!sp.getInventory().add(toMove)) {
                sp.drop(toMove, false);
            }
        }
        sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.DUAL_PISTOL_LEFT));
    }

    /**
     * 双枪客独立胜利判定：除坠木/皮革嘎的外，双枪客为唯一存活玩家时获胜。
     * （坠木/皮革嘎的不计入击杀目标，同亡命徒的处理方式）
     * 供 CustomWinnerClass 的 AllowGameEnd 监听调用。
     */
    public static boolean checkDualGunnerVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        boolean dualGunnerAlive = false;
        int aliveCount = 0;
        for (ServerPlayer sp : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                continue;
            }
            // 坠木/皮革嘎的不计入击杀目标（同亡命徒）
            if (gameWorld.isRole(sp, ModRoles.ZHUIMU) || gameWorld.isRole(sp, ModRoles.PIGE)) {
                continue;
            }
            aliveCount++;
            if (gameWorld.isRole(sp, ModRoles.DUAL_GUNNER)) {
                dualGunnerAlive = true;
            }
        }
        if (dualGunnerAlive && aliveCount == 1) {
            RoleUtils.customWinnerWin(serverLevel,
                    GameUtils.WinStatus.CUSTOM,
                    ModRoles.DUAL_GUNNER_ID.getPath(),
                    OptionalInt.of(ModRoles.DUAL_GUNNER.color()));
            return true;
        }
        return false;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("RightGunGiven", rightGunGiven);
        tag.putBoolean("LeftGunGiven", leftGunGiven);
        tag.putBoolean("EspUnlocked", espUnlocked);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        rightGunGiven = tag.getBoolean("RightGunGiven");
        leftGunGiven = tag.getBoolean("LeftGunGiven");
        espUnlocked = tag.getBoolean("EspUnlocked");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
