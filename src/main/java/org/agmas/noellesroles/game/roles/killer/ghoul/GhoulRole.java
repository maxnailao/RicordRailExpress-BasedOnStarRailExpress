package org.agmas.noellesroles.game.roles.killer.ghoul;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.gui.PlayerBodyEntityContainer;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;

import java.util.UUID;

/**
 * 食尸鬼角色 - 杀手阵营
 * 
 * 技能同殡仪员，可以拾取尸体上的道具
 * - 最多拿取2个物品
 * - 对尸体使用技能后，尸体变为骨架，留下黑色粒子
 * - 获取尸体生前40%的金钱
 * - CD 30秒
 * - 不可拾取：保安盾、画板
 * - 拾取德林加、双截棍、处刑者手枪自动转化为左轮手枪
 */
public class GhoulRole extends NormalRole {

    public GhoulRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean canSeeBodyItems(Player player, PlayerBodyEntity body) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;
        var cca = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (cca == null || cca.gameMode == null) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return false;
        }
        // 检查冷却
        var ghoulComponent = ModComponents.GHOUL.get(serverPlayer);
        if (ghoulComponent == null || !ghoulComponent.isCooldownReady()) {
            return false;
        }
        // 检查这具尸体是否已被打开过
        if (ghoulComponent.hasOpenedCorpse(body.getUUID())) {
            return false;
        }
        // 检查是否在范围内（10格水平，3格垂直）
        double dx = serverPlayer.getX() - body.getX();
        double dy = serverPlayer.getY() - body.getY();
        double dz = serverPlayer.getZ() - body.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        return horizontalDist <= 10.0 && Math.abs(dy) <= 3.0;
    }

    @Override
    public void onClosedPlayerBodyChest(Player player, PlayerBodyEntity corpseEntity,
            PlayerBodyEntityContainer container) {
        if (corpseEntity != null) {
            UUID corpseUuid = corpseEntity.getUUID();
            GhoulPlayerComponent ghoul = ModComponents.GHOUL.get(player);
            if (ghoul != null) {
                ghoul.onCorpseOpened(corpseUuid);

                // 将尸体变为骨架（腐化状态）
                corpseEntity.setCorrupted(true);

                // 在尸体位置生成黑色粒子
                if (player.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 20; i++) {
                        serverLevel.sendParticles(
                                ParticleTypes.SMOKE,
                                corpseEntity.getX() + (serverLevel.random.nextDouble() - 0.5) * 1.0,
                                corpseEntity.getY() + serverLevel.random.nextDouble() * 0.5,
                                corpseEntity.getZ() + (serverLevel.random.nextDouble() - 0.5) * 1.0,
                                1, 0.0, 0.0, 0.0, 0.02);
                    }
                }

                // 获取尸体生前40%的金钱
                UUID deadPlayerUuid = corpseEntity.getPlayerUuid();
                if (deadPlayerUuid != null && player instanceof ServerPlayer serverPlayer) {
                    ServerPlayer deadPlayer = serverPlayer.server.getPlayerList().getPlayer(deadPlayerUuid);
                    if (deadPlayer != null) {
                        int deadPlayerCoins = PlayerEconomyManager.getCoinNum(deadPlayer);
                        int gainedCoins = (int) (deadPlayerCoins * 0.4);
                        if (gainedCoins > 0) {
                            PlayerEconomyManager.addCoinNum(serverPlayer, gainedCoins);
                            serverPlayer.displayClientMessage(
                                    Component.translatable("message.noellesroles.ghoul.coins_gained", gainedCoins)
                                            .withStyle(ChatFormatting.GOLD),
                                    true);
                        }
                    }
                }

                // 发送消息提示
                if (player instanceof ServerPlayer serverPlayer) {
                    int itemsTaken = getGhoulItemsTaken(player);
                    if (itemsTaken > 0) {
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.noellesroles.ghoul.items_taken", itemsTaken)
                                        .withStyle(ChatFormatting.DARK_RED),
                                true);
                    }
                }
            }
        }
    }

    @Override
    public boolean canGetBodyContent(int slotId, int button, ClickType clickType, Player player,
            PlayerBodyEntityContainer container, int rows, NonNullList<Slot> slots) {
        GhoulPlayerComponent ghoul = ModComponents.GHOUL.get(player);
        if (ghoul == null || !ghoul.isCooldownReady()) {
            // 冷却中，禁止任何操作
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.ghoul.on_cooldown")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        // 如果操作涉及容器槽位（索引 0 ~ rows*9-1）
        {
            Slot slot = slots.get(slotId);

            switch (clickType) {
                case THROW: // 丢出（Q键）
                    return false; // 直接禁止
                case PICKUP: // 左键点击拿起物品
                case SWAP: // 数字键交换
                case CLONE: // 中键复制
                case QUICK_MOVE: // Shift+点击
                case QUICK_CRAFT: // Ctrl+点击
                    // 食尸鬼物品限制检查
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        if (isBannedItem(stack)) {
                            return false; // 禁止拿取
                        }
                        ghoulTookItem(player);

                        // 德林加、双截棍、处刑者手枪自动转化为左轮手枪
                        if (stack.is(TMMItems.DERRINGER) || stack.is(TMMItems.NUNCHUCK) || stack.is(ModItems.EXECUTIONER_GUN)) {
                            MCItemsUtils.insertStackInFreeSlot(player, TMMItems.REVOLVER.getDefaultInstance());
                        } else {
                            MCItemsUtils.insertStackInFreeSlot(player, stack.copy());
                        }

                        // 检查是否已经拿够了2个物品
                        if (!canGhoulTakeMore(player)) {
                            // 关闭菜单
                            if (player instanceof ServerPlayer serverPlayer) {
                                serverPlayer.closeContainer();
                            }
                        }
                        slot.set(ItemStack.EMPTY);
                        slots.set(slotId, slot);
                        return false;
                    }
                    return true;
                default:
                    // 食尸鬼物品限制检查
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        if (isBannedItem(stack))
                            return false;
                    }
                    return true;
            }
        }
    }

    /**
     * 检查物品是否是食尸鬼不可拿取的物品
     * 不可拾取：保安盾、画板
     * 德林加和双截棍可拾取但自动转化为左轮手枪
     */
    private boolean isBannedItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 保安盾（防暴盾牌）
        if (stack.is(ModItems.RIOT_SHIELD)) {
            return true;
        }
        // 画板
        if (stack.is(TMMItems.DRAWING_BOARD)) {
            return true;
        }
        // 命令方块（同殡仪员）
        if (stack.is(Items.COMMAND_BLOCK) ||
                stack.is(Items.REPEATING_COMMAND_BLOCK) ||
                stack.is(Items.CHAIN_COMMAND_BLOCK)) {
            return true;
        }
        return false;
    }

    /**
     * 获取食尸鬼组件
     */
    public GhoulPlayerComponent GhCCA(Player player) {
        return GhoulPlayerComponent.KEY.get(player);
    }

    /**
     * 记录食尸鬼拿取了一个物品
     */
    public void ghoulTookItem(Player player) {
        GhCCA(player).ghoulItemsTaken++;
    }

    /**
     * 获取食尸鬼已拿取的物品数量
     */
    public int getGhoulItemsTaken(Player player) {
        return GhCCA(player).ghoulItemsTaken;
    }

    /**
     * 检查食尸鬼是否还能拿取物品（最多2个）
     */
    public boolean canGhoulTakeMore(Player player) {
        return GhCCA(player).ghoulItemsTaken < 2;
    }

    @Override
    public void startOpenPlayerBody(Player player) {
        GhCCA(player).ghoulLooting = true;
        GhCCA(player).ghoulItemsTaken = 0;
    }

    @Override
    public boolean canTakePlayerBodyItem(Player player, Container container, int slot, ItemStack stack) {
        // 检查是否已达到拿取上限（最多2个）
        if (GhCCA(player).ghoulItemsTaken >= 2) {
            return false;
        }

        // 检查是否是禁止拿取的物品
        if (isBannedItem(stack)) {
            return false;
        }

        return true;
    }

    @Override
    public void stopOpenPlayerBody(Player player) {
        GhCCA(player).ghoulLooting = false;
        GhCCA(player).ghoulItemsTaken = 0;
    }
}
