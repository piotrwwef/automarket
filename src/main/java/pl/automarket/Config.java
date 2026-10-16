package pl.automarket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("automarket.json");

    /** klucz (id przedmiotu, fragment nazwy albo "all") -> maksymalna cena ZA SZTUKE */
    public Map<String, Double> rules = new LinkedHashMap<>();
    /** komenda otwierajaca rynek (bez ukosnika) */
    public String command = "ah";
    /** opoznienie miedzy kliknieciami w tickach (20 ticków = 1 s) */
    public int clickDelay = 6;
    /** przerwa przed ponownym otwarciem rynku, gdy nic nie znaleziono */
    public int refreshTicks = 40;
    /** numer slotu przycisku "nastepna strona" (-1 = wylaczone) */
    public int nextPageSlot = -1;
    /** ile stron przeszukac, gdy nextPageSlot jest ustawiony */
    public int maxPages = 1;
    /** slot przycisku "Sortowanie" (-1 = wylaczone) i nazwa trybu, ktory ma byc ustawiony */
    public int sortSlot = 53;
    public String sortName = "Najtańsze";
    /** klawisz dodawania/usuwania przedmiotu z listy po najechaniu kursorem (kod GLFW, 65 = A) */
    public int pickKey = 65;
    /** maksymalna laczna kwota do wydania w sesji (0 = bez limitu) */
    public double budget = 0;

    public static Config load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) {
                        if (c.rules == null) c.rules = new LinkedHashMap<>();
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
}
