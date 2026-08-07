package org.agmas.noellesroles.game.modes.werewolf.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 狼人杀座位按钮：显示玩家头像 + 座位号
 * 头像取自在线玩家列表的皮肤贴图；找不到皮肤时退化为纯文字按钮
 * Author: jiale
 */
public class WerewolfSeatButton extends Button {
    /** 头像区域大小 */
    private static final int FACE_SIZE = 16;

    private final int seat;
    private final String playerName;
    /** 缓存的皮肤贴图（懒加载） */
    private ResourceLocation cachedSkin;
    private boolean skinResolved = false;

    /** 全局皮肤缓存：玩家名 -> 贴图（Optional.empty 表示已解析但无皮肤，避免每帧重复遍历） */
    private static final java.util.Map<String, java.util.Optional<ResourceLocation>> SKIN_CACHE = new java.util.HashMap<>();

    public WerewolfSeatButton(int x, int y, int width, int height, int seat, String playerName,
            OnPress onPress) {
        super(x, y, width, height, Component.translatable("werewolf.screen.seat", seat), onPress,
                DEFAULT_NARRATION);
        this.seat = seat;
        this.playerName = playerName != null ? playerName : "";
    }

    /**
     * 按玩家名从在线列表解析皮肤贴图（带全局缓存，供按钮与 HUD 共用）
     */
    public static ResourceLocation findSkin(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        var cached = SKIN_CACHE.get(playerName);
        if (cached != null) {
            return cached.orElse(null);
        }
        ResourceLocation found = null;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            for (PlayerInfo info : conn.getOnlinePlayers()) {
                if (info.getProfile() != null && playerName.equalsIgnoreCase(info.getProfile().getName())) {
                    var skin = info.getSkin();
                    found = skin != null ? skin.texture() : null;
                    break;
                }
            }
        }
        SKIN_CACHE.put(playerName, java.util.Optional.ofNullable(found));
        return found;
    }

    /**
     * 断线/重置时清空缓存
     */
    public static void clearSkinCache() {
        SKIN_CACHE.clear();
    }

    /**
     * 懒加载解析本按钮的皮肤
     */
    private ResourceLocation resolveSkin() {
        if (skinResolved) return cachedSkin;
        skinResolved = true;
        cachedSkin = findSkin(playerName);
        return cachedSkin;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 无头像时右移标签平衡视觉；有头像时保持默认居中（头像在左侧）
        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        ResourceLocation skin = resolveSkin();
        if (skin != null && this.visible) {
            // 头像绘制在按钮左侧（含帽层）
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(
                    graphics, skin, this.getX() + 2, this.getY() + (this.height - FACE_SIZE) / 2, FACE_SIZE);
        }
    }
}
