#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Scraper kalendáře svozu odpadu a komunálních služeb města Loštice
Zdroj: https://www.mu-lostice.cz/obcan/komunalni-sluzby/
"""

import csv
import datetime
import hashlib
import html as html_lib
import json
import os
import re
import urllib.request
from typing import Dict, List, Any

BASE_URL = "https://www.mu-lostice.cz/obcan/komunalni-sluzby/"
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(ROOT_DIR, "data")

# Výchozí přehled všech typů událostí z rozbalovacího seznamu "Filtr" na webu města Loštice
DEFAULT_WASTE_TYPES = [
    {
        "id": "7",
        "name": "Svoz směsného komunálního odpadu - 14 denní interval svozu SKO",
        "shortName": "Komunální odpad (14 dní)",
        "color": "#00aeef",
        "category": "sko_14",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "12",
        "name": "Svoz směsného komunálního odpadu - 28 denní interval svozu SKO",
        "shortName": "Komunální odpad (28 dní)",
        "color": "#fdc68c",
        "category": "sko_28",
        "defaultNotify": False,
        "isCollection": True,
    },
    {
        "id": "2",
        "name": "Pytlový sběr plastů",
        "shortName": "Plasty (pytlový sběr)",
        "color": "#ffff00",
        "category": "plast",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "10",
        "name": "Svoz bioodpadu interval 14 dnů",
        "shortName": "Bioodpad (14 dní)",
        "color": "#ff0000",
        "category": "bio_14",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "11",
        "name": "Svoz bioodpadu interval 28 dnů",
        "shortName": "Bioodpad (28 dní)",
        "color": "#37b44a",
        "category": "bio_28",
        "defaultNotify": False,
        "isCollection": True,
    },
    {
        "id": "8",
        "name": "Svoz bioodpadu",
        "shortName": "Bioodpad",
        "color": "#8b5e3c",
        "category": "bio",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "3",
        "name": "Sběr potravinářských olejů",
        "shortName": "Potravinářské oleje",
        "color": "#ed008c",
        "category": "oleje",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "13",
        "name": "Gastro odpad",
        "shortName": "Gastro odpad",
        "color": "#7c4900",
        "category": "gastro",
        "defaultNotify": False,
        "isCollection": True,
    },
    {
        "id": "9",
        "name": "Velkoobjemové kontejnery na listí",
        "shortName": "Kontejnery na listí",
        "color": "#6aa84f",
        "category": "listi",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "14",
        "name": "Likvidace větví z jarního ořezu stromů",
        "shortName": "Likvidace větví",
        "color": "#38761d",
        "category": "vetve",
        "defaultNotify": True,
        "isCollection": True,
    },
    {
        "id": "4",
        "name": "Provozní doba sběrného dvora",
        "shortName": "Sběrný dvůr (Po, St)",
        "color": "#333333",
        "category": "sberny_dvur",
        "defaultNotify": False,
        "isCollection": False,
    },
    {
        "id": "6",
        "name": "Provozní doba sběrného dvora víkend",
        "shortName": "Sběrný dvůr (Víkend)",
        "color": "#555555",
        "category": "sberny_dvur_vikend",
        "defaultNotify": False,
        "isCollection": False,
    },
]

DEFAULT_PLACES = [
    {"id": "2", "name": "Loštice"},
    {"id": "5", "name": "Žádlovice a Vlčice"},
    {"id": "6", "name": "Sídliště"},
    {"id": "8", "name": "ulice Sokolská"},
    {"id": "3", "name": "ulice Moravičanská"},
    {"id": "4", "name": "ulice Olomoucká"},
    {"id": "7", "name": "ulice Ke Koupališti"},
]


def fetch_html(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SvozOdpaduLostice/1.0"
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8", errors="replace")


def normalize_color(raw_color: str) -> str:
    c = raw_color.strip().lower()
    if re.match(r"^#[0-9a-f]{3}$", c):
        return "#" + "".join(ch * 2 for ch in c[1:])
    if re.match(r"^#[0-9a-f]{6}$", c):
        return c
    return "#00aeef"


def clean_text(raw: str) -> str:
    txt = re.sub(r"<[^>]+>", " ", raw)
    txt = html_lib.unescape(txt)
    txt = re.sub(r"[ \t\r\f\v]+", " ", txt)
    lines = [line.strip() for line in txt.split("\n") if line.strip()]
    return "\n".join(lines).strip()


def extract_filters_from_html(page_html: str):
    waste_types_by_name = {w["name"]: dict(w) for w in DEFAULT_WASTE_TYPES}
    waste_types_by_id = {w["id"]: dict(w) for w in DEFAULT_WASTE_TYPES}

    select_waste = re.search(
        r'<select[^>]*name="communal_waste"[^>]*>(.*?)</select>',
        page_html,
        re.DOTALL | re.IGNORECASE,
    )
    if select_waste:
        for opt_match in re.finditer(
            r'<option[^>]*value="(\d+)"[^>]*>(.*?)</option>',
            select_waste.group(1),
            re.DOTALL | re.IGNORECASE,
        ):
            opt_id = opt_match.group(1).strip()
            opt_name = clean_text(opt_match.group(2))
            if opt_id == "0" or not opt_name:
                continue
            if opt_id not in waste_types_by_id and opt_name not in waste_types_by_name:
                new_entry = {
                    "id": opt_id,
                    "name": opt_name,
                    "shortName": opt_name,
                    "color": "#00aeef",
                    "category": f"type_{opt_id}",
                    "defaultNotify": "sběrného dvora" not in opt_name.lower(),
                    "isCollection": "sběrného dvora" not in opt_name.lower(),
                }
                waste_types_by_id[opt_id] = new_entry
                waste_types_by_name[opt_name] = new_entry

    places_by_id = {p["id"]: dict(p) for p in DEFAULT_PLACES}
    select_place = re.search(
        r'<select[^>]*name="communal_place"[^>]*>(.*?)</select>',
        page_html,
        re.DOTALL | re.IGNORECASE,
    )
    if select_place:
        for opt_match in re.finditer(
            r'<option[^>]*value="(\d+)"[^>]*>(.*?)</option>',
            select_place.group(1),
            re.DOTALL | re.IGNORECASE,
        ):
            opt_id = opt_match.group(1).strip()
            opt_name = clean_text(opt_match.group(2))
            if opt_id == "0" or not opt_name:
                continue
            if opt_id not in places_by_id:
                places_by_id[opt_id] = {"id": opt_id, "name": opt_name}

    return list(waste_types_by_name.values()), list(places_by_id.values())


def parse_month_events(page_html: str, waste_lookup: Dict[str, Dict[str, Any]]) -> List[Dict[str, Any]]:
    events: List[Dict[str, Any]] = []

    # Najít všechny buňky s událostmi (obsahující data-tip="tooltipYYYYMMDD")
    for match in re.finditer(
        r'data-tip="tooltip(\d{8})"[^>]*>\s*<div class="gcm-calendar-event">(.*?)<div class="sr-only"\s+id="tooltip\1">(.*?)</div>\s*</div>\s*</a>',
        page_html,
        re.DOTALL | re.IGNORECASE,
    ):
        raw_date = match.group(1)
        event_block = match.group(2)
        tooltip_block = match.group(3)

        date_iso = f"{raw_date[0:4]}-{raw_date[4:6]}-{raw_date[6:8]}"

        # Získat páry gcm-calendar-desc a gcm-calendar-venue
        item_pattern = re.compile(
            r'<div class="gcm-calendar-desc">(.*?)</div>\s*<div class="gcm-calendar-venue">(.*?)</div>',
            re.DOTALL | re.IGNORECASE,
        )

        # Získat případné poznámky z tooltipu (např. <p><b>...</b></p>)
        tooltip_notes: Dict[str, str] = {}
        for tip_item in re.finditer(
            r"<p><b>(.*?)</b></p>.*?<b>(.*?)</b>",
            tooltip_block,
            re.DOTALL | re.IGNORECASE,
        ):
            note_txt = clean_text(tip_item.group(1))
            w_name = clean_text(tip_item.group(2))
            if w_name and note_txt:
                tooltip_notes[w_name] = note_txt

        for item_match in item_pattern.finditer(event_block):
            desc_html = item_match.group(1)
            venue_html = item_match.group(2)

            color_match = re.search(r"background-color:\s*([^;\"']+)", desc_html, re.IGNORECASE)
            raw_color = normalize_color(color_match.group(1)) if color_match else "#00aeef"

            name_match = re.search(
                r'<span class="gcm-communal-name">(.*?)</span>',
                desc_html,
                re.DOTALL | re.IGNORECASE,
            )
            waste_name = clean_text(name_match.group(1)) if name_match else "Komunální událost"

            desc_match = re.search(
                r'<span class="gcm-communal-waste-desc">(.*?)</span>',
                desc_html,
                re.DOTALL | re.IGNORECASE,
            )
            waste_desc = clean_text(desc_match.group(1)) if desc_match else waste_name

            venues = [
                clean_text(v)
                for v in re.findall(
                    r'<span class="gcm-calendar-venue-fullname">(.*?)</span>',
                    venue_html,
                    re.DOTALL | re.IGNORECASE,
                )
                if clean_text(v)
            ]

            w_meta = waste_lookup.get(waste_name)
            if w_meta:
                waste_id = w_meta["id"]
                short_name = w_meta["shortName"]
                is_collection = w_meta["isCollection"]
                # Pokud je barva černá pro sběrný dvůr, použijeme čitelnou barvu z meta
                color = w_meta["color"] if raw_color in ("#000000", "#000") else raw_color
                w_meta["color"] = color
            else:
                waste_id = "0"
                short_name = waste_name
                is_collection = "sběrného dvora" not in waste_name.lower()
                color = raw_color

            note = tooltip_notes.get(waste_name, "")

            # Unikátní klíč pro deduplikaci (datum + název typu odpadu)
            dedup_key = f"{date_iso}|{waste_name}"
            event_id = hashlib.md5(dedup_key.encode("utf-8")).hexdigest()[:12]

            events.append(
                {
                    "id": event_id,
                    "date": date_iso,
                    "wasteTypeId": waste_id,
                    "wasteTypeName": waste_name,
                    "shortName": short_name,
                    "description": waste_desc,
                    "note": note,
                    "places": venues if venues else ["Loštice"],
                    "color": color,
                    "isCollection": is_collection,
                }
            )

    return events


def export_ics(events: List[Dict[str, Any]], filepath: str):
    lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Svoz odpadu Loštice//CZ",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:Svoz odpadu Loštice",
        "X-WR-TIMEZONE:Europe/Prague",
    ]
    now_stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    for ev in events:
        if not ev.get("isCollection", True):
            continue
        dt_str = ev["date"].replace("-", "")
        dt_date = datetime.date.fromisoformat(ev["date"])
        next_day_str = (dt_date + datetime.timedelta(days=1)).strftime("%Y%m%d")
        places_str = ", ".join(ev.get("places", []))
        desc = ev.get("description", "").replace("\n", "\\n")
        if ev.get("note"):
            desc += "\\n" + ev["note"].replace("\n", "\\n")
        lines.extend(
            [
                "BEGIN:VEVENT",
                f"UID:{ev['id']}@svoz-lostice.cz",
                f"DTSTAMP:{now_stamp}",
                f"DTSTART;VALUE=DATE:{dt_str}",
                f"DTEND;VALUE=DATE:{next_day_str}",
                f"SUMMARY:{ev['shortName']} ({places_str})",
                f"DESCRIPTION:{desc}",
                f"LOCATION:{places_str}",
                "END:VEVENT",
            ]
        )
    lines.append("END:VCALENDAR")
    with open(filepath, "w", encoding="utf-8", newline="\r\n") as f:
        f.write("\r\n".join(lines) + "\r\n")


def main():
    os.makedirs(DATA_DIR, exist_ok=True)
    today = datetime.date.today()
    years_to_scrape = sorted({today.year, today.year + 1})

    print(f"[*] Stahuji hlavní stránku {BASE_URL} ...")
    initial_html = fetch_html(BASE_URL)
    waste_types, places = extract_filters_from_html(initial_html)
    waste_lookup = {w["name"]: w for w in waste_types}

    all_events_by_key: Dict[str, Dict[str, Any]] = {}

    for year in years_to_scrape:
        for month in range(1, 13):
            url = f"{BASE_URL}?communal_month={month}&communal_year={year}"
            print(f"    -> Stahuji {month:02d}/{year} ...")
            try:
                page_html = fetch_html(url)
                month_events = parse_month_events(page_html, waste_lookup)
                for ev in month_events:
                    key = f"{ev['date']}|{ev['wasteTypeName']}"
                    if key not in all_events_by_key:
                        all_events_by_key[key] = ev
                    else:
                        # Sloučit případná další místa
                        existing_places = all_events_by_key[key]["places"]
                        for p in ev["places"]:
                            if p not in existing_places:
                                existing_places.append(p)
            except Exception as e:
                print(f"    [!] Chyba při stahování {month:02d}/{year}: {e}")

    sorted_events = sorted(
        all_events_by_key.values(),
        key=lambda x: (x["date"], not x["isCollection"], x["shortName"]),
    )

    payload = {
        "municipality": "Město Loštice",
        "sourceUrl": BASE_URL,
        "updatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "wasteTypes": waste_types,
        "places": places,
        "events": sorted_events,
    }

    json_path = os.path.join(DATA_DIR, "calendar.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    csv_path = os.path.join(DATA_DIR, "calendar.csv")
    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f, delimiter=";")
        writer.writerow(["Datum", "Zkratka", "Typ události", "Místo", "Popis", "Svoz odpadu"])
        for ev in sorted_events:
            writer.writerow(
                [
                    ev["date"],
                    ev["shortName"],
                    ev["wasteTypeName"],
                    ", ".join(ev["places"]),
                    ev["description"].replace("\n", " "),
                    "ANO" if ev["isCollection"] else "NE",
                ]
            )

    ics_path = os.path.join(DATA_DIR, "calendar.ics")
    export_ics(sorted_events, ics_path)

    print(f"[+] Hotovo! Uloženo {len(sorted_events)} událostí do {json_path}")


if __name__ == "__main__":
    main()
