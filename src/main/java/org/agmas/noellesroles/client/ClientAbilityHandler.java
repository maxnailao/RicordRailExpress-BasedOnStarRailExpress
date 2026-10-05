package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.custom_role.CustomRoleSelectScreen;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

public class ClientAbilityHandler {
    private static boolean unifiedSkillHeld;
    private static int heldSlot = -1;

    public static void handler(Minecraft client) {

        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(client.player.level());

        // 操纵师附身：技能键改为以被操控目标的身份释放目标自身技能（冷却记在目标身上）
        ManipulatorPlayerComponent manipulatorComp = ManipulatorPlayerComponent.KEY.get(client.player);
        if (manipulatorComp.isControlling && manipulatorComp.target != null) {
            ClientPlayNetworking.send(new ManipulatorAbilityC2SPacket());
            return;
        }
        // 游戏模式：自选职业
        if (gameWorldComponent.isRunning() && gameWorldComponent.getGameMode().equals(SREGameModes.CUSTOM_SELECTED_MODE)
                && gameWorldComponent.isRole(client.player, SpecialGameModeRoles.CUSTOM_PENDING)) {
            if (!CustomRoleGameModeTeamsPlayerComponent.KEY.get(client.player).selected()) {
                client.execute(() -> {
                    client.setScreen(new CustomRoleSelectScreen(client.player));
                });
            }
            return;
        }
        // 慕恋者持续按键检测（窥视）

        RicesRoleRhapsodyClient.handleAdmirerContinuousInput(client);
        if (client.player == null)
            return;

        var currentRole = gameWorldComponent.getRole(client.player);

        // 模仿者客户端前置逻辑：复制模式无目标→提示，消息技能→打开界面
        if (currentRole != null && gameWorldComponent.isRole(client.player, org.agmas.noellesroles.role.ModRoles.IMITATOR)) {
            var comp = org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent.KEY.get(client.player);
            if (comp.isCopyMode) {
                var hitResult = client.hitResult;
                if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                    if (entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                        // 有目标，继续走统一技能发包（带target）
                        var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
                        heldSlot = ability.getSelectedSkill();
                        unifiedSkillHeld = true;
                        ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                                heldSlot, RoleSkill.Phase.PRESS, entityHit.getEntity().getUUID()));
                        return;
                    }
                }
                // 复制模式无目标 → 提示
                client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.noellesroles.imitator.copy_mode_hint").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                return;
            }
            // 非复制模式：检查当前能力是否是消息技能
            var currentAbility = comp.getCurrentAbilityRoleId();
            if (currentAbility != null && org.agmas.noellesroles.game.roles.killer.imitator.ImitatorSkillRegistry.isMessageSkill(currentAbility)) {
                int cd = comp.getCurrentSkillCooldown();
                if (cd > 0) {
                    client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.noellesroles.imitator.cooldown", (cd + 19) / 20)
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }
                if (currentAbility.equals(org.agmas.noellesroles.role.BounsRoles.TELEGRAPHER_ID)) {
                    client.execute(() -> client.setScreen(new org.agmas.noellesroles.client.screen.TelegrapherScreen()));
                } else if (currentAbility.equals(org.agmas.noellesroles.role.ModRoles.BROADCASTER_ID)) {
                    client.execute(() -> client.setScreen(new org.agmas.noellesroles.client.screen.BroadcasterScreen()));
                }
                return;
            }
        }

        if (RoleSkill.hasUnifiedSkills(currentRole)) {
            var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
            heldSlot = ability.getSelectedSkill();
            unifiedSkillHeld = true;
            // 「蹲下 + 技能键」的 Shift 变体只对该职业真的定义了 shifted 技能时才生效。
            // 否则（例如幻术师三个技能都没变体）潜行时 applicable 会被过滤成空集合，
            // 直接 return false —— 表现就是「蹲着按技能键没反应」。
            // 技能本来就有独立的切换键（Y），不需要再占用 Shift。
            boolean sneaking = client.player.isShiftKeyDown() && hasShiftedVariant(currentRole);
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.PRESS, findTarget(client), sneaking));
            return;
        }

        if (GKeyRoleSkill.trigger(client, gameWorldComponent, true)) {
            return;
        }
        if (RicesRoleRhapsodyClient.onAbilityKeyPressed(client)) {
            return;
        }
        if (GKeyRoleSkill.trigger(client, gameWorldComponent, false)) {
            return;
        }
        ClientPlayNetworking.send(new AbilityC2SPacket());
    }

    public static void tickContinuousInput(Minecraft client) {
        if (client.player == null || client.level == null) {
            unifiedSkillHeld = false;
            heldSlot = -1;
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        var role = gameWorld.getRole(client.player);

        if (!RoleSkill.hasUnifiedSkills(role)) {
            unifiedSkillHeld = false;
            heldSlot = -1;
            return;
        }

        if (unifiedSkillHeld && NoellesrolesClient.abilityBind.isDown()) {
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.HOLD, findTarget(client)));
        } else if (unifiedSkillHeld) {
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.RELEASE, findTarget(client)));
            unifiedSkillHeld = false;
            heldSlot = -1;
        }
    }

    public static void selectNextSkill(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        var gameWorld = SREGameWorldComponent.KEY.get(client.level);
        var role = gameWorld.getRole(client.player);

        // 处理统一技能体系中的模式切换角色（会计、小偷、药剂师、建筑师、葬仪、设陷者、模仿者等）
        if (gameWorld.isRole(client.player, ModRoles.WIZARD)) {
            ClientPlayNetworking.send(new WizardSwitchSpellC2SPacket());
            return;
        }

        var definitions = RoleSkill.getDefinitions(role);
        var selectableDefs = RoleSkill.getSelectableDefinitions(role);
        if (!definitions.isEmpty() && selectableDefs.size() < 2) {
            var shiftedDefs = definitions.stream().filter(RoleSkill.Definition::shifted).toList();
            if (!shiftedDefs.isEmpty()) {
                // 存在模式切换技能且可选技能不足2个，V 键触发模式切换
                ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                        definitions.indexOf(shiftedDefs.getFirst()),
                        RoleSkill.Phase.PRESS,
                        findTarget(client),
                        true));
                return;
            }
        }
        if (definitions.size() < 2) {
            return;
        }
        if (selectableDefs.size() < 2) {
            return;
        }
        var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
        int next = (ability.getSelectedSkill() + 1) % selectableDefs.size();
        ClientPlayNetworking.send(new org.agmas.noellesroles.packet.UnifiedSkillSelectC2SPacket(next));
    }

    /**
     * 该职业是否真的定义了「Shift 变体」技能。
     * <p>只有定义了变体，按技能键时的潜行状态才有意义；没定义时若仍把潜行状态发上去，
     * 服务端会把技能集合过滤成 shifted 子集（空集）而直接失败 —— 也就是「蹲着放不出技能」。
     */
    private static boolean hasShiftedVariant(io.wifi.starrailexpress.api.SRERole role) {
        for (RoleSkill.Definition definition : RoleSkill.getDefinitions(role)) {
            if (definition.shifted()) {
                return true;
            }
        }
        return false;
    }

    private static UUID findTarget(Minecraft client) {
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player target) {
            return target.getUUID();
        }
        return null;
    }
}
