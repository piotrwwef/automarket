package pl.automarket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("automarket.json");

    /** Jedna pozycja na liscie snajpowania. */
    public static class Rule {
        /** id przedmiotu bez "minecraft:" (np. ender_pearl) albo "*" = dowolny */
        public String item = "*";
        /** opcjonalny fragment nazwy (dla przedmiotow z wlasna nazwa, np. mikstur); puste = bez filtra */
        public String name = "";
        /** maksymalna cena ZA SZTUKE; 0 = nie kupuje */
        public double max = 0;
        public boolean on = true;

        public Rule() {
        }

        public Rule(String item, String name, double max) {
            this.item = item;
            this.name = name;
            this.max = max;
        }

        public boolean same(String item, String name) {
            return this.item.equals(item) && this.name.equalsIgnoreCase(name);
        }

        public ItemStack stack() {
            if (item == null || item.isEmpty() || item.equals("*")) return new ItemStack(Items.NETHER_STAR);
            Item it = Registries.ITEM.get(Identifier.of("minecraft", item));
            return new ItemStack(it);
        }

        public String title() {
            if (name != null && !name.isEmpty()) return name;
            if (item == null || item.isEmpty() || item.equals("*")) return "(dowolny przedmiot)";
            return Registries.ITEM.get(Identifier.of("minecraft", item)).getName().getString();
        }
    }

    public List<Rule> snipes = new ArrayList<>();
    /** komenda otwierajaca rynek (bez ukosnika) */
    public String command = "ah";
    /** opoznienie miedzy kliknieciami w tickach (20 tickow = 1 s) */
    public int clickDelay = 6;
    /** przerwa przed ponownym otwarciem rynku, gdy nic nie znaleziono */
    public int refreshTicks = 10;
    /** numer slotu przycisku "nastepna strona" (-1 = wylaczone) */
    public int nextPageSlot = -1;
    public int maxPages = 1;
    /** slot przycisku "Sortowanie" (-1 = mod nie rusza sortowania) i tryb, ktory ma byc ustawiony */
    public int sortSlot = 53;
    public String sortMode = "Najnowsze";
    /** klawisz dodawania/usuwania przedmiotu po najechaniu na oferte (kod GLFW, 65 = A) */
    public int pickKey = 65;
    /** klawisz otwierajacy GUI moda (kod GLFW, 345 = prawy Ctrl) */
    public int guiKey = 345;
    /** maksymalna laczna kwota do wydania w sesji (0 = bez limitu) */
    public double budget = 0;

    public static Config load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) {
                        if (c.snipes == null) c.snipes = new ArrayList<>();
                        return c;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Config c = new Config();
        c.save();
        return c;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Rule find(String item, String name) {
        for (Rule r : snipes) {
            if (r.same(item, name)) return r;
        }
        return null;
    }
}
