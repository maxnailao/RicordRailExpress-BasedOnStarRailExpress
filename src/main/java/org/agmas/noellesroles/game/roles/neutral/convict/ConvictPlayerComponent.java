package org.agmas.noellesroles.game.roles.neutral.convict;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.item.ConvictHandcuffsItem;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 重刑犯角色组件 - 中立独立胜利（三分支玩法核心状态载体）
 *
 * <p>存储重刑犯一局内的全部玩法状态，并在服务端与客户端之间同步（供抉择 GUI、透视渲染、
 * 押运拴绳等使用）。所有分支判定与交互都读写这里的字段。</p>
 *
 * <ul>
 *   <li>{@link Choice} - 开局「做出你的抉择」的结果：改过自新 / 毁灭一切 / 加入组织</li>
 *   <li>{@link #handcuffRemoved} - 手铐是否已被解除（解除后才触发对应分支）</li>
 *   <li>{@link #espUnlocked} - 透视是否解锁（毁灭一切分支解铐后为 true，同步给 InstinctRenderer）</li>
 *   <li>{@link #leashedBy} - 被哪个狱警用押运工具拴住</li>
 *   <li>{@link #choiceTimeLeftTicks} - 抉择 GUI 剩余计时（超时默认毁灭一切）</li>
 * </ul>
 */
public class ConvictPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ConvictPlayerComponent> KEY = ModComponents.CONVICT;

    /** 开局抉择分支 */
    public enum Choice {
        /** 尚未选择 */
        NONE,
        /** 改过自新：解铐后可捡枪，捡起左轮/巡警手枪转职为狱警 */
        REFORM,
        /** 毁灭一切：解铐后全局透视、无法捡枪、开启个人商店、击杀所有人独赢 */
        DESTROY,
        /** 加入组织：无法捡枪，被杀手解铐变解铐者职业，被警长阵营解铐变普通杀手 */
        JOIN
    }

    private final Player player;

    /** 当前抉择分支 */
    public Choice choice = Choice.NONE;
    /** 手铐是否已被解除 */
    public boolean handcuffRemoved = false;
    /** 透视是否已解锁（同步到客户端供 InstinctRenderer 使用） */
    public boolean espUnlocked = false;
    /** 是否已购买过左轮（限购一次标记） */
    public boolean hasBoughtRevolver = false;
    /** 是否已购买过警棍（限购一次标记） */
    public boolean hasBoughtBaton = false;
    /** 被哪个狱警用押运工具拴住（null 表示未被拴住） */
    public UUID leashedBy = null;
    /** 被谁解除手铐（用于「加入组织」分支转职为解救者的身份；null 表示尚未解铐）。仅服务端使用，不同步。 */
    public UUID uncuffedBy = null;

    /** 抉择 GUI 是否已开启 */
    public boolean choiceGuiOpened = false;
    /** 抉择剩余计时（tick）；<=0 表示未在计时 */
    public int choiceTimeLeftTicks = 0;
    /** 被动收入计时（tick）；<=0 表示到达发放节点，重新装填间隔 */
    private int passiveIncomeTicksLeft = 0;

    /** 押运拴绳牵引阈值：重刑犯与狱警距离超过此值即被牵引收回 */
    private static final double LEASH_MAX_DISTANCE = 6.0;
    /** 牵引后重刑犯与狱警保持的目标距离 */
    private static final double LEASH_PULL_TO = 3.0;

    public ConvictPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        resetState();
        sync();
    }

    @Override
    public void clear() {
        resetState();
        sync();
    }

    private void resetState() {
        choice = Choice.NONE;
        handcuffRemoved = false;
        espUnlocked = false;
        hasBoughtRevolver = false;
        hasBoughtBaton = false;
        leashedBy = null;
        uncuffedBy = null;
        choiceGuiOpened = false;
        choiceTimeLeftTicks = 0;
        passiveIncomeTicksLeft = 0;
    }

    public void sync() {
        ModComponents.CONVICT.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void serverTick() {
        // 抉择计时：GUI 已开启且尚未选择时倒计时，超时默认「毁灭一切」。
        // 非重刑犯玩家 choiceGuiOpened 恒为 false，此处为廉价空操作。
        if (choiceGuiOpened && choice == Choice.NONE && choiceTimeLeftTicks > 0) {
            choiceTimeLeftTicks--;
            if (choiceTimeLeftTicks <= 0 && player instanceof ServerPlayer sp) {
                ConvictChoiceManager.applyChoice(sp, Choice.DESTROY);
            }
        }
        // 押运拴绳跟随：仅对被拴住的重刑犯生效，非重刑犯 leashedBy 恒为 null。
        if (leashedBy != null) {
            tickLeash();
        }
        if (player instanceof ServerPlayer sp) {
            tickActiveConvict(sp);
        }
    }

    /**
     * 存活重刑犯的周期性逻辑：
     * <ul>
     *   <li>毁灭一切：手铐已不在但透视未解锁时补解锁（覆盖「先解铐后抉择/超时」等顺序，
     *       防止分支效果永久缺失）；</li>
     *   <li>被动收入：按配置的间隔/金额周期性发放金币（不注册 RolePassive，因此不占用 HUD 被动栏）。</li>
     * </ul>
     */
    private void tickActiveConvict(ServerPlayer sp) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.CONVICT)
                || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        // 毁灭一切：手铐已不在（无论经由何种方式解除）即应有透视，未解锁则补解锁
        if (choice == Choice.DESTROY && !espUnlocked && isHandcuffGone()) {
            espUnlocked = true;
            sync();
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.convict.destroy_unlocked").withStyle(ChatFormatting.RED),
                    true);
        }
        // 被动收入：金额/间隔取自配置
        if (passiveIncomeTicksLeft <= 0) {
            passiveIncomeTicksLeft =
                    Math.max(1, NoellesRolesConfig.instance().convictPassiveIncomeIntervalSeconds) * 20;
        }
        passiveIncomeTicksLeft--;
        if (passiveIncomeTicksLeft <= 0) {
            int amount = NoellesRolesConfig.instance().convictPassiveIncomeAmount;
            if (amount > 0) {
                SREPlayerShopComponent.KEY.get(sp).addToBalance(amount);
            }
        }
    }

    /** 手铐是否已不在身上：解除标记已置位，或额外槽中已无重刑犯手铐。 */
    public boolean isHandcuffGone() {
        return handcuffRemoved || !ConvictHandcuffsItem.hasConvictHandCuff(player);
    }

    /** 改过自新分支是否已可捡枪：已选择改过自新且手铐已解除。 */
    public boolean canReformPickUpGun() {
        return choice == Choice.REFORM && isHandcuffGone();
    }

    /**
     * 押运拴绳每 tick 跟随：
     * <ul>
     *   <li>重刑犯死亡 / 转职（加入组织、改过自新捡枪变狱警）→ 自动解除拴绳；</li>
     *   <li>狱警离线 / 死亡 → 自动解除拴绳；</li>
     *   <li>距离超过 {@link #LEASH_MAX_DISTANCE} → 碰撞安全地把重刑犯牵引收回（限速：仅超阈值时触发）。</li>
     * </ul>
     */
    private void tickLeash() {
        if (!(player instanceof ServerPlayer convict) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        // 仅对存活的重刑犯生效；转职或死亡后解除
        if (!GameUtils.isPlayerAliveAndSurvival(convict) || !gameWorld.isRole(convict, ModRoles.CONVICT)) {
            leashedBy = null;
            sync();
            return;
        }
        // getPlayerByUUID 返回 Player（离线时为 null）；用 instanceof 收窄为 ServerPlayer 并校验存活
        if (!(level.getPlayerByUUID(leashedBy) instanceof ServerPlayer jailer)
                || !GameUtils.isPlayerAliveAndSurvival(jailer)) {
            leashedBy = null;
            sync();
            return;
        }
        if (convict.distanceToSqr(jailer) > LEASH_MAX_DISTANCE * LEASH_MAX_DISTANCE) {
            reelIn(jailer, convict);
        }
    }

    /**
     * 把被拴住的重刑犯牵引到狱警身旁：沿「狱警→重刑犯」水平方向收缩到 {@link #LEASH_PULL_TO}，
     * 逐级做碰撞检测（避免卡进方块），全部失败时回退到狱警脚下。
     * 纯位移，不造成伤害、不修改 lastHurtBy（押运是持续行为，不应污染击杀归属）。
     */
    private static void reelIn(ServerPlayer jailer, ServerPlayer convict) {
        ServerLevel level = jailer.serverLevel();
        Vec3 delta = convict.position().subtract(jailer.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double nx;
        double nz;
        if (horizontal > 1.0E-4) {
            nx = delta.x / horizontal;
            nz = delta.z / horizontal;
        } else {
            // 与狱警几乎重叠：用狱警朝向反方向作为默认牵引方向
            Vec3 view = jailer.getViewVector(1.0F);
            double vl = Math.sqrt(view.x * view.x + view.z * view.z);
            nx = vl > 1.0E-4 ? -view.x / vl : 0.0;
            nz = vl > 1.0E-4 ? -view.z / vl : 0.0;
        }
        for (double d = LEASH_PULL_TO; d >= 0.5; d -= 0.5) {
            double x = jailer.getX() + nx * d;
            double y = jailer.getY();
            double z = jailer.getZ() + nz * d;
            AABB box = convict.getBoundingBox()
                    .move(x - convict.getX(), y - convict.getY(), z - convict.getZ());
            if (level.noCollision(convict, box)) {
                convict.teleportTo(x, y, z);
                return;
            }
        }
        convict.teleportTo(jailer.getX(), jailer.getY(), jailer.getZ());
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Choice", choice.ordinal());
        tag.putBoolean("HandcuffRemoved", handcuffRemoved);
        tag.putBoolean("EspUnlocked", espUnlocked);
        tag.putBoolean("HasBoughtRevolver", hasBoughtRevolver);
        tag.putBoolean("HasBoughtBaton", hasBoughtBaton);
        tag.putBoolean("ChoiceGuiOpened", choiceGuiOpened);
        tag.putInt("ChoiceTimeLeftTicks", choiceTimeLeftTicks);
        tag.putUUID("LeashedBy", leashedBy == null ? new UUID(0L, 0L) : leashedBy);
        tag.putBoolean("HasLeashedBy", leashedBy != null);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        choice = choiceFromOrdinal(tag.getInt("Choice"));
        handcuffRemoved = tag.getBoolean("HandcuffRemoved");
        espUnlocked = tag.getBoolean("EspUnlocked");
        hasBoughtRevolver = tag.getBoolean("HasBoughtRevolver");
        hasBoughtBaton = tag.getBoolean("HasBoughtBaton");
        choiceGuiOpened = tag.getBoolean("ChoiceGuiOpened");
        choiceTimeLeftTicks = tag.getInt("ChoiceTimeLeftTicks");
        leashedBy = tag.getBoolean("HasLeashedBy") ? tag.getUUID("LeashedBy") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Choice", choice.ordinal());
        tag.putBoolean("HandcuffRemoved", handcuffRemoved);
        tag.putBoolean("EspUnlocked", espUnlocked);
        tag.putBoolean("HasBoughtRevolver", hasBoughtRevolver);
        tag.putBoolean("HasBoughtBaton", hasBoughtBaton);
        tag.putBoolean("ChoiceGuiOpened", choiceGuiOpened);
        tag.putInt("ChoiceTimeLeftTicks", choiceTimeLeftTicks);
        tag.putUUID("LeashedBy", leashedBy == null ? new UUID(0L, 0L) : leashedBy);
        tag.putBoolean("HasLeashedBy", leashedBy != null);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        choice = choiceFromOrdinal(tag.getInt("Choice"));
        handcuffRemoved = tag.getBoolean("HandcuffRemoved");
        espUnlocked = tag.getBoolean("EspUnlocked");
        hasBoughtRevolver = tag.getBoolean("HasBoughtRevolver");
        hasBoughtBaton = tag.getBoolean("HasBoughtBaton");
        choiceGuiOpened = tag.getBoolean("ChoiceGuiOpened");
        choiceTimeLeftTicks = tag.getInt("ChoiceTimeLeftTicks");
        leashedBy = tag.getBoolean("HasLeashedBy") ? tag.getUUID("LeashedBy") : null;
    }

    private static Choice choiceFromOrdinal(int ordinal) {
        Choice[] values = Choice.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Choice.NONE;
        }
        return values[ordinal];
    }
}
