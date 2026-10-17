package pl.automarket;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Glowne okno moda: lista przedmiotow do snajpienia z cenami, start/stop i ustawienia. */
public class MainScreen extends Screen {
    private static final int ROWS = 6;
    private static final int PANEL_H = 256;
    private static final String[] SORTS = {"Najnowsze", "Najstarsze", "Najtańsze", "Najdroższe"};
    private static final int[] REFRESH = {4, 6, 10, 20, 30, 40, 60, 100, 200};
    private static final int[] DELAY = {2, 3, 4, 6, 8, 10, 15};

    private final Config cfg;
    private final Bot bot;

    private int left;
    private int top;
    private int ry;
    private int page = 0;
    private boolean refreshing = false;
    private String note = "";

    private final TextFieldWidget[] price = new TextFieldWidget[ROWS];
    private final ButtonWidget[] toggle = new ButtonWidget[ROWS];
    private final ButtonWidget[] remove = new ButtonWidget[ROWS];
    private ButtonWidget startBtn;
    private ButtonWidget prevBtn;
    private ButtonWidget nextBtn;
    private ButtonWidget refreshBtn;
    private ButtonWidget delayBtn;
    private ButtonWidget sortBtn;
    private TextFieldWidget budgetField;

    public MainScreen(Config cfg, Bot bot) {
        super(Text.literal("AutoRynek"));
        this.cfg = cfg;
        this.bot = bot;
    }

    @Override
    protected void init() {
        left = width / 2 - 150;
        top = Math.max(4, (height - PANEL_H) / 2);
        ry = top + 42;

        startBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Start"), b -> toggleBot())
                .dimensions(left, top + 18, 94, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Katalog"),
                        b -> client.setScreen(new PickerScreen(cfg, bot, this)))
                .dimensions(left + 98, top + 18, 94, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Trzymany"), b -> addHeld())
                .dimensions(left + 196, top + 18, 104, 18).build());

        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            int y = ry + i * 20;
            price[i] = new TextFieldWidget(textRenderer, left + 190, y, 60, 18, Text.literal("cena"));
            price[i].setMaxLength(14);
            price[i].setChangedListener(s -> onPriceEdited(row, s));
            addDrawableChild(price[i]);
            toggle[i] = addDrawableChild(ButtonWidget.builder(Text.literal("ON"), b -> toggleRule(row))
                    .dimensions(left + 254, y, 24, 18).build());
            remove[i] = addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> removeRule(row))
                    .dimensions(left + 280, y, 20, 18).build());
        }

        int py = ry + ROWS * 20 + 2;
        prevBtn = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            page--;
            refresh();
        }).dimensions(left, py, 24, 18).build());
        nextBtn = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            page++;
            refresh();
        }).dimensions(left + 276, py, 24, 18).build());

        int sy = py + 22;
        refreshBtn = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            cfg.refreshTicks = next(REFRESH, cfg.refreshTicks);
            cfg.save();
            refresh();
        }).dimensions(left, sy, 148, 18).build());
        delayBtn = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            cfg.clickDelay = next(DELAY, cfg.clickDelay);
            cfg.save();
            refresh();
        }).dimensions(left + 152, sy, 148, 18).build());
        sortBtn = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            cfg.sortMode = nextSort(cfg.sortMode);
            cfg.save();
            refresh();
        }).dimensions(left, sy + 22, 148, 18).build());

        budgetField = new TextFieldWidget(textRenderer, left + 152, sy + 22, 148, 18, Text.literal("budzet"));
        budgetField.setMaxLength(14);
        budgetField.setPlaceholder(Text.literal("Budzet sesji (puste = bez limitu)"));
        budgetField.setText(cfg.budget > 0 ? fmtPrice(cfg.budget) : "");
        budgetField.setChangedListener(s -> {
            Double v = Listing.parseMoney(s);
            cfg.budget = (v == null || s.isBlank()) ? 0 : v;
            cfg.save();
        });
        addDrawableChild(budgetField);

        refresh();
    }

    // ---------------------------------------------------------------- logika

    private void refresh() {
        refreshing = true;
        List<Config.Rule> list = cfg.snipes;
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            boolean vis = idx < list.size();
            price[i].visible = vis;
            price[i].active = vis;
            toggle[i].visible = vis;
            toggle[i].active = vis;
            remove[i].visible = vis;
            remove[i].active = vis;
            if (vis) {
                Config.Rule r = list.get(idx);
                price[i].setText(r.max > 0 ? fmtPrice(r.max) : "");
                toggle[i].setMessage(Text.literal(r.on ? "ON" : "OFF"));
            }
        }
        prevBtn.active = page > 0;
        nextBtn.active = page < pages - 1;
        startBtn.setMessage(Text.literal(bot.isRunning() ? "Stop" : "Start"));
        refreshBtn.setMessage(Text.literal("Odswiezanie rynku: " + String.format("%.2f", cfg.refreshTicks / 20.0) + " s"));
        delayBtn.setMessage(Text.literal("Klikanie co: " + String.format("%.2f", cfg.clickDelay / 20.0) + " s"));
        sortBtn.setMessage(Text.literal("Sortowanie: " + cfg.sortMode));
        refreshing = false;
    }

    private void onPriceEdited(int row, String s) {
        if (refreshing) return;
        int idx = page * ROWS + row;
        if (idx >= cfg.snipes.size()) return;
        Double v = Listing.parseMoney(s);
        cfg.snipes.get(idx).max = (v == null || s.isBlank()) ? 0 : v;
        cfg.save();
    }

    private void toggleRule(int row) {
        int idx = page * ROWS + row;
        if (idx >= cfg.snipes.size()) return;
        Config.Rule r = cfg.snipes.get(idx);
        r.on = !r.on;
        cfg.save();
        refresh();
    }

    private void removeRule(int row) {
        int idx = page * ROWS + row;
        if (idx >= cfg.snipes.size()) return;
        cfg.snipes.remove(idx);
        cfg.save();
        refresh();
    }

    private void toggleBot() {
        if (bot.isRunning()) {
            bot.stop(client, "GUI");
            refresh();
        } else {
            close();
            bot.start(client);
        }
    }

    private void addHeld() {
        if (client == null || client.player == null) return;
        ItemStack st = client.player.getMainHandStack();
        if (st.isEmpty()) {
            note = "Trzymasz pusta reke. Wez przedmiot do reki.";
            return;
        }
        String id = Registries.ITEM.getId(st.getItem()).getPath();
        String nm = st.get(DataComponentTypes.CUSTOM_NAME) != null ? st.getName().getString() : "";
        if (cfg.find(id, nm) != null) {
            note = "Ten przedmiot jest juz na liscie.";
            return;
        }
        cfg.snipes.add(new Config.Rule(id, nm, 0));
        cfg.save();
        page = Math.max(0, (cfg.snipes.size() - 1) / ROWS);
        note = "Dodano: " + st.getName().getString() + ". Wpisz cene max za sztuke.";
        refresh();
    }

    private static int next(int[] arr, int cur) {
        for (int v : arr) {
            if (v > cur) return v;
        }
        return arr[0];
    }

    private static String nextSort(String cur) {
        for (int i = 0; i < SORTS.length; i++) {
            if (SORTS[i].equalsIgnoreCase(cur)) return SORTS[(i + 1) % SORTS.length];
        }
        return SORTS[0];
    }

    private static String fmtPrice(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ---------------------------------------------------------------- rysowanie

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("AutoRynek - lista snajpowania"),
                width / 2, top + 4, 0xFFFFFFFF);

        List<Config.Rule> list = cfg.snipes;
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) break;
            Config.Rule r = list.get(idx);
            int y = ry + i * 20;
            ctx.drawItem(r.stack(), left, y + 1);
            String t = textRenderer.trimToWidth(r.title(), 164);
            ctx.drawTextWithShadow(textRenderer, Text.literal(t), left + 20, y + 5,
                    r.on ? 0xFFFFFFFF : 0xFF777777);
        }
        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Lista jest pusta."), width / 2, ry + 20, 0xFFFFAA00);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Kliknij \"+ Katalog\" albo na rynku"),
                    width / 2, ry + 36, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("najedz na oferte i nacisnij A."),
                    width / 2, ry + 48, 0xFFAAAAAA);
        }

        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Strona " + (page + 1) + "/" + pages
                        + "   (cena max za sztuke)"),
                width / 2, ry + ROWS * 20 + 7, 0xFFCCCCCC);

        String stats = (bot.isRunning() ? "WLACZONY" : "wylaczony") + "   Kupiono: " + bot.bought
                + "   Wydano: $" + Bot.fmt(bot.spent);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(stats), width / 2, top + 236,
                bot.isRunning() ? 0xFF55FF55 : 0xFFAAAAAA);
        if (!note.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(note), width / 2, top + 247, 0xFFFFFF55);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        cfg.save();
        super.close();
    }
}
