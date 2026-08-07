package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.*;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PlayerVolumeComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.item.RadioItem;
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.commander.CommanderHandler;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class NoellesrolesVoiceChatPlugin implements VoicechatPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger("NoellesrolesVoiceChat");
  private static VoicechatServerApi SERVER_API;

  @Override
  public String getPluginId() {
    return Noellesroles.MOD_ID;
  }

  @Override
  public void initialize(VoicechatApi api) {
    VoicechatPlugin.super.initialize(api);
  }

  public static boolean isAlive(Player player) {
    return GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player);
  }

  public static boolean shouldBanVoice(VoicechatConnection senderConnection, VoicechatConnection receiverConnection) {
    if (senderConnection == null || receiverConnection == null)
      return false;
    if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
      return false;
    if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
      return false;

    // 鹈鹕语音隔离：被吞噬的玩家只能与鹈鹕和肚子里的其他玩家语音
    if (PelicanManager.shouldCancelVoice(senderPlayer.getUUID(), receiverPlayer.getUUID())) {
      return true;
    }
    // 亡命徒期间活人玩家不可听话
    if (RefugeeComponent.KEY.get(senderPlayer.level()).isAnyRevivals) {
      if (ModRoles.isLooseEndVariant(RoleUtils.getPlayerRole(senderPlayer))
          && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(senderPlayer)) {
        return true;
      }
      if (ModRoles.isLooseEndVariant(RoleUtils.getPlayerRole(receiverPlayer))
          && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(receiverPlayer)) {
        return true;
      }
    }
    if (senderPlayer.getEffect(ModEffects.TIME_STOP) != null) {
      if (!TimeStopEffect.canMovePlayers.contains(senderPlayer.getUUID())) {
        return true;
      }
    }
    if (receiverPlayer.getEffect(ModEffects.TIME_STOP) != null) {
      if (!TimeStopEffect.canMovePlayers.contains(receiverPlayer.getUUID())) {
        return true;
      }
    }
    if (receiverPlayer.hasEffect(ModEffects.PLAYER_ISOLATION) || senderPlayer.hasEffect(ModEffects.PLAYER_ISOLATION)) {
      return true;
    }
    if (SREGameWorldComponent.KEY.get(senderPlayer.level()).isRole(senderPlayer, ModRoles.WRAITH_ASSASSIN)) {
      var wraith = ModComponents.WRAITH_ASSASSIN.get(senderPlayer);
      if (!wraith.isManifested()
          && !org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinPlayerComponent
              .canPerceiveWraith(receiverPlayer)) {
        return true;
      }
    }
    var deathPenalty = ModComponents.DEATH_PENALTY.get(receiverPlayer);
    if (deathPenalty.hasPenalty()) {
      // 如果任一玩家被鹈鹕吞噬（肚内/stashed），不要因为死亡惩罚把他们直接拉到死亡语音频道
      if (PelicanManager.isStashed(senderPlayer) || PelicanManager.isStashed(receiverPlayer)) {
        return false;
      }
      if (deathPenalty.limitCameraUUID != null) {
        return true;
      }
      if (deathPenalty.limitPos != null) {
        return true;
      }
    }

    var pvc = PlayerVolumeComponent.KEY.get(receiverPlayer);
    if (receiverPlayer.isSpectator() && pvc.vtMode) {
      if (senderPlayer.isSpectator()
          && SREGameWorldComponent.KEY.get(senderPlayer.level()).isRunning()) {
        return true;
      }
    }
    return false;
  }

  public void soundEvent_Static(StaticSoundPacketEvent event) {
    try {
      VoicechatConnection senderConnection = event.getSenderConnection();
      VoicechatConnection receiverConnection = event.getReceiverConnection();
      if (shouldBanVoice(senderConnection, receiverConnection)) {
        event.cancel();
      }
    } catch (Exception e) {
      LOGGER.warn("Error in Noellesroles soundEvent_Static", e);
    }
  }

  public void soundEvent_Entity(EntitySoundPacketEvent event) {
    try {
      VoicechatConnection senderConnection = event.getSenderConnection();
      VoicechatConnection receiverConnection = event.getReceiverConnection();
      if (shouldBanVoice(senderConnection, receiverConnection)) {
        event.cancel();
      }
    } catch (Exception e) {
      LOGGER.warn("Error in Noellesroles soundEvent_Entity", e);
    }
  }

  public void soundEvent_Locational(LocationalSoundPacketEvent event) {
    try {
      VoicechatConnection senderConnection = event.getSenderConnection();
      VoicechatConnection receiverConnection = event.getReceiverConnection();
      if (shouldBanVoice(senderConnection, receiverConnection)) {
        event.cancel();
      }
    } catch (Exception e) {
      LOGGER.warn("Error in Noellesroles soundEvent_Locational", e);
    }
  }

  public void paranoidEvent(MicrophonePacketEvent event) {
    try {
      paranoidEventInternal(event);
    } catch (Exception e) {
      LOGGER.warn("Error in Noellesroles voice paranoidEvent", e);
    }
  }

  private void paranoidEventInternal(MicrophonePacketEvent event) {
    VoicechatServerApi api = event.getVoicechat();
    var connection = event.getSenderConnection();
    if (connection != null && connection.isInstalled() && connection.isConnected()) {
      var vcplayer = connection.getPlayer();
      if (vcplayer != null) {
        var vctplayer = vcplayer.getPlayer();
        if (vctplayer instanceof ServerPlayer player) {
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent != null) {
              // 检查沉默语音效果
              if (player.hasEffect(ModEffects.VOICE_SILENCE)) {
              event.cancel();
              return;
            }
            if (gameWorldComponent.getGameStatus().equals(GameStatus.STOPPING)
                || gameWorldComponent.getGameStatus().equals(GameStatus.STARTING)) {
              event.cancel();
              return;
            }
            
            // === 狼人杀语音路由 ===
            if (org.agmas.noellesroles.game.modes.werewolf.WerewolfGameState.isActive(player.serverLevel())) {
              var wwState = org.agmas.noellesroles.game.modes.werewolf.WerewolfGameState.get(player.serverLevel());
              var wwComp = ModComponents.WEREWOLF.get(player);
              
              // 死亡玩家不能说话（例外：被票出玩家在遗言阶段可以说遗言，显式路由给所有存活玩家）
              if (!wwComp.alive) {
                boolean lastWordsException = wwState.phase == org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase.DAY_LAST_WORDS
                        && player.getUUID().equals(wwState.votedOutPlayer);
                if (lastWordsException) {
                  // 显式路由：旁观者默认语音可能被限制，强制发送给所有存活玩家
                  event.cancel();
                  for (UUID aliveUuid : wwState.getAlivePlayers(player.serverLevel())) {
                    ServerPlayer alivePlayer = player.serverLevel().getServer().getPlayerList().getPlayer(aliveUuid);
                    if (alivePlayer != null) {
                      VoicechatConnection con = api.getConnectionOf(aliveUuid);
                      if (con != null && con.isInstalled() && con.isConnected()) {
                        api.sendLocationalSoundPacketTo(con, event.getPacket()
                            .locationalSoundPacketBuilder()
                            .position(api.createPosition(alivePlayer.getX(), alivePlayer.getY(), alivePlayer.getZ()))
                            .distance((float) api.getVoiceChatDistance())
                            .build());
                      }
                    }
                  }
                  return;
                }
                event.cancel();
                return;
              }
              
              // 轮流发言阶段：仅当前发言者可以说话
              if (wwState.phase == org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase.DAY_SPEECH) {
                if (wwState.currentActor == null || !player.getUUID().equals(wwState.currentActor)) {
                  event.cancel();
                  return;
                }
                // 发言者正常广播
              }
              
              // 夜晚狼方阶段：狼方语音只路由给狼方
              if (wwState.phase == org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase.NIGHT_WOLVES) {
                if (wwComp.isWolf()) {
                  // 狼方玩家：只发送给其他狼方
                  event.cancel();
                  var wolves = wwState.getAlivePlayersByFaction(player.serverLevel(), 
                          org.agmas.noellesroles.game.modes.werewolf.WerewolfRoleDef.Faction.WOLF);
                  for (UUID wolfUuid : wolves) {
                    if (!wolfUuid.equals(player.getUUID())) {
                      ServerPlayer wolf = player.serverLevel().getServer().getPlayerList().getPlayer(wolfUuid);
                      if (wolf != null) {
                        VoicechatConnection con = api.getConnectionOf(wolfUuid);
                        if (con != null && con.isInstalled() && con.isConnected()) {
                          api.sendLocationalSoundPacketTo(con, event.getPacket()
                              .locationalSoundPacketBuilder()
                              .position(api.createPosition(wolf.getX(), wolf.getY(), wolf.getZ()))
                              .distance((float) api.getVoiceChatDistance())
                              .build());
                        }
                      }
                    }
                  }
                  return;
                } else {
                  // 非狼方玩家夜晚不能说话
                  event.cancel();
                  return;
                }
              }
              
              // 夜晚其他阶段：所有人不能说话
              if (wwState.phase.isNight()) {
                event.cancel();
                return;
              }
            }
            
            // 静语者疯魔期间：对说话的玩家施加缓慢惩罚（语音监听）
            org.agmas.noellesroles.game.roles.killer.silencer.SilencerFrenzyPlayerComponent.onPlayerSpeak(player);
            // 如果发送者被鹈鹕吞噬，单独处理路由：只转发给鹈鹕和肚内玩家，避免默认逻辑忽略旁观者
            if (PelicanManager.isStashed(player)) {
              var bellyReceivers = PelicanManager.getBellyReceivers(player.getUUID());
              if (!bellyReceivers.isEmpty()) {
                // 取消默认广播，仅转发给肚内接收者（包括鹈鹕）
                event.cancel();
                for (UUID rUuid : bellyReceivers) {
                  ServerPlayer rp = player.serverLevel().getServer().getPlayerList().getPlayer(rUuid);
                  if (rp != null) {
                    VoicechatConnection con = api.getConnectionOf(rp.getUUID());
                    if (con != null && con.isInstalled() && con.isConnected()) {
                      api.sendLocationalSoundPacketTo(con, event.getPacket()
                          .locationalSoundPacketBuilder()
                          .position(api.createPosition(rp.getX(), rp.getY(), rp.getZ()))
                          .distance((float) api.getVoiceChatDistance())
                          .build());
                    }
                  }
                }
                return;
              }
            }
            if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
              float voiceRangeMultiplier = ModEffects.getVoiceRangeMultiplier(player);
              if (voiceRangeMultiplier > 1.0f) {
                event.cancel();
                double maxDistance = api.getVoiceChatDistance() * voiceRangeMultiplier;
                var players = player.level().players();
                if (players == null) {
                  return;
                }
                players.forEach((p) -> {
                  if (p.getUUID() != player.getUUID() && player.distanceTo(p) <= maxDistance) {
                    VoicechatConnection con = api.getConnectionOf(p.getUUID());
                    if (con != null && con.isInstalled() && con.isConnected()) {
                      api.sendLocationalSoundPacketTo(con, event.getPacket()
                          .locationalSoundPacketBuilder()
                          .position(api.createPosition(player.getX(), player.getY(), player.getZ()))
                          .distance((float) maxDistance)
                          .build());
                    }
                  }
                });
                return;
              }
              if (gameWorldComponent.isRole(player, ModRoles.NOISEMAKER)) {
                event.cancel();
                var players = player.level().players();
                if (players == null) {
                  return;
                }
                players.forEach((p) -> {
                  if (p.getUUID() != player.getUUID()) {
                    double rangeMultiplier = 2;
                    if (player.hasEffect(MobEffects.LUCK)) {
                      rangeMultiplier = 8;
                    }
                    if (player.distanceTo(p) <= api.getVoiceChatDistance() * rangeMultiplier) {
                      VoicechatConnection con = api.getConnectionOf(p.getUUID());
                      if (con != null && con.isInstalled() && con.isConnected()) {
                        api.sendLocationalSoundPacketTo(con, event.getPacket()
                            .locationalSoundPacketBuilder()
                            .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                            .distance((float) api.getVoiceChatDistance())
                            .build());
                      }
                    }
                  }
                });
              } else {
                CommanderHandler.vcparanoidEvent(gameWorldComponent, player, event);
                if (event.isCancelled()) {
                  return;
                }
                RadioItem.vcparanoidEvent(gameWorldComponent, player, event);
                if (event.isCancelled()) {
                  return;
                }
              }
              // 鹈鹕语音频道：鹈鹕同时听内外，肚内玩家只能听鹈鹕和肚内（参考对讲机实现）
              {
                var bellyReceivers = PelicanManager.getBellyReceivers(player.getUUID());
                if (!bellyReceivers.isEmpty()) {
                  boolean isStashed = PelicanManager.isStashed(player);
                  // 肚内玩家：取消默认语音，只转发给鹈鹕和肚内玩家
                  if (isStashed) {
                    event.cancel();
                  }
                  // 鹈鹕或肚内玩家：转发语音给肚内接收者（不排除旁观者，肚内玩家本身就是旁观者）
                  for (UUID rUuid : bellyReceivers) {
                    ServerPlayer rp = player.serverLevel().getServer().getPlayerList().getPlayer(rUuid);
                    if (rp != null) {
                      VoicechatConnection con = api.getConnectionOf(rp.getUUID());
                      if (con != null && con.isInstalled() && con.isConnected()) {
                        api.sendLocationalSoundPacketTo(con, event.getPacket()
                            .locationalSoundPacketBuilder()
                            .position(api.createPosition(rp.getX(), rp.getY(), rp.getZ()))
                            .distance((float) api.getVoiceChatDistance())
                            .build());
                      }
                    }
                  }
                  if (isStashed) {
                    return;
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * 获取嬉命人变装时的语音音调，1.0F为正常
   */
  public static float getEmbalmerVoicePitch(Player player) {
    if (player == null)
      return 1.0F;
    return EmbalmerPlayerComponent.getVoicePitch(player);
  }

  /**
   * 鹈鹕吞噬时调用 - 将被吞玩家移出任何语音组，防止被自动拉入死者频道
   */
  public static void onPelicanStash(UUID targetId, UUID pelicanId) {
    if (SERVER_API == null)
      return;
    VoicechatConnection con = SERVER_API.getConnectionOf(targetId);
    if (con != null) {
      con.setGroup(null);
    }
  }

  /**
   * 鹈鹕释放时调用 - 恢复语音分组到默认
   */
  public static void onPelicanRelease(UUID targetId) {
    if (SERVER_API == null)
      return;
    VoicechatConnection con = SERVER_API.getConnectionOf(targetId);
    if (con != null) {
      con.setGroup(null);
    }
  }

  @Override
  public void registerEvents(EventRegistration registration) {
    registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
      SERVER_API = event.getVoicechat();
    });
    registration.registerEvent(MicrophonePacketEvent.class, this::paranoidEvent);

    registration.registerEvent(LocationalSoundPacketEvent.class, this::soundEvent_Locational);
    registration.registerEvent(StaticSoundPacketEvent.class, this::soundEvent_Static);
    registration.registerEvent(EntitySoundPacketEvent.class, this::soundEvent_Entity);
    VoicechatPlugin.super.registerEvents(registration);
  }
}
