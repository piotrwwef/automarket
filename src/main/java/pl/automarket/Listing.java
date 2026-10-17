package pl.automarket;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Oferta z rynku odczytana z lore przedmiotu ("Cena: $4 000", "Wystawil: nick", "Kliknij LEWYM, aby kupic!"). */
public class Listing {
    private static final Pattern MONEY = Pattern.compile("(\\d[\\d\\s\\u00a0.,]*)\\s*([kKmM]?)");

    public final double price;
    public final int count;
    public final double unit;
    public final String seller;
    public final String itemId;
    public final String name;

    private Listing(double price, int count, String seller, String itemId, String name) {
        this.price = price;
        this.count = Math.max(1, count);
        this.unit = price / this.count;
        this.seller = seller;
        this.itemId = itemId;
        this.name = name;
    }

    /** Zwraca null, jesli przedmiot nie jest oferta rynku. */
    public static Listing parse(ItemStack stack) {
        if (stack.isEmpty()) return null;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        Double price = null;
        String seller = "";
        boolean buyable = false;

        for (Text line : lore.lines()) {
            String s = line.getString();
            String low = s.toLowerCase(Locale.ROOT);
            if (low.contains("cena:")) {
                price = parseMoney(s.substring(low.indexOf("cena:") + 5));
            } else if (low.contains("wystawi") && s.contains(":")) {
                seller = s.substring(s.indexOf(':') + 1).trim();
            } else if (low.contains("aby kupi")) {
                buyable = true;
            }
        }
        if (price == null || !buyable) return null;

        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        String name = stack.getName().getString();
        return new Listing(price, stack.getCount(), seller, id, name);
    }

    public static Double parseMoney(String text) {
        Matcher m = MONEY.matcher(text.replace("$", ""));
        if (!m.find()) return null;
        String n = m.group(1).replaceAll("[\\s\\u00a0]", "");
        String suffix = m.group(2).toLowerCase(Locale.ROOT);
        try {
            double value;
            int idx = Math.max(n.lastIndexOf('.'), n.lastIndexOf(','));
            if (idx >= 0 && n.length() - idx - 1 <= 2 && n.length() - idx - 1 >= 1) {
                String ip = n.substring(0, idx).replaceAll("[.,]", "");
                value = Double.parseDouble((ip.isEmpty() ? "0" : ip) + "." + n.substring(idx + 1));
            } else {
                value = Double.parseDouble(n.replaceAll("[.,]", ""));
            }
            if (suffix.equals("k")) value *= 1_000;
            if (suffix.equals("m")) value *= 1_000_000;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Czy pozycja z listy snajpowania pasuje do tej oferty. */
    public boolean matches(Config.Rule r) {
        if (!r.on) return false;
        boolean itemOk = r.item == null || r.item.isEmpty() || r.item.equals("*") || r.item.equals(itemId);
        boolean nameOk = r.name == null || r.name.isEmpty()
                || name.toLowerCase(Locale.ROOT).contains(r.name.toLowerCase(Locale.ROOT));
        return itemOk && nameOk;
    }
}
