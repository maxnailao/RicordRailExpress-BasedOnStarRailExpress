package org.agmas.noellesroles.content.block;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.SREFumoBlocks;
import org.agmas.noellesroles.packet.PlushMakerOpenS2CPacket;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩偶制作器方块。
 *
 * <p>右键打开 {@code PlushMakerScreen}，玩家在界面里输入「玩家名或 UUID」，
 * 客户端把输入发回服务端（{@link org.agmas.noellesroles.packet.PlushMakerCraftC2SPacket}），
 * 服务端解析该玩家档案后产出一个自定义玩家玩偶。</p>
 *
 * <p>产出物就是上游 {@code SREFumoBlocks.CUSTOM_PLAYER_PLUSH} 的方块物品，只把
 * {@link DataComponents#PROFILE} 换成目标玩家的档案 —— 渲染由
 * {@code CustomPlayerPlushModel} / {@code CustomPlayerPlushItemRenderer} 按档案里的皮肤
 * 动态生成，因此不需要为每个玩家准备模型或贴图。</p>
 *
 * <p>本方块不限制权限（所有玩家可用），也不消耗任何材料。</p>
 */
public class PlushMakerBlock extends Block {

    public PlushMakerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new PlushMakerOpenS2CPacket());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 按「玩家名或 UUID」解析档案并给玩家一个该玩家的玩偶。
     *
     * <p>解析顺序：在线玩家（名字 / UUID）→ UUID 字面量 → 服务端档案缓存 → 离线 UUID 兜底。
     * 兜底保证任何输入都能产出玩偶（只是皮肤可能是默认皮肤）。</p>
     *
     * @param player 使用者
     * @param input  玩家名或 UUID 字符串
     */
    public static void craftFor(ServerPlayer player, String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) {
            return;
        }

        GameProfile profile = resolveProfile(player.getServer(), raw);
        if (profile == null) {
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.plush_maker.invalid", raw)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ItemStack stack = new ItemStack(SREFumoBlocks.CUSTOM_PLAYER_PLUSH.asItem());
        stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        String displayName = profile.getName() == null ? raw : profile.getName();
        player.displayClientMessage(Component
                .translatable("message.noellesroles.plush_maker.crafted", displayName)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** 把输入解析成玩家档案；解析失败返回 {@code null}。 */
    private static GameProfile resolveProfile(MinecraftServer server, String input) {
        // 1) 在线玩家：名字或 UUID 直接命中（最准，带完整皮肤属性）
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                GameProfile gp = online.getGameProfile();
                if (gp.getName() != null && gp.getName().equalsIgnoreCase(input)) {
                    return gp;
                }
                if (gp.getId() != null && gp.getId().toString().equalsIgnoreCase(input)) {
                    return gp;
                }
            }
        }

        // 2) UUID 字面量：拿不到名字时用 UUID 当作名字兜底，避免 profile 名字为 null
        UUID parsedId = null;
        try {
            parsedId = UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID，按名字继续
        }
        if (parsedId != null) {
            return new GameProfile(parsedId, input);
        }

        // 3) 服务端档案缓存（正版用户名 → 带 UUID 与皮肤属性）
        if (server != null && server.getProfileCache() != null) {
            Optional<GameProfile> cached = server.getProfileCache().get(input);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        // 4) 离线 UUID 兜底：保证任何输入都能出玩偶
        return new GameProfile(
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + input).getBytes(StandardCharsets.UTF_8)),
                input);
    }
}
