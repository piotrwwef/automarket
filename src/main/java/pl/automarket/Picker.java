package pl.automarket;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Dodawanie przedmiotow do listy kupowanych: w oknie rynku najedz kursorem na oferte i nacisnij klawisz (domyslnie A).
 * Limit ceny = aktualna cena za sztuke tej oferty. Ponowne nacisniecie usuwa przedmiot z listy.
 */
public class Picker {
    private static boolean wasDown = false;

    public static void tick(MinecraftClient mc, Config cfg) {
        if (mc.player == null) return;

        GenericContainerScreenHandler h = Bot.market(mc);
        if (h == null || !(mc.currentScreen instanceof GenericContainerScreen screen)) {
            wasDown = false;
            return;
        }

        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), cfg.pickKey) == GLFW.GLFW_PRESS;
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!pressed) return;

        // pozycja kursora w pikselach GUI
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();

        // okno duzej skrzyni: 176 x (114 + 18 * rzedy)
        int left = (screen.width - 176) / 2;
        int top = (screen.height - (114 + h.getRows() * 18)) / 2;

        Slot hovered = null;
        for (int i = 0; i < h.getRows() * 9; i++) {
            Slot s = h.getSlot(i);
            if (mx >= left + s.x - 1 && mx < left + s.x + 17 && my >= top + s.y - 1 && my < top + s.y + 17) {
                hovered = s;
                break;
            }
        }
        if (hovered == null) return;

        Listing l = Listing.parse(hovered.getStack());
        if (l == null) {
            Bot.msg(mc, "To nie jest oferta rynku.", Formatting.RED);
            return;
        }

        String key = l.name.toLowerCase(Locale.ROOT);
        if (cfg.rules.containsKey(key)) {
            cfg.rules.remove(key);
            cfg.save();
            Bot.msg(mc, "Usunieto z listy: " + l.name, Formatting.YELLOW);
        } else {
            cfg.rules.put(key, l.unit);
            cfg.save();
            Bot.msg(mc, "Dodano: " + l.name + " <= $" + Bot.fmt(l.unit) + " za sztuke", Formatting.GREEN);
        }
    }
}
