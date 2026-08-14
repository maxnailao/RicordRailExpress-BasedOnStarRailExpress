package org.agmas.noellesroles.game.roles.killer.killman;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 诱杀者组件 - 杀手阵营
 *
 * 技能"诱杀左轮"：
 * - 花费75金币在原地生成一个带标记的左轮手枪掉落物
 * - 捡起该左轮手枪的玩家即视为被标记（标记由背包中是否持有标记左轮推导，
 *   玩家背包内不再有标记左轮时标记自动清空）
 * - 被标记的玩家开枪后：先清除其背包内所有左轮手枪，再以死因"手枪炸膛"击杀，
 *   避免死亡后掉落左轮手枪
 * - 技能CD 100s（由统一技能系统管理）
 * - 每局开始/结束时清理场上残留的诱杀左轮掉落物并重置状态
 */
public class KillmanPlayerComponent implements RoleComponent {

    public static final ComponentKey<KillmanPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "youshazhe_killman"),
            KillmanPlayerComponent.class);

    // ==================== 常量定义 ====================

    /** 技能花费（金币） */
    public static final int SKILL_COST = 75;

    /** 诱杀左轮的自定义数据标记键 */
    public static final String TAG_TRAP_REVOLVER = "killman_trap_revolver";

    /** 死因：手枪炸膛 */
    public static final ResourceLocation DEATH_REASON_REVOLVER_BURST = Noellesroles.id("killman_revolver_burst");

    /** 掉落物拾取延迟（tick），防止放置者立刻捡回 */
    private static final int DROP_PICKUP_DELAY = 40;

    static {
        // 开枪触雷：被标记的玩家开枪后清除左轮并以"手枪炸膛"击杀
        // （注册在组件 static 块，类被 ModComponents 引用时即加载生效，不依赖外部注册入口）
        OnRevolverUsed.EVENT.register((shooter, target) -> handleTrapShot(shooter));

        // 游戏结束时重置：清除场上残留的诱杀左轮掉落物并清空标记记录；
        // 额外扫除所有玩家背包中残留的诱杀左轮（兼容被标记者离线等边缘情况，确保每局彻底重置）
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (ServerPlayer p : serverLevel.players()) {
                KillmanPlayerComponent comp = KEY.maybeGet(p).orElse(null);
                if (comp != null) {
                    comp.clear();
                }
                SREItemUtils.clearItem(p, KillmanPlayerComponent::isTrapRevolver);
            }
        });
    }

    // ==================== 状态变量 ====================

    private final Player player;

    /** 本局已放置的诱杀左轮掉落物实体 UUID（用于每局结束时清理） */
    private final Set<UUID> spawnedTrapEntities = new HashSet<>();

    public KillmanPlayerComponent(Player player) {
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
        discardSpawnedTraps();
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 清理本局放置的诱杀左轮掉落物并重置记录 */
    private void discardSpawnedTraps() {
        if (!spawnedTrapEntities.isEmpty() && player.level() instanceof ServerLevel level) {
            for (UUID uuid : spawnedTrapEntities) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof ItemEntity itemEntity && !itemEntity.isRemoved()) {
                    itemEntity.discard();
                }
            }
        }
        spawnedTrapEntities.clear();
    }

    // ==================== 技能逻辑 ====================

    /**
     * 使用诱杀左轮技能：花费75金币在原地生成一个带标记的左轮手枪掉落物
     *
     * @return 是否成功使用（成功才进入冷却）
     */
    public boolean useTrapRevolver() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer))
            return false;

        // 验证角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (gameWorld.getRole(serverPlayer.getUUID()) != ModRoles.KILLMAN)
            return false;

        // 检查金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(serverPlayer);
        if (shop.balance < SKILL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.killman.not_enough_money")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-SKILL_COST);

        // 生成带标记的左轮手枪掉落物
        ServerLevel level = serverPlayer.serverLevel();
        ItemStack stack = TMMItems.REVOLVER.getDefaultInstance();
        markAsTrapRevolver(stack);
        ItemEntity itemEntity = new ItemEntity(level,
                serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), stack);
        itemEntity.setPickUpDelay(DROP_PICKUP_DELAY);
        level.addFreshEntity(itemEntity);
        spawnedTrapEntities.add(itemEntity.getUUID());

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.killman.trap_placed")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
        return true;
    }

    // ==================== 静态工具方法（标记与触雷） ====================

    /** 给左轮手枪物品打上诱杀标记 */
    public static void markAsTrapRevolver(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG_TRAP_REVOLVER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 判断物品是否为诱杀左轮 */
    public static boolean isTrapRevolver(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(TMMItems.REVOLVER))
            return false;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(TAG_TRAP_REVOLVER);
    }

    /**
     * 判断玩家是否被标记（背包/副手等任意槽位持有诱杀左轮即视为被标记）。
     * 玩家丢掉/失去诱杀左轮后标记自动清空。
     */
    public static boolean isMarked(Player player) {
        return SREItemUtils.hasItem(player, KillmanPlayerComponent::isTrapRevolver);
    }

    /**
     * 开枪触雷处理（OnRevolverUsed 事件调用）：
     * 被标记的玩家开枪后，先清除背包内所有左轮手枪，再以死因"手枪炸膛"击杀，
     * 避免死亡后掉落左轮手枪。
     */
    public static void handleTrapShot(ServerPlayer shooter) {
        if (shooter == null)
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(shooter))
            return;
        if (!isMarked(shooter))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(shooter.level());
        if (!gameWorld.isRunning())
            return;

        // 先清除背包内所有左轮手枪，避免死亡后掉落
        SREItemUtils.clearItem(shooter, stack -> stack.is(TMMItems.REVOLVER));
        // 以死因"手枪炸膛"击杀
        GameUtils.killPlayer(shooter, true, null, DEATH_REASON_REVOLVER_BURST);
    }

    // ==================== NBT 读写 ====================

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        // 掉落物记录仅服务端使用，无需同步
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
