package org.agmas.noellesroles.game.roles.killer.raider;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 掠夺者玩家组件
 * - 弩击杀后30秒冷却
 * - 特殊疯魔模式：快速装填3弩 + 2根毒箭，锁定主手，疯魔期间无击杀冷却
 * - 疯魔结束后回收掠夺之弩，返还普通弩（毒箭不回收）
 */
public class RaiderPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<RaiderPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "raider"),
            RaiderPlayerComponent.class);

    /** 弩击杀冷却：30秒 = 600 ticks */
    public static final int CROSSBOW_KILL_COOLDOWN = 30 * 20;

    /** 疯魔持续时间：30秒 = 600 ticks */
    public static final int FRENZY_DURATION = 30 * 20;

    private final Player player;

    /** 弩击杀冷却计时器(tick) */
    public int crossbowKillCooldown = 0;

    /** 是否处于疯魔状态 */
    public boolean inFrenzy = false;

    /** 疯魔前保存的普通弩 */
    private ItemStack savedCrossbow = null;

    public RaiderPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.crossbowKillCooldown = 0;
        this.inFrenzy = false;
        this.savedCrossbow = null;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        // 减少弩击杀冷却
        if (this.crossbowKillCooldown > 0) {
            this.crossbowKillCooldown--;
            if (this.crossbowKillCooldown % 20 == 0 || this.crossbowKillCooldown == 0) {
                this.sync();
            }
        }

        // 疯魔状态检测
        if (inFrenzy) {
            SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
            if (psychoComponent.getPsychoTicks() <= 0) {
                stopFrenzy();
            }
        }
    }

    /**
     * 保证背包内至少有1根疯魔毒箭
     */
    private void ensureFrenzyPoisonArrow() {
        boolean hasArrow = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.TIPPED_ARROW) && isPoisonArrow(stack)) {
                hasArrow = true;
                break;
            }
        }
        if (!hasArrow) {
            ItemStack poisonArrow = createFrenzyPoisonArrow();
            inv.add(poisonArrow);
        }
    }

    /**
     * 判断是否为毒箭（通过POTION_CONTENTS判定）
     */
    private boolean isPoisonArrow(ItemStack stack) {
        if (!stack.is(Items.TIPPED_ARROW)) return false;
        var potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents == null || potionContents.potion().isEmpty()) return false;
        var potion = potionContents.potion().get();
        return potion.value().getEffects().stream()
                .anyMatch(effect -> effect.getEffect().value() == MobEffects.POISON);
    }

    /**
     * 创建一根疯魔毒箭
     */
    private ItemStack createFrenzyPoisonArrow() {
        ItemStack poisonArrow = Items.TIPPED_ARROW.getDefaultInstance();
        poisonArrow.set(DataComponents.ITEM_NAME,
                Component.translatable("item.raider_frenzy_poison_arrow.name").withStyle(ChatFormatting.RED));
        poisonArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
        poisonArrow.set(DataComponents.MAX_STACK_SIZE, 1);
        return poisonArrow;
    }

    /**
     * 弩击杀玩家后触发冷却（疯魔期间无冷却）
     */
    public void onCrossbowKill() {
        if (inFrenzy) {
            // 疯魔期间重置冷却，使得无冷却效果
            this.crossbowKillCooldown = 0;
        } else {
            this.crossbowKillCooldown = CROSSBOW_KILL_COOLDOWN;
        }
        this.sync();
    }

    /**
     * 启动掠夺者疯魔模式
     * - 保存当前普通弩，给予快速装填3的掠夺之弩（锁定主手）
     * - 给予2根毒箭（不回收）
     * - 清除弩冷却，疯魔期间弩击杀无冷却
     */
    public boolean startFrenzy() {
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() > 0) {
            return false;
        }
        if (inFrenzy) {
            return false;
        }

        // 重置冷却 + 清除弩原版冷却（以防万一）
        this.crossbowKillCooldown = 0;
        player.getCooldowns().removeCooldown(Items.CROSSBOW);

        // 保存当前主手弩（疯魔结束后返还）
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(Items.CROSSBOW)) {
            this.savedCrossbow = mainHand.copy();
        } else {
            // 如果主手不是弩，尝试从背包找一把弩保存
            this.savedCrossbow = findAndRemoveNormalCrossbow();
        }

        // 给予快速装填1的掠夺之弩并锁定主手
        ItemStack frenzyCrossbow = Items.CROSSBOW.getDefaultInstance();
        frenzyCrossbow.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        frenzyCrossbow.set(DataComponents.ITEM_NAME,
                Component.translatable("item.raider_frenzy_crossbow.name").withStyle(ChatFormatting.DARK_RED));
        // 添加快速装填1附魔
        if (player.level() instanceof ServerLevel serverLevel) {
            var quickChargeHolder = serverLevel.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .holders()
                    .filter(holder -> holder.is(Enchantments.QUICK_CHARGE))
                    .findFirst();
            quickChargeHolder.ifPresent(holder -> frenzyCrossbow.enchant(holder, 1));
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, frenzyCrossbow);

        // 给予2根毒箭（不回收）
        player.getInventory().add(createFrenzyPoisonArrow());
        player.getInventory().add(createFrenzyPoisonArrow());

        // 设置psycho模式（锁定主手）
        psychoComponent.setPsychoTicks(FRENZY_DURATION);
        psychoComponent.setArmour(1); // 一层护盾
        psychoComponent.type = 4; // 掠夺者专属疯魔类型
        psychoComponent.sync();

        // 更新psycho计数
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);

        // 触发状态栏
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
        }

        this.inFrenzy = true;
        this.sync();

        // 全图响起劫掠号角 + 掠夺者笑声（所有玩家直接播放，保证听到）
        if (player.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer p : serverLevel.players()) {
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 1.0F, 1.0F);
                p.playNotifySound(SoundEvents.PILLAGER_CELEBRATE, SoundSource.PLAYERS, 1.0F, 1.0F);
                p.playNotifySound(SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 0.8F);
            }
            // 变身粒子
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.5, 1.0, 0.5, 0.08);
            serverLevel.sendParticles(ParticleTypes.ASH,
                    player.getX(), player.getY() + 1, player.getZ(),
                    25, 0.5, 1.0, 0.5, 0.05);
        }

        return true;
    }

    /**
     * 从背包中查找并移除一把普通弩（非掠夺之弩）
     */
    private ItemStack findAndRemoveNormalCrossbow() {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.CROSSBOW) && !isFrenzyCrossbow(stack)) {
                ItemStack copy = stack.copy();
                inv.setItem(i, ItemStack.EMPTY);
                return copy;
            }
        }
        return null;
    }

    /**
     * 判断是否为掠夺之弩（疯魔弩）
     * 通过附魔检测：疯魔弩带有快速装填附魔，普通弩无附魔
     */
    private boolean isFrenzyCrossbow(ItemStack stack) {
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments != null && !enchantments.isEmpty();
    }

    /**
     * 停止掠夺者疯魔模式
     * - 回收掠夺之弩，返还普通弩
     * - 毒箭不回收
     * - 解除主手锁定
     */
    public void stopFrenzy() {
        if (!inFrenzy)
            return;

        this.inFrenzy = false;

        // 回收掠夺之弩，返还普通弩
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(Items.CROSSBOW) && isFrenzyCrossbow(mainHand)) {
            if (savedCrossbow != null && !savedCrossbow.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, savedCrossbow.copy());
            } else {
                ItemStack normalCrossbow = Items.CROSSBOW.getDefaultInstance();
                normalCrossbow.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
                player.setItemInHand(InteractionHand.MAIN_HAND, normalCrossbow);
            }
        } else {
            if (savedCrossbow != null && !savedCrossbow.isEmpty()) {
                player.getInventory().add(savedCrossbow.copy());
            }
        }
        this.savedCrossbow = null;

        // 重置psycho type（解除物品锁定）
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        psychoComponent.type = -1;
        psychoComponent.sync();

        this.sync();
    }

    /**
     * 清空背包内所有毒箭（TIPPED_ARROW类型）
     */
    private void removeAllPoisonArrows() {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.TIPPED_ARROW)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 检查玩家是否处于掠夺者疯魔状态
     */
    public static boolean isInFrenzy(Player player) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRole(player, ModRoles.LUEDUOZHE))
            return false;
        return KEY.get(player).inFrenzy;
    }

    /**
     * 注册击杀事件（备用，主要用于非疯魔期冷却同步）
     * 疯魔期击杀特效已移至ArrowMixin直接触发
     */
    public static void registerKillCooldownEvent() {
        // 冷却逻辑已在ArrowMixin中直接处理，此处保留事件以备扩展
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("crossbowKillCooldown", this.crossbowKillCooldown);
        tag.putBoolean("inFrenzy", this.inFrenzy);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.crossbowKillCooldown = tag.getInt("crossbowKillCooldown");
        this.inFrenzy = tag.getBoolean("inFrenzy");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
