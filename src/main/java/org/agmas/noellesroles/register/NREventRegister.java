package org.agmas.noellesroles.register;

import org.agmas.noellesroles.handler.AAAHandlerFather;
import org.agmas.noellesroles.init.ModEventsRegister;

/**
 * Noellesroles 世界系统与事件处理器注册，
 * 从 {@link org.agmas.noellesroles.Noellesroles#onInitialize()} 中按类别剥离归一化而来。
 */
public class NREventRegister {

    public static void registerWorldSystemsAndEvents() {
        // 注册C4系统
        org.agmas.noellesroles.game.c4.C4Detonation.register();
        org.agmas.noellesroles.game.c4.PliersDefuseManager.register();
        // 注册鹈鹕系统
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.register();
        // 注册 Mafia 系统
        org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.register();
        // 注册预备魔女系统（杀人现场记录 / 死亡回溯 / 转化为魔女的报幕）
        org.agmas.noellesroles.game.roles.neutral.prewitch.PreWitchManager.register();

        // 注册事件处理器
        ModEventsRegister.registerEvents();
        AAAHandlerFather.register();
        // 
        org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaEventHandler.register();
        org.agmas.noellesroles.game.roles.neutral.amon.AmonEventHandler.register();

        // 注册疫使胜利检测
        org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.registerEvent();
        // 注册神父「天堂制造」序列事件（胜负拦截 + 咏诵/加速推进）
        org.agmas.noellesroles.game.roles.neutral.priest.PriestEventHandler.register();
        // 破镜重圆结局彩蛋：镜像世界终幕检测
        org.agmas.noellesroles.game.MirrorReunionEndEgg.register();
        // 注册「入梦」药水效果处理（睡觉时恢复理智并给予迅捷）
        org.agmas.noellesroles.content.effects.DreamEffectHandler.register();
        // 怯懦、胆小鬼、暴怒的死亡反应（与修饰符标记效果配合）。
        org.agmas.noellesroles.content.effects.DeathReactionHandler.register();
        // Fake Steve event and replacement AI.
        org.agmas.noellesroles.game.fake_steve.FakeSteveDirector.register();
    }
}
