package org.agmas.noellesroles.mixin.client.roles.taopiaozhe;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.TaopiaozhePlayerWidget;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 逃票者物品栏界面 Mixin
 * 在物品栏界面显示已知晓阵营归属的玩家头像列表（头像边框颜色代表阵营），基于 ZhensouzheScreenMixin
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class TaopiaozheScreenMixin extends LimitedHandledScreen<InventoryMenu> implements PlayerPaginationHelper.ScreenWithChildren {
    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
        @Override
        public String getPageTranslationKey() {
            return "hud.pagination.page";
        }

        @Override
        public String getPrevTranslationKey() {
            return "hud.pagination.prev";
        }

        @Override
        public String getNextTranslationKey() {
            return "hud.pagination.next";
        }
    };

    @Shadow @Final
    public LocalPlayer player;

    @Unique
    private RoleScreenHelper<Map.Entry<UUID, Byte>> roleScreenHelper;

    public TaopiaozheScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<Map.Entry<UUID, Byte>> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                player,
                ModRoles.TAOPIAOZHE,
                this::createTaopiaozheWidget,
                TEXT_PROVIDER,
                this::drawTaopiaozheTip,
                this::getEligiblePlayers
            );
        }
        return roleScreenHelper;
    }

    @Unique
    private TaopiaozhePlayerWidget createTaopiaozheWidget(int x, int y, Map.Entry<UUID, Byte> entry, int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        PlayerInfo playerListEntry = client.player.connection.getPlayerInfo(entry.getKey());
        if (playerListEntry == null) {
            return null;
        }

        TaopiaozhePlayerWidget widget = new TaopiaozhePlayerWidget(
            (LimitedInventoryScreen) (Object) this,
            x, y, entry.getKey(), playerListEntry, entry.getValue()
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawTaopiaozheTip(GuiGraphics context, Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.noellesroles.taopiaozhe.tip");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
            point.x - textWidth / 2, point.y + 40, new Color(80, 80, 95).getRGB());
    }

    @Unique
    private List<Map.Entry<UUID, Byte>> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }

        var component = ModComponents.TAOPIAOZHE.get(client.player);
        if (component == null) {
            return List.of();
        }
        return new ArrayList<>(component.getRevealedCamps().entrySet());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onInit(CallbackInfo ci) {
        getRoleScreenHelper().onInit(this);
    }

    @Override
    public void addDrawableChild(net.minecraft.client.gui.components.Button button) {
        super.addRenderableWidget(button);
    }

    @Override
    public void removeDrawableChild(net.minecraft.client.gui.components.Button button) {
        super.removeWidget(button);
    }

    @Override
    public void clearWidgets() {
        super.clearWidgets();
    }

    @Override
    public void clearChildren() {
        super.clearWidgets();
    }
}
