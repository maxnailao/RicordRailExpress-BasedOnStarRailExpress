package org.agmas.noellesroles.game.roles.killer.betterkillerghost;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 鬼魅角色组件
 * 
 * 管理鬼魅的幽影模式技能状态
 */
public class BetterKillerGhostComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    
    public static final ComponentKey<BetterKillerGhostComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "betterkillerghost"),
            BetterKillerGhostComponent.class);

    private final Player player;
    
    /** 是否处于幽影模式 */
    public boolean isInShadowMode = false;
    
    /** 幻影实体UUID */
    public java.util.UUID phantomUuid = null;
    
    /** 剩余传送次数 */
    public int teleportCount = 3;
    
    /** 技能冷却时间（tick） */
    public int cooldown = 0;
    
    /** 技能冷却总时间（120秒 = 2400 tick） */
    public static final int COOLDOWN_TIME = 120 * 20;
    
    /** 最大距离（格） */
    public static final double MAX_DISTANCE = 20.0;

    static {
        // 注册死亡免疫事件 - 幽影模式下无法被击杀
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            // 检查玩家是否有该组件(假人玩家可能没有)
            if (!KEY.maybeGet(player).isPresent()) {
                return true; // 没有组件的玩家正常死亡
            }
            
            BetterKillerGhostComponent comp = KEY.get(player);
            if (comp != null && comp.isInShadowMode) {
                // 幽影模式下免疫所有伤害，无法被击杀
                return false;
            }
            return true;
        });
        
        // 注册左键攻击拦截事件 - 幽影模式下禁止左键攻击
        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
            if (!(attacker instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if (!gameWorld.isRole(serverPlayer, ModRoles.BETTER_KILLER_GHOST)) {
                return InteractionResult.PASS;
            }
            
            BetterKillerGhostComponent comp = KEY.get(serverPlayer);
            if (comp != null && comp.isInShadowMode) {
                // 幽影模式下禁止左键攻击任何实体(不显示提示)
                return InteractionResult.FAIL;
            }
            
            return InteractionResult.PASS;
        });
        
        // 注册右键使用物品拦截事件 - 幽影模式下禁止右键使用手雷、左轮等
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if (!gameWorld.isRole(serverPlayer, ModRoles.BETTER_KILLER_GHOST)) {
                return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            
            BetterKillerGhostComponent comp = KEY.get(serverPlayer);
            if (comp != null && comp.isInShadowMode) {
                // 幽影模式下禁止右键使用任何物品
                return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            
            return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }

    public BetterKillerGhostComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.isInShadowMode = false;
        this.phantomUuid = null;
        this.teleportCount = 2; // 修改为2次
        this.cooldown = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void clientTick() {
        // 客户端更新冷却时间显示
        if (cooldown > 0) {
            cooldown--;
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        
        // 检查是否是鬼魅角色
        if (!gameWorld.isRole(player, ModRoles.BETTER_KILLER_GHOST)) {
            return;
        }

        // 检查游戏是否运行
        if (!gameWorld.isRunning()) {
            return;
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            // 如果玩家在幽影模式中死亡，清理状态
            if (isInShadowMode) {
                exitShadowMode(false);
            }
            return;
        }

        // 更新冷却时间
        if (cooldown > 0) {
            cooldown--;
            // 每20秒同步一次
            if (cooldown % 400 == 0) {
                sync();
            }
        }

        // 如果在幽影模式中，检查各种条件
        if (isInShadowMode) {
            handleShadowMode(serverPlayer, gameWorld);
            
            // 幽影模式下，强制手持物品进入1秒冷却
            applyCooldownToHeldItem(serverPlayer);
        }

        // 发送ActionBar显示信息
        sendActionBarInfo(serverPlayer);
    }

    /**
     * 处理幽影模式逻辑
     */
    private void handleShadowMode(ServerPlayer serverPlayer, SREGameWorldComponent gameWorld) {
        // 获取幻影实体
        GhostPhantomEntity phantom = getPhantomEntity();
        
        if (phantom == null || !phantom.isAlive()) {
            // 幻影不存在或已死亡（应该已经在playerHurt中处理了死亡）
            // 这里只是清理状态，不再造成伤害
            exitShadowModeForced();
            return;
        }

        // 检查距离
        double distance = player.distanceTo(phantom);
        if (distance > MAX_DISTANCE) {
            // 距离超过20格，强制退出幽影模式
            exitShadowMode(true);
            serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.betterkillerghost.too_far")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                true
            );
            return;
        }

        // 确保玩家处于隐身状态
        if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, true));
        }
    }

    /**
     * 使用技能 - 进入幽影模式
     */
    public void useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        
        // 检查是否在幽影模式中
        if (isInShadowMode) {
            // 已在幽影模式中，执行传送或其他操作
            handleInShadowMode(serverPlayer);
            return;
        }

        // 检查冷却时间
        if (cooldown > 0) {
            player.displayClientMessage(
                Component.translatable("message.noellesroles.ability_cooldown", (cooldown + 19) / 20)
                    .withStyle(net.minecraft.ChatFormatting.RED),
                true
            );
            return;
        }

        // 检查游戏状态
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 进入幽影模式
        enterShadowMode(serverPlayer);
    }

    /**
     * 进入幽影模式
     */
    private void enterShadowMode(ServerPlayer serverPlayer) {
        // 在玩家脚下生成幻影
        GhostPhantomEntity phantom = new GhostPhantomEntity(ModEntities.GHOST_PHANTOM, player.level());
        phantom.setPos(player.getX(), player.getY(), player.getZ());
        phantom.setOwner(serverPlayer);
        player.level().addFreshEntity(phantom);
        
        this.phantomUuid = phantom.getUUID();
        this.isInShadowMode = true;
        this.teleportCount = 2; // 修改为2次
        
        // 玩家隐身
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, true));
        
        // 播放音效
        player.level().playSound(null, player.blockPosition(),
            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
        
        // 发送消息
        serverPlayer.displayClientMessage(
            Component.translatable("message.noellesroles.betterkillerghost.shadow_mode_enter")
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE),
            true
        );
        
        sync();
    }

    /**
     * 处理在幽影模式中的操作
     */
    private void handleInShadowMode(ServerPlayer serverPlayer) {
        GhostPhantomEntity phantom = getPhantomEntity();
        if (phantom == null || !phantom.isAlive()) {
            exitShadowMode(false);
            return;
        }

        // 检查是否按下Shift键（传送到幻影位置）
        if (player.isShiftKeyDown()) {
            // 传送到幻影位置
            teleportToPhantom(serverPlayer, phantom);
            exitShadowMode(true);
            return;
        }

        // 普通按键：传送到身边
        if (teleportCount > 0) {
            teleportPhantomToPlayer(serverPlayer, phantom);
            teleportCount--;
            
            // 播放音效
            player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.0F);
            
            // 如果传送次数用完，显示提示但不退出，等待再次按G键
            if (teleportCount == 0) {
                serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.betterkillerghost.teleport_exhausted")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true
                );
            }
        } else {
            // 传送次数已为0，再次按G键强制退出幽影模式
            exitShadowMode(true);
        }
    }

    /**
     * 将幻影传送到玩家身边
     */
    private void teleportPhantomToPlayer(ServerPlayer serverPlayer, GhostPhantomEntity phantom) {
        // 在玩家面前生成幻影
        double offsetX = -Math.sin(Math.toRadians(player.getYRot())) * 2.0;
        double offsetZ = Math.cos(Math.toRadians(player.getYRot())) * 2.0;
        
        phantom.setPos(
            player.getX() + offsetX,
            player.getY(),
            player.getZ() + offsetZ
        );
        
        // 播放粒子效果
        ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(
            net.minecraft.core.particles.ParticleTypes.PORTAL,
            phantom.getX(), phantom.getY() + 1.0, phantom.getZ(),
            10, 0.5, 0.5, 0.5, 0.1
        );
    }

    /**
     * 玩家传送到幻影位置
     */
    private void teleportToPhantom(ServerPlayer serverPlayer, GhostPhantomEntity phantom) {
        serverPlayer.teleportTo(
            serverPlayer.serverLevel(),
            phantom.getX(), phantom.getY(), phantom.getZ(),
            phantom.getYRot(), phantom.getXRot()
        );
        
        // 播放粒子效果
        ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(
            net.minecraft.core.particles.ParticleTypes.PORTAL,
            player.getX(), player.getY() + 1.0, player.getZ(),
            20, 0.5, 0.5, 0.5, 0.2
        );
    }

    /**
     * 退出幽影模式（强制，不设置冷却）
     * 用于幻影被摧毁时调用
     */
    public void exitShadowModeForced() {
        if (!isInShadowMode) {
            return;
        }

        // 移除隐身效果
        player.removeEffect(MobEffects.INVISIBILITY);

        // 移除幻影
        GhostPhantomEntity phantom = getPhantomEntity();
        if (phantom != null) {
            phantom.discard();
        }

        // 重置状态（不设置冷却）
        this.isInShadowMode = false;
        this.phantomUuid = null;
        this.teleportCount = 3;

        sync();
    }

    /**
     * 退出幽影模式（正常，设置冷却时间）
     * @param removePhantom 是否移除幻影
     */
    private void exitShadowMode(boolean removePhantom) {
        if (!isInShadowMode) {
            return;
        }

        // 移除隐身效果
        player.removeEffect(MobEffects.INVISIBILITY);

        // 移除幻影
        if (removePhantom) {
            GhostPhantomEntity phantom = getPhantomEntity();
            if (phantom != null) {
                phantom.discard();
            }
        }

        // 设置冷却时间
        this.cooldown = COOLDOWN_TIME;
        this.isInShadowMode = false;
        this.phantomUuid = null;
        this.teleportCount = 2; // 修改为2次

        // 发送消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.betterkillerghost.shadow_mode_exit")
                    .withStyle(net.minecraft.ChatFormatting.GREEN),
                true
            );
        }

        sync();
    }

    /**
     * 获取幻影实体
     */
    private GhostPhantomEntity getPhantomEntity() {
        if (phantomUuid == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        
        net.minecraft.world.entity.Entity entity = serverLevel.getEntity(phantomUuid);
        if (entity instanceof GhostPhantomEntity) {
            return (GhostPhantomEntity) entity;
        }
        
        return null;
    }

    /**
     * 发送ActionBar信息
     */
    private void sendActionBarInfo(ServerPlayer serverPlayer) {
        if (isInShadowMode) {
            GhostPhantomEntity phantom = getPhantomEntity();
            if (phantom != null && phantom.isAlive()) {
                double distance = player.distanceTo(phantom);
                int distanceInt = (int) Math.round(distance);
                
                Component actionBar = Component.translatable(
                    "message.noellesroles.betterkillerghost.actionbar",
                    distanceInt,
                    teleportCount
                ).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE);
                
                serverPlayer.sendSystemMessage(actionBar, true);
            }
        } else if (cooldown > 0) {
            // 显示冷却时间
            int seconds = (cooldown + 19) / 20;
            Component actionBar = Component.translatable(
                "message.noellesroles.betterkillerghost.cooldown",
                seconds
            ).withStyle(net.minecraft.ChatFormatting.GRAY);
            
            serverPlayer.sendSystemMessage(actionBar, true);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isInShadowMode", this.isInShadowMode);
        if (this.phantomUuid != null) {
            tag.putUUID("phantomUuid", this.phantomUuid);
        }
        tag.putInt("teleportCount", this.teleportCount);
        tag.putInt("cooldown", this.cooldown);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isInShadowMode = tag.getBoolean("isInShadowMode");
        if (tag.contains("phantomUuid")) {
            this.phantomUuid = tag.getUUID("phantomUuid");
        } else {
            this.phantomUuid = null;
        }
        this.teleportCount = tag.getInt("teleportCount");
        this.cooldown = tag.getInt("cooldown");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不需要持久化
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不需要持久化
    }

    /**
     * 幽影模式下，给背包内所有物品强制添加0.3秒(6tick)冷却（不论是否手持）
     * @param player 玩家
     */
    private void applyCooldownToHeldItem(Player player) {
        var cooldowns = player.getCooldowns();
        
        // 遍历玩家物品栏，给所有物品添加冷却
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                cooldowns.addCooldown(stack.getItem(), 6); // 0.3秒冷却(20tick = 1秒)
            }
        }
        
        // 也检查盔甲槽
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                cooldowns.addCooldown(stack.getItem(), 6);
            }
        }
    }
    
    /**
     * 给单个物品添加冷却
     */
    private void applyCooldownToItem(Player player, net.minecraft.world.item.ItemStack stack, 
                                     net.minecraft.world.item.ItemCooldowns cooldowns, int cooldownTicks) {
        if (!stack.isEmpty()) {
            cooldowns.addCooldown(stack.getItem(), cooldownTicks);
        }
    }
}
