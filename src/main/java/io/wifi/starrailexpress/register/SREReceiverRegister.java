package io.wifi.starrailexpress.register;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.original.*;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.game.modes.fourthroom.network.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * 服务端网络包接收器注册，从 {@link SRE#onInitialize()} 中按类别剥离归一化而来。
 */
public class SREReceiverRegister {

    public static void registerGlobalReceivers() {
        SceneAssetNetwork.registerServerReceivers();

        // 表情系统服务端接收器与 tick 检测
        io.wifi.starrailexpress.emote.EmoteServerManager.register();

        // 紧急会议系统：服务端核心 + 发言/跳票请求
        net.exmo.sre.meeting.MeetingManager.register();
        net.exmo.sre.meeting.MeetingReportServerHandler.register();
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanMeetingHandler.register();
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.meeting.network.MeetingSpeakC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> net.exmo.sre.meeting.MeetingManager.setManualSpeaking(context.player(),
                                payload.speaking())));
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.meeting.network.MeetingSkipC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> net.exmo.sre.meeting.MeetingManager.setSkipVote(context.player(),
                                payload.skip())));

        UpdateSkinSelectedPayload.registerReceiver();
        UpdateNameTagSelectedPayload.registerReceiver();
        // 服务端处理客户端投票包
        ServerPlayNetworking.registerGlobalReceiver(VoteCastC2SPacket.TYPE, (packet, context) -> {
            VoteManager.handleVoteCast(context.player(), packet.optionIndices());
        });
        ServerPlayNetworking.registerGlobalReceiver(
                io.wifi.starrailexpress.network.packet.WaypointDeleteC2SPayload.ID,
                new io.wifi.starrailexpress.network.packet.WaypointDeleteC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(KnifeStabPayload.ID, new KnifeStabPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(ModVersionPacket.ID, new ModVersionPacket.Receiver());
        // 全局战绩 / 回放查询请求
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.record.network.RecordListRequestC2SPayload.ID,
                (payload, context) -> net.exmo.sre.record.MatchRecordService.openListWindow(context.player(),
                        payload.offset(), payload.limit()));
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.record.network.RecordReplayRequestC2SPayload.ID,
                (payload, context) -> net.exmo.sre.record.MatchRecordService.openReplayFor(context.player(),
                        payload.matchId()));
        ServerPlayNetworking.registerGlobalReceiver(GunShootPayload.ID, new GunShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(SniperShootPayload.TYPE, new SniperShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(
                io.wifi.starrailexpress.network.original.TrainDummySpawnC2SPayload.ID,
                new io.wifi.starrailexpress.network.original.TrainDummySpawnC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(StoreBuyPayload.ID, new StoreBuyPayload.Receiver());
        // 商店价格同步：客户端缓存未命中时请求完整价格表 / Shop price sync: full-table request on cache miss
        ServerPlayNetworking.registerGlobalReceiver(
                io.wifi.starrailexpress.shop.network.ShopPriceRequestC2SPayload.TYPE,
                (payload, context) -> context.server().execute(() -> io.wifi.starrailexpress.shop.ShopPriceSyncServer
                        .handleRequest(context.player(), payload.hash())));
        ServerPlayNetworking.registerGlobalReceiver(NoteEditPayload.ID, new NoteEditPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(RequestOpenClueArchivePayload.ID,
                new RequestOpenClueArchivePayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                (payload, context) -> {
                    io.wifi.starrailexpress.network.VoteForMapPayload.Handler.handle(payload, context.player());
                });

        // 实体交互方块服务端网络处理
        EntityInteractionBlockServerNetwork.register();
        // 展示方块（文本 / 方块 / 物品 / 实体展示）服务端网络处理
        io.wifi.starrailexpress.network.DisplayBlockServerNetwork.register();
        MinigameQuestServerNetwork.register();
        TicketOfficeServerNetwork.register();
        EffectGeneratorServerNetwork.register();
        // 画板服务端网络处理
        DrawingBoardServerNetwork.register();
        ServerPlayNetworking.registerGlobalReceiver(SecurityCameraExitRequestPayload.ID,
                new SecurityCameraExitRequestPayload.ServerReceiver());
        ServerPlayNetworking.registerGlobalReceiver(JoinSpecGroupPayload.ID, (payload, context) -> {
            joinVoice(payload, context);

        });
        ServerPlayNetworking.registerGlobalReceiver(NunchuckHitPayload.ID, new NunchuckHitPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(CardPlayPayload.ID, new CardPlayPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(BuyFourthRoomItemPayload.ID,
                new BuyFourthRoomItemPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(RevealIdentityPayload.ID, new RevealIdentityPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(CompleteFourthRoomTaskPayload.ID,
                new CompleteFourthRoomTaskPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(EndTurnPayload.ID, new EndTurnPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(UseAssassinationItemPayload.ID,
                new UseAssassinationItemPayload.Receiver());

        // Role Rotation receivers
        io.wifi.starrailexpress.game.modes.funny.SRERoleRotationGameMode.registerServerPacketRecievers();

        // 音乐盒 C2S 接收器
        io.wifi.starrailexpress.content.musicbox.network.SelectMusicBoxC2SPayload.registerReceiver();
        io.wifi.starrailexpress.content.musicbox.network.DrawMusicBoxLotteryC2SPayload.registerReceiver();

        // CS2 开箱系统 C2S 接收器
        org.agmas.noellesroles.cs2.network.CS2ServerReceiverRegister.registerAll();

        // 称号系统 C2S 接收器（装备 / 卸下）
        io.wifi.starrailexpress.content.title.TitleNetwork.registerReceivers();

        // 职业轮换系统：管理员编辑名单
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.RoleRosterUpdatePayload.ID,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        if (!player.hasPermissions(2)) {
                            player.displayClientMessage(Component.translatable("sre.command.permission_denied").withStyle(ChatFormatting.RED), false);
                            return;
                        }
                        if(!SREConfig.instance().enableRoster){
                            player.displayClientMessage(Component.translatable("sre.command.not_enabled").withStyle(ChatFormatting.RED), false);
                            return;
                        }
                        switch (payload.action()) {
                            case "set" -> io.wifi.starrailexpress.roster.RoleRosterManager.setFromJson(payload.json());
                            case "enable" -> io.wifi.starrailexpress.roster.RoleRosterManager.setEnabled(true);
                            case "disable" -> io.wifi.starrailexpress.roster.RoleRosterManager.setEnabled(false);
                            case "clear" -> io.wifi.starrailexpress.roster.RoleRosterManager.clear();
                            default -> {
                            }
                        }
                    });
                });
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.MapIntroRequestPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> sendMapIntro(context.player(), payload.includeAll())));

        // 地图轮换：管理员启用/停用地图
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.MapRotationTogglePayload.ID,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        if (!player.hasPermissions(2)) {
                            player.displayClientMessage(Component.translatable("sre.command.permission_denied")
                                    .withStyle(ChatFormatting.RED), false);
                            return;
                        }
                        io.wifi.starrailexpress.game.data.ServerMapConfig mapConfig =
                                io.wifi.starrailexpress.game.data.ServerMapConfig.getInstance(player.server);
                        io.wifi.starrailexpress.game.data.MapConfig.MapEntry entry =
                                mapConfig.getMapById(payload.mapId());
                        if (entry == null) {
                            return;
                        }
                        entry.canSelect = payload.enabled();
                        mapConfig.saveConfig(player.server);
                        // 同步更新服务端展示缓存里的这一张图，避免同一局内界面显示过期
                        io.wifi.starrailexpress.network.MapIntroData.updateCanSelect(payload.mapId(),
                                payload.enabled());
                        broadcastMapRotation(player.server);
                    });
                });

        // 物品栏垃圾桶：删除光标上拿着的物品
        ServerPlayNetworking.registerGlobalReceiver(
                io.wifi.starrailexpress.network.DeleteCarriedItemC2SPayload.ID,
                (payload, context) -> context.server()
                        .execute(() -> io.wifi.starrailexpress.network.DeleteCarriedItemC2SPayload
                                .handle(context.player())));

        // Mailbox receivers
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.Receiver());

        // Chat Dialogue advance handler
        ServerPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.client.chat.ChatDialogueAdvancePayload.ID, (payload, context) -> {
                    var mgr = net.exmo.sre.client.chat.ChatDialogueManager
                            .getInstance(context.player().getServer());
                    var data = mgr.get(payload.dialogueId());
                    if (data == null)
                        return;
                    int idx = payload.lineIndex();
                    if (idx < 0 || idx >= data.lines.size())
                        return;
                    var line = data.lines.get(idx);

                    if (payload.choiceIndex() >= 0) {
                        if (!line.hasChoices())
                            return;
                        int choiceIndex = payload.choiceIndex();
                        if (choiceIndex < 0 || choiceIndex >= line.choices.size())
                            return;

                        var choice = line.choices.get(choiceIndex);
                        executeDialogueCommand(context, choice.command, choice.runsOnServer());

                        if (choice.opensDialogue()) {
                            var nextDialogue = mgr.get(choice.nextDialogue);
                            if (nextDialogue != null) {
                                net.exmo.sre.client.chat.OpenChatDialoguePayload.sendToPlayer(
                                        context.player(), nextDialogue, payload.focusEntityId());
                            } else {
                                SRE.LOGGER.warn("[SRE-Chat] Missing next dialogue '{}' from '{}' line {} choice {}",
                                        choice.nextDialogue, payload.dialogueId(), idx, choiceIndex);
                            }
                        }
                        return;
                    }

                    executeDialogueCommand(context, line.command, line.runsOnServer());
                });
    }

    private static void sendMapIntro(ServerPlayer player, boolean includeAllRequested) {
        // 只有管理员（权限 ≥2）能请求「显示全部地图」；普通请求只拿投票配置里的地图
        boolean includeAll = includeAllRequested && player.hasPermissions(2);
        java.util.List<io.wifi.starrailexpress.network.MapDisplayInfo> maps =
                io.wifi.starrailexpress.network.MapIntroData.get(player.server, includeAll);
        for (io.wifi.starrailexpress.network.MapIntroSyncPayload payload :
                io.wifi.starrailexpress.network.MapIntroSyncPayload.chunk(maps)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** 把全部地图的启用状态广播给所有在线玩家（比重发 MapIntroSyncPayload 便宜得多）。 */
    private static void broadcastMapRotation(net.minecraft.server.MinecraftServer server) {
        io.wifi.starrailexpress.game.data.ServerMapConfig mapConfig =
                io.wifi.starrailexpress.game.data.ServerMapConfig.getInstance(server);
        if (mapConfig.getMaps() == null) {
            return;
        }
        java.util.List<io.wifi.starrailexpress.network.MapRotationSyncPayload.Entry> entries = new ArrayList<>();
        for (io.wifi.starrailexpress.game.data.MapConfig.MapEntry entry : mapConfig.getMaps()) {
            if (entry == null || entry.id == null || entry.id.isBlank()) {
                continue;
            }
            entries.add(new io.wifi.starrailexpress.network.MapRotationSyncPayload.Entry(entry.id, entry.canSelect));
        }
        io.wifi.starrailexpress.network.MapRotationSyncPayload payload =
                new io.wifi.starrailexpress.network.MapRotationSyncPayload(entries);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(online, payload);
        }
    }

    private static void executeDialogueCommand(ServerPlayNetworking.Context context, String command,
            boolean runOnServer) {
        if (!runOnServer || command == null || command.isBlank())
            return;
        context.player().getServer().getCommands()
                .performPrefixedCommand(
                        context.player().createCommandSourceStack()
                                .withPermission(2)
                                .withSuppressedOutput(),
                        command);
    }

    private static void joinVoice(JoinSpecGroupPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sp = context.player();
        boolean isJoin = payload.isJoin();
        if (isJoin) {
            if (GameUtils.isPlayerSpectator(sp)) {
                TrainVoicePlugin.addPlayer(sp.getUUID());
            }
        } else {
            TrainVoicePlugin.resetPlayer(sp.getUUID());
        }
    }
}
