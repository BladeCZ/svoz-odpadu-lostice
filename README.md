# Svoz odpadu Loštice – Kalendář, Upozornění a Android Aplikace 🗑️📅

Kompletní řešení pro automatické stahování kalendáře svozu odpadu a komunálních služeb z webu **[Města Loštice](https://www.mu-lostice.cz/obcan/komunalni-sluzby/)**, včetně **nativní Android aplikace** s chytrými notifikacemi a filtrováním komodit i lokalit přesně podle rozbalovacího seznamu „Filtr“ na webu města.

---

## 🚀 Co projekt obsahuje

1. **Automatický Python Scraper ([`scraper/scraper.py`](scraper/scraper.py))**:
   - Prochází kalendář komunálních služeb na webu města Loštice pro aktuální i následující rok.
   - Automaticky načítá všechny položky z rozbalovacího seznamu pod tlačítkem **„Filtr“**:
     - **Typ události (12 komodit)**:
       - *Svoz směsného komunálního odpadu – 14 denní interval svozu SKO* (`#00aeef`)
       - *Svoz směsného komunálního odpadu – 28 denní interval svozu SKO* (`#fdc68c`)
       - *Pytlový sběr plastů* (`#ffff00`)
       - *Svoz bioodpadu interval 14 dnů* (`#ff0000`)
       - *Svoz bioodpadu interval 28 dnů* (`#37b44a`)
       - *Svoz bioodpadu* (`#8b5e3c`)
       - *Sběr potravinářských olejů* (`#ed008c`)
       - *Gastro odpad* (`#7c4900`)
       - *Velkoobjemové kontejnery na listí* (`#6aa84f`)
       - *Likvidace větví z jarního ořezu stromů* (`#38761d`)
       - *Provozní doba sběrného dvora* a *Provozní doba sběrného dvora víkend*
     - **Místo / Lokalita (7 míst)**:
       - *Loštice*, *Žádlovice a Vlčice*, *Sídliště*, *ulice Sokolská*, *ulice Moravičanská*, *ulice Olomoucká*, *ulice Ke Koupališti*
   - Exportuje data do **3 formátů** ve složce `data/`:
     - `data/calendar.json` – kompletní databáze pro Android aplikaci a web
     - `data/calendar.csv` – tabulka pro Excel
     - `data/calendar.ics` – iCalendar soubor pro import do Google Kalendáře / Apple Kalendáře

2. **Nativní Android aplikace ([`android/`](android/))**:
   - Napsaná v **Kotlinu** s **Jetpack Compose (Material 3)**.
   - **3 hlavní záložky**:
     - **Budoucí svozy**: Zvýrazněná karta *„Nejbližší svoz“* (Dnes / Zítra / Za X dní), rychlé štítky komodit a kompletní chronologický seznam nadcházejících svozů.
     - **Kalendář**: Měsíční kalendář (Po–Ne) s barevnými tečkami podle druhu odpadu a detailem vybraného dne.
     - **Upozornění**:
       - Individuální zaškrtávání typů odpadu z rozbalovacího seznamu „Filtr“ (např. pouze *Komunální odpad 14 dní*, *Plasty*, *Bioodpad* a *Oleje*).
       - Filtrování upozornění podle místa (*Loštice*, *Žádlovice a Vlčice*, *Sídliště*...).
       - Volba času upozornění: **Den předem večer** (např. v 18:00) a/nebo **V den svozu ráno** (např. v 06:00).
       - Tlačítko **„Vyzkoušet upozornění“** pro okamžitý test notifikace.
   - **Plná offline podpora**: Aplikace má výchozí kalendář přibalený přímo v `.apk` (`assets/calendar.json`) a na pozadí jej automaticky aktualizuje z GitHub Pages.

3. **Bezplatný provoz přes GitHub Actions (`.github/workflows/`)**:
   - [`scraper.yml`](.github/workflows/scraper.yml): Každý den automaticky zkontroluje web města Loštice, zaktualizuje `calendar.json` a publikuje webovou aplikaci na GitHub Pages.
   - [`build_apk.yml`](.github/workflows/build_apk.yml): Automaticky v cloudu GitHubu zkompiluje Android aplikaci `SvozOdpaduLostice.apk` a nahraje ji do **GitHub Releases** i do **Artifacts**.

---

## 🛠️ Jak nahrát na GitHub a získat `.apk` do mobilu

1. Na [github.com/new](https://github.com/new) vytvořte nový **Public** repozitář s názvem **`svoz-odpadu-lostice`**.
2. Ve složce `svoz-odpadu-lostice` spusťte:
   ```powershell
   git init
   git add .
   git commit -m "Inicializace projektu Svoz odpadu Loštice"
   git branch -M main
   git remote add origin https://github.com/BladeCZ/svoz-odpadu-lostice.git
   git push -u origin main
   ```
3. V nastavení repozitáře na GitHubu (**Settings -> Pages**) nastavte **Source** na **GitHub Actions**.
4. V záložce **Actions** se automaticky spustí sestavení Android aplikace (**Sestavení Android aplikace (APK)**). Po dokončení si `SvozOdpaduLostice.apk` stáhnete přímo ze sekce **Releases** (nebo z **Actions -> Artifacts**) do telefonu.
