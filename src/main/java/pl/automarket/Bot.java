package pl.automarket;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.Optional;

public class Bot {
    private enum State { OFF, OPEN, WAIT_MARKET, SCAN, WAIT_CONFIRM, COOLDOWN }

    private final Config cfg;
    private State state = State.OFF;
    private int wait = 0;
    private int timeout = 0;
    private int page = 0;
    private int sortAttempts = 0;
    private int lastSync = -1;

    private double pendingPrice = 0;
    private String pendingName = "";

    public int bought = 0;
    public double spent = 0;

    public Bot(Config cfg) {
        this.cfg = cfg;
    }

    public boolean isRunning() {
        return state != State.OFF;
    }

    public void start(MinecraftClient mc) {
        boolean any = false;
        for (Config.Rule r : cfg.snipes) {
            if (r.on && r.max > 0) any = true;
        }
        if (!any) {
            msg(mc, "Dodaj przedmioty do snajpienia (GUI: prawy Ctrl) i ustaw cene wieksza od 0.", Formatting.RED);
            return;
        }
        bought = 0;
        spent = 0;
        page = 0;
        wait = 0;
        state = State.OPEN;
        msg(mc, "Wlaczono. Zatrzymanie: /am stop (albo przycisk Stop w GUI).", Formatting.GREEN);
    }

    public void stop(MinecraftClient mc, String reason) {
        if (state == State.OFF) return;
        state = State.OFF;
        msg(mc, "Wylaczono (" + reason + "). Kupiono: " + bought + ", wydano ok. $" + fmt(spent), Formatting.YELLOW);
    }

    public String status() {
        return (isRunning() ? "wlaczony (" + state + ")" : "wylaczony")
                + ", kupiono: " + bought + ", wydano ok. $" + fmt(spent);
    }

    public void tick(MinecraftClient mc) {
        if (state == State.OFF) return;
        // gdy uzytkownik ma otwarte GUI moda, bot czeka
        if (mc.currentScreen instanceof MainScreen || mc.currentScreen instanceof PickerScreen) return;
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            state = State.OFF;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }

        switch (state) {
            case OPEN -> {
                // nie zamykamy starego okna rynku: zapamietujemy jego id i czekamy na nowe
                GenericContainerScreenHandler cur = market(mc);
                lastSync = cur == null ? -1 : cur.syncId;
                mc.player.networkHandler.sendChatCommand(cfg.command);
                state = State.WAIT_MARKET;
                timeout = 60;
            }
            case WAIT_MARKET -> {
                GenericContainerScreenHandler h = market(mc);
                if (h != null && h.syncId != lastSync && loaded(h)) {
                    page = 0;
                    sortAttempts = 0;
                    state = State.SCAN;
                    scan(mc); // skanuje od razu w tym samym ticku, bez dodatkowego czekania
                } else if (--timeout <= 0) {
                    state = State.OPEN;
                    wait = cfg.refreshTicks;
                }
            }
            case SCAN -> scan(mc);
            case WAIT_CONFIRM -> confirm(mc);
            case COOLDOWN -> state = State.OPEN;
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- skan rynku

    private void scan(MinecraftClient mc) {
        GenericContainerScreenHandler h = market(mc);
        if (h == null) {
            state = State.OPEN;
            wait = cfg.refreshTicks;
            return;
        }

        // ustaw sortowanie (np. "Najnowsze"), jesli przycisk jest i tryb nie jest jeszcze wybrany
        if (cfg.sortSlot >= 0 && cfg.sortSlot < h.getRows() * 9 && sortAttempts < 4
                && cfg.sortMode != null && !cfg.sortMode.isEmpty()) {
            LoreComponent lore = h.getSlot(cfg.sortSlot).getStack().get(DataComponentTypes.LORE);
            if (lore != null) {
                String want = cfg.sortMode.toLowerCase(Locale.ROOT);
                boolean found = false;
                boolean selected = false;
                for (Text line : lore.lines()) {
                    if (line.getString().toLowerCase(Locale.ROOT).contains(want)) {
                        found = true;
                        selected = isUnderlined(line);
                    }
                }
                if (found && !selected) {
                    mc.interactionManager.clickSlot(h.syncId, cfg.sortSlot, 1, SlotActionType.PICKUP, mc.player);
                    sortAttempts++;
                    wait = Math.max(6, cfg.clickDelay * 2); // serwer musi zdazyc odswiezyc przycisk sortowania
                    return;
                }
            }
        }

        String me = mc.player.getName().getString();
        int best = -1;
        double bestUnit = Double.MAX_VALUE;
        Listing bestListing = null;

        int size = h.getRows() * 9;
        for (int i = 0; i < size; i++) {
            Listing l = Listing.parse(h.getSlot(i).getStack());
            if (l == null) continue;
            if (l.seller.equalsIgnoreCase(me)) continue; // nie kupuj wlasnych ofert
            Double max = maxUnitFor(l);
            if (max == null || l.unit > max) continue;
            if (cfg.budget > 0 && spent + l.price > cfg.budget) continue;
            if (l.unit < bestUnit) {
                bestUnit = l.unit;
                best = i;
                bestListing = l;
            }
        }

        if (best >= 0) {
            pendingPrice = bestListing.price;
            pendingName = bestListing.count + "x " + bestListing.name;
            click(mc, h, best);
            state = State.WAIT_CONFIRM; // czekamy na okno potwierdzenia (sprawdzamy co tick)
            timeout = 40;
            return;
        }

        if (cfg.nextPageSlot >= 0 && page + 1 < cfg.maxPages && cfg.nextPageSlot < size) {
            click(mc, h, cfg.nextPageSlot);
            page++;
            wait = cfg.clickDelay * 2;
            return;
        }

        // nic nie znaleziono -> po chwili otworz rynek od nowa (automatyczne odswiezanie)
        state = State.OPEN;
        wait = cfg.refreshTicks;
    }

    /** Okno ma juz zawartosc od serwera (pakiet z przedmiotami przychodzi zaraz po otwarciu). */
    private static boolean loaded(GenericContainerScreenHandler h) {
        int size = h.getRows() * 9;
        for (int i = 0; i < size; i++) {
            if (!h.getSlot(i).getStack().isEmpty()) return true;
        }
        return false;
    }

    /** Wybrana opcja w tooltipie sortowania jest podkreslona. */
    private static boolean isUnderlined(Text t) {
        boolean[] u = {false};
        t.visit((style, s) -> {
            if (style.isUnderlined() && !s.isBlank()) u[0] = true;
            return Optional.empty();
        }, Style.EMPTY);
        return u[0];
    }

    private Double maxUnitFor(Listing l) {
        Double best = null;
        for (Config.Rule r : cfg.snipes) {
            if (r.max <= 0 || !l.matches(r)) continue;
            if (best == null || r.max > best) best = r.max;
        }
        return best;
    }

    // ---------------------------------------------------------------- potwierdzenie

    private void confirm(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen s && s.getScreenHandler().getRows() == 3) {
            GenericContainerScreenHandler h = s.getScreenHandler();
            int ok = 11; // domyslnie zielone pole ze zrzutu ekranu
            for (int i = 0; i < 27; i++) {
                ItemStack st = h.getSlot(i).getStack();
                if (st.isEmpty()) continue;
                String id = Registries.ITEM.getId(st.getItem()).getPath();
                if (id.contains("lime") || id.contains("green")) {
                    ok = i;
                    break;
                }
            }
            click(mc, h, ok);
            bought++;
            spent += pendingPrice;
            msg(mc, "Kupiono " + pendingName + " za $" + fmt(pendingPrice), Formatting.GREEN);

            if (cfg.budget > 0 && spent >= cfg.budget) {
                stop(mc, "osiagnieto budzet");
                return;
            }
            state = State.COOLDOWN;
            wait = 2;
            return;
        }
        if (--timeout <= 0) {
            // okno potwierdzenia sie nie pojawilo
            state = State.COOLDOWN;
            wait = 2;
        }
    }

    // ---------------------------------------------------------------- narzedzia

    /** Zwraca handler duzego okna rynku (6 rzedow) albo null. */
    public static GenericContainerScreenHandler market(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen s && s.getScreenHandler().getRows() == 6) {
            return s.getScreenHandler();
        }
        return null;
    }

    private void click(MinecraftClient mc, GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    public static void msg(MinecraftClient mc, String text, Formatting color) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[AutoRynek] " + text).formatted(color), false);
        }
    }

    public static String fmt(double v) {
        return String.format("%,.0f", v).replace(',', ' ');
    }
}
