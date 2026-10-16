package org.agmas.noellesroles.client.commands;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.ConfigCompact.ui.TestScreen;
import io.wifi.rhythm.client.RhythmMapManager;
import io.wifi.rhythm.client.screen.RhythmGameListScreen;
import io.wifi.rhythm.client.screen.RhythmGameScreen;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.gui.screen.NewspaperScreen;
import io.wifi.starrailexpress.client.util.ClientScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.screen.GameManagementScreen;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SREClientCommand {
  public static void register() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) -> {
          dispatcher.register(ClientCommandManager.literal("sre:client")
              .then(ClientCommandManager.literal("settings")
                  .then(ClientCommandManager.literal("random_skin")
                      .then(ClientCommandManager.literal("enable").executes((ctx) -> {
                        SREClientConfig.instance().enableRandomSkinForStreaming = true;
                        SREClientConfig.HANDLER.save();
                        ctx.getSource()
                            .sendFeedback(Component
                                .translatable("Success enabled %s! Rejoin the world/server to make it work!",
                                    Component.translatable(
                                        "text.autoconfig.starrailexpress-client.option.enableRandomSkinForStreaming"))
                                .withStyle(ChatFormatting.GREEN));
                        return 1;
                      }))
                      .then(ClientCommandManager.literal("disable")
                          .executes((ctx) -> {
                            ctx.getSource()
                                .sendFeedback(Component
                                    .translatable("Success disabled %s! Rejoin the world/server to make it work!",
                                        Component.translatable(
                                            "text.autoconfig.starrailexpress-client.option.enableRandomSkinForStreaming"))
                                    .withStyle(ChatFormatting.RED));
                            SREClientConfig.instance().enableRandomSkinForStreaming = false;
                            SREClientConfig.HANDLER.save();

                            return 1;
                          }))))
              .then(ClientCommandManager.literal("debug")
                  .then(ClientCommandManager.literal("rhythm_game")
                      .executes((ctx) -> {
                        ClientScheduler.schedule(() -> {
                          ctx.getSource().getClient().setScreen(new RhythmGameListScreen(null));
                        }, 1);
                        return 1;
                      })
                      .then(ClientCommandManager.literal("random")
                          .executes((ctx) -> {
                            var mapDatas = new ArrayList<>(RhythmMapManager.MAP_NAMES.keySet());
                            if (mapDatas.isEmpty()) {
                              ctx.getSource().sendError(Component.literal("No available maps found!"));
                              return 0;
                            }
                            final var mapKey = mapDatas.get(new Random().nextInt(0, mapDatas.size()));
                            final var mapData = RhythmMapManager.MAP_NAMES.get(mapKey);
                            if (mapData == null) {
                              ctx.getSource().sendError(Component.literal("Not a valid src!"));
                              return 0;
                            }
                            ClientScheduler.schedule(() -> {
                              RhythmGameScreen.open(mapData);
                            }, 1);
                            ctx.getSource().sendFeedback(Component.literal("Successfully!"));
                            return 1;
                          }))
                      .then(ClientCommandManager.argument("src", StringArgumentType.greedyString()).suggests((c, b) -> {
                        for (final var t : RhythmMapManager.MAP_NAMES.keySet()) {
                          b.suggest(t.toString());
                        }
                        return b.buildFuture();
                      })
                          .executes((ctx) -> {
                            String str = StringArgumentType.getString(ctx, "src");
                            var t = ResourceLocation.tryParse(str);
                            if (t == null) {
                              ctx.getSource().sendError(Component.literal("Not a valid src!"));
                              return 0;
                            }
                            var mapData = RhythmMapManager.MAP_NAMES.get(t);
                            if (mapData == null) {
                              ctx.getSource().sendError(Component.literal("Not a valid src!"));
                              return 0;
                            }
                            ClientScheduler.schedule(() -> {
                              RhythmGameScreen.open(mapData);
                            }, 1);
                            ctx.getSource().sendFeedback(Component.literal("Successfully!"));
                            return 1;
                          }))))
              .then(ClientCommandManager.literal("screen")
                  .then(ClientCommandManager.literal("GameManagePanel")
                      .executes(context -> {
                        if (context.getSource().getPlayer().hasPermissions(2)) {
                          ClientScheduler.schedule(() -> {
                            context.getSource().getClient()
                                .setScreen(new GameManagementScreen());
                          }, 1);
                        } else {
                          context.getSource()
                              .sendError(
                                  Component.literal(
                                      "You do not have permission to do that!")
                                      .withStyle(ChatFormatting.RED));
                        }
                        return 1;
                      }))
                  .then(ClientCommandManager.literal("settings")
                      .executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new SettingMenuScreen(null));
                        }, 1);
                        return 1;
                      }))
                  .then(ClientCommandManager.literal("test")
                      .executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new TestScreen(null));
                        }, 1);
                        return 1;
                      }))

                  .then(ClientCommandManager.literal("newspaper_test")
                      .then(ClientCommandManager.literal("editing").executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new NewspaperScreen(new ArrayList<>(List.of("这是报纸测试页面"))));
                        }, 1);
                        return 1;
                      })).then(ClientCommandManager.literal("view").executes(context -> {
                        ClientScheduler.schedule(() -> {
                          var msg = new ArrayList<Component>();
                          msg.add(Component.translatable("Hello %s from %s", 1, 2).withStyle(ChatFormatting.RED));
                          context.getSource().getClient()
                              .setScreen(
                                  new NewspaperScreen(msg, Component.literal("hello"), Component.literal("author")));
                        }, 1);
                        return 1;
                      })))));

          // /sre:client pos —— 打印自己当前坐标。
          // 冒险模式看不到 F3 调试屏，排查"决斗领域到底生成在哪"时需要坐标，
          // 所以单独给一条客户端命令（纯本地读取，不发包、不需要权限）。
          dispatcher.register(ClientCommandManager.literal("sre:client")
              .then(ClientCommandManager.literal("pos")
                  .executes(ctx -> {
                    var player = ctx.getSource().getPlayer();
                    if (player == null) {
                      return 0;
                    }
                    String dim = player.level().dimension().location().toString();
                    String pos = String.format("%.1f %.1f %.1f",
                        player.getX(), player.getY(), player.getZ());
                    String tp = String.format("%d %d %d",
                        (int) Math.floor(player.getX()),
                        (int) Math.floor(player.getY()),
                        (int) Math.floor(player.getZ()));
                    ctx.getSource().sendFeedback(
                        Component.literal("维度 " + dim + "　坐标 " + pos)
                            .withStyle(ChatFormatting.AQUA));
                    ctx.getSource().sendFeedback(
                        Component.literal("可直接 /tp " + tp).withStyle(ChatFormatting.GRAY));
                    return 1;
                  })));
        });
  }
}
