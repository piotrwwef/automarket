package pl.automarket;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AutoMarketMod implements ClientModInitializer {
    private static Config config;
    private static Bot bot;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        bot = new Bot(config);
        ClientTickEvents.END_CLIENT_TICK.register(bot::tick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Picker.tick(mc, config));
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    private static void say(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, String s) {
        ctx.getSource().sendFeedback(Text.literal("[AutoRynek] " + s));
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
                        .then(literal("add")
                                .then(argument("klucz", StringArgumentType.string())
                                        .then(argument("cena", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> {
                                                    String k = StringArgumentType.getString(ctx, "klucz");
                                                    double c = DoubleArgumentType.getDouble(ctx, "cena");
                                                    config.rules.put(k, c);
                                                    config.save();
                                                    say(ctx, "Regula: " + k + " <= $" + Bot.fmt(c) + " za sztuke");
                                                    return 1;
                                                }))))
                        .then(literal("remove")
                                .then(argument("klucz", StringArgumentType.string()).executes(ctx -> {
                                    String k = StringArgumentType.getString(ctx, "klucz");
                                    boolean removed = config.rules.remove(k) != null;
                                    config.save();
                                    say(ctx, removed ? "Usunieto: " + k : "Nie ma takiej reguly: " + k);
                                    return 1;
                                })))
                        .then(literal("list").executes(ctx -> {
                            if (config.rules.isEmpty()) say(ctx, "Brak regul.");
                            for (Map.Entry<String, Double> e : config.rules.entrySet()) {
                                say(ctx, e.getKey() + " <= $" + Bot.fmt(e.getValue()) + " / szt.");
                            }
                            say(ctx, "budzet: " + (config.budget > 0 ? "$" + Bot.fmt(config.budget) : "bez limitu")
                                    + ", opoznienie: " + config.clickDelay + " ticków, odswiezanie: " + config.refreshTicks
                                    + " ticków, komenda: /" + config.command);
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
                                .then(argument("ticki", IntegerArgumentType.integer(2, 100)).executes(ctx -> {
                                    config.clickDelay = IntegerArgumentType.getInteger(ctx, "ticki");
                                    config.save();
                                    say(ctx, "Opoznienie miedzy kliknieciami: " + config.clickDelay + " ticków");
                                    return 1;
                                })))
                        .then(literal("refresh")
                                .then(argument("ticki", IntegerArgumentType.integer(10, 1200)).executes(ctx -> {
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
                                        .then(argument("nazwa", StringArgumentType.string()).executes(ctx -> {
                                            config.sortSlot = IntegerArgumentType.getInteger(ctx, "slot");
                                            config.sortName = StringArgumentType.getString(ctx, "nazwa");
                                            config.save();
                                            say(ctx, "Sortowanie: slot " + config.sortSlot + ", tryb \"" + config.sortName + "\"");
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
                        // diagnostyka: wypisuje oferty odczytane z otwartego okna rynku
                        .then(literal("scan").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            GenericContainerScreenHandler h = Bot.market(mc);
                            if (h == null) {
                                say(ctx, "Otworz najpierw rynek (/" + config.command + ").");
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
