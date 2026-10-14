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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.network.MapDisplayInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「该地图会出现哪些特殊职业」的展示行。
 *
 * <p>
 * 完全基于服务端解析好的 {@link MapDisplayInfo}：地图特性字符串里包含的
 * {@link SRERole.SpecialMapRoleMap} 名称与在册职业的 {@code getSpecialMapRole()} 相互匹配。
 *
 * <p>
 * 特性名与 {@code SpecialMapRoleMap} 枚举名一一对应（QIYUCUN / UNDERWATER / BIGMAP /
 * FLY / TRAP / SNOW / DESERT / WITCH_PRISON / PRISON），由服务端的
 * {@code MapIntroData} 从地图自身 {@code customMapFeatures} 与旧 NoellesRolesConfig 名单合并得到。
 */
public final class MapSpecialRoleLines {

    private MapSpecialRoleLines() {
    }

    /** 显示顺序：先配置列表类（按名单刷新职业的地图）。 */
    private static final SRERole.SpecialMapRoleMap[] DISPLAY_ORDER = {
            SRERole.SpecialMapRoleMap.QIYUCUN,
            SRERole.SpecialMapRoleMap.UNDERWATER,
            SRERole.SpecialMapRoleMap.BIGMAP,
            SRERole.SpecialMapRoleMap.FLY,
            SRERole.SpecialMapRoleMap.TRAP,
            SRERole.SpecialMapRoleMap.SNOW,
            SRERole.SpecialMapRoleMap.DESERT,
            SRERole.SpecialMapRoleMap.WITCH_PRISON,
            SRERole.SpecialMapRoleMap.PRISON
    };

    /**
     * 生成特殊地图职业条目。
     *
     * @param info 地图展示数据（null 时返回空列表）
     * @return 每行一条 {@link Component}，无匹配时返回空列表
     */
    public static List<Component> build(MapDisplayInfo info) {
        List<Component> lines = new ArrayList<>();
        if (info == null || info.features() == null) {
            return lines;
        }
        for (SRERole.SpecialMapRoleMap category : DISPLAY_ORDER) {
            if (!info.features().contains(category.name())) {
                continue;
            }
            String names = gatherRoleNames(category);
            if (names.isEmpty()) {
                continue;
            }
            lines.add(Component.translatable("map_intro.special.prefix").append(Component.literal(names)));
        }
        return lines;
    }

    /** 收集该类别下所有职业的翻译名，以“/”连接。 */
    private static String gatherRoleNames(SRERole.SpecialMapRoleMap category) {
        List<String> names = new ArrayList<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role.getSpecialMapRole() == category) {
                names.add(role.getName().getString());
            }
        }
        return String.join("/", names);
    }
}
