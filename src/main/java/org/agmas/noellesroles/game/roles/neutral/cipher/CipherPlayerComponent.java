package org.agmas.noellesroles.game.roles.neutral.cipher;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.*;
import org.agmas.noellesroles.game.roles.neutral.cipher.CipherPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.c4.C4Detonation;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 密客组件（本体侧）。本体留在原地，画面通过 setCameraEntity 切到无人机。
 */
public class CipherPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<CipherPlayerComponent> KEY = ModComponents.CIPHER;

    public static final int COOLDOWN_SECONDS = 45;
    public static final int MAX_EMP = 3;
    public static final double RUSH_SPEED_MULT = 2.0;
    public static final double EXPLOSION_RADIUS = 5.0;
    public static final double KNOCKBACK_STRENGTH = 1.0;
    /** 自杀冲锋装弹后的引信时长（秒），也是屏幕中央倒计时起始值 */
    public static final int BOMB_FUSE_SECONDS = 15;

    private final Player player;

    // 同步到客户端的字段
    public boolean droneActive;
    public boolean rush;
    public int emp;
    @Nullable
    public UUID droneId;
    // 同步到客户端：上一次引爆炸飞了多少名玩家 + 引爆事件计数（用于客户端触发提示）
    public int blownPlayers;
    public int blownEventId;
    // 同步到客户端：无人机已贴的 C4 目标 + 倒计时(秒) + 预判炸死人数
    @Nullable public UUID c4CarrierId;
    public int c4RemainingSeconds;
    public int c4PredictedKills;
    // 同步到客户端：已装弹冲锋的引信剩余 tick（>0 时 HUD 中央显示倒计时）
    public int bombRemainingTicks;
    // 同步到客户端：无人机是否已被悍匪装载了 C4；仅 true 时冲锋才带炸弹引信
    public boolean bombLoaded;
    // 同步到客户端：装弹悍匪的 UUID（爆炸击杀归属与赏金归算用）
    @Nullable public UUID bombPlanterId;

    // 仅服务端：本体锚点
    private Vec3 anchor = Vec3.ZERO;
    // 仅服务端：无人机所在区块的强制加载票据位置（变化时才重挂）
    @Nullable
    private ChunkPos lastDroneTicketPos = null;
    // 仅服务端：票据 holder（POST_TELEPORT 是 TicketType<Integer>，用 drone.getId()）
    private int lastDroneTicketHolder = 0;
    // 仅服务端：无人机直接引用，避免同一 tick 内 UUID 索引未刷新导致误判丢失
    @Nullable
    private CipherDroneEntity droneRef = null;
    // 仅服务端：召唤后宽限 ticks（<60 tick 内即使查不到也不算丢失，避免与 addFreshEntity 竞争）
    private int droneGraceTicks = 0;
    // 仅服务端：技能键防抖剩余 tick（40 = 2 秒，避免连点导致召唤后立刻进入冲锋/自爆）
    private int skillInputGraceTicks = 0;
    // 仅服务端：已装弹冲锋的引爆绝对世界 tick（-1 表示未装弹）
    private long bombDetonationAtGameTick = -1L;
    // 仅服务端：上一次同步到客户端的十分之一秒刻度（降频同步）
    private int lastBombTenths = -1;

    public CipherPlayerComponent(Player player) {
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

    @Override
    public void init() {
        this.droneActive = false;
        this.rush = false;
        this.emp = 0;
        this.droneId = null;
        this.droneRef = null;
        this.droneGraceTicks = 0;
        this.skillInputGraceTicks = 0;
        this.blownPlayers = 0;
        this.blownEventId = 0;
        this.c4CarrierId = null;
        this.c4RemainingSeconds = 0;
        this.c4PredictedKills = 0;
        this.bombLoaded = false;
        this.bombPlanterId = null;
        this.sync();
    }

    @Override
    public void clear() {
        discardDrone();
        this.droneActive = false;
        this.rush = false;
        this.droneId = null;
        this.droneRef = null;
        this.droneGraceTicks = 0;
        this.skillInputGraceTicks = 0;
        this.c4CarrierId = null;
        this.c4RemainingSeconds = 0;
        this.c4PredictedKills = 0;
        this.bombLoaded = false;
        this.bombPlanterId = null;
        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /** 技能键：未激活且冷却结束 → 召唤；已激活且未冲锋且有 EMP → 开启自杀式冲锋 */
    public void onSkillKey(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel serverLevel)) return;
        // 2 秒防抖：召唤/冲锋触发后 skillInputGraceTicks=40，期间忽略后续点击
        if (skillInputGraceTicks > 0) return;

        if (!droneActive) {
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(sp);
            if (ability.cooldown > 0) return;
            summonDrone(serverLevel, sp);
            skillInputGraceTicks = 40;
            return;
        }

        // 已在驾驶：再次点击技能键 = 消耗 EMP 开启冲锋（冲锋中不可再次触发）
        if (!rush && emp > 0) {
            emp--;
            rush = true;
            CipherDroneEntity drone = getDrone(serverLevel);
            if (drone != null) drone.setRush(true);
            // 只有已被悍匪装弹的无人机才启动引信；否则冲锋 = 空撞解体
            if (this.bombLoaded) {
                this.bombDetonationAtGameTick = serverLevel.getGameTime() + (long) BOMB_FUSE_SECONDS * 20L;
                this.bombRemainingTicks = BOMB_FUSE_SECONDS * 20;
                this.lastBombTenths = -1;
            }
            skillInputGraceTicks = 40;
            sync();
        }
    }

    private void summonDrone(ServerLevel serverLevel, ServerPlayer sp) {
        anchor = sp.position();

        CipherDroneEntity drone = new CipherDroneEntity(ModEntities.CIPHER_DRONE, serverLevel);
        // 出生点自动避让墙体：朝视线方向依次尝试候选位置，选第一个脚下+头顶都通透的
        Vec3 spawn = pickFreeSpawn(serverLevel, sp);
        drone.setPos(spawn);
        drone.setOwner(sp.getUUID());
        drone.setRush(false);
        drone.setPersistenceRequired(); // 防 Mob 通用 despawn 逻辑误清
        drone.setNoGravity(true);       // 无人机飞行不需要重力，也避免任何 fall damage
        serverLevel.addFreshEntity(drone);

        this.droneRef = drone;              // 直接持有引用
        this.droneId = drone.getUUID();
        this.droneActive = true;
        this.rush = false;
        this.droneGraceTicks = 60;          // 3 秒宽限期
        sync();

        serverLevel.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    /** 无人机出生点：优先正前方，若卡墙则逐步收回/抬高，最后兜底在头顶上方 */
    private static Vec3 pickFreeSpawn(ServerLevel level, ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 fwd = sp.getLookAngle();
        Vec3[] candidates = {
                eye.add(fwd.scale(1.5)).add(0, 0.6, 0),
                eye.add(fwd.scale(1.0)).add(0, 0.6, 0),
                eye.add(fwd.scale(0.5)).add(0, 0.4, 0),
                eye.add(0, 1.4, 0),
                sp.position().add(0, 2.6, 0),
                sp.position().add(0, 3.5, 0),
        };
        for (Vec3 c : candidates) {
            if (areaFree(level, c)) return c;
        }
        return candidates[candidates.length - 1];
    }

    /** 该位置脚下与头顶两格是否都通透（不阻挡移动） */
    private static boolean areaFree(ServerLevel level, Vec3 p) {
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(p.x, p.y, p.z);
        return passable(level, feet) && passable(level, feet.above());
    }

    private static boolean passable(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return !level.getBlockState(pos).blocksMotion();
    }

    /** 客户端拼写 finish 完成后请求关闭 */
    public void requestClose() {
        if (droneActive && player instanceof ServerPlayer sp) {
            closeDrone(sp.serverLevel(), true);
        }
    }

    /** 无人机被打碎 */
    public void onDroneBroken() {
        if (player instanceof ServerPlayer sp) {
            closeDrone(sp.serverLevel(), true);
        }
    }

    /** 冲锋(已装弹)无人机被打碎 → 在死亡点掉落一枚携带剩余引信的已激活落地 C4，可被别人拆弹 */
    public void onDroneRushDestroyed(ServerLevel serverLevel, Vec3 pos) {
        if (this.bombLoaded && bombDetonationAtGameTick >= 0L
                && serverLevel.getGameTime() < bombDetonationAtGameTick) {
            UUID planter = this.bombPlanterId != null ? this.bombPlanterId : player.getUUID();
            C4Detonation.dropArmedChargeAt(serverLevel, pos, planter, bombDetonationAtGameTick);
        }
        closeDrone(serverLevel, true);
    }

    /** 自杀式冲锋引爆：仅在无人机已装载悍匪 C4 时触发真实爆炸（击杀归悍匪），排除本体 */
    public void detonate() {
        if (!(player instanceof ServerPlayer sp)) return;
        ServerLevel serverLevel = sp.serverLevel();
        CipherDroneEntity drone = getDrone(serverLevel);
        Vec3 center = drone != null ? drone.position() : sp.position();

        int killed = 0;
        if (this.bombLoaded) {
            Player attacker = sp;
            if (this.bombPlanterId != null) {
                ServerPlayer planter = serverLevel.getServer().getPlayerList().getPlayer(this.bombPlanterId);
                if (planter != null) attacker = planter;
            }
            killed = C4Detonation.detonateAt(serverLevel, center, attacker, sp.getUUID());
            sp.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.MASTER, 1.0F, 1.0F);
        } else {
            // 空撞：机体解体特效，无爆炸击杀
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    center.x, center.y, center.z, 25, 0.35, 0.35, 0.35, 0.04);
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.METAL_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }

        this.blownPlayers = killed;
        this.blownEventId++;
        closeDrone(serverLevel, true);
    }

    /** 悍匪手持 C4 右键无人机调用此方法装载；成功返回 true，从悍匪手上扣 1 枚 C4 */
    public boolean loadBombFromGangster(ServerPlayer gangster, net.minecraft.world.InteractionHand hand, Vec3 dronePos) {
        if (!(player instanceof ServerPlayer owner)) return false;
        if (!(owner.level() instanceof ServerLevel serverLevel)) return false;
        if (this.bombLoaded) {
            gangster.displayClientMessage(Component.literal("无人机已经装过炸弹了"), true);
            return false;
        }
        if (this.droneActive && this.rush) {
            gangster.displayClientMessage(Component.literal("冲锋中的无人机无法再装弹"), true);
            return false;
        }
        ItemStack stack = gangster.getItemInHand(hand);
        if (!stack.is(org.agmas.noellesroles.init.ModItems.C4)) return false;
        stack.shrink(1);
        gangster.containerMenu.sendAllDataToRemote();

        this.bombLoaded = true;
        this.bombPlanterId = gangster.getUUID();
        serverLevel.playSound(null, dronePos.x, dronePos.y, dronePos.z,
                SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.9F);
        owner.displayClientMessage(
                Component.literal("悍匪 " + gangster.getName().getString() + " 已为无人机装载 C4"), true);
        gangster.displayClientMessage(Component.literal("已把炸弹装到密客无人机"), true);
        sync();
        return true;
    }

    private void closeDrone(ServerLevel serverLevel, boolean applyCooldown) {
        releaseDroneTicket();
        discardDrone();
        this.droneActive = false;
        this.rush = false;
        this.droneId = null;
        this.droneRef = null;
        this.droneGraceTicks = 0;
        this.skillInputGraceTicks = 40; // 关闭后再给 2 秒，防止同一波连点触发二次召唤
        this.c4CarrierId = null;        // 停止对已贴 C4 的 HUD 跟踪
        this.bombRemainingTicks = 0;
        this.bombDetonationAtGameTick = -1L;
        this.lastBombTenths = -1;
        this.bombLoaded = false;
        this.bombPlanterId = null;

        // 本体回锚点（防漂移）
        if (player instanceof ServerPlayer sp) {
            sp.teleportTo(anchor.x, anchor.y, anchor.z);
            sp.setDeltaMovement(Vec3.ZERO);
        }

        if (applyCooldown) {
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
            ability.cooldown = GameConstants.getInTicks(0, COOLDOWN_SECONDS);
            ability.sync();
        }
        sync();
    }

    /** 商店购买 EMP 充能 */
    public void addEmp() {
        if (emp < MAX_EMP) {
            emp++;
            sync();
        }
    }

    /** 无人机为某玩家贴上悍匪(GANGSTERS)的 C4：从悍匪背包取一枚，planter=悍匪（引爆赏金自动归悍匪） */
    public boolean plantDroneC4(ServerLevel level, ServerPlayer target) {
        if (!(player instanceof ServerPlayer owner)) return false;
        if (owner.getUUID().equals(target.getUUID())) return false;
        io.wifi.starrailexpress.cca.SREGameWorldComponent gameWorld =
                io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level);
        // 找到悍匪队友，作为贴弹者/收益归属者
        ServerPlayer gangster = null;
        for (ServerPlayer p : level.players()) {
            if (p.equals(target)) continue;
            if (gameWorld.isRole(p, org.agmas.noellesroles.role.ModRoles.GANGSTERS)) {
                gangster = p;
                break;
            }
        }
        if (gangster == null) {
            owner.displayClientMessage(Component.literal("场上没有悍匪，无法贴 C4"), true);
            return false;
        }
        ItemStack c4 = findItemInInventory(gangster, org.agmas.noellesroles.init.ModItems.C4);
        if (c4 == null) {
            owner.displayClientMessage(Component.literal("悍匪没有 C4 炸药"), true);
            return false;
        }
        org.agmas.noellesroles.cca.C4BackComponent c4comp =
                org.agmas.noellesroles.cca.C4BackComponent.KEY.getNullable(level);
        if (c4comp == null || c4comp.hasC4(target.getUUID())) return false;
        if (!c4comp.addC4(target.getUUID(), gangster.getUUID())) return false;
        c4.shrink(1);
        gangster.containerMenu.sendAllDataToRemote();

        this.c4CarrierId = target.getUUID();
        long rem = c4comp.ticksUntilDetonation(target.getUUID());
        this.c4RemainingSeconds = rem < 0 ? 0 : (int) Math.ceil(rem / 20.0);
        this.c4PredictedKills = org.agmas.noellesroles.game.c4.C4Detonation
                .predictDetonationKills(level, target.position());
        sync();

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.8F, 1.2F);
        target.displayClientMessage(Component.translatable("c4.you_have_c4"), false);
        owner.displayClientMessage(
                Component.literal("已为 " + target.getName().getString() + " 贴上悍匪的 C4"), true);
        return true;
    }

    @Nullable
    private static ItemStack findItemInInventory(Player p, net.minecraft.world.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(item)) return st;
        }
        return null;
    }

    @Nullable
    private CipherDroneEntity getDrone(ServerLevel serverLevel) {
        // 优先使用缓存引用；只在引用被移除时再回退到 UUID 查询
        if (droneRef != null && !droneRef.isRemoved()) return droneRef;
        if (droneId == null) return null;
        Entity e = serverLevel.getEntity(droneId);
        if (e instanceof CipherDroneEntity d) {
            this.droneRef = d;
            return d;
        }
        return null;
    }

    /** 公开访问当前存活的无人机引用（供服务端网络接收器等同步路径使用，避免 UUID 反查竞争） */
    @Nullable
    public CipherDroneEntity getDroneRef() {
        if (droneRef != null && !droneRef.isRemoved()) return droneRef;
        return null;
    }

    private void discardDrone() {
        if (droneRef != null && !droneRef.isRemoved()) {
            droneRef.discard();
        } else if (player.level() instanceof ServerLevel serverLevel) {
            CipherDroneEntity drone = getDrone(serverLevel);
            if (drone != null) drone.discard();
        }
        this.droneRef = null;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        if (skillInputGraceTicks > 0) skillInputGraceTicks--;
        if (!droneActive) return;

        // 本体钉在原地：清位移 + 禁移效果 + 漂移回拉；不加 TURN_BANED 以保留转视角
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0;
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, true, false, true));
        if (sp.distanceToSqr(anchor.x, anchor.y, anchor.z) > 2.25) {
            sp.teleportTo(anchor.x, anchor.y, anchor.z);
        }

        CipherDroneEntity drone = getDrone(sp.serverLevel());
        if (drone == null || !drone.isAlive()) {
            // 宽限期内不判定丢失；超出后再进入 onDroneBroken 兜底
            if (droneGraceTicks > 0) {
                droneGraceTicks--;
                return;
            }
            if (sp.level().getGameTime() % 10 == 0) {
                onDroneBroken();
            }
            return;
        }
        if (droneGraceTicks > 0) droneGraceTicks = 0; // 一旦查到就清零

        // 无人机贴的 C4：更新倒计时与预判人数，目标失效(引爆/掉落/拆除)则清除
        if (this.c4CarrierId != null) {
            org.agmas.noellesroles.cca.C4BackComponent c4comp =
                    org.agmas.noellesroles.cca.C4BackComponent.KEY.getNullable(sp.serverLevel());
            ServerPlayer carrier = c4comp == null ? null
                    : sp.getServer().getPlayerList().getPlayer(this.c4CarrierId);
            long rem = c4comp == null ? -1L : c4comp.ticksUntilDetonation(this.c4CarrierId);
            if (rem < 0L || carrier == null || carrier.isRemoved() || carrier.isSpectator()) {
                this.c4CarrierId = null;
                this.c4RemainingSeconds = 0;
                this.c4PredictedKills = 0;
                sync();
            } else {
                int secs = (int) Math.ceil(rem / 20.0);
                int kills = org.agmas.noellesroles.game.c4.C4Detonation
                        .predictDetonationKills(sp.serverLevel(), carrier.position());
                if (secs != this.c4RemainingSeconds || kills != this.c4PredictedKills) {
                    this.c4RemainingSeconds = secs;
                    this.c4PredictedKills = kills;
                    sync();
                }
            }
        }

        // 已装弹冲锋：引信倒计时；到点就地引爆
        if (this.rush && bombDetonationAtGameTick >= 0L) {
            long remaining = bombDetonationAtGameTick - sp.serverLevel().getGameTime();
            if (remaining <= 0L) {
                detonate();
                return;
            }
            this.bombRemainingTicks = (int) remaining;
            int tenths = (int) ((remaining + 1) / 2);
            if (tenths != this.lastBombTenths) {
                this.lastBombTenths = tenths;
                sync();
            }
        }

        // 强制加载无人机所在区块，保证它在锚点加载范围外仍持续 tick
        ChunkPos cp = new ChunkPos(drone.blockPosition());
        if (lastDroneTicketPos == null || !lastDroneTicketPos.equals(cp)) {
            releaseDroneTicket();
            sp.serverLevel().getChunkSource().addRegionTicket(
                    TicketType.POST_TELEPORT, cp, 1, drone.getId());
            lastDroneTicketPos = cp;
            lastDroneTicketHolder = drone.getId();
        }
    }

    /** 释放无人机区块票据 */
    private void releaseDroneTicket() {
        if (lastDroneTicketPos != null && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().removeRegionTicket(
                    TicketType.POST_TELEPORT, lastDroneTicketPos, 1, lastDroneTicketHolder);
        }
        lastDroneTicketPos = null;
        lastDroneTicketHolder = 0;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("droneActive", droneActive);
        tag.putBoolean("rush", rush);
        tag.putInt("emp", emp);
        if (droneId != null) tag.putUUID("droneId", droneId);
        tag.putInt("blownPlayers", blownPlayers);
        tag.putInt("blownEventId", blownEventId);
        if (c4CarrierId != null) tag.putUUID("c4CarrierId", c4CarrierId);
        tag.putInt("c4RemainingSeconds", c4RemainingSeconds);
        tag.putInt("c4PredictedKills", c4PredictedKills);
        tag.putBoolean("bombLoaded", bombLoaded);
        if (bombPlanterId != null) tag.putUUID("bombPlanterId", bombPlanterId);
        tag.putInt("bombRemainingTicks", bombRemainingTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        boolean prevActive = this.droneActive;
        boolean prevRush = this.rush;
        int prevBlownEventId = this.blownEventId;

        this.droneActive = tag.getBoolean("droneActive");
        this.rush = tag.getBoolean("rush");
        this.emp = tag.getInt("emp");
        this.droneId = tag.hasUUID("droneId") ? tag.getUUID("droneId") : null;
        this.blownPlayers = tag.getInt("blownPlayers");
        this.blownEventId = tag.getInt("blownEventId");
        this.c4CarrierId = tag.hasUUID("c4CarrierId") ? tag.getUUID("c4CarrierId") : null;
        this.c4RemainingSeconds = tag.getInt("c4RemainingSeconds");
        this.c4PredictedKills = tag.getInt("c4PredictedKills");
        this.bombLoaded = tag.getBoolean("bombLoaded");
        this.bombPlanterId = tag.hasUUID("bombPlanterId") ? tag.getUUID("bombPlanterId") : null;
        this.bombRemainingTicks = tag.getInt("bombRemainingTicks");

        // 驱动客户端转场
        if (!prevActive && this.droneActive) {
            CipherClientState.onEnterDrone();
        } else if (prevActive && !this.droneActive) {
            CipherClientState.onExitDrone();
        }
        if (!prevRush && this.rush) {
            CipherClientState.onRushStart();
        }
        // 引爆事件变化 → 客户端弹出"炸飞了X人！"提示（仅当确实炸到人）
        if (this.blownEventId != prevBlownEventId && this.blownPlayers > 0) {
            CipherClientState.onBlown(this.blownPlayers);
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}