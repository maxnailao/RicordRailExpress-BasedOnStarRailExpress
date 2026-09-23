package org.agmas.noellesroles.game.roles.neutral.prewitch;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 预备魔女 / 魔女 共用组件。
 *
 * <p>
 * 预备魔女（pre_witch）：
 * <ul>
 * <li>特殊中立阵营，仅在魔女监牢地图刷新</li>
 * <li>开局随机获得一个技能：召回者 / 时空旅者 / 净化者 / 明星 / 死亡回溯（低概率）</li>
 * <li>san 值不会自然降低；目击尸体与杀人现场会扣除心情值</li>
 * <li>心情值归零时转化（transform）为魔女</li>
 * </ul>
 *
 * <p>
 * 魔女（majo）：由预备魔女转化而来，属于杀手阵营，继承随机技能（部分技能发生形态变化）。
 *
 * <p>
 * 注意：改职（{@link RoleUtils#changeRole}）会触发本组件的 {@link #init()}，
 * 因此 {@link #transform} 必须在改职之后再把状态写回。
 */
public class PreWitchPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<PreWitchPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pre_witch"),
            PreWitchPlayerComponent.class);

    /** 随机技能池 */
    public enum GrantedSkill {
        /** 召回者：标记地点 / 回到标记地点 */
        RECALLER("recaller"),
        /** 时空旅者：放置传送门 */
        RUIKE("ruike"),
        /** 净化者（转为魔女后变为破法者） */
        JINGHUAZHE("jinghuazhe"),
        /** 明星（转为魔女后变为禁锢） */
        SUPERSTAR("superstar"),
        /** 死亡回溯：死亡时在房间内复活，每局仅一次 */
        REWIND("rewind");

        private final String id;

        GrantedSkill(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static GrantedSkill byId(String id) {
            for (GrantedSkill skill : values()) {
                if (skill.id.equals(id)) {
                    return skill;
                }
            }
            return RECALLER;
        }
    }

    /** 死亡回溯的抽中概率（百分比，其他技能均分剩余概率） */
    private static final int REWIND_CHANCE_PERCENT = 8;

    /** 目击尸体扣除的心情值 */
    public static final float CORPSE_MOOD_PENALTY = 0.25f;
    /** 目击杀人现场扣除的心情值 */
    public static final float SCENE_MOOD_PENALTY = 0.15f;
    /** 目击判定半径（格） */
    public static final double WITNESS_RADIUS = 14.0D;
    /** 尸体与现场的重叠判定（同一处死亡不重复扣心情） */
    private static final double DEDUP_RADIUS_SQR = 2.5D * 2.5D;
    /** 死亡回溯的复活延迟（tick） */
    private static final int REWIND_DELAY_TICKS = 3;

    private final Player player;

    /** 开局随机到的技能 */
    private GrantedSkill grantedSkill = GrantedSkill.RECALLER;
    /** 开局随机技能是否已经真正摇过（防止组件默认值 RECALLER 被当成结果） */
    private boolean grantedSkillRolled = false;
    /**
     * transform() 内部改职同样会触发 init()，用这个标记告诉 init()：这次要保留继承下来的技能。
     * （init() 也会额外检查当前职业是否已经是魔女，双保险）
     */
    private boolean suppressNextRoll = false;
    /** 是否已转化为魔女 */
    private boolean transformed = false;
    /** 死亡回溯是否已经使用过（每局一次） */
    private boolean rewindUsed = false;
    /** 死亡回溯待复活倒计时，-1 表示没有待处理的回溯 */
    private int pendingRewindTicks = -1;
    /** 当前技能的剩余冷却（tick），由 {@link PreWitchSkillDispatcher} 在释放成功后写入 */
    private int skillCooldownTicks = 0;
    /** 开局随机技能是否已经向玩家播报过 */
    private boolean grantedSkillAnnounced = false;
    /** 已目击过的尸体 */
    private final Set<UUID> seenBodies = new HashSet<>();
    /** 已目击过的杀人现场（现场 id） */
    private final Set<Long> seenScenes = new HashSet<>();

    public PreWitchPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    // ==================== 状态访问 ====================

    public GrantedSkill getGrantedSkill() {
        return grantedSkill;
    }

    public boolean isTransformed() {
        return transformed;
    }

    public boolean isRewindUsed() {
        return rewindUsed;
    }

    /** 是否还能触发死亡回溯（每局一次，转化为魔女后依然保留） */
    public boolean canTriggerRewind() {
        return !rewindUsed && grantedSkill == GrantedSkill.REWIND;
    }

    public int getSkillCooldownTicks() {
        return skillCooldownTicks;
    }

    public void setSkillCooldownTicks(int ticks) {
        this.skillCooldownTicks = Math.max(0, ticks);
    }

    // ==================== 生命周期 ====================

    @Override
    public void init() {
        // init() 有两种来源：
        // 1) 真正进入一局（每局开始框架调用的 clear()、或者本局被分配到预备魔女 / 魔女）：重新随机技能；
        // 2) transform() 内部改职触发：此时 suppressNextRoll 为 true，保留继承下来的技能
        //    （transform() 在 changeRole 之后还会再写回一次状态，属于双保险）。
        boolean keepInheritedSkill = this.suppressNextRoll;
        this.suppressNextRoll = false;
        if (!keepInheritedSkill) {
            this.grantedSkill = rollSkill();
        }
        // 注意：这里必须无条件重置 transformed / rewindUsed。
        // grantedSkill 与 transformed 都会被写进玩家存档，旧版本只在 !transformed 时才重摇，
        // 于是上一局残留的转化状态会让新的一局既不重新随机技能、也无法再次转化，
        // 玩家就会一直拿着上一局抽到的技能（表现为「每一局都是时空旅者」）。
        this.grantedSkillRolled = true;
        this.transformed = false;
        this.rewindUsed = false;
        this.pendingRewindTicks = -1;
        this.skillCooldownTicks = 0;
        this.grantedSkillAnnounced = false;
        this.seenBodies.clear();
        this.seenScenes.clear();
    }

    @Override
    public void clear() {
        // 框架在每局开始 / 每局结束时都会先调用 clear()：这里彻底清空，
        // 再让 init() 重新随机一次技能，避免玩家存档里的旧技能一直沿用下去。
        this.suppressNextRoll = false;
        this.transformed = false;
        this.grantedSkill = GrantedSkill.RECALLER;
        this.grantedSkillRolled = false;
        this.rewindUsed = false;
        init();
    }

    /** 把组件状态同步给该玩家客户端（与其他职业组件保持一致） */
    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 摇一次开局技能：8% 死亡回溯，其余四个技能均分。
     *
     * <p>
     * 刻意用世界的随机源（{@link ServerLevel#getRandom()}）而不是实体随机源，
     * 它一定在持续推进，不会因为实体复用/读档而重复出现同一个结果。
     */
    private GrantedSkill rollSkill() {
        RandomSource random = player.level() instanceof ServerLevel serverLevel
                ? serverLevel.getRandom()
                : player.getRandom();
        GrantedSkill rolled;
        if (random.nextInt(100) < REWIND_CHANCE_PERCENT) {
            rolled = GrantedSkill.REWIND;
        } else {
            GrantedSkill[] pool = {
                    GrantedSkill.RECALLER,
                    GrantedSkill.RUIKE,
                    GrantedSkill.JINGHUAZHE,
                    GrantedSkill.SUPERSTAR
            };
            rolled = pool[random.nextInt(pool.length)];
        }
        if (isPreWitchOrMajo()) {
            Noellesroles.LOGGER.info("[PreWitch] {} 本局随机技能 -> {}", player.getName().getString(), rolled.id());
        } else {
            Noellesroles.LOGGER.debug("[PreWitch] {} 随机技能（当前不是预备魔女）-> {}",
                    player.getName().getString(), rolled.id());
        }
        return rolled;
    }

    /** 当前职业是否是预备魔女 / 魔女（只用于日志判断） */
    private boolean isPreWitchOrMajo() {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        SRERole role = SREGameWorldComponent.KEY.get(serverLevel).getRole(player);
        return PreWitchManager.isPreWitch(role) || PreWitchManager.isMajo(role);
    }

    /**
     * 组件被延迟创建（例如中途改职、读档）时补一次随机技能。
     *
     * <p>
     * 只在「从未摇过」时执行，避免覆盖已经确定（或已转化为魔女继承下来）的技能。
     */
    public void ensureSkillRolled() {
        if (grantedSkillRolled) {
            return;
        }
        grantedSkillRolled = true;
        if (!transformed) {
            this.grantedSkill = rollSkill();
        }
    }

    // ==================== 每刻逻辑 ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 死亡回溯：延迟若干 tick 后在房间内复活，避免与死亡结算流程互相干扰
        if (pendingRewindTicks >= 0) {
            if (pendingRewindTicks == 0) {
                pendingRewindTicks = -1;
                reviveByRewind(serverPlayer);
            } else {
                pendingRewindTicks--;
            }
        }

        tickSkillCooldown(serverPlayer);

        ServerLevel level = serverPlayer.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRunning()) {
            return;
        }
        SRERole role = gameWorld.getRole(serverPlayer);
        if (!PreWitchManager.isPreWitch(role)) {
            // 已转化为魔女（或不是预备魔女）：无需维持心情
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }

        ensureSkillRolled();

        if (!grantedSkillAnnounced) {
            grantedSkillAnnounced = true;
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.prewitch.granted_skill",
                            Component.translatable("message.noellesroles.prewitch.skill_name." + grantedSkill.id()))
                            .withStyle(ChatFormatting.AQUA),
                    false);
        }

        compensateNaturalMoodDrain(serverPlayer);
        witnessScan(serverPlayer, level);

        if (SREPlayerMoodComponent.KEY.get(serverPlayer).getMood() <= 0f) {
            transform(serverPlayer, level, gameWorld);
        }
    }

    /**
     * 递减技能冷却，并把真实冷却补回技能状态。
     *
     * <p>
     * 技能定义本身的冷却写的是 0，释放成功后框架会把技能状态冷却重置为 0，
     * 所以这里在下一 tick 把组件上的剩余冷却写回去；之后框架与本地计数同步递减，不会反复同步。
     */
    private void tickSkillCooldown(ServerPlayer serverPlayer) {
        if (this.skillCooldownTicks <= 0) {
            return;
        }
        this.skillCooldownTicks--;
        if (this.skillCooldownTicks <= 0) {
            return;
        }
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(serverPlayer);
        var skillId = PreWitchSkillDispatcher.skillIdOf(this.transformed);
        if (ability.getSkillState(skillId).cooldown <= 0) {
            ability.setSkillCooldown(skillId, this.skillCooldownTicks);
        }
    }

    /**
     * 预备魔女的 san 值不会自然降低：把任务带来的自然流逝补回来。
     */
    private void compensateNaturalMoodDrain(ServerPlayer serverPlayer) {
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(serverPlayer);
        if (mood.playerTaskComponent == null) {
            mood.playerTaskComponent = SREPlayerTaskComponent.KEY.get(serverPlayer);
        }
        int taskCount = mood.playerTaskComponent.tasks.size();
        if (taskCount <= 0) {
            return;
        }
        float current = mood.getMood();
        if (current >= 1f || current <= 0f) {
            return;
        }
        float restore = taskCount * GameConstants.MOOD_DRAIN * ModEffects.getMoodDrainMultiplier(serverPlayer);
        mood.setMood(Math.min(1f, current + restore));
    }

    /**
     * 目击尸体与杀人现场扣除心情值（同一具尸体 / 同一处现场只扣一次）。
     */
    private void witnessScan(ServerPlayer serverPlayer, ServerLevel level) {
        double radiusSqr = WITNESS_RADIUS * WITNESS_RADIUS;

        List<PlayerBodyEntity> bodies = level.getEntitiesOfClass(PlayerBodyEntity.class,
                serverPlayer.getBoundingBox().inflate(WITNESS_RADIUS), body -> true);
        for (PlayerBodyEntity body : bodies) {
            UUID bodyUuid = body.getUUID();
            if (seenBodies.contains(bodyUuid)) {
                continue;
            }
            if (serverPlayer.distanceToSqr(body) > radiusSqr) {
                continue;
            }
            if (!serverPlayer.hasLineOfSight(body)) {
                continue;
            }
            seenBodies.add(bodyUuid);
            // 尸体与杀人现场是同一处，避免重复扣心情
            markScenesNear(body.position());
            loseMood(serverPlayer, CORPSE_MOOD_PENALTY,
                    Component.translatable("message.noellesroles.prewitch.witness_corpse"));
        }

        for (PreWitchManager.KillScene scene : PreWitchManager.getScenes(level)) {
            if (seenScenes.contains(scene.id())) {
                continue;
            }
            if (player.distanceToSqr(scene.pos()) > radiusSqr) {
                continue;
            }
            if (!canSeePosition(serverPlayer, scene.pos())) {
                continue;
            }
            seenScenes.add(scene.id());
            loseMood(serverPlayer, SCENE_MOOD_PENALTY,
                    Component.translatable("message.noellesroles.prewitch.witness_scene"));
        }
    }

    private void markScenesNear(Vec3 pos) {
        for (PreWitchManager.KillScene scene : PreWitchManager.getScenes((ServerLevel) player.level())) {
            if (scene.pos().distanceToSqr(pos) <= DEDUP_RADIUS_SQR) {
                seenScenes.add(scene.id());
            }
        }
    }

    /** 射线检测：玩家眼睛到目标点之间是否被方块阻挡 */
    private boolean canSeePosition(ServerPlayer serverPlayer, Vec3 target) {
        Vec3 eye = serverPlayer.getEyePosition();
        HitResult hit = serverPlayer.level().clip(
                new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, serverPlayer));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void loseMood(ServerPlayer serverPlayer, float amount, Component message) {
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(serverPlayer);
        mood.setMood(Math.max(0f, mood.getMood() - amount));
        serverPlayer.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    // ==================== 死亡回溯 ====================

    /**
     * 死亡时由 {@link PreWitchManager} 调用；满足条件则安排一次房间内复活。
     */
    public void onDeathForRewind() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!canTriggerRewind()) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
        SRERole role = gameWorld.getRole(serverPlayer);
        if (!gameWorld.isRunning() || !(PreWitchManager.isPreWitch(role) || PreWitchManager.isMajo(role))) {
            return;
        }
        this.rewindUsed = true;
        this.pendingRewindTicks = REWIND_DELAY_TICKS;
    }

    private void reviveByRewind(ServerPlayer serverPlayer) {
        GameUtils.revivePlayerToItsRoom(serverPlayer);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.prewitch.rewind_triggered").withStyle(ChatFormatting.AQUA),
                false);
        serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    // ==================== 转化为魔女 ====================

    /**
     * 心情值归零：转化为魔女。
     *
     * <p>
     * 改职会触发组件 {@link #init()}，所以必须在 {@link RoleUtils#changeRole} 之后写回状态。
     */
    public void transform(ServerPlayer serverPlayer, ServerLevel level, SREGameWorldComponent gameWorld) {
        if (transformed) {
            return;
        }
        if (!PreWitchManager.isPreWitch(gameWorld.getRole(serverPlayer))) {
            return;
        }

        // 先保存需要继承的状态
        GrantedSkill keptSkill = this.grantedSkill;
        boolean keptRewindUsed = this.rewindUsed;
        Set<UUID> keptBodies = new HashSet<>(this.seenBodies);
        Set<Long> keptScenes = new HashSet<>(this.seenScenes);

        // 转为杀手阵营的魔女（changeRole 会触发 init()，用标记让它保留继承的技能）
        this.suppressNextRoll = true;
        RoleUtils.changeRole(serverPlayer, ModRoles.MAJO);

        // changeRole 内部会触发 init()，此处必须写回继承的状态
        this.transformed = true;
        this.grantedSkillRolled = true;
        this.grantedSkill = keptSkill;
        this.rewindUsed = keptRewindUsed;
        this.pendingRewindTicks = -1;
        this.skillCooldownTicks = 0;
        this.grantedSkillAnnounced = true;
        this.seenBodies.clear();
        this.seenBodies.addAll(keptBodies);
        this.seenScenes.clear();
        this.seenScenes.addAll(keptScenes);

        // 转化后失去拾取手枪的能力，已持有的手枪会被没收
        RoleUtils.clearAllRevolver(serverPlayer);

        PreWitchManager.announceTransformation(serverPlayer, level);
    }

    // ==================== 存档 / 同步 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putString("GrantedSkill", this.grantedSkill.id());
        tag.putBoolean("Transformed", this.transformed);
        tag.putBoolean("RewindUsed", this.rewindUsed);
        tag.putInt("SkillCooldownTicks", this.skillCooldownTicks);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("GrantedSkill")) {
            this.grantedSkill = GrantedSkill.byId(tag.getString("GrantedSkill"));
            // 存档里已有技能记录，说明这一局已经摇过，不要再次随机
            this.grantedSkillRolled = true;
        }
        this.transformed = tag.getBoolean("Transformed");
        this.rewindUsed = tag.getBoolean("RewindUsed");
        this.skillCooldownTicks = tag.getInt("SkillCooldownTicks");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putString("GrantedSkill", this.grantedSkill.id());
        tag.putBoolean("Transformed", this.transformed);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("GrantedSkill")) {
            this.grantedSkill = GrantedSkill.byId(tag.getString("GrantedSkill"));
        }
        this.transformed = tag.getBoolean("Transformed");
    }
}
