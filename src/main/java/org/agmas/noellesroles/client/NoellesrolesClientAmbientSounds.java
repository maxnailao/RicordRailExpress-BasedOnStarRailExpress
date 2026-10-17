package org.agmas.noellesroles.client;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;

public class NoellesrolesClientAmbientSounds {

  public static void register() {
    // 阿蒙终幕「阿蒙时刻」的音乐改用显式播放/停止（见 ClientAmonState 的
    // startFinaleSound / stopFinaleSound）。
    //
    // 原来这里用 AmbienceUtil 的循环环境音，但 ratatouille 的
    // BackgroundAmbience.tryStarting 在谓词变 false 时**只返回不停止**，
    // 音源（3 分 29 秒）会一直播下去，无法满足「阿蒙时刻结束即停」。
    AmbienceUtil.registerBackgroundAmbience(
        new BackgroundAmbience(NRSounds.JESTER_AMBIENT,
            player -> {
              if (SREClient.gameComponent == null)
                return false;
              if (SREClient.gameComponent.isPsychoActive()) {
                var level = Minecraft.getInstance().level;
                if (level == null)
                  return false;
                return (level.players().stream().anyMatch((p) -> {
                  if (SREClient.gameComponent.isRole(p, ModRoles.JESTER)) {
                    if (SREPlayerPsychoComponent.KEY.get(p).getPsychoTicks() > 0) {
                      return true;
                    }
                  }
                  return false;
                }));
              }
              return false;
            },
            1));
    AmbienceUtil.registerBackgroundAmbience(
        new BackgroundAmbience(NRSounds.NYAN_CAT,
            player -> {
              if (SREClient.gameComponent == null)
                return false;
              if (SREClient.gameComponent.isPsychoActive()) {
                var level = Minecraft.getInstance().level;
                if (level == null)
                  return false;
                return (level.players().stream().anyMatch((p) -> {
                  if (SREClient.gameComponent.isRole(p, BounsRoles.CAT_KILLER)) {
                    if (SREPlayerPsychoComponent.KEY.get(p).getPsychoTicks() > 0) {
                      return true;
                    }
                  }
                  return false;
                }));
              }
              return false;
            },
            1));
    AmbienceUtil.registerBackgroundAmbience(
        new MyBackgroundAmbience(NRSounds.MUSIC_CLOCK, SoundSource.MASTER,
            player -> {
              var client = Minecraft.getInstance();
              if (client == null || client.player == null)
                return false;
              if (client.player.hasEffect(ModEffects.OTHERWORLD_AURA))
                return true;
              return false;
            },
            0.8f, 10, 10));
    // 听觉干扰：受影响的玩家耳边循环播放干扰音频，使用 MASTER 声道确保无法被音量设置屏蔽
    AmbienceUtil.registerBackgroundAmbience(
        new MyBackgroundAmbience(NRSounds.OPPOAFIVE, SoundSource.MASTER,
            player -> {
              var client = Minecraft.getInstance();
              if (client == null || client.player == null)
                return false;
              return client.player.hasEffect(ModEffects.TINGJUEGANRAO);
            },
            1.0f, 2, 2));
    // Dream（Dream）狂暴：MANHUNT_CHASE 追杀音乐，狂暴期间全程循环。
    // 移植自 StarRailExpress：用 MASTER 声道（与小丑/猫娘/阿蒙等所有职业狂暴音乐一致），
    // MUSIC 声道会被玩家的音乐音量滑块静音，导致开狂暴时听不到专属追杀音乐。
    AmbienceUtil.registerBackgroundAmbience(
        new MyBackgroundAmbience(NRSounds.MANHUNT_CHASE, SoundSource.MASTER,
            player -> {
              if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
                return false;
              return org.agmas.noellesroles.game.roles.killer.dream.client.DreamClientHandler
                  .isAnyDreamBerserk();
            },
            0.9f, 10, 20));
  }
}
