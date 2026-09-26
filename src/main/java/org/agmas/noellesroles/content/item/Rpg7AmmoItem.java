package org.agmas.noellesroles.content.item;

import net.minecraft.world.item.Item;

/**
 * RPG-7 火箭弹
 * - 用于装填 {@link Rpg7Item}（左键装填时自动消耗背包中的本物品）
 * - 同时作为火箭弹抛射物的渲染模型（见 {@code Rpg7RocketRenderer}）
 */
public class Rpg7AmmoItem extends Item {
    public Rpg7AmmoItem(Properties settings) {
        super(settings);
    }
}
