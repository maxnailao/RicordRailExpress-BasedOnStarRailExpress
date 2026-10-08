
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
     * 自选职业卡黑名单：按职业 {@link ResourceLocation} 的 path 精确禁用（跨命名空间通用）。
     * <p>
     * 名单里的职业要么是靠转化 / 绑定 / 体系 / 玩法生成的派生职业，要么是被明确禁止自选的职业。
     * 命中即 {@link #isSelfSelectableRole(SRERole)} 返回 {@code false}，客户端界面与服务端命令同时生效。
     * 其中 majo / guardian / parasol / nutritionist / mafioso / janitor / qingsuanzhe_morelooseend
     * 也已带 {@code setSelfSelectable(false)} 标记，这里再列入以保证单一可审计来源。
     * </p>
     */
    public static final Set<String> SELF_SELECT_DENY_PATHS = Set.of(
            "jojo", "guest_ghost", "ma_chen_xu", "banyanzhe", "dio", "poisoner", "gangsters",
            "majo", "shadow_falcon", "witch_accomplice", "convict", "zhuimu_dream", "loose_end",
            "initiate", "morichika_rinnosuke", "kawashiro_nitori", "furandoru", "super_loose_end",
            "pilot", "guardian", "jingjiren_wow", "star", "singer", "fitter", "water_ghost",
            "diver", "monokuma", "parasol", "nutritionist", "mafioso", "janitor",
            "pigegade_piggod", "qingsuanzhe_morelooseend",
            // ── 「必须由别的职业产生」的职业 ──
            // 权威名单是 SelfSelectGate.GENERATED_ONLY_PATHS（它先于彩蛋例外生效，
            // 能挡住"彩蛋可直选"的绕过）。这里**再列一遍做双保险**，
            // 但两边都不允许出现重复元素（Set.of 会抛 IllegalArgumentException）。
            //   工蜂 / 马蜂       —— 蜂后召唤
            //   医生 / 锁匠 / 钳工 —— 绑定副职业（毒师→医生、工程师→锁匠、悍匪→钳工）
            //   猫娘杀手 / 操纵师 / 傀儡师 —— 由别的职业产生
            //   黑手党 / 清洁工 / 营养师 / 阳伞 —— 教父家族（上面已列，勿重复）
            "bee_worker", "bee_wasp", "doctor", "locksmith",
            "cat_killer", "manipulator", "puppeteer");

    /**
     * 是否可由「自选职业卡」选择。
     * <p>
     * 只允许谋杀模式的职业：排除原版基础职业（{@code VANNILA_ROLES}）、其他模式职业
     * 与特殊地图限定职业。此外排除不会自然刷新的职业
     * （{@code defaultMaxCount <= 0}，如操纵师）；彩蛋职业（{@link EggRole}，如迪奥）
     * 与警长阵营虽默认不刷新，但作为特殊职业/阵营仍允许自选。
     * </p>
     * <p>
     * 注意「特殊地图限定职业」靠 {@link SRERole#isSpecialMapRole()} 判定，
     * 因此只在特定地图刷新的职业必须注册 {@code setSpecialMapRole(...)}
     * （监狱图的重刑犯 / 狱警用 {@code PRISON}）。若某个职业的地图门禁只写在
     * {@code getRoundMaxCount} 里、没打 specialMapRole 标记，就会被这里漏放而可自选。
     * </p>
     * <p>
     * 转化 / 派生生成的职业（如魔女、强化巡警、亡命徒变体、黑手党链）不是地图职业，
     * 必须显式注册 {@code setSelfSelectable(false)}，见 {@link SRERole#isSelfSelectable()}。
     * </p>
     */
    public static boolean isSelfSelectableRole(SRERole role) {
        if (role == null) {
            return false;
        }
        // 自选黑名单：命中即禁用（按 path 精确匹配）
        if (SELF_SELECT_DENY_PATHS.contains(role.identifier().getPath())) {
            return false;
        }
        // 显式声明不可自选（转化 / 派生生成的职业走这里）
        if (!role.isSelfSelectable()) {
            return false;
        }
        // 只允许谋杀模式的职业（对齐 SREMurderGameMode.getAllRoles 的池构建）
        if (Harpymodloader.VANNILA_ROLES.contains(role)
                || role.isOtherModeRole()
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
