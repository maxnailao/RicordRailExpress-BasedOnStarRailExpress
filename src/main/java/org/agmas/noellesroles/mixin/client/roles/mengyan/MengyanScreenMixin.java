package org.agmas.noellesroles.mixin.client.roles.mengyan;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.MengyanPlayerWidget;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.game.roles.killer.mengyan.MengyanPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 梦魇物品栏屏幕Mixin
 * 在背包界面显示可施加恐惧的玩家列表
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class MengyanScreenMixin extends LimitedHandledScreen<InventoryMenu> implements PlayerPaginationHelper.ScreenWithChildren {
    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider MENGYAN_TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
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
    private RoleScreenHelper<PlayerInfo> mengyanRoleScreenHelper;

    public MengyanScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getMengyanRoleScreenHelper() {
        if (mengyanRoleScreenHelper == null) {
            mengyanRoleScreenHelper = new RoleScreenHelper<PlayerInfo>(
                    player,
                    ModRoles.MENGYAN,
                    this::createMengyanWidget,
                    MENGYAN_TEXT_PROVIDER,
                    this::drawMengyanSelectionHint,
                    this::getMengyanEligiblePlayers
            );
        }
        return mengyanRoleScreenHelper;
    }

    @Unique
    private MengyanPlayerWidget createMengyanWidget(int x, int y, PlayerInfo playerEntity, int index) {
        MengyanPlayerWidget widget = new MengyanPlayerWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerEntity
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawMengyanSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.noellesroles.mengyan.player_selection");
        int color = new Color(60, 0, 0).getRGB();

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    @Unique
    private List<PlayerInfo> getMengyanEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }

        // 如果已有活跃的恐惧技能，不显示可选玩家
        MengyanPlayerComponent comp = ModComponents.MENGYAN.get(player);
        if (comp.fearActive) {
            return List.of();
        }

        // 显示所有存活玩家（排除自己、杀手队友、已死亡玩家）
        return client.getConnection().getOnlinePlayers().stream()
                .filter(a -> a.getProfile().getId() != player.getUUID()
                        && a.getGameMode() == GameType.ADVENTURE
                        && !ModRoles.isVisibleKillerTeammate(
                                io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY
                                        .get(client.level).getRole(a.getProfile().getId())))
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onMengyanRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getMengyanRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onMengyanInit(CallbackInfo ci) {
        if (mengyanRoleScreenHelper != null) {
            mengyanRoleScreenHelper.getPaginationHelper().clearManagedWidgets(this);
        }
        getMengyanRoleScreenHelper().onInit(this);
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
