# AlexiaOS

Ukážka (koncept) súkromného mobilného systému – moderné rozhranie s „aurora“ dizajnom,
kde je šifrovanie všade. Beží ako launcher nad Androidom: po nastavení ako domovská obrazovka
vyzerá telefón ako AlexiaOS.

## Čo obsahuje
| Časť | Popis |
|---|---|
| Úvod a zámok | Animovaný boot, PIN (PBKDF2-SHA256, 210 000 iterácií, soľ), predlžovanie zámku po chybách |
| Domovská obrazovka | Hodiny, widgety, appky AlexiaOS, sekcia **Alexia Core** (automaticky nájde appky Alexia), dok |
| Centrum notifikácií | Potiahnutie zhora: prepínače + notifikácie celého telefónu v štýle AlexiaOS (otvoriť, zrušiť, skryť v režime súkromia) |
| Plocha | Pripnuté appky, priečinky, systémové widgety, jednotný štýl ikon, 2× ťuk = zamknúť, podržanie appky = možnosti |
| Súbory | Šifrované úložisko a galéria: každý súbor vlastný kľúč AES-256 zabalený kľúčom z čipu, AES-GCM po 1 MB blokoch |
| Všetky appky | Potiahnutie nahor: skutočné appky telefónu s vyhľadávaním |
| Trezor | Poznámky šifrované AES-256-GCM kľúčom z Android Keystore (StrongBox/TEE) + pohľad „ako to vidí zlodej“ |
| Správy | Ukážka end-to-end šifrovania: ECDH P-256 + AES-256-GCM, odtlačky kľúčov, čo ide sieťou |
| Bezpečnosť | Skóre ochrany a overenie vrstiev: šifrovanie úložiska, hardvérový kľúč, zámok |
| Nastavenia | Tapety (aj vlastná z galérie), zmena PIN, nastavenie ako domovskej obrazovky |

## Build
Každý push na `main` zostaví APK cez GitHub Actions a vydá ho v **Releases**.
Pre stály podpis pridaj secret `SIGNING_PASSWORD` (rovnaký ako v Alexia Shield).
