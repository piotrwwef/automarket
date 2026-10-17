package pl.automarket;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AutoMarketMod implements ClientModInitializer {
    private static Config config;
    private static Bot bot;
    private static boolean openGui = false;
    private static boolean guiWasDown = false;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        bot = new Bot(config);
        ClientTickEvents.END_CLIENT_TICK.register(bot::tick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Picker.tick(mc, config));
        ClientTickEvents.END_CLIENT_TICK.register(AutoMarketMod::guiTick);
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    /** Otwiera GUI klawiszem (domyslnie prawy Ctrl) albo po komendzie /am gui. */
    private static void guiTick(MinecraftClient mc) {
        if (mc.player == null) return;
        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), config.guiKey) == GLFW.GLFW_PRESS;
        if (mc.currentScreen == null && ((down && !guiWasDown) || openGui)) {
            openGui = false;
            mc.setScreen(new MainScreen(config, bot));
        }
        guiWasDown = down;
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String s) {
        ctx.getSource().sendFeedback(Text.literal("[AutoRynek] " + s));
    }

    private static String stripQuotes(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.trim();
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(
                literal("automarket")
                        .then(literal("start").executes(ctx -> {
                            bot.start(MinecraftClient.getInstance());
                            return 1;
                        }))
                        .then(literal("stop").executes(ctx -> {
                            bot.stop(MinecraftClient.getInstance(), "komenda");
                            return 1;
                        }))
                        .then(literal("status").executes(ctx -> {
                            say(ctx, bot.status());
                            return 1;
                        }))
                        .then(literal("gui").executes(ctx -> {
                            openGui = true;
                            return 1;
                        }))
                        // /am add <nazwa lub id przedmiotu> <cena: 50000 / 50k / 1.5m>
                        .then(literal("add")
                                .then(argument("tekst", StringArgumentType.greedyString()).executes(ctx -> {
                                    String t = StringArgumentType.getString(ctx, "tekst").trim();
                                    int sp = t.lastIndexOf(' ');
                                    if (sp <= 0) {
                                        say(ctx, "Uzycie: /am add <nazwa> <cena>, np. /am add ender_pearl 500");
                                        return 0;
                                    }
                                    String k = stripQuotes(t.substring(0, sp));
                                    Double c = Listing.parseMoney(t.substring(sp + 1));
                                    if (c == null || k.isEmpty()) {
                                        say(ctx, "Nie rozumiem ceny \"" + t.substring(sp + 1) + "\". Wpisz np. 50000 albo 50k.");
                                        return 0;
                                    }
                                    String id = k.toLowerCase(Locale.ROOT).replace(' ', '_');
                                    Config.Rule r;
                                    if (id.equals("all") || id.equals("*")) {
                                        r = config.find("*", "");
                                        if (r == null) r = new Config.Rule("*", "", c);
                                        else config.snipes.remove(r);
                                    } else if (Registries.ITEM.containsId(Identifier.of("minecraft", id))) {
                                        r = config.find(id, "");
                                        if (r != null) config.snipes.remove(r);
                                        r = new Config.Rule(id, "", c);
                                    } else {
                                        r = config.find("*", k);
                                        if (r != null) config.snipes.remove(r);
                                        r = new Config.Rule("*", k, c);
                                    }
                                    r.max = c;
                                    config.snipes.add(r);
                                    config.save();
                                    say(ctx, "Dodano: " + r.title() + " <= $" + Bot.fmt(c) + " za sztuke");
                                    return 1;
                                })))
                        // /am remove <numer z /am list albo nazwa>
                        .then(literal("remove")
                                .then(argument("tekst", StringArgumentType.greedyString()).executes(ctx -> {
                                    String t = stripQuotes(StringArgumentType.getString(ctx, "tekst"));
                                    Config.Rule found = null;
                                    try {
                                        int n = Integer.parseInt(t);
                                        if (n >= 1 && n <= config.snipes.size()) found = config.snipes.get(n - 1);
                                    } catch (NumberFormatException ignored) {
                                        String low = t.toLowerCase(Locale.ROOT);
                                        int hits = 0;
                                        for (Config.Rule r : config.snipes) {
                                            if (r.title().toLowerCase(Locale.ROOT).contains(low)) {
                                                found = r;
                                                hits++;
                                            }
                                        }
                                        if (hits > 1) {
                                            say(ctx, "Pasuje kilka pozycji, uzyj numeru z /am list.");
                                            return 0;
                                        }
                                    }
                                    if (found == null) {
                                        say(ctx, "Nie ma takiej pozycji. Sprawdz /am list i uzyj numeru.");
                                        return 0;
                                    }
                                    config.snipes.remove(found);
                                    config.save();
                                    say(ctx, "Usunieto: " + found.title());
                                    return 1;
                                })))
                        .then(literal("clear").executes(ctx -> {
                            int n = config.snipes.size();
                            config.snipes.clear();
                            config.save();
                            say(ctx, "Wyczyszczono liste (" + n + " pozycji).");
                            return 1;
                        }))
                        .then(literal("list").executes(ctx -> {
                            if (config.snipes.isEmpty()) say(ctx, "Lista jest pusta.");
                            int num = 0;
                            for (Config.Rule r : config.snipes) {
                                say(ctx, (++num) + ". " + r.title() + " <= $" + Bot.fmt(r.max) + " / szt."
                                        + (r.on ? "" : " (wylaczone)"));
                            }
                            say(ctx, "budzet: " + (config.budget > 0 ? "$" + Bot.fmt(config.budget) : "bez limitu")
                                    + ", klikanie: " + config.clickDelay + " ticków, odswiezanie: " + config.refreshTicks
                                    + " ticków, komenda: /" + config.command + ", sortowanie: " + config.sortMode);
                            return 1;
                        }))
                        .then(literal("budget")
                                .then(argument("kwota", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
                                    config.budget = DoubleArgumentType.getDouble(ctx, "kwota");
                                    config.save();
                                    say(ctx, "Budzet sesji: " + (config.budget > 0 ? "$" + Bot.fmt(config.budget) : "bez limitu"));
                                    return 1;
                                })))
                        .then(literal("delay")
                                .then(argument("ticki", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
                                    config.clickDelay = IntegerArgumentType.getInteger(ctx, "ticki");
                                    config.save();
                                    say(ctx, "Opoznienie miedzy kliknieciami: " + config.clickDelay + " ticków");
                                    return 1;
                                })))
                        .then(literal("refresh")
                                .then(argument("ticki", IntegerArgumentType.integer(2, 1200)).executes(ctx -> {
                                    config.refreshTicks = IntegerArgumentType.getInteger(ctx, "ticki");
                                    config.save();
                                    say(ctx, "Odswiezanie rynku co " + config.refreshTicks + " ticków");
                                    return 1;
                                })))
                        .then(literal("sort")
                                .then(literal("off").executes(ctx -> {
                                    config.sortSlot = -1;
                                    config.save();
                                    say(ctx, "Automatyczne sortowanie wylaczone");
                                    return 1;
                                }))
                                .then(argument("slot", IntegerArgumentType.integer(0, 53))
                                        .then(argument("tryb", StringArgumentType.greedyString()).executes(ctx -> {
                                            config.sortSlot = IntegerArgumentType.getInteger(ctx, "slot");
                                            config.sortMode = stripQuotes(StringArgumentType.getString(ctx, "tryb"));
                                            config.save();
                                            say(ctx, "Sortowanie: slot " + config.sortSlot + ", tryb \"" + config.sortMode + "\"");
                                            return 1;
                                        }))))
                        .then(literal("cmd")
                                .then(argument("komenda", StringArgumentType.word()).executes(ctx -> {
                                    config.command = StringArgumentType.getString(ctx, "komenda");
                                    config.save();
                                    say(ctx, "Komenda rynku: /" + config.command);
                                    return 1;
                                })))
                        .then(literal("nextpage")
                                .then(literal("off").executes(ctx -> {
                                    config.nextPageSlot = -1;
                                    config.save();
                                    say(ctx, "Przechodzenie po stronach wylaczone");
                                    return 1;
                                }))
                                .then(argument("slot", IntegerArgumentType.integer(0, 53))
                                        .then(argument("strony", IntegerArgumentType.integer(1, 50)).executes(ctx -> {
                                            config.nextPageSlot = IntegerArgumentType.getInteger(ctx, "slot");
                                            config.maxPages = IntegerArgumentType.getInteger(ctx, "strony");
                                            config.save();
                                            say(ctx, "Nastepna strona: slot " + config.nextPageSlot + ", max stron: " + config.maxPages);
                                            return 1;
                                        }))))
                        // diagnostyka: wypisuje oferty odczytane z otwartego okna rynku (dziala tez klawisz D na rynku)
                        .then(literal("scan").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            GenericContainerScreenHandler h = Bot.market(mc);
                            if (h == null) {
                                say(ctx, "Otworz najpierw rynek (/" + config.command + ") i nacisnij klawisz D.");
                                return 0;
                            }
                            int n = 0;
                            for (int i = 0; i < h.getRows() * 9; i++) {
                                Listing l = Listing.parse(h.getSlot(i).getStack());
                                if (l == null) continue;
                                n++;
                                say(ctx, "slot " + i + ": " + l.count + "x " + l.name + " [" + l.itemId + "] cena $"
                                        + Bot.fmt(l.price) + " ($" + Bot.fmt(l.unit) + "/szt.) od " + l.seller);
                            }
                            say(ctx, "Odczytano ofert: " + n);
                            return 1;
                        })));

        dispatcher.register(literal("am").redirect(root));
    }
}
