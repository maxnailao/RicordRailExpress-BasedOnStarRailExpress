package pro.fazeclan.river.stupid_express.constants;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.Util;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;

import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.role.amnesiac.RoleSelectionHandler;
import pro.fazeclan.river.stupid_express.role.arsonist.OilDousingHandler;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler;
import pro.fazeclan.river.stupid_express.role.initiate.InitiateRole;
import pro.fazeclan.river.stupid_express.role.necromancer.RevivalSelectionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SERoles {
    private static final HashMap<String, SRERole> ROLES = new HashMap<>();

    public static SRERole AMNESIAC = registerRole(new NormalRole(
            StupidExpress.id("amnesiac"),
            0x9baae8,
            false,
            false,
            SRERole.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            false)).setPassiveIncome(false).setDefaultEnableChance(5000).setDefaultEnableNeededPlayerCount(12);

    public static SRERole ARSONIST = registerRole(new NormalRole(
            StupidExpress.id("arsonist"),
            0xfc9526,
            false,
            false,
            SRERole.MoodType.FAKE,
            -1,
            true)).setCanUseInstinct(true).setDefaultEnableNeededPlayerCount(12);

    public static SRERole AVARICIOUS = registerRole(new NormalRole(
            StupidExpress.id("avaricious"),
            0x8f00ff,
            false,
            true,
            SRERole.MoodType.FAKE,
            -1,
            true)).setServerGameTickEvent((player, gameWorldComponent) -> {
                AvariciousGoldHandler.playerServerTick(player, gameWorldComponent);
            }).setPassiveIncome(false).setDefaultEnableNeededPlayerCount(12);

    public static SRERole NECROMANCER = registerRole(new NormalRole(
            StupidExpress.id("necromancer"),
            0x9457ff,
            false,
            true,
            SRERole.MoodType.FAKE,
            -1,
            true)).setDefaultEnableChance(5000).setDefaultEnableNeededPlayerCount(12);

    public static SRERole INITIATE = registerRole(new InitiateRole(
            StupidExpress.id("initiate"),
            0xffd154,
            false,
            false,
            SRERole.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            true))
            .setCanBeRandomedByOtherRoles(false)
            // 池内上限保持 1：抽到一次就会通过自我绑定展开成一对。
            // 不要改成 2，否则会抽出两对再被去重逻辑收敛，反而浪费中立槽位。
            .setDefaultMax(1)
            .setDefaultEnableChance(8000)
            .setDefaultEnableNeededPlayerCount(12)
            // 初学者一次生成一对（自己绑定自己）：抽取时只占 1 个池内名额，
            // 但最终名单要占 2 个中立位，否则会挤掉另一个中立职业。
            .setOccupiedRoleCount(2);

    public static List<ShopEntry> INITIATE_SHOP = Util.make(new ArrayList<>(), entries -> {
        entries.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 200, ShopEntry.Type.WEAPON));
    });

    public static List<ShopEntry> NECROMANCER_SHOP = Util.make(new ArrayList<>(), entries -> {
        entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
    });

    public static void init() {
        RoleAssignmentManager.addOccupationRole(SERoles.INITIATE, SERoles.INITIATE);

        /// AMNESIAC
        Harpymodloader.setRoleMaximum(AMNESIAC.getIdentifier(), 1);
        RoleSelectionHandler.init();

        /// ARSONIST
        Harpymodloader.setRoleMaximum(ARSONIST.getIdentifier(), 1);
        OilDousingHandler.init();

        ResetPlayerEvent.EVENT.register(player -> {
            var dousedComponent = DousedPlayerComponent.KEY.get(player);
            dousedComponent.reset();
            dousedComponent.sync();
            player.removeTag("nearDoor");
        });
        /// NECROMANCER

        RevivalSelectionHandler.init();

        /// AVARICIOUS

        AvariciousGoldHandler.registerEvents();

    }

    public static SRERole registerRole(SRERole role) {
        TMMRoles.registerRole(role);
        ROLES.put(role.identifier().getPath(), role);
        return role;
    }

}
