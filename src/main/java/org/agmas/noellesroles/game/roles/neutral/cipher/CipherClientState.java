package org.agmas.noellesroles.game.roles.neutral.cipher;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.CipherCloseDroneC2SPacket;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 密客无人机客户端状态：转场动画计时 + finish 顺序拼写捕获。
 * 由 CipherPlayerComponent 的同步回调驱动 onEnter/onExit/onRush，
 * 由 NoellesrolesClient 的 END_CLIENT_TICK 调 tickFinishSpelling。
 */
public final class CipherClientState {

    // 转场时长（毫秒）
    public static final long ENTER_MS = 600L;   // 进入：中间白条散开
    public static final long EXIT_CONVERGE_MS = 700L; // 退出：上下白光向中间靠拢
    public static final long EXIT_BLACK_MS = 700L;      // 退出后黑屏
    public static final long RUSH_RED_MS = 300L;        // 冲锋：边缘 0.3s 平滑变红
    public static final long BLOWN_SHOW_MS = 3000L;     // 引爆提示"炸飞了X人！"显示时长

    private static long enterStart = 0L;
    private static long exitStart = 0L;
    private static long rushStart = 0L;

    // 引爆提示：显示截止时间 + 人数
    private static long blownUntil = 0L;
    private static int blownShownCount = 0;

    // finish 拼写：F I N I S H
    // 使用 GLFW 键码
    private static final int GLFW_F = 70, GLFW_I = 73, GLFW_N = 78, GLFW_S = 83, GLFW_H = 72;
    private static final int[] SPELL_KEYS = { GLFW_F, GLFW_I, GLFW_N, GLFW_I, GLFW_S, GLFW_H };
    private static final String[] SPELL_LETTERS = { "F", "I", "N", "I", "S", "H" };

    private static int finishIndex = 0;   // 已正确拼写到第几个字母
    private static final boolean[] lastDown = new boolean[SPELL_KEYS.length];

    private CipherClientState() {
    }

    // ==================== 转场驱动 ====================
    public static void onEnterDrone() {
        enterStart = System.currentTimeMillis();
        exitStart = 0L;
        rushStart = 0L;
        finishIndex = 0;
    }

    public static void onExitDrone() {
        exitStart = System.currentTimeMillis();
        enterStart = 0L;
    }

    public static void onRushStart() {
        rushStart = System.currentTimeMillis();
    }

    // ==================== 引爆提示 ====================
    public static void onBlown(int count) {
        blownShownCount = count;
        blownUntil = System.currentTimeMillis() + BLOWN_SHOW_MS;
    }

    public static boolean blownActive() {
        return System.currentTimeMillis() < blownUntil;
    }

    public static int blownCount() {
        return blownShownCount;
    }

    // ==================== 无人机物品栏锁定（仅最后三格 6/7/8 可用） ====================
    private static boolean hotbarLocked = false;
    private static int savedSlot = -1;
    private static int lastDroneSel = 6;

    /** 驾驶中每客户端 tick 调用：把选择限制并循环在 7/8/9 三格（索引 6/7/8） */
    public static void lockHotbarToLocal3(net.minecraft.client.Minecraft client) {
        if (client.player == null) return;
        var inv = client.player.getInventory();
        if (!hotbarLocked) {
            hotbarLocked = true;
            savedSlot = inv.selected;
            lastDroneSel = (inv.selected >= 6 && inv.selected <= 8) ? inv.selected : 6;
        }
        int sel = inv.selected;
        if (sel >= 6 && sel <= 8) {
            lastDroneSel = sel;
        } else {
            // 越界：按上一次位置循环（下滚越过6→8，上滚越过8→6）
            inv.selected = (lastDroneSel <= 6) ? 8 : 6;
            lastDroneSel = inv.selected;
        }
    }

    /** 退出驾驶：恢复进入前的物品栏选择 */
    public static void unlockHotbar(net.minecraft.client.Minecraft client) {
        if (!hotbarLocked) return;
        hotbarLocked = false;
        if (client.player != null && savedSlot >= 0) {
            client.player.getInventory().selected = savedSlot;
        }
        savedSlot = -1;
    }

    // ==================== 供 HUD 读取 ====================
    /** 进入转场进度 0~1（>1 表示已结束，无转场） */
    public static float enterProgress() {
        if (enterStart == 0L) return 2f;
        return (System.currentTimeMillis() - enterStart) / (float) ENTER_MS;
    }

    /** 退出转场：<0 表示无。返回自退出开始的毫秒；HUD 自行按 700/1400 分段 */
    public static long exitElapsedMs() {
        if (exitStart == 0L) return -1L;
        long e = System.currentTimeMillis() - exitStart;
        if (e > EXIT_CONVERGE_MS + EXIT_BLACK_MS) {
            exitStart = 0L;
            return -1L;
        }
        return e;
    }

    /** 冲锋红边进度 0~1 */
    public static float rushRedProgress() {
        if (rushStart == 0L) return 0f;
        return Math.min(1f, (System.currentTimeMillis() - rushStart) / (float) RUSH_RED_MS);
    }

    public static int finishIndex() {
        return finishIndex;
    }

    public static String[] letters() {
        return SPELL_LETTERS;
    }

    public static void resetAll() {
        enterStart = 0L;
        exitStart = 0L;
        rushStart = 0L;
        finishIndex = 0;
        blownUntil = 0L;
        blownShownCount = 0;
        for (int i = 0; i < lastDown.length; i++) lastDown[i] = false;
    }

    // ==================== finish 逐字母捕获（每客户端 tick 调用） ====================
    /**
     * 按顺序检测：仅当按下"当前应拼的下一个字母"才点亮；拼完 FINISH → 发送关闭包。
     * 用上升沿检测，避免长按连触发。
     */
    public static void tickFinishSpelling(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;
        if (!CipherPlayerComponent.KEY.get(player).droneActive) return;
        long window = client.getWindow().getWindow();

        int need = finishIndex;
        if (need >= SPELL_KEYS.length) return;

        boolean down = InputConstants.isKeyDown(window, SPELL_KEYS[need]);
        boolean wasDown = lastDown[need];

        // 上升沿：本次按下、上次未按下
        if (down && !wasDown) {
            lastDown[need] = true;
            finishIndex++;
            if (finishIndex >= SPELL_KEYS.length) {
                // 拼完 finish → 关闭无人机
                ClientPlayNetworking.send(new CipherCloseDroneC2SPacket());
                finishIndex = 0;
            }
        } else if (!down) {
            lastDown[need] = false;
        }
    }
}