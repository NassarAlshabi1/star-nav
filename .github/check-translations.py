#!/usr/bin/env python3
"""CI check: the app's translations must stay in step with values/strings.xml.

Checks, for every locale under app/src/main/res/values-*:
  1. Coverage: every translatable string and plurals name exists in the locale (and no extras).
  2. Placeholders: format arguments (%1$s, %2$d, %s, %d) match the English text one for one.
  3. Plural forms: Arabic needs all six forms (zero, one, two, few, many, other), every locale
     needs "other" at least.
  4. Not untranslated: a translated string that is identical to English (and has Latin letters)
     is almost certainly a forgotten copy.

Usage: .github/check-translations.py   (exit code 0 = all locales in step)

Originally added with the Arabic translation (0.6.1) so the 1300+ strings stay aligned across
English, Ukrainian and Arabic as they evolve.
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
DEFAULT = RES / "values/strings.xml"

# Locale -> plural quantities that must be present (CLDR cardinal rules).
REQUIRED_QUANTITIES = {"ar": {"zero", "one", "two", "few", "many", "other"}}
DEFAULT_QUANTITIES = {"other"}

# Locale -> plural quantities where the number is spelled out ("no records", "one record"),
# so the translated form may drop some of English's format arguments: a subset is fine there.
# Every other quantity, and every quantity of an unlisted locale, must keep the arguments exactly.
VERBALIZED_NUMBERS = {"ar": {"zero", "one", "two"}}

# Strings that stay the same in every language on purpose: brand names, and the word the
# Bypass confirmation asks the user to type (the hint around it is translated).
SAME_IN_EVERY_LANGUAGE = {
    "live_short_dish_starlink",  # "Starlink"
    "live_short_gms_fused",  # "Google"
    "mock_source_gms_fused",  # "Google fused"
    "confirm_word_bypass",  # "BYPASS" (typed confirmation word)
    "tab_starlink",  # "Starlink"
    "pos_mark_starlink",  # "Starlink"
    "pos_starlink_title",  # "Starlink"
}

# Format arguments we track: positional (%1$s) and sequential (%s, %d), %% excluded.
FMT = re.compile(r"%(?:\d+\$)?[sdf]|%d|%s")
# A string with Latin letters of this length is a sentence, not a brand name.
LATIN_SENTENCE = re.compile(r"[A-Za-z]{5,}")


def parse(path: Path):
    """name -> (text or {quantity: text}, translatable flag)."""
    root = ET.parse(path).getroot()
    strings, plurals = {}, {}
    for el in root:
        if el.tag == "string":
            strings[el.get("name")] = ((el.text or "").strip(), el.get("translatable", "true") == "true")
        elif el.tag == "plurals":
            plurals[el.get("name")] = {i.get("quantity"): (i.text or "").strip() for i in el}
    return strings, plurals


def main() -> int:
    en_strings, en_plurals = parse(DEFAULT)
    translatable = {k for k, (_, t) in en_strings.items() if t}
    findings = 0

    locale_dirs = []
    for p in sorted(RES.iterdir()):
        if p.is_dir() and p.name.startswith("values-") and (p / "strings.xml").is_file():
            locale_dirs.append(p)
    if not locale_dirs:
        print("no locale folders found under app/src/main/res/values-*")
        return 1

    checked = 0
    for d in locale_dirs:
        locale = d.name.removeprefix("values-")
        f = d / "strings.xml"
        if not f.is_file():
            continue  # other resource kinds (values-night etc.) never carry strings
        strings, plurals = parse(f)
        tag = f"[{locale}]"
        checked += 1

        # 1. Coverage, both directions.
        missing = sorted(translatable - strings.keys())
        extra = sorted(strings.keys() - en_strings.keys())
        for name in missing:
            print(f"{tag} missing string: {name}")
        for name in extra:
            print(f"{tag} unknown string (not in values/strings.xml): {name}")
        missing_p = sorted(en_plurals.keys() - plurals.keys())
        for name in missing_p:
            print(f"{tag} missing plurals: {name}")

        # 2. Placeholders match the English text exactly.
        for name, (en_text, _) in en_strings.items():
            if name not in strings:
                continue  # already reported above
            if sorted(FMT.findall(en_text)) != sorted(FMT.findall(strings[name][0])):
                print(f"{tag} placeholders differ in {name}: en={en_text!r} {locale}={strings[name][0]!r}")
                findings += 1

        # 3. Plural forms.
        need = REQUIRED_QUANTITIES.get(locale, DEFAULT_QUANTITIES)
        for name, items in plurals.items():
            for q in sorted(need - items.keys()):
                print(f"{tag} plurals {name} lacks the '{q}' form")
                findings += 1

        # 3b. Placeholders inside plural forms, against the matching English quantity
        # (or its 'other' when English has no such form). Quantities where the number is
        # spelled out may drop arguments; the rest must match exactly.
        for name, items in plurals.items():
            en_items = en_plurals.get(name, {})
            for q, text in items.items():
                ref = en_items.get(q) or en_items.get("other")
                if not ref:
                    continue
                en_f, loc_f = Counter(FMT.findall(ref)), Counter(FMT.findall(text))
                bad = (
                    loc_f - en_f  # invented or mistyped arguments: always wrong
                    if q in VERBALIZED_NUMBERS.get(locale, set())
                    else en_f != loc_f  # numeric quantity: keep every argument
                )
                if bad:
                    print(f"{tag} placeholders differ in plurals {name} ({q}): en={ref!r} {locale}={text!r}")
                    findings += 1

        # 4. Copies that were never translated.
        for name, (text, _) in strings.items():
            en = en_strings.get(name)
            if (
                en
                and name not in SAME_IN_EVERY_LANGUAGE
                and text == en[0].strip()
                and LATIN_SENTENCE.search(text)
            ):
                print(f"{tag} looks untranslated (same as English): {name}")
                findings += 1

        findings += len(missing) + len(extra) + len(missing_p)

    if findings:
        print(f"translations check: FAILED ({findings} findings)")
        return 1
    print(f"translations check: OK ({checked} locales, {len(translatable)} strings, {len(en_plurals)} plurals)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
