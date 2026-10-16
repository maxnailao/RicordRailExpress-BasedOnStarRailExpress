package org.agmas.noellesroles.mixin.client.roles.cowboy;

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
import org.agmas.noellesroles.client.widget.CowboyDuelWidget;
import org.agmas.noellesroles.game.roles.vigilante.cowboy.CowboyDuelManager;
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
 * 西部牛仔物品栏屏幕 Mixin（决斗）。
 *
 * <p>牛仔打开背包（按 E）时列出所有存活玩家头像，点击即花费
 * {@link CowboyDuelManager#COST} 金币请求决斗。
 * 交互方式与变形者 / 咒术师领域完全一致，不新增按键绑定。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class CowboyScreenMixin extends LimitedHandledScreen<InventoryMenu>
        implements PlayerPaginationHelper.ScreenWithChildren {
    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider COWBOY_TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
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
    private RoleScreenHelper<PlayerInfo> cowboyRoleScreenHelper;

    public CowboyScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getCowboyRoleScreenHelper() {
        if (cowboyRoleScreenHelper == null) {
            cowboyRoleScreenHelper = new RoleScreenHelper<PlayerInfo>(
                    player,
                    ModRoles.NIUZAI_JUEDOUBA,
                    this::createCowboyWidget,
                    COWBOY_TEXT_PROVIDER,
                    this::drawCowboySelectionHint,
                    this::getEligibleTargets);
        }
        return cowboyRoleScreenHelper;
    }

    @Unique
    private CowboyDuelWidget createCowboyWidget(int x, int y, PlayerInfo playerEntity, int index) {
        CowboyDuelWidget widget = new CowboyDuelWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerEntity);
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawCowboySelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.noellesroles.cowboy.duel_selection",
                CowboyDuelManager.COST);
        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, Color.ORANGE.getRGB());
    }

    /**
     * 决斗候选目标：25 格内的其他存活玩家。
     *
     * <p>{@code PlayerInfo} 只有档案信息、没有坐标，所以要先按 UUID 从客户端世界解析出实体，
     * 再用距离过滤掉范围外的人 —— 否则头像会照常显示，但点下去必然被服务端拒绝。
     *
     * <p>距离口径与 {@link CowboyDuelManager#RANGE} 一致（用平方距离比较，省一次开方）。
     */
    @Unique
    private List<PlayerInfo> getEligibleTargets() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null) {
            return List.of();
        }
        final double rangeSqr = CowboyDuelManager.RANGE * CowboyDuelManager.RANGE;
        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(info -> !info.getProfile().getId().equals(player.getUUID()))
                .filter(info -> info.getGameMode() == GameType.ADVENTURE)
                .filter(info -> {
                    net.minecraft.world.entity.player.Player target = client.level
                            .getPlayerByUUID(info.getProfile().getId());
                    return target != null && target.distanceToSqr(player) <= rangeSqr;
                })
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$cowboyOnRender(GuiGraphics context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        getCowboyRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$cowboyOnInit(CallbackInfo ci) {
        if (cowboyRoleScreenHelper != null) {
            cowboyRoleScreenHelper.getPaginationHelper().clearManagedWidgets(this);
        }
        getCowboyRoleScreenHelper().onInit(this);
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
