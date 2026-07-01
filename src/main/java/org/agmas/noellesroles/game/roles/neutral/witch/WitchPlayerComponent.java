package org.agmas.noellesroles.game.roles.neutral.witch;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * 女巫玩家组件
 *
 * 中立阵营（偏狼），假心情，无限体力
 * 胜利条件：跟随杀手阵营一同胜利
 *
 * 被动：每10秒获取50金币（与捣蛋鬼一致）
 *
 * 技能：
 * - 蹲下15秒获取药剂素材（无声音）
 * - 蹲下按技能键：切换当前炼制的药水
 * - 直接按技能键：消耗素材+金币炼制一份喷溅药水
 *
 * 药水清单（均为喷溅型）：
 * 1. 速度1 15s    150金币 2素材
 * 2. 缓慢2 10s    100金币 1素材
 * 3. 急迫2 10s    200金币 1素材
 * 4. 隐身1 5s     200金币 2素材
 * 5. 失明1+黑暗1 8s  150金币 2素材
 * 6. 转向受限 8s   150金币 1素材
 * 7. 按键禁用 3s   150金币 1素材
 *
 * 限制：每种药水只能炼两次，游戏结束时重置
 */
public class WitchPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<WitchPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "witch"),
            WitchPlayerComponent.class);

    /** 蹲下获取素材间隔（15秒 = 300 tick） */
    public static final int MATERIAL_GATHER_INTERVAL = 15 * 20;

    /** 每次蹲下获取的素材数量 */
    public static final int MATERIALS_PER_GATHER = 1;

    /** 炼制药水需要的素材数量（按药水类型不同） */
    public static final int[] MATERIALS_TO_CRAFT = {2, 1, 1, 2, 2, 1, 1};

    /** 药水最大炼制次数 */
    public static final int MAX_CRAFT_COUNT = 2;

    /** 被动收入间隔：20秒 = 400 ticks */
    private static final int PASSIVE_INCOME_INTERVAL = 400;

    /** 被动收入金额 */
    private static final int PASSIVE_INCOME_AMOUNT = 50;

    /** 药水类型常量 */
    public static final int POTION_SPEED = 0;         // 速度1 15s   150金币 2素材
    public static final int POTION_SLOWNESS = 1;      // 缓慢2 10s   100金币 1素材
    public static final int POTION_HASTE = 2;         // 急迫2 10s   200金币 1素材
    public static final int POTION_INVISIBILITY = 3;  // 隐身1 5s    200金币 2素材
    public static final int POTION_BLIND_DARK = 4;    // 失明1+黑暗1 8s  150金币 2素材
    public static final int POTION_TURN_WEAK = 5;     // 转向受限 8s  150金币 1素材
    public static final int POTION_USED_BANED = 6;    // 按键禁用 3s  150金币 1素材

    /** 药水总数 */
    public static final int POTION_COUNT = 7;

    private final Player player;

    /** 蹲下获取素材计时器 */
    private int materialGatherTimer = MATERIAL_GATHER_INTERVAL;

    /** 被动收入计时器 */
    private int passiveIncomeTimer = 0;

    /** 当前选择的药水索引 */
    private int currentPotionIndex = POTION_SPEED;

    /** 每种药水的已炼制次数 */
    private final int[] potionCraftCounts = new int[POTION_COUNT];

    public WitchPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (!tag.contains("MaterialGatherTimer")) {
            this.clear();
            return;
        }
        this.materialGatherTimer = tag.getInt("MaterialGatherTimer");
        this.passiveIncomeTimer = tag.getInt("PassiveIncomeTimer");
        this.currentPotionIndex = tag.getInt("CurrentPotionIndex");

        for (int i = 0; i < POTION_COUNT; i++) {
            this.potionCraftCounts[i] = tag.getInt("PotionCraftCount_" + i);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning()) {
            return;
        }
        if (!gameWorldComponent.isRole(this.player, ModRoles.WITCH)) {
            return;
        }
        tag.putInt("MaterialGatherTimer", this.materialGatherTimer);
        tag.putInt("PassiveIncomeTimer", this.passiveIncomeTimer);
        tag.putInt("CurrentPotionIndex", this.currentPotionIndex);

        for (int i = 0; i < POTION_COUNT; i++) {
            tag.putInt("PotionCraftCount_" + i, this.potionCraftCounts[i]);
        }
    }

    @Override
    public void init() {
        this.materialGatherTimer = MATERIAL_GATHER_INTERVAL;
        this.passiveIncomeTimer = 0;
        this.currentPotionIndex = POTION_SPEED;
        for (int i = 0; i < POTION_COUNT; i++) {
            this.potionCraftCounts[i] = 0;
        }
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.WITCH))
            return;
        if (player.isSpectator())
            return;

        // ===== 被动收入：每20秒给50金币 =====
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            passiveIncomeTimer++;
            if (passiveIncomeTimer >= PASSIVE_INCOME_INTERVAL) {
                passiveIncomeTimer = 0;
                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
                shopComponent.addToBalance(PASSIVE_INCOME_AMOUNT);
            }
        }

        // ===== 蹲下获取素材（无声音） =====
        if (player.isShiftKeyDown()) {
            if (materialGatherTimer > 0) {
                // 每10秒同步一次到客户端
                if (materialGatherTimer % 200 == 0) {
                    sync();
                }
                materialGatherTimer--;
                if (materialGatherTimer == 0) {
                    gatherMaterials();
                    materialGatherTimer = MATERIAL_GATHER_INTERVAL;
                    sync();
                }
            }
        } else {
            if (materialGatherTimer != MATERIAL_GATHER_INTERVAL) {
                materialGatherTimer = MATERIAL_GATHER_INTERVAL;
            }
        }
    }

    /**
     * 获取药剂素材（无声音）
     */
    private void gatherMaterials() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        ItemStack materials = new ItemStack(ModItems.ALCHEMY_MATERIAL, MATERIALS_PER_GATHER);
        if (!player.getInventory().add(materials)) {
            player.drop(materials, false);
        }

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.witch.material_gathered", MATERIALS_PER_GATHER)
                        .withStyle(ChatFormatting.GREEN),
                true);
        // 女巫获取素材无声音
    }

    /**
     * 切换药水（蹲下按技能键）
     */
    public void switchPotion() {
        currentPotionIndex = (currentPotionIndex + 1) % POTION_COUNT;

        if (player instanceof ServerPlayer serverPlayer) {
            Component potionName = getPotionName(currentPotionIndex);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.witch.potion_selected", potionName)
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }

        sync();
    }

    /**
     * 炼制当前药水（直接按技能键）
     */
    public void craftPotion() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查当前药水的炼制次数
        if (potionCraftCounts[currentPotionIndex] >= MAX_CRAFT_COUNT) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.witch.max_craft_reached")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查素材是否足够
        int materialCost = MATERIALS_TO_CRAFT[currentPotionIndex];
        int materialCount = countMaterials();
        if (materialCount < materialCost) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.witch.insufficient_materials", materialCost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查金币是否足够
        int goldCost = getPotionCost(currentPotionIndex);
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < goldCost) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.witch.insufficient_gold", goldCost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 消耗素材
        removeMaterials(materialCost);

        // 扣除金币
        shopComponent.balance -= goldCost;
        shopComponent.sync();

        // 给予药水
        ItemStack potion = getPotionItemStack(currentPotionIndex);
        if (!player.getInventory().add(potion)) {
            player.drop(potion, false);
        }

        // 增加炼制次数
        potionCraftCounts[currentPotionIndex]++;

        // 通知玩家
        Component potionName = getPotionName(currentPotionIndex);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.witch.potion_crafted", potionName)
                        .withStyle(ChatFormatting.GOLD),
                true);

        sync();
    }

    public int getCurrentPotionIndex() {
        return currentPotionIndex;
    }

    public int getMaterialGatherRemainingSeconds() {
        return (materialGatherTimer + 19) / 20;
    }

    public int getPotionCraftCount(int potionIndex) {
        if (potionIndex < 0 || potionIndex >= POTION_COUNT) {
            return 0;
        }
        return potionCraftCounts[potionIndex];
    }

    public int getCurrentPotionCraftCount() {
        return potionCraftCounts[currentPotionIndex];
    }

    private int countMaterials() {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ALCHEMY_MATERIAL)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeMaterials(int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ALCHEMY_MATERIAL) && remaining > 0) {
                int stackCount = stack.getCount();
                if (stackCount <= remaining) {
                    remaining -= stackCount;
                    stack.setCount(0);
                } else {
                    stack.shrink(remaining);
                    remaining = 0;
                }
            }
        }
    }

    private Component getPotionName(int potionIndex) {
        return switch (potionIndex) {
            case POTION_SPEED -> Component.translatable("potion.noellesroles.witch_speed");
            case POTION_SLOWNESS -> Component.translatable("potion.noellesroles.witch_slowness");
            case POTION_HASTE -> Component.translatable("potion.noellesroles.witch_haste");
            case POTION_INVISIBILITY -> Component.translatable("potion.noellesroles.witch_invisibility");
            case POTION_BLIND_DARK -> Component.translatable("potion.noellesroles.witch_blind_dark");
            case POTION_TURN_WEAK -> Component.translatable("potion.noellesroles.witch_turn_weak");
            case POTION_USED_BANED -> Component.translatable("potion.noellesroles.witch_used_baned");
            default -> Component.translatable("potion.noellesroles.unknown");
        };
    }

    /**
     * 获取药水的炼制金币花费
     */
    public static int getPotionCost(int potionIndex) {
        return switch (potionIndex) {
            case POTION_SPEED -> 150;          // 速度1 15s
            case POTION_SLOWNESS -> 100;       // 缓慢2 10s
            case POTION_HASTE -> 200;          // 急迫2 10s
            case POTION_INVISIBILITY -> 200;   // 隐身1 5s
            case POTION_BLIND_DARK -> 150;     // 失明1+黑暗1 8s
            case POTION_TURN_WEAK -> 150;      // 转向受限 8s
            case POTION_USED_BANED -> 150;     // 按键禁用 3s
            default -> 0;
        };
    }

    /**
     * 获取药水的素材花费
     */
    public static int getMaterialCost(int potionIndex) {
        if (potionIndex < 0 || potionIndex >= POTION_COUNT) {
            return 1;
        }
        return MATERIALS_TO_CRAFT[potionIndex];
    }

    /**
     * 获取药水的key（用于翻译）
     */
    public static String getPotionKey(int potionIndex) {
        return switch (potionIndex) {
            case POTION_SPEED -> "witch_speed";
            case POTION_SLOWNESS -> "witch_slowness";
            case POTION_HASTE -> "witch_haste";
            case POTION_INVISIBILITY -> "witch_invisibility";
            case POTION_BLIND_DARK -> "witch_blind_dark";
            case POTION_TURN_WEAK -> "witch_turn_weak";
            case POTION_USED_BANED -> "witch_used_baned";
            default -> "unknown";
        };
    }

    /**
     * 获取药水的物品栈（使用原版喷溅药水 + 自定义PotionContents）
     */
    public static ItemStack getPotionItemStack(int potionIndex) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        List<MobEffectInstance> effects;
        int color;
        String nameKey;

        switch (potionIndex) {
            case POTION_SPEED -> {
                effects = List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15 * 20, 0, false, true, true));
                color = 0x7CAFC6;
                nameKey = "item.noellesroles.witch_potion_speed";
            }
            case POTION_SLOWNESS -> {
                effects = List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 1, false, true, true));
                color = 0x5A6E82;
                nameKey = "item.noellesroles.witch_potion_slowness";
            }
            case POTION_HASTE -> {
                effects = List.of(new MobEffectInstance(MobEffects.DIG_SPEED, 10 * 20, 1, false, true, true));
                color = 0xD9C043;
                nameKey = "item.noellesroles.witch_potion_haste";
            }
            case POTION_INVISIBILITY -> {
                effects = List.of(new MobEffectInstance(MobEffects.INVISIBILITY, 5 * 20, 0, false, true, true));
                color = 0x7F83FF;
                nameKey = "item.noellesroles.witch_potion_invisibility";
            }
            case POTION_BLIND_DARK -> {
                effects = List.of(
                        new MobEffectInstance(MobEffects.BLINDNESS, 8 * 20, 0, false, true, true),
                        new MobEffectInstance(MobEffects.DARKNESS, 8 * 20, 0, false, true, true));
                color = 0x1F1F2E;
                nameKey = "item.noellesroles.witch_potion_blind_dark";
            }
            case POTION_TURN_WEAK -> {
                effects = List.of(new MobEffectInstance(ModEffects.TURN_WEAK, 8 * 20, 0, false, true, true));
                color = 0xCC8800;
                nameKey = "item.noellesroles.witch_potion_turn_weak";
            }
            case POTION_USED_BANED -> {
                effects = List.of(new MobEffectInstance(ModEffects.USED_BANED, 3 * 20, 0, false, true, true));
                color = 0x990000;
                nameKey = "item.noellesroles.witch_potion_used_baned";
            }
            default -> {
                return ItemStack.EMPTY;
            }
        }

        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(), Optional.of(color), effects));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
        return stack;
    }

    /**
     * 同步组件数据到客户端
     */
    public void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }

    @Override
    public void clientTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.WITCH))
            return;

        // 客户端蹲下计时器（与服务端同步）
        if (player.isShiftKeyDown()) {
            if (materialGatherTimer > 0) {
                materialGatherTimer--;
            }
        } else {
            materialGatherTimer = MATERIAL_GATHER_INTERVAL;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
