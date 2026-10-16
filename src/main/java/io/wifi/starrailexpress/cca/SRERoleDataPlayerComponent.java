/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.utils.RoleUtils;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 玩家职业数据组件（{@link RoleData} 的载体）。
 *
 * <p>
 * 它把「当前职业对应的数据实例」挂在玩家身上，并在客户端 / 服务端之间同步：
 * </p>
 * <ul>
 * <li>服务端：换职业时 {@link #serverInit()} 依据 {@code SRERole#getRoleDataFunc()} 新建实例并同步；</li>
 * <li>客户端：收到 {@code __init__} 包时按职业 path 还原职业并新建本地实例（{@link #clientInit}）；</li>
 * <li>每 tick 转发 {@link RoleData#serverTick()} / {@link RoleData#clientTick()}。</li>
 * </ul>
 *
 * <p>具体职业逻辑请写在各自的 {@code RoleData} 实现类里，不要写进本组件。</p>
 */
public class SRERoleDataPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public RoleData roleData = null;
    public SRERole playerRole = null;
    private boolean initSync = false;

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        if (roleData != null) {
            return (initSync && p == player) || roleData.shouldSyncWith(p);
        }
        return player == p;
    }

    /**
     * 构造函数
     */
    public SRERoleDataPlayerComponent(Player player) {
        this.player = player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<SRERoleDataPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("role_data"),
            SRERoleDataPlayerComponent.class);

    // 持有该组件的玩家
    private final Player player;
    private boolean forceClear = false;

    @Override
    public void clientTick() {
        if (SREGameWorldComponent.getInstance(player).isRunning()) {
            if (roleData != null)
                roleData.clientTick();
        }
    }

    @Override
    public void serverTick() {
        if (SREGameWorldComponent.getInstance(player).isRunning()) {
            if (roleData != null)
                roleData.serverTick();
        }
    }

    @Override
    public void init() {
        if (!player.level().isClientSide)
            serverInit();
        else
            clientInit(RoleUtils.getPlayerRole(player));
    }

    public void serverInit() {
        final var cca = SREGameWorldComponent.getInstance(player);
        playerRole = cca.getRole(player);
        if (playerRole == null) {
            clear();
            return;
        }
        final var roleDataFunc = playerRole.getRoleDataFunc();
        final RoleDataContext ctx = new RoleDataContext(player, playerRole, () -> {
            sync();
        }, (p) -> {
            syncTo(p);
        });
        if (roleDataFunc != null) {
            roleData = roleDataFunc.apply(ctx);
        }
        if (roleData != null) {
            initSync = true;
            sync();
            initSync = false;
            roleData.init();
        }
    }

    public void clientInit(SRERole playerRole) {
        if (playerRole == null) {
            clear();
            return;
        }
        final var roleDataFunc = playerRole.getRoleDataFunc();
        final RoleDataContext ctx = new RoleDataContext(player, playerRole, null, null);
        if (roleDataFunc != null) {
            roleData = roleDataFunc.apply(ctx);
            roleData.initOnClient();
        }
    }

    @Override
    /** 仅当玩家重置时使用，切换职业不使用 */
    public void clear() {
        if (roleData != null) {
            roleData.clear();
        }
        playerRole = null;
        roleData = null;
        initSync = false;
        forceClear = true;
        sync();
    }

    /**
     * 同步到指定玩家客户端
     */
    public void syncTo(ServerPlayer p) {
        initSync = false;
        KEY.syncWith(p, player.asComponentProvider());
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        if (player.level().isClientSide)
            return;
        KEY.sync(this.player);
        initSync = false;
        forceClear = false;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        if (initSync) {
            tag.putString("__init__", playerRole != null ? playerRole.identifier().getPath() : "");
            return;
        }
        if (forceClear) {
            tag.putBoolean("__clear__", true);
            return;
        }
        if (roleData != null) {
            roleData.writeToSyncNbt(tag, registryLookup);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        if (tag.contains("__init__")) {
            if (roleData != null) {
                clear();
            }
            String rolePath = tag.getString("__init__");
            playerRole = RoleUtils.getRoleByPath(rolePath);
            clientInit(playerRole);
            return;
        }
        if (tag.contains("__clear__")) {
            clear();
            return;
        }
        if (roleData == null) {
            clientInit(RoleUtils.getPlayerRole(player));
        }

        if (roleData != null) {
            roleData.readFromSyncNbt(tag, registryLookup);
        }
    }

    public void onRemoveRole() {
        if (roleData != null) {
            roleData.clear();
        }
        playerRole = null;
        roleData = null;
        initSync = false;
        forceClear = false;
        // sync();
    }

}
