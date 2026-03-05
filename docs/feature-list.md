# S&C Batch Referral Processor — Feature List

## PDF Processing
- **Batch PDF intake** — select and process 1–50 SSA/DDS referral PDFs in a single run
- **Text extraction** — extract raw text with positional/coordinate data using Apache PDFBox
- **Table extraction** — detect and extract structured table data from PDF pages using Tabula-java
- **OCR fallback** — handle scanned/image-only pages via Tesseract (Tess4J)
- **Per-file error resilience** — one failed PDF never aborts the batch; errors are captured and reported per file

## Field Extraction
- **Regex-based field parsing** — extract SSN, DOB, dates, case/claim numbers, claimant names, exam types, referring agency, and other structured fields
- **Coordinate-based field identification** — locate fields by position on the page using configurable templates
- **Multi-source field merging** — combine results from header blocks, tables, invoice sections, and fallback patterns with defined priority
- **Confidence scoring** — flag low-confidence extractions for manual review
- **Graceful partial extraction** — extract what's available, flag what's missing

## Spreadsheet Output
- **XLSX generation** — one row per referral, proper column headings, typed data (dates as dates, numbers as numbers)
- **Auto-named output** — `patient-referrals-[date]-[time].xlsx`, saved alongside the source PDFs
- **Google Sheets compatible** — no Excel-only features; preserves layout on import

## Desktop UI
- **File picker** — native file selection dialog for choosing PDFs
- **Drag-and-drop** — drop PDFs directly onto the application window
- **Processing progress** — real-time per-file status during batch extraction
- **Data preview** — review all extracted fields before saving to spreadsheet
- **PHI masking** — all sensitive fields masked by default in the UI; toggle to reveal
- **Settings screen** — configure PHI display defaults and preferences
- **Carbon Works branding** — warm, clean design following CW brand guidelines (Deep Ink, Warm White palette; Inter typography)

## PHI Safety
- **Default-masked display** — PHI is hidden on launch; user must explicitly reveal
- **XLSX output always unmasked** — spreadsheet contains raw values regardless of UI masking state
- **No PHI in logs** — console output logs filenames and status only, never field values
- **Fully offline** — all processing is local; no network calls from any library

## Packaging & Distribution
- **Windows .msi installer** — bundled JRE via jpackage (~60–100 MB, no Java install required)
- **macOS .dmg** (secondary target)
- **Cross-platform codebase** — Kotlin/Compose Multiplatform
