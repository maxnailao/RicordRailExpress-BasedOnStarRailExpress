package org.agmas.noellesroles.mixin.client.roles.zhensouzhe;

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
import org.agmas.noellesroles.client.widget.ZhensouzhePlayerWidget;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 侦搜者物品栏界面 Mixin
 * 在物品栏界面显示可点击的玩家头像列表，花费金币查询玩家存活状态，基于 BlackkeScreenMixin
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class ZhensouzheScreenMixin extends LimitedHandledScreen<InventoryMenu> implements PlayerPaginationHelper.ScreenWithChildren {
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
    private RoleScreenHelper<UUID> roleScreenHelper;

    public ZhensouzheScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<UUID> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                player,
                ModRoles.ZHENSOUZHE,
                this::createZhensouzheWidget,
                TEXT_PROVIDER,
                this::drawZhensouzheTip,
                this::getEligiblePlayers
            );
        }
        return roleScreenHelper;
    }

    @Unique
    private ZhensouzhePlayerWidget createZhensouzheWidget(int x, int y, UUID playerUUID, int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        PlayerInfo playerListEntry = client.player.connection.getPlayerInfo(playerUUID);
        if (playerListEntry == null) {
            return null;
        }

        ZhensouzhePlayerWidget widget = new ZhensouzhePlayerWidget(
            (LimitedInventoryScreen) (Object) this,
            x, y, playerUUID, playerListEntry
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawZhensouzheTip(GuiGraphics context, Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.zhensouzhe.tip");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
            point.x - textWidth / 2, point.y + 40, new Color(0, 51, 153).getRGB());
    }

    @Unique
    private List<UUID> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }

        return client.player.connection.getOnlinePlayerIds().stream()
                .filter(uuid -> !uuid.equals(player.getUUID()))
                .filter(uuid -> {
                    PlayerInfo entry = client.player.connection.getPlayerInfo(uuid);
                    return entry != null;
                })
                .collect(Collectors.toList());
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
