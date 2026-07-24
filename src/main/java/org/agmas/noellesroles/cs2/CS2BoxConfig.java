package org.agmas.noellesroles.cs2;

import java.util.ArrayList;
import java.util.List;

/**
 * CS2 风格箱子配置数据类
 * <p>
 * 对应一个 .json 箱子配置文件，定义箱子名称、所需钥匙、各品质概率和皮肤池。
 * </p>
 */
public class CS2BoxConfig {

    /** 箱子唯一 ID（即文件名，不含 .json） */
    private final String boxId;

    /** 箱子显示名称 */
    private final String boxName;

    /** 开启所需钥匙 ID */
    private final String keyName;

    /** 各品质概率（0.0 ~ 1.0），总和应为 1.0 */
    private final double common;
    private final double uncommon;
    private final double rare;
    private final double epic;
    private final double legendary;
    private final double unbelievable;

    /** 各品质皮肤列表（格式："itemType/skinName"） */
    private final List<String> commonSkins;
    private final List<String> uncommonSkins;
    private final List<String> rareSkins;
    private final List<String> epicSkins;
    private final List<String> legendarySkins;
    private final List<String> unbelievableSkins;

    public CS2BoxConfig(String boxId, String boxName, String keyName,
                        double common, double uncommon, double rare,
                        double epic, double legendary, double unbelievable,
                        List<String> commonSkins, List<String> uncommonSkins,
                        List<String> rareSkins, List<String> epicSkins,
                        List<String> legendarySkins, List<String> unbelievableSkins) {
        this.boxId = boxId;
        this.boxName = boxName;
        this.keyName = keyName;
        this.common = common;
        this.uncommon = uncommon;
        this.rare = rare;
        this.epic = epic;
        this.legendary = legendary;
        this.unbelievable = unbelievable;
        this.commonSkins = commonSkins != null ? commonSkins : new ArrayList<>();
        this.uncommonSkins = uncommonSkins != null ? uncommonSkins : new ArrayList<>();
        this.rareSkins = rareSkins != null ? rareSkins : new ArrayList<>();
        this.epicSkins = epicSkins != null ? epicSkins : new ArrayList<>();
        this.legendarySkins = legendarySkins != null ? legendarySkins : new ArrayList<>();
        this.unbelievableSkins = unbelievableSkins != null ? unbelievableSkins : new ArrayList<>();
    }

    public String getBoxId() { return boxId; }
    public String getBoxName() { return boxName; }
    public String getKeyName() { return keyName; }

    public double getCommon() { return common; }
    public double getUncommon() { return uncommon; }
    public double getRare() { return rare; }
    public double getEpic() { return epic; }
    public double getLegendary() { return legendary; }
    public double getUnbelievable() { return unbelievable; }

    public List<String> getCommonSkins() { return commonSkins; }
    public List<String> getUncommonSkins() { return uncommonSkins; }
    public List<String> getRareSkins() { return rareSkins; }
    public List<String> getEpicSkins() { return epicSkins; }
    public List<String> getLegendarySkins() { return legendarySkins; }
    public List<String> getUnbelievableSkins() { return unbelievableSkins; }

    /**
     * 获取品质等级对应的概率列表（按品质从低到高排列）
     * <p>
     * 返回格式：List of (probability, skinList)，跳过空皮肤池
     * </p>
     */
    public List<org.agmas.noellesroles.utils.Pair<Double, List<String>>> getQualityListGroup() {
        List<org.agmas.noellesroles.utils.Pair<Double, List<String>>> groups = new ArrayList<>();
        addIfNonEmpty(groups, common, commonSkins);
        addIfNonEmpty(groups, uncommon, uncommonSkins);
        addIfNonEmpty(groups, rare, rareSkins);
        addIfNonEmpty(groups, epic, epicSkins);
        addIfNonEmpty(groups, legendary, legendarySkins);
        addIfNonEmpty(groups, unbelievable, unbelievableSkins);
        return groups;
    }

    /**
     * 获取品质等级对应的皮肤列表（包含空列表，保持6个等级）
     */
    public List<org.agmas.noellesroles.utils.Pair<Double, List<String>>> getAllQualityGroups() {
        List<org.agmas.noellesroles.utils.Pair<Double, List<String>>> groups = new ArrayList<>();
        groups.add(new org.agmas.noellesroles.utils.Pair<>(common, commonSkins));
        groups.add(new org.agmas.noellesroles.utils.Pair<>(uncommon, uncommonSkins));
        groups.add(new org.agmas.noellesroles.utils.Pair<>(rare, rareSkins));
        groups.add(new org.agmas.noellesroles.utils.Pair<>(epic, epicSkins));
        groups.add(new org.agmas.noellesroles.utils.Pair<>(legendary, legendarySkins));
        groups.add(new org.agmas.noellesroles.utils.Pair<>(unbelievable, unbelievableSkins));
        return groups;
    }

    private void addIfNonEmpty(List<org.agmas.noellesroles.utils.Pair<Double, List<String>>> groups,
                               double prob, List<String> skins) {
        if (skins != null && !skins.isEmpty() && prob > 0) {
            groups.add(new org.agmas.noellesroles.utils.Pair<>(prob, skins));
        }
    }
}
