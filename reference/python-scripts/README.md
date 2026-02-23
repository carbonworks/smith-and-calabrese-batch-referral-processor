# Python Reference Scripts

These are the Python prototype scripts from the analysis phase of the S&C Phase 1 engagement. They were used to validate PDF extraction feasibility and develop the field extraction logic.

**These are reference implementations, not production code.** The production application is being built in Kotlin/Compose Multiplatform. These scripts are preserved here as extraction logic reference during the Kotlin port.

## Scripts

| Script | Purpose |
|--------|---------|
| `extract_referral_fields.py` | Core field extraction — regex-based parsing of SSA referral PDF text into structured JSON |
| `dump_pdf.py` | pdfplumber extraction — dumps raw PDF structure (text, tables, metadata) to JSON |
| `dump_pdf_docling.py` | Docling extraction — AI-driven document conversion to JSON |
| `sanitize_extraction.py` | PHI sanitization — strips patient data, replaces with placeholders |
| `trim_docling_pages.py` | Utility — removes pages 5+ from Docling JSON output |
| `clean_docling_output.py` | Utility — cleans/simplifies Docling JSON for analysis |

## Dependencies (not installed in this project)

```
pdfplumber>=0.11.0
docling
openpyxl>=3.1.0
```

## Usage

These scripts are **not meant to be run** from this repository. They require:
- Python 3.13+
- The pip packages listed above
- Access to SSA referral PDF files (PHI — not in this repository)

To run them for reference/testing, set up a Python virtual environment separately.
