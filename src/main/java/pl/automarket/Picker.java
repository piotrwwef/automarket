package pl.automarket;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * W oknie rynku:
 *  - najedz kursorem na oferte i nacisnij A  -> dodaje/usuwa przedmiot z listy snajpowania,
 *  - nacisnij D                              -> wypisuje na czacie wszystkie oferty, ktore mod widzi (diagnostyka).
 */
public class Picker {
    private static final int DUMP_KEY = GLFW.GLFW_KEY_D;
    private static boolean wasDown = false;
    private static boolean wasDump = false;

    public static void tick(MinecraftClient mc, Config cfg) {
        if (mc.player == null) return;

        GenericContainerScreenHandler h = Bot.market(mc);
        if (h == null || !(mc.currentScreen instanceof GenericContainerScreen screen)) {
            wasDown = false;
            wasDump = false;
            return;
        }

        long window = mc.getWindow().getHandle();
        boolean down = GLFW.glfwGetKey(window, cfg.pickKey) == GLFW.GLFW_PRESS;
        boolean dump = GLFW.glfwGetKey(window, DUMP_KEY) == GLFW.GLFW_PRESS;
        boolean pressed = down && !wasDown;
        boolean dumpPressed = dump && !wasDump;
        wasDown = down;
        wasDump = dump;

        if (dumpPressed) {
            int n = 0;
            for (int i = 0; i < h.getRows() * 9; i++) {
                Listing l = Listing.parse(h.getSlot(i).getStack());
                if (l == null) continue;
                n++;
                Bot.msg(mc, "slot " + i + ": " + l.count + "x " + l.name + " | $" + Bot.fmt(l.price)
                        + " ($" + Bot.fmt(l.unit) + "/szt.) | " + l.seller, Formatting.AQUA);
            }
            Bot.msg(mc, "Odczytano ofert: " + n, Formatting.AQUA);
        }

        if (!pressed) return;

        // pozycja kursora w pikselach GUI
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();

        // okno duzej skrzyni: 176 x (114 + 18 * rzedy)
        int left = (screen.width - 176) / 2;
        int top = (screen.height - (114 + h.getRows() * 18)) / 2;

        Slot hovered = null;
        int idx = -1;
        for (int i = 0; i < h.getRows() * 9; i++) {
            Slot s = h.getSlot(i);
            if (mx >= left + s.x - 1 && mx < left + s.x + 17 && my >= top + s.y - 1 && my < top + s.y + 17) {
                hovered = s;
                idx = i;
                break;
            }
        }
        if (hovered == null) {
            Bot.msg(mc, "Kursor nie jest na zadnym slocie rynku.", Formatting.RED);
            return;
        }

        ItemStack st = hovered.getStack();
        Listing l = Listing.parse(st);
        if (l == null) {
            LoreComponent lore = st.get(DataComponentTypes.LORE);
            Bot.msg(mc, "To nie jest oferta: slot " + idx + ", " + Registries.ITEM.getId(st.getItem())
                    + ", opis: " + (lore == null ? "brak" : lore.lines().size() + " linii"), Formatting.RED);
            if (lore != null) {
                int c = 0;
                for (Text line : lore.lines()) {
                    if (c++ >= 8) break;
                    Bot.msg(mc, "  > " + line.getString(), Formatting.GRAY);
                }
            }
            return;
        }

        // zwykla nazwa przedmiotu -> bez filtra nazwy; wlasna nazwa (np. mikstura) -> filtr po nazwie
        String defName = st.getItem().getName().getString();
        String nm = l.name.equals(defName) ? "" : l.name;

        Config.Rule existing = cfg.find(l.itemId, nm);
        if (existing != null) {
            cfg.snipes.remove(existing);
            cfg.save();
            Bot.msg(mc, "Usunieto z listy: " + l.name, Formatting.YELLOW);
        } else {
            cfg.snipes.add(new Config.Rule(l.itemId, nm, l.unit));
            cfg.save();
            Bot.msg(mc, "Dodano: " + l.name + " <= $" + Bot.fmt(l.unit) + " za sztuke (zmienisz w GUI)", Formatting.GREEN);
        }
    }
}
