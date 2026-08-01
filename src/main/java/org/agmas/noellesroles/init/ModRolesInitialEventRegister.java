package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RolePassive;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.RicesRoleRhapsody;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PlayerVolumeComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.game.roles.innocence.accountant.AccountantPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.alchemist.AlchemistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.attendant.AttendantHandler;
import org.agmas.noellesroles.game.roles.innocence.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.hoan_meirin.HoanMeirinPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.painter.PainterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.shushi.ShuShiPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.dio.DIOPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.eling_apex.ElingApexPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.commander.CommanderHandler;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mortician.MorticianBodyMakerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.game.roles.special.super_loose_end.SuperLooseEndPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.UUID;

public class ModRolesInitialEventRegister {

    public static void register() {

        // 初始化亡灵之主事件（亡者复苏 / 角色初始化）
        org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordHandler.init();

        // ========== 添加：游戏结束时强制重置黑警组件 ==========
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (var player : serverLevel.players()) {
                var comp = ModComponents.CORRUPT_COP.maybeGet(player).orElse(null);
                if (comp != null) {
                    comp.reset();  // 使用Reset 方法
                }
            }
        });

        // 游戏真正开始时也重置一次
        OnGameTrueStarted.EVENT.register((serverLevel) -> {
            for (var player : serverLevel.players()) {
                var comp = ModComponents.CORRUPT_COP.maybeGet(player).orElse(null);
                if (comp != null) {
                    comp.reset();
                }
            }
        });

        // 初始化仇杀客事件
        BloodFeudistPlayerComponent.registerEvents();
        // 初始化熊孩子音频事件
        org.agmas.noellesroles.game.roles.innocence.child.ChildPunchHandler.register();
        // 初始化武术家击打事件
        org.agmas.noellesroles.game.roles.innocence.wushujia.WushujiaPunchHandler.register();
        // 初始化皮革嘎的的铁剑攻击事件
        org.agmas.noellesroles.game.roles.neutral.pigegade.PigeSwordHandler.register();
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            // 魔术师角色初始化
            if (RoleUtils.compareRole(role, ModRoles.CONSPIRATOR)) {
                ModEventsRegister.reJudgeSpectatorsPenalty(player.level());
            }
            if (role.identifier().equals(ModRoles.BARTENDER.identifier())) {
                FoodDrinkGlowComponent.KEY.get(player).init();
            }
            if (role.identifier().equals(ModRoles.CHEF.identifier())) {
                FoodDrinkGlowComponent.KEY.get(player).init();
            }
            if (role.identifier().equals(ModRoles.MAGICIAN.identifier())) {
                var magicianComponent = ModComponents.MAGICIAN.maybeGet(player).orElse(null);
                if (magicianComponent != null) {
                    // 停止疯狂模式（如果之前存在）
                    var psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
                    if (psychoComponent != null) {
                        psychoComponent.init();
                    }
                    // 随机分配一个杀手身份给魔术师（原版杀手、毒师和清道夫除外）
                    magicianComponent.startDisguiseRandomRole();
                }
                // 检查是否有指挥官，如果有则加入指挥官频道
                boolean hasCommander = player.getServer().getPlayerList().getPlayers().stream()
                        .anyMatch(p -> {
                            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(p.level());
                            var ro = gw.getRole(p);
                            if (ro != null) {
                                return ro.identifier().equals(ModRoles.COMMANDER_ID);
                            }
                            return false;
                        });
                if (hasCommander) {
                    // 魔术师加入指挥官频道
                    player.sendSystemMessage(Component.translatable("message.magician.commander_present_joined_channel")
                            .withStyle(ChatFormatting.GOLD));
                }
            }

            if (role.identifier().equals(ModRoles.DIO.identifier())) {
                var tpc = DIOPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(RedHouseRoles.HOAN_MEIRIN.identifier())) {
                var tpc = HoanMeirinPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(RedHouseRoles.FURANDORU.identifier())) {
                var tpc = GhostPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(RedHouseRoles.MAID_SAKUYA.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            if (role.identifier().equals(ModRoles.JOJO.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            // 初始化记录员
            if (role.identifier().equals(ModRoles.RECORDER.identifier())) {
                var tpc = RecorderPlayerComponent.KEY.get(player);
                tpc.initRecorder();
            }
            if (role.identifier().equals(ModRoles.EXAMPLER.identifier())) {
                var tpc = SREAbilityPlayerComponent.KEY.get(player);
                tpc.init(false);
                tpc.status = 0;
                tpc.sync();
                return;
            }
            if (role.identifier().equals(ModRoles.THIEF.identifier())) {
                int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                var tpc = ThiefPlayerComponent.KEY.get(player);
                tpc.updateHonorCost(totalPlayers);
            }
            if (role.identifier().equals(ModRoles.WATCHER.identifier())) {
                var tpc = WatcherPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(ModRoles.MERCENARY.identifier())) {
                var mercenary = MercenaryPlayerComponent.KEY.get(player);
                mercenary.init();
                mercenary.sync();
            }
            // 坠木角色初始化
            if (role.identifier().equals(ModRoles.ZHUIMU.identifier())) {
                var zhuimu = org.agmas.noellesroles.game.roles.neutral.zhuimu.ZhuimuPlayerComponent.KEY.get(player);
                zhuimu.init();
                zhuimu.sync();
            }
            // 皮革嘎的角色初始化（设置猪形态）
            if (role.identifier().equals(ModRoles.PIGE.identifier())) {
                var pige = org.agmas.noellesroles.game.roles.neutral.pigegade.PigegadePlayerComponent.KEY.get(player);
                pige.init();
                pige.sync();
            }
            // 将坠木和皮革嘎的加入同一个 team，确保互相可见（seeFriendlyInvisibles 保障透视）
            if (role.identifier().equals(ModRoles.ZHUIMU.identifier())
                    || role.identifier().equals(ModRoles.PIGE.identifier())) {
                joinZhuimuPigeTeam(player);
            }
            if (role.identifier().equals(ModRoles.WAYFARER.identifier())) {
                MCItemsUtils.clearItem(player);
                RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_REVOLVER.getDefaultInstance());
                RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_KNIFE.getDefaultInstance());
                // (WayfarerPlayerComponent.KEY.get(player)).reset();
                return;
            }
            if (role.identifier().equals(ModRoles.WIND_YAOSE.identifier())) {
                // 现在在NoellesRolesAbilityPlayerComponent serverTick中处理。
                return;
            }
            if (role.identifier().equals(ModRoles.ACCOUNTANT.identifier())) {
                // 会计角色初始化
                var accountantComponent = AccountantPlayerComponent.KEY.get(player);
                accountantComponent.init();
                return;
            }
            if (role.identifier().equals(ModRoles.ALCHEMIST.identifier())) {
                // 药剂师角色初始化
                var alchemistComponent = AlchemistPlayerComponent.KEY.get(player);
                alchemistComponent.init();
                return;
            }
            if (role.identifier().equals(ModRoles.WITCH.identifier())) {
                // 女巫角色初始化
                var witchComponent = org.agmas.noellesroles.game.roles.neutral.witch.WitchPlayerComponent.KEY.get(player);
                witchComponent.init();
                return;
            }
            if (role.identifier().equals(ModRoles.BLACKKE.identifier())) {
                // Hacker role initialization
                var blackkeComponent = org.agmas.noellesroles.game.roles.innocence.blackke.BlackkePlayerComponent.KEY.get(player);
                blackkeComponent.init();
                return;
            }
            if (role.identifier().equals(ModRoles.SHUSHI.identifier())) {
                // 术士角色初始化
                var shushiComponent = ShuShiPlayerComponent.KEY.get(player);
                shushiComponent.init();
                return;
            }
            // 派对狂角色初始化 - 基于开局玩家数设置threshold
            if (role.identifier().equals(ModRoles.PARTY_KILLER.identifier())) {
                int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                var partyComponent = org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent.KEY
                        .get(player);
                partyComponent.initThreshold(totalPlayers);
                return;
            }
            if (role.identifier().equals(TMMRoles.KILLER.identifier())) {
                player.addItem(TMMItems.KNIFE.getDefaultInstance().copy());
                return;
            }
            if (role.identifier().equals(TMMRoles.VIGILANTE.identifier())) {
                if (!SREItemUtils.hasItem(player, TMMItems.REVOLVER)) {
                    player.addItem(TMMItems.REVOLVER.getDefaultInstance().copy());
                }
                return;
            }
            if (role.identifier().equals(ModRoles.SHERIFF_ID)) {
                // 警卫角色初始化：重置任务计数
                return;
            }
            if (role.identifier().equals(ModRoles.ATTENDANT.identifier())) {
                if (player instanceof ServerPlayer sp)
                    SRE.SendRoomInfoToPlayer(sp);
                return;
            }
            if (role.identifier().equals(ModRoles.GUEST_GHOST.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(player);
            abilityPlayerComponent.init(false);
            abilityPlayerComponent.cooldown = NoellesRolesConfig.HANDLER.instance().generalCooldownTicks;

            if (role.equals(ModRoles.BROADCASTER)) {
                abilityPlayerComponent.cooldown = 0;
                SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                playerShopComponent.setBalance(200);
                playerShopComponent.sync();
            }
            if (role.equals(ModRoles.EXECUTIONER)) {
                ExecutionerPlayerComponent executionerPlayerComponent = (ExecutionerPlayerComponent) ExecutionerPlayerComponent.KEY
                        .get(player);
                SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                        .get(player);
                executionerPlayerComponent.init();
                playerShopComponent.setBalance(100);
                executionerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.VULTURE)) {
                if (VulturePlayerComponent.KEY.isProvidedBy(player)) {
                    VulturePlayerComponent vulturePlayerComponent = VulturePlayerComponent.KEY.get(player);
                    vulturePlayerComponent.init();
                    vulturePlayerComponent.bodiesRequired = Math.max(1, (int) ((player.level().players().size() / 3f)
                            - Math.floor(player.level().players().size() / 6f)));
                    vulturePlayerComponent.sync();
                }
            }
            if (role.equals(ModRoles.PELICAN)) {
                if (PelicanPlayerComponent.KEY.isProvidedBy(player)) {
                    var pelicanComponent = PelicanPlayerComponent.KEY.get(player);
                    pelicanComponent.init();
                    int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                    double percent = NoellesRolesConfig.HANDLER.instance().pelicanEatPercentage;
                    pelicanComponent.requiredEaten = Math.max(1,
                            (int) Math.ceil(totalPlayers * (percent / 100.0D)) - 1);
                    pelicanComponent.sync();
                }
            }
            if (role.equals(ModRoles.INSANE_KILLER)) {
                final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(player);
                insaneKillerPlayerComponent.init();
                insaneKillerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.RECORDER)) {
                final var recorderPlayerComponent = RecorderPlayerComponent.KEY.get(player);
                recorderPlayerComponent.initializeRoles();
            }

            // 更新所有记录员的可用角色列表
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                if (SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.RECORDER)) {
                    RecorderPlayerComponent.KEY.get(p).updateAvailableRoles();
                }
            }
            if (role.equals(ModRoles.RECORDER)) {
                final var recorderPlayerComponent = RecorderPlayerComponent.KEY.get(player);
                recorderPlayerComponent.init();
                recorderPlayerComponent.sync();
            }

            if (role.equals(ModRoles.GAMBLER)) {
                org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent gamblerPlayerComponent = org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent.KEY
                        .get(player);
                gamblerPlayerComponent.init();
                gamblerPlayerComponent.sync();
            }

            if (role.equals(ModRoles.NOISEMAKER)) {
                org.agmas.noellesroles.game.roles.innocence.noise_maker.NoiseMakerPlayerComponent noiseMakerPlayerComponent = org.agmas.noellesroles.game.roles.innocence.noise_maker.NoiseMakerPlayerComponent.KEY
                        .get(player);
                noiseMakerPlayerComponent.init();
                noiseMakerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.GHOST)) {
                org.agmas.noellesroles.game.roles.innocence.ghost.GhostPlayerComponent ghostPlayerComponent = org.agmas.noellesroles.game.roles.innocence.ghost.GhostPlayerComponent.KEY
                        .get(player);
                ghostPlayerComponent.init();
                ghostPlayerComponent.sync();
            }
            if (role.equals(ModRoles.CANDLE_BEARER)) {
                CandleBearerPlayerComponent candleBearer = CandleBearerPlayerComponent.KEY.get(player);
                candleBearer.init();
                RoleUtils.insertStackInFreeSlot(player, Items.CANDLE.getDefaultInstance());
                candleBearer.sync();
            }
            if (role.equals(ModRoles.CAKE_MAKER)) {
                ModComponents.CAKE_MAKER.get(player).init();
            }
            if (role.equals(ModRoles.RAVEN)) {
                ModComponents.RAVEN.get(player).init();
            }
            if (role.equals(ModRoles.AMON)) {
                ModComponents.AMON.get(player).init();
            }
            if (role.equals(ModRoles.WRAITH_ASSASSIN)) {
                ModComponents.WRAITH_ASSASSIN.get(player).init();
            }
            if (role.equals(ModRoles.ADVENTURER)) {
                ModComponents.ADVENTURER.get(player).init();
            }
            // 操纵师角色初始化
            if (role.equals(ModRoles.MANIPULATOR)) {
                ManipulatorPlayerComponent manipulatorPlayerComponent = ManipulatorPlayerComponent.KEY.get(player);
                manipulatorPlayerComponent.init();
                manipulatorPlayerComponent.sync();
            }
            // 巫毒师角色初始化 - 开局75秒冷却
            if (role.equals(ModRoles.VOODOO)) {
                abilityPlayerComponent.cooldown = 100 * 20;
                abilityPlayerComponent.sync();
                return;
            }
            if (role.equals(ModRoles.BOMBER)) {
                if (role.equals(ModRoles.MONITOR)) {
                    MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY.get(player);
                    monitorComponent.init();
                    monitorComponent.sync();
                }
                // bomberPlayerComponent.reset(); // 如果有 reset 方法
                ModComponents.BOMBER.sync(player);
            }
            // if (role.equals(SHERIFF)) {
            // player.giveItemStack(TMMItems.REVOLVER.getDefaultStack());
            // org.agmas.noellesroles.game.roles.sheriff.SheriffPlayerComponent
            // sheriffPlayerComponent =
            // org.agmas.noellesroles.game.roles.sheriff.SheriffPlayerComponent.KEY.get(player);
            // sheriffPlayerComponent.reset();
            // sheriffPlayerComponent.sync();
            // }
            // 在角色分配时清除之前的跟踪者状态（如果有）
            // 但是如果跟踪者正在进化（切换角色），不清除状态
            StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(player);
            if (!stalkerComp.isActiveStalker()) {
                stalkerComp.clearAll();
            }

            // // 在角色分配时清除之前的傀儡师状态（如果有）
            // // 但是如果傀儡师正在操控假人（临时切换角色），不清除状态
            // PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);
            // if (!puppeteerComp.isPuppeteerMarked) {
            // puppeteerComp.clearAll();
            // }
            RicesRoleRhapsody.onRoleAssigned(player, role);
            if (role.identifier().equals(ModRoles.ELF.identifier())) {
                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
                shopComponent.setBalance(45);
                return;
            }
            // 猎魔人角色初始化 - 初始金币45（同游侠）
            if (role.identifier().equals(ModRoles.LIEMOREN.identifier())) {
                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
                shopComponent.setBalance(45);
                return;
            }

            // 纵火犯物品初始化
            if (role.equals(SERoles.ARSONIST)) {
                player.addItem(SEItems.JERRY_CAN.getDefaultInstance().copy());
                player.addItem(SEItems.LIGHTER.getDefaultInstance().copy());
            }
            if (role.equals(ModRoles.NIAN_SHOU)) {
                var comc = NianShouPlayerComponent.KEY.maybeGet(player).orElse(null);
                if (comc != null) {
                    comc.init();
                }
            }
            if (role.equals(ModRoles.PUPPETEER)) {
                var comc = PuppeteerPlayerComponent.KEY.maybeGet(player).orElse(null);
                if (comc != null) {
                    if (!comc.isActivePuppeteer())
                        comc.init();
                }
            }
            // 画家角色初始化
            if (role.equals(ModRoles.PAINTER)) {
                var painterComponent = PainterPlayerComponent.KEY.get(player);
                painterComponent.init();
                painterComponent.sync();
            }
            // 葬仪角色初始化
            if (role.equals(ModRoles.MORTICIAN_BODYMAKER)) {
                var morticianComponent = MorticianBodyMakerPlayerComponent.KEY.get(player);
                morticianComponent.init();
                morticianComponent.sync();
            }
            // 幻音师角色初始化
            if (role.equals(ModRoles.PHANTOM_MUSICIAN)) {
                var pmComponent = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                        .get(player);
                pmComponent.init();
                pmComponent.sync();
            }
            if (role.equals(ModRoles.GODFATHER)) {
                if (player instanceof ServerPlayer sp) {
                    for (var p : sp.serverLevel().players()) {
                        if (p != null) {
                            p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
                        }
                    }
                }
            }
            // 熊孩子角色初始化
            if (role.identifier().equals(ModRoles.CHILD.identifier())) {
                var childComp = org.agmas.noellesroles.game.roles.innocence.child.ChildPlayerComponent.KEY.get(player);
                childComp.init();
                childComp.sync();
                return;
            }
            // 召回杀手角色初始化
            if (role.identifier().equals(ModRoles.RECALL_KILLER.identifier())) {
                var comp = ModComponents.RECALL_KILLER.get(player);
                comp.init();
                comp.sync();
                return;
            }
            // 情报官角色初始化
            if (role.identifier().equals(ModRoles.INTELLIGENCE.identifier())) {
                var comp = ModComponents.INTELLIGENCE.get(player);
                comp.init();
                comp.sync();
                return;
            }
            // 鬼魅角色初始化
            if (role.identifier().equals(ModRoles.BETTER_KILLER_GHOST.identifier())) {
                var comp = ModComponents.BETTER_KILLER_GHOST.get(player);
                comp.init();
                comp.sync();
                return;
            }

            // 时空旅者角色初始化
            if (role.identifier().equals(ModRoles.RUIKE.identifier())) {
                var comp = ModComponents.RUIKE.get(player);
                comp.init();
                comp.sync();
                return;
            }
            // 雪原猎手初始化
            if (role.identifier().equals(ModRoles.SNOW_HUNTER.identifier())) {
                var comp = ModComponents.SNOW_HUNTER.get(player);
                comp.init();
            }
            // 恶灵角色初始化
            if (role.identifier().equals(ModRoles.ELING_APEX.identifier())) {
                var comp = ModComponents.ELING_APEX.get(player);
                comp.init();
                comp.sync();
            }
            // 如果不拦截就同步
            abilityPlayerComponent.sync();
        });



    }

    static {//技能注册
        //调用熊孩子技能注册
        org.agmas.noellesroles.game.roles.innocence.child.ChildSkillRegistry.register();
        RolePassive.register(ModRoles.PHANTOM_MUSICIAN,
                RolePassive.passive(SRE.id("phantom_musician_income"),
                        "passive.noellesroles.phantom_musician.income", 30 * 20, player -> {
                            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                            if (gameWorld.isRunning() && GameUtils.isPlayerAliveAndSurvival(player)) {
                                SREPlayerShopComponent.KEY.get(player).addToBalance(50);
                            }
                        }));
        // 宿命的罪人技能注册：
        // 技能 1「命运的启示」(G)：近距离查看准星目标最近 3 次杀人方式
        // 技能 2「重启」(Shift+G)：随机死因死亡脱离，回房间 + 短暂无敌
        RoleSkill.register(ModRoles.DOOMED_SINNER,
                RoleSkill.skill(SRE.id("doomed_sinner_revelation"),
                        "skill.noellesroles.doomed_sinner.revelation",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) {
                                return false;
                            }
                            ServerPlayer target = context.target() != null
                                    && player.level().getPlayerByUUID(context.target()) instanceof ServerPlayer sp
                                            ? sp
                                            : null;
                            return org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent
                                    .revealFate(player, target);
                        }).cooldownSeconds(40).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("doomed_sinner_reboot"),
                        "skill.noellesroles.doomed_sinner.reboot",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) {
                                return false;
                            }
                            return org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent
                                    .reboot(player);
                        }).cooldownSeconds(75).shifted(true).showOnHud(true).announceToSelf(true).build());

        // 智力障碍患者技能注册：探查周围3.5格内有刀的玩家，5秒后高亮3秒，CD60秒
        RoleSkill.register(ModRoles.ZHIZHANG, RoleSkill.skill(
                SRE.id("zhizhang_detect"),
                "skill.noellesroles.zhizhang.detect",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = org.agmas.noellesroles.component.ModComponents.ZHIZHANG.get(player);
                    if (comp == null) return false;
                    return comp.useSkill();
                }).cooldownSeconds(60).build());

        // 监护人技能注册：花费125金币，解除智力障碍患者的debuff 12秒并给予2秒无敌，CD30秒
        RoleSkill.register(ModRoles.GUARDIAN, RoleSkill.skill(
                SRE.id("guardian_protect"),
                "skill.noellesroles.guardian.protect",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = org.agmas.noellesroles.component.ModComponents.GUARDIAN.get(player);
                    if (comp == null) return false;
                    return comp.useSkill();
                }).cooldownSeconds(30).build());

        RolePassive.register(ModRoles.PHANTOM_MUSICIAN,
                RolePassive.passive(SRE.id("phantom_musician_income"),
                        "passive.noellesroles.phantom_musician.income", 30 * 20, player -> {
                            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                            if (gameWorld.isRunning() && GameUtils.isPlayerAliveAndSurvival(player)) {
                                SREPlayerShopComponent.KEY.get(player).addToBalance(50);
                            }
                        }));

        // 疫使技能注册：按技能键感染目标玩家
        RoleSkill.register(ModRoles.INFECTED, RoleSkill.skill(
                SRE.id("infected_infect"),
                "skill.noellesroles.infected.infect",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();

                    if (targetUuid == null) {
                        return false;
                    }

                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (target == null) {
                        return false;
                    }

                    if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                        return false;
                    }

                    InfectedPlayerComponent targetComponent = ModComponents.INFECTED.get(target);
                    if (targetComponent.infectedTicks > 0) {
                        return false;
                    }

                    targetComponent.infect(player);

                    if (NRSounds.INFECTED_INFECT != null) {
                        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                                NRSounds.SYRINGE_STAB, SoundSource.MASTER, 0.5f, 0.5f);
                    }
                    return true;
                }).cooldownSeconds(80).build());

        // 鹈鹕技能注册：按技能键吞噬鼠标准星对准的玩家，蹲下按技能键释放最后吞噬的玩家
        RoleSkill.register(ModRoles.PELICAN,
                RoleSkill.skill(SRE.id("pelican_eat"), "skill.noellesroles.pelican.eat", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(player);
                    if (comp == null || context.target() == null)
                        return false;
                    Player candidate = player.level().getPlayerByUUID(context.target());
                    if (!(candidate instanceof ServerPlayer target)
                            || !GameUtils.isPlayerAliveAndSurvival(target)
                            || player.distanceToSqr(target) > 2.15D * 2.15D
                            || !player.hasLineOfSight(target)) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.pelican.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    return comp.tryEat(target);
                    // 不在此处设统一技能冷却：统一技能系统无论 handler 是否成功都会进入冷却
                    // （见 RoleSkill.useUnified），会导致"没吃到人也进CD"。鹈鹕冷却由
                    // PelicanPlayerComponent.eatCooldownUntil 管理，仅在成功吞噬后生效（并由 PelicanHud 显示）。
                }).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("pelican_release"), "skill.noellesroles.pelican.release", context -> {
                    PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(context.player());
                    return comp != null && comp.releaseLast();
                }).shifted(true).announceToSelf(false).build());

        // 阿蒙技能：
        // - G 键：对准星玩家静默种下时之虫（附身期间也可为其他人种虫）
        // - Shift+G 键：附身期间完成夺舍（变成目标、令其死亡、本体处生成尸体）
        RoleSkill.register(ModRoles.AMON,
                RoleSkill.skill(SRE.id("amon_plant_seed"), "skill.noellesroles.amon.plant_seed", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var comp = org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent.KEY.get(player);
                    if (comp == null)
                        return false;
                    // G 键始终执行种时之虫（附身期间不夺舍，夺舍改用 Shift+G）
                    if (!context.skillReady())
                        return false;
                    ServerPlayer target = context.target() == null ? null
                            : (player.level().getPlayerByUUID(context.target()) instanceof ServerPlayer sp ? sp : null);
                    return comp.plantSeed(target);
                }).cooldownSeconds(20).toggleable(true).announceToSelf(false).build(),

                // Shift+G：附身期间完成夺舍
                RoleSkill.skill(SRE.id("amon_usurp"), "skill.noellesroles.amon.usurp", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var comp = org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent.KEY.get(player);
                    if (comp == null)
                        return false;
                    if (!comp.isPossessing())
                        return false;
                    return comp.finalizePossession();
                }).shifted(true).announceToSelf(false).build());

        // 葬仪技能注册：使用当前模式的技能
        RoleSkill.register(ModRoles.MORTICIAN_BODYMAKER, context -> {
            ServerPlayer player = context.player();
            MorticianBodyMakerPlayerComponent morticianComponent = MorticianBodyMakerPlayerComponent.KEY.get(player);
            if (morticianComponent != null) {
                morticianComponent.useAbility();
            }
        });

        // 咒法师技能注册：标记目标玩家
        RoleSkill.register(ModRoles.WARLOCK, context -> {
            ServerPlayer player = context.player();
            var comp = org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent.KEY.get(player);
            if (comp == null)
                return;
            UUID targetUuid = context.target();
            ServerPlayer target = null;
            if (targetUuid != null) {
                Player p = player.level().getPlayerByUUID(targetUuid);
                if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp)
                        && player.distanceToSqr(sp) <= 4.0D * 4.0D) {
                    target = sp;
                }
            }
            if (target != null && comp.tryMark(target)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.warlock.marked", target.getName().getString())
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.warlock.mark_fail").withStyle(ChatFormatting.RED),
                        true);
            }
        });

        // 幻音师技能注册：花费100金币传送到30格外随机一人的身边
        RoleSkill.register(ModRoles.PHANTOM_MUSICIAN, context -> {
            ServerPlayer player = context.player();
            var comp = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                    .get(player);
            if (comp == null)
                return;
            comp.useTeleport();
        });

        // 鬼魅技能注册：进入/操作幽影模式
        RoleSkill.register(ModRoles.BETTER_KILLER_GHOST, context -> {
            ServerPlayer player = context.player();
            var comp = ModComponents.BETTER_KILLER_GHOST.get(player);
            if (comp == null) return;
            comp.useAbility();
        });

        // 情报官技能注册：放置监视器
        RoleSkill.register(ModRoles.INTELLIGENCE, context -> {
            ServerPlayer player = context.player();
            var comp = ModComponents.INTELLIGENCE.get(player);
            if (comp == null) return;
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
            if (ability.cooldown > 0) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.cooldown", ability.cooldown / 20)
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            if (!comp.canPlaceMonitor()) {
                player.displayClientMessage(
                        Component.literal("监视器已用完").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            comp.addMonitor(player.getX(), player.getY(), player.getZ(), player.level().dimension().location());
            ability.cooldown = 60 * 20;
            ability.sync();
            player.displayClientMessage(
                    Component.literal("监视器已放置").withStyle(ChatFormatting.GREEN),
                    true);
        });

        // 海王技能注册：20格外水下玩家施加禁锢效果5秒，冷却60秒
        RoleSkill.register(ModRoles.SEA_KING, RoleSkill.skill(
                SRE.id("sea_king_aoe"),
                "skill.noellesroles.sea_king.aoe",
                context -> {
                    ServerPlayer player = context.player();
                    final double radius = 20.0D;
                    final int duration = 5 * 20;
                    int affected = 0;

                    for (var target : player.serverLevel().getEntitiesOfClass(
                            ServerPlayer.class,
                            player.getBoundingBox().inflate(radius),
                            p -> !p.getUUID().equals(player.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
                        if (player.distanceToSqr(target) > radius * radius) {
                            continue;
                        }
                        if (!(target.isInWater() || target.isUnderWater())) {
                            continue;
                        }

                        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, true, false));
                        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, true, false));
                        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, false, true, false));
                        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, duration, 0, false, true, false));
                        affected++;
                    }

                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.TRIDENT_RETURN, SoundSource.MASTER, 5.0F, 1.0F);

                    if (affected > 0) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.sea_king.skill_used", affected)
                                        .withStyle(ChatFormatting.AQUA),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.sea_king.skill_no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }

                    return true; // 始终进入冷却
                }).cooldownSeconds(60).build());

        // 清洁工技能注册：清除附近5格外掉落物，冷却90秒
        RoleSkill.register(ModRoles.CLEANER, RoleSkill.skill(
                SRE.id("cleaner_cleanup"),
                "skill.noellesroles.cleaner.cleanup",
                context -> {
                    ServerPlayer player = context.player();
                    var items = player.level().getEntitiesOfClass(ItemEntity.class,
                            player.getBoundingBox().inflate(5.0), (p) -> true);
                    int count = 0;
                    for (var it : items) {
                        it.discard();
                        count++;
                    }
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F,
                            1.0F + player.level().random.nextFloat() * 0.1F - 0.05F);
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.cleaner.cleanned", count)
                            .withStyle(ChatFormatting.GOLD), true);
                    return true;
                }).cooldownSeconds(90).build());

        // 布谷鸟技能注册：在脚下放置蛋，冷却20秒
        RoleSkill.register(ModRoles.CUCKOO, RoleSkill.skill(
                SRE.id("cuckoo_place_egg"),
                "skill.noellesroles.cuckoo.place_egg",
                context -> {
                    ServerPlayer player = context.player();
                    if (!(player instanceof ServerPlayer sp))
                        return false;
                    var comp = org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooPlayerComponent.KEY.get(player);
                    if (comp == null)
                        return false;
                    return comp.placeEgg(sp);
                }).cooldownSeconds(20).build());

        // 风妖精技能注册：30格外玩家降低音量10秒，冷却120秒
        RoleSkill.register(ModRoles.WIND_YAOSE, RoleSkill.skill(
                SRE.id("wind_yaose_volume"),
                "skill.noellesroles.wind_yaose.volume",
                context -> {
                    ServerPlayer player = context.player();
                    for (var p : player.level().players()) {
                        if (p.distanceTo(player) <= 30.0) {
                            PlayerVolumeComponent.KEY.get(p).setVolume(600, 0.05f);
                        }
                    }
                    return true;
                }).cooldownSeconds(120).build());

        // 噪音制造者技能注册：制造噪音，冷却60秒
        RoleSkill.register(ModRoles.NOISEMAKER, RoleSkill.skill(
                SRE.id("noisemaker_ability"),
                "skill.noellesroles.noisemaker.ability",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.NOISEMAKER.get(player);
                    if (comp == null)
                        return false;
                    comp.useAbility(); // 组件内部已管理效果逻辑
                    return true;
                }).cooldownSeconds(60).build());

        // 小透明技能注册：隐身，冷却20秒，消耗150金币
        RoleSkill.register(ModRoles.GHOST, RoleSkill.skill(
                SRE.id("ghost_invisibility"),
                "skill.noellesroles.ghost.invisibility",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = org.agmas.noellesroles.game.roles.innocence.ghost.GhostPlayerComponent.KEY.get(player);
                    if (comp == null)
                        return false;
                    if (!comp.abilityUnlocked) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.ghost.not_unlocked")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    return comp.useAbility();
                }).cooldownSeconds(20).build());

        // 点灯人技能注册：隐身，消耗1次效果，最多5次
        RoleSkill.register(ModRoles.CANDLE_BEARER, RoleSkill.skill(
                SRE.id("candlebearer_invisibility"),
                "skill.noellesroles.candlebearer.invisibility",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = CandleBearerPlayerComponent.KEY.get(player);
                    if (comp == null)
                        return false;
                    return comp.useAbility();
                }).charges(5).build());

        // 破魔师技能注册：沉默50格外非杀手玩家，冷却130秒
        RoleSkill.register(ModRoles.SPELLBREAKER, RoleSkill.skill(
                SRE.id("spellbreaker_silence"),
                "skill.noellesroles.spellbreaker.silence",
                context -> {
                    ServerPlayer player = context.player();
                    SpellbreakerPlayerComponent.KEY.get(player).useAbility();
                    return true;
                }).cooldownSeconds(130).build());

        // 侍者技能注册：开启灯光，冷却45秒
        RoleSkill.register(ModRoles.ATTENDANT, RoleSkill.skill(
                SRE.id("attendant_light"),
                "skill.noellesroles.attendant.light",
                context -> {
                    ServerPlayer player = context.player();
                    AttendantHandler.openLight(player);
                    return true;
                }).cooldownSeconds(45).build());

        // 守望者技能注册：切换姿态
        RoleSkill.register(ModRoles.WATCHER, RoleSkill.skill(
                SRE.id("watcher_stance"),
                "skill.noellesroles.watcher.stance",
                context -> {
                    ServerPlayer player = context.player();
                    WatcherPlayerComponent.KEY.get(player).toggleStance();
                    return true;
                }).cooldownSeconds(30).build());

        // 方名美铃技能注册：可切换飘浮效果，冷却60秒
        RoleSkill.register(RedHouseRoles.HOAN_MEIRIN, RoleSkill.skill(
                SRE.id("hoan_meirin_levitation"),
                "skill.hoan_meirin.levitation",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.hasEffect(MobEffects.LEVITATION)) {
                        player.removeEffect(MobEffects.LEVITATION);
                        player.displayClientMessage(
                                Component.translatable("hud.hoan_meirin.ability_stop").withStyle(ChatFormatting.AQUA),
                                true);
                        return true;
                    }
                    if (!context.skillReady())
                        return false;
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                            10 * 20, 1, true, false, true));
                    player.displayClientMessage(
                            Component.translatable("hud.hoan_meirin.ability_activated").withStyle(ChatFormatting.GREEN),
                            true);
                    return true;
                }).cooldownSeconds(60).toggleable(true).build());

        // 窃贼技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式
        RoleSkill.register(ModRoles.THIEF,
                RoleSkill.skill(SRE.id("thief_ability"),
                        "skill.noellesroles.thief.ability",
                        context -> {
                            return ThiefPlayerComponent.KEY.get(context.player()).useAbility();
                        }).build(),
                RoleSkill.skill(SRE.id("thief_toggle_mode"),
                        "skill.noellesroles.thief.toggle_mode",
                        context -> {
                            ThiefPlayerComponent.KEY.get(context.player()).toggleMode();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // 会计技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式
        RoleSkill.register(ModRoles.ACCOUNTANT,
                RoleSkill.skill(SRE.id("accountant_ability"),
                        "skill.noellesroles.accountant.ability",
                        context -> {
                            return AccountantPlayerComponent.KEY.get(context.player()).useAbility();
                        }).announceToSelf(false).showOnHud(false).build(),
                RoleSkill.skill(SRE.id("accountant_toggle_mode"),
                        "skill.noellesroles.accountant.toggle_mode",
                        context -> {
                            AccountantPlayerComponent.KEY.get(context.player()).toggleMode();
                            return true;
                        }).shifted(true).announceToSelf(false).build());

        // 炼金术师技能注册：普通按 G 调制药剂，蹲下+ G 切换药剂
        RoleSkill.register(ModRoles.ALCHEMIST,
                RoleSkill.skill(SRE.id("alchemist_craft"),
                        "skill.noellesroles.alchemist.craft",
                        context -> {
                            AlchemistPlayerComponent.KEY.get(context.player()).craftPotion();
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("alchemist_switch_potion"),
                        "skill.noellesroles.alchemist.switch_potion",
                        context -> {
                            AlchemistPlayerComponent.KEY.get(context.player()).switchPotion();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // 女巫技能注册：普通按 G 炼制药水，蹲下+ G 切换药水
        RoleSkill.register(ModRoles.WITCH,
                RoleSkill.skill(SRE.id("witch_craft"),
                        "skill.noellesroles.witch.craft",
                        context -> {
                            org.agmas.noellesroles.game.roles.neutral.witch.WitchPlayerComponent.KEY.get(context.player()).craftPotion();
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("witch_switch_potion"),
                        "skill.noellesroles.witch.switch_potion",
                        context -> {
                            org.agmas.noellesroles.game.roles.neutral.witch.WitchPlayerComponent.KEY.get(context.player()).switchPotion();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 建筑师技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式 ====================
        RoleSkill.register(ModRoles.BUILDER,
                RoleSkill.skill(SRE.id("builder_ability"),
                        "skill.noellesroles.builder.ability",
                        context -> {
                            var comp = org.agmas.noellesroles.component.ModComponents.BUILDER.get(context.player());
                            if (comp.isBuildMode()) {
                                return comp.useBuildAbility();
                            } else {
                                return comp.useDemolishAbility();
                            }
                        }).build(),
                RoleSkill.skill(SRE.id("builder_toggle_mode"),
                        "skill.noellesroles.builder.toggle_mode",
                        context -> {
                            org.agmas.noellesroles.component.ModComponents.BUILDER.get(context.player()).switchMode();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 管家技能注册：普通按 G 放置家具，按 Shift+G 切换家具类型 ====================
        RoleSkill.register(ModRoles.HOUSEKEEPER,
                RoleSkill.skill(SRE.id("housekeeper_ability"),
                        "skill.noellesroles.housekeeper.ability",
                        context -> {
                            var comp = org.agmas.noellesroles.component.ModComponents.HOUSEKEEPER.get(context.player());
                            return comp.placeFurniture();
                        }).build(),
                RoleSkill.skill(SRE.id("housekeeper_cycle_type"),
                        "skill.noellesroles.housekeeper.cycle_type",
                        context -> {
                            org.agmas.noellesroles.component.ModComponents.HOUSEKEEPER.get(context.player()).cycleType();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 探路者技能注册：按 G 放置照明灯 ====================
        RoleSkill.register(ModRoles.PATHFINDER,
                RoleSkill.skill(SRE.id("pathfinder_place_light"),
                        "skill.noellesroles.pathfinder.place_light",
                        context -> {
                            var comp = org.agmas.noellesroles.component.ModComponents.PATHFINDER.get(context.player());
                            return comp.placeLight();
                        }).showOnHud(true).announceToSelf(true).build());

        // ==================== 维修工技能注册：按 G 维护灯光 ====================
        RoleSkill.register(ModRoles.WEIXIUGONG,
                RoleSkill.skill(SRE.id("weixiugong_maintain_light"),
                        "skill.noellesroles.weixiugong.maintain_light",
                        context -> {
                            var comp = org.agmas.noellesroles.component.ModComponents.WEIXIUGONG.get(context.player());
                            return comp.maintainLight();
                        }).showOnHud(true).announceToSelf(true).build());

        // ==================== 信徒技能注册：祷告，随机效果，花费125金币，CD90s ====================
        RoleSkill.register(ModRoles.BELIEVER,
                RoleSkill.skill(SRE.id("believer_pray"),
                        "skill.noellesroles.believer.pray",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) return false;

                            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                            if (shop.balance < 125) {
                                player.displayClientMessage(
                                        Component.translatable("hud.noellesroles.believer.not_enough_money")
                                                .withStyle(ChatFormatting.RED), true);
                                return false;
                            }
                            shop.addToBalance(-125);

                            int roll = player.level().random.nextInt(7);
                            String effectKey;

                            switch (roll) {
                                case 0:
                                    effectKey = "hud.noellesroles.believer.effect.none";
                                    break;
                                case 1: {
                                    effectKey = "hud.noellesroles.believer.effect.blackout";
                                    io.wifi.starrailexpress.cca.SREWorldBlackoutComponent blackout =
                                            io.wifi.starrailexpress.cca.SREWorldBlackoutComponent.KEY.get(player.level());
                                    blackout.blackOutRemainingTicks = Math.max(0, blackout.blackOutRemainingTicks - 7 * 20);
                                    break;
                                }
                                case 2: {
                                    effectKey = "hud.noellesroles.believer.effect.night_vision";
                                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 25 * 20, 0, false, false, true));
                                    break;
                                }
                                case 3: {
                                    effectKey = "hud.noellesroles.believer.effect.shield";
                                    io.wifi.starrailexpress.cca.SREArmorPlayerComponent armor =
                                            io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(player);
                                    armor.giveArmor();
                                    player.getServer().tell(new net.minecraft.server.TickTask(
                                            player.getServer().getTickCount() + 30 * 20,
                                            () -> {
                                                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                                                    armor.removeArmor();
                                                }
                                            }));
                                    break;
                                }
                                case 4: {
                                    effectKey = "hud.noellesroles.believer.effect.blizzard";
                                    org.agmas.noellesroles.scene.BlizzardManager.delayNextBlizzard(45 * 20);
                                    break;
                                }
                                case 5: {
                                    effectKey = "hud.noellesroles.believer.effect.speed";
                                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45 * 20, 0, false, false, true));
                                    break;
                                }
                                case 6: {
                                    effectKey = "hud.noellesroles.believer.effect.glowing";
                                    for (Player p : player.level().players()) {
                                        if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp)) {
                                            sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, 3 * 20, 0, false, false, true));
                                        }
                                    }
                                    break;
                                }
                                default:
                                    effectKey = "hud.noellesroles.believer.effect.none";
                            }

                            player.displayClientMessage(
                                    Component.translatable("hud.noellesroles.believer.pray_complete")
                                            .append(Component.translatable(effectKey))
                                            .withStyle(ChatFormatting.GOLD), true);
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
                            return true;
                        }).cooldownSeconds(90).showOnHud(true).announceToSelf(true).build());

        // ==================== 葬仪技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式 ====================
        RoleSkill.register(ModRoles.MORTICIAN_BODYMAKER,
                RoleSkill.skill(SRE.id("mortician_bodymaker_ability"),
                        "skill.noellesroles.mortician_bodymaker.ability",
                        context -> {
                            return MorticianBodyMakerPlayerComponent.KEY.get(context.player()).useAbility();
                        }).build(),
                RoleSkill.skill(SRE.id("mortician_bodymaker_toggle_mode"),
                        "skill.noellesroles.mortician_bodymaker.toggle_mode",
                        context -> {
                            MorticianBodyMakerPlayerComponent.KEY.get(context.player()).toggleMode();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 设陷者技能注册：普通按 G 使用技能，按技能切换键(Y) 切换陷阱类型 ====================
        RoleSkill.register(ModRoles.TRAPPER,
                RoleSkill.skill(SRE.id("trapper_ability"),
                        "skill.noellesroles.trapper.ability",
                        context -> {
                            return TrapperPlayerComponent.KEY.get(context.player()).tryPlaceTrap();
                        }).build(),
                RoleSkill.skill(SRE.id("trapper_toggle_mode"),
                        "skill.noellesroles.trapper.toggle_mode",
                        context -> {
                            TrapperPlayerComponent.KEY.get(context.player()).switchTrapType();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 模仿者技能注册：普通按 G 使用技能，按技能切换键(Y) 切换槽位 ====================
        RoleSkill.register(ModRoles.IMITATOR,
                RoleSkill.skill(SRE.id("imitator_ability"),
                        "skill.noellesroles.imitator.ability",
                        context -> {
                            var comp = org.agmas.noellesroles.component.ModComponents.IMITATOR.get(context.player());
                            if (context.target() != null) {
                                comp.tryCopyAbility(context.player(), context.target());
                            } else {
                                comp.useActiveAbility(context.player(), null);
                            }
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("imitator_toggle_slot"),
                        "skill.noellesroles.imitator.toggle_slot",
                        context -> {
                            org.agmas.noellesroles.component.ModComponents.IMITATOR.get(context.player()).switchSlot();
                            return true;
                        }).shifted(true).announceToSelf(false).showOnHud(false).build());

        // 幽灵技能注册：可切换隐身效果
        RoleSkill.register(ModRoles.PHANTOM, RoleSkill.skill(
                SRE.id("phantom_invisibility"),
                "skill.noellesroles.phantom.invisibility",
                context -> {
                    ServerPlayer player = context.player();
                    // 疯魔（幽灵幻影）期间强制隐身，禁用技能切换避免冲突
                    if (org.agmas.noellesroles.game.roles.killer.phantom.PhantomFrenzyPlayerComponent.isInFrenzy(player)) {
                        return false;
                    }
                    if (context.skillReady()) {
                        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                                NoellesRolesConfig.HANDLER.instance().phantomInvisibilityDuration * 20,
                                0, true, false, true));
                        return true;
                    } else {
                        var effect = player.getEffect(MobEffects.INVISIBILITY);
                        if (effect != null && effect.getDuration() > 0) {
                            player.removeEffect(MobEffects.INVISIBILITY);
                            player.displayClientMessage(
                                    Component.translatable("tip.phantom.exited").withStyle(ChatFormatting.YELLOW),
                                    true);
                            return true;
                        }
                        return false;
                    }
                }).cooldownSeconds(NoellesRolesConfig.instance().phantomInvisibilityCooldown).toggleable(true).build());

        // 指挥官技能注册：切换杀手/普通广播频道
        RoleSkill.register(ModRoles.COMMANDER, RoleSkill.skill(
                SRE.id("commander_switch_channel"),
                "skill.noellesroles.commander.switch_channel",
                context -> {
                    CommanderHandler.tryActiveAbility(context.player());
                    return true;
                }).build());

        // 炸弹人技能注册：购买炸弹
        RoleSkill.register(ModRoles.BOMBER, RoleSkill.skill(
                SRE.id("bomber_buy_bomb"),
                "skill.noellesroles.bomber.buy_bomb",
                context -> {
                    ModComponents.BOMBER.get(context.player()).buyBomb();
                    return true;
                }).build());

        // 仇杀客技能注册：切换效果开关
        RoleSkill.register(ModRoles.BLOOD_FEUDIST, RoleSkill.skill(
                SRE.id("blood_feudist_toggle"),
                "skill.noellesroles.blood_feudist.toggle",
                context -> {
                    ModComponents.BLOOD_FEUDIST.get(context.player()).toggleEffects();
                    return true;
                }).toggleable(true).build());

        // 操纵师技能注册：切换凝视模式
        RoleSkill.register(ModRoles.MANIPULATOR, RoleSkill.skill(
                SRE.id("manipulator_toggle_gaze"),
                "skill.noellesroles.manipulator.toggle_gaze",
                context -> {
                    boolean active = ManipulatorPlayerComponent.KEY.get(context.player()).toggleGazeMode();
                    context.player().displayClientMessage(
                            Component.translatable(active
                                    ? "message.noellesroles.manipulator.gaze_on"
                                    : "message.noellesroles.manipulator.gaze_off")
                                    .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED),
                            true);
                    return true;
                }).toggleable(true).build());

        // 钟表匠技能注册：削减他人回合时间
        RoleSkill.register(ModRoles.CLOCKMAKER, RoleSkill.skill(
                SRE.id("clockmaker_use_skill"),
                "skill.noellesroles.clockmaker.use_skill",
                context -> {
                    ModComponents.CLOCKMAKER.get(context.player()).useSkill();
                    return true;
                }).build());

        // 超级亡命徒技能注册：使用技能，蹲下+ G 为特殊模式
        RoleSkill.register(SpecialGameModeRoles.SUPER_LOOSE_END,
                RoleSkill.skill(SRE.id("super_loose_end_ability"),
                        "skill.noellesroles.super_loose_end.ability",
                        context -> {
                            SuperLooseEndPlayerComponent.KEY.get(context.player()).useAbility(false);
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("super_loose_end_shift"),
                        "skill.noellesroles.super_loose_end.shift",
                        context -> {
                            SuperLooseEndPlayerComponent.KEY.get(context.player()).useAbility(true);
                            return true;
                        }).shifted(true).build());

        // 布袋鬼鬼术注册：4 个鬼术作为可选槽位（V 切换、G 释放、Sneak+G 开里世界大招）。
        // 冷却/门控由 MaChenXuPlayerComponent 自有逻辑负责（cooldownTicks=0 让引擎不拦截），
        // announceToSelf(false) 由组件自定义提示。槽位顺序须与 MaChenXuPlayerComponent.ART_ORDER 一致。
        RoleSkill.register(ModRoles.MA_CHEN_XU,
                RoleSkill.skill(SRE.id("ma_chen_xu_veil"), "hud.noellesroles.ma_chen_xu.skill.veil",
                                context -> MaChenXuPlayerComponent.KEY.get(context.player()).onGhostArt("veil"))
                        .announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_effigy"), "hud.noellesroles.ma_chen_xu.skill.effigy",
                                context -> MaChenXuPlayerComponent.KEY.get(context.player()).onGhostArt("effigy"))
                        .announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_wail"), "hud.noellesroles.ma_chen_xu.skill.wail",
                                context -> MaChenXuPlayerComponent.KEY.get(context.player()).onGhostArt("wail"))
                        .announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_seize"), "hud.noellesroles.ma_chen_xu.skill.seize",
                                context -> MaChenXuPlayerComponent.KEY.get(context.player()).onGhostArt("seize"))
                        .announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_ultimate"), "hud.noellesroles.ma_chen_xu.skill.ultimate",
                                context -> MaChenXuPlayerComponent.KEY.get(context.player()).usePrayerRain())
                        .shifted(true).announceToSelf(false).build());


        RoleSkill.register(ModRoles.WRAITH_ASSASSIN,
                RoleSkill.skill(SRE.id("wraith_assault"), "skill.noellesroles.wraith_assassin.assault",
                        context -> WraithAssassinPlayerComponent.KEY.get(context.player()).useAssault(context.player()))
                        .cooldownSeconds(4).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("wraith_wail"), "skill.noellesroles.wraith_assassin.wail",
                        context -> WraithAssassinPlayerComponent.KEY.get(context.player()).useWail(context.player()))
                        .cooldownSeconds(50).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("wraith_manifest"), "skill.noellesroles.wraith_assassin.manifest",
                        context -> WraithAssassinPlayerComponent.KEY.get(context.player())
                                .useManifest(context.player()))
                        .cooldownSeconds(110).showOnHud(true).announceToSelf(false).build());

        RoleSkill.register(ModRoles.SALTED_FISH,
                RoleSkill.skill(SaltedFishPlayerComponent.SKILL_ID, "skill.noellesroles.salted_fish.sunbathe",
                        context -> SaltedFishPlayerComponent.KEY.get(context.player()).useSkill(context.player()))
                        .showOnHud(true).announceToSelf(false).build());

        // 出题人不适用于统一的技能注册：其需要不同的触发方式但这个api不兼容。
        // 年兽技能注册：发送红包给目标玩家（客户端选目标）
        RoleSkill.register(ModRoles.NIAN_SHOU, RoleSkill.skill(
                SRE.id("nian_shou_red_packet"),
                "skill.noellesroles.nian_shou.red_packet",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();
                    if (targetUuid == null) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.nianshou.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (!(target instanceof ServerPlayer targetPlayer))
                        return false;
                    NianShouPlayerComponent comp = NianShouPlayerComponent.KEY.get(player);
                    if (comp.getRedPacketCount() <= 0) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.nianshou.no_red_packet")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    comp.useRedPacket();
                    ConfigWorldComponent configWorld = ConfigWorldComponent.KEY.get(targetPlayer.level());
                    configWorld.addRedPacketTimer(targetPlayer.getUUID());
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.nianshou.red_packet_sent", target.getName())
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                    return true;
                }).build());

        // 幸运使者技能注册：保护目标玩家，冷却120秒，消耗200金币
        RoleSkill.register(ModRoles.FORTUNETELLER, RoleSkill.skill(
                SRE.id("fortuneteller_protect"),
                "skill.noellesroles.fortuneteller.protect",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();
                    if (targetUuid == null)
                        return false;
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (target == null)
                        return false;
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 200) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.addToBalance(-200);
                    FortunetellerPlayerComponent.KEY.get(player).protectPlayer(target);
                    return true;
                }).cooldownSeconds(120).build());

        // 十六夜咲夜技能注册：时间停止5秒，冷却240秒
        RoleSkill.register(RedHouseRoles.MAID_SAKUYA, RoleSkill.skill(
                SRE.id("maid_sakuya_timestop"),
                "skill.maid_sakuya.timestop",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.getCooldowns().isOnCooldown(Items.CLOCK))
                        return false;
                    return TimeStopEffect.tryTriggerStart(player, 20 * 5,
                            Component.translatable("title.maid_sakuya.timestopper")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }).cooldownSeconds(240).build());

        // JOJO技能注册：时间停止3秒，冷却240秒
        RoleSkill.register(ModRoles.JOJO, RoleSkill.skill(
                SRE.id("jojo_timestop"),
                "skill.noellesroles.jojo.timestop",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.getCooldowns().isOnCooldown(Items.CLOCK))
                        return false;
                    return TimeStopEffect.tryTriggerStart(player, 20 * 3,
                            Component.translatable("hud.noellesroles.jojo.the_world")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }).cooldownSeconds(240).build());

        // DIO技能注册：时间停止，委托组件
        RoleSkill.register(ModRoles.DIO, RoleSkill.skill(
                SRE.id("dio_timestop"),
                "skill.noellesroles.dio.timestop",
                context -> {
                    DIOPlayerComponent.KEY.get(context.player()).tryActivateTimeStop();
                    return true;
                }).build());

        // 武术家技能注册：心流状态，10s持续，冷却60s
        RoleSkill.register(ModRoles.WUSHUJIA, RoleSkill.skill(
                SRE.id("wushujia_flow"),
                "skill.noellesroles.wushujia.flow",
                context -> {
                    var comp = ModComponents.WUSHUJIA.get(context.player());
                    return comp.activateFlow();
                }).cooldownSeconds(60).build());

        // 暗影技能注册：暗影步（隐身+自动位移+粒子）
        // 次数和回转冷却由 GhostofanyingPlayerComponent 内部管理
        RoleSkill.register(ModRoles.GHOSTOFANYING, RoleSkill.skill(
                SRE.id("ghostofanying_shadow_step"),
                "skill.noellesroles.ghostofanying.shadow_step",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.GHOSTOFANYING.get(player);
                    if (comp == null) return false;
                    return comp.useShadowStep();
                }).toggleable(true).announceToSelf(false).showOnHud(true).build());

        // 特工技能注册：潜行模式（10秒速度I+消脚步+反透视），冷却90秒
        RoleSkill.register(ModRoles.TEGONG, RoleSkill.skill(
                SRE.id("tegong_stealth"),
                "skill.noellesroles.tegong.stealth",
                context -> {
                    var comp = ModComponents.TEGONG.get(context.player());
                    return comp.activateSkill();
                }).cooldownSeconds(90).announceToSelf(true).showOnHud(true).build());

        // 净化者技能注册：花费75金币清空准心对准的玩家身上所有效果，射程6格，冷却30秒
        RoleSkill.register(ModRoles.JINGHUAZHE, RoleSkill.skill(
                SRE.id("jinghuazhe_purify"),
                "skill.noellesroles.jinghuazhe.purify",
                context -> {
                    ServerPlayer player = context.player();
                    if (GameUtils.isPlayerEliminated(player)) return false;
                    UUID targetUuid = context.target();
                    if (targetUuid == null) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.jinghuazhe.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.jinghuazhe.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    double distance = player.distanceTo(target);
                    if (distance > 6.0) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.jinghuazhe.too_far")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 75) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.addToBalance(-150);
                    target.removeAllEffects();
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.jinghuazhe.purified", target.getName().getString())
                                    .withStyle(ChatFormatting.AQUA),
                            true);
                    target.displayClientMessage(
                            Component.translatable("message.noellesroles.jinghuazhe.been_purified")
                                    .withStyle(ChatFormatting.AQUA),
                            true);
                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
                    return true;
                }).cooldownSeconds(5).build());

        // 时空旅者技能注册：花费125金币在原地放置传送门
        RoleSkill.register(ModRoles.RUIKE, RoleSkill.skill(
                SRE.id("ruike_place_portal"),
                "skill.noellesroles.ruike.place_portal",
                context -> {
                    ServerPlayer player = context.player();
                    if (GameUtils.isPlayerEliminated(player)) return false;

                    // 检查金币
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 125) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }

                    // 获取组件
                    var comp = ModComponents.RUIKE.get(player);
                    if (comp == null) return false;

                    // 如果已有2个传送门，销毁最早放置的
                    if (comp.getPortalCount() >= 2) {
                        UUID oldestUuid = comp.portalUuids.get(0);
                        var oldestEntity = player.serverLevel().getEntity(oldestUuid);
                        if (oldestEntity instanceof org.agmas.noellesroles.content.entity.RuikePortalEntity oldPortal) {
                            oldPortal.discard();
                        } else {
                            // 实体已不存在，手动从组件清理
                            comp.removePortal(oldestUuid);
                        }
                    }

                    // 扣除金币
                    shop.addToBalance(-125);

                    // 生成传送门实体
                    var portal = new org.agmas.noellesroles.content.entity.RuikePortalEntity(
                            org.agmas.noellesroles.init.ModEntities.RUIKE_PORTAL,
                            player.level());
                    portal.setPos(player.getX(), player.getY(), player.getZ());
                    portal.setYRot(player.getYRot());
                    portal.setOwnerUUID(player.getUUID());
                    player.serverLevel().addFreshEntity(portal);

                    // 配对逻辑：如果已有1个传送门，新传送门与之配对
                    if (comp.getPortalCount() == 1) {
                        UUID existingPortalUuid = comp.portalUuids.get(0);
                        var existingPortal = player.serverLevel().getEntity(existingPortalUuid);
                        if (existingPortal instanceof org.agmas.noellesroles.content.entity.RuikePortalEntity existing) {
                            // 双向配对
                            existing.setPairUUID(portal.getUUID());
                            portal.setPairUUID(existing.getUUID());
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.ruike.portal_paired")
                                            .withStyle(ChatFormatting.AQUA),
                                    true);
                        }
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.ruike.portal_placed_first")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                                true);
                    }

                    // 添加传送门到组件
                    comp.addPortal(portal.getUUID());

                    // 播放放置音效
                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.0F);

                    return true;
                }).cooldownSeconds(10).build());

        // 梦魇技能注册：打开背包选择玩家施加恐惧
        RoleSkill.register(ModRoles.MENGYAN, RoleSkill.skill(
                SRE.id("mengyan_fear"),
                "skill.noellesroles.mengyan.fear",
                context -> {
                    // 实际逻辑在C2S包处理器中，这里只是标记技能可用
                    // 玩家打开背包后通过选人UI发送C2S包触发
                    return false;
                }).cooldownSeconds(75).showOnHud(false).build());

        // 殉道者技能注册：牺牲复活
        RoleSkill.register(ModRoles.XUNDAOZHE, RoleSkill.skill(
                SRE.id("xundaozhe_revive"),
                "skill.noellesroles.xundaozhe.revive",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.XUNDAOZHE.get(player);
                    return comp.tryStartRevival();
                }).cooldownSeconds(0).build());

        // 雪原猎手技能注册：花费80元获得无距离限制透视8秒，冷却60秒（透视结束后开始）
        RoleSkill.register(ModRoles.SNOW_HUNTER, RoleSkill.skill(
                SRE.id("snow_hunter_vision"),
                "skill.noellesroles.snow_hunter.vision",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.SNOW_HUNTER.get(player);
                    if (comp.skillCooldownTicks > 0) return false;
                    if (comp.isSkillActive()) return false;
                    io.wifi.starrailexpress.cca.SREPlayerShopComponent shop = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < org.agmas.noellesroles.game.roles.killer.snow_hunter.SnowHunterPlayerComponent.SKILL_COST) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds")
                                        .withStyle(net.minecraft.ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.addToBalance(-org.agmas.noellesroles.game.roles.killer.snow_hunter.SnowHunterPlayerComponent.SKILL_COST);
                    comp.activateSkill();
                    return true;
                }).cooldownSeconds(0).build());

        // 慈善家技能注册：花费100金币，使目标玩家金币增加50
        RoleSkill.register(ModRoles.PHILANTHROPIST, RoleSkill.skill(
                SRE.id("philanthropist_donate"),
                "skill.noellesroles.philanthropist.donate",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();
                    if (targetUuid == null) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.philanthropist.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (!(target instanceof ServerPlayer targetPlayer))
                        return false;
                    if (GameUtils.isPlayerEliminated(targetPlayer)) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.philanthropist.target_invalid")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 100) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds_money", 100)
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.addToBalance(-100);
                    SREPlayerShopComponent.KEY.get(targetPlayer).addToBalance(50);
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.philanthropist.donated",
                                            targetPlayer.getName().getString())
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                    targetPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.philanthropist.received",
                                            player.getName().getString())
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                    return true;
                }).cooldownSeconds(30).build());

        // 敛财者技能注册：花费 100 金币恢复 100% 理智值，冷却 90 秒
        RoleSkill.register(ModRoles.ILIKEMONEY, RoleSkill.skill(
                SRE.id("ilikemoney_restore_mood"),
                "skill.noellesroles.ilikemoney.restore_mood",
                context -> {
                    ServerPlayer player = context.player();
                    if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;

                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 100) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds_money", 100)
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }

                    SREPlayerMoodComponent moodComp = SREPlayerMoodComponent.KEY.get(player);
                    if (moodComp.getMood() >= 1.0f) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.ilikemoney.mood_full")
                                        .withStyle(ChatFormatting.YELLOW),
                                true);
                        return false;
                    }

                    shop.addToBalance(-100);
                    moodComp.setMood(1.0f);
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.ilikemoney.mood_restored")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    return true;
                }).cooldownSeconds(90).showOnHud(true).announceToSelf(false).build());

        // 售衣员技能注册：手持皮革护甲时对准玩家按G键，将对应物品给予目标玩家，无冷却
        RoleSkill.register(ModRoles.SHOUYIYUAN, RoleSkill.skill(
                SRE.id("shouyiyuan_give_armor"),
                "skill.noellesroles.shouyiyuan.give_armor",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator() || GameUtils.isPlayerEliminated(player)) {
                        return false;
                    }
                    // 获取目标玩家
                    java.util.UUID targetUuid = context.target();
                    if (targetUuid == null) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.shouyiyuan.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (!(target instanceof ServerPlayer targetPlayer) || GameUtils.isPlayerEliminated(targetPlayer)) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.shouyiyuan.target_invalid")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    // 检查手持物品是否为皮革护甲
                    net.minecraft.world.item.ItemStack held = player.getMainHandItem();
                    if (held.getItem() != Items.LEATHER_CHESTPLATE
                            && held.getItem() != Items.LEATHER_LEGGINGS
                            && held.getItem() != Items.LEATHER_BOOTS) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.shouyiyuan.not_leather")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    // 给予目标玩家一份该物品
                    net.minecraft.world.item.ItemStack gift = held.copy();
                    gift.setCount(1);
                    if (!targetPlayer.getInventory().add(gift)) {
                        // 物品栏满了就丢在地上
                        targetPlayer.drop(gift, false);
                    }
                    // 获取物品名称（在减少数量前）
                    String itemName = held.getItem().getDescription().getString();
                    // 减少手持物品数量
                    held.shrink(1);
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.shouyiyuan.gave",
                                            itemName, targetPlayer.getName().getString())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    targetPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.shouyiyuan.received",
                                            itemName, player.getName().getString())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    return true;
                }).cooldownSeconds(0).build());

        // 天气预报员技能注册：花费125金币得知下一次普通暴风雪到来的时间
        RoleSkill.register(ModRoles.TIANQIYUBAOYUAN, RoleSkill.skill(
                SRE.id("tianqiyubaoyuan_forecast"),
                "skill.noellesroles.tianqiyubaoyuan.forecast",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
                        return false;
                    }
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 125) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds_money", 125)
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.addToBalance(-125);
                    int ticks = org.agmas.noellesroles.scene.BlizzardManager.getNextBlizzardIn();
                    int seconds = (ticks + 19) / 20;
                    int minutes = seconds / 60;
                    int remainSeconds = seconds % 60;
                    if (minutes > 0) {
                        String paddedSec = String.format("%02d", remainSeconds);
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.tianqiyubaoyuan.forecast_result_minutes", minutes, paddedSec)
                                        .withStyle(ChatFormatting.AQUA),
                                false);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.tianqiyubaoyuan.forecast_result_seconds", seconds)
                                        .withStyle(ChatFormatting.AQUA),
                                false);
                    }
                    return true;
                }).cooldownSeconds(30).showOnHud(true).announceToSelf(false).build());

        // ==================== 恶灵技能注册：相位转移（1.5s前摇+8s空间状态+1.5s后摇，CD 45s） ====================
        RoleSkill.register(ModRoles.ELING_APEX, RoleSkill.skill(
                SRE.id("eling_apex_phase_shift"),
                "skill.noellesroles.eling_apex.phase_shift",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.ELING_APEX.get(player);
                    if (comp == null) return false;
                    return comp.usePhaseShift();
                }).cooldownSeconds(45).toggleable(true).showOnHud(true).announceToSelf(false).build());

        // ==================== 幻魔者技能注册：地刺(G)，CD30s；恼鬼召唤通过背包界面点选玩家触发 ====================
        RoleSkill.register(ModRoles.HUANMOZHE,
                RoleSkill.skill(SRE.id("huanmozhe_spike"),
                        "skill.noellesroles.huanmozhe.spike",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) return false;
                            var comp = ModComponents.HUANMOZHE.get(player);
                            if (comp == null) return false;
                            // 检查技能存储
                            if (comp.skillStorage <= 0) {
                                player.displayClientMessage(
                                        Component.translatable("message.noellesroles.huanmozhe.no_storage")
                                                .withStyle(ChatFormatting.RED), true);
                                return false;
                            }
                            boolean success = comp.startSpikeCast(player);
                            if (success) {
                                comp.skillStorage--;
                                comp.sync();
                            }
                            return success;
                        }).cooldownSeconds(30).showOnHud(true).announceToSelf(true).build());

        // ==================== 幻术师技能注册：三个技能通过V键切换，G键释放，共用CD 30s ====================
        RoleSkill.register(ModRoles.HUANSHUSHI,
                RoleSkill.skill(SRE.id("huanshushi_skill1"),
                        "skill.noellesroles.huanshushi.skill1",
                        context -> {
                            ServerPlayer player = context.player();
                            var comp = ModComponents.HUANSHUSHI.get(player);
                            if (comp == null) return false;
                            return comp.useSkill1();
                        }).showOnHud(true).announceToSelf(true).build(),
                RoleSkill.skill(SRE.id("huanshushi_skill2"),
                        "skill.noellesroles.huanshushi.skill2",
                        context -> {
                            ServerPlayer player = context.player();
                            var comp = ModComponents.HUANSHUSHI.get(player);
                            if (comp == null) return false;
                            return comp.useSkill2();
                        }).showOnHud(true).announceToSelf(true).build(),
                RoleSkill.skill(SRE.id("huanshushi_skill3"),
                        "skill.noellesroles.huanshushi.skill3",
                        context -> {
                            ServerPlayer player = context.player();
                            var comp = ModComponents.HUANSHUSHI.get(player);
                            if (comp == null) return false;
                            return comp.useSkill3();
                        }).showOnHud(true).announceToSelf(true).build());


    }

    /**
     * 将坠木和皮革嘎的玩家加入同一个 scoreboard team，
     * 并开启 seeFriendlyInvisibles，确保坠木能透视到隐身的皮革嘎的。
     */
    private static void joinZhuimuPigeTeam(ServerPlayer player) {
        try {
            var scoreboard = player.serverLevel().getScoreboard();
            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam("zhuimu_pige");
            if (team == null) {
                team = scoreboard.addPlayerTeam("zhuimu_pige");
                team.setSeeFriendlyInvisibles(true);
            }
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        } catch (Exception e) {
            org.agmas.noellesroles.Noellesroles.LOGGER.error("Failed to join zhuimu_pige team", e);
        }
    }

}
