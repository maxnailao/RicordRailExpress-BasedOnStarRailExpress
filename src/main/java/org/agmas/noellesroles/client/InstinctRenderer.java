package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeModifiers;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.content.item.SignedPaperItem;
import org.agmas.noellesroles.game.roles.innocence.awesome_binglus.AwesomePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.detective.AgentPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.magician.MagicianPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.zhizhang.ZhizhangPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggData;
import org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaEventHandler;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.ghost_eye.GhostEyePlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

import java.awt.*;
import java.util.HashMap;

public class InstinctRenderer {
    public static void registerInstinctEvents() {
        // 坠木/皮革嘎的被动：无法被任何角色透视到（必须最先注册，首个非-1返回值生效）
        // 例外：坠木与皮革嘎的可以互相透视（透传给后续处理器）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            if (!(target instanceof Player targetPlayer))
                return -1;
            boolean targetIsZhuimu = SREClient.gameComponent.isRole(targetPlayer, ModRoles.ZHUIMU);
            boolean targetIsPige = SREClient.gameComponent.isRole(targetPlayer, ModRoles.PIGE);
            if (!targetIsZhuimu && !targetIsPige)
                return -1;
            Player self = Minecraft.getInstance().player;
            // 观察者自己也是坠木/皮革嘎的时透传，允许互相透视
            if (SREClient.gameComponent.isRole(self, ModRoles.ZHUIMU)
                    || SREClient.gameComponent.isRole(self, ModRoles.PIGE))
                return -1;
            // 其他任何观察者均无法透视到这两个角色
            return -2;
        });
        TouhouInstincts.registerEvents();
        // 病娇：本能仅可透视爱慕对象（粉色）与目标（红色），其余玩家一律不透视
        // 高亮不依赖本能开关，目标在病娇视角下始终发光
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
                return -1;
            var self = Minecraft.getInstance().player;
            if (!SREClient.gameComponent.isRole(self, ModRoles.YANDERE))
                return -1;
            // 病娇死亡/旁观后透传给默认逻辑（旁观者可查看角色颜色）
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (!(target instanceof Player targetPlayer) || targetPlayer == self)
                return -1;
            if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
                return -1;
            var yandere = ModComponents.YANDERE.maybeGet(self).orElse(null);
            if (yandere == null)
                return -1;
            if (yandere.isCrush(targetPlayer.getUUID()))
                return new Color(255, 105, 180).getRGB(); // 爱慕对象：粉色
            if (yandere.isTarget(targetPlayer.getUUID()))
                return new Color(255, 60, 60).getRGB(); // 目标：红色发光
            return -2; // 其余玩家禁止透视
        });
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!hasInstinct || Minecraft.getInstance().player == null || SREClient.gameComponent == null) {
                return -1;
            }
            Player self = Minecraft.getInstance().player;
            if (!isKillerTeam(SREClient.gameComponent.getRole(self))) {
                return -1;
            }
            if (target instanceof SaltedFishBodyEntity) {
                return -2;
            }
            if (target instanceof Player targetPlayer) {
                SaltedFishPlayerComponent component = SaltedFishPlayerComponent.KEY.maybeGet(targetPlayer).orElse(null);
                if (component != null && component.isActive()) {
                    return -2;
                }
            }
            return -1;
        });
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player) || !hasInstinct || Minecraft.getInstance().player == null
                    || SREClient.gameComponent == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!SREClient.gameComponent.isRole(self, ModRoles.RAVEN))
                return -1;
            var raven = ModComponents.RAVEN.get(self);
            if (raven.isHunting())
                return Color.WHITE.getRGB();
            if (self.distanceTo(target) <= 10.0)
                return Color.WHITE.getRGB();
            return -2;
        });
        // 鬼眼·杨间 被动：扫描期间，周身范围内的所有玩家显示白色直觉轮廓
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer) || Minecraft.getInstance().player == null
                    || SREClient.gameComponent == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!isGhostEyeRole(self))
                return -1;
            if (!isGhostEyeScanActive(self))
                return -1;
            if (targetPlayer == self || targetPlayer.isSpectator())
                return -1;
            // 超出扫描范围的玩家不由此处理器控制，透传给后续处理器
            if (targetPlayer.distanceToSqr(self) > GhostEyePlayerComponent.SCAN_RADIUS
                    * GhostEyePlayerComponent.SCAN_RADIUS)
                return -1;
            return Color.WHITE.getRGB();
        });
        // 鬼祟效果：当目标玩家8格范围内时，禁用杀手直觉高亮
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer)) {
                return -1;
            }
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
                return -1;
            }
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
                return -1;
            }
            if (!SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
                return -1;
            }

            Player localPlayer = Minecraft.getInstance().player;

            // 检查目标玩家是否有鬼祟修饰符
            try {
                WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(targetPlayer.level());
                if (modifiers != null && modifiers.isModifier(targetPlayer.getUUID(), TraitorAndModifiers.SNEAKY)) {
                    // 检查目标是否在当前玩家8格范围内
                    double dist = localPlayer.distanceTo(targetPlayer);
                    if (dist <= 8.0) {
                        // 鬼祟生效：禁用直觉高亮
                        return -2;
                    }
                }
            } catch (Exception e) {
                // 静默处理错误
            }

            return -1;
        });
        // 特工：技能激活时反透视
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer)) return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) return -1;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return -1;
            try {
                var tegongComp = ModComponents.TEGONG.get(targetPlayer);
                if (tegongComp != null && tegongComp.skillActive) {
                    return -2;
                }
            } catch (Exception e) {
                // 静默处理
            }
            return -1;
        });
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player target_player))
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            if (!SREClient.gameComponent.isRunning()) {
                return -1;
            }
            if (!SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
                return -1;
            }
            var self = Minecraft.getInstance().player;
            if (SREClient.gameComponent.gameMode.identifier.equals(SREGameModes.HIDE_AND_SEEK_MODE.identifier)) {
                if (SREClient.gameComponent.isKillerTeam(self)) {
                    if (SREClient.gameComponent.isKillerTeam(target_player)) {
                        return TMMRoles.KILLER.color();
                    }
                } else {
                    if (self.hasEffect(ModEffects.SAFE_TIME) && SREClient.gameComponent.isKillerTeam(target_player)) {
                        return TMMRoles.KILLER.color();
                    }
                }
                if (self.hasEffect(MobEffects.GLOWING)) {
                    return TMMRoles.VIGILANTE.color();
                }
                return -2;
            }
            if (SREClient.gameComponent.gameMode.identifier.equals(SREGameModes.TNT_TAG_MODE.identifier)) {
                if (SREClient.modifierComponent.isModifier(target_player, SpecialGameModeModifiers.TNT_TAGGED))
                    return SpecialGameModeModifiers.TNT_TAGGED.color();
                return TMMRoles.CIVILIAN.color();
            }
            return -1;
        });
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer))
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRole(self, ModRoles.THE_FOOL))
                return -1;

            FoolPlayerComponent component = FoolPlayerComponent.KEY.get(self);
            if (component.hereticTarget == null)
                return -1;
            if (!component.hereticTarget.equals(targetPlayer.getUUID()))
                return -1;
            return 0xF2C56A;
        });

        // 记者便签
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            if (!(target instanceof io.wifi.starrailexpress.content.entity.NoteEntity note))
                return -1;
            if (SREClient.gameComponent.isRole(self, ModRoles.AWESOME_BINGLUS)) {
                return getGradientColor(note.getId());
            }

            return -1;
        });

        // 殡仪员：透视物品掉落物
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            if (!(target instanceof net.minecraft.world.entity.item.ItemEntity itemEntity))
                return -1;

            // 检查是否是殡仪员
            if (!SREClient.gameComponent.isRole(self, ModRoles.MORTICIAN))
                return -1;

            // 检查是否在范围内（10格水平，3格垂直）
            double dx = self.getX() - itemEntity.getX();
            double dy = self.getY() - itemEntity.getY();
            double dz = self.getZ() - itemEntity.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist > 10.0 || Math.abs(dy) > 3.0)
                return -1;

            // 返回渐变颜色高亮
            return getGradientColor(itemEntity.getId());
        });

        // 恋人
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (self == null)
                return -1;
            if (!(target instanceof Player))
                return -1;
            if (!WorldModifierComponent.KEY.get(self.level()).isModifier(self, SEModifiers.LOVERS))
                return -1;
            var lc = LoversComponent.KEY.get(self);
            var loverUuid = lc.getLover();
            if (loverUuid != null && loverUuid.equals(target.getUUID())) {
                return SEModifiers.LOVERS.color();
            }
            return -1;
        });
        // CUPID
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (self == null)
                return -1;
            if (SREClient.gameComponent != null && SREClient.gameComponent.isRole(self, ModRoles.CUPID)) {
                if (!hasInstinct)
                    return -1;
                if (!(target instanceof Player targetPlayer))
                    return -1;
                if (targetPlayer.isSpectator())
                    return -2;
                if (WorldModifierComponent.KEY.get(targetPlayer.level()).isModifier(targetPlayer, SEModifiers.LOVERS)
                        || LoversComponent.KEY.get(targetPlayer).isLover()) {
                    return Color.ORANGE.getRGB();
                }
                return ModRoles.CUPID.color();
            }
            return -1;
        });
        // 明星
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (self == null)
                return -1;
            if (!(target instanceof Player targetPlayer))
                return -1;
            var itemStack = MCItemsUtils.getFirstMatchedItem(self, (it) -> it.getItem() instanceof SignedPaperItem);
            if (itemStack != null) {
                String owner = itemStack.getOrDefault(SREDataComponentTypes.OWNER, "NULL");
                if (targetPlayer.getScoreboardName().equals(owner)) {
                    return new Color(254, 254, 254).getRGB();
                }
            }
            return -1;
        });
        // 死亡惩罚已被移到最高级的地方（调用处）

        // 秉烛人：可透视被秉烛的活人与对应尸体
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.CANDLE_BEARER))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!hasInstinct)
                return -1;
            CandleBearerPlayerComponent component = CandleBearerPlayerComponent.KEY.get(self);
            // 尸体：已被完成秉烛的显示蓝色
            if (target instanceof PlayerBodyEntity body) {
                if (body.getPlayerUuid() != null && component.isCorpseCandleCompleted(body.getPlayerUuid())) {
                    return Color.BLUE.getRGB();
                }
                if (body.getPlayerUuid() != null && component.isCandleLit(body.getPlayerUuid())) {
                    return ModRoles.CANDLE_BEARER.color();
                }
                return Color.GRAY.getRGB();
            }
            // 活人：无法透视的职业不显示，被秉烛过的显示原色，其余灰色
            if (target instanceof Player targetPlayer) {
                if (targetPlayer.distanceToSqr(self) > 40 * 40)
                    return -2;
                // 无法被透视的职业（小透明/秉烛人/雇佣兵/捣蛋鬼）
                if (isTargetInvisibleToInstinct(targetPlayer)) {
                    return -2;
                }
                if (component.isCandleLit(targetPlayer.getUUID())) {
                    return ModRoles.CANDLE_BEARER.color();
                }
                return Color.GRAY.getRGB();
            }
            return -1;
        });

        // 疫使：透视所有玩家，被感染者显示橙色边框
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.INFECTED))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!hasInstinct)
                return -1;

            if (target instanceof Player targetPlayer) {
                // 无法被透视的职业（小透明/秉烛人/雇佣兵/捣蛋鬼）
                if (isTargetInvisibleToInstinct(targetPlayer)) {
                    return -2;
                }
                // 检查目标玩家是否被感染（非疫使角色的玩家被感染）
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(targetPlayer);
                if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                    // 被感染者显示橙色边框
                    return Color.ORANGE.getRGB();
                }
                // 其他玩家显示疫使的颜色
                return ModRoles.INFECTED.color();
            }
            return -1;
        });

        // 葬仪：看所有人和尸体都是自己的颜色，且可以透视场上所有尸体
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.MORTICIAN_BODYMAKER))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            // 葬仪总是可以看到尸体（不需要开启杀手直觉）
            if (target instanceof PlayerBodyEntity) {
                return ModRoles.MORTICIAN_BODYMAKER.color();
            }

            // 需要开启杀手直觉才能看到玩家
            if (!hasInstinct)
                return -1;

            // 所有玩家都显示葬仪的颜色（无法透视的职业除外）
            if (target instanceof Player targetPlayer) {
                // 无法被透视的职业（小透明/秉烛人/雇佣兵/捣蛋鬼）
                if (isTargetInvisibleToInstinct(targetPlayer)) {
                    return -2;
                }
                return ModRoles.MORTICIAN_BODYMAKER.color();
            }
            return -1;
        });

        // 雇佣兵：仅在雇佣兵客户端将其合约目标高亮显示
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.MERCENARY))
                return -1;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;

            var mercComp = ModComponents.MERCENARY.get(self);
            if (mercComp == null || !mercComp.contractActive)
                return -1;

            // 检查目标是否为合约目标（支持活人或尸体实体）
            if (target instanceof Player targetPlayer) {
                if (targetPlayer.getUUID().equals(mercComp.contractTargetUuid)) {
                    return ModRoles.MERCENARY.color();
                }
                return -1;
            }
            if (target instanceof PlayerBodyEntity body) {
                if (body.getPlayerUuid() != null && body.getPlayerUuid().equals(mercComp.contractTargetUuid)) {
                    return ModRoles.MERCENARY.color();
                }
                return -1;
            }
            return -1;
        });
        // 验尸官
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(self, ModRoles.CORONER)) {
                return -1;
            }

            long time = self.level().getGameTime();
            if (time % 400 >= 100) {
                return -1;

            }

            if (target instanceof PlayerBodyEntity) {
                return (ModRoles.CORONER.color());
            }

            if (target instanceof Player targetPlayer) {
                InsaneKillerPlayerComponent component = InsaneKillerPlayerComponent.KEY.get(targetPlayer);
                if (component.isActive) {
                    return (ModRoles.CORONER.color());
                }
            }
            return -1;
        });
        // 初学者
        // 幻音师：本能看所有玩家显示与自身一致的颜色（无法透视不可透职业）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!hasInstinct)
                return -1;
            if (!(target instanceof Player targetPlayer))
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
                return -1;
            if (!SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit())
                return -1;
            var self = Minecraft.getInstance().player;
            if (!SREClient.gameComponent.isRole(self, ModRoles.PHANTOM_MUSICIAN))
                return -1;
            if (isTargetInvisibleToInstinct(targetPlayer))
                return -1;
            return ModRoles.PHANTOM_MUSICIAN.color();
        });

        // 黑警时刻：透视所有存活玩家（黑色边框）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.CORRUPT_COP))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            var corruptComp = ModComponents.CORRUPT_COP.maybeGet(self).orElse(null);
            if (corruptComp == null)
                return -1;

            if (!corruptComp.isBlackoutActive())
                return -1;

            if (target instanceof Player targetPlayer) {
                if (targetPlayer == self)
                    return -1;
                if (targetPlayer.isSpectator())
                    return -1;
                if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
                    return -1;

                if (isTargetInvisibleToInstinct(targetPlayer))
                    return -2;

                return java.awt.Color.BLACK.getRGB();
            }

            return -1;
        });

        // 女巫：始终透视所有玩家（浅红色边框）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.WITCH))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            if (target instanceof Player targetPlayer) {
                if (targetPlayer == self)
                    return -1;
                if (targetPlayer.isSpectator())
                    return -1;
                if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
                    return -1;
                if (isTargetInvisibleToInstinct(targetPlayer))
                    return -2;
                // 浅红色边框
                return new java.awt.Color(255, 128, 128).getRGB();
            }
            return -1;
        });


        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            Player player = Minecraft.getInstance().player;
            if (!SREClient.gameComponent.isRole(Minecraft.getInstance().player, SERoles.INITIATE)) {
                return -1;
            }
            if (SREItemUtils.countItem(player, TMMItems.KNIFE) <= 0) {
                return -1;
            }
            if (target instanceof Player targettedPlayer) {
                if (SREClient.gameComponent.isRole(targettedPlayer, SERoles.INITIATE)) {
                    return (SERoles.INITIATE.color());
                }
            }
            return -1;
        });
        // 纵火犯
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            var player = Minecraft.getInstance().player;
            if (!(target instanceof Player targettedPlayer)) {
                return -1;
            }
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(player, SERoles.ARSONIST)) {
                return -1;
            }
            if (SREClient.isPlayerSpectatingOrCreative()) {
                return -1;
            }
            if (!SREClient.isInstinctEnabled()) {
                return -1;
            }

            if (targettedPlayer.distanceToSqr(player) > 40 * 40)
                return -2;
            var douse = DousedPlayerComponent.KEY.get(targettedPlayer);
            if (douse.getDoused()) {
                return (SERoles.ARSONIST.color());
            } else {
                return (Color.GRAY.getRGB());
            }
        });
        // 记者
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!(target instanceof Player targetPlayer)) {
                return -1;
            }
            if (targetPlayer.isInvisibleTo(self))
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.AWESOME_BINGLUS)) {
                return -1;
            }
            if (SREClient.isPlayerSpectatingOrCreative()) {
                return -1;
            }
            if (targetPlayer.distanceTo(self) <= 5) {
                var awpc = AwesomePlayerComponent.KEY.get(targetPlayer);
                if (awpc.nearByDeathTime <= 1)
                    return -1;
                int redDepth = (int) (255
                        * ((float) awpc.nearByDeathTime
                                / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime));
                redDepth = Math.clamp(redDepth, 0, 255);
                return new Color(redDepth, 0, 0).getRGB();
            }
            return -1;
        });
        // 侦探
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!(target instanceof Player targetPlayer)) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(targetPlayer, ModRoles.CONSPIRATOR)) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(self, ModRoles.AGENT)) {
                return -1;
            }
            var awpc = AgentPlayerComponent.KEY.get(self);
            if (awpc.conspiratorInstinctTime <= 0)
                return -1;
            return ModRoles.AGENT.color();
        });
        // 失忆
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!(target instanceof PlayerBodyEntity)) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(self, SERoles.AMNESIAC)) {
                return -1;
            }
            if (SREClient.isPlayerSpectatingOrCreative()) {
                return -1;
            }
            return SERoles.AMNESIAC.color();
        });

        // 通用逻辑
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null)
                return -1;
            var self = client.player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(target.level());
            var self_role = SREClient.gameComponent.getRole(self);
            if (worldModifierComponent != null) {
                if (worldModifierComponent.isModifier(self, SEModifiers.SPLIT_PERSONALITY)) {
                    if (self.isSpectator()) {
                        var splitComponent = SplitPersonalityComponent.KEY.get(self);
                        if (splitComponent != null && !splitComponent.isDeath()) {
                            return -2;
                        }
                    }
                }
            }
            // 布谷鸟：无法透视玩家；非布谷鸟：无法透视蛋
            boolean selfAlive = GameUtils.isPlayerAliveAndSurvival(self);
            if (selfAlive && SREClient.gameComponent.isRole(self, ModRoles.REASONER) && target instanceof Player) {
                return -2;
            }
            if (selfAlive && SREClient.gameComponent.isRole(self, ModRoles.CUCKOO)) {
                if (target instanceof Player) {
                    return -2;
                }
            } else {
                if (target instanceof Display.BlockDisplay) {
                    return -2;
                }
            }
            if (target instanceof Player target_player) {
                // 不开直觉，默认有
                // 红尘客
                if (SREClient.gameComponent.isRole(self, ModRoles.WAYFARER)) {
                    if (GameUtils.isPlayerAliveAndSurvival(target_player)) {
                        var wayC = WayfarerPlayerComponent.KEY.get(self);
                        if (wayC.phase == 1) {
                            if (wayC.killer != null) {
                                if (target_player.getUUID().equals(wayC.killer)) {
                                    return Color.RED.getRGB();
                                }
                            }
                        }
                    }
                }
                // JOJO
                if (SREClient.gameComponent.isRole(self, ModRoles.JOJO)) {
                    if (GameUtils.isPlayerAliveAndSurvival(target_player)) {
                        if (target_player.distanceTo(self) <= 3) {
                            if (SREClient.gameComponent.isRole(target_player, ModRoles.DIO)) {
                                if (self.hasEffect(ModEffects.SKILL_BANED)) {
                                    return -1;
                                }
                                return ModRoles.DIO.color();
                            }
                        }
                    }
                }

                // 智力障碍患者：技能探查到的有刀玩家红色高亮
                if (SREClient.gameComponent.isRole(self, ModRoles.ZHIZHANG)) {
                    ZhizhangPlayerComponent zComp = ZhizhangPlayerComponent.KEY.get(self);
                    if (zComp.highlightTicks > 0 && zComp.highlightedPlayers.contains(target_player.getUUID())) {
                        if (GameUtils.isPlayerAliveAndSurvival(target_player)) {
                            return Color.RED.getRGB();
                        }
                    }
                }
                var target_role = SREClient.gameComponent.getRole(target_player);
                SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(target_player);
                SREPlayerPoisonComponent playerPoisonComponent = SREPlayerPoisonComponent.KEY.get(target_player);
                if (SREClient.gameComponent.isRole(self, ModRoles.BETTER_VIGILANTE)) {
                    var betterC = BetterVigilantePlayerComponent.KEY.get(self);
                    if (betterC.lastStandActivated) {
                        return (Color.BLUE.getRGB());
                    }
                }
                if (SREClient.gameComponent.isRole(self, RedHouseRoles.PACHURI)) {
                    if (!self.hasEffect(ModEffects.SAFE_TIME)) {
                        if (target.distanceToSqr(self) <= 25) {
                            if (SREClient.gameComponent.isRole(target_player, RedHouseRoles.FURANDORU)) {
                                return RedHouseRoles.FURANDORU.color();
                            }
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.CHEF)) {
                    // LoggerFactory.getLogger("renderer").info("glowTick {}",
                    // bartenderPlayerComponent.glowTicks);
                    if (self.hasEffect(ModEffects.SAFE_TIME))
                        return -1;
                    int t = FoodDrinkGlowComponent.KEY.get(self).glowTicks
                            .getOrDefault(target.getScoreboardName(), new HashMap<>())
                            .getOrDefault(1, 0);
                    if (t > 0) {
                        return (Color.GREEN.getRGB());
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.BARTENDER)) {
                    // LoggerFactory.getLogger("renderer").info("glowTick {}",
                    // bartenderPlayerComponent.glowTicks);
                    if (self.hasEffect(ModEffects.SAFE_TIME))
                        return -1;
                    if (armorPlayerComponent.getArmor() > 0 && playerPoisonComponent.poisonTicks > 0) {
                        return (new Color(186, 255, 65).getRGB());

                    }
                    if (armorPlayerComponent.getArmor() > 0) {
                        if (target_role.identifier().equals(ModRoles.WATCHER_ID)) {
                            return -1;
                        }
                        return (Color.BLUE.getRGB());
                    }
                    int t = FoodDrinkGlowComponent.KEY.get(self).glowTicks
                            .getOrDefault(target.getScoreboardName(), new HashMap<>())
                            .getOrDefault(0, 0);
                    if (t > 0) {
                        return (Color.GREEN.getRGB());
                    }
                }
                if ((SREClient.gameComponent.isRole(self, ModRoles.BARTENDER)
                        || SREClient.gameComponent.isRole(self, ModRoles.POISONER))
                        && playerPoisonComponent.poisonTicks > 0) {
                    return (Color.RED.getRGB());
                }

                if (SREClient.gameComponent.isRole(self, ModRoles.EXECUTIONER)) {
                    ExecutionerPlayerComponent executionerPlayerComponent = (ExecutionerPlayerComponent) ExecutionerPlayerComponent.KEY
                            .get(self);
                    if (executionerPlayerComponent != null && executionerPlayerComponent.target != null) {
                        if (executionerPlayerComponent.target.equals(target.getUUID())
                                && !SREClient.gameComponent.isRole(target.getUUID(), ModRoles.GHOST)) {
                            return new java.awt.Color(0, 254, 254).getRGB();
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.MANIPULATOR)) {
                    ManipulatorPlayerComponent manipulatorPlayerComponent = (ManipulatorPlayerComponent) ManipulatorPlayerComponent.KEY
                            .get(self);
                    if (manipulatorPlayerComponent != null && manipulatorPlayerComponent.target != null) {
                        if (manipulatorPlayerComponent.target.equals(target.getUUID())) {
                            return (Color.orange.getRGB());
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.ADMIRER)) {
                    AdmirerPlayerComponent admirerPlayerComponent = (AdmirerPlayerComponent) AdmirerPlayerComponent.KEY
                            .get(self);
                    if (admirerPlayerComponent != null && admirerPlayerComponent.getBoundTarget() != null) {
                        if (admirerPlayerComponent.getBoundTarget().getUUID().equals(target.getUUID())) {
                            // LoggerFactory.getLogger("Instinct").info("PINK");
                            return (Color.PINK.getRGB());
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.MONITOR)) {
                    MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY
                            .get(self);
                    if (monitorComponent != null && monitorComponent.getMarkedTarget() != null) {
                        if (monitorComponent.getMarkedTarget().equals(target.getUUID())) {
                            return (Color.CYAN.getRGB());
                        }
                    }
                }
                // 鹈鹕：透视所有玩家，被吞噬过的显示橙色，其他显示鹈鹕颜色
                if (SREClient.gameComponent.isRole(self, ModRoles.PELICAN)) {
                    if (!hasInstinct)
                        return -1;
                    if (GameUtils.isPlayerSpectatingOrCreative(self))
                        return -1;
                    double distSq = target_player.distanceToSqr(self);
                    int range = PelicanPlayerComponent.INSTINCT_RANGE;
                    if (distSq > range * range) {
                        return -2;
                    }
                    PelicanPlayerComponent pelicanComp = PelicanPlayerComponent.KEY.get(self);
                    if (pelicanComp != null && pelicanComp.uniqueEaten.contains(target_player.getUUID())) {
                        return Color.ORANGE.getRGB();
                    }
                    return ModRoles.PELICAN.color();
                }
                // 需要开启直觉
                if (!hasInstinct)
                    return -1;
                if (GameUtils.isPlayerSpectatingOrCreative(self))
                    return -1; // 旁观默认高亮
                // 直觉看不到旁观
                if ((target_player).isSpectator())
                    return -2;
                if (isTargetInvisibleToInstinct(target_player))
                    return -2;

                // 小透明：杀手无法看到高亮（杀手，与大部分中立偏狼）
                if (SREClient.gameComponent.isRole(target_player, ModRoles.GHOST) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }

                // 风精灵
                if (SREClient.gameComponent.isRole(self, ModRoles.WIND_YAOSE)) {
                    return ModRoles.WIND_YAOSE.getColor();
                }
                if (SREClient.gameComponent.isRole(target_player, ModRoles.SALTED_FISH)) {
                    if (target_player.isInvisible()) {
                        return -2;
                    }
                }

                if (SREClient.gameComponent.isRole(self, RedHouseRoles.FURANDORU)) {
                    if (target_role != null) {
                        if (RoleUtils.compareRole(target_role, RedHouseRoles.PACHURI)) {
                            return RedHouseRoles.PACHURI.color();
                        }
                        return new Color(2, 224, 2).getRGB();
                    }
                    return -1;
                }
                // 傀儡师
                PuppeteerPlayerComponent selfPuppeteerComp = ModComponents.PUPPETEER.get(self);
                if (selfPuppeteerComp.isControllingPuppet && SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
                    return ModRoles.PUPPETEER.color();
                }
                if (selfPuppeteerComp.isPuppeteerMarked && SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()
                        && selfPuppeteerComp.phase >= 1) {
                    return -1;
                }
                // 黑白熊形态：对所有人隐藏高亮
                if (SREClient.gameComponent.isRole(target_player, ModRoles.MONOKUMA)
                        && MonokumaEventHandler.isMonokumaBearForm(target_player)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.MONOKUMA) && SREClient.isPlayerAliveAndInSurvival()) {
                    return ModRoles.MONOKUMA.color();
                }

                // 黑白狂暴前奏：杀手看到灰色
                // if (SREClient.gameComponent.isRole(target_player, ModRoles.MONOKUMA)
                // && MonokumaEventHandler.isInFrenzy(target_player)
                // && SREClient.isPlayerAliveAndInSurvival()) {
                // return Color.RED.getRGB();
                // }
                // 秉烛人：杀手无法透视察觉
                if (SREClient.gameComponent.isRole(target_player, ModRoles.CANDLE_BEARER) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                // 雇佣兵：杀手直觉无法透视
                if (SREClient.gameComponent.isRole(target_player, ModRoles.MERCENARY) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                // 怀旧者里世界：杀手无法透视察觉
                if (SREClient.gameComponent.isRole(target_player, ModRoles.NOSTALGIST)
                        && target_player.hasEffect(ModEffects.NOSTALGIST_BACKWORLD) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.WRAITH_ASSASSIN)
                        && target instanceof Player targetPlayer
                        && targetPlayer != self
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    int san = Math.round(SREPlayerMoodComponent.KEY.get(targetPlayer).getMood() * 100.0f);
                    if (san < 10) {
                        return 0x2C8DFF;
                    }
                    if (san < 30) {
                        return 0xFFD84A;
                    }
                }
                // 低 SAN 玩家可见冤魂高亮
                if (target instanceof Player targetPlayer && targetPlayer != self
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    SRERole selfRole = SREClient.gameComponent.getRole(self);
                    if (selfRole != null && !selfRole.isKiller()) {
                        int viewerSan = Math.round(
                                SREPlayerMoodComponent.KEY.get(self).getMood() * 100.0f);
                        if (viewerSan < 40
) {
                            return 0xAA66FF;
                        }
                    }
                }
                // 记录员
                if (SREClient.gameComponent.isRole(self, ModRoles.RECORDER)) {
                    if (target instanceof Player targetPlayer) {
                        if (self.isSpectator())
                            return -1;
                        if (targetPlayer.distanceToSqr(self) > 20 * 20)
                            return -2;
                        if (targetPlayer == self)
                            return -2;

                        RecorderPlayerComponent recorder = ModComponents.RECORDER.get(self);
                        if (recorder.getGuesses().containsKey(targetPlayer.getUUID())) {
                            // 已记录（猜测过）：亮黄色
                            return (0xFFFF55);
                        } else {
                            // 未记录：暗蓝色
                            return (0x0000AA);
                        }
                    }
                }
                // 爱慕
                if (SREClient.gameComponent.isRole(self, ModRoles.ADMIRER) && SREClient.isPlayerAliveAndInSurvival()) {
                    return (Color.PINK.getRGB());
                }
                // 小丑&LOOSE END
                if ((SREClient.gameComponent.isRole(self, ModRoles.JESTER)
                        || ModRoles.isLooseEndVariant(SREClient.gameComponent.getRole(self))
                        || SREClient.gameComponent.isRole(self, SpecialGameModeRoles.SUPER_LOOSE_END)
                        || SREClient.gameComponent.isRole(self, SpecialGameModeRoles.DIRT))
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.GHOST)) {
                        return -2;
                    }
                    // 超级亡命徒无法看到隐身的人
                    if (SREClient.gameComponent.isRole(self, SpecialGameModeRoles.SUPER_LOOSE_END) &&
                            target_player.isInvisible()) {
                        return -2;
                    }
                    return (Color.PINK.getRGB());
                }
                // // 柜子区
                // if (SREClient.gameComponent.isRole(self, ModRoles.EXECUTIONER)
                // && SREClient.isPlayerAliveAndInSurvival()) {
                // return (ModRoles.EXECUTIONER.color());
                // }

                // 家族本能透视
                if (self_role != null && self_role.isMafiaTeam() && SREClient.isPlayerAliveAndInSurvival()) {
                    if (target_role != null && target_role.isMafiaTeam()) {
                        // 家族成员透视家族成员 - 无距离限制
                        if (SREClient.gameComponent.isRole(target_player, ModRoles.GODFATHER)) {
                            return new Color(135, 206, 235).getRGB(); // 天蓝色
                        }
                        // 其他家族成员显示棕色
                        return new Color(139, 69, 19).getRGB(); // 棕色
                    }
                    // 家族成员透视非家族成员 - 20格距离限制
                    if (self.distanceTo(target_player) > 20.0D) {
                        return -2;
                    }
                }

                // 杀手直觉
                if (isKillerTeam(self_role) && SREClient.isPlayerAliveAndInSurvival()) {
                    // 布袋鬼：里世界期间无杀手直觉
                    if (SREClient.gameComponent.isRole(self, ModRoles.MA_CHEN_XU)) {
                        MaChenXuPlayerComponent macComp = MaChenXuPlayerComponent.KEY.get(self);
                        if (macComp != null && macComp.otherworldActive) {
                            return -2;
                        }
                    }
                    // 强盗直觉：只能透视半径10格内的玩家，透视杀手队友无距离限制
                    if (SREClient.gameComponent.isRole(self, ModRoles.BANDIT)) {
                        // 检查目标是否是杀手队友
                        if (target_role != null && SREClient.gameComponent.isKillerTeamRole(target_role)) {
                            // 杀手队友无距离限制
                        } else {
                            // 普通玩家只能透视10格内
                            if (target_player.distanceTo(self) >= 10) {
                                return -2;
                            }
                        }
                    }
                    // 雪原猎手直觉：未开技能10格范围，开技能全图，统一金黄色边框
                    if (SREClient.gameComponent.isRole(self, ModRoles.SNOW_HUNTER)) {
                        var snowComp = ModComponents.SNOW_HUNTER.maybeGet(self).orElse(null);
                        boolean skillActive = snowComp != null && snowComp.isSkillActive();

                        if (!skillActive && target_player.distanceTo(self) >= 10) {
                            return -2;
                        }

                        return new Color(255, 215, 0).getRGB();
                    }



                    // 魔术师：杀手看魔术师时显示红色边框（像看其他杀手一样）
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.MAGICIAN)) {
                        target_role = RoleUtils
                                .getRole(MagicianPlayerComponent.KEY.get(target_player).getDisguiseRoleId());
                    }

                    if (RoleUtils.compareRole(target_role, ModRoles.PUPPETEER)) {
                        // int entityOffset = target_player.getId() * 7;
                        return (ModRoles.PUPPETEER.color());
                    }
                    if (SREClient.gameComponent.isRole(self, ModRoles.COMMANDER)) {
                        if (isKillerTeam(target_role)) {
                            return getRoleColor(target_role);
                        }
                        if (target_player.distanceTo(self) <= 5) {
                            var role = SREClient.gameComponent.getRole(target_player);
                            if (role != null && role.isVigilanteTeam()) {
                                return new Color(63, 72, 204).getRGB();
                            }
                        }
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.VULTURE)) {
                        return (ModRoles.VULTURE.color());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.ADMIRER)) {
                        return (ModRoles.ADMIRER.color());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.EXECUTIONER)) {
                        return (ModRoles.EXECUTIONER.color());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.JESTER)) {
                        return (Color.PINK.getRGB());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.LOST_KILLER)) {
                        return TMMRoles.CIVILIAN.color();
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.PRANKSTER)) {
                        return -2;
                    }
                    if (RoleUtils.compareRole(target_role, SERoles.AMNESIAC)) {
                        if (StupidExpress.CONFIG.rolesSection.amnesiacSection.amnesiacGlowsDifferently) {
                            return SERoles.AMNESIAC.color();
                        }
                    }

                    if (SREClient.gameComponent.isRole(self, RedHouseRoles.REMILIA)) {
                        if (!self.hasEffect(ModEffects.SAFE_TIME)) {
                            if (target.distanceToSqr(self) <= 25) {
                                if (RoleUtils.compareRole(target_role, RedHouseRoles.PACHURI)) {
                                    return RedHouseRoles.PACHURI.color();
                                } else if (RoleUtils.compareRole(target_role, RedHouseRoles.FURANDORU)) {
                                    return RedHouseRoles.FURANDORU.color();
                                }
                            }
                        }

                    }
                    // 疫使：杀手本能中透视的框为深绿色
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.INFECTED)) {
                        return new Color(0, 100, 0).getRGB(); // 深绿色
                    }
                    // 葬仪：杀手本能中透视的框为淡灰色
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.MORTICIAN_BODYMAKER)) {
                        return new Color(180, 180, 180).getRGB(); // 淡灰色
                    }
                    // 肉汁：当杀手在4格范围内时，该杀手的透视框变为深蓝色
                    if (RoleUtils.compareRole(target_role, ModRoles.MEATBALL)) {
                        if (self.distanceTo(target_player) <= 4.0) {
                            return new Color(0, 0, 180).getRGB(); // 深蓝色
                        }
                    }
                    // 风精灵：杀手本能中透视的框为浅青色
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.WIND_YAOSE)) {
                        return new Color(255, 255, 51).getRGB(); // 黄色
                    }

                // 默认fallback
                    if (target_role == null)
                        return Color.WHITE.getRGB();
                    if (target_role.canUseKiller()) {
                        return Color.RED.getRGB();
                    } else if (target_role.isNeutralForKiller()) {
                        return Color.ORANGE.getRGB();
                    } else {
                        if (SREClient.gameComponent.isRole(self, ModRoles.MA_CHEN_XU)) {
                            if (SREPlayerMoodComponent.KEY.get(target_player).getMood() <= 0.1) {
                                return java.awt.Color.CYAN.getRGB();// 青色
                            }
                        }
                        if (SREClient.gameComponent.isRole(self, ModRoles.DIO)) {
                            if (RoleUtils.compareRole(target_role, ModRoles.JOJO)) {
                                return Color.CYAN.getRGB();
                            }
                        }
                        if (SREGameTimeComponent.KEY.get(client.level).getTime() >= GameConstants
                                .getFurandoruSafeLine()) {
                            if (SREClient.gameComponent.isRole(target_player, RedHouseRoles.FURANDORU)) {
                                return -2;
                            }
                        }
                        if (SREClient.gameComponent.isRole(target_player, ModRoles.GAMBLER)) {
                            return -2;
                        }
                        return TMMRoles.CIVILIAN.color();
                    }
                }
            }
            // 布谷鸟：只透视自己下的蛋（BlockDisplay）
            if (SREClient.gameComponent.isRole(self, ModRoles.CUCKOO)) {
                if (target instanceof Display.BlockDisplay blockDisplay) {
                    if (!hasInstinct)
                        return -1;
                    if (!GameUtils.isPlayerAliveAndSurvival(self))
                        return -1;
                    try {
                        if (CuckooEggData.isOwnEggClient(blockDisplay)) {
                            return ModRoles.CUCKOO.color();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return -1;
        });

        // 工人：透视工程师和建筑师（开局即可透视，无需购买物品）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            if (!SREClient.gameComponent.isRole(Minecraft.getInstance().player, ModRoles.WORKER))
                return -1;
            if (target instanceof Player targetPlayer) {
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.ENGINEER)
                        || SREClient.gameComponent.isRole(targetPlayer, ModRoles.BUILDER)) {
                    return ModRoles.WORKER.color();
                }
                // 工人只能透视工程师和建筑师，其他玩家禁用透视
                return -2;
            }
            return -1;
        });

        // 监护人：透视智力障碍患者（开局即可透视，无需本能，浅蓝色高亮）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            if (!SREClient.gameComponent.isRole(Minecraft.getInstance().player, ModRoles.GUARDIAN))
                return -1;
            if (target instanceof Player targetPlayer) {
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.ZHIZHANG)) {
                    if (GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                        return ModRoles.ZHIZHANG.color();
                    }
                }
                // 监护人只能透视智力障碍患者，其他玩家禁用透视
                return -2;
            }
            return -1;
        });

        // 经纪人：透视歌手与明星（常驻，无需本能）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            Player self = Minecraft.getInstance().player;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.JINGJIREN_WOW))
                return -1;
            if (target instanceof Player targetPlayer) {
                if (targetPlayer == self)
                    return -1;
                if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
                    return -2;
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.SINGER)) {
                    return ModRoles.SINGER.color();
                }
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.SUPERSTAR)) {
                    return ModRoles.SUPERSTAR.color();
                }
                // 经纪人只能透视歌手与明星，其他玩家禁用透视
                return -2;
            }
            return -1;
        });

        // 被签约的歌手/明星：透视签约自己的经纪人（常驻，无需本能）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            Player self = Minecraft.getInstance().player;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            java.util.UUID managerUuid = null;
            if (SREClient.gameComponent.isRole(self, ModRoles.SINGER)) {
                managerUuid = ModComponents.SINGER.get(self).signedManagerUuid;
            } else if (SREClient.gameComponent.isRole(self, ModRoles.SUPERSTAR)) {
                managerUuid = ModComponents.STAR.get(self).signedManagerUuid;
            }
            if (managerUuid == null)
                return -1;
            if (target instanceof Player targetPlayer) {
                if (targetPlayer.getUUID().equals(managerUuid)
                        && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                    return ModRoles.JINGJIREN_WOW.color();
                }
            }
            // 不阻断歌手/明星的其他高亮来源
            return -1;
        });

        // 雪怪：透视所有存活玩家（常驻，无需本能，白色边框）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            if (!SREClient.gameComponent.isRole(Minecraft.getInstance().player, ModRoles.SNOWGUAI_WOW))
                return -1;
            if (target instanceof Player targetPlayer) {
                if (targetPlayer == Minecraft.getInstance().player)
                    return -1;
                if (GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                    // 不能返回纯白 (0xFFFFFF == -1)，使用近似白
                    return new java.awt.Color(254, 254, 254).getRGB();
                }
            }
            return -1;
        });

        // 坠木：仅透视皮革嘎的（常驻，无需本能）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            Player self = Minecraft.getInstance().player;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.ZHUIMU))
                return -1;
            if (target instanceof Player targetPlayer) {
                if (targetPlayer == self)
                    return -1;
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.PIGE)
                        && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                    return ModRoles.PIGE.color();
                }
                // 仅透视皮革嘎的，其他玩家禁用
                return -2;
            }
            return -1;
        });

        // 皮革嘎的：仅透视坠木（常驻，无需本能）
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null)
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            Player self = Minecraft.getInstance().player;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.PIGE))
                return -1;
            if (target instanceof Player targetPlayer) {
                if (targetPlayer == self)
                    return -1;
                if (SREClient.gameComponent.isRole(targetPlayer, ModRoles.ZHUIMU)
                        && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                    return ModRoles.ZHUIMU.color();
                }
                // 仅透视坠木，其他玩家禁用
                return -2;
            }
            return -1;
        });

    }

    private static int getRoleColor(SRERole target_role) {
        if (target_role == null)
            return TMMRoles.CIVILIAN.color();
        return target_role.color();
    }

    /** 检查鬼眼·杨间的被动扫描是否激活。 */
    private static boolean isGhostEyeScanActive(Player self) {
        if (self == null || self.level() == null) {
            return false;
        }
        GhostEyePlayerComponent comp = GhostEyePlayerComponent.KEY.maybeGet(self).orElse(null);
        if (comp != null && comp.revealTicks > 0) {
            return true;
        }

        int intervalTicks = GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().ghostEyeScanInterval);
        if (intervalTicks <= 0) {
            return false;
        }
        return self.level().getGameTime() % intervalTicks < GhostEyePlayerComponent.REVEAL_TICKS;
    }

    private static boolean isGhostEyeRole(Player self) {
        if (self == null || SREClient.gameComponent == null)
            return false;
        SRERole role = SREClient.gameComponent.getRole(self);
        return role != null && role.identifier().equals(ModRoles.GHOST_EYE_ID);
    }

    private static boolean isKillerTeam(SRERole role) {
        if (SREClient.gameComponent == null) {
            return false;
        }
        return SREClient.gameComponent.isKillerTeamRole(role);
    }

    /**
     * 检查目标玩家是否属于「无法被任何本能透视」的职业。
     * 包含：小透明、秉烛人、雇佣兵、捣蛋鬼、赌徒。
     */
    private static boolean isTargetInvisibleToInstinct(Player target) {
        if (SREClient.gameComponent == null || target == null)
            return false;
        return SREClient.gameComponent.isRole(target, ModRoles.GHOST)
                || SREClient.gameComponent.isRole(target, ModRoles.CANDLE_BEARER)
                || SREClient.gameComponent.isRole(target, ModRoles.MERCENARY)
                || SREClient.gameComponent.isRole(target, ModRoles.PRANKSTER)
                || SREClient.gameComponent.isRole(target, ModRoles.GAMBLER);
    }

    private static final int[] GRADIENT_COLORS = {
            new Color(255, 0, 0).getRGB(), // 红色
            new Color(255, 85, 0).getRGB(), // 橙红
            new Color(255, 170, 0).getRGB(), // 橙色
            new Color(255, 255, 0).getRGB(), // 黄色
            new Color(255, 170, 0).getRGB(), // 橙色
            new Color(255, 85, 0).getRGB(), // 橙红
    };

    // 渐变周期（tick）
    private static final int GRADIENT_CYCLE = 60; // 3秒一个周期

    /**
     * 获取渐变颜色
     * 
     * @param tickOffset 每个实体的偏移量，使不同实体颜色略有不同
     * @return 当前渐变颜色
     */
    public static int getGradientColor(int tickOffset) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return GRADIENT_COLORS[0];

        long worldTime = client.level.getGameTime();
        int cyclePosition = (int) ((worldTime + tickOffset) % GRADIENT_CYCLE);

        // 计算在颜色数组中的位置
        float progress = (float) cyclePosition / GRADIENT_CYCLE * GRADIENT_COLORS.length;
        int colorIndex = (int) progress;
        float blend = progress - colorIndex;

        // 获取当前颜色和下一个颜色
        int currentColor = GRADIENT_COLORS[colorIndex % GRADIENT_COLORS.length];
        int nextColor = GRADIENT_COLORS[(colorIndex + 1) % GRADIENT_COLORS.length];

        // 混合两个颜色
        return blendColors(currentColor, nextColor, blend);
    }

    /**
     * 混合两个颜色
     */
    public static int blendColors(int color1, int color2, float blend) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * blend);
        int g = (int) (g1 + (g2 - g1) * blend);
        int b = (int) (b1 + (b2 - b1) * blend);

        return (r << 16) | (g << 8) | b;
    }
}
