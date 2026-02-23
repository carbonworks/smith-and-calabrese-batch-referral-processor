"""
SSA Referral Field Extractor
Extracts structured fields from SSA CE referral PDFs using pdfplumber.
Also accepts pre-extracted Docling JSON as input.

Usage:
    python extract_referral_fields.py referral.pdf
    python extract_referral_fields.py file1.pdf file2.pdf
    python extract_referral_fields.py referral-docling.json

Output: <filename>-extracted.json in same directory.

Extracts:
- Claimant name (first, middle, last)
- Case ID / case number components
- Authorization number
- Date of issue
- DOB
- Applicant (parent/guardian)
- Appointment date and time
- Claimant address (street, city, state, zip)
- Phone number
- Services authorized (CPT codes, descriptions, fees)
- Provider info (assigned to, vendor number, Federal Tax ID)

See: build-plan.md Section 6
"""

import sys
import os
import json
import re
import argparse
from datetime import datetime


def log(msg="", indent=0):
    """Print a log line."""
    prefix = "  " * indent
    print(f"{prefix}{msg}")


def mask_value(val):
    """Mask a value, keeping only the first character and whitespace.

    Example: "John" -> "J***", "Glen Burnie" -> "G*** B*****"
    """
    if not isinstance(val, str) or len(val) <= 1:
        return val
    result = []
    for word in val.split(" "):
        if len(word) <= 1:
            result.append(word)
        else:
            result.append(word[0] + "*" * (len(word) - 1))
    return " ".join(result)


def mask_result(obj):
    """Recursively mask all string values in a dict/list structure."""
    if isinstance(obj, str):
        return mask_value(obj)
    elif isinstance(obj, list):
        return [mask_result(item) for item in obj]
    elif isinstance(obj, dict):
        return {key: mask_result(value) for key, value in obj.items()}
    return obj


def find_texts(doc):
    """Get all text items from the document."""
    return doc.get("texts", [])


def find_tables(doc):
    """Get all table items from the document."""
    return doc.get("tables", [])


def get_text_content(item):
    """Get text content from a document item, preferring 'text' over 'orig'."""
    if isinstance(item, dict):
        return item.get("text", "") or item.get("orig", "")
    return ""


def extract_header_block(texts):
    """Extract fields from the appointment authorization header block.

    Looks for the text block containing: Date: ... Case ID: ... RE: ... DOB: ... Applicant: ... Authorization #: ...
    """
    fields = {}

    for item in texts:
        text = get_text_content(item)
        if "Case ID:" in text and "Authorization #:" in text:
            # Full header block
            m = re.search(
                r"Date:\s*(.+?)\s*Case ID:\s*(.+?)\s*RE:\s*(.+?)\s*DOB:\s*(.+?)\s*Applicant:\s*(.+?)\s*Authorization #:\s*(\S+)",
                text,
            )
            if m:
                fields["date_of_issue"] = m.group(1).strip()
                fields["case_id"] = m.group(2).strip()

                # Parse RE: field into name components
                re_name = m.group(3).strip()
                name_parts = re_name.split()
                if len(name_parts) >= 3:
                    fields["first_name"] = name_parts[0]
                    fields["middle_name"] = " ".join(name_parts[1:-1])
                    fields["last_name"] = name_parts[-1]
                elif len(name_parts) == 2:
                    fields["first_name"] = name_parts[0]
                    fields["middle_name"] = ""
                    fields["last_name"] = name_parts[1]
                else:
                    fields["claimant_name_raw"] = re_name

                fields["dob"] = m.group(4).strip()
                fields["applicant_name"] = m.group(5).strip()
                fields["authorization_number"] = m.group(6).strip()

            log(f"Found header block with Case ID and Authorization #", indent=1)
            break

    return fields


def extract_case_number_components(texts):
    """Extract case number components from various text locations."""
    fields = {}

    for item in texts:
        text = get_text_content(item)

        # Footer pattern: CASE-NUMBER/ Assigned NNNN null/ DCPS / ...
        m = re.match(r"^(\S+)/\s*Assigned\s+(\d+)\s+null/\s*DCPS\s*/\s*(\S+)", text)
        if m:
            fields["case_number_full_footer"] = m.group(1).strip()
            fields["assigned_code"] = m.group(2).strip()
            fields["dcc_number"] = m.group(3).strip()

        # "Case Number:" label followed by value
        if text.strip().startswith("Case Number:"):
            # The next text item might have the value, or it might be inline
            pass  # handled by header block

    return fields


def extract_table_fields(tables):
    """Extract fields from the appointment authorization table.

    The table has 3 cells:
    - Cell 0: Claimant Information (name, address, city, state, zip, phone)
    - Cell 1: Date and Time (appointment date/time)
    - Cell 2: Services Authorized (CPT codes, descriptions, fees)
    """
    fields = {}
    services = []

    for table in tables:
        data = table.get("data", {})
        cells = data.get("table_cells", [])

        for cell in cells:
            cell_text = cell.get("text", "")

            # Cell 0: Claimant Information
            if cell_text.startswith("Claimant Information"):
                fields["claimant_info_raw"] = cell_text
                _parse_claimant_cell(cell_text, fields)

            # Cell 1: Date and Time
            elif "Date and Time" in cell_text:
                fields["appointment_raw"] = cell_text
                _parse_appointment_cell(cell_text, fields)

            # Cell 2: Services Authorized
            elif "Services Authorized" in cell_text or "Code:" in cell_text:
                fields["services_raw"] = cell_text
                services = _parse_services_cell(cell_text)

    if services:
        fields["services"] = services

    return fields


def _parse_claimant_cell(text, fields):
    """Parse the claimant information cell.

    Expected format (real data):
        Claimant Information FIRST MIDDLE LAST 123 STREET CITY, ST 12345 555-123-4567
    Sanitized format:
        Claimant Information {{{FIRST-NAME}}} ... {{{CITY}}}, {{{STATE}}}{{{ZIP-CODE}}} {{{PHONE-NUMBER}}}
    """
    # Remove "Claimant Information" prefix
    text = text.replace("Claimant Information", "").strip()

    # Try to extract phone (real digits or sanitized placeholder)
    phone_match = re.search(r"(\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4})\s*$", text)
    if phone_match:
        fields["phone"] = phone_match.group(1).strip()
        text = text[:phone_match.start()].strip()

    # Try to extract city, state, zip pattern (real addresses)
    addr_match = re.search(r"(.+?),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)", text)
    if addr_match:
        before_city = text[:addr_match.start()].strip()
        fields["city"] = addr_match.group(1).strip().split()[-1] if addr_match.group(1).strip() else None
        fields["state"] = addr_match.group(2).strip()
        fields["zip_code"] = addr_match.group(3).strip()

        # Separate name from street address
        # Name is typically the first line of words, street address follows
        words = before_city.split()
        if len(words) >= 4:
            # Heuristic: street addresses start with a number
            for i, word in enumerate(words):
                if i > 0 and word[0].isdigit():
                    fields["claimant_name_table"] = " ".join(words[:i])
                    fields["street_address"] = " ".join(words[i:])
                    break
            else:
                fields["claimant_name_table"] = before_city


def _parse_appointment_cell(text, fields):
    """Parse the appointment date and time cell."""
    text = text.replace("Date and Time", "").strip()

    # Try to extract date pattern (e.g., "Thursday September 5th, 2024")
    date_match = re.search(
        r"((?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\s+"
        r"\w+\s+\d{1,2}(?:st|nd|rd|th)?,?\s*\d{4})",
        text,
    )
    if date_match:
        fields["appointment_date"] = date_match.group(1).strip()

    # Try to extract time (e.g., "10:00 AM")
    time_match = re.search(r"(\d{1,2}:\d{2}\s*(?:AM|PM))", text)
    if time_match:
        fields["appointment_time"] = time_match.group(1).strip()

    # Timezone
    tz_match = re.search(r"(Eastern\s+\w+\s+Time|E[SD]T)", text)
    if tz_match:
        fields["appointment_timezone"] = tz_match.group(1).strip()


def _parse_services_cell(text):
    """Parse the services authorized cell into a list of service dicts.

    Only Code is required. All other fields are optional:
        Code: {cpt_code} [Procedure Type Code: {type}] [Desc: {description}] [Fee: ${amount}]
    """
    services = []

    # Split on each "Code:" that is NOT preceded by "Type "
    # This gives us one chunk per service entry
    chunks = re.split(r"(?<!\bType )(?=Code:\s*)", text)

    for chunk in chunks:
        chunk = chunk.strip()
        if not chunk.startswith("Code:"):
            continue

        service = {}

        code_match = re.match(r"Code:\s*(\S+)", chunk)
        if not code_match:
            continue
        service["cpt_code"] = code_match.group(1)

        proc_match = re.search(r"Procedure Type Code:\s*(\S+)", chunk)
        if proc_match:
            service["procedure_type_code"] = proc_match.group(1)

        desc_match = re.search(r"Desc:\s*(.+?)(?=\s+Fee:|\s+Code:|$)", chunk)
        if desc_match:
            service["description"] = desc_match.group(1).strip()

        fee_match = re.search(r"Fee:\s*\$\s*([\d,.]+)", chunk)
        if fee_match:
            service["fee"] = fee_match.group(1)

        services.append(service)

    return services


def extract_invoice_fields(texts):
    """Extract fields from the invoice section (page 3)."""
    fields = {}

    for item in texts:
        text = get_text_content(item)

        # Vendor/Tax ID
        m = re.search(r"Federal Tax ID Number:\s*(\d+)", text)
        if m:
            fields["federal_tax_id"] = m.group(1)

        # Vendor number
        m = re.search(r"Vendor Number:\s*(\S+)", text)
        if m:
            fields["vendor_number"] = m.group(1)

        # Authorization number (standalone text item or labeled)
        m = re.search(r"Authorization Number:\s*(\S+)", text)
        if m:
            fields["authorization_number_invoice"] = m.group(1)

        # Request ID (RQID in barcode section)
        m = re.search(r"RQID:(\S+)", text)
        if m:
            fields["request_id"] = m.group(1)

        # Pay to
        if "Pay to:" in text:
            m = re.search(r"Pay to:\s*(.+)", text, re.DOTALL)
            if m:
                fields["pay_to"] = " ".join(m.group(1).split())

        # "On: ... At: ..." pattern
        m = re.search(r"On:\s*(.+?)\s+At:\s*(.+?)$", text, re.MULTILINE)
        if m:
            fields["scheduled_date"] = m.group(1).strip()
            fields["scheduled_time"] = m.group(2).strip()

    return fields


def extract_phone(texts):
    """Extract phone number from CELL # pattern."""
    for item in texts:
        text = get_text_content(item)
        m = re.search(r"CELL\s*#\s*(\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4})", text)
        if m:
            return m.group(1).strip()
    return None


def extract_fields(data):
    """Extract all target fields from a Docling JSON structure."""
    doc = data.get("document", {})
    texts = find_texts(doc)
    tables = find_tables(doc)

    log(f"Document has {len(texts)} text items, {len(tables)} tables", indent=1)

    # Extract from each source
    header_fields = extract_header_block(texts)
    case_fields = extract_case_number_components(texts)
    table_fields = extract_table_fields(tables)
    invoice_fields = extract_invoice_fields(texts)
    phone = extract_phone(texts)

    # Merge all fields (header takes priority, then table, then invoice)
    extracted = {}
    extracted.update(invoice_fields)
    extracted.update(table_fields)
    extracted.update(header_fields)
    extracted.update(case_fields)

    if phone and "phone" not in extracted:
        extracted["phone"] = phone

    # Build a clean summary of the most important fields
    summary = {
        "claimant_first_name": extracted.get("first_name"),
        "claimant_middle_name": extracted.get("middle_name"),
        "claimant_last_name": extracted.get("last_name"),
        "case_id": extracted.get("case_id"),
        "authorization_number": extracted.get("authorization_number"),
        "request_id": extracted.get("request_id"),
        "date_of_issue": extracted.get("date_of_issue"),
        "dob": extracted.get("dob"),
        "applicant_name": extracted.get("applicant_name"),
        "appointment_date": extracted.get("appointment_date"),
        "appointment_time": extracted.get("appointment_time"),
        "street_address": extracted.get("street_address"),
        "city": extracted.get("city"),
        "state": extracted.get("state"),
        "zip_code": extracted.get("zip_code"),
        "phone": extracted.get("phone"),
        "services": extracted.get("services", []),
        "federal_tax_id": extracted.get("federal_tax_id"),
        "vendor_number": extracted.get("vendor_number"),
    }

    # Count how many fields were extracted
    filled = sum(1 for v in summary.values() if v is not None and v != [] and v != "")
    total = len(summary)
    log(f"Extracted {filled}/{total} fields", indent=1)

    return {
        "summary": summary,
        "all_fields": extracted,
    }


def pdf_to_docling_shape(pdf_path, pages=None):
    """Extract text from a PDF using pdfplumber and shape it into the same
    structure that the Docling JSON extraction functions expect.

    Args:
        pdf_path: Path to the PDF file.
        pages: Optional list of 1-based page numbers to extract. Default: all pages.
    """
    import pdfplumber

    log(f"Opening PDF with pdfplumber: {pdf_path}")
    texts = []
    tables_data = []

    with pdfplumber.open(pdf_path) as pdf:
        total_pages = len(pdf.pages)
        target_pages = pages if pages else list(range(1, total_pages + 1))
        log(f"PDF has {total_pages} pages, extracting: {target_pages}", indent=1)

        for page_num in target_pages:
            if page_num < 1 or page_num > total_pages:
                continue
            page = pdf.pages[page_num - 1]

            # Extract text as a single block per page
            page_text = page.extract_text()
            if page_text:
                texts.append({
                    "text": page_text,
                    "orig": page_text,
                    "label": "text",
                    "prov": [{"page_no": page_num}],
                })

            # Extract tables
            for table in page.extract_tables():
                if not table or not table[0]:
                    continue
                cells = []
                for row_idx, row in enumerate(table):
                    for col_idx, cell_text in enumerate(row):
                        if cell_text:
                            cells.append({
                                "text": cell_text,
                                "start_row_offset_idx": row_idx,
                                "end_row_offset_idx": row_idx + 1,
                                "start_col_offset_idx": col_idx,
                                "end_col_offset_idx": col_idx + 1,
                            })
                tables_data.append({
                    "data": {"table_cells": cells},
                    "prov": [{"page_no": page_num}],
                })

    log(f"Extracted {len(texts)} text blocks, {len(tables_data)} tables", indent=1)

    # Shape into Docling-compatible structure
    return {
        "source_file": os.path.basename(pdf_path),
        "tool": "pdfplumber",
        "document": {
            "texts": texts,
            "tables": tables_data,
        },
    }


def extract_file(file_path, mask=False):
    """Extract fields from a PDF or Docling JSON file."""
    log(f"Reading: {file_path}")

    if file_path.lower().endswith(".pdf"):
        data = pdf_to_docling_shape(file_path, pages=[3, 4])
    else:
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)

    result = extract_fields(data)
    result["source_file"] = data.get("source_file", os.path.basename(file_path))
    result["extracted_at"] = datetime.now().isoformat()

    if mask:
        result["summary"] = mask_result(result["summary"])
        result["all_fields"] = mask_result(result["all_fields"])
        result["masked"] = True
        log("Applied masking to output", indent=1)

    # Save extracted fields
    base, _ = os.path.splitext(file_path)
    out_path = f"{base}-extracted.json"

    log(f"Writing: {out_path}")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, ensure_ascii=False)

    return out_path


def main():
    parser = argparse.ArgumentParser(
        description="Extract structured fields from SSA referral Docling JSON output.",
    )
    parser.add_argument("files", nargs="+", help="PDF or Docling JSON file(s) to extract from")
    parser.add_argument("--mask", action="store_true", help="Mask extracted values (keep first/last char, replace middle with asterisks)")
    args = parser.parse_args()

    print()
    print("=" * 60)
    log("SSA Referral Field Extractor")
    log(f"Files to process: {len(args.files)}")
    print("=" * 60)

    results = []
    failures = []

    for idx, file_path in enumerate(args.files, start=1):
        file_path = file_path.strip('"').strip("'")
        file_path = os.path.abspath(file_path)

        print()
        log(f"--- File {idx}/{len(args.files)} ---")

        if not os.path.isfile(file_path):
            log(f"ERROR: File not found: {file_path}")
            failures.append(file_path)
            continue

        try:
            out_path = extract_file(file_path, mask=args.mask)
            results.append(out_path)

            # Print summary for quick review
            with open(out_path, "r") as f:
                extracted = json.load(f)
            summary = extracted.get("summary", {})
            print()
            log("Summary:", indent=1)
            for key, val in summary.items():
                if val is not None and val != [] and val != "":
                    display = json.dumps(val) if isinstance(val, list) else str(val)
                    log(f"{key}: {display}", indent=2)

        except Exception as e:
            log(f"ERROR: {type(e).__name__}: {e}")
            failures.append(file_path)

    print()
    print("=" * 60)
    log("BATCH COMPLETE")
    log(f"Processed: {len(results)}/{len(args.files)} succeeded")

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
