package pl.automarket;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Katalog wszystkich przedmiotow: wpisz cene max, kliknij przedmiot i trafia na liste snajpowania. */
public class PickerScreen extends Screen {
    private static final int COLS = 9;
    private static final int GROWS = 5;
    private static final int CELL = 20;
    private static final int PER_PAGE = COLS * GROWS;

    private static List<Item> all;
    private static List<String> keys;

    private final Config cfg;
    private final Bot bot;
    private final Screen parent;

    private final ButtonWidget[] cells = new ButtonWidget[PER_PAGE];
    private final Item[] shown = new Item[PER_PAGE];
    private final List<Item> filtered = new ArrayList<>();

    private TextFieldWidget search;
    private TextFieldWidget priceField;
    private ButtonWidget prevBtn;
    private ButtonWidget nextBtn;

    private String searchText = "";
    private String priceText = "";
    private String note = "";
    private int page = 0;
    private int gl;
    private int top;
    private int gy;

    public PickerScreen(Config cfg, Bot bot, Screen parent) {
        super(Text.literal("Katalog przedmiotow"));
        this.cfg = cfg;
        this.bot = bot;
        this.parent = parent;
        ensureCatalog();
    }

    private static void ensureCatalog() {
        if (all != null) return;
        all = new ArrayList<>();
        keys = new ArrayList<>();
        for (Item it : Registries.ITEM) {
            if (it == Items.AIR) continue;
            all.add(it);
            String id = Registries.ITEM.getId(it).getPath();
            keys.add((id + " " + id.replace('_', ' ') + " " + it.getName().getString()).toLowerCase(Locale.ROOT));
        }
    }

    @Override
    protected void init() {
        gl = width / 2 - COLS * CELL / 2;
        top = Math.max(4, (height - 230) / 2);
        gy = top + 42;

        search = new TextFieldWidget(textRenderer, gl, top + 18, 110, 18, Text.literal("szukaj"));
        search.setMaxLength(40);
        search.setPlaceholder(Text.literal("Szukaj przedmiotu..."));
        search.setText(searchText);
        search.setChangedListener(s -> {
            searchText = s;
            page = 0;
            applyFilter();
        });
        addDrawableChild(search);

        priceField = new TextFieldWidget(textRenderer, gl + 114, top + 18, 66, 18, Text.literal("cena"));
        priceField.setMaxLength(14);
        priceField.setPlaceholder(Text.literal("Cena/szt."));
        priceField.setText(priceText);
        priceField.setChangedListener(s -> priceText = s);
        addDrawableChild(priceField);

        for (int k = 0; k < PER_PAGE; k++) {
            final int slot = k;
            int c = k % COLS;
            int r = k / COLS;
            cells[k] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> pick(slot))
                    .dimensions(gl + c * CELL, gy + r * CELL, CELL, CELL).build());
        }

        int by = gy + GROWS * CELL + 4;
        prevBtn = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            page--;
            applyFilter();
        }).dimensions(gl, by, 20, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> client.setScreen(parent))
                .dimensions(gl + 24, by, 132, 18).build());
        nextBtn = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            page++;
            applyFilter();
        }).dimensions(gl + 160, by, 20, 18).build());

        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        for (int i = 0; i < all.size(); i++) {
            if (q.isEmpty() || keys.get(i).contains(q)) filtered.add(all.get(i));
        }
        int pages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        for (int k = 0; k < PER_PAGE; k++) {
            int idx = page * PER_PAGE + k;
            shown[k] = idx < filtered.size() ? filtered.get(idx) : null;
            cells[k].visible = shown[k] != null;
            cells[k].active = shown[k] != null;
        }
        prevBtn.active = page > 0;
        nextBtn.active = page < pages - 1;
    }

    private void pick(int slot) {
        Item it = shown[slot];
        if (it == null) return;
        Double p = Listing.parseMoney(priceText);
        if (priceText.isBlank() || p == null || p <= 0) {
            note = "Najpierw wpisz cene max za sztuke (np. 5000 albo 5k) w polu po prawej.";
            return;
        }
        String id = Registries.ITEM.getId(it).getPath();
        Config.Rule existing = cfg.find(id, "");
        if (existing != null) {
            existing.max = p;
            existing.on = true;
        } else {
            cfg.snipes.add(new Config.Rule(id, "", p));
        }
        cfg.save();
        note = "Dodano: " + it.getName().getString() + " <= $" + Bot.fmt(p) + " za sztuke";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Katalog - wpisz cene, potem kliknij przedmiot"), width / 2, top + 4, 0xFFFFFFFF);

        String hover = "";
        for (int k = 0; k < PER_PAGE; k++) {
            if (shown[k] == null) continue;
            ButtonWidget b = cells[k];
            ctx.drawItem(new ItemStack(shown[k]), b.getX() + 2, b.getY() + 2);
            String id = Registries.ITEM.getId(shown[k]).getPath();
            if (cfg.find(id, "") != null) {
                ctx.fill(b.getX() + 1, b.getY() + CELL - 3, b.getX() + CELL - 1, b.getY() + CELL - 1, 0xFF55FF55);
            }
            if (mouseX >= b.getX() && mouseX < b.getX() + CELL && mouseY >= b.getY() && mouseY < b.getY() + CELL) {
                hover = shown[k].getName().getString();
            }
        }

        int by = gy + GROWS * CELL + 4;
        int pages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Strona " + (page + 1) + "/" + pages
                + "  (" + filtered.size() + " przedmiotow)"), width / 2, by + 24, 0xFFCCCCCC);
        if (!hover.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hover), width / 2, by + 36, 0xFFFFFFFF);
        }
        if (!note.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(note), width / 2, by + 48, 0xFFFFFF55);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
