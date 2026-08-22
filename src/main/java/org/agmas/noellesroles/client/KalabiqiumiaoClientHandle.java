package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.kalabiqiumiao.KalabiqiumiaoPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 纸片人客户端视角处理：
 * - 弦化期间保留玩家自己的视角选择（允许 F5 自由切换第一/第三人称）；
 * - 弦化结束后不再干预，原版逻辑会强制存活的平民回到第一人称。
 */
public class KalabiqiumiaoClientHandle {
    public static void register() {
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (isLocalPaperActive(localPlayer)) {
                return switch (original) {
                    case CameraType.FIRST_PERSON -> AllowOtherCameraType.ReturnCameraType.FIRST_PERSON;
                    case CameraType.THIRD_PERSON_BACK -> AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
                    case CameraType.THIRD_PERSON_FRONT -> AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_FRONT;
                };
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
    }

    /**
     * 客户端判定玩家是否处于弦化状态，三层判定依次兜底：
     * 1. 组件同步状态；
     * 2. 原版实体位置同步包镜像的判定箱（服务端每 tick 将压扁判定箱同步给所有客户端，最可靠）；
     * 3. 角色判定 + 技能赋予的缓降 + 跳跃提升双效果。
     */
    public static boolean isPaperVisible(Player player) {
        if (player == null) {
            return false;
        }
        if (KalabiqiumiaoPlayerComponent.isPaperActive(player)) {
            return true;
        }
        if (isBoundingBoxPaperThin(player)) {
            return true;
        }
        var gameComponent = SREClient.gameComponent;
        return gameComponent != null
                && gameComponent.isRole(player, ModRoles.KALABIQIUMIAO)
                && player.hasEffect(MobEffects.SLOW_FALLING)
                && player.hasEffect(MobEffects.JUMP);
    }

    /**
     * 通过边界箱判定纸片形态：压扁后的判定箱某一水平轴厚度 ≤ {@code PAPER_THICKNESS}。
     * 客户端边界箱由原版实体同步包镜像服务端状态，不受模组组件同步影响。
     */
    private static boolean isBoundingBoxPaperThin(Player player) {
        var box = player.getBoundingBox();
        double xSize = box.maxX - box.minX;
        double zSize = box.maxZ - box.minZ;
        double min = Math.min(xSize, zSize);
        double max = Math.max(xSize, zSize);
        return min <= KalabiqiumiaoPlayerComponent.PAPER_THICKNESS + 0.01D
                && max >= KalabiqiumiaoPlayerComponent.PAPER_WIDTH - 0.01D;
    }

    private static boolean isLocalPaperActive(LocalPlayer localPlayer) {
        return isPaperVisible(localPlayer);
    }
}
