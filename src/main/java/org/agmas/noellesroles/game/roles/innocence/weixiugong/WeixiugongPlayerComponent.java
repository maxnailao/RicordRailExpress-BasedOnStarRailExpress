package org.agmas.noellesroles.game.roles.innocence.weixiugong;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 维修工组件
 *
 * 管理维修工技能：花费25金币对准灯光进行维护操作
 * - 被维护的灯在下一次关灯（blackout）后会自动亮起
 * - 亮起后维护效果消失
 * - 准心无灯则不消耗金钱
 */
public class WeixiugongPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<WeixiugongPlayerComponent> KEY = ModComponents.WEIXIUGONG;

    // ==================== 常量定义 ====================

    /** 维护花费（金币） */
    public static final int MAINTAIN_COST = 25;

    /** 最大维护射线距离（格） */
    private static final double MAX_RANGE = 5.0;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 被维护灯光的坐标列表 */
    private final List<BlockPos> maintainedLights = new ArrayList<>();

    // ==================== 构造函数 ====================

    public WeixiugongPlayerComponent(Player player) {
        this.player = player;
    }

    // ==================== 初始化/清理 ====================

    @Override
    public void init() {
        this.maintainedLights.clear();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    // ==================== 技能逻辑 ====================

    /**
     * 维护灯光
     * 对准灯光方块，花费25金币进行维护
     * 被维护的灯在下一次关灯后会自动亮起，亮起后维护效果消失
     */
    public boolean maintainLight() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 检查角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.WEIXIUGONG)) {
            return false;
        }

        // 射线检测准心对准的方块
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 targetPos = eyePos.add(lookVec.scale(MAX_RANGE));

        ClipContext context = new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = serverLevel.clip(context);

        // 检查是否命中方块
        if (hit.getType() != HitResult.Type.BLOCK) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.weixiugong.no_light_targeted")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        BlockPos targetBlockPos = hit.getBlockPos();
        BlockState targetState = serverLevel.getBlockState(targetBlockPos);

        // 检查目标方块是否为灯光（具有LIT和ACTIVE属性）
        if (!targetState.hasProperty(BlockStateProperties.LIT) || !targetState.hasProperty(TMMProperties.ACTIVE)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.weixiugong.no_light_targeted")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 检查是否已经维护过这个灯
        if (maintainedLights.contains(targetBlockPos)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.weixiugong.already_maintained")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < MAINTAIN_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.weixiugong.not_enough_money", MAINTAIN_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 扣除金币
        shop.addToBalance(-MAINTAIN_COST);

        // 记录被维护灯光的坐标
        maintainedLights.add(targetBlockPos.immutable());

        // 播放维护音效
        serverLevel.playSound(null,
                targetBlockPos,
                SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0f, 1.5f);

        serverPlayer.displayClientMessage(
                Component.translatable("hud.noellesroles.weixiugong.maintained")
                        .withStyle(ChatFormatting.GREEN),
                true);

        this.sync();
        return true;
    }

    // ==================== Tick 处理 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        if (maintainedLights.isEmpty()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 检测被维护灯光是否被关闭（blackout后LIT=false）
        Iterator<BlockPos> iterator = maintainedLights.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            BlockState state = serverLevel.getBlockState(pos);

            // 检查方块是否仍然是灯光方块
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE)) {
                // 方块已被破坏或替换，移除记录
                iterator.remove();
                continue;
            }

            // 如果灯被关闭了（LIT=false），自动亮起
            if (!state.getValue(BlockStateProperties.LIT)) {
                // 自动亮起灯光
                serverLevel.setBlockAndUpdate(pos,
                        state.setValue(BlockStateProperties.LIT, true).setValue(TMMProperties.ACTIVE, true));

                // 播放亮起音效
                serverLevel.playSound(null, pos,
                        SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.5f, 2.0f);

                // 移除维护记录（维护效果消失）
                iterator.remove();
            }
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        ModComponents.WEIXIUGONG.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (BlockPos pos : maintainedLights) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            list.add(posTag);
        }
        tag.put("maintainedLights", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        maintainedLights.clear();
        ListTag list = tag.getList("maintainedLights", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            maintainedLights.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }
}
