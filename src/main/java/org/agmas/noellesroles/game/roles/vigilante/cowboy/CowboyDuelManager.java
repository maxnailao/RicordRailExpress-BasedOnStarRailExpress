package org.agmas.noellesroles.game.roles.vigilante.cowboy;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 西部牛仔的「决斗领域」。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>牛仔在背包界面（按 E）点选一名玩家，花 {@link #COST} 金币发起决斗；</li>
 *   <li>双方被传送到领域建筑的两个预设点位（{@link #COWBOY_SPAWN} / {@link #TARGET_SPAWN}），
 *       背靠背、互相看不见；</li>
 *   <li>背包被替换为单一左轮手枪，体力无限、获得夜视，视角被锁定
 *       （{@link ModEffects#TURN_BANED}）；</li>
 *   <li>{@link #LOCK_TICKS} 后解除锁定，进入 1v1；</li>
 *   <li>先被击中者判负 → 双方退出领域，败者 1 秒后被"巫毒强制杀死"，胜者获得一层护盾（不可叠加）。</li>
 * </ol>
 *
 * <h2>场地坐标</h2>
 * 领域建筑请自行搭建在远处虚空（与咒术师领域 {@code WarlockDomainManager} 同思路），
 * 然后把两组坐标填到下面的常量里。<b>位置完全由这里决定，代码不生成任何方块。</b>
 * 默认值是一个 200 格的虚空平台，仅用于跑通流程，正式场地请替换。
 */
public final class CowboyDuelManager {

    private CowboyDuelManager() {
    }

    // ════════════════════════════════════════════════════════════════════════
    // 一、场地配置
    // ════════════════════════════════════════════════════════════════════════
    //
    // ┌─────────────────────────────────────────────────────────────────────┐
    // │  改这里一行就能在三种场地之间切换：                                   │
    // │                                                                     │
    // │     ARENA_MODE = 0  →  自动生成草方块平台（开箱即用）                │
    // │     ARENA_MODE = 1  →  结构模板（NBT）：代码把你搭的建筑生成出来      │
    // │     ARENA_MODE = 2  →  纯世界坐标（你自己填 COWBOY_SPAWN 等）         │
    // └─────────────────────────────────────────────────────────────────────┘
    //
    // ★ 推荐 ARENA_MODE = 1：你把决斗场搭一次、存成 .nbt，
    //   之后代码自动在锚点生成，**世界坐标完全不用填**，只需填两个"相对锚点的偏移"。

    /**
     * 场地模式。
     *
     * <ul>
     *   <li>{@code 0} —— <b>自动生成</b>：在 {@link #COWBOY_SPAWN} 处铺一块草方块平台。无需准备任何东西。</li>
     *   <li>{@code 1} —— <b>单块结构模板</b>：从 {@link #ARENA_STRUCTURE} 读取 .nbt 并在
     *       {@link #ARENA_ORIGIN} 生成；入场点用 {@link #STRUCT_COWBOY_OFFSET} /
     *       {@link #STRUCT_TARGET_OFFSET}（相对结构原点的偏移）。</li>
     *   <li>{@code 2} —— <b>纯世界坐标</b>：直接用 {@link #COWBOY_SPAWN} / {@link #TARGET_SPAWN}。</li>
     *   <li>{@code 3} —— <b>分块结构模板（大场地）</b>：结构超过结构方块 48 的上限，
     *       切成若干块分别导出，用 {@link #ARENA_PARTS} 列出「每块的 id + 相对锚点的偏移」。
     *       入场点由 {@link #SPAWN_COWBOY_BLOCK} / {@link #SPAWN_TARGET_BLOCK}
     *       直接给出 —— 它们就是<b>你用来标记站位的那个方块的坐标</b>（本场地是灰化土）。</li>
     * </ul>
     */
    public static int ARENA_MODE = 3;

    // ══════════════════════════════════════════════════════════════════════
    //  ARENA_MODE = 3：分块结构模板（结构 > 48 ³ 时用这个）
    // ══════════════════════════════════════════════════════════════════════
    //
    //  工作流：
    //   1. 结构方块 48 是硬上限（1.21.1 没有放宽的 gamerule），大场地只能切块存。
    //   2. 每块用结构方块 SAVE 一次，得到 arena1.nbt / arena2.nbt …
    //      放进 src/main/resources/data/noellesroles/structure/
    //   3. 在下面 ARENA_PARTS 里列出每块的 id 与「相对 ARENA_ORIGIN 的偏移」。
    //      偏移 = 该块在原场地坐标系里的起点。
    //   4. 站位用两种方式之一给：
    //        (a) SPAWN_COWBOY_BLOCK / SPAWN_TARGET_BLOCK：站位的方块坐标（推荐，精确）
    //        (b) 留空 —— 代码会自动扫描结构里所有 PODZOL（灰化土）并取前两个
    //   5. 在场地里放一个特殊方块标出站位（本场地用的是灰化土 podzol）。

    /**
     * 结构分块列表。每块 = 模板 id + 相对 {@link #ARENA_ORIGIN} 的偏移。
     *
     * <p>偏移的 y 一般填 0；z 同理。相邻块的起点要严格等于「前一块起点 + 前一块尺寸」，
     * 否则接缝会出现缝隙或重叠。
     */

    /** 一块结构：模板 id + 相对锚点的偏移。 */
    public static final class Part {
        public final ResourceLocation id;
        public final net.minecraft.core.BlockPos offset;

        public Part(String path, net.minecraft.core.BlockPos offset) {
            this.id = ResourceLocation.fromNamespaceAndPath("noellesroles", path);
            this.offset = offset;
        }
    }

    /**
     * 决斗场的分块清单。
     *
     * <p>当前配置来自两块实际导出的 nbt：
     * <pre>
     *   cowboy_arena1.nbt  尺寸 48×24×33   偏移 (0, 0, 0)
     *   cowboy_arena2.nbt  尺寸 28×24×33   偏移 (48, 0, 0)   ← 接在第 1 块右边
     *   ⇒ 拼合后整体 76×24×33
     * </pre>
     */
    public static final Part[] ARENA_PARTS = {
            new Part("cowboy_arena1", new net.minecraft.core.BlockPos(0, 0, 0)),
            new Part("cowboy_arena2", new net.minecraft.core.BlockPos(48, 0, 0)),
    };

    /**
     * 站位标记方块（默认灰化土）。代码会扫描结构里所有该方块，按坐标排序后取前两个作为站位。
     *
     * <p>留空不填站位坐标时才会走这个自动扫描。
     */
    public static final String SPAWN_MARKER_BLOCK = "minecraft:podzol";

    /**
     * 牛仔站位的方块坐标（<b>结构坐标系</b>，即相对 {@link #ARENA_ORIGIN} 的偏移）。
     *
     * <p>实测自 cowboy_arena1.nbt：灰化土在局部 (6, 5, 23)，该块偏移为 (0,0,0) → 全局 (6, 5, 23)。
     * 玩家脚底站在该方块上方，所以实际传送 y = 5 + 1 = 6。
     *
     * <p>设为 {@code null} 则改为自动扫描 {@link #SPAWN_MARKER_BLOCK}。
     */
    public static net.minecraft.core.BlockPos SPAWN_COWBOY_BLOCK =
            new net.minecraft.core.BlockPos(6, 5, 23);

    /**
     * 对手站位的方块坐标（结构坐标系）。
     *
     * <p>实测自 cowboy_arena2.nbt：灰化土在局部 (21, 5, 23)，该块偏移为 (48,0,0)
     * → 全局 (69, 5, 23)。
     */
    public static net.minecraft.core.BlockPos SPAWN_TARGET_BLOCK =
            new net.minecraft.core.BlockPos(69, 5, 23);


    // ══════════════════════════════════════════════════════════════════════
    //  ARENA_MODE = 1：结构模板（NBT）
    // ══════════════════════════════════════════════════════════════════════
    //
    //  使用步骤：
    //   1. 创造模式搭好决斗场（建议包含地面、围墙、中间掩体）。
    //   2. 用 WorldEdit 选区 → //copy → //schem save cowboy_arena
    //      （或用结构方块保存），得到一个 .nbt 文件。
    //   3. 放到：src/main/resources/data/noellesroles/structure/cowboy_arena.nbt
    //      （文件名要与下面的 ARENA_STRUCTURE 对应；1.21 的目录名是单数 structure）
    //   4. 重建 mod、进游戏，把 ARENA_MODE 改成 1 即可。
    //
    //  两个入场点由"相对结构原点的偏移"决定，不用填世界坐标：
    //    · 结构原点 = 你 /copy 时选区的西北下角（WorldEdit 的 //copy 以你选区 min 角为原点）。
    //    · 所以偏移 = (站位坐标 - 选区 min 角坐标)。
    //    · 如果你懒得算，先随便填 (0,1,0) 和 (0,1,24) 开一局，
    //      代码会把实际生成的坐标打进日志，你照着挪偏移即可。

    /**
     * 结构模板 id。
     *
     * <p>对应文件 {@code data/noellesroles/structure/cowboy_arena.nbt}
     *（若用 WorldEdit 的 {@code //schem save}，可能是 {@code data/noellesroles/structure/cowboy_arena.nbt} 或
     * {@code structures/} 复数目录 —— 不同版本导出路径不同，以实际为准）。
     */
    public static ResourceLocation ARENA_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "cowboy_arena");

    /**
     * 结构生成的锚点（世界坐标）。结构以这个点作为<b>原点</b>放置。
     *
     * <p>建议放在远处虚空（与咒术师领域同思路），避免和正常地图重叠。
     */
    public static net.minecraft.core.BlockPos ARENA_ORIGIN =
            new net.minecraft.core.BlockPos(0, 200, -21000);

    /** 牛仔入场点相对结构原点的偏移（脚底那一格）。 */
    public static net.minecraft.core.BlockPos STRUCT_COWBOY_OFFSET =
            new net.minecraft.core.BlockPos(0, 1, 0);

    /** 对手入场点相对结构原点的偏移（脚底那一格）。 */
    public static net.minecraft.core.BlockPos STRUCT_TARGET_OFFSET =
            new net.minecraft.core.BlockPos(0, 1, 24);

    // ══════════════════════════════════════════════════════════════════════
    //  ARENA_MODE = 2：纯世界坐标
    // ══════════════════════════════════════════════════════════════════════

    /** 场地所在维度：{@code null} = 与发起者同一维度（推荐，省去跨维度处理）。 */
    public static ServerLevel CUSTOM_LEVEL = null;

    /**
     * 牛仔的入场点（x, y, z）。y 是从脚算起的高度。
     *
     * <p>牛仔在 -Z 侧，对手在 +Z 侧。
     */
    public static Vec3 COWBOY_SPAWN = new Vec3(0.5D, 200.0D, -21012.5D);

    /** 对手的入场点。 */
    public static Vec3 TARGET_SPAWN = new Vec3(0.5D, 200.0D, -20988.5D);

    /**
     * 场地活动范围（越界会被拉回）。中心自动取两入场点的中点，半径就是本值。
     *
     * <p><b>务必让这个值不超过地面的实际半径</b>，否则玩家会被拉回到没有方块的位置。
     * {@link #ARENA_MODE} = 0 时默认 22，比自动平台的半径 40 小很多，够安全。
     */
    public static double BOUND_RADIUS = 22.0D;
    /** 活动范围高度（中心上下各半）。 */
    public static double BOUND_HEIGHT = 8.0D;

    /**
     * 牛仔朝向：<b>背对</b>对手（开局背靠背，5 秒后自己转身）。
     *
     * <p>用 {@link #yawFacing} 从坐标推导，而不是手写魔数：
     * yaw 约定为「yaw 增加 = 顺时针（俯视）」，{@code yaw=0} 面向 +Z。
     *
     * <p>{@code + 180} 是"背靠背"的关键：{@link #yawFacing} 得到的是**正对**对方的朝向，
     * 加 180 才是背对。这个翻转对 X 轴布局和 Z 轴布局都成立
     *（已用 {@code dir = (-sin(yaw), cos(yaw))} 逐轴验算）。
     */
    public static float COWBOY_YAW = yawFacing(COWBOY_SPAWN, TARGET_SPAWN) + 180.0F;

    /** 对手朝向：同样<b>背对</b>牛仔。 */
    public static float TARGET_YAW = yawFacing(TARGET_SPAWN, COWBOY_SPAWN) + 180.0F;

    /**
     * 求「站在 {@code from} 看向 {@code to}」所需的 yaw。
     *
     * <p>公式与 {@code WarlockDomainManager.pullIn} 一致（那里是既有、已验证的用法）：
     * {@code yaw = atan2(dz, dx) * 180/π - 90}。
     * 验证：目标在 +Z 方向时算出 0（面向 +Z），在 +X 方向时算出 -90（面向 +X），符合游戏约定。
     */
    public static float yawFacing(Vec3 from, Vec3 to) {
        return (float) (Math.atan2(to.z - from.z, to.x - from.x) * 180.0D / Math.PI) - 90.0F;
    }

    // ── ARENA_MODE = 0 时自动平台的参数 ────────────────────────────────────

    /** 临时平台半径（格）：以 {@link #COWBOY_SPAWN} 为中心。 */
    private static final int TEMP_PLATFORM_RADIUS = 40;
    /** 临时平台厚度（格，向下）。 */
    private static final int TEMP_PLATFORM_THICKNESS = 2;
    /** 中间隔墙高度（格）。 */
    private static final int TEMP_WALL_HEIGHT = 4;
    /** 隔墙沿 X 方向的半长。 */
    private static final int TEMP_WALL_HALF_LENGTH = 6;
    /** 两堵隔墙之间留的通道半宽（格）。 */
    private static final int TEMP_WALL_GAP = 3;

    /** 建立过临时平台的维度，避免每次都重新铺。 */
    private static final Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> PLATFORM_BUILT =
            new java.util.HashSet<>();

    // ════════════════════════════════════════════════════════════════════════
    // 二、规则参数
    // ════════════════════════════════════════════════════════════════════════

    /** 发起决斗的金币花费。 */
    public static final int COST = 200;
    /**
     * 可发起决斗的最大距离（格）。超出这个距离的玩家不会出现在背包选人界面里，
     * 服务端也会拒绝 —— 客户端只做显示过滤，真正的判定在这里。
     */
    public static final double RANGE = 25.0D;
    /** 锁视角时长（tick）：5 秒后放开。 */
    public static final int LOCK_TICKS = 20 * 5;
    /** 败者被判负后，延迟多久被强制杀死（tick）：1 秒。 */
    public static final int DEATH_DELAY_TICKS = 20;
    /** 决斗最长时长（tick），超时判平局（双方都安全退出）：60 秒。 */
    public static final int MAX_DUEL_TICKS = 20 * 60;
    /** 发起决斗后的冷却（tick）：60 秒。 */
    public static final int COOLDOWN_TICKS = 20 * 60;

    // ════════════════════════════════════════════════════════════════════════
    // 三、运行时状态
    // ════════════════════════════════════════════════════════════════════════

    /** 进行中的决斗：按参与者索引，方便 O(1) 查询"这人是否在决斗中"。 */
    private static final Map<UUID, Duel> BY_PARTICIPANT = new HashMap<>();

    private static boolean registered = false;

    /** 一次决斗的运行时状态。 */
    private static final class Duel {
        final UUID cowboyId;
        final UUID targetId;
        /** 每个参与者的完整入场前快照，退出时原样还原。 */
        final Map<UUID, Snapshot> snapshots = new LinkedHashMap<>();
        int ticksLeft = LOCK_TICKS;
        boolean resolved = false;

        Duel(UUID cowboyId, UUID targetId) {
            this.cowboyId = cowboyId;
            this.targetId = targetId;
        }
    }

    /**
     * 参与者进入领域前的完整状态快照。
     *
     * <p>退出时按这份快照**整体还原**，而不是"只把决斗临时加的东西撤掉" —— 后者会把玩家
     * 原本就有的同类状态（比如入场前就挂着的夜视、速度）一起清掉。
     */
    private static final class Snapshot {
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final ItemStack[] inventory;
        final List<MobEffectInstance> effects;
        final float health;
        final int foodLevel;
        final float saturation;

        Snapshot(ServerPlayer p) {
            this.level = p.serverLevel();
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.yaw = p.getYRot();
            this.pitch = p.getXRot();
            this.inventory = snapshotInventory(p);
            this.effects = new ArrayList<>(p.getActiveEffects());
            this.health = p.getHealth();
            this.foodLevel = p.getFoodData().getFoodLevel();
            this.saturation = p.getFoodData().getSaturationLevel();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 四、注册
    // ════════════════════════════════════════════════════════════════════════

    /** 在职业初始化阶段调用一次：挂全局 tick、死亡否决、掉落否决、以及游戏结束兜底清理。 */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(CowboyDuelManager::tickAll);
        AllowPlayerDeathWithKiller.EVENT.register(CowboyDuelManager::allowDeath);
        // 「用枪击杀平民」的通用惩罚（掉枪 + 理智清零，见 GunShootPayload）必须对决斗豁免，
        // 否则牛仔打赢一场决斗就会掉枪并 san 归零。用既有的掉落决策事件拦掉即可。
        io.wifi.starrailexpress.event.AllowShootRevolverDrop.EVENT.register(
                (shooter, target) -> {
                    if (shooter != null && target != null
                            && isInDuel(shooter.getUUID()) && isInDuel(target.getUUID())) {
                        return io.wifi.starrailexpress.util.TrueFalseResult.FALSE;
                    }
                    return io.wifi.starrailexpress.util.TrueFalseResult.PASS;
                });
        // 一局结束时把还留在领域里的双方送回原位（否则下一局会出生在虚空）。
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register(
                (serverLevel, gameWorldComponent) -> forceEndAll());
        // 雪茄 30 秒冷却：cigar 是别的模组的物品（watheextraitems:cigar），
        // 无法改它的 use()，所以在使用前用 UseItemCallback 拦一道。
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(
                (player, level, hand) -> {
                    if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
                        return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
                    }
                    var stack = sp.getItemInHand(hand);
                    if (!isCigar(stack)) {
                        return net.minecraft.world.InteractionResultHolder.pass(stack);
                    }
                    // 非牛仔不干预（别的职业捡到雪茄照常能用）
                    SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
                    if (game == null || !game.isRunning()
                            || !game.isRole(sp, ModRoles.NIUZAI_JUEDOUBA)) {
                        return net.minecraft.world.InteractionResultHolder.pass(stack);
                    }
                    CowboyPlayerComponent comp = CowboyPlayerComponent.KEY.maybeGet(sp).orElse(null);
                    if (comp == null) {
                        return net.minecraft.world.InteractionResultHolder.pass(stack);
                    }
                    if (!comp.isCigarReady()) {
                        sp.displayClientMessage(
                                Component.translatable(
                                        "message.noellesroles.niuzai_juedouba.cigar_cooldown",
                                        (comp.cigarCooldownTicks + 19) / 20)
                                        .withStyle(ChatFormatting.RED),
                                true);
                        // 返回 SUCCESS 吃掉这次交互：既不消耗雪茄，也不让物品逻辑跑
                        return net.minecraft.world.InteractionResultHolder.success(stack);
                    }
                    comp.startCigarCooldown();
                    // 同步到原版物品冷却：雪茄图标、商店条目上的冷却条读的是
                    // ItemCooldowns（由服务端 addCooldown 发包给客户端后自行递减），
                    // 只设组件字段的话那条进度条不会动，看上去就是"一直 30 秒"。
                    sp.getCooldowns().addCooldown(stack.getItem(),
                            CowboyPlayerComponent.CIGAR_COOLDOWN_TICKS);
                    // 抽完给速度 I。
                    // wathe-extra-items 的 ConsumableCigarItem 只做了烟雾粒子 + 音效 + 自带冷却，
                    // **没有任何增益效果**，所以"抽雪茄加速"必须由我们补上。
                    sp.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED, CIGAR_SPEED_TICKS, CIGAR_SPEED_AMPLIFIER,
                            false, true, true));
                    return net.minecraft.world.InteractionResultHolder.pass(stack);
                });
    }

    /** 雪茄 id：来自 libs 里的 wathe-extra-items（mod id {@code watheextraitems}）。 */
    public static final ResourceLocation CIGAR_ID =
            ResourceLocation.fromNamespaceAndPath("watheextraitems", "cigar");

    /** 抽雪茄获得的速度效果时长（tick）：5 秒。 */
    public static final int CIGAR_SPEED_TICKS = 20 * 5;
    /** 速度 I。 */
    public static final int CIGAR_SPEED_AMPLIFIER = 0;

    /** 玩家背包里是否已经有雪茄（用于商店限购 1 个）。 */
    public static boolean hasCigar(Player player) {
        var item = cigarItem();
        if (item == null || player == null) {
            return false;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 是否已经就「装了但不含雪茄」告警过（避免刷屏）。 */
    private static boolean cigarMissingWarned = false;

    /**
     * 兼容性取法：该模组未安装时返回 {@code null}，不让调用方崩。
     *
     * <p>注意：这里**没有编译期依赖**，纯按 id 查注册表 ——
     * 所以即使 {@code watheextraitems} 不在 classpath 也能编译。
     * 但**运行环境必须装**这个模组，否则牛仔商店里不会出现雪茄。缺了会打一条错误日志。
     */
    public static net.minecraft.world.item.Item cigarItem() {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(CIGAR_ID).orElse(null);
        if (item == null && !cigarMissingWarned) {
            cigarMissingWarned = true;
            SRE.LOGGER.error(
                    "[CowboyDuel] 找不到物品 {} —— 请确认已安装 Wathe Extra Items "
                            + "(libs/1_wathe-extra-items-*.jar)。缺了它牛仔商店不会出现雪茄。",
                    CIGAR_ID);
        }
        return item;
    }

    /** 判断是不是雪茄（该模组未安装时恒为 false）。 */
    public static boolean isCigar(net.minecraft.world.item.ItemStack stack) {
        var item = cigarItem();
        return item != null && stack.is(item);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 五、对外查询
    // ════════════════════════════════════════════════════════════════════════

    public static boolean isInDuel(UUID playerId) {
        return playerId != null && BY_PARTICIPANT.containsKey(playerId);
    }

    /**
     * 该玩家此刻是否应享有「决斗领域内的环境致死免疫」。
     *
     * <p>用途见 {@code CowboyDuelDeathImmunityMixin}：领域位于远处高空/地下，
     * {@code SREGameWorldComponent.isPlayerOutGameAreas} 每 tick 检查
     * {@code player.getY() < areas.playArea.minY}，一旦成立就用
     * {@code FELL_OUT_OF_TRAIN} 强杀（还会 follow-up 一次 forceKill）。
     * 领域里只有"被列车碾压"这一个死法显然不对，所以要在这个入口兜住。
     *
     * <p>三种情形都算"在决斗场里"，任一命中即免疫：
     * <ol>
     *   <li>正在决斗（{@link #BY_PARTICIPANT} 里有记录）；</li>
     *   <li>位置落在领域活动范围内（覆盖"刚被拉进去、记录还没写入"的一瞬间）；</li>
     *   <li>刚被判负、正在被钉住等待处决（见 {@link #PENDING_EXECUTION}）。</li>
     * </ol>
     */
    public static boolean hasDuelEnvironmentImmunity(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUUID();
        if (isInDuel(id) || PENDING_EXECUTION.containsKey(id)) {
            return true;
        }
        // 位置兜底：覆盖"刚被拉进去、记录还没写入"的缝隙。
        // arenaBounds 的 level 参数其实没被用到（只算坐标），所以这里用 player.level() 即可。
        return arenaBounds().inflate(2.0D).contains(player.position());
    }

    /**
     * 判负后正在等待处决的玩家 → 剩余 tick。
     *
     * <p>这 1 秒里玩家已经被送回原位、也早已从 {@link #BY_PARTICIPANT} 移除，
     * 但被 MOVE_BANED 钉在原地，不该再被环境判定插一脚（否则会以
     * {@code fell_out_of_train} 而不是 {@code cowboy_duel} 的名义死掉，死因就错了）。
     */
    private static final Map<UUID, Integer> PENDING_EXECUTION = new HashMap<>();

    public static boolean isDuelOpponent(ServerPlayer cowboy, ServerPlayer target) {
        Duel duel = BY_PARTICIPANT.get(cowboy.getUUID());
        return duel != null && duel.targetId.equals(target.getUUID());
    }

    /**
     * 这次击杀是否属于决斗范畴 —— 供小脑惩罚等"误杀"判定排除决斗。
     *
     * <p>决斗是牛仔主动发起的合法对决，被拉进来的人本来就可能是平民，
     * 打中他是**规则内的胜负判定**，不该按"误杀平民"处理。
     *
     * <p>覆盖三种情形，任一命中即返回 true：
     * <ol>
     *   <li>死亡原因就是决斗处决（{@code cowboy_duel}）；</li>
     *   <li>开火方正在决斗中；</li>
     *   <li>被击杀方正在决斗中。</li>
     * </ol>
     * 后两条是必要的：决斗中的命中会被拦截、延迟 1 秒后才用决斗原因处决，
     * 所以处决那一刻双方**已经退出领域**，只靠死亡原因判断会漏掉第一枪。
     */
    public static boolean isDuelRelated(Player shooter, Player victim, ResourceLocation deathReason) {
        if (deathReason != null
                && GameConstants.DeathReasons.COWBOY_DUEL.getPath().equals(deathReason.getPath())) {
            return true;
        }
        return (shooter != null && isInDuel(shooter.getUUID()))
                || (victim != null && isInDuel(victim.getUUID()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 六、场地准备
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 按 {@link #ARENA_MODE} 准备场地。
     *
     * <ul>
     *   <li>{@code 0}：必要时铺临时草方块平台。</li>
     *   <li>{@code 1}：生成 {@link #ARENA_STRUCTURE} 结构模板，并把两个入场点
     *       从"相对偏移"换算成世界坐标写回 {@link #COWBOY_SPAWN} / {@link #TARGET_SPAWN}。</li>
     *   <li>{@code 2}：什么都不做（坐标由你自己填）。</li>
     * </ul>
     */
    public static boolean ensureArena(ServerLevel level) {
        if (ARENA_MODE == 1 || ARENA_MODE == 3) {
            return prepareStructureArena(level);
        }
        // ARENA_MODE = 0（自动平台）/ 2（纯坐标）：只有 0 需要建东西
        if (ARENA_MODE != 0) {
            return true;
        }
        if (!PLATFORM_BUILT.add(level.dimension())) {
            return true; // 本维度已铺过
        }
        buildTempPlatform(level);
        return true;
    }

    /** 铺临时草方块平台（含中间两堵隔墙）。调用方负责去重。 */
    private static void buildTempPlatform(ServerLevel level) {
        BlockPos feet = BlockPos.containing(COWBOY_SPAWN.x, COWBOY_SPAWN.y, COWBOY_SPAWN.z);
        // 脚下已有一层实体方块（说明是真实建筑 / 天然地形）→ 不铺
        if (!level.getBlockState(feet.below()).isAir()) {
            return;
        }

        BlockPos center = feet.below();
        // 远端坐标的区块可能还没加载，未加载时 setBlock 会被静默丢弃（平台只铺一半）。
        // 先把覆盖范围内涉及的区块强制加载，铺完保留加载状态，保证玩家不会被"半张平台"坑到。
        int minChunkX = (center.getX() - TEMP_PLATFORM_RADIUS) >> 4;
        int maxChunkX = (center.getX() + TEMP_PLATFORM_RADIUS) >> 4;
        int minChunkZ = (center.getZ() - TEMP_PLATFORM_RADIUS) >> 4;
        int maxChunkZ = (center.getZ() + TEMP_PLATFORM_RADIUS) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz); // 触发实际加载
            }
        }

        for (int dy = 0; dy < TEMP_PLATFORM_THICKNESS; dy++) {
            for (int dx = -TEMP_PLATFORM_RADIUS; dx <= TEMP_PLATFORM_RADIUS; dx++) {
                for (int dz = -TEMP_PLATFORM_RADIUS; dz <= TEMP_PLATFORM_RADIUS; dz++) {
                    // 顶层铺草方块，下面两层铺泥土，看起来像个正常地面
                    var block = dy == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT;
                    level.setBlockAndUpdate(center.offset(dx, -dy, dz), block.defaultBlockState());
                }
            }
        }
        // 四角 + 四边中点放火把，免得夜里/坑里全黑（夜视效果另有，这里只是观感）
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                if (sx == 0 && sz == 0) {
                    continue;
                }
                BlockPos torchPos = center.offset(sx * (TEMP_PLATFORM_RADIUS - 1), 1,
                        sz * (TEMP_PLATFORM_RADIUS - 1));
                if (level.getBlockState(torchPos).isAir()) {
                    level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
                }
            }
        }
        // 场地正中立两堵隔墙（沿 X 方向），中间留一条通道。
        // 位置取两人中点，所以无论你后来怎么改入场点，墙始终在正中间。
        int midZ = (int) Math.floor((COWBOY_SPAWN.z + TARGET_SPAWN.z) / 2.0D);
        int midX = (int) Math.floor((COWBOY_SPAWN.x + TARGET_SPAWN.x) / 2.0D);
        for (int dz : new int[] { -TEMP_WALL_GAP, TEMP_WALL_GAP }) {
            for (int dx = -TEMP_WALL_HALF_LENGTH; dx <= TEMP_WALL_HALF_LENGTH; dx++) {
                for (int dy = 0; dy < TEMP_WALL_HEIGHT; dy++) {
                    level.setBlockAndUpdate(
                            new BlockPos(midX + dx, center.getY() + 1 + dy, midZ + dz),
                            Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }

        SRE.LOGGER.info("[CowboyDuel] 已在 {} 铺设临时决斗平台（中心 {}，半径 {}，区块 {}~{} / {}~{}）",
                level.dimension().location(), center, TEMP_PLATFORM_RADIUS,
                minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    /**
     * 生成本模组自带的结构模板（{@link #ARENA_STRUCTURE}），并把入场点从相对偏移换算成世界坐标。
     *
     * <p>与自动平台一样，每个维度只生成一次（{@link #PLATFORM_BUILT} 复用同一份记录）。
     * 结构找不到时**不崩**：只打一条错误日志，并回退到自动平台，保证决斗仍能进行。
     */
    public static boolean prepareStructureArena(ServerLevel level) {
        if (ARENA_MODE == 3) {
            return preparePartedArena(level);
        }
        // ── 先把入场点算出来（无论结构能否加载，这两个坐标都是确定的）──
        BlockPos cowboyPos = ARENA_ORIGIN.offset(STRUCT_COWBOY_OFFSET);
        BlockPos targetPos = ARENA_ORIGIN.offset(STRUCT_TARGET_OFFSET);
        applySpawns(cowboyPos, targetPos);

        if (!PLATFORM_BUILT.add(level.dimension())) {
            return true; // 本维度已经放过了
        }

        var manager = level.getStructureManager();
        var templateOpt = manager.get(ARENA_STRUCTURE);
        if (templateOpt.isEmpty()) {
            // 结构模式：找不到模板就直接取消这局，不铺平台占位（见 failArena 注释）
            failArena(null, "找不到结构模板 " + ARENA_STRUCTURE
                    + "（应放在 src/main/resources/data/"
                    + ARENA_STRUCTURE.getNamespace() + "/structure/"
                    + ARENA_STRUCTURE.getPath() + ".nbt）");
            return false;
        }

        var template = templateOpt.get();
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        settings.setIgnoreEntities(false);
        // 先把结构覆盖到的区块强制加载，否则远端坐标会只生成一半
        forceLoadArea(level, ARENA_ORIGIN, template.getSize());

        template.placeInWorld(level, ARENA_ORIGIN, ARENA_ORIGIN, settings,
                level.getRandom(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        SRE.LOGGER.info(
                "[CowboyDuel] 已在 {} 生成结构 {}（原点 {}，尺寸 {}）｜牛仔入场点 {}｜对手入场点 {}",
                level.dimension().location(), ARENA_STRUCTURE, ARENA_ORIGIN, template.getSize(),
                COWBOY_SPAWN, TARGET_SPAWN);
        return true;
    }

    /**
     * 分块结构场地（{@link #ARENA_MODE} = 3）：按 {@link #ARENA_PARTS} 逐块生成。
     *
     * <p>站位优先用 {@link #SPAWN_COWBOY_BLOCK} / {@link #SPAWN_TARGET_BLOCK}；
     * 它们为 {@code null} 时自动扫描已生成的结构、找出 {@link #SPAWN_MARKER_BLOCK}
     * （灰化土）的前两个位置作为站位。
     */
    public static boolean preparePartedArena(ServerLevel level) {
        if (!PLATFORM_BUILT.add(level.dimension())) {
            return true; // 本维度已经放过了（重复调用时不要再铺一遍）
        }
        var manager = level.getStructureManager();
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        settings.setIgnoreEntities(false);

        // ── 第 0 步：先清掉领域里"不属于结构"的杂散方块 ──
        // 早先版本误铺的草方块平台横跨 y=199..200、直径 81 格，把结构的地面层
        //（含灰化土站位）整个盖掉了。只靠"再放一次结构"不一定能完全恢复，
        // 所以先把结构占位以外的方块清空，再重新放置，让 nbt 原样落回去。
        clearNonStructureBlocksInArena(level);

        int placed = 0;
        int changedBlocks = 0;
        for (Part part : ARENA_PARTS) {
            var opt = manager.get(part.id);
            if (opt.isEmpty()) {
                SRE.LOGGER.error("[CowboyDuel] 找不到结构分块 {}（偏移 {}）—— 跳过这一块",
                        part.id, part.offset);
                continue;
            }
            var template = opt.get();
            BlockPos origin = ARENA_ORIGIN.offset(part.offset);
            forceLoadArea(level, origin, template.getSize());

            // 统计"放置前后真正发生变化的格子数"，仅作诊断用。
            // ★ 注意：这里**不能**用"改动 0"来判定放置失败 ——
            //   结构一旦已经存在于世界（上一局放过、存档里还在），再放一次改动自然是 0，
            //   那是"已经就位"而不是"失败"。早先版本就是在这里误判，把草方块平台盖到了
            //   真实场地上面。真正的判定改到下面"校验站位标记方块"那一步。
            var before = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            var size = template.getSize();
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        BlockPos p = origin.offset(x, y, z);
                        before.put(p, level.getBlockState(p));
                    }
                }
            }

            template.placeInWorld(level, origin, origin, settings,
                    level.getRandom(), net.minecraft.world.level.block.Block.UPDATE_ALL);

            int diff = 0;
            for (var e : before.entrySet()) {
                if (!level.getBlockState(e.getKey()).equals(e.getValue())) {
                    diff++;
                }
            }
            changedBlocks += diff;
            placed++;
            SRE.LOGGER.info("[CowboyDuel]   分块 {} 已生成于 {}（尺寸 {}），实际改动 {} 个方块",
                    part.id, origin, size, diff);
        }
        if (placed == 0) {
            failArena(null, "没有生成任何结构分块（见上方逐块日志）");
            return false;
        }
        SRE.LOGGER.info("[CowboyDuel] 分块放置完成（累计改动 {} 个方块；0 表示结构本就在世界，属正常）",
                changedBlocks);

        // ── 站位：优先显式坐标，否则扫描灰化土 ──
        // ★ 这两个常量是**结构局部坐标**（相对 ARENA_ORIGIN），
        //   必须先换算成世界坐标才拿去查方块 / 传送。
        //   早先版本直接把它们当世界坐标用，于是查的是 (6,5,23) 这种地方 ——
        //   那里当然是空气，于是每次都误判"结构没落地"并回退铺平台。
        BlockPos cowboyBlock = SPAWN_COWBOY_BLOCK == null
                ? null : ARENA_ORIGIN.offset(SPAWN_COWBOY_BLOCK);
        BlockPos targetBlock = SPAWN_TARGET_BLOCK == null
                ? null : ARENA_ORIGIN.offset(SPAWN_TARGET_BLOCK);
        if (cowboyBlock == null || targetBlock == null) {
            var found = findSpawnMarkers(level);
            if (found.size() >= 2) {
                cowboyBlock = found.get(0);
                targetBlock = found.get(1);
                SRE.LOGGER.info("[CowboyDuel] 自动扫描到 {} 个 {}, 取前两个作站位: {} / {}",
                        found.size(), SPAWN_MARKER_BLOCK, cowboyBlock, targetBlock);
            } else {
                failArena(null, "只找到 " + found.size() + " 个站位标记方块 " + SPAWN_MARKER_BLOCK
                        + "，不足以定位双方");
                return false;
            }
        }

        // ── 真正的落地校验：站位那一格是不是我们预期的标记方块（世界坐标）──
        var markerBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse(SPAWN_MARKER_BLOCK));
        boolean cowboyOk = level.getBlockState(cowboyBlock).is(markerBlock);
        boolean targetOk = level.getBlockState(targetBlock).is(markerBlock);
        if (!cowboyOk || !targetOk) {
            failArena(null, "站位标记缺失（牛仔位 " + cowboyBlock + "="
                    + level.getBlockState(cowboyBlock) + ", 对手位 " + targetBlock + "="
                    + level.getBlockState(targetBlock) + "，期望 " + SPAWN_MARKER_BLOCK + "）");
            return false;
        }
        SRE.LOGGER.info("[CowboyDuel] 站位标记校验通过（{} / {} 均为 {}）",
                cowboyBlock, targetBlock, SPAWN_MARKER_BLOCK);

        // 方块坐标是"地面那一格"，玩家脚底要站在它上面
        applySpawns(cowboyBlock.above(), targetBlock.above());

        // ── 最后才收拾旧平台 ──
        // 必须放在 applySpawns 之后：清理范围是以 COWBOY_SPAWN 为中心算的，
        // 早先版本在解析前就调用，中心还是默认值 (-21012.5)，清了个寂寞。
        clearStaleTempPlatform(level);

        // ── 自检：把「实际世界坐标」和「该坐标处的真实方块」都打出来 ──
        // 排查"进去了但看不到建筑"时，这两行是关键证据：
        //   · 坐标对不对（是否落在 ARENA_ORIGIN 附近）
        //   · 落点脚下/身上到底是不是方块（是 air 就说明结构真没写进去）
        BlockPos cowFeet = BlockPos.containing(COWBOY_SPAWN.x, COWBOY_SPAWN.y, COWBOY_SPAWN.z);
        BlockPos tgtFeet = BlockPos.containing(TARGET_SPAWN.x, TARGET_SPAWN.y, TARGET_SPAWN.z);
        SRE.LOGGER.info("[CowboyDuel] 自检｜维度={}｜锚点={}"
                + "｜牛仔位={} 脚下方块={}（其下方 {}）"
                + "｜对手位={} 脚下方块={}（其下方 {}）",
                level.dimension().location(), ARENA_ORIGIN,
                COWBOY_SPAWN, level.getBlockState(cowFeet),
                level.getBlockState(cowFeet.below()),
                TARGET_SPAWN, level.getBlockState(tgtFeet),
                level.getBlockState(tgtFeet.below()));
        SRE.LOGGER.info("[CowboyDuel] 分块场地就绪（{} 块）｜牛仔入场点 {}｜对手入场点 {}｜朝向 {} / {}",
                placed, COWBOY_SPAWN, TARGET_SPAWN, COWBOY_YAW, TARGET_YAW);

        // 把世界坐标直接算成"可直接用的整数"，方便玩家在冒险模式（看不到 F3）下核对/传送。
        // 格式刻意写成 /tp 能直接粘的样子。
        int cx = (int) Math.floor(COWBOY_SPAWN.x);
        int cy = (int) Math.floor(COWBOY_SPAWN.y);
        int cz = (int) Math.floor(COWBOY_SPAWN.z);
        int tx = (int) Math.floor(TARGET_SPAWN.x);
        int ty = (int) Math.floor(TARGET_SPAWN.y);
        int tz = (int) Math.floor(TARGET_SPAWN.z);
        SRE.LOGGER.info("[CowboyDuel] 世界坐标｜牛仔 ({}, {}, {})｜对手 ({}, {}, {})｜"
                + "可直接 /tp {} {} {} 与 /tp {} {} {} 过去查看",
                cx, cy, cz, tx, ty, tz, cx, cy, cz, tx, ty, tz);
        return true;
    }

    /**
     * 把早先版本误铺的临时草方块平台从领域里清掉。
     *
     * <p>历史原因：上一版把"本次放置改动 0 个方块"误判成放置失败，于是往真实场地上
     * 盖了一层草方块平台（半径 40、厚 2，还有火把与两堵石墙）。升级后结构没问题了，
     * 但平台还留在存档里，所以需要主动清一次。
     *
     * <p>安全策略：<b>只删平台特征方块</b>（草方块 / 泥土 / 圆石 / 火把），
     * 且 <b>只删结构 NBT 自己不占用的格子</b> —— 结构放下的方块一个都不碰。
     */
    private static void clearStaleTempPlatform(ServerLevel level) {
        if (ARENA_MODE != 3) {
            return; // 只有分块结构模式才需要（其它模式没铺过平台）
        }
        // 结构 NBT 覆盖到的格子（这些必须保留）
        var protectedPos = new java.util.HashSet<BlockPos>();
        var manager = level.getStructureManager();
        for (Part part : ARENA_PARTS) {
            var opt = manager.get(part.id);
            if (opt.isEmpty()) {
                continue;
            }
            var size = opt.get().getSize();
            BlockPos origin = ARENA_ORIGIN.offset(part.offset);
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        protectedPos.add(origin.offset(x, y, z));
                    }
                }
            }
        }

        BlockPos center = BlockPos.containing(COWBOY_SPAWN.x, COWBOY_SPAWN.y, COWBOY_SPAWN.z);
        int removed = 0;
        // 平台是"顶层草方块 + 下方泥土"共 2 层，外加四角/四边火把与中间两堵石墙；
        // 范围取半径 +2 留点余量。
        for (int dx = -TEMP_PLATFORM_RADIUS - 2; dx <= TEMP_PLATFORM_RADIUS + 2; dx++) {
            for (int dz = -TEMP_PLATFORM_RADIUS - 2; dz <= TEMP_PLATFORM_RADIUS + 2; dz++) {
                for (int dy = -TEMP_PLATFORM_THICKNESS - 1; dy <= TEMP_WALL_HEIGHT + 1; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (protectedPos.contains(p)) {
                        continue; // 结构自己的方块，保留
                    }
                    var st = level.getBlockState(p);
                    var b = st.getBlock();
                    boolean isPlatformBlock = b == Blocks.GRASS_BLOCK
                            || b == Blocks.DIRT
                            || b == Blocks.COBBLESTONE
                            || b == Blocks.TORCH;
                    if (isPlatformBlock) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            SRE.LOGGER.info("[CowboyDuel] 已清理早先误铺的临时平台方块 {} 个（领域中心 {}）",
                    removed, center);
        }
    }

    /**
     * 把领域范围内、但**不属于结构**的杂散方块清掉。
     *
     * <p>为什么需要：早先的版本误把草方块平台铺在真实场地上，而平台横跨
     * y=199..200、直径 81 格 —— 它把结构的地面层（局部 y=5 那层，含灰化土站位）
     * 整个覆盖掉了，所以升级后光"再放一次结构"可能仍缺地面。
     *
     * <p>做法：算出结构两块占用的格子集合；在结构包围盒（外扩 2 格、含上方余量）
     * 内，把**不在该集合里**的方块全部清成空气。这样：
     * <ul>
     *   <li>旧平台残留 → 清掉；</li>
     *   <li>结构自身的方块 → 一个不碰。</li>
     * </ul>
     * 调用方随后会重新放置一次模板，把被覆盖过的格子按 nbt 原样还原。
     */
    private static void clearNonStructureBlocksInArena(ServerLevel level) {
        var manager = level.getStructureManager();
        var protectedPos = new java.util.HashSet<BlockPos>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part part : ARENA_PARTS) {
            var opt = manager.get(part.id);
            if (opt.isEmpty()) {
                continue;
            }
            var size = opt.get().getSize();
            BlockPos origin = ARENA_ORIGIN.offset(part.offset);
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        BlockPos p = origin.offset(x, y, z);
                        protectedPos.add(p);
                        if (p.getX() < minX) minX = p.getX();
                        if (p.getX() > maxX) maxX = p.getX();
                        if (p.getY() < minY) minY = p.getY();
                        if (p.getY() > maxY) maxY = p.getY();
                        if (p.getZ() < minZ) minZ = p.getZ();
                        if (p.getZ() > maxZ) maxZ = p.getZ();
                    }
                }
            }
        }
        if (protectedPos.isEmpty()) {
            return;
        }
        // 只清"结构包围盒外扩 2 格"范围：旧平台正好比结构外扩一圈，这样能覆盖到，
        // 又不至于把世界别处的东西也扫了。y 只往上多留 2 格（平台在结构脚下，
        // 但结构地面以下的残留由 buildTempPlatform 的 y-1..y-2 决定，已在范围内）。
        int removed = 0;
        for (int x = minX - 2; x <= maxX + 2; x++) {
            for (int z = minZ - 2; z <= maxZ + 2; z++) {
                for (int y = minY; y <= maxY + 2; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (protectedPos.contains(p)) {
                        continue;
                    }
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            SRE.LOGGER.info("[CowboyDuel] 已清除领域内不属于结构的杂散方块 {} 个"
                    + "（范围 x {}..{}, y {}..{}, z {}..{}）",
                    removed, minX - 2, maxX + 2, minY, maxY + 2, minZ - 2, maxZ + 2);
        }
    }

    /**
     * 决斗领域准备失败时的兜底：**不再铺草方块平台**。
     *
     * <p>理由：结构模式（1 / 3）下如果验证没过，直接铺平台会紧挨着真实建筑多出一块
     * 草方块场地，比"这局不打"难看得多。这里只拒绝开局并打错误日志，让管理员去修。
     * 自动平台（{@link #ARENA_MODE} = 0）本来就不走这条路，不受影响。
     *
     * @return false，调用方据此中止本次决斗
     */
    private static boolean failArena(ServerPlayer cowboy, String reason) {
        SRE.LOGGER.error("[CowboyDuel] {} —— 本次决斗取消（结构模式下不再生成草方块平台占位）",
                reason);
        if (cowboy != null) {
            cowboy.displayClientMessage(
                    Component.translatable("message.noellesroles.niuzai_juedouba.duel_arena_broken")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        return false;
    }

    /**
     * 扫描已生成的分块结构，找出所有 {@link #SPAWN_MARKER_BLOCK}（灰化土）的位置。
     *
     * <p>按 {@link #ARENA_PARTS} 的偏移 + 各自尺寸限定扫描范围，
     * 避免扫到世界里别处的同种方块。返回结果按 (y, z, x) 排序，保证两次调用顺序一致。
     */
    private static java.util.List<BlockPos> findSpawnMarkers(ServerLevel level) {
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        var manager = level.getStructureManager();
        for (Part part : ARENA_PARTS) {
            var opt = manager.get(part.id);
            if (opt.isEmpty()) {
                continue;
            }
            var size = opt.get().getSize();
            BlockPos origin = ARENA_ORIGIN.offset(part.offset);
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        BlockPos p = origin.offset(x, y, z);
                        var st = level.getBlockState(p);
                        if (st.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .get(ResourceLocation.parse(SPAWN_MARKER_BLOCK)))) {
                            found.add(p);
                        }
                    }
                }
            }
        }
        found.sort((a, b) -> {
            if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            }
            if (a.getZ() != b.getZ()) {
                return Integer.compare(a.getZ(), b.getZ());
            }
            return Integer.compare(a.getX(), b.getX());
        });
        return found;
    }

    /** 把两个「方块坐标」换算成实际传送坐标（方块中心 x/z，脚底 y）并重算朝向。 */
    private static void applySpawns(BlockPos cowboyBlock, BlockPos targetBlock) {
        COWBOY_SPAWN = new Vec3(cowboyBlock.getX() + 0.5D, cowboyBlock.getY(),
                cowboyBlock.getZ() + 0.5D);
        TARGET_SPAWN = new Vec3(targetBlock.getX() + 0.5D, targetBlock.getY(),
                targetBlock.getZ() + 0.5D);
        // 朝向：yawFacing 得到的是「正对对方」，再 +180 才是**背靠背**
        //（与你要求的"双方背对背生成"一致）。这个翻转对 X/Z 两种轴向都成立。
        COWBOY_YAW = yawFacing(COWBOY_SPAWN, TARGET_SPAWN) + 180.0F;
        TARGET_YAW = yawFacing(TARGET_SPAWN, COWBOY_SPAWN) + 180.0F;
    }

    /** 把某个区域涉及的区块强制加载，避免远端坐标 setBlock 被丢弃。 */
    private static void forceLoadArea(ServerLevel level, BlockPos origin, net.minecraft.core.Vec3i size) {
        int minX = Math.min(origin.getX(), origin.getX() + size.getX());
        int maxX = Math.max(origin.getX(), origin.getX() + size.getX());
        int minZ = Math.min(origin.getZ(), origin.getZ() + size.getZ());
        int maxZ = Math.max(origin.getZ(), origin.getZ() + size.getZ());
        for (int cx = minX >> 4; cx <= (maxX >> 4); cx++) {
            for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 六、发起决斗
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 牛仔向目标发起决斗。
     *
     * @return true 表示决斗已开始（金币已扣、双方已传送）
     */
    public static boolean start(ServerPlayer cowboy, ServerPlayer target) {
        if (cowboy == null || target == null || cowboy == target) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(cowboy.level());
        if (game == null || !game.isRunning() || !game.isRole(cowboy, ModRoles.NIUZAI_JUEDOUBA)) {
            return false;
        }
        // 只要求「活着 + 不是旁观」，不要求生存模式 —— 否则创造模式没法测试决斗。
        if (!isStillInDuel(cowboy) || !isStillInDuel(target)) {
            return false;
        }
        // 距离校验：只能拉取 RANGE 格内的人。
        // 客户端选人界面已按同样口径过滤，这里再判一次防止绕过 / 竞态（点下去到送达之间跑开）。
        if (cowboy.distanceToSqr(target) > RANGE * RANGE) {
            cowboy.displayClientMessage(
                    Component.translatable("message.noellesroles.niuzai_juedouba.duel_out_of_range",
                            (int) RANGE).withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        // 领域互斥：已在任何领域（咒术师 / 愚者塔罗会）中时禁止再开
        if (ModEffects.isInAnyDomain(cowboy) || ModEffects.isInAnyDomain(target)) {
            cowboy.displayClientMessage(
                    Component.translatable("message.noellesroles.niuzai_juedouba.duel_domain_busy")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (isInDuel(cowboy.getUUID()) || isInDuel(target.getUUID())) {
            return false;
        }

        CowboyPlayerComponent comp = CowboyPlayerComponent.KEY.get(cowboy);
        if (!comp.isDuelReady()) {
            cowboy.displayClientMessage(
                    Component.translatable("message.noellesroles.niuzai_juedouba.duel_cooldown",
                            comp.duelCooldownTicks / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 金币校验与扣除（与其他付费技能同源，用商店账户余额）。
        // 放在所有校验之后、真正动手之前，避免"扣了钱没开成"。
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(cowboy);
        if (shop.balance < COST) {
            cowboy.displayClientMessage(
                    Component.translatable("message.noellesroles.niuzai_juedouba.duel_no_coin", COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.balance -= COST;
        shop.sync();

        // 场地维度：CUSTOM_LEVEL 优先（ARENA_MODE=2 时用），否则跟随发起者所在维度。
        ServerLevel level = CUSTOM_LEVEL != null ? CUSTOM_LEVEL : cowboy.serverLevel();
        // 准备场地。这一步必须在下面取 COWBOY_SPAWN / TARGET_SPAWN **之前**完成 ——
        // ARENA_MODE=1 时会把入场点从"相对结构偏移"换算成世界坐标并写回这两个字段。
        if (!ensureArena(level)) {
            // 场地没准备好：把刚扣的金币退回去，本次决斗不开始。
            // （结构模式不再铺平台占位，所以必须在这里中止并且不扣钱。）
            shop.balance += COST;
            shop.sync();
            cowboy.displayClientMessage(
                    Component.literal("§c决斗场地不可用，已退回 " + COST + " 金币（详见服务端日志）"),
                    true);
            return false;
        }

        Duel duel = new Duel(cowboy.getUUID(), target.getUUID());

        // 先登记参与者，再传送 —— 否则传送那一刻 isPlayerOutGameAreas 的每 tick 判定
        // 可能在"已进领域但还没登记"的缝隙里把两人按出界强杀。
        BY_PARTICIPANT.put(cowboy.getUUID(), duel);
        BY_PARTICIPANT.put(target.getUUID(), duel);

        pullIn(duel, cowboy, level, COWBOY_SPAWN, COWBOY_YAW);
        pullIn(duel, target, level, TARGET_SPAWN, TARGET_YAW);

        comp.setDuelCooldown(COOLDOWN_TICKS);

        cowboy.displayClientMessage(
                Component.translatable("message.noellesroles.niuzai_juedouba.duel_start_cowboy",
                        target.getName().getString()).withStyle(ChatFormatting.GOLD),
                true);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.niuzai_juedouba.duel_start_target",
                        cowboy.getName().getString()).withStyle(ChatFormatting.RED),
                true);
        level.playSound(null, COWBOY_SPAWN.x, COWBOY_SPAWN.y, COWBOY_SPAWN.z,
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    /** 传送一名参与者进领域：先整体留底，再换装备、上效果。 */
    private static void pullIn(Duel duel, ServerPlayer player, ServerLevel level, Vec3 spawn, float yaw) {
        player.stopSleeping();
        player.stopRiding();

        // ── 先整份留底（位置/朝向/背包/效果/血量/饱食度），退出时原样还原 ──
        duel.snapshots.put(player.getUUID(), new Snapshot(player));

        // ── 背包替换为单一左轮手枪 ──
        player.getInventory().clearContent();
        player.getInventory().setItem(0, TMMItems.REVOLVER.getDefaultInstance());

        // ── 传送 ──
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.of(), yaw, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        // ── 清掉身上所有效果，再上决斗专用的那几层 ──
        // 必须先清：否则玩家原本的速度/抗性等会带进决斗，而且退出时无法区分
        // "本来就有"和"决斗给的"。退出时按快照整体还原原有效果。
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA,
                MAX_DUEL_TICKS + LOCK_TICKS, 0, false, false, false));

        // ── 锁住视角（5 秒后由 tick 移除）──
        player.addEffect(new MobEffectInstance(ModEffects.TURN_BANED,
                LOCK_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED,
                LOCK_TICKS, 0, false, false, false));

        // 转场黑屏（复用现有转场效果）
        player.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 20, 0, false, false, false));
    }

    private static ItemStack[] snapshotInventory(ServerPlayer player) {
        var inv = player.getInventory();
        ItemStack[] copy = new ItemStack[inv.getContainerSize()];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = inv.getItem(i).copy();
        }
        return copy;
    }

    private static void restoreInventory(ServerPlayer player, ItemStack[] saved) {
        if (saved == null) {
            return;
        }
        var inv = player.getInventory();
        inv.clearContent();
        for (int i = 0; i < saved.length && i < inv.getContainerSize(); i++) {
            inv.setItem(i, saved[i]);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 七、判负
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 决斗中有人被击中时调用（由 {@code GameUtils.killPlayer} 的监听方触发）。
     *
     * <p>先被击中者判负：双方立刻退出领域，败者 {@link #DEATH_DELAY_TICKS} 后被巫毒强制杀死，
     * 胜者获得一层护盾（不可叠加）。
     *
     * @return true 表示本次命中已被决斗消费（调用方不应再走常规击杀流程）
     */
    public static boolean handleHit(ServerPlayer shooter, ServerPlayer victim) {
        Duel duel = BY_PARTICIPANT.get(victim.getUUID());
        if (duel == null || duel.resolved) {
            return false;
        }
        // 只有决斗的另一方打中才算数（双方都用左轮，所以 shooter 必然是参与者）
        if (!duel.cowboyId.equals(shooter.getUUID()) && !duel.targetId.equals(shooter.getUUID())) {
            return false;
        }
        duel.resolved = true;

        ServerPlayer winner = shooter;
        ServerPlayer loser = victim;
        endArena(duel);

        // 胜者获得一层护盾（不可叠加）
        // 用 giveArmor() 而不是直接写 armor 字段：giveArmor 内部会判 armor<1，天然不可叠加。
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(winner);
        if (armor != null) {
            armor.giveArmor();
        }
        winner.displayClientMessage(
                Component.translatable("message.noellesroles.niuzai_juedouba.duel_won")
                        .withStyle(ChatFormatting.GOLD),
                true);

        // 败者：先钉在原地 1 秒（否则退场后这一秒能跑），再被"巫毒强制杀死"。
        loser.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED,
                DEATH_DELAY_TICKS + 5, 0, false, false, false));
        loser.addEffect(new MobEffectInstance(ModEffects.TURN_BANED,
                DEATH_DELAY_TICKS + 5, 0, false, false, false));
        loser.displayClientMessage(
                Component.translatable("message.noellesroles.niuzai_juedouba.duel_lost",
                        winner.getName().getString()).withStyle(ChatFormatting.RED),
                true);

        final UUID loserId = loser.getUUID();
        // 标记"等待处决"：这 1 秒里环境判定不该插一脚
        // （否则败者会以 fell_out_of_train 而不是 cowboy_duel 的名义死掉，死因显示就错了）。
        PENDING_EXECUTION.put(loserId, DEATH_DELAY_TICKS);
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(
                DEATH_DELAY_TICKS, () -> {
                    PENDING_EXECUTION.remove(loserId);
                    MinecraftServer server = SRE.SERVER;
                    if (server == null) {
                        return;
                    }
                    ServerPlayer still = server.getPlayerList().getPlayer(loserId);
                    if (still != null && GameUtils.isPlayerAliveAndSurvival(still)) {
                        GameUtils.forceKillPlayer(still, true, null, GameConstants.DeathReasons.COWBOY_DUEL);
                    }
                }));
        return true;
    }

    /**
     * 决斗死亡否决钩子：决斗期间谁被击中谁就判负，不真正走常规死亡流程
     * （败者的死由 {@link #handleHit} 延迟 1 秒用巫毒强制杀死执行）。
     *
     * <p>注册在 {@code AllowPlayerDeathWithKiller} 上 —— 它在 {@code GameMode.killPlayer}
     * 里先于"护盾吃伤害"的分支执行，所以拦下不会误消耗护盾。
     *
     * @return true = 允许正常死亡；false = 拦下本次死亡
     */
    public static boolean allowDeath(Player victim, Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer victimSp) || !isInDuel(victimSp.getUUID())) {
            return true;
        }
        // 自己延迟执行的巫毒处决必须放行。
        if (deathReason != null
                && GameConstants.DeathReasons.COWBOY_DUEL.getPath().equals(deathReason.getPath())) {
            return true;
        }
        if (!(killer instanceof ServerPlayer killerSp)) {
            return true;
        }
        return !handleHit(killerSp, victimSp);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 八、退出 / tick
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 把双方**按入场前的快照整体还原**并清理。
     *
     * <p>还原内容：位置、朝向、整份背包、全部药水效果、血量、饱食度。
     * 这样无论决斗期间发生了什么，退出后玩家都回到"进决斗前"的状态 ——
     * 胜者的护盾由调用方在还原之后另外补上。
     */
    private static void endArena(Duel duel) {
        MinecraftServer server = resolveServer(duel);
        for (Map.Entry<UUID, Snapshot> e : duel.snapshots.entrySet()) {
            BY_PARTICIPANT.remove(e.getKey());
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                continue;
            }
            Snapshot snap = e.getValue();

            // ── 按快照整体还原到"进入决斗前的状态" ──
            // 背包
            restoreInventory(p, snap.inventory);
            // 位置与朝向
            p.teleportTo(snap.level, snap.x, snap.y, snap.z, Set.of(), snap.yaw, snap.pitch);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0.0F;

            // 效果：先清空决斗期间加的所有效果，再还原入场前本来就有的那些。
            // 用"清空 + 还原"而不是逐个 removeEffect，是为了不改动玩家原有的同类效果
            //（例如入场前就挂着的夜视/速度，逐个 remove 会被误删）。
            p.removeAllEffects();
            for (MobEffectInstance eff : snap.effects) {
                p.addEffect(new MobEffectInstance(eff));
            }

            // 血量与饱食度
            p.setHealth(Math.min(snap.health, p.getMaxHealth()));
            p.getFoodData().setFoodLevel(snap.foodLevel);
            p.getFoodData().setSaturation(snap.saturation);

            // 同步给客户端（背包 / 效果 / 血量都要刷，否则界面与实际不一致）
            p.containerMenu.broadcastChanges();
            p.getInventory().setChanged();
            p.onUpdateAbilities();
        }
    }

    /** 从任一参与者反查服务器实例（参与者可能已掉线，所以逐个试）。 */
    private static MinecraftServer resolveServer(Duel duel) {
        MinecraftServer server = io.wifi.starrailexpress.SRE.SERVER;
        if (server == null) {
            return null;
        }
        return server;
    }

    /**
     * 该玩家是否还「留在决斗里」（用于判断要不要收场）。
     *
     * <p>刻意<b>不</b>用 {@code GameUtils.isPlayerAliveAndSurvival}：那个方法把创造模式
     * 也算作"非生存"，会让玩家一切换创造就被踢出领域。这里只要求
     * 「在线 + 活着 + 不是旁观」。
     */
    private static boolean isStillInDuel(ServerPlayer p) {
        return p != null && !p.isRemoved() && p.isAlive() && !p.isSpectator();
    }

    private static void tickAll(MinecraftServer server) {
        // 冷却递减：与是否有进行中的决斗无关，所以放在最前面、不受下面的提前 return 影响。
        //
        // ★ 条件必须是「任一条冷却在跑」，不能只判 duelCooldownTicks：
        //   抽雪茄时 duelCooldownTicks 通常是 0，只判决斗冷却的话
        //   tickDuelCooldown() 永远不会被调用 →
        //   cigarCooldownTicks 卡在 600 不动，界面一直显示"30 秒"。
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CowboyPlayerComponent comp = CowboyPlayerComponent.KEY.maybeGet(p).orElse(null);
            if (comp != null && (comp.duelCooldownTicks > 0 || comp.cigarCooldownTicks > 0)) {
                comp.tickDuelCooldown();
            }
        }

        if (BY_PARTICIPANT.isEmpty()) {
            return;
        }
        // 去重：一次决斗有两个参与者，只处理一次。
        //
        // ★ 必须先拍一份快照再遍历，不能直接 for-each BY_PARTICIPANT.values()：
        //   下面超时 / 有人掉线时会调用 endArena()，而它会 BY_PARTICIPANT.remove(...)，
        //   在活着的 value 视图上边遍历边删 → ConcurrentModificationException（崩服）。
        //   （2026-10-16 12:51 的 server 崩溃就是这个。）
        java.util.Set<Duel> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.List<Duel> snapshot = new ArrayList<>();
        for (Duel d : BY_PARTICIPANT.values()) {
            if (seen.add(d)) {
                snapshot.add(d);
            }
        }
        for (Duel duel : snapshot) {
            if (duel.resolved) {
                continue;
            }
            duel.ticksLeft--;

            ServerPlayer cowboy = server.getPlayerList().getPlayer(duel.cowboyId);
            ServerPlayer target = server.getPlayerList().getPlayer(duel.targetId);

            // 任一方掉线/死亡 → 直接结束，避免把对方困在领域里。
            //
            // ★ 这里不能只认 isPlayerAliveAndSurvival：创造模式会被它判成"不在生存"，
            //   于是玩家一切换创造就被立刻踢出领域（表现为"跳出来"）。
            //   所以放宽成"在线 + 活着 + 不是旁观"。
            if (cowboy == null || target == null
                    || !isStillInDuel(cowboy) || !isStillInDuel(target)) {
                duel.resolved = true;
                endArena(duel);
                continue;
            }

            // 锁定解除的瞬间给个提示
            if (duel.ticksLeft == 0) {
                cowboy.removeEffect(ModEffects.TURN_BANED);
                cowboy.removeEffect(ModEffects.MOVE_BANED);
                target.removeEffect(ModEffects.TURN_BANED);
                target.removeEffect(ModEffects.MOVE_BANED);
                cowboy.displayClientMessage(
                        Component.translatable("message.noellesroles.niuzai_juedouba.duel_fight")
                                .withStyle(ChatFormatting.GOLD),
                        true);
                target.displayClientMessage(
                        Component.translatable("message.noellesroles.niuzai_juedouba.duel_fight")
                                .withStyle(ChatFormatting.RED),
                        true);
                cowboy.level().playSound(null, cowboy.getX(), cowboy.getY(), cowboy.getZ(),
                        SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
            }

            // 超时判平局：双方都按快照还原到决斗前的状态，谁都不死、谁都没有护盾。
            if (duel.ticksLeft < -MAX_DUEL_TICKS) {
                duel.resolved = true;
                Component timeout = Component.translatable(
                        "message.noellesroles.niuzai_juedouba.duel_timeout",
                        MAX_DUEL_TICKS / 20).withStyle(ChatFormatting.YELLOW);
                cowboy.displayClientMessage(timeout, true);
                target.displayClientMessage(timeout, true);
                endArena(duel);
                continue;
            }

            // 边界约束
            AABB bounds = arenaBounds();
            GameUtils.limitPlayerToBox(cowboy, bounds);
            GameUtils.limitPlayerToBox(target, bounds);
        }
    }

    /** 领域活动范围。中心取两入场点中点；只依赖坐标，和维度无关。 */
    private static AABB arenaBounds() {
        double cx = (COWBOY_SPAWN.x + TARGET_SPAWN.x) / 2.0D;
        double cy = (COWBOY_SPAWN.y + TARGET_SPAWN.y) / 2.0D;
        double cz = (COWBOY_SPAWN.z + TARGET_SPAWN.z) / 2.0D;
        return new AABB(
                cx - BOUND_RADIUS, cy - BOUND_HEIGHT, cz - BOUND_RADIUS,
                cx + BOUND_RADIUS, cy + BOUND_HEIGHT, cz + BOUND_RADIUS);
    }

    /** 游戏结束时兜底清理（由统一次局结束钩子调用）。 */
    public static void forceEndAll() {
        java.util.Set<Duel> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Duel duel : new java.util.ArrayList<>(BY_PARTICIPANT.values())) {
            if (seen.add(duel)) {
                duel.resolved = true;
                endArena(duel);
            }
        }
        BY_PARTICIPANT.clear();
    }
}
