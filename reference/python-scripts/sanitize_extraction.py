"""
PHI Sanitization Tool for PDF Extraction JSON
Strips likely PHI from extraction output and replaces with typed placeholders.

Usage:
    python sanitize_extraction.py input-pdfplumber.json
    python sanitize_extraction.py input-docling.json
    python sanitize_extraction.py *.json

Output: <filename>-sanitized.json in same directory.

IMPORTANT: This is a helper, not a guarantee. Always review output manually
before sharing. Automated pattern matching will miss some PHI and may
over-match non-PHI text. The manual review step is not optional.

See: build-plan.md Section 6 — Sanitization Protocol
"""

import sys
import os
import json
import re
from datetime import datetime


# --- Pattern definitions ---
# Each pattern: (compiled regex, placeholder, description)
# Ordered from most specific to least to reduce false positives.

PATTERNS = [
    # SSN: 123-45-6789 or 123 45 6789
    (
        re.compile(r"\b\d{3}[-\s]\d{2}[-\s]\d{4}\b"),
        "[SSN]",
        "Social Security Number",
    ),
    # SSN: 9 digits with no separator (only in contexts that look like SSNs)
    (
        re.compile(r"\b\d{9}\b"),
        "[SSN_CANDIDATE]",
        "Possible SSN (9 consecutive digits)",
    ),
    # Phone: (555) 123-4567, 555-123-4567, 555.123.4567
    (
        re.compile(r"\(?\d{3}\)?[-.\s]\d{3}[-.\s]\d{4}\b"),
        "[PHONE]",
        "Phone number",
    ),
    # Date: MM/DD/YYYY, MM-DD-YYYY, MM.DD.YYYY (and 2-digit year variants)
    (
        re.compile(r"\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b"),
        "[DATE]",
        "Date (possible DOB)",
    ),
    # ZIP code: 12345 or 12345-6789 (only flag, don't auto-replace — low risk alone)
    (
        re.compile(r"\b\d{5}(-\d{4})?\b"),
        None,  # None = flag but don't replace (too many false positives)
        "Possible ZIP code",
    ),
]

# Labels that typically precede PHI values in SSA referral forms.
# Used for the flagging report, not for automatic replacement.
PHI_LABEL_KEYWORDS = [
    "name", "patient", "claimant", "beneficiary",
    "dob", "date of birth", "birth date", "born",
    "ssn", "social security", "social sec",
    "address", "street", "city", "state", "zip",
    "phone", "telephone", "tel", "cell", "mobile",
    "diagnosis", "condition", "icd", "dx",
    "case number", "claim number", "case no", "claim no",
]


def sanitize_text(text, stats):
    """Apply all replacement patterns to a text string. Returns sanitized text."""
    if not isinstance(text, str):
        return text

    for pattern, placeholder, description in PATTERNS:
        if placeholder is None:
            # Flag-only pattern: count matches but don't replace
            matches = pattern.findall(text)
            if matches:
                stats["flagged"].append({
                    "pattern": description,
                    "count": len(matches),
                    "samples": matches[:3],
                })
            continue

        def _replace(match, ph=placeholder, desc=description):
            stats["replaced"].append({
                "pattern": desc,
                "original_length": len(match.group()),
            })
            return ph

        text = pattern.sub(_replace, text)

    return text


def sanitize_value(obj, stats):
    """Recursively walk a JSON structure and sanitize all string values."""
    if isinstance(obj, str):
        return sanitize_text(obj, stats)
    elif isinstance(obj, list):
        return [sanitize_value(item, stats) for item in obj]
    elif isinstance(obj, dict):
        return {key: sanitize_value(value, stats) for key, value in obj.items()}
    else:
        return obj


def flag_phi_labels(obj, flags, path=""):
    """Find keys or text near PHI-related labels for manual review."""
    if isinstance(obj, str):
        lower = obj.lower()
        for keyword in PHI_LABEL_KEYWORDS:
            if keyword in lower:
                flags.append({
                    "path": path,
                    "keyword": keyword,
                    "context": obj[:120],
                })
                break
    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            flag_phi_labels(item, flags, f"{path}[{i}]")
    elif isinstance(obj, dict):
        for key, value in obj.items():
            flag_phi_labels(value, flags, f"{path}.{key}")


def sanitize_file(json_path):
    """Sanitize a single JSON extraction file."""
    print(f"\n  Processing: {os.path.basename(json_path)}")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    stats = {"replaced": [], "flagged": []}
    phi_flags = []

    # Sanitize all string values
    sanitized = sanitize_value(data, stats)

    # Flag PHI-adjacent labels for manual review
    flag_phi_labels(sanitized, phi_flags)

    # Add sanitization metadata
    sanitized["_sanitization"] = {
        "sanitized_at": datetime.now().isoformat(),
        "tool": "sanitize_extraction.py",
        "auto_replacements": len(stats["replaced"]),
        "flagged_patterns": len(stats["flagged"]),
        "phi_label_flags": len(phi_flags),
        "note": "REVIEW REQUIRED: Automated sanitization is not sufficient. "
                "Manually verify no PHI remains before sharing.",
    }

    # Save sanitized file
    base, ext = os.path.splitext(json_path)
    out_path = f"{base}-sanitized{ext}"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(sanitized, f, indent=2, ensure_ascii=False)

    file_size_kb = os.path.getsize(out_path) / 1024
    print(f"  Saved: {out_path} ({file_size_kb:.0f} KB)")

    # Print summary
    print(f"\n  --- Sanitization Summary ---")
    print(f"  Auto-replaced:  {len(stats['replaced'])} values")
    if stats["replaced"]:
        by_type = {}
        for r in stats["replaced"]:
            by_type[r["pattern"]] = by_type.get(r["pattern"], 0) + 1
        for ptype, count in by_type.items():
            print(f"    - {ptype}: {count}")

    print(f"  Flagged (not replaced): {len(stats['flagged'])} patterns")
    for flag in stats["flagged"]:
        print(f"    - {flag['pattern']}: {flag['count']} matches (samples: {flag['samples'][:2]})")

    print(f"  PHI-adjacent labels:    {len(phi_flags)} locations need manual review")
    if phi_flags:
        for flag in phi_flags[:10]:
            print(f"    - {flag['path']}: \"{flag['keyword']}\" in \"{flag['context'][:60]}...\"")
        if len(phi_flags) > 10:
            print(f"    ... and {len(phi_flags) - 10} more")

    print()
    print("  >>> MANUAL REVIEW REQUIRED <<<")
    print("  Open the -sanitized.json file and verify:")
    print("  1. No patient names remain (names can't be auto-detected)")
    print("  2. No addresses remain (street names can't be auto-detected)")
    print("  3. All dates are replaced (or are non-PHI dates like form dates)")
    print("  4. No diagnosis descriptions or medical terminology with identifiers")

    return out_path


def main():
    if len(sys.argv) < 2:
        print("Usage: python sanitize_extraction.py <json_file> [json_file2] ...")
        print("  Strips PHI patterns from PDF extraction JSON files.")
        print("  Output: <filename>-sanitized.json in same directory.")
        print()
        print("  IMPORTANT: Always review output manually before sharing.")
        sys.exit(1)

    json_paths = sys.argv[1:]
    results = []

    for json_path in json_paths:
        json_path = json_path.strip('"').strip("'")
        json_path = os.path.abspath(json_path)

        if not os.path.isfile(json_path):
            print(f"  ERROR: File not found: {json_path}")
            continue
        if not json_path.lower().endswith(".json"):
            print(f"  WARNING: Not a .json file: {json_path}")

        out_path = sanitize_file(json_path)
        results.append(out_path)

    print(f"\nDone. {len(results)} files sanitized.")
    print("Remember: review each -sanitized.json before pulling into the intranet.")


if __name__ == "__main__":
    main()
