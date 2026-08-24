package org.agmas.noellesroles.game.roles.innocence.duomaomao_meimeihide;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 躲藏专家的角色组件
 * - 技能「变身躲藏」：花费 200 金币，变身为准星对准的方块
 * - 变身期间玩家隐身（本体不可见），客户端将该方块的模型渲染在玩家位置
 * - 持续 40 秒，冷却 175 秒，可再次按技能键主动退出
 * - 变身期间施加隐身与禁用道具效果，无法使用任何道具
 */
public class DuomaomaoMeimeiHidePlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<DuomaomaoMeimeiHidePlayerComponent> KEY = ModComponents.DUOMAOMAO_MEIMEIHIDE;
    public static final ResourceLocation SKILL_ID = Noellesroles.id("duomaomao_meimeihide_transform");
    /** 蹲下（Shift+G）变体技能 ID：统一技能系统按蹲下状态过滤定义，双定义保证蹲下时也能释放/退出 */
    public static final ResourceLocation SKILL_ID_SHIFTED = Noellesroles.id("duomaomao_meimeihide_transform_shifted");

    private final Player player;
    /** 变身剩余时间（tick），0 表示未变身 */
    public int activeTicks;
    /** 变身方块的注册名（ResourceLocation 字符串），空串表示未变身 */
    public String hiddenBlock = "";
    /** 刚变身后的保护期（tick）：期间忽略一切退出请求，防止重复按键/多触发路径瞬间把刚开启的变身又关掉 */
    public int startProtectTicks;

    /** 变身开启后的保护期时长（0.5 秒） */
    private static final int START_PROTECT_TICKS = 10;

    public DuomaomaoMeimeiHidePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 变身方块模型需要对所有玩家可见
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        // 角色分配事件在开局时会多次触发 init，不能无条件清零；
        // 开局/结束的彻底清理由 clear() 保证（框架在 onStartGame/onEndGame 先调 clear）。
        if (activeTicks <= 0) {
            sync();
        }
    }

    @Override
    public void clear() {
        stopHide(false);
    }

    public boolean isHiding() {
        return activeTicks > 0 && !hiddenBlock.isEmpty();
    }

    /** 客户端渲染用：当前变身的方块，未变身或方块无效时返回 null */
    public Block getHiddenBlock() {
        if (hiddenBlock.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(hiddenBlock);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    /** 技能入口：开始变身 / 再次按下时主动退出。返回 true 才会消耗冷却 */
    public boolean useSkill(ServerPlayer sp, boolean skillReady) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.DUOMAOMAO_MEIMEIHIDE)) {
            return false;
        }
        // 变身中再次按下：主动退出（不重置冷却）；保护期内忽略，避免瞬间误退出
        if (isHiding()) {
            tryExit();
            return false;
        }
        if (!skillReady) {
            // 冷却中：给出剩余冷却提示，避免静默失败
            int cd = SREAbilityPlayerComponent.KEY.get(sp).getSkillState(SKILL_ID).cooldown;
            if (cd > 0) {
                sp.displayClientMessage(
                        Component.translatable("message.sre.skill.cooldown",
                                String.format("%.1f", cd / 20.0F))
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        int cost = config.duomaomaoMeimeiHideCost;
        // 金币校验：与其他付费技能（史莱姆/回溯者等）同源，使用商店账户余额
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.duomaomao_meimeihide.no_coin", cost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 准星取块：服务端沿视线方向射线检测
        HitResult hit = sp.pick(config.duomaomaoMeimeiHideReach, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.duomaomao_meimeihide.no_block")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        BlockState state = sp.level().getBlockState(blockHit.getBlockPos());
        if (state.isAir()) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.duomaomao_meimeihide.no_block")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 扣除金币并进入变身状态
        shop.balance -= cost;
        shop.sync();
        hiddenBlock = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        activeTicks = config.duomaomaoMeimeiHideDurationSeconds * 20;
        startProtectTicks = START_PROTECT_TICKS;
        applyHideEffects();
        sync();
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                SoundSource.PLAYERS, 1.0F, 1.2F);
        // 变身粒子：使用目标方块的方块粒子，给本地玩家明确的视觉反馈
        sp.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                sp.getX(), sp.getY() + 0.5, sp.getZ(), 40, 0.5, 0.9, 0.5, 0.05);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.duomaomao_meimeihide.start",
                        state.getBlock().getName(), activeTicks / 20)
                        .withStyle(ChatFormatting.GREEN),
                true);
        Noellesroles.LOGGER.info("[duomaomao_meimeihide] {} 变身为 {}，持续 {} tick",
                sp.getGameProfile().getName(), hiddenBlock, activeTicks);
        return true;
    }

    /**
     * 主动退出变身；保护期内的退出请求一律忽略（防止重复触发瞬间取消刚开启的变身）。
     *
     * @return 是否真正退出了变身
     */
    public boolean tryExit() {
        if (!isHiding()) {
            return false;
        }
        if (startProtectTicks > 0) {
            return false;
        }
        stopHide(true);
        return true;
    }

    /**
     * 结束变身
     *
     * @param notify 是否提示玩家（主动退出/时间到时提示，清场时不提示）
     */
    public void stopHide(boolean notify) {
        if (!isHiding()) {
            activeTicks = 0;
            hiddenBlock = "";
            startProtectTicks = 0;
            return;
        }
        activeTicks = 0;
        hiddenBlock = "";
        startProtectTicks = 0;
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.INVISIBILITY);
            sp.removeEffect(ModEffects.USED_BANED);
            sp.removeEffect(ModEffects.JINGBU);
            Noellesroles.LOGGER.info("[duomaomao_meimeihide] {} 结束变身 (notify={})",
                    sp.getGameProfile().getName(), notify);
            if (notify) {
                sp.serverLevel().sendParticles(ParticleTypes.POOF,
                        sp.getX(), sp.getY() + 0.5, sp.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.duomaomao_meimeihide.end")
                                .withStyle(ChatFormatting.GRAY),
                        true);
            }
        }
        sync();
    }

    /** 施加变身期间的隐身、禁用道具与静步（同特工静步，屏蔽脚步声）效果 */
    private void applyHideEffects() {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.JINGBU, 60, 0, true, false, false));
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (activeTicks <= 0) {
            return;
        }
        // 仅在玩家死亡/旁观时终止；不绑定回合状态与角色判定，
        // 避免开局分波分配/状态切换瞬间误杀刚开启的变身（回合结束由框架 clear 清理）
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            stopHide(false);
            return;
        }
        // 维持效果（短时长 + 每 tick 兜底，退出时立即移除）
        applyHideEffects();
        if (startProtectTicks > 0) {
            startProtectTicks--;
        }
        activeTicks--;
        if (activeTicks % 20 == 0) {
            sync();
        }
        if (activeTicks <= 0) {
            stopHide(true);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("activeTicks", activeTicks);
        tag.putString("hiddenBlock", hiddenBlock);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        activeTicks = tag.getInt("activeTicks");
        hiddenBlock = tag.getString("hiddenBlock");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
