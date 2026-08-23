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
 * - 存活期间全程循环播报"空气中弥漫着左轮的火药味"
 * - 场上剩余 总人数/2 人时：获得双枪-右手，解锁透视
 * - 场上剩余 总人数/3 - 2 人时：获得双枪-左手，自动装配到副手
 * - 在场时游戏不会结束；胜利条件为独自存活（判定见 CustomWinnerClass）
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

    /** 播报间隔：60秒 */
    private static final int BROADCAST_INTERVAL_TICKS = 60 * 20;

    private final Player player;

    /** 是否已发放双枪-右手（同时解锁透视） */
    public boolean rightGunGiven = false;
    /** 是否已发放双枪-左手 */
    public boolean leftGunGiven = false;
    /** 透视是否已解锁（同步到客户端供 InstinctRenderer 使用） */
    public boolean espUnlocked = false;

    /** 播报计时（仅服务端使用，无需同步） */
    private int broadcastTicks = 0;

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
        // 开局稍等几秒再首次播报，避免与开场公告重叠
        broadcastTicks = BROADCAST_INTERVAL_TICKS - 5 * 20;

        // 强制赋予黄油手修饰符
        applyButterFingers();
        sync();
    }

    @Override
    public void clear() {
        rightGunGiven = false;
        leftGunGiven = false;
        espUnlocked = false;
        broadcastTicks = 0;

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

        // 强制保持黄油手修饰符：开局 assignModifiers 会清空全部修饰符再随机分配，
        // init() 中加上的黄油手会被清掉，这里每 40 tick 补回，确保双枪客局内始终持有
        if (player.level().getGameTime() % 40 == 0) {
            applyButterFingers();
        }

        // 全程播报：存活期间每 60 秒向全体玩家广播
        broadcastTicks++;
        if (broadcastTicks >= BROADCAST_INTERVAL_TICKS) {
            broadcastTicks = 0;
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.noellesroles.dual_gunner.broadcast")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    false);
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
     * 双枪客独立胜利判定：双枪客为唯一存活玩家时获胜。
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
