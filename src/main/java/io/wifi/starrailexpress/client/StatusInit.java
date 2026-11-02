package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class StatusInit {
   /**
    * @param spectatorVisible 观战者是否也能看到这条读条
    *                         （黑警时刻这种全场公共事件应为 true；个人状态如狂暴为 false）
    */
   public record StatusBar(String id, String name, Supplier<Float> progressSupplier, boolean spectatorVisible){

       /** 默认只有存活玩家可见（保持原有行为）。 */
       public StatusBar(String id, String name, Supplier<Float> progressSupplier) {
           this(id, name, progressSupplier, false);
       }
   }
    public static Map<String, StatusBar> statusBars = new HashMap<>();

   static {
       statusBars.put("Psycho", new StatusBar("Psycho", "\u00a76狂暴模式", () -> {
           final var playerPsychoComponent = SREPlayerPsychoComponent.KEY.get(Minecraft.getInstance().player);
           if (playerPsychoComponent == null){
               return 0.0f;
           }
           if (playerPsychoComponent.getPsychoTicks() <= 0) return 0.0f;
           return playerPsychoComponent.getPsychoTicks() / (float) GameConstants.getPsychoTimer();
       }));

       // 黑警时刻：和亡命徒一样用读条，替代原来屏幕正中 + 左下角的两行倒计时文字。
       // 进度由服务端每秒刷新的剩余秒数算出来（服务端 20 tick 同步一次）。
       statusBars.put(
               org.agmas.noellesroles.game.roles.neutral.corruptcop.CorruptCopTime.PROGRESS_BAR_ID,
               new StatusBar(
                       org.agmas.noellesroles.game.roles.neutral.corruptcop.CorruptCopTime.PROGRESS_BAR_ID,
                       "\u00a78黑警时刻",
                       () -> {
                           var gameComponent = SREClient.gameComponent;
                           if (gameComponent == null || !gameComponent.isCorruptCopBlackoutActive()) {
                               return 0.0f;
                           }
                           int totalSeconds = org.agmas.noellesroles.game.roles.neutral.corruptcop.CorruptCopTime.DURATION_TICKS
                                   / 20;
                           if (totalSeconds <= 0) {
                               return 0.0f;
                           }
                           int remaining = gameComponent.getCorruptCopBlackoutRemainingSeconds();
                           return Math.max(0.0f, Math.min(1.0f, remaining / (float) totalSeconds));
                       },
                       // 全场公共事件：阵亡/观战的人也看得到读条
                       true));
   }

    public static StatusBar getStatusBar(String id) {
        return statusBars.get(id);
    }
}
