package io.wifi.ConfigCompact.ui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.noellesroles.utils.RoleUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class RoleManageConfigUI {

    public static HashMap<String, Boolean> RoleEnableStatus = new HashMap<>();
    public static HashMap<String, Boolean> ModifierEnableStatus = new HashMap<>();

    public static class RoleAndModifierSyncInfo {
        public HashMap<String, Boolean> roleInfo;
        public HashMap<String, Boolean> modifierInfo;

        public RoleAndModifierSyncInfo() {
            this(new HashMap<>(), new HashMap<>());
        }

        public RoleAndModifierSyncInfo(HashMap<ResourceLocation, Boolean> roleInfos,
                HashMap<ResourceLocation, Boolean> modifierInfos) {
            this.roleInfo = new HashMap<>();
            this.modifierInfo = new HashMap<>();
            for (var r : roleInfos.entrySet()) {
                this.roleInfo.put(r.getKey().toString(), r.getValue());
            }
            for (var r : modifierInfos.entrySet()) {
                this.modifierInfo.put(r.getKey().toString(), r.getValue());
            }
        }
    }

    public static void setRoleInfo(HashMap<String, Boolean> packetInfo) {
        RoleEnableStatus.clear();
        RoleEnableStatus.putAll(packetInfo);
    }

    public static void setModifierInfo(HashMap<String, Boolean> packetInfo) {
        ModifierEnableStatus.clear();
        ModifierEnableStatus.putAll(packetInfo);
    }

    public static Screen getScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.starrailexpress.role_config"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory roleCategory = builder
                .getOrCreateCategory(Component.translatable("category.starrailexpress.config.role"));
        ConfigCategory modifierCategory = builder
                .getOrCreateCategory(Component.translatable("category.starrailexpress.config.modifier"));
        if (Minecraft.getInstance().player == null) {
            RoleEnableStatus.clear();
            ModifierEnableStatus.clear();
        }
        if (RoleEnableStatus.isEmpty()) {
            RoleEnableStatus.clear();
            for (var info : TMMRoles.ROLES.keySet()) {
                if (HarpyModLoaderConfig.HANDLER.instance().getDisabled().contains(info.toString())) {
                    RoleEnableStatus.put(info.toString(), false);
                } else {
                    RoleEnableStatus.put(info.toString(), true);
                }
            }
        }
        if (ModifierEnableStatus.isEmpty()) {
            ModifierEnableStatus.clear();
            for (var info : HMLModifiers.MODIFIERS) {
                if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(info.identifier().toString())) {
                    ModifierEnableStatus.put(info.identifier().toString(), false);
                } else {
                    ModifierEnableStatus.put(info.identifier().toString(), true);
                }
            }
        }
        ArrayList<Entry<String, Boolean>> entrySets = new ArrayList<>(RoleEnableStatus.entrySet());
        sortRoles(entrySets);
        for (var info : entrySets) {
            var roleId = info.getKey();
            roleCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.role_enable_option",
                                            RoleUtils.getTeamName(ResourceLocation.tryParse(roleId)),
                                            RoleUtils.getRoleName(ResourceLocation.tryParse(roleId)), roleId),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString(), RoleUtils.getRoleDescription(roleId)))
                            .setSaveConsumer(newValue -> RoleEnableStatus.put(roleId, newValue))
                            .build());
        }
        for (var info : ModifierEnableStatus.entrySet()) {
            var roleId = info.getKey();
            modifierCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.modifier_enable_option",
                                            RoleUtils.getModifierName(ResourceLocation.tryParse(roleId)), roleId),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString(), RoleUtils.getModifierDescription(roleId)))
                            .setSaveConsumer(newValue -> ModifierEnableStatus.put(roleId, newValue))
                            .build());
        }

        builder.setSavingRunnable(() -> {
            HarpyModLoaderConfig.HANDLER.instance().disabled.clear();
            for (Entry<String, Boolean> entry : RoleEnableStatus.entrySet()) {
                if (!entry.getValue()) {
                    HarpyModLoaderConfig.HANDLER.instance().disabled.add(entry.getKey());
                }
            }
            HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.clear();
            for (Entry<String, Boolean> entry : ModifierEnableStatus.entrySet()) {
                if (!entry.getValue()) {
                    HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.add(entry.getKey());
                }
            }
            HarpyModLoaderConfig.HANDLER.save();

            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null
                    && !Minecraft.getInstance().isLocalServer()) {
                if (Minecraft.getInstance().player.hasPermissions(2)) {
                    String roleCommandPrefix = "setEnabledRole";
                    String modifierCommandPrefix = "setEnabledModifier";
                    {
                        Minecraft.getInstance().player.connection.sendCommand(roleCommandPrefix + " enableAll");
                        Minecraft.getInstance().player.connection.sendCommand(modifierCommandPrefix + " enableAll");
                    }
                    for (var role : HarpyModLoaderConfig.HANDLER.instance().disabledModifiers) {
                        Minecraft.getInstance().player.connection
                                .sendCommand(modifierCommandPrefix + " " + role + " false");
                    }
                    for (var role : HarpyModLoaderConfig.HANDLER.instance().getDisabled()) {
                        Minecraft.getInstance().player.connection
                                .sendCommand(roleCommandPrefix + " " + role + " false");
                    }
                }
            }
        });
        return builder.build();
    }

    private static void sortRoles(ArrayList<Entry<String, Boolean>> clone) {
        Collator collator = Collator.getInstance();
        boolean killerFirst = false;
        clone.sort((ea, eb) -> {
            SRERole a = TMMRoles.ROLES.get(ResourceLocation.tryParse(ea.getKey()));
            SRERole b = TMMRoles.ROLES.get(ResourceLocation.tryParse(eb.getKey()));
            // 未注册职业统一排到末尾，且两两间按 id 比较；
            // 任何情况下都不能返回恒等的 0，否则违反比较器传递性契约导致 TimSort 崩溃
            if (a == null || b == null) {
                if (a == b) {
                    return ea.getKey().compareTo(eb.getKey());
                }
                return a == null ? 1 : -1;
            }
            int rt_a = RoleUtils.getRoleType(a);
            int rt_b = RoleUtils.getRoleType(b);
            int typeCmp = killerFirst ? Integer.compare(rt_b, rt_a) : Integer.compare(rt_a, rt_b);
            if (typeCmp != 0)
                return typeCmp;
            int nsCmp = collator.compare(a.identifier().getNamespace(), b.identifier().getNamespace());
            if (nsCmp != 0)
                return nsCmp;
            int nameCmp = collator.compare(RoleUtils.getRoleName(a).getString(),
                    RoleUtils.getRoleName(b).getString());
            if (nameCmp != 0)
                return nameCmp;
            // 同类型同名职业：用 id 作最终次序，保证全序稳定
            return ea.getKey().compareTo(eb.getKey());
        });
    }

    public static void startConfigUI() {
        Screen screen = getScreen(Minecraft.getInstance().screen);
        Minecraft.getInstance().setScreen(screen);
    }
}
