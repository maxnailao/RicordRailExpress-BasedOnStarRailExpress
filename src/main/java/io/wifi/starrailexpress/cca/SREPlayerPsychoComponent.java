package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;

public class SREPlayerPsychoComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerPsychoComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("psycho"),
            SREPlayerPsychoComponent.class);
    private final Player player;
    public int psychoTicks = -1;
    public int armour = 1;
    public int type = -1;
    /**
     * 「只借皮肤」的疯狂模式：只用于客户端的狂暴皮肤 / 疯狂 HUD 表现，
     * 不占用全局疯狂计数、不给护盾、不会强制切换手持物，
     * 结束时也不清球棒、不触发职业的 onPsychoOver 回调。
     * <p>典型来源：幻术师·幻影陷阱的「扣除 25% 理智」惩罚。
     * <p>这类状态必须由调用方自己把 armour 归零，否则默认的 1 层就是白送的护盾。
     */
    public boolean cosmeticPsycho = false;
    private SREGameWorldComponent gameWorldComponent = null;

    public SREPlayerPsychoComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        if (checkIsGameRunning())
            return true;
        return false;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.stopPsychoAndRefreshPsychoCount(true);
        this.sync();
        this.psychoTicks = -1;
    }

    public void resetNotSync() {
        this.stopPsychoAndRefreshPsychoCount(false);
        this.psychoTicks = -1;
    }

    @Override
    public void clientTick() {
        if (!checkIsGameRunning()) {
            if (this.psychoTicks > 0)
                this.psychoTicks = -1;
            return;
        }

        if (this.psychoTicks <= 0)
            return;
        if (this.psychoTicks > 1) {
            if (this.player.isSpectator()) {
                this.psychoTicks = -1;
                return;
            }
            this.psychoTicks--;
        }
        // 只借皮肤：不强制把手持物切回球棒/疯魔武器
        if (this.cosmeticPsycho)
            return;
        Item psychoItem = TMMItems.BAT;
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (role != null) {
            psychoItem = role.getPsychoItem();
        }
        if (this.player.getMainHandItem().is(psychoItem))
            return;
        // 幽灵幻影疯魔(type=2)：允许切换空手，不强制切回刀
        if (this.type == 2 && this.player.getMainHandItem().isEmpty())
            return;
        if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            for (int i = 0; i < 9; i++) {
                if (!this.player.getInventory().getItem(i).is(psychoItem))
                    continue;
                this.player.getInventory().selected = i;
                break;
            }
        }

    }

    @Override
    public void serverTick() {
        if (!checkIsGameRunning()) {
            if (this.psychoTicks > 0) {
                this.stopPsycho();
            }
            return;
        }
        if (this.psychoTicks <= 0)
            return;
        if (this.psychoTicks > 0) {
            if (this.player.isSpectator()) {
                this.stopPsychoAndRefreshPsychoCount(true);
                return;
            }
        }
        if (--this.psychoTicks == 0) {
            this.stopPsycho();
            this.sync();
        } else {
            if (this.psychoTicks % 200 == 0) { // 10s一次
                this.sync();
            }
        }

    }

    public boolean startPsycho() {
        return startPsycho(1d, GameConstants.getPsychoModeArmour());
    }

    public boolean startPsycho_time(int time, int armour) {
        if (this.psychoTicks > 0)
            return false;
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(this.player);
        boolean success = false;
        if (role != null) {
            success = role.onPsychoGiveItem(player, this);
        } else {
            success = RoleUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.BAT));
        }
        if (success) {
            this.setPsychoTicks(time);
            this.setArmour(armour);
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
            gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);
            if (player instanceof ServerPlayer serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
            }
            if (role != null) {
                role.onPsychoStart(player, this);
            }
            return true;
        }
        return false;
    }

    /** Returns whether this player currently has an active psycho state. */
    public boolean inPsycho() {
        return this.psychoTicks > 0;
    }

    /** Compatibility overload used by event controllers that also pass a sync flag. */
    public boolean startPsycho_time(int time, int armour, boolean sync) {
        boolean started = startPsycho_time(time, armour);
        if (started && sync) {
            sync();
        }
        return started;
    }

    public boolean startPsycho(double multtiplier, int armour) {
        return startPsycho_time((int) ((double) GameConstants.getPsychoTimer() * multtiplier), armour);
    }

    @Override
    public void clear() {
        init();
    }

    public int stopPsychoAndSync() {
        int result = stopPsycho();
        sync();
        return result;
    }

    public int stopPsycho() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        int result = gameWorldComponent.getPsychosActive();
        boolean cosmetic = this.cosmeticPsycho;
        this.cosmeticPsycho = false;
        // 只借皮肤的状态从未占用过全局疯狂计数，自然也不该把它减回去
        if (!cosmetic && result >= 1) {
            gameWorldComponent.setPsychosActive(result - 1);
        }
        this.psychoTicks = -1;
        if (!cosmetic && this.player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new RemoveStatusBarPayload("Psycho"));
        }
        // 只借皮肤：不碰背包里的球棒，也不触发职业的疯魔结束回调
        if (cosmetic)
            return result;

        Item psychoItem = TMMItems.BAT;
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (role != null) {
            psychoItem = role.getPsychoItem();
        }
        MCItemsUtils.clearItem(player, psychoItem);
        if (checkIsGameRunning()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                if (role != null) {
                    role.onPsychoOver(player, this);
                }
            }
        }
        return result;
    }

    public void stopPsychoAndRefreshPsychoCount(boolean shouldSync) {
        if (this.stopPsycho() > 0) {
            int count = 0;
            if (this.player instanceof ServerPlayer sp) {
                var players = sp.level().players();
                for (var pl : players) {
                    var ppc = SREPlayerPsychoComponent.KEY.maybeGet(pl).orElse(null);
                    if (ppc != null) {
                        if (ppc.psychoTicks > 0) {
                            count++;
                        }
                    }
                }
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
            gameWorldComponent.setPsychosActive(count, shouldSync);
        }

    }

    public int getArmour() {
        return this.armour;
    }

    public void setArmour(int armour) {
        this.armour = armour;
        this.sync();
    }

    public int getPsychoTicks() {
        return this.psychoTicks;
    }

    public void setPsychoTicks(int ticks) {
        this.psychoTicks = ticks;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    public boolean checkIsGameRunning() {
        gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        return gameWorldComponent.isRunning();
    }

    // ==================== 疯魔武器判定（切槽限制共用） ====================
    // 移植自 StarRailExpress。服务端在疯魔期间会丢弃「切到非法武器」的选栏包，
    // 但像 Dream 这种「疯魔物品从不入包」（巨幕面具）的职业，如果不区分
    // 「手上有没有合法疯魔武器」，就会把所有选栏包全部丢掉，
    // 导致服务端主手与客户端脱同步 —— 表现就是狂暴期间打不了人。

    /** 该职业在疯魔模式下的兼容武器列表。 */
    private List<Item> getPsychoSupportedWeapons(SRERole role) {
        if (role == null) {
            return List.of(TMMItems.BAT);
        }
        List<Item> weapons = role.getPsychoSupportedWeapons(player);
        return weapons == null ? List.of() : weapons.stream().filter(item -> item != null).distinct().toList();
    }

    private boolean isPsychoSupportedWeapon(SRERole role, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return role == null ? stack.is(TMMItems.BAT) : role.isPsychoSupportedWeapon(player, stack);
    }

    /** 供切槽、滚轮等输入限制共用。 */
    public boolean isPsychoSupportedWeapon(ItemStack stack) {
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        return isPsychoSupportedWeapon(role, stack);
    }

    /** 玩家（含副手与背包）当前是否真的持有至少一件该职业的疯魔兼容武器。 */
    public boolean hasPsychoSupportedWeapon() {
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        return isPsychoSupportedWeapon(role, player.getOffhandItem())
                || findPsychoWeaponSlot(role, player.getInventory().getContainerSize()) >= 0;
    }

    private int findPsychoWeaponSlot(SRERole role, int slotLimit) {
        int limit = Math.min(slotLimit, player.getInventory().items.size());
        for (Item weapon : getPsychoSupportedWeapons(role)) {
            for (int slot = 0; slot < limit; slot++) {
                if (player.getInventory().getItem(slot).is(weapon)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putInt("psychoTicks", this.psychoTicks);
        tag.putInt("armour", this.armour);
        tag.putInt("type", this.type);
        tag.putBoolean("cosmeticPsycho", this.cosmeticPsycho);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        this.psychoTicks = tag.contains("psychoTicks") ? tag.getInt("psychoTicks") : 0;
        this.armour = tag.contains("armour") ? tag.getInt("armour") : 1;
        this.type = tag.contains("type") ? tag.getInt("type") : -1;
        this.cosmeticPsycho = tag.getBoolean("cosmeticPsycho");
    }
}
