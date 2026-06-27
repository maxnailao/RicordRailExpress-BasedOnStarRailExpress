package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.MovingPlatformConfigC2SPacket;
import org.jetbrains.annotations.NotNull;
import net.minecraft.util.Mth;

public class MovingPlatformConfigScreen extends Screen {
    private final BlockPos pos;
    private int distance;
    private double speed;
    private double collisionSize;

    private EditBox distanceEdit;
    private EditBox speedEdit;
    private EditBox collisionSizeEdit;

    public MovingPlatformConfigScreen(BlockPos pos, int distance, double speed, double collisionSize) {
        super(Component.translatable("screen.noellesroles.moving_platform_config"));
        this.pos = pos;
        this.distance = Mth.clamp(distance, 1, 50);
        this.speed = Mth.clamp(speed, 0.1, 5.0);
        this.collisionSize = Mth.clamp(collisionSize, 0.5, 3.0);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;
        int startY = height / 2 - 60;

        distanceEdit = new EditBox(font, centerX - 78, startY, 156, 20, Component.empty());
        distanceEdit.setValue(String.valueOf(distance));
        addRenderableWidget(distanceEdit);

        speedEdit = new EditBox(font, centerX - 78, startY + 32, 156, 20, Component.empty());
        speedEdit.setValue(String.valueOf(speed));
        addRenderableWidget(speedEdit);

        collisionSizeEdit = new EditBox(font, centerX - 78, startY + 64, 156, 20, Component.empty());
        collisionSizeEdit.setValue(String.valueOf(collisionSize));
        addRenderableWidget(collisionSizeEdit);

        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.save"), btn -> {
            saveAndClose();
        }).bounds(centerX - 100, startY + 96, 200, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> {
            onClose();
        }).bounds(centerX - 100, startY + 121, 200, 20).build());
    }

    private void saveAndClose() {
        try {
            int newDistance = Integer.parseInt(distanceEdit.getValue());
            double newSpeed = Double.parseDouble(speedEdit.getValue());
            double newCollisionSize = Double.parseDouble(collisionSizeEdit.getValue());

            newDistance = Mth.clamp(newDistance, 1, 50);
            newSpeed = Mth.clamp(newSpeed, 0.1, 5.0);
            newCollisionSize = Mth.clamp(newCollisionSize, 0.5, 3.0);

            ClientPlayNetworking.send(
                    new MovingPlatformConfigC2SPacket(pos, newDistance, newSpeed, newCollisionSize));
            Minecraft.getInstance().setScreen(null);
        } catch (NumberFormatException e) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.noellesroles.invalid_config").withStyle(net.minecraft.ChatFormatting.RED),
                    false);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics, mouseX, mouseY, delta);
        super.render(guiGraphics, mouseX, mouseY, delta);

        int centerX = width / 2;
        int startY = height / 2 - 60;

        guiGraphics.drawCenteredString(font, title, centerX, startY - 20, 0xFFFFFF);

        guiGraphics.drawString(font, Component.translatable("screen.noellesroles.moving_platform.distance"),
                centerX - 78, startY - 11, 0xAAAAAA);
        guiGraphics.drawString(font, Component.translatable("screen.noellesroles.moving_platform.speed"),
                centerX - 78, startY + 21, 0xAAAAAA);
        guiGraphics.drawString(font, Component.translatable("screen.noellesroles.moving_platform.collision"),
                centerX - 78, startY + 53, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
