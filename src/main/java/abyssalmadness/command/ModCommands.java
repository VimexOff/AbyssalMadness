package abyssalmadness.command;

import abyssalmadness.ambient.PhantomSounds;
import abyssalmadness.beacon.LureBeacon;
import abyssalmadness.blackout.Blackout;
import abyssalmadness.cleansing.Cleansing;
import abyssalmadness.hollow.Hollows;
import abyssalmadness.infection.InfectionState;
import abyssalmadness.star.StarCycle;
import abyssalmadness.teleport.FogTeleport;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Ops-only commands:
//   /abyss stars wake|sleep|status      control the Sleeping Stars cycle
//   /abyss infection <0-100> [players]  set a player's infection level
//   /abyss ambient on|off|status|test   the Level 1 phantom-player sounds
//   /abyss teleport [players]           Level 4: fog closes in and moves them
//   /abyss beacon light|out|status      the lure: a beam that infects whoever reaches it
//   /abyss blackout ...                 the world seen as a sphere of dark, and the
//                                       rot walking through it leaves behind
public final class ModCommands {
    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("abyss")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("stars")
                                .then(Commands.literal("wake")
                                        .executes(ctx -> wake(ctx.getSource(), 60))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 100000))
                                                .executes(ctx -> wake(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds")))))
                                .then(Commands.literal("sleep")
                                        .executes(ctx -> sleep(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource()))))
                        .then(Commands.literal("cleansing")
                                .then(Commands.literal("stop")
                                        .executes(ctx -> cleansingStop(ctx.getSource())))
                                .then(Commands.literal("start")
                                        .executes(ctx -> cleansingStart(ctx.getSource(), "")))
                                // /abyss cleansing NICK1,NICK2 - exactly these live
                                .then(Commands.argument("survivors", StringArgumentType.greedyString())
                                        .executes(ctx -> cleansingStart(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "survivors"))))
                                .executes(ctx -> cleansingStatus(ctx.getSource())))
                        .then(Commands.literal("teleport")
                                .executes(ctx -> fogTeleport(ctx.getSource(),
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> fogTeleport(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("ambient")
                                .then(Commands.literal("on")
                                        .executes(ctx -> ambient(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> ambient(ctx.getSource(), false)))
                                .then(Commands.literal("test")
                                        .executes(ctx -> ambientTest(ctx.getSource())))
                                .executes(ctx -> ambientStatus(ctx.getSource())))
                        .then(Commands.literal("infection")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, InfectionState.MAX))
                                        .executes(ctx -> infect(ctx.getSource(),
                                                List.of(ctx.getSource().getPlayerOrException()),
                                                IntegerArgumentType.getInteger(ctx, "percent")))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> infect(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        IntegerArgumentType.getInteger(ctx, "percent"))))))
                        .then(Commands.literal("beacon")
                                .then(Commands.literal("anchor")
                                        .executes(ctx -> beaconAnchor(ctx.getSource())))
                                .then(Commands.literal("activate")
                                        .executes(ctx -> beaconActivate(ctx.getSource())))
                                .then(Commands.literal("out")
                                        .executes(ctx -> beaconOut(ctx.getSource())))
                                .executes(ctx -> beaconStatus(ctx.getSource())))
                        .then(Commands.literal("hollow")
                                .then(Commands.literal("spawn")
                                        .then(Commands.literal("warm")
                                                .executes(ctx -> hollowSpawn(ctx.getSource(), true)))
                                        .executes(ctx -> hollowSpawn(ctx.getSource(), false)))
                                .then(Commands.literal("watch")
                                        .then(Commands.literal("on")
                                                .executes(ctx -> hollowMode(ctx.getSource(), Hollows.Mode.WATCH)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> hollowMode(ctx.getSource(), Hollows.Mode.STILL))))
                                .then(Commands.literal("chase")
                                        .then(Commands.literal("slow")
                                                .executes(ctx -> hollowMode(ctx.getSource(), Hollows.Mode.SLOW)))
                                        .then(Commands.literal("fast")
                                                .executes(ctx -> hollowMode(ctx.getSource(), Hollows.Mode.FAST)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> hollowMode(ctx.getSource(), Hollows.Mode.STILL))))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> hollowClear(ctx.getSource())))
                                .executes(ctx -> hollowStatus(ctx.getSource())))
                        .then(Commands.literal("blackout")
                                .then(Commands.literal("on")
                                        .executes(ctx -> blackout(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> blackout(ctx.getSource(), false)))
                                .then(Commands.literal("anchor")
                                        .executes(ctx -> blackoutAnchor(ctx.getSource())))
                                .then(Commands.literal("radius")
                                        .then(Commands.argument("blocks",
                                                        IntegerArgumentType.integer(Blackout.MIN_RADIUS, Blackout.MAX_RADIUS))
                                                .executes(ctx -> blackoutRadius(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "blocks")))))
                                .then(Commands.literal("noise")
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(ctx -> blackoutNoise(ctx.getSource(),
                                                        FloatArgumentType.getFloat(ctx, "amount")))))
                                .then(Commands.literal("palette")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests((ctx, builder) ->
                                                        SharedSuggestionProvider.suggest(Blackout.PALETTES, builder))
                                                .executes(ctx -> blackoutPalette(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("follow")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> blackoutFollow(ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(Commands.literal("decay")
                                        .then(Commands.literal("on")
                                                .executes(ctx -> decay(ctx.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> decay(ctx.getSource(), false)))
                                        .then(Commands.literal("clear")
                                                .executes(ctx -> decayClear(ctx.getSource())))
                                        .then(Commands.literal("radius")
                                                .then(Commands.argument("blocks",
                                                                IntegerArgumentType.integer(Blackout.DECAY_MIN_RADIUS, Blackout.DECAY_MAX_RADIUS))
                                                        .executes(ctx -> decayRadius(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "blocks")))))
                                        .then(Commands.literal("reach")
                                                .then(Commands.argument("blocks",
                                                                IntegerArgumentType.integer(Blackout.DECAY_MIN_REACH, Blackout.DECAY_MAX_REACH))
                                                        .executes(ctx -> decayReach(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "blocks"))))))
                                .executes(ctx -> blackoutStatus(ctx.getSource())))
                        .then(Commands.literal("immune")
                                .then(Commands.literal("on")
                                        .executes(ctx -> immune(ctx.getSource(),
                                                List.of(ctx.getSource().getPlayerOrException()), true))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> immune(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"), true))))
                                .then(Commands.literal("off")
                                        .executes(ctx -> immune(ctx.getSource(),
                                                List.of(ctx.getSource().getPlayerOrException()), false))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> immune(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"), false)))))
                        .then(Commands.literal("warm")
                                .then(Commands.literal("on")
                                        .executes(ctx -> warm(ctx.getSource(),
                                                List.of(ctx.getSource().getPlayerOrException()), true))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> warm(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"), true))))
                                .then(Commands.literal("off")
                                        .executes(ctx -> warm(ctx.getSource(),
                                                List.of(ctx.getSource().getPlayerOrException()), false))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> warm(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"), false)))))));
    }

    // Only the colour changes; the level and everything it drives is untouched.
    private static int warm(CommandSourceStack source, Collection<ServerPlayer> targets, boolean on) {
        for (ServerPlayer player : targets) {
            InfectionState.setWarm(source.getServer(), player.getUUID(), on);
        }
        String names = targets.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(
                (on ? "Тёплый свет: " : "Холодный свет: ") + names), false);
        return targets.size();
    }

    // A sphere of dark around the player, and the rot left behind by walking through
    // it. Neither touches a block - both repaint the chunk mesh on the client, so
    // nothing can be mined and nothing moves differently. One config for everybody.
    private static int blackout(CommandSourceStack source, boolean on) {
        Blackout.setEnabled(source.getServer(), on);
        source.sendSuccess(() -> Component.literal(on
                ? "Затемнение включено: радиус " + Blackout.radius() + ", палитра " + Blackout.palette()
                : "Затемнение выключено"), false);
        return 1;
    }

    private static int blackoutAnchor(CommandSourceStack source) {
        if (!Blackout.isEnabled()) {
            source.sendFailure(Component.literal("Затемнение выключено"));
            return 0;
        }
        Blackout.anchor(source.getServer());
        source.sendSuccess(() -> Component.literal("Сфера пересажена на игрока"), false);
        return 1;
    }

    private static int blackoutRadius(CommandSourceStack source, int blocks) {
        Blackout.setRadius(source.getServer(), blocks);
        source.sendSuccess(() -> Component.literal("Радиус затемнения: " + Blackout.radius()), false);
        return 1;
    }

    private static int blackoutNoise(CommandSourceStack source, float amount) {
        Blackout.setNoise(source.getServer(), amount);
        source.sendSuccess(() -> Component.literal(
                String.format("Рваность края: %.2f", Blackout.noise())), false);
        return 1;
    }

    private static int blackoutPalette(CommandSourceStack source, String name) {
        if (!Blackout.setPalette(source.getServer(), name)) {
            source.sendFailure(Component.literal("Нет такой палитры: " + name
                    + " (есть: " + String.join(", ", Blackout.PALETTES) + ")"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Палитра: " + Blackout.palette()), false);
        return 1;
    }

    private static int blackoutFollow(CommandSourceStack source, boolean on) {
        Blackout.setFollow(source.getServer(), on);
        source.sendSuccess(() -> Component.literal(on
                ? "Сфера едет за игроком" : "Сфера стоит на месте"), false);
        return 1;
    }

    private static int decay(CommandSourceStack source, boolean on) {
        Blackout.setDecay(source.getServer(), on);
        source.sendSuccess(() -> Component.literal(on
                ? "Гниение включено: радиус " + Blackout.decayRadius() + ", захват " + Blackout.decayReach()
                : "Гниение выключено (след сохранён, стереть - /abyss blackout decay clear)"), false);
        return 1;
    }

    private static int decayClear(CommandSourceStack source) {
        Blackout.wipeTrail(source.getServer());
        source.sendSuccess(() -> Component.literal("След стёрт"), false);
        return 1;
    }

    private static int decayRadius(CommandSourceStack source, int blocks) {
        Blackout.setDecayRadius(source.getServer(), blocks);
        source.sendSuccess(() -> Component.literal(
                "Ширина следа: " + Blackout.decayRadius() + " (для нового пути)"), false);
        return 1;
    }

    private static int decayReach(CommandSourceStack source, int blocks) {
        Blackout.setDecayReach(source.getServer(), blocks);
        source.sendSuccess(() -> Component.literal("Захват по высоте: " + Blackout.decayReach()), false);
        return 1;
    }

    private static int blackoutStatus(CommandSourceStack source) {
        String text = "Затемнение " + (Blackout.isEnabled() ? "вкл" : "выкл")
                + " | радиус " + Blackout.radius()
                + " | рваность " + String.format("%.2f", Blackout.noise())
                + " | палитра " + Blackout.palette()
                + " | " + (Blackout.follows() ? "едет за игроком" : "на месте")
                + "\nГниение " + (Blackout.decayEnabled() ? "вкл" : "выкл")
                + " | радиус " + Blackout.decayRadius()
                + " | захват " + Blackout.decayReach();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // Absolute: nothing raises an immune player's level. For whoever holds the camera.
    private static int immune(CommandSourceStack source, Collection<ServerPlayer> targets, boolean on) {
        for (ServerPlayer player : targets) {
            InfectionState.setImmune(player, on);
        }
        String names = targets.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(
                (on ? "Иммунитет выдан: " : "Иммунитет снят: ") + names), false);
        return targets.size();
    }

    // Stands where the caller does, facing the same way, until watching is switched on.
    private static int hollowSpawn(CommandSourceStack source, boolean warm) throws CommandSyntaxException {
        ServerPlayer self = source.getPlayerOrException();
        if (!Hollows.spawn(self.level(), self.position(), self.getYRot(), warm)) {
            source.sendFailure(Component.literal("Не удалось поставить пустышку"));
            return 0;
        }
        String what = warm ? "Тёплая пустышка поставлена" : "Пустышка поставлена";
        source.sendSuccess(() -> Component.literal(what), false);
        return 1;
    }

    private static int hollowMode(CommandSourceStack source, Hollows.Mode to) {
        Hollows.setMode(to);
        source.sendSuccess(() -> Component.literal("Пустышки: " + describe(to)), false);
        return 1;
    }

    private static String describe(Hollows.Mode mode) {
        return switch (mode) {
            case STILL -> "замерли";
            case WATCH -> "следят";
            case SLOW -> "идут";
            case FAST -> "бегут";
        };
    }

    private static int hollowClear(CommandSourceStack source) {
        int removed = Hollows.clear(source.getServer());
        source.sendSuccess(() -> Component.literal("Убрано пустышек: " + removed), false);
        return 1;
    }

    private static int hollowStatus(CommandSourceStack source) {
        String text = "Пустышек: " + Hollows.count() + ", " + describe(Hollows.mode());
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // Anchored where the caller stands, so no coordinates are typed on camera.
    // Lighting it is a separate call.
    private static int beaconAnchor(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer self = source.getPlayerOrException();
        LureBeacon.anchor(self.level(), self.blockPosition());
        source.sendSuccess(() -> Component.literal("Маяк закреплён здесь"), false);
        return 1;
    }

    private static int beaconActivate(CommandSourceStack source) {
        if (!LureBeacon.activate()) {
            source.sendFailure(Component.literal("Сначала /abyss beacon anchor"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Маяк зажжён"), false);
        return 1;
    }

    private static int beaconOut(CommandSourceStack source) {
        LureBeacon.extinguish();
        source.sendSuccess(() -> Component.literal("Маяк погашен"), false);
        return 1;
    }

    private static int beaconStatus(CommandSourceStack source) {
        String text;
        if (!LureBeacon.anchored()) {
            text = "Маяк не закреплён";
        } else {
            text = (LureBeacon.active() ? "Маяк горит: " : "Маяк закреплён, не горит: ")
                    + LureBeacon.site().toShortString();
        }
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // Silent: a chat line here would show on the recording and give it away.
    private static int cleansingStart(CommandSourceStack source, String survivorList)
            throws CommandSyntaxException {
        ServerPlayer self = source.getPlayerOrException();
        List<String> names = survivorList.isBlank()
                ? List.of()
                : Arrays.asList(survivorList.split("[,\\s]+"));
        Cleansing.start(self, names);
        return 1;
    }

    private static int cleansingStop(CommandSourceStack source) {
        Cleansing.stop();
        return 1;
    }

    // On demand rather than broadcast, for the same reason.
    private static int cleansingStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        double dist = Cleansing.distanceTo(player.position());
        String state = !Cleansing.isActive()
                ? "не активна"
                : "идёт, до тебя " + Math.round(dist) + " блоков";

        List<String> survivors = Cleansing.survivors(source.getServer());
        List<String> exposed = Cleansing.exposed(source.getServer());
        StringBuilder text = new StringBuilder("Очистка: " + state);
        text.append("\n§aВыживут: §f")
                .append(survivors.isEmpty() ? "никто" : String.join(", ", survivors));
        text.append("\n§cПогибнут: §f")
                .append(exposed.isEmpty() ? "никто" : String.join(", ", exposed));

        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int fogTeleport(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            FogTeleport.begin(player);
        }
        int count = targets.size();
        source.sendSuccess(() -> Component.literal(
                "Туман сужается... игроков: " + count), true);
        return count;
    }

    private static int ambient(CommandSourceStack source, boolean on) {
        PhantomSounds.setEnabled(on);
        source.sendSuccess(() -> Component.literal(
                "Призрачные звуки: " + (on ? "ВКЛ" : "выкл")), true);
        return 1;
    }

    private static int ambientStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long dist = Math.max(Math.abs((long) player.getBlockX()), Math.abs((long) player.getBlockZ()));
        double progress = PhantomSounds.progressAt(dist);
        String state = PhantomSounds.isEnabled() ? "ВКЛ" : "выкл";
        String where = progress < 0
                ? "вне 1-го уровня, тишина"
                : "нарастание " + Math.round(progress * 100) + "%, раз в "
                        + String.format("%.1f", PhantomSounds.intervalFor(dist) / 20.0)
                        + " с, по " + PhantomSounds.burstFor(dist) + " звук(а) разом";
        source.sendSuccess(() -> Component.literal(
                "Призрачные звуки: " + state + " | " + where + " (дистанция " + dist + ")"), false);
        return 1;
    }

    private static int ambientTest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long dist = Math.max(Math.abs((long) player.getBlockX()), Math.abs((long) player.getBlockZ()));
        double progress = PhantomSounds.progressAt(dist);
        if (progress < 0) {
            PhantomSounds.burst(player, 1.0, PhantomSounds.maxBurst()); // outside: full power
        } else {
            PhantomSounds.burst(player, progress, PhantomSounds.burstFor(dist));
        }
        source.sendSuccess(() -> Component.literal("Пробный залп звуков."), false);
        return 1;
    }

    private static int infect(CommandSourceStack source, Collection<ServerPlayer> targets, int percent) {
        for (ServerPlayer player : targets) {
            InfectionState.set(player, percent);
        }
        int count = targets.size();
        source.sendSuccess(() -> Component.literal(
                "Заражение " + percent + "% - игроков: " + count), true);
        return count;
    }

    private static int wake(CommandSourceStack source, int seconds) {
        StarCycle.forceWake(source.getServer(), seconds);
        source.sendSuccess(() -> Component.literal("Звёзды разбужены на " + seconds + " с."), true);
        return 1;
    }

    private static int sleep(CommandSourceStack source) {
        StarCycle.forceSleep(source.getServer());
        source.sendSuccess(() -> Component.literal("Звёзды усыплены."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        boolean awake = StarCycle.isAwake();
        long secondsLeft = StarCycle.ticksLeft(source.getServer()) / 20L;
        source.sendSuccess(() -> Component.literal("Звёзды: " + (awake ? "ПРОСНУЛИСЬ" : "спят")
                + " (смена через ~" + secondsLeft + " с)"), false);
        return 1;
    }
}
