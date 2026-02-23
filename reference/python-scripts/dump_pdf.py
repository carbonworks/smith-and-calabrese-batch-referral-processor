"""
PDF Structure Dump Tool (pdfplumber)
Extracts all available data from a PDF and saves it as JSON.

Usage:
    python dump_pdf.py "C:\\path\\to\\file.pdf"
    python dump_pdf.py file1.pdf file2.pdf file3.pdf
    python dump_pdf.py --pages 1,3-5 file.pdf

Part of the S&C Phase 1 dual-library extraction pipeline.
See: build-plan.md Section 6
"""

import sys
import os
import json
import argparse
import time
from decimal import Decimal
from datetime import datetime


def log(msg="", indent=0):
    """Print a timestamped log line."""
    ts = datetime.now().strftime("%H:%M:%S")
    prefix = "  " * indent
    print(f"[{ts}] {prefix}{msg}")


def parse_page_ranges(page_str):
    """Parse a page range string like '1,3-5,8' into a set of 1-based page numbers."""
    pages = set()
    for part in page_str.split(","):
        part = part.strip()
        if "-" in part:
            start, end = part.split("-", 1)
            pages.update(range(int(start), int(end) + 1))
        else:
            pages.add(int(part))
    return pages


def make_json_safe(obj):
    """
    Convert objects that json.dumps can't handle.
    pdfplumber returns Decimal objects for all coordinates and sizes.
    This converts them to regular floating-point numbers.
    """
    if isinstance(obj, Decimal):
        return float(obj)
    if isinstance(obj, bytes):
        try:
            return obj.decode("utf-8", errors="replace")
        except Exception:
            return str(obj)
    if isinstance(obj, set):
        return list(obj)
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Object of type {type(obj).__name__} is not JSON serializable")


def extract_page_data(page, page_num):
    """Extract all available data from a single page."""
    page_data = {
        "page_number": page_num,
        "width": page.width,
        "height": page.height,
    }

    # Full page text
    try:
        page_data["text"] = page.extract_text() or ""
    except Exception as e:
        page_data["text"] = f"[Error extracting text: {e}]"

    # Words with positions (each word = text + bounding box)
    try:
        page_data["words"] = page.extract_words(
            keep_blank_chars=False,
            extra_attrs=["fontname", "size"],
        )
    except Exception as e:
        page_data["words"] = f"[Error extracting words: {e}]"

    # Character count (NOT full char dump -- see technical notes in guide)
    try:
        char_count = len(page.chars)
        page_data["char_count"] = char_count
        sample_size = min(20, char_count)
        page_data["char_sample"] = page.chars[:sample_size]
    except Exception as e:
        page_data["char_count"] = f"[Error: {e}]"
        page_data["char_sample"] = []

    # Lines (horizontal/vertical rules -- important for table detection)
    try:
        page_data["lines"] = page.lines
        page_data["line_count"] = len(page.lines)
    except Exception as e:
        page_data["lines"] = f"[Error: {e}]"

    # Rectangles (boxes, form fields, shaded regions)
    try:
        page_data["rects"] = page.rects
        page_data["rect_count"] = len(page.rects)
    except Exception as e:
        page_data["rects"] = f"[Error: {e}]"

    # Curves (borders, decorative elements)
    try:
        page_data["curves"] = page.curves
        page_data["curve_count"] = len(page.curves)
    except Exception as e:
        page_data["curves"] = f"[Error: {e}]"

    # Tables (pdfplumber's automatic table detection)
    try:
        tables = page.extract_tables()
        page_data["tables"] = tables if tables else []
        page_data["table_count"] = len(tables) if tables else 0
    except Exception as e:
        page_data["tables"] = f"[Error extracting tables: {e}]"

    return page_data


def dump_pdf(pdf_path, page_filter=None):
    """Open a PDF and extract its full structure to a dictionary.

    Args:
        pdf_path: Path to the PDF file.
        page_filter: Optional set of 1-based page numbers to extract.
                     None means all pages.
    """
    if not os.path.isfile(pdf_path):
        log(f"ERROR: File not found: {pdf_path}")
        return None

    if not pdf_path.lower().endswith(".pdf"):
        log(f"WARNING: Not a .pdf extension: {pdf_path}")

    try:
        import pdfplumber
    except ImportError:
        log("ERROR: pdfplumber is not installed.")
        log("  Run: pip install pdfplumber")
        return None

    log(f"Library: pdfplumber {pdfplumber.__version__}")
    log(f"Network: none (pure Python, fully offline)")
    log(f"Opening: {pdf_path}")

    pdf_start = time.monotonic()

    try:
        with pdfplumber.open(pdf_path) as pdf:
            total = len(pdf.pages)
            log(f"PDF loaded: {total} pages, metadata keys: {list((pdf.metadata or {}).keys())}")

            if page_filter:
                invalid = {p for p in page_filter if p < 1 or p > total}
                if invalid:
                    log(f"WARNING: Pages {sorted(invalid)} out of range (PDF has {total} pages), skipping")
                page_numbers = sorted(p for p in page_filter if 1 <= p <= total)
                log(f"Page filter active: extracting pages {page_numbers} of {total}")
            else:
                page_numbers = list(range(1, total + 1))
                log(f"Extracting all {total} pages")

            result = {
                "source_file": os.path.basename(pdf_path),
                "dump_created": datetime.now().isoformat(),
                "tool": "pdfplumber",
                "pdfplumber_version": pdfplumber.__version__,
                "total_pages": total,
                "pages_extracted": page_numbers,
                "metadata": pdf.metadata or {},
                "pages": [],
            }

            total_words = 0
            total_chars = 0
            total_tables = 0

            for i in page_numbers:
                page = pdf.pages[i - 1]
                page_start = time.monotonic()
                page_data = extract_page_data(page, i)
                page_elapsed = time.monotonic() - page_start

                result["pages"].append(page_data)

                word_count = len(page_data["words"]) if isinstance(page_data["words"], list) else 0
                char_count = page_data.get("char_count", 0) if isinstance(page_data.get("char_count"), int) else 0
                table_count = page_data.get("table_count", 0) if isinstance(page_data.get("table_count"), int) else 0

                total_words += word_count
                total_chars += char_count
                total_tables += table_count

                log(f"Page {i}/{total}: {word_count} words, {char_count} chars, "
                    f"{table_count} tables ({page_elapsed:.2f}s)")

            pdf_elapsed = time.monotonic() - pdf_start
            log(f"Extraction complete: {len(page_numbers)} pages in {pdf_elapsed:.2f}s")
            log(f"Totals: {total_words} words, {total_chars} chars, {total_tables} tables")

            return result

    except Exception as e:
        error_type = type(e).__name__
        log(f"ERROR opening PDF: {error_type}: {e}")
        if "password" in str(e).lower() or "encrypt" in str(e).lower():
            log("PDF appears to be password-protected or encrypted.")
        return None


def save_result(result, pdf_path):
    """Save extraction result as JSON alongside the source PDF."""
    pdf_dir = os.path.dirname(pdf_path)
    pdf_name = os.path.splitext(os.path.basename(pdf_path))[0]
    json_filename = f"{pdf_name}-pdfplumber.json"
    json_path = os.path.join(pdf_dir, json_filename)

    log(f"Writing JSON: {json_path}")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, default=make_json_safe, ensure_ascii=False)

    file_size_kb = os.path.getsize(json_path) / 1024
    log(f"Saved: {file_size_kb:.0f} KB")
    return json_path


def main():
    parser = argparse.ArgumentParser(
        description="Extract PDF structure to JSON using pdfplumber.",
    )
    parser.add_argument("pdf_files", nargs="+", help="PDF file(s) to process")
    parser.add_argument(
        "--pages", "-p",
        help="Pages to extract (e.g. '1,3-5,8'). Default: 3-4.",
        default="3-4",
    )
    args = parser.parse_args()

    page_filter = parse_page_ranges(args.pages) if args.pages else None

    print()
    print("=" * 60)
    log("PDF Structure Dump Tool (pdfplumber)")
    log(f"Files to process: {len(args.pdf_files)}")
    if page_filter:
        log(f"Page filter: {sorted(page_filter)}")
    print("=" * 60)

    batch_start = time.monotonic()
    results = []
    failures = []

    for idx, pdf_path in enumerate(args.pdf_files, start=1):
        pdf_path = pdf_path.strip('"').strip("'")
        pdf_path = os.path.abspath(pdf_path)

        print()
        log(f"--- File {idx}/{len(args.pdf_files)} ---")
        result = dump_pdf(pdf_path, page_filter=page_filter)
        if result:
            json_path = save_result(result, pdf_path)
            results.append(json_path)
        else:
            log(f"FAILED: {pdf_path}")
            failures.append(pdf_path)

    batch_elapsed = time.monotonic() - batch_start

    print()
    print("=" * 60)
    log("BATCH COMPLETE")
    log(f"Processed: {len(results)}/{len(args.pdf_files)} succeeded")
    log(f"Total time: {batch_elapsed:.2f}s")

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
