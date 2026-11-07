package org.agmas.noellesroles.game.roles.neutral.thousand_faces;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.util.PlayerHatSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.SkincrawlerSkinS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 千面人 —— 特殊中立职业。
 *
 * <p>
 * 核心机制「千面伪装」：
 * </p>
 * <ul>
 * <li>开局按「杀手数量 + 1」获得可用次数；使用技能与死亡都会各消耗 1 次。</li>
 * <li>对准尸体使用技能，可以把真实职业临时换成尸体职业，并获得其皮肤；
 * 伪装期间可以正常使用该职业的商店、技能与本能颜色。</li>
 * <li>死亡时清空物品、重新发放房间钥匙、可用次数 -1、技能强制 60 秒冷却，
 * 并在随机出生点复活；转回千面人本体。</li>
 * <li>只有「死亡导致次数降到 0」才会真正死亡；用技能把次数降到 0 不会立即死亡。</li>
 * </ul>
 *
 * <p>
 * 因为伪装会真实地切换职业，{@code RoleData} 会随职业变化被重建，
 * 所以跨职业保留的状态（可用次数、是否伪装、待复活等）放在本 CCA 里。
 * 千面人本体再次被分配时 {@link #init()} 不会重置已有次数（见 {@link #initialized}）。
 * </p>
 */
public class ThousandFacesPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ThousandFacesPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("thousand_faces"), ThousandFacesPlayerComponent.class);

    /** 伪装技能 ID（供死亡后强制冷却使用）。 */
    public static final ResourceLocation SKILL_ID = Noellesroles.id("thousand_faces_disguise");

    /** 死亡后技能强制冷却（tick）。 */
    public static final int RESPAWN_COOLDOWN_TICKS = 60 * 20;

    /** 伪装可选取尸体距离（格）。 */
    public static final double DISGUISE_RANGE = 4.0D;

    static {
        // 纳入开局/局末的统一 clear()：本组件用 initialized 标记区分「开局」与「中途变回千面人」，
        // 若开局不 clear，上一局的次数会被保留。注意职业链式 setComponentKey 发生在
        // TMMRoles.registerRole 之后，不会被自动收录，需手动登记。
        if (!TMMRoles.COMPONENT_KEYS.contains(KEY)) {
            TMMRoles.addRoleComponents(KEY);
        }
        // 死亡已定局后触发（有/无击杀者两个版本都监听，配合 pendingRevive 防重复）。
        OnPlayerDeath.EVENT.register((player, deathReason) -> onAnyDeath(player));
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> onAnyDeath(player));
    }

    private final Player player;

    // ── 同步状态（客户端用于 HUD / 本能颜色） ──────────────────────
    /** 本局是否是千面人（伪装成其他职业后仍然为 true）。 */
    public boolean active = false;
    /** 当前是否已伪装成其他职业。 */
    public boolean disguised = false;
    /** 剩余可用次数。 */
    public int uses = 0;
    /** 是否已彻底死亡（可用次数归零）。 */
    public boolean permanentlyDead = false;

    // ── 仅服务端 ─────────────────────────────────────────────────
    /** 是否已完成本局初始化；中途改回千面人时用于避免重置次数。 */
    private boolean initialized = false;
    /** 已排队的复活。 */
    private boolean pendingRevive = false;
    private int reviveDelay = 0;
    /** 伪装目标的已装备物品皮肤：物品皮肤类型 -> 皮肤名（仅服务端）。 */
    private final Map<String, String> disguiseSkins = new HashMap<>();
    /** 是否成功取得伪装目标的皮肤数据（目标离线时保持千面人自己的皮肤）。 */
    private boolean disguiseSkinsKnown = false;
    /** 千面人自己原来的帽子皮肤；伪装期间临时改成目标玩家的帽子。 */
    private String previousOwnHat = null;
    private boolean hatCaptured = false;
    /** 全背包皮肤重刷计时（每 10 tick 一次，手持物品每 tick 一次）。 */
    private int skinRefreshTicker = 0;

    public ThousandFacesPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 本局是否为千面人（包括已伪装状态）。 */
    public boolean isActive() {
        return active;
    }

    /** 当前是否已伪装成其他职业。 */
    public boolean isDisguised() {
        return disguised;
    }

    public int getUses() {
        return uses;
    }

    public boolean isPermanentlyDead() {
        return permanentlyDead;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        // 本能颜色需要其他客户端知道目标是否为伪装中的千面人，所以广播给所有人。
        return true;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ── 生命周期 ─────────────────────────────────────────────────

    @Override
    public void init() {
        if (initialized) {
            // 中途从伪装职业变回千面人时，保留次数/状态。
            sync();
            return;
        }
        initialized = true;
        active = true;
        disguised = false;
        permanentlyDead = false;
        pendingRevive = false;
        reviveDelay = 0;
        disguiseSkins.clear();
        disguiseSkinsKnown = false;
        previousOwnHat = null;
        hatCaptured = false;
        skinRefreshTicker = 0;

        int killerCount = 0;
        if (player.level() instanceof ServerLevel serverLevel) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            killerCount = gameWorld.getAllKillerPlayers().size();
        }
        uses = killerCount + 1;

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(
                    "message.noellesroles.thousand_faces.initialized",
                    String.valueOf(killerCount), String.valueOf(uses))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            restoreOwnHat(sp);
        }
        initialized = false;
        active = false;
        disguised = false;
        uses = 0;
        permanentlyDead = false;
        pendingRevive = false;
        reviveDelay = 0;
        disguiseSkins.clear();
        disguiseSkinsKnown = false;
        previousOwnHat = null;
        hatCaptured = false;
        skinRefreshTicker = 0;
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (!gameWorld.isRunning()) {
            return;
        }
        // 兼容未经过标准 ModdedRoleAssigned 流程的分配路径。
        if (!initialized && gameWorld.isRole(sp, ModRoles.THOUSAND_FACES)) {
            init();
        }
        if (!active) {
            return;
        }
        if (pendingRevive) {
            reviveDelay--;
            if (reviveDelay <= 0) {
                doRespawn(sp);
            }
        }
        // 伪装期间持续把目标玩家的武器皮肤刷到手上的 2D/3D 武器上。
        if (disguised && !pendingRevive) {
            tickDisguiseSkins(sp);
        }
    }

    // ── 技能：千面伪装 ───────────────────────────────────────────

    /**
     * 对准尸体使用伪装技能。
     *
     * @return true 表示技能成功释放（统一技能系统会因此进入冷却）
     */
    public static boolean useDisguise(ServerPlayer sp) {
        if (sp.isSpectator()) {
            return false;
        }
        ThousandFacesPlayerComponent comp = KEY.maybeGet(sp).orElse(null);
        if (comp == null || !comp.active) {
            return false;
        }
        if (comp.permanentlyDead) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.perished")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (comp.disguised) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.already_disguised")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (comp.uses <= 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.no_uses")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        PlayerBodyEntity body = findCorpse(sp);
        if (body == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.no_corpse")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        UUID corpseUuid = body.getPlayerUuid();
        if (corpseUuid != null && corpseUuid.equals(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.invalid_corpse")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        PlayerBodyEntityComponent bodyComponent = PlayerBodyEntityComponent.KEY.get(body);
        if (bodyComponent.isFakeBody) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.invalid_corpse")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        SRERole role = RoleUtils.getRole(bodyComponent.playerRole);
        if (!isDisguisable(role)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.invalid_corpse")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 消耗 1 次（技能导致的减少不会致死）。
        comp.uses--;
        comp.disguised = true;
        // 记录尸体玩家当前装备的物品皮肤（刀/枪/球棒/手雷/弓等），
        // 伪装期间让千面人手上的武器也显示成对方的 2D/3D 皮肤。
        comp.captureDisguiseSkins(sp, corpseUuid);
        comp.sync();

        // 完整走一遍「获得职业」流程以初始化该职业的组件/技能，
        // 但不发放该职业的开局物品：先快照背包，改职后再恢复。
        List<ItemStack> snapshot = snapshotInventory(sp);
        int selectedSlot = sp.getInventory().selected;

        RoleUtils.changeRole(sp, role, true, false, false, false);
        restoreInventory(sp, snapshot, selectedSlot);
        // 改职流程可能重写过手持物品，这里统一按伪装目标的皮肤重刷一次。
        comp.applyDisguiseSkins(sp, true);

        // 同步尸体主人的皮肤给所有人。
        if (corpseUuid != null) {
            broadcastSkin(sp, corpseUuid);
        }

        sp.level().playSound(null, sp.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.disguised",
                RoleUtils.getRoleName(role), String.valueOf(comp.uses))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return true;
    }

    /** 准星前方 4 格内的尸体。 */
    private static PlayerBodyEntity findCorpse(ServerPlayer sp) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(sp,
                entity -> entity instanceof PlayerBodyEntity, DISGUISE_RANGE);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof PlayerBodyEntity body) {
            return body;
        }
        return null;
    }

    /** 是否允许伪装成该职业（沿用失忆者的排除名单，避免破坏特殊职业流程）。 */
    private static boolean isDisguisable(SRERole role) {
        if (role == null || !role.canBeRandomed()) {
            return false;
        }
        ResourceLocation id = role.identifier();
        if (id.equals(ModRoles.THOUSAND_FACES_ID)) {
            return false;
        }
        if (id.equals(ModRoles.MA_CHEN_XU_ID)) {
            return false;
        }
        if (id.equals(TMMRoles.LOOSE_END.identifier())) {
            return false;
        }
        if (id.equals(SERoles.INITIATE.identifier())) {
            return false;
        }
        if (id.equals(TraitorAndModifiers.TRAITOR.identifier())) {
            return false;
        }
        if (id.equals(SERoles.AMNESIAC.identifier())) {
            return false;
        }
        return true;
    }

    // ── 死亡 / 复活 ──────────────────────────────────────────────

    private static void onAnyDeath(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!(sp.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (!gameWorld.isRunning()) {
            return;
        }
        ThousandFacesPlayerComponent comp = KEY.maybeGet(sp).orElse(null);
        if (comp == null || !comp.active || comp.permanentlyDead || comp.pendingRevive) {
            return;
        }
        comp.handleDeath(sp);
    }

    private void handleDeath(ServerPlayer sp) {
        if (uses <= 1) {
            // 这次死亡导致的 -1 会让次数归零：真正死亡，不复活。
            uses = 0;
            permanentlyDead = true;
            disguised = false;
            pendingRevive = false;
            disguiseSkins.clear();
            disguiseSkinsKnown = false;
            restoreOwnHat(sp);
            // 真正死亡时变回千面人职业，并还原自己原本的玩家皮肤。
            broadcastSkin(sp, sp.getUUID());
            if (!SREGameWorldComponent.KEY.get(sp.level()).isRole(sp, ModRoles.THOUSAND_FACES)) {
                RoleUtils.changeRole(sp, ModRoles.THOUSAND_FACES, false, false, false);
            }
            sync();
            sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.perished")
                    .withStyle(ChatFormatting.DARK_RED), false);
            return;
        }

        // 死亡消耗 1 次。
        uses--;
        pendingRevive = true;
        reviveDelay = 2;
        sync();
        sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.will_revive",
                String.valueOf(uses)).withStyle(ChatFormatting.GOLD), false);
    }

    private void doRespawn(ServerPlayer sp) {
        pendingRevive = false;
        if (permanentlyDead) {
            return;
        }
        if (!sp.isSpectator()) {
            // 已被其他机制复活，不要重复处理。
            return;
        }

        // 转回千面人本体，并还原皮肤。
        disguised = false;
        broadcastSkin(sp, sp.getUUID());
        if (!SREGameWorldComponent.KEY.get(sp.level()).isRole(sp, ModRoles.THOUSAND_FACES)) {
            // 走完整改职流程，让被伪装职业的组件/数据正确收尾；
            // 千面人本体没有开局物品，随后还会清空背包，所以不会残留物品。
            RoleUtils.changeRole(sp, ModRoles.THOUSAND_FACES, false, false, false);
        }

        // 清空物品并重新发放房间钥匙。
        sp.getInventory().clearContent();
        disguiseSkins.clear();
        disguiseSkinsKnown = false;
        restoreOwnHat(sp);
        skinRefreshTicker = 0;
        giveRoomKey(sp);

        // 在随机出生点复活。
        GameUtils.revivePlayer(sp, sp.getX(), sp.getY(), sp.getZ());
        GameUtils.teleportToRandomRoom(sp);

        // 技能强制进入 60 秒冷却。
        SREAbilityPlayerComponent.KEY.get(sp).setSkillCooldown(SKILL_ID, RESPAWN_COOLDOWN_TICKS);

        sync();
        sp.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 1.2F);
        sp.displayClientMessage(Component.translatable("message.noellesroles.thousand_faces.reborn",
                String.valueOf(uses)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** 重新发放带房间号的房间钥匙。 */
    private static void giveRoomKey(ServerPlayer sp) {
        int roomNumber = GameUtils.roomToPlayer.getOrDefault(sp.getUUID(), 0);
        ItemStack key = new ItemStack(TMMItems.KEY);
        String roomName = "Room " + roomNumber;
        key.update(DataComponents.LORE, ItemLore.EMPTY,
                lore -> new ItemLore(Component.literal(roomName)
                        .toFlatList(Style.EMPTY.withItalic(false).withColor(0xFF8C00))));
        RoleUtils.insertStackInFreeSlot(sp, key);
    }

    /** 广播皮肤覆盖：{@code skinSource == subject} 时等价于恢复本体皮肤。 */
    private static void broadcastSkin(ServerPlayer subject, UUID skinSource) {
        if (!(subject.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SkincrawlerSkinS2CPacket packet = new SkincrawlerSkinS2CPacket(subject.getUUID(), skinSource);
        for (ServerPlayer viewer : serverLevel.players()) {
            ServerPlayNetworking.send(viewer, packet);
        }
    }

    // ── 伪装目标的武器皮肤（2D/3D 物品皮肤） ─────────────────────

    /**
     * 记录尸体玩家当前装备的物品皮肤。
     * <p>
     * 刀/枪/球棒/手雷/弓等武器皮肤分 2D 贴图与 3D 模型两种，渲染时读取物品堆栈上的
     * {@code SKIN} 组件。伪装时把目标玩家已装备的皮肤刷到千面人的武器上，
     * 这样别人看到的武器皮肤也与目标一致。
     * </p>
     */
    private void captureDisguiseSkins(ServerPlayer sp, UUID corpseUuid) {
        disguiseSkins.clear();
        disguiseSkinsKnown = false;
        if (corpseUuid == null || !(sp.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player owner = serverLevel.getPlayerByUUID(corpseUuid);
        if (owner == null) {
            // 尸体主人已离线：无法取得其外观数据，保持千面人自己的皮肤。
            Noellesroles.LOGGER.info("[ThousandFaces] corpse owner {} offline; keep own skins",
                    corpseUuid);
            return;
        }
        disguiseSkinsKnown = true;
        disguiseSkins.putAll(PlayerEconomyManager.getEquippedSkins(owner));
        if (disguiseSkins.isEmpty() && owner instanceof ServerPlayer serverOwner) {
            // 兜底：经济数据尚未从 CCA 恢复时直接读组件。
            disguiseSkins.putAll(SREPlayerSkinsComponent.KEY.get(serverOwner).getEquippedSkins());
        }

        // 3D 帽子也跟随目标玩家；伪装期间临时改写千面人自己的帽子数据，
        // 死亡/复活时恢复。否则本地渲染的回退分支会继续显示千面人自己的帽子。
        previousOwnHat = SREPlayerSkinsComponent.KEY.get(sp)
                .getEquippedSkin(ItemSkinManager.SkinTypes.HAT);
        hatCaptured = true;
        String corpseHat = SREPlayerSkinsComponent.KEY.get(owner)
                .getEquippedSkin(ItemSkinManager.SkinTypes.HAT);
        applyHat(sp, corpseHat);

        Noellesroles.LOGGER.info("[ThousandFaces] {} captured {} weapon skin(s) and hat '{}' from corpse {}",
                sp.getName().getString(), disguiseSkins.size(), corpseHat, corpseUuid);
    }

    /** 设置玩家头顶帽子（同时写 CCA 与实体数据，保证本地/他人渲染一致）。 */
    private static void applyHat(ServerPlayer sp, String hat) {
        String value = hat == null || hat.isBlank() ? PlayerHatSync.DEFAULT : hat;
        SREPlayerSkinsComponent.KEY.get(sp)
                .setEquippedSkinForItemType(ItemSkinManager.SkinTypes.HAT, value);
        PlayerHatSync.setHat(sp, value);
    }

    /** 恢复千面人自己原来的帽子。 */
    private void restoreOwnHat(ServerPlayer sp) {
        if (!hatCaptured) {
            return;
        }
        applyHat(sp, previousOwnHat);
        hatCaptured = false;
        previousOwnHat = null;
    }

    /** 把伪装目标的物品皮肤刷到千面人当前手持的武器上。 */
    private void tickDisguiseSkins(ServerPlayer sp) {
        skinRefreshTicker++;
        // 手持物品每 tick 检查（换手/捡起后立刻纠正）；其余背包每 10 tick 刷一次。
        applyDisguiseSkins(sp, skinRefreshTicker % 10 == 0);
    }

    private void applyDisguiseSkins(ServerPlayer sp, boolean fullInventory) {
        if (!disguiseSkinsKnown) {
            return;
        }
        applyDisguiseSkin(sp.getMainHandItem());
        applyDisguiseSkin(sp.getOffhandItem());
        if (!fullInventory) {
            return;
        }
        var inventory = sp.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                applyDisguiseSkin(stack);
            }
        }
    }

    private void applyDisguiseSkin(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SkinableItem skinable)) {
            return;
        }
        String itemType = skinable.getItemSkinType();
        if (itemType == null || itemType.isBlank()) {
            return;
        }
        String skin = disguiseSkins.getOrDefault(itemType, "default");
        if (skin == null || skin.isBlank()) {
            skin = "default";
        }
        // 注意：即使目标是 default 也要写入 "default" 组件，不能 remove。
        // 否则 ItemSkinManager.getEquippedSkin 会回退到千面人自己装备的皮肤，
        // 表现为武器贴图/模型仍是千面人自己的。
        if (!skin.equals(stack.get(SREDataComponentTypes.SKIN))) {
            stack.set(SREDataComponentTypes.SKIN, skin);
        }
    }

    // ── 背包快照（改职时不发放目标职业的开局物品） ─────────────

    private static List<ItemStack> snapshotInventory(ServerPlayer sp) {
        int size = sp.getInventory().getContainerSize();
        List<ItemStack> snapshot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            snapshot.add(sp.getInventory().getItem(i).copy());
        }
        return snapshot;
    }

    private static void restoreInventory(ServerPlayer sp, List<ItemStack> snapshot, int selectedSlot) {
        sp.getInventory().clearContent();
        for (int i = 0; i < snapshot.size() && i < sp.getInventory().getContainerSize(); i++) {
            sp.getInventory().setItem(i, snapshot.get(i));
        }
        sp.getInventory().selected = selectedSlot;
        sp.getInventory().setChanged();
    }

    // ── 同步 / 持久化 ───────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("Active", active);
        tag.putBoolean("Disguised", disguised);
        tag.putInt("Uses", uses);
        tag.putBoolean("Dead", permanentlyDead);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        active = tag.getBoolean("Active");
        disguised = tag.getBoolean("Disguised");
        uses = tag.getInt("Uses");
        permanentlyDead = tag.getBoolean("Dead");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不跨局持久化
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不跨局持久化
    }
}
