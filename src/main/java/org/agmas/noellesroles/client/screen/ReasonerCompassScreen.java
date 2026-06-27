package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.packet.ReasonerOpenScreenS2CPacket;
import org.agmas.noellesroles.packet.ReasonerSubmitC2SPacket;

import java.util.List;
import java.util.Locale;

public class ReasonerCompassScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 216;
    private static final int ROW_HEIGHT = 30;
    private static final int OPTION_PAGE_SIZE = 6;

    private final ReasonerOpenScreenS2CPacket payload;
    private EditBox aliveCountInput;
    private EditBox killerCountInput;
    private String selectedRole = "";
    private String selectedDeathReason = "";
    private String selectedTask = "";
    private int selectionQuestion = 0;
    private int selectionPage = 0;

    public ReasonerCompassScreen(ReasonerOpenScreenS2CPacket payload) {
        super(Component.translatable("screen.noellesroles.reasoner.title"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        rebuildMain();
    }

    private void rebuildMain() {
        clearWidgets();
        selectionQuestion = 0;
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int rowX = left + 24;
        int inputX = left + PANEL_WIDTH - 118;
        int buttonX = left + PANEL_WIDTH - 82;
        int y = top + 46;

        aliveCountInput = numberBox(inputX, y + 5, 34, "1");
        addRenderableWidget(aliveCountInput);
        addRenderableWidget(Button.builder(submitLabel(payload.solvedAliveCount()), button -> submitNumber(1, aliveCountInput.getValue()))
                .bounds(buttonX, y + 3, 62, 20).build());

        y += ROW_HEIGHT;
        addRenderableWidget(Button.builder(selectionLabel(selectedRole, "screen.noellesroles.reasoner.choose_role"),
                button -> openSelection(2)).bounds(buttonX - 86, y + 3, 148, 20).build());
        addRenderableWidget(Button.builder(submitLabel(payload.solvedRole()), button -> submitChoice(2, selectedRole))
                .bounds(buttonX + 68, y + 3, 62, 20).build());

        y += ROW_HEIGHT;
        if (payload.deathReasonQuestionAvailable()) {
            addRenderableWidget(Button.builder(selectionLabel(selectedDeathReason, "screen.noellesroles.reasoner.choose_reason"),
                    button -> openSelection(3)).bounds(buttonX - 86, y + 3, 148, 20).build());
            addRenderableWidget(Button.builder(submitLabel(payload.solvedDeathReason()), button -> submitChoice(3, selectedDeathReason))
                    .bounds(buttonX + 68, y + 3, 62, 20).build());
        }

        y += ROW_HEIGHT;
        addRenderableWidget(Button.builder(selectionLabel(selectedTask, "screen.noellesroles.reasoner.choose_task"),
                button -> openSelection(4)).bounds(buttonX - 86, y + 3, 148, 20).build());
        addRenderableWidget(Button.builder(submitLabel(payload.solvedTask()), button -> submitChoice(4, selectedTask))
                .bounds(buttonX + 68, y + 3, 62, 20).build());

        y += ROW_HEIGHT;
        if (payload.killerQuestionAvailable()) {
            killerCountInput = numberBox(inputX, y + 5, 34, "5");
            addRenderableWidget(killerCountInput);
            addRenderableWidget(Button.builder(submitLabel(payload.solvedKillerCount()), button -> submitNumber(5, killerCountInput.getValue()))
                    .bounds(buttonX, y + 3, 62, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 84, top + PANEL_HEIGHT - 28, 62, 20).build());
    }

    private EditBox numberBox(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setFilter(value -> value.matches("\\d*"));
        box.setMaxLength(3);
        return box;
    }

    private Component submitLabel(boolean solved) {
        return Component.translatable(solved ? "screen.noellesroles.reasoner.solved" : "screen.noellesroles.reasoner.submit");
    }

    private Component selectionLabel(String selected, String fallbackKey) {
        if (selected == null || selected.isBlank()) {
            return Component.translatable(fallbackKey);
        }
        return displayFor(valueQuestion(selected), selected);
    }

    private int valueQuestion(String value) {
        if (payload.deathReasonIds().contains(value)) {
            return 3;
        }
        if (payload.taskIds().contains(value)) {
            return 4;
        }
        return 2;
    }

    private void openSelection(int question) {
        selectionQuestion = question;
        selectionPage = 0;
        rebuildSelection();
    }

    private void rebuildSelection() {
        clearWidgets();
        List<String> options = optionsFor(selectionQuestion);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int start = selectionPage * OPTION_PAGE_SIZE;
        int end = Math.min(start + OPTION_PAGE_SIZE, options.size());

        for (int i = start; i < end; i++) {
            String option = options.get(i);
            int y = top + 48 + (i - start) * 24;
            addRenderableWidget(Button.builder(displayFor(selectionQuestion, option), button -> {
                setSelected(selectionQuestion, option);
                rebuildMain();
            }).bounds(left + 50, y, PANEL_WIDTH - 100, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (selectionPage > 0) {
                selectionPage--;
                rebuildSelection();
            }
        }).bounds(left + 54, top + PANEL_HEIGHT - 30, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((selectionPage + 1) * OPTION_PAGE_SIZE < options.size()) {
                selectionPage++;
                rebuildSelection();
            }
        }).bounds(left + 92, top + PANEL_HEIGHT - 30, 32, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> rebuildMain())
                .bounds(left + PANEL_WIDTH - 108, top + PANEL_HEIGHT - 30, 54, 20).build());
    }

    private List<String> optionsFor(int question) {
        return switch (question) {
            case 3 -> payload.deathReasonIds();
            case 4 -> payload.taskIds();
            default -> payload.roleIds();
        };
    }

    private void setSelected(int question, String value) {
        switch (question) {
            case 2 -> selectedRole = value;
            case 3 -> selectedDeathReason = value;
            case 4 -> selectedTask = value;
            default -> {
            }
        }
    }

    private Component displayFor(int question, String value) {
        if (question == 2) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            return id == null ? Component.literal(value) : Component.translatable("announcement.star.role." + id.getPath());
        }
        if (question == 3) {
            return Component.translatable("death_reason." + value.replace(':', '.'));
        }
        if (question == 4) {
            return Component.translatable("task." + value.toLowerCase(Locale.ROOT));
        }
        return Component.literal(value);
    }

    private void submitNumber(int question, String answer) {
        if (!answer.isBlank()) {
            ClientPlayNetworking.send(new ReasonerSubmitC2SPacket(question, answer));
            onClose();
        }
    }

    private void submitChoice(int question, String answer) {
        if (answer != null && !answer.isBlank()) {
            ClientPlayNetworking.send(new ReasonerSubmitC2SPacket(question, answer));
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectionQuestion != 0) {
            List<String> options = optionsFor(selectionQuestion);
            if (verticalAmount < 0 && (selectionPage + 1) * OPTION_PAGE_SIZE < options.size()) {
                selectionPage++;
                rebuildSelection();
                return true;
            }
            if (verticalAmount > 0 && selectionPage > 0) {
                selectionPage--;
                rebuildSelection();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        drawPanel(guiGraphics, left, top);
        if (selectionQuestion == 0) {
            drawQuestions(guiGraphics, left, top);
        } else {
            drawSelectionTitle(guiGraphics, left, top);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE140F1D);
        guiGraphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xAA211626);
        int cx = left + PANEL_WIDTH / 2;
        int cy = top + PANEL_HEIGHT / 2;
        guiGraphics.hLine(cx - 92, cx + 92, cy, 0x77E8C872);
        guiGraphics.vLine(cx, cy - 92, cy + 92, 0x77E8C872);
        for (int r = 70; r <= 88; r += 6) {
            guiGraphics.hLine(cx - r, cx + r, cy - r / 2, 0x44E8C872);
            guiGraphics.hLine(cx - r, cx + r, cy + r / 2, 0x44E8C872);
            guiGraphics.vLine(cx - r, cy - r / 2, cy + r / 2, 0x44E8C872);
            guiGraphics.vLine(cx + r, cy - r / 2, cy + r / 2, 0x44E8C872);
        }
        guiGraphics.drawCenteredString(font, title, cx, top + 16, 0xFFECCB79);
        guiGraphics.drawCenteredString(font, Component.translatable(
                "screen.noellesroles.reasoner.subtitle"), cx, top + 29, 0xFFBBA86D);
    }

    private void drawQuestions(GuiGraphics guiGraphics, int left, int top) {
        int y = top + 52;
        drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q1"));
        y += ROW_HEIGHT;
        drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q2", payload.roleTargetName()));
        y += ROW_HEIGHT;
        drawQuestion(guiGraphics, y, payload.deathReasonQuestionAvailable()
                ? Component.translatable("screen.noellesroles.reasoner.q3", payload.bodyTargetName())
                : Component.translatable("screen.noellesroles.reasoner.hidden"));
        y += ROW_HEIGHT;
        drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q4", payload.taskTargetName()));
        y += ROW_HEIGHT;
        drawQuestion(guiGraphics, y, payload.killerQuestionAvailable()
                ? Component.translatable("screen.noellesroles.reasoner.q5")
                : Component.translatable("screen.noellesroles.reasoner.hidden"));
    }

    private void drawQuestion(GuiGraphics guiGraphics, int y, Component text) {
        guiGraphics.drawString(font, text, (width - PANEL_WIDTH) / 2 + 28, y, 0xFFEBDFAE, false);
    }

    private void drawSelectionTitle(GuiGraphics guiGraphics, int left, int top) {
        Component label = switch (selectionQuestion) {
            case 3 -> Component.translatable("screen.noellesroles.reasoner.select_reason");
            case 4 -> Component.translatable("screen.noellesroles.reasoner.select_task");
            default -> Component.translatable("screen.noellesroles.reasoner.select_role");
        };
        guiGraphics.drawCenteredString(font, label, left + PANEL_WIDTH / 2, top + 36, 0xFFEBDFAE);
        guiGraphics.drawString(font, Component.literal((selectionPage + 1) + "/" + Math.max(1,
                (optionsFor(selectionQuestion).size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE)),
                left + 132, top + PANEL_HEIGHT - 24, 0xFFBBA86D, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
