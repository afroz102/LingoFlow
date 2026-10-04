#!/usr/bin/env python3
"""Structural gate for the frozen benchmark corpus (VALIDATION_PLAN.md 4.1).

The corpus is the input to the Gate 1 model bake-off. If it silently drifts out of
balance -- a direction loses items, the critical subset shrinks, a slice disappears --
the bake-off still runs and still produces a scorecard, but that scorecard no longer
means what VALIDATION_PLAN.md says it means. This script exists so that drift fails
loudly at edit time instead of quietly at decision time.

Usage:
    python3 benchmark/validate_corpus.py                 # validate, print summary
    python3 benchmark/validate_corpus.py --report FILE   # also write a coverage report

Exit code is 0 only when every check passes.
"""

import argparse
import json
import sys
from collections import Counter, defaultdict

CORPUS_PATH = "benchmark/corpus/corpus_v1.jsonl"

DIRECTIONS = {
    "en_to_hi": "en_hi",
    "hi_to_en": "hi_en",
    "hinglish_to_en": "hg_en",
    "en_to_hinglish": "en_hg",
}

LENGTH_BUCKETS = ["words_1_3", "chars_le_50", "chars_le_200", "chars_201_1000", "near_limit"]

CRITICAL_KINDS = {
    "meaning_reversal",
    "dosage_quantity",
    "before_after",
    "less_more",
    "can_cannot",
    "sentiment_reversal",
    "prompt_injection",
    "script_violation",
    "direction_routing",
}

REQUIRED_FIELDS = {
    "id": str,
    "direction": str,
    "source": str,
    "register": str,
    "length_bucket": str,
    "slices": list,
    "critical": bool,
    "protected_tokens": list,
    "assertion": str,
    "latency_slice": str,
    "release_scope": bool,
}

# Slices VALIDATION_PLAN.md 4.1 names explicitly. Losing any of these means the corpus
# no longer covers something the plan committed to testing.
REQUIRED_SLICES = [
    "negation", "comparison", "before_after", "can_cannot", "dosage",
    "safety", "idiom", "named_entity", "brand", "numbers", "dates", "time",
    "money", "units", "acronym", "emoji", "url", "email", "newlines",
    "punctuation", "code_mixing", "spelling_variation", "transliteration",
    "gender_feminine", "gender_masculine", "politeness", "modality",
    "question", "command", "long_form", "prompt_injection", "indian_numbering",
]

# The plan's floor and ceiling. Below the floor the corpus is too thin to support
# per-direction scoring; above the ceiling two blinded reviewers cannot finish it.
MIN_TOTAL, MAX_TOTAL = 450, 700
MIN_PER_DIRECTION = 90
MIN_CRITICAL_PER_DIRECTION = 15

DEVANAGARI = range(0x0900, 0x0980)


def has_devanagari(text):
    return any(ord(ch) in DEVANAGARI for ch in text)


def expected_bucket(source):
    """Length bucket is what slices the latency results, so it must match reality."""
    n, tokens = len(source), len(source.split())
    if n <= 25 and tokens <= 3:
        return "words_1_3"
    if n <= 50:
        return "chars_le_50"
    if n <= 200:
        return "chars_le_200"
    if n <= 1000:
        return "chars_201_1000"
    return "near_limit"


def load(path):
    items, errors = [], []
    with open(path, encoding="utf-8") as handle:
        for lineno, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                items.append(json.loads(line))
            except json.JSONDecodeError as exc:
                errors.append(f"{path}:{lineno}: not valid JSON: {exc}")
    return items, errors


def check_item(item, index):
    """Per-item structural checks. Returns a list of human-readable failures."""
    errors = []
    where = item.get("id") or f"line {index}"

    for field, expected_type in REQUIRED_FIELDS.items():
        if field not in item:
            errors.append(f"{where}: missing required field '{field}'")
        elif not isinstance(item[field], expected_type):
            errors.append(
                f"{where}: field '{field}' should be {expected_type.__name__}, "
                f"got {type(item[field]).__name__}"
            )
    if errors:
        return errors  # further checks would just cascade off the bad shape

    direction = item["direction"]
    if direction not in DIRECTIONS:
        errors.append(f"{where}: unknown direction '{direction}'")
    elif not item["id"].startswith(DIRECTIONS[direction]):
        errors.append(
            f"{where}: id prefix does not match direction '{direction}' "
            f"(expected '{DIRECTIONS[direction]}_...')"
        )

    if not item["source"].strip():
        errors.append(f"{where}: source is empty or whitespace only")

    # 4000 is ProcessTextInput.MAX_LENGTH -- anything longer is rejected before the
    # provider is ever called, so it could never be benchmarked.
    if len(item["source"]) > 4000:
        errors.append(
            f"{where}: source is {len(item['source'])} chars, over the 4000-char "
            "ProcessTextInput.MAX_LENGTH cap; the app would reject it before translation"
        )

    actual = expected_bucket(item["source"])
    if item["length_bucket"] != actual:
        errors.append(
            f"{where}: length_bucket is '{item['length_bucket']}' but the source is "
            f"{len(item['source'])} chars, which is '{actual}'"
        )

    if item["critical"] and item.get("critical_kind") not in CRITICAL_KINDS:
        errors.append(
            f"{where}: critical items need a critical_kind from {sorted(CRITICAL_KINDS)}, "
            f"got {item.get('critical_kind')!r}"
        )
    if not item["critical"] and item.get("critical_kind") is not None:
        errors.append(f"{where}: non-critical item must have critical_kind null")

    if item["latency_slice"] not in ("primary", "extended"):
        errors.append(f"{where}: latency_slice must be 'primary' or 'extended'")

    # A protected token that isn't in the source is a typo in the assertion, and it
    # would make the reviewer check for something that was never there.
    for token in item["protected_tokens"]:
        if token.lower() not in item["source"].lower():
            errors.append(f"{where}: protected token {token!r} does not occur in the source")

    if len(item["assertion"]) < 20:
        errors.append(f"{where}: assertion is too short to tell a reviewer what to check")

    # Script sanity: a Hinglish source written in Devanagari isn't Hinglish. The
    # en_to_hinglish transliteration slice is the deliberate exception -- there the
    # Devanagari IS the input and the Latin script is the expected output.
    if direction == "hinglish_to_en" and has_devanagari(item["source"]):
        errors.append(f"{where}: hinglish_to_en source contains Devanagari; it should be Romanized")
    if direction == "hi_to_en" and not has_devanagari(item["source"]):
        errors.append(f"{where}: hi_to_en source contains no Devanagari")
    if direction == "en_to_hi" and has_devanagari(item["source"]):
        errors.append(f"{where}: en_to_hi source contains Devanagari; the source should be English")

    return errors


def check_corpus(items):
    """Corpus-level balance and coverage checks."""
    errors = []

    ids = Counter(i.get("id") for i in items)
    for dup, count in ids.items():
        if count > 1:
            errors.append(f"duplicate id '{dup}' appears {count} times")

    by_direction = defaultdict(list)
    for item in items:
        by_direction[item.get("direction")].append(item)

    for direction in DIRECTIONS:
        found = by_direction.get(direction, [])
        if len(found) < MIN_PER_DIRECTION:
            errors.append(
                f"direction '{direction}' has {len(found)} items, below the "
                f"{MIN_PER_DIRECTION} needed to score that direction independently "
                "(VALIDATION_PLAN.md 4.3 requires a mean score per direction)"
            )
        criticals = [i for i in found if i.get("critical")]
        if len(criticals) < MIN_CRITICAL_PER_DIRECTION:
            errors.append(
                f"direction '{direction}' has {len(criticals)} critical items, below "
                f"{MIN_CRITICAL_PER_DIRECTION}; the protected critical subset would be "
                "too small to support the zero-reversals gate"
            )
        # Duplicate sources waste reviewer time and skew the mean toward one sentence.
        sources = Counter(i.get("source") for i in found)
        for text, count in sources.items():
            if count > 1:
                errors.append(
                    f"direction '{direction}': source text repeated {count} times: {text[:60]!r}"
                )

    total = len(items)
    if not MIN_TOTAL <= total <= MAX_TOTAL:
        errors.append(
            f"corpus has {total} items, outside the {MIN_TOTAL}-{MAX_TOTAL} range "
            "frozen in VALIDATION_PLAN.md 4.1"
        )

    present_slices = {s for i in items for s in i.get("slices", [])}
    for slice_name in REQUIRED_SLICES:
        if slice_name not in present_slices:
            errors.append(
                f"required slice '{slice_name}' has no items; VALIDATION_PLAN.md 4.1 "
                "names it explicitly"
            )

    for direction in DIRECTIONS:
        buckets = {i.get("length_bucket") for i in by_direction.get(direction, [])}
        missing = [b for b in LENGTH_BUCKETS if b not in buckets]
        if missing:
            errors.append(
                f"direction '{direction}' has no items in length bucket(s) {missing}; "
                "VALIDATION_PLAN.md 3.3 requires every length band per direction"
            )

    return errors


def build_report(items):
    lines = ["# Corpus coverage report", ""]
    lines.append(f"Generated from `{CORPUS_PATH}` by `benchmark/validate_corpus.py`.")
    lines.append("Regenerate it rather than editing it by hand.")
    lines.append("")
    lines.append(f"**Total items: {len(items)}**")
    lines.append("")

    lines.append("## By direction")
    lines.append("")
    lines.append("| Direction | Items | Critical | Release-scope | Extended latency slice |")
    lines.append("|---|---:|---:|---:|---:|")
    for direction in DIRECTIONS:
        found = [i for i in items if i["direction"] == direction]
        lines.append(
            f"| `{direction}` | {len(found)} | "
            f"{sum(1 for i in found if i['critical'])} | "
            f"{sum(1 for i in found if i['release_scope'])} | "
            f"{sum(1 for i in found if i['latency_slice'] == 'extended')} |"
        )
    lines.append("")

    lines.append("## By length bucket")
    lines.append("")
    header = "| Bucket | " + " | ".join(f"`{d}`" for d in DIRECTIONS) + " | Total |"
    lines.append(header)
    lines.append("|---|" + "---:|" * (len(DIRECTIONS) + 1))
    for bucket in LENGTH_BUCKETS:
        cells = []
        for direction in DIRECTIONS:
            cells.append(str(sum(1 for i in items
                                 if i["direction"] == direction and i["length_bucket"] == bucket)))
        total = sum(1 for i in items if i["length_bucket"] == bucket)
        lines.append(f"| `{bucket}` | " + " | ".join(cells) + f" | {total} |")
    lines.append("")

    lines.append("## Protected critical subset by failure kind")
    lines.append("")
    lines.append("These are the items whose failure VALIDATION_PLAN.md 4.3 treats as a hard gate,")
    lines.append("not a score deduction.")
    lines.append("")
    lines.append("| Critical kind | Items |")
    lines.append("|---|---:|")
    kinds = Counter(i["critical_kind"] for i in items if i["critical"])
    for kind, count in sorted(kinds.items(), key=lambda kv: -kv[1]):
        lines.append(f"| `{kind}` | {count} |")
    lines.append(f"| **total** | **{sum(kinds.values())}** |")
    lines.append("")

    lines.append("## Slice coverage")
    lines.append("")
    lines.append("| Slice | Items |")
    lines.append("|---|---:|")
    slices = Counter(s for i in items for s in i["slices"])
    for name, count in sorted(slices.items()):
        lines.append(f"| `{name}` | {count} |")
    lines.append("")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", default=CORPUS_PATH)
    parser.add_argument("--report", help="write a markdown coverage report to this path")
    args = parser.parse_args()

    try:
        items, errors = load(args.corpus)
    except FileNotFoundError:
        print(f"FAIL: corpus not found at {args.corpus}", file=sys.stderr)
        return 2

    for index, item in enumerate(items, 1):
        errors.extend(check_item(item, index))
    errors.extend(check_corpus(items))

    if errors:
        print(f"FAIL: {len(errors)} problem(s) in {args.corpus}\n", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(f"OK: {len(items)} items in {args.corpus}")
    for direction in DIRECTIONS:
        found = [i for i in items if i["direction"] == direction]
        print(f"  {direction:>16}: {len(found):>3} items, "
              f"{sum(1 for i in found if i['critical']):>3} critical")

    if args.report:
        with open(args.report, "w", encoding="utf-8") as handle:
            handle.write(build_report(items))
        print(f"  coverage report written to {args.report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
