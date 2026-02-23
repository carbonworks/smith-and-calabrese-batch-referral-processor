"""
Docling Page Trimmer
Removes all content from page 5 onward from Docling JSON output files.
Overwrites the input file in place.

Usage:
    python trim_docling_pages.py output-docling.json
    python trim_docling_pages.py file1.json file2.json

Removes:
- Pages 5+ from document.pages
- All texts, pictures, tables, groups with provenance on pages 5+
- $ref pointers to removed items from body/furniture/group children

See: build-plan.md Section 6
"""

import sys
import os
import json
import argparse
from datetime import datetime


KEEP_PAGES_THROUGH = 4  # keep pages 1-4, remove 5+


def log(msg="", indent=0):
    """Print a timestamped log line."""
    ts = datetime.now().strftime("%H:%M:%S")
    prefix = "  " * indent
    print(f"[{ts}] {prefix}{msg}")


def _item_on_kept_pages(item):
    """Check if an item has provenance on pages we're keeping."""
    if not isinstance(item, dict):
        return True
    prov = item.get("prov", [])
    if not prov:
        return True  # no provenance — keep it
    for p in prov:
        page_no = p.get("page_no")
        if page_no is not None and int(page_no) <= KEEP_PAGES_THROUGH:
            return True
    return False


def _build_kept_refs(doc):
    """Build a set of self_ref values for items that survive the page filter."""
    kept = set()
    for list_key in ("texts", "pictures", "tables", "groups", "key_value_items", "form_items"):
        items = doc.get(list_key)
        if not isinstance(items, list):
            continue
        for item in items:
            if isinstance(item, dict) and _item_on_kept_pages(item):
                ref = item.get("self_ref")
                if ref:
                    kept.add(ref)
    return kept


def _filter_children(children, kept_refs):
    """Remove $ref entries from a children list if they point to removed items. Returns count removed."""
    if not isinstance(children, list):
        return 0
    original = len(children)
    children[:] = [
        child for child in children
        if not isinstance(child, dict)
        or child.get("$ref", "") in kept_refs
    ]
    return original - len(children)


def trim_pages(data):
    """Remove all content from page 5+ from a Docling extraction dict.

    Returns (trimmed_data, stats).
    """
    stats = {
        "pages_removed": 0,
        "texts_removed": 0,
        "pictures_removed": 0,
        "tables_removed": 0,
        "groups_removed": 0,
        "refs_removed": 0,
    }

    doc = data.get("document")
    if not isinstance(doc, dict):
        log("WARNING: No 'document' key found — nothing to trim")
        return data, stats

    # 1. Remove pages 5+
    pages = doc.get("pages")
    if isinstance(pages, dict):
        to_remove = [k for k in pages if int(k) > KEEP_PAGES_THROUGH]
        for k in to_remove:
            del pages[k]
        stats["pages_removed"] = len(to_remove)
        if to_remove:
            log(f"Removed {len(to_remove)} pages (kept 1-{KEEP_PAGES_THROUGH})", indent=1)

    # 2. Filter item lists by provenance
    for list_key, stat_key in [
        ("texts", "texts_removed"),
        ("pictures", "pictures_removed"),
        ("tables", "tables_removed"),
        ("groups", "groups_removed"),
    ]:
        items = doc.get(list_key)
        if isinstance(items, list):
            original = len(items)
            doc[list_key] = [item for item in items if _item_on_kept_pages(item)]
            stats[stat_key] = original - len(doc[list_key])
            if stats[stat_key]:
                log(f"Removed {stats[stat_key]}/{original} {list_key}", indent=1)

    # 3. Clean up $ref pointers in body, furniture, and remaining groups
    kept_refs = _build_kept_refs(doc)

    for section_name in ("body", "furniture"):
        section = doc.get(section_name)
        if isinstance(section, dict) and "children" in section:
            stats["refs_removed"] += _filter_children(section["children"], kept_refs)

    groups = doc.get("groups")
    if isinstance(groups, list):
        for group in groups:
            if isinstance(group, dict) and "children" in group:
                stats["refs_removed"] += _filter_children(group["children"], kept_refs)

    if stats["refs_removed"]:
        log(f"Removed {stats['refs_removed']} $ref pointers to trimmed items", indent=1)

    return data, stats


def trim_file(json_path):
    """Trim pages from a single Docling JSON file. Overwrites in place."""
    log(f"Reading: {json_path}")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    original_size = os.path.getsize(json_path)
    log(f"Input size: {original_size / 1024:.0f} KB", indent=1)

    data, stats = trim_pages(data)

    total_removed = sum(stats.values())
    if total_removed == 0:
        log("No content on pages 5+ — file unchanged", indent=1)
        return json_path

    log(f"Writing: {json_path}")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    trimmed_size = os.path.getsize(json_path)
    reduction = original_size - trimmed_size
    log(f"Output size: {trimmed_size / 1024:.0f} KB (reduced {reduction / 1024:.0f} KB)", indent=1)

    return json_path


def main():
    parser = argparse.ArgumentParser(
        description=f"Remove all content from page {KEEP_PAGES_THROUGH + 1}+ from Docling JSON files (in place).",
    )
    parser.add_argument("json_files", nargs="+", help="Docling JSON file(s) to trim")
    args = parser.parse_args()

    print()
    print("=" * 60)
    log("Docling Page Trimmer")
    log(f"Keeping pages 1-{KEEP_PAGES_THROUGH}, removing {KEEP_PAGES_THROUGH + 1}+")
    log(f"Files to process: {len(args.json_files)}")
    print("=" * 60)

    results = []
    failures = []

    for idx, json_path in enumerate(args.json_files, start=1):
        json_path = json_path.strip('"').strip("'")
        json_path = os.path.abspath(json_path)

        print()
        log(f"--- File {idx}/{len(args.json_files)} ---")

        if not os.path.isfile(json_path):
            log(f"ERROR: File not found: {json_path}")
            failures.append(json_path)
            continue

        try:
            out_path = trim_file(json_path)
            results.append(out_path)
        except Exception as e:
            log(f"ERROR: {type(e).__name__}: {e}")
            failures.append(json_path)

    print()
    print("=" * 60)
    log("BATCH COMPLETE")
    log(f"Processed: {len(results)}/{len(args.json_files)} succeeded")

    if failures:
        log("Failed files:")
        for path in failures:
            log(f"  {path}")

    print("=" * 60)
    print()


if __name__ == "__main__":
    main()
