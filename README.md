# AutoRynek (Fabric 1.21.11)

Klientowy mod, który otwiera `/ah`, szuka ofert tańszych niż ustawione limity i kupuje je
(klik w ofertę -> okno potwierdzenia -> klik w zielone pole).

## Budowanie
Wymagane: Java 21.

    ./gradlew build        (Windows: gradlew.bat build)

Gotowy plik: `build/libs/automarket-1.0.0.jar`. Wrzuć go do `.minecraft/mods/` razem z Fabric API.
Jesli Gradle zglosi brak wersji, zaktualizuj `gradle.properties` wg https://fabricmc.net/develop/

## GUI (prawy Ctrl albo /am gui)
Okno z lista przedmiotow do snajpowania: ikona, nazwa, pole z cena max za sztuke (np. 5000 / 5k / 1.5m),
przelacznik ON/OFF i X do usuniecia. Przyciski: Start/Stop, "+ Katalog" (wszystkie przedmioty, wyszukiwarka,
wpisz cene i kliknij przedmiot), "+ Trzymany" (przedmiot z reki), odswiezanie rynku, tempo klikania,
sortowanie (Najnowsze/Najstarsze/Najtansze/Najdrozsze) i budzet sesji. Na rynku: A = dodaj/usun oferte pod
kursorem, D = wypisz oferty, ktore mod widzi.

## Dodawanie przedmiotow z rynku
Otworz `/ah`, najedz kursorem na oferte i nacisnij **A** (bez klikania). Przedmiot trafia na liste kupowanych,
a limit ceny = jego aktualna cena za sztuke. Ponowne **A** na tym samym przedmiocie usuwa go z listy.
Limit mozesz potem zmienic: `/am add "nazwa" <cena>`. Inny klawisz: zmien `pickKey` w `config/automarket.json` (kod GLFW).

## Odswiezanie i sortowanie
Gdy na rynku nie ma nic do kupienia, mod zamyka go i po `refresh` tickach otwiera od nowa (`/am refresh 40` = co 2 s;
nie schodz za nisko, serwer moze miec cooldown na /ah). Po kazdym otwarciu mod prawym klikiem w slot 53 ("Sortowanie")
ustawia tryb "Najtansze". Zmiana: `/am sort <slot> "<tryb>"`, wylaczenie: `/am sort off`.

## Komendy (`/automarket` albo `/am`)
| Komenda | Opis |
|---|---|
| `/am add <przedmiot\|all> <cena>` | limit ceny ZA SZTUKE. Klucz = id (`ender_pearl`), fragment nazwy (`"strength"`) albo `all` |
| `/am remove <klucz>` / `/am list` | zarzadzanie regulami |
| `/am start` / `/am stop` / `/am status` | sterowanie |
| `/am budget <kwota>` | limit wydatkow na sesje (0 = bez limitu) |
| `/am delay <ticki>` | przerwa miedzy kliknieciami (domyslnie 6 = 0,3 s) |
| `/am refresh <ticki>` | przerwa przed ponownym otwarciem rynku (domyslnie 40 = 2 s) |
| `/am nextpage <slot> <ile stron>` / `nextpage off` | przechodzenie po stronach rynku |
| `/am scan` | po otwarciu `/ah` wypisuje, jakie oferty mod widzi (do sprawdzenia ustawien) |

Przyklad: `/am add ender_pearl 500`, `/am add "strength" 3000`, `/am budget 50000`, `/am start`.

Ustawienia zapisuja sie w `config/automarket.json`.

## Uwaga
Auto-kupowanie moze byc niezgodne z regulaminem CraftCube. Uzywasz na wlasne ryzyko.
