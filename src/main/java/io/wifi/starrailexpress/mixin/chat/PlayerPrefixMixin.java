package io.wifi.starrailexpress.mixin.chat;

import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 {@code Player#getDisplayName()} 带上「名片」（原有行为）与「称号」（新增）。
 *
 * <h2>为什么要改这里，而不是只靠 scoreboard team</h2>
 * 队伍 prefix/suffix 只在**渲染头顶名字牌 / 玩家列表**时生效。
 * 但聊天、瞄准准星上的名字、各种 {@code getDisplayName()} 文本都**不走**那条渲染路径，
 * 所以在这些地方看不到称号 —— 这就是"装备了称号但发消息时名字前没有"的原因。
 *
 * <p>两个来源分工：
 * <ul>
 * <li>{@code getDisplayName()}（本 mixin）→ 聊天 / 准星名字 / 指令回执等文本；</li>
 * <li>scoreboard team prefix/suffix → 头顶名字牌 / 玩家列表。</li>
 * </ul>
 * 两者渲染路径不同，**不会重复显示**。
 *
 * <p>客户端也要生效：准星上的目标名是客户端渲染的，只在服务端加就没用。
 */
@Mixin(Player.class)
public class PlayerPrefixMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    public void getDisplayName(CallbackInfoReturnable<Component> cir) {
        Player mainPlayer = (Player) (Object) this;

        // 称号：从记分板队伍取（服务端/客户端都有这份数据）。
        // 有了称号就用「纯名字」当底，**不能**再用原版 getDisplayName() ——
        // 那个已经含队伍 prefix 了，会变成 "[VIP] [VIP] Steve"。
        MutableComponent title = io.wifi.starrailexpress.content.title.TitleManager
                .displayTitleFor(mainPlayer);
        Component base;
        MutableComponent out = Component.empty();
        if (title != null) {
            out.append(title);
            base = Component.literal(mainPlayer.getName().getString());
        } else {
            // 没称号（或队伍还没同步）→ 保留原版行为（含队伍 prefix = 别的机制的前缀）
            base = cir.getReturnValue();
            if (base == null) {
                base = Component.literal(mainPlayer.getName().getString());
            }
        }
        out.append(base);

        // 原有「名片」：只在服务端有组件数据
        if (mainPlayer instanceof ServerPlayer) {
            try {
                MutableComponent nametag = NameTagInventoryComponent.KEY.get(mainPlayer).generate();
                if (nametag != null) {
                    out.append(nametag);
                }
            } catch (Throwable ignored) {
                // 组件还没就绪时不影响名字显示
            }
        }

        if (out.getString().isEmpty()) {
            return; // 什么都没有：保持原样，避免多包一层
        }
        cir.setReturnValue(out);
    }
}
