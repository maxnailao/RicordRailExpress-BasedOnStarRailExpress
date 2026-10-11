package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.MenuHubScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1))
    private void tmm$replaceInventoryScreenWithLimitedInventoryScreen(Minecraft instance, Screen screen,
            Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(instance, screen);
            return;
        }

        SREGameWorldComponent gameComponent = SREClient.gameComponent;
        if (gameComponent!=null){

        if (gameComponent.getFade() > 0) {
            return;
        }


        }
        boolean aliveSurvival = SREClient.isPlayerAliveAndInSurvival();
        boolean flag = aliveSurvival;
        if(!flag && checkOnOpenInventory(player, screen)){
            flag = true;
        }

        if (!flag) {
            original.call(instance, screen);
            return;
        }

        // 局内（回合进行中且玩家存活）：打开受限物品栏，内含角色商店，可用 E 键购买并关闭；
        // 非局内（等待 / 间歇）：打开自定义菜单栏（主菜单）。
        boolean inRound = gameComponent != null && gameComponent.isRunning();
        if (inRound && instance.player != null) {
            original.call(instance, new LimitedInventoryScreen(instance.player));
        } else {
            original.call(instance, new MenuHubScreen());
        }
    }

    private static boolean checkOnOpenInventory(LocalPlayer player, Screen screen) {
        return io.wifi.starrailexpress.event.OnOpenInventory.EVENT.invoker().needOpenLimittedInventory(player, screen);
    }
}
