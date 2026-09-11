package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.commands.BroadcastCommand;

/**
 * 关押门方块实体
 * <p>
 * 继承核心 {@link SmallDoorBlockEntity}，因此可被撬棍（核心 {@code CrowbarItem} 逻辑会调用 {@link #blast()}）
 * 强行破坏开启，也可被狱警钥匙（见 {@code DetentionDoorBlock}）正常开关。
 * </p>
 * <p>
 * 新增永久警报字段 {@code permanentlyAlarmed}（写入 NBT，不可恢复）：门被强行破坏（撬棍撬开 / 爆炸）时置真，
 * 并触发一次全场警报——复用 {@code AlarmTrapItem} 的声音组合（MASTER 全场可听）+ 一条全场播报。
 * </p>
 */
public class DetentionDoorBlockEntity extends SmallDoorBlockEntity {

    private boolean permanentlyAlarmed = false;

    public DetentionDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isPermanentlyAlarmed() {
        return this.permanentlyAlarmed;
    }

    public void setPermanentlyAlarmed(boolean permanentlyAlarmed) {
        this.permanentlyAlarmed = permanentlyAlarmed;
    }

    @Override
    public void blast() {
        super.blast();
        // 门被强行破坏（撬棍撬开或爆炸）：触发一次性、但永久记录且不可恢复的警报
        if (!this.permanentlyAlarmed) {
            this.permanentlyAlarmed = true;
            this.sync();
            triggerGlobalAlarm();
        }
    }

    private void triggerGlobalAlarm() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        BlockPos pos = this.worldPosition;
        // 复用 AlarmTrapItem 的警报声音组合（MASTER 让全场都能听到）
        this.level.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                SoundEvents.BELL_BLOCK, SoundSource.MASTER, 3.0f, 0.8f);
        this.level.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 3.0f, 0.5f);
        this.level.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                SoundEvents.WARDEN_ROAR, SoundSource.MASTER, 1.5f, 2.0f);
        // 全场播报
        if (this.level.getServer() != null) {
            Component message = Component.translatable("message.noellesroles.detention_door.alarmed")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer player : this.level.getServer().getPlayerList().getPlayers()) {
                BroadcastCommand.BroadcastMessage(player, message);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putBoolean("permanentlyAlarmed", this.permanentlyAlarmed);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.permanentlyAlarmed = nbt.getBoolean("permanentlyAlarmed");
    }
}
