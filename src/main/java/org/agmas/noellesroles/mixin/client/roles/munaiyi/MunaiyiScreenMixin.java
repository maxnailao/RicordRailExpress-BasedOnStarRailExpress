package org.agmas.noellesroles.mixin.client.roles.munaiyi;

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
import org.agmas.noellesroles.client.widget.MunaiyiPlayerWidget;
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
import java.util.stream.Collectors;

/**
 * 木乃伊物品栏屏幕Mixin（技能1「木乃伊的诅咒」）
 * 木乃伊打开背包时显示所有存活玩家头像，点击施加诅咒层
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class MunaiyiScreenMixin extends LimitedHandledScreen<InventoryMenu>
        implements PlayerPaginationHelper.ScreenWithChildren {
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

    @Shadow
    @Final
    public LocalPlayer player;

    @Unique
    private RoleScreenHelper<PlayerInfo> roleScreenHelper;

    public MunaiyiScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<PlayerInfo>(
                    player,
                    ModRoles.MUNAIYI_DESERT,
                    this::createMunaiyiWidget,
                    TEXT_PROVIDER,
                    this::drawMunaiyiSelectionHint,
                    this::getEligiblePlayers
            );
        }
        return roleScreenHelper;
    }

    @Unique
    private MunaiyiPlayerWidget createMunaiyiWidget(int x, int y, PlayerInfo playerEntity, int index) {
        MunaiyiPlayerWidget widget = new MunaiyiPlayerWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerEntity
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawMunaiyiSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.munaiyi.player_selection");
        int color = new Color(216, 184, 114).getRGB(); // 沙色

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    @Unique
    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }
        // 诅咒目标：除自己外所有存活玩家（冒险模式=存活），无距离限制
        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(a -> !a.getProfile().getId().equals(player.getUUID())
                        && a.getGameMode() == GameType.ADVENTURE)
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onInit(CallbackInfo ci) {
        if (roleScreenHelper != null) {
            roleScreenHelper.getPaginationHelper().clearManagedWidgets(this);
        }
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
