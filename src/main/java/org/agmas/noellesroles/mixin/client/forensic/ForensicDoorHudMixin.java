package org.agmas.noellesroles.mixin.client.forensic;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通用物证 · 第4批：破门痕。
 *
 * <p>当存活玩家看着一扇被撬棍/开锁器强行打开过的门时，在准星下方显示
 * "此门被 撬棍/开锁器 强行打开过（N 秒前）"。门实体（{@link DoorBlockEntity}，SyncingBlockEntity）
 * 会把破坏记录同步给客户端，这里直接读取，无需新增网络包。
 *
 * <p>使用原版 {@code Minecraft.hitResult} 作为方块准星结果，避免自行 raycast。
 */
@Mixin(RoleNameRenderer.class)
public abstract class ForensicDoorHudMixin {

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sre$forensicDoorHud(Font renderer, LocalPlayer player, FakeGuiGraphics context,
            DeltaTracker tickCounter, CallbackInfo ci) {
        SREConfig cfg = SREConfig.instance();
        if (cfg == null || !cfg.enableForensicEvidence || !cfg.forensicDoorMark) {
            return;
        }
        if (!SREClient.isPlayerAliveAndInSurvival()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult bhr)) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof DoorBlockEntity)) {
            be = mc.level.getBlockEntity(pos.below());
        }
        if (!(be instanceof DoorBlockEntity door)) {
            return;
        }
        long tamperedTime = door.getTamperedTime();
        if (tamperedTime < 0) {
            return;
        }
        int seconds = (int) Math.max(0, (mc.level.getGameTime() - tamperedTime) / 20);
        String toolKey = door.getTamperedTool() == 1 ? "forensic.door.crowbar" : "forensic.door.lockpick";
        Component line = Component.translatable("forensic.door.tampered",
                Component.translatable(toolKey), seconds).withColor(0xE0A030);

        context.pose().pushPose();
        context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F, 0.0F);
        context.pose().scale(0.6F, 0.6F, 1.0F);
        context.drawString(renderer, line, -renderer.width(line) / 2, 32, CommonColors.WHITE);
        context.pose().popPose();
    }
}
