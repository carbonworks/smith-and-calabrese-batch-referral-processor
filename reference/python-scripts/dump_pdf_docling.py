"""
PDF Structure Dump Tool (Docling)
Extracts document structure from a PDF using IBM Docling and saves as JSON.

Usage:
    python dump_pdf_docling.py "C:\\path\\to\\file.pdf"
    python dump_pdf_docling.py file1.pdf file2.pdf file3.pdf
    python dump_pdf_docling.py --pages 1,3-5 file.pdf

All processing is LOCAL. Docling runs TableFormer and OCR models on your
machine. No data is sent to any cloud service. After initial model download,
set HF_HUB_OFFLINE=1 to block all network access.

Part of the S&C Phase 1 dual-library extraction pipeline.
Produces output alongside dump_pdf.py (pdfplumber) for comparison.
See: build-plan.md Section 6
"""

import sys
import os
import json
import argparse
import time
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


def filter_doc_dict(doc_dict, page_numbers):
    """Filter a Docling export dict to only include content from requested pages.

    Docling items carry provenance ('prov') with page numbers. This walks the
    document body and tables, keeping only items whose provenance references
    a page in page_numbers.
    """
    filtered = dict(doc_dict)

    # Filter pages metadata
    if "pages" in filtered and isinstance(filtered["pages"], dict):
        filtered["pages"] = {
            k: v for k, v in filtered["pages"].items()
            if _page_key_matches(k, page_numbers)
        }

    # Filter body items by provenance
    if "body" in filtered and isinstance(filtered["body"], list):
        original = len(filtered["body"])
        filtered["body"] = [
            item for item in filtered["body"]
            if _item_on_pages(item, page_numbers)
        ]
        log(f"Body items: {original} -> {len(filtered['body'])} after page filter", indent=1)

    # Filter furniture (headers/footers) by provenance
    if "furniture" in filtered and isinstance(filtered["furniture"], list):
        original = len(filtered["furniture"])
        filtered["furniture"] = [
            item for item in filtered["furniture"]
            if _item_on_pages(item, page_numbers)
        ]
        log(f"Furniture items: {original} -> {len(filtered['furniture'])} after page filter", indent=1)

    return filtered


def _page_key_matches(key, page_numbers):
    """Check if a pages dict key (often a string page number) matches the filter."""
    try:
        return int(key) in page_numbers
    except (ValueError, TypeError):
        return True  # keep non-numeric keys


def _item_on_pages(item, page_numbers):
    """Check if a document item has provenance on any of the requested pages."""
    if not isinstance(item, dict):
        return True
    prov = item.get("prov", [])
    if not prov:
        return True  # no provenance info — keep it
    for p in prov:
        page_no = p.get("page_no") or p.get("page")
        if page_no is not None and int(page_no) in page_numbers:
            return True
    return False


def create_converter():
    """Create a Docling DocumentConverter with explicit local-only configuration."""
    from docling.document_converter import DocumentConverter, PdfFormatOption
    from docling.datamodel.base_models import InputFormat
    from docling.datamodel.pipeline_options import PdfPipelineOptions

    pipeline_options = PdfPipelineOptions()
    pipeline_options.do_ocr = True
    pipeline_options.do_table_structure = True
    pipeline_options.generate_picture_images = False
    pipeline_options.generate_page_images = False

    log("Pipeline config:")
    log(f"OCR enabled: {pipeline_options.do_ocr}", indent=1)
    log(f"Table structure: {pipeline_options.do_table_structure}", indent=1)
    log(f"Picture images: {pipeline_options.generate_picture_images}", indent=1)
    log(f"Page images: {pipeline_options.generate_page_images}", indent=1)
    log(f"All models run locally -- no cloud inference", indent=1)
    log(f"NOTE: First load can take 1-2 minutes (loading OCR + layout models into memory)", indent=1)

    offline = os.environ.get("HF_HUB_OFFLINE", "0")
    if offline == "1":
        log("HF_HUB_OFFLINE=1 -- network access blocked (using cached models)", indent=1)
    else:
        log("HF_HUB_OFFLINE not set -- models will download on first run if needed", indent=1)
        log("After first run, set HF_HUB_OFFLINE=1 to guarantee offline operation", indent=1)

    converter = DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options),
        }
    )
    return converter


def dump_pdf_docling(pdf_path, converter, page_filter=None):
    """Open a PDF and extract its structure using Docling.

    Args:
        pdf_path: Path to the PDF file.
        converter: Pre-configured DocumentConverter instance.
        page_filter: Optional set of 1-based page numbers to include in output.
                     None means all pages. Note: Docling always processes the
                     full document; filtering is applied to the output.
    """
    if not os.path.isfile(pdf_path):
        log(f"ERROR: File not found: {pdf_path}")
        return None

    if not pdf_path.lower().endswith(".pdf"):
        log(f"WARNING: Not a .pdf extension: {pdf_path}")

    log(f"Opening: {pdf_path}")
    if page_filter:
        log(f"Page filter: {sorted(page_filter)} (applied after full-document conversion)")

    # Step 1: Convert
    log("Converting PDF (this may take a moment for large documents)...")
    convert_start = time.monotonic()

    try:
        conv_result = converter.convert(pdf_path)
        doc = conv_result.document
    except Exception as e:
        error_type = type(e).__name__
        log(f"ERROR during conversion: {error_type}: {e}")
        return None

    convert_elapsed = time.monotonic() - convert_start
    log(f"Conversion complete ({convert_elapsed:.2f}s)")

    # Step 2: Export to dict
    log("Exporting document structure...")
    result = {
        "source_file": os.path.basename(pdf_path),
        "dump_created": datetime.now().isoformat(),
        "tool": "docling",
        "conversion_time_seconds": round(convert_elapsed, 2),
    }

    try:
        doc_dict = doc.export_to_dict()

        # Log document stats before filtering
        body_count = len(doc_dict.get("body", []))
        page_count = len(doc_dict.get("pages", {}))
        log(f"Document structure: {page_count} pages, {body_count} body items")

        if page_filter:
            log(f"Applying page filter: {sorted(page_filter)}")
            doc_dict = filter_doc_dict(doc_dict, page_filter)
            result["pages_extracted"] = sorted(page_filter)

        result["document"] = doc_dict
    except Exception as e:
        log(f"ERROR exporting to dict: {e}")
        result["document"] = f"[Error exporting to dict: {e}]"

    # Step 3: Markdown export
    log("Generating markdown preview...")
    try:
        result["markdown"] = doc.export_to_markdown()
        md_lines = result["markdown"].count("\n") + 1
        log(f"Markdown: {md_lines} lines")
        if page_filter:
            result["_markdown_note"] = (
                "Markdown is from full document. Use 'document' dict "
                "for page-filtered content."
            )
    except Exception as e:
        log(f"ERROR exporting to markdown: {e}")
        result["markdown"] = f"[Error exporting to markdown: {e}]"

    # Step 4: Extract tables
    log("Extracting tables...")
    try:
        tables = []
        skipped = 0
        for item in doc.tables:
            if page_filter and hasattr(item, "prov"):
                table_pages = {
                    p.page_no for p in item.prov if hasattr(p, "page_no")
                }
                if table_pages and not table_pages & page_filter:
                    skipped += 1
                    continue

            table_data = {
                "export": item.export_to_dataframe().to_dict(orient="records")
                if hasattr(item, "export_to_dataframe")
                else None,
                "text": item.text if hasattr(item, "text") else None,
            }
            tables.append(table_data)

        result["tables"] = tables
        result["table_count"] = len(tables)
        msg = f"Tables: {len(tables)} extracted"
        if skipped:
            msg += f", {skipped} skipped (outside page filter)"
        log(msg)
    except Exception as e:
        log(f"ERROR extracting tables: {e}")
        result["tables"] = f"[Error extracting tables: {e}]"
        result["table_count"] = 0

    return result


def make_json_safe(obj):
    """Handle non-serializable types from Docling output."""
    if isinstance(obj, datetime):
        return obj.isoformat()
    if isinstance(obj, bytes):
        try:
            return obj.decode("utf-8", errors="replace")
        except Exception:
            return str(obj)
    if isinstance(obj, set):
        return list(obj)
    return str(obj)


def save_result(result, pdf_path):
    """Save extraction result as JSON alongside the source PDF."""
    pdf_dir = os.path.dirname(pdf_path)
    pdf_name = os.path.splitext(os.path.basename(pdf_path))[0]
    json_filename = f"{pdf_name}-docling.json"
    json_path = os.path.join(pdf_dir, json_filename)

    log(f"Writing JSON: {json_path}")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, default=make_json_safe, ensure_ascii=False)

    file_size_kb = os.path.getsize(json_path) / 1024
    log(f"Saved: {file_size_kb:.0f} KB")
    return json_path


def main():
    parser = argparse.ArgumentParser(
        description="Extract PDF structure to JSON using Docling (AI-driven, local-only).",
    )
    parser.add_argument("pdf_files", nargs="+", help="PDF file(s) to process")
    parser.add_argument(
        "--pages", "-p",
        help="Pages to include in output (e.g. '1,3-5,8'). Default: 3-4. "
             "Note: Docling processes the full document; filtering is applied to output.",
        default="3-4",
    )
    args = parser.parse_args()

    page_filter = parse_page_ranges(args.pages) if args.pages else None

    print()
    print("=" * 60)
    log("PDF Structure Dump Tool (Docling)")
    log(f"Files to process: {len(args.pdf_files)}")
    if page_filter:
        log(f"Page filter: {sorted(page_filter)}")
    log("Mode: LOCAL ONLY -- no data leaves this machine")
    print("=" * 60)

    # Import and configure Docling once
    log("Loading Docling pipeline...")
    load_start = time.monotonic()

    try:
        converter = create_converter()
    except ImportError:
        log("ERROR: docling is not installed.")
        log("  Run: pip install docling")
        sys.exit(1)
    except Exception as e:
        log(f"ERROR initializing Docling: {e}")
        sys.exit(1)

    load_elapsed = time.monotonic() - load_start
    log(f"Pipeline loaded ({load_elapsed:.2f}s)")

    batch_start = time.monotonic()
    results = []
    failures = []

    for idx, pdf_path in enumerate(args.pdf_files, start=1):
        pdf_path = pdf_path.strip('"').strip("'")
        pdf_path = os.path.abspath(pdf_path)

        print()
        log(f"--- File {idx}/{len(args.pdf_files)} ---")
        result = dump_pdf_docling(pdf_path, converter, page_filter=page_filter)
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
    log(f"Total time: {batch_elapsed:.2f}s (pipeline load: {load_elapsed:.2f}s)")

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
