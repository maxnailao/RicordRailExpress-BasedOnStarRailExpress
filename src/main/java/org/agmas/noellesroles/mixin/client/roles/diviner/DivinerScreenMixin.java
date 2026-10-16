/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client.roles.diviner;

import io.wifi.starrailexpress.cca.ParticipationComponent;
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
import org.agmas.noellesroles.client.widget.DivinerPlayerWidget;
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
 * 占卜家背包界面扩展：列出本局参与者，点击头像选中占卜目标。
 *
 * <p>
 * 与巫毒师不同的是：中途加入（未参与本局）的玩家不会出现在列表里。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class DivinerScreenMixin extends LimitedHandledScreen<InventoryMenu>
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
    private RoleScreenHelper<UUID> roleScreenHelper;

    public DivinerScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<UUID> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                    player,
                    ModRoles.DIVINER,
                    this::createDivinerWidget,
                    TEXT_PROVIDER,
                    this::drawDivinerTip,
                    this::getEligiblePlayers);
        }
        return roleScreenHelper;
    }

    @Unique
    private DivinerPlayerWidget createDivinerWidget(int x, int y, UUID playerUUID, int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        PlayerInfo playerListEntry = client.player.connection.getPlayerInfo(playerUUID);
        if (playerListEntry == null) {
            return null;
        }

        DivinerPlayerWidget widget = new DivinerPlayerWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerUUID, playerListEntry, player.level(), index);
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawDivinerTip(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.diviner.tip");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, Color.WHITE.getRGB());
    }

    @Unique
    private List<UUID> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }

        ParticipationComponent participation = ParticipationComponent.KEY.get(client.player.level());
        return client.player.connection.getOnlinePlayerIds().stream()
                .filter(uuid -> !uuid.equals(player.getUUID()))
                .filter(uuid -> {
                    PlayerInfo entry = client.player.connection.getPlayerInfo(uuid);
                    return entry != null;
                })
                // 与巫毒师不同：中途加入（未参与本局）的玩家不显示
                .filter(participation::isParticipating)
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
