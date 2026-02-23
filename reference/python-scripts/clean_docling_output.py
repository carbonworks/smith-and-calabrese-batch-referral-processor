"""
Docling Output Cleanup Tool
Strips embedded image data from Docling JSON output files while preserving
all text, tables, markdown, metadata, and document structure.

Usage:
    python clean_docling_output.py output-docling.json
    python clean_docling_output.py file1-docling.json file2-docling.json
    python clean_docling_output.py *.json

Output: <filename>-cleaned.json in same directory.

Removes:
- document.pictures list items (embedded figure/image data)
- document.pages.*.image (per-page base64 bitmaps)
- $ref pointers to removed pictures in body.children, group children, etc.

See: build-plan.md Section 6
"""

import sys
import os
import json
import argparse
from datetime import datetime


def log(msg="", indent=0):
    """Print a timestamped log line."""
    ts = datetime.now().strftime("%H:%M:%S")
    prefix = "  " * indent
    print(f"[{ts}] {prefix}{msg}")


def clean_docling_json(data):
    """Remove image data from a Docling extraction dict.

    Handles Docling DoclingDocument v1.9.0 structure where:
    - pictures is a top-level list of items with embedded image data
    - pages.*.image holds per-page base64 bitmaps
    - body/furniture are dicts with children arrays of $ref pointers
    - groups may also contain $ref pointers to pictures

    Returns (cleaned_data, stats) where stats tracks what was removed.
    """
    stats = {
        "pictures_removed": 0,
        "page_images_removed": 0,
        "picture_refs_removed": 0,
    }

    doc = data.get("document")
    if not isinstance(doc, dict):
        log("WARNING: No 'document' key found or not a dict — nothing to clean")
        return data, stats

    # 1. Remove document.pictures (list of picture items with image data)
    pictures = doc.get("pictures")
    if isinstance(pictures, list) and pictures:
        stats["pictures_removed"] = len(pictures)
        doc["pictures"] = []
        log(f"Cleared document.pictures ({stats['pictures_removed']} items)", indent=1)

    # 2. Remove per-page image data (document.pages.*.image)
    pages = doc.get("pages")
    if isinstance(pages, dict):
        for page_key, page_data in pages.items():
            if isinstance(page_data, dict) and "image" in page_data:
                del page_data["image"]
                stats["page_images_removed"] += 1
        if stats["page_images_removed"]:
            log(f"Removed image data from {stats['page_images_removed']} pages", indent=1)

    # 3. Remove $ref pointers to pictures from body, furniture, and groups
    #    Docling uses {"$ref": "#/pictures/N"} in children arrays
    for section_name in ("body", "furniture"):
        section = doc.get(section_name)
        if isinstance(section, dict) and "children" in section:
            stats["picture_refs_removed"] += _remove_picture_refs(
                section["children"]
            )

    groups = doc.get("groups")
    if isinstance(groups, list):
        for group in groups:
            if isinstance(group, dict) and "children" in group:
                stats["picture_refs_removed"] += _remove_picture_refs(
                    group["children"]
                )

    if stats["picture_refs_removed"]:
        log(f"Removed {stats['picture_refs_removed']} $ref pointers to pictures", indent=1)

    return data, stats


def _remove_picture_refs(children):
    """Remove $ref entries pointing to pictures from a children list. Returns count removed."""
    if not isinstance(children, list):
        return 0
    original = len(children)
    children[:] = [
        child for child in children
        if not _is_picture_ref(child)
    ]
    return original - len(children)


def _is_picture_ref(item):
    """Check if a $ref entry points to a picture."""
    if not isinstance(item, dict):
        return False
    ref = item.get("$ref", "")
    return ref.startswith("#/pictures/")


def clean_file(json_path):
    """Clean a single Docling JSON file. Returns output path or None on failure."""
    log(f"Reading: {json_path}")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    original_size = os.path.getsize(json_path)
    log(f"Input size: {original_size / 1024:.0f} KB", indent=1)

    data, stats = clean_docling_json(data)

    total_removed = sum(stats.values())
    if total_removed == 0:
        log("No image data found — file is already clean", indent=1)

    # Save cleaned file
    base, ext = os.path.splitext(json_path)
    out_path = f"{base}-cleaned{ext}"

    log(f"Writing: {out_path}")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    cleaned_size = os.path.getsize(out_path)
    reduction = original_size - cleaned_size
    log(f"Output size: {cleaned_size / 1024:.0f} KB (reduced {reduction / 1024:.0f} KB)", indent=1)

    return out_path


def main():
    parser = argparse.ArgumentParser(
        description="Strip embedded image data from Docling JSON output files.",
    )
    parser.add_argument("json_files", nargs="+", help="Docling JSON file(s) to clean")
    args = parser.parse_args()

    print()
    print("=" * 60)
    log("Docling Output Cleanup Tool")
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

        if not json_path.lower().endswith(".json"):
            log(f"WARNING: Not a .json file: {json_path}")

        try:
            out_path = clean_file(json_path)
            results.append(out_path)
        except Exception as e:
            log(f"ERROR: {type(e).__name__}: {e}")
            failures.append(json_path)

    print()
    print("=" * 60)
    log("BATCH COMPLETE")
    log(f"Processed: {len(results)}/{len(args.json_files)} succeeded")

    if results:
        log("Output files:")
        for path in results:
            log(f"  {path}")

    if failures:
        log("Failed files:")
        for path in failures:
            log(f"  {path}")

    print("=" * 60)
    print()


if __name__ == "__main__":
    main()
