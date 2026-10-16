package io.wifi.starrailexpress.content.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.game.roles.killer.dream.VirtualShieldComponent;

import java.util.Collection;

public class ShieldCommand {
  private static final String TYPE_VIRTUAL = "virtual";

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    var shieldCommand = dispatcher.register(Commands.literal("sre:shield")
        .requires(source -> source.hasPermission(2))
        .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
            .executes(context -> executeAdd(context.getSource(),
                ImmutableList.of(context.getSource().getEntityOrException()),
                IntegerArgumentType.getInteger(context, "amount")))
            .then(
                Commands.argument("targets", EntityArgument.entities())
                    .executes(context -> executeAdd(context.getSource(),
                        EntityArgument.getEntities(context, "targets"),
                        IntegerArgumentType.getInteger(context, "amount"))))))
        .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer())
            .executes(context -> executeSet(context.getSource(),
                ImmutableList.of(context.getSource().getEntityOrException()),
                IntegerArgumentType.getInteger(context, "amount")))
            .then(
                Commands.argument("targets", EntityArgument.entities())
                    .executes(context -> executeSet(context.getSource(),
                        EntityArgument.getEntities(context, "targets"),
                        IntegerArgumentType.getInteger(context, "amount"))))))
        .then(Commands.literal("get").executes(context -> executeGet(context.getSource(),
            ImmutableList.of(context.getSource().getEntityOrException())))
            .then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> executeGet(context.getSource(),
                    EntityArgument.getEntities(context, "targets")))))
        // 虚拟护盾（点数条，只抵挡虚拟血量伤害）：移植自 SRE ShieldCommand 的 virtual 分支
        .then(buildVirtual()));

    dispatcher.register(
        Commands.literal("sre:armor")
            .redirect(shieldCommand) // /h 会执行 /home 的逻辑
    );
  }

  /**
   * {@code sre:shield virtual <add|set|get|clear> <值> [目标...]}。
   *
   * <p>虚拟护盾挂在 {@code VirtualShieldComponent} 上，只抵挡 Dream 虚拟血量伤害，
   * 挡不住正常死亡。
   */
  private static LiteralArgumentBuilder<CommandSourceStack> buildVirtual() {
    LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add")
        .then(Commands.argument("value", IntegerArgumentType.integer(1))
            .executes(context -> virtualAdd(context.getSource(),
                ImmutableList.of(context.getSource().getEntityOrException()),
                IntegerArgumentType.getInteger(context, "value")))
            .then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> virtualAdd(context.getSource(),
                    EntityArgument.getEntities(context, "targets"),
                    IntegerArgumentType.getInteger(context, "value")))));
    LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
        .then(Commands.argument("value", IntegerArgumentType.integer(0))
            .executes(context -> virtualSet(context.getSource(),
                ImmutableList.of(context.getSource().getEntityOrException()),
                IntegerArgumentType.getInteger(context, "value")))
            .then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> virtualSet(context.getSource(),
                    EntityArgument.getEntities(context, "targets"),
                    IntegerArgumentType.getInteger(context, "value")))));
    LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get")
        .executes(context -> virtualGet(context.getSource(),
            ImmutableList.of(context.getSource().getEntityOrException())))
        .then(Commands.argument("targets", EntityArgument.entities())
            .executes(context -> virtualGet(context.getSource(),
                EntityArgument.getEntities(context, "targets"))));
    LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
        .executes(context -> virtualClear(context.getSource(),
            ImmutableList.of(context.getSource().getEntityOrException())))
        .then(Commands.argument("targets", EntityArgument.entities())
            .executes(context -> virtualClear(context.getSource(),
                EntityArgument.getEntities(context, "targets"))));
    return Commands.literal(TYPE_VIRTUAL)
        .then(add)
        .then(set)
        .then(get)
        .then(clear);
  }

  private static int virtualAdd(CommandSourceStack source, Collection<? extends Entity> targets, int value) {
    int applied = 0;
    for (Entity target : targets) {
      if (VirtualShieldComponent.KEY.get(target).addShield(value) >= 0) {
        applied++;
      }
    }
    final int total = applied;
    source.sendSuccess(
        () -> Component.translatable("commands.sre.shield.virtual.add", total, value)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return applied;
  }

  private static int virtualSet(CommandSourceStack source, Collection<? extends Entity> targets, int value) {
    int applied = 0;
    for (Entity target : targets) {
      if (VirtualShieldComponent.KEY.get(target).setShield(value) >= 0) {
        applied++;
      }
    }
    final int total = applied;
    source.sendSuccess(
        () -> Component.translatable("commands.sre.shield.virtual.set", total, value)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return applied;
  }

  private static int virtualGet(CommandSourceStack source, Collection<? extends Entity> targets) {
    MutableComponent out = Component.translatable("commands.sre.shield.virtual.get.header")
        .withStyle(style -> style.withColor(0x00FF00));
    for (Entity target : targets) {
      out.append(Component.translatable("commands.sre.shield.virtual.get.entry", target.getName().getString(),
          VirtualShieldComponent.KEY.get(target).currentShield()));
    }
    source.sendSuccess(() -> out, true);
    return 1;
  }

  private static int virtualClear(CommandSourceStack source, Collection<? extends Entity> targets) {
    int applied = 0;
    for (Entity target : targets) {
      if (VirtualShieldComponent.KEY.get(target).clearShield() >= 0) {
        applied++;
      }
    }
    final int total = applied;
    source.sendSuccess(
        () -> Component.translatable("commands.sre.shield.virtual.clear", total)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return applied;
  }

  private static int executeSet(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
    int total = 0;

    for (Entity target : targets) {
      int finalAmount = Math.max(amount, 0);
      SREArmorPlayerComponent.KEY.get(target).armor = finalAmount;
      SREArmorPlayerComponent.KEY.get(target).sync();
      total += finalAmount;
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.setshield", target.getName().getString(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.setshield.multiple", targets.size(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return total;
  }

  private static int executeAdd(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
    int total = 0;
    for (Entity target : targets) {
      SREArmorPlayerComponent comp = SREArmorPlayerComponent.KEY.get(target);
      comp.armor = Math.max(0, comp.armor + amount);
      comp.sync();
      total += comp.armor;
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      int shield = SREArmorPlayerComponent.KEY.get(target).armor;
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.addshield", target.getName().getString(), amount, shield)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.addshield.multiple", targets.size(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return total;
  }

  private static int executeGet(CommandSourceStack source, Collection<? extends Entity> targets) {
    final int total = targets.stream().mapToInt(target -> {
      var comp = SREArmorPlayerComponent.KEY.maybeGet(target).orElse(null);
      if (comp != null) {
        return comp.armor;
      }
      return 0;
    }).sum();
    source.sendSuccess(
        () -> Component
            .translatable("commands.sre.getshield", total)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return total;
  }
}
