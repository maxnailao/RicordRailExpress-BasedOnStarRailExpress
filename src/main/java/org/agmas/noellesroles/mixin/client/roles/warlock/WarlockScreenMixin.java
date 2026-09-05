package org.agmas.noellesroles.mixin.client.roles.warlock;

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
import org.agmas.noellesroles.client.widget.WarlockDomainWidget;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 咒术师物品栏屏幕Mixin（技能三「领域展开·灰髓之境」）
 * 咒术师打开背包时列出所有「已被诅咒且存活」的玩家头像，点击即请求对其展开领域。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class WarlockScreenMixin extends LimitedHandledScreen<InventoryMenu>
        implements PlayerPaginationHelper.ScreenWithChildren {
    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider WARLOCK_TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
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
    private RoleScreenHelper<PlayerInfo> warlockRoleScreenHelper;

    public WarlockScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getWarlockRoleScreenHelper() {
        if (warlockRoleScreenHelper == null) {
            warlockRoleScreenHelper = new RoleScreenHelper<PlayerInfo>(
                    player,
                    ModRoles.WARLOCK,
                    this::createWarlockWidget,
                    WARLOCK_TEXT_PROVIDER,
                    this::drawWarlockSelectionHint,
                    this::getEligibleVictims);
        }
        return warlockRoleScreenHelper;
    }

    @Unique
    private WarlockDomainWidget createWarlockWidget(int x, int y, PlayerInfo playerEntity, int index) {
        WarlockDomainWidget widget = new WarlockDomainWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerEntity);
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawWarlockSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.warlock.domain_selection");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, Color.RED.getRGB());
    }

    /** 领域候选目标：处于（未过期）诅咒中且仍在冒险模式（=存活）的其他玩家。 */
    @Unique
    private List<PlayerInfo> getEligibleVictims() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null) {
            return List.of();
        }
        WarlockPlayerComponent comp = WarlockPlayerComponent.KEY.maybeGet(client.player).orElse(null);
        if (comp == null || comp.cursedPlayers.isEmpty()) {
            return List.of();
        }
        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(info -> !info.getProfile().getId().equals(player.getUUID())
                        && info.getGameMode() == GameType.ADVENTURE)
                .filter(info -> {
                    Integer remaining = comp.cursedPlayers.get(info.getProfile().getId());
                    return remaining != null && remaining > 0;
                })
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$warlockOnRender(GuiGraphics context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        getWarlockRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$warlockOnInit(CallbackInfo ci) {
        if (warlockRoleScreenHelper != null) {
            warlockRoleScreenHelper.getPaginationHelper().clearManagedWidgets(this);
        }
        getWarlockRoleScreenHelper().onInit(this);
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
