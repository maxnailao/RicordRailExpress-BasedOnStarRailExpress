
package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.*;

public class TMMRoles {
    public static final Map<ResourceLocation, SRERole> ROLES = new HashMap<>();
    public static final List<ComponentKey<? extends RoleComponent>> COMPONENT_KEYS = new ArrayList<>();
    public static final SRERole DISCOVERY_CIVILIAN = registerRole(
            new OriginalRole(SRE.id("discovery_civilian"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true))
            .setCanPickUpRevolver(false).setNeutrals(true).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);
    public static final SRERole CIVILIAN = registerRole(new OriginalRole(SRE.id("civilian"), 0x36E51B, true, false,
            SRERole.MoodType.REAL, GameConstants.getInTicks(0, 10), false));
    public static final SRERole VIGILANTE = registerRole(new OriginalRole(SRE.id("vigilante"), 0x1B8AE5, true, false,
            SRERole.MoodType.REAL, GameConstants.getInTicks(0, 10), false) {
        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of(new ItemStack(TMMItems.REVOLVER).copy());
        }
    }.setVigilanteTeam(true).setDefaultMax(0).setCanSetSpawnInfoInConfig(false));
    public static final SRERole KILLER = registerRole(
            new OriginalRole(SRE.id("killer"), 0xC13838, false, true, SRERole.MoodType.FAKE, -1, true));
    public static final SRERole LOOSE_END = registerRole(
            new LooseEndRole(SRE.id("loose_end"), 0x9F0000, false, false, SRERole.MoodType.NONE, -1, false,
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            30 * 20, // 持续时间 60s（tick）
                            2, // 等级（0 = 速度 I）
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    )))
            .setCanSeeTime(true).setCanUseInstinct(true).setCanBeRandomedByOtherRoles(false);

    public static SRERole registerRole(SRERole role, String... flags) {
        return registerRole(role.addFlag(flags));
    }

    public static SRERole registerRole(SRERole role) {
        ROLES.put(role.identifier(), role);
        if (role.getComponentKey() != null) {
            COMPONENT_KEYS.add(role.getComponentKey());
        }
        return role;
    }

    public static void addRoleComponents(ComponentKey<? extends RoleComponent> componentKeyToAdd) {
        COMPONENT_KEYS.add(componentKeyToAdd);
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : ROLES.values()) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }

    public static SRERole getRole(ResourceLocation id) {
        return ROLES.getOrDefault(id, null);
    }

    /**
     * 是否可由「自选职业卡」选择。
     * <p>
     * 只允许谋杀模式的职业：排除原版基础职业（{@code VANNILA_ROLES}）、其他模式职业、
     * 修理逃脱模式职业（{@link RepairRole}）与特殊地图限定职业。此外排除不会自然刷新的职业
     * （{@code defaultMaxCount <= 0}，如操纵师）；彩蛋职业（{@link EggRole}，如迪奥）
     * 与警长阵营虽默认不刷新，但作为特殊职业/阵营仍允许自选。
     * </p>
     */
    public static boolean isSelfSelectableRole(SRERole role) {
        if (role == null) {
            return false;
        }
        // 只允许谋杀模式的职业（对齐 SREMurderGameMode.getAllRoles 的池构建）
        if (Harpymodloader.VANNILA_ROLES.contains(role)
                || role.isOtherModeRole()
                || role instanceof RepairRole
                || role.isSpecialMapRole()) {
            return false;
        }
        // 不会自然刷新的职业不可自选；彩蛋职业（如迪奥）与警长阵营例外。
        if (role.defaultMaxCount <= 0 && !(role instanceof EggRole) && !role.isVigilanteTeam()) {
            return false;
        }
        return true;
    }
}
