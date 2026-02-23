---
type: knowledge
status: active
created: 2026-02-16
updated: 2026-02-17
---

# PDF Data Extraction Technology Stack (PHI-Compliant)

Research into automated extraction of structured data from government referral PDFs, with all processing local (no cloud APIs) per HIPAA/PHI requirements.

---

## Context

Target: SSA/DDS consultative examination referral PDFs (40-50/week) from the ELRQ portal. These are government-generated forms with consistent layouts — ideal for template-based deterministic extraction.

---

## Recommended Stack

### Primary (Deterministic — 95%+ of PDFs)

| Component | Tool | License | Role |
|---|---|---|---|
| PDF text extraction | **pdfplumber** 0.11.x | MIT | Coordinate-based template extraction |
| Template definition | Custom JSON schema | N/A | Field locations, validation rules |
| XLSX output | **openpyxl** 3.1.x | MIT | Read existing spreadsheet structure, write output |
| Validation | Python `re` + custom rules | stdlib | Regex patterns, required field checks |

### Fallback (Local LLM — 0-5% of PDFs)

| Component | Tool | License | Role |
|---|---|---|---|
| LLM inference | **Ollama** | MIT | Serve models locally on localhost:11434 |
| Extraction model | **NuExtract** (3.8B) | Apache 2.0 | Structured extraction with JSON templates |
| OCR (scanned PDFs) | **Tesseract** 5.4.0 | Apache 2.0 | Already installed; likely not needed for ELRQ PDFs |

---

## Why pdfplumber

- **`.crop(bounding_box)`** — extract text from specific (x0, top, x1, bottom) region
- **`.extract_words()`** — individual words with bounding boxes
- **`.chars`** — every character with font, size, position
- **`.lines` / `.rects`** — detect form structure (borders, boxes)
- **`extract_tables()`** — built-in table detection
- **Visual debugging** — render pages with extracted elements highlighted
- Built on pdfminer.six (low-level access available)
- MIT licensed, actively maintained (Jeremy Singer-Vine, ProPublica)

---

## Template-Based Extraction

JSON config maps field names to bounding box coordinates:

```json
{
    "form_type": "SSA_DDS_CE_REFERRAL",
    "fields": {
        "patient_name": {
            "page": 0,
            "bbox": [72, 150, 300, 170],
            "type": "text",
            "required": true
        },
        "po_number": {
            "page": 0,
            "bbox": [400, 150, 550, 170],
            "type": "text",
            "pattern": "^[A-Z0-9-]+$"
        }
    }
}
```

**Calibration process** (Analysis phase, Days 1-3):
1. Open sample PDF with pdfplumber visual debugging
2. Use `.lines`, `.rects`, `.chars` to identify form structure
3. Define bounding boxes for each extraction field
4. Validate against 3-5 samples for coordinate consistency
5. Add regex patterns for field validation

---

## Processing Pipeline

```
PDFs in → Detect native/scanned (text length heuristic)
  → Native: pdfplumber template extraction
  → Scanned: Tesseract OCR → then template extraction
  → Validate fields (regex, required checks, data types)
  → If validation passes → direct to output
  → If validation fails → Ollama/NuExtract fallback
  → openpyxl → XLSX output
```

### Performance Estimates (consumer Windows PC)

| Scenario | Time/PDF | Batch of 50 |
|---|---|---|
| Native text, deterministic | 0.1-0.3 sec | 5-15 sec |
| Native text, LLM fallback | 3-8 sec | per-PDF only |
| Scanned, OCR + extraction | 5-15 sec | rare |

---

## Local LLM Details

### NuExtract

Purpose-built for structured information extraction. Accepts input text + JSON template, returns structured JSON output.

| Variant | Parameters | VRAM | Notes |
|---|---|---|---|
| NuExtract-tiny-v1.5 | 0.5B | ~1 GB | Runs anywhere, less accurate |
| NuExtract (base) | 3.8B | ~3-4 GB | Good balance |
| NuExtract-large | 7B | ~6-8 GB | Best accuracy |

**Critical**: Run with temperature=0 (extractive model, not generative).

### Ollama Hardware Requirements

| GPU VRAM | Model Size | Speed |
|---|---|---|
| 8 GB | 7-8B (Q4) | ~40 tok/sec |
| 6 GB | 3-4B | ~50-60 tok/sec |
| 4 GB or less | 0.5-1.7B | ~30-40 tok/sec |
| CPU only | 0.5-1.7B | ~5-15 tok/sec |

---

## Library Comparison

| Library | Text | Tables | Coordinates | Speed | License | Verdict |
|---|---|---|---|---|---|---|
| **pdfplumber** | Excellent | Excellent | Yes (`.crop()`) | Moderate | MIT | **Primary choice** |
| **Docling** | Excellent | Excellent (AI) | Yes | Slower (model inference) | MIT | Alternative for varied layouts — see [dedicated entry](docling-pdf-processing.md) |
| PyMuPDF (fitz) | Excellent | Good | Yes | Fastest | **AGPL** | Avoid (license issue for source delivery) |
| Camelot | Basic | Excellent | Limited | Moderate | MIT | Backup for complex tables |
| tabula-py | Basic | Good | Limited | Moderate | MIT | Skip (JVM dependency) |
| pdfminer.six | Good | None | Yes | Slow | MIT | Already included via pdfplumber |
| Apache Tika | Good | Moderate | Limited | Heavy | Apache 2.0 | Skip (JVM dependency) |
| borb | Moderate | Moderate | Yes | Slow | Various | Skip (small community) |

---

## Alternative Approach: Docling

IBM's Docling library takes an AI-driven approach to PDF extraction using local models (TableFormer for tables, Granite-Docling for document understanding). Same MIT license and local-only processing as pdfplumber, but uses machine learning instead of coordinate-based templates. Better for varied layouts; pdfplumber is better for uniform government forms at high volume. See [Docling PDF Processing](docling-pdf-processing.md) for full comparison.

---

## PHI Compliance

| Tool | Telemetry | PHI Safe | Notes |
|---|---|---|---|
| pdfplumber | None | Yes | Pure Python, no network |
| openpyxl | None | Yes | Pure file I/O |
| Tesseract | None | Yes | Local executable |
| Ollama | None by default | Yes (verify config) | Ensure localhost-only binding |
| NuExtract | None | Yes | Local model weights |

### Safeguards

- No logging of extracted PHI values (log file names and error counts only)
- Delete intermediate files (OCR images, text buffers) after processing
- Output in same directory as source PDFs (keeps PHI contained)
- Ollama bound to localhost:11434 only (not 0.0.0.0)
- Pin all dependency versions; audit transitive dependencies
- Windows BitLocker for disk encryption

---

## Applied In

- [Smith & Calabrese Consulting](/work/projects/smith-calabrese-consulting/) — Phase 1 PDF referral parser

---

## Sources

Research conducted 2026-02-16. Key sources: pdfplumber GitHub/docs, arXiv:2410.09871 (PDF parsing benchmark), PyMuPDF benchmarks, Ollama/NuExtract documentation, Unstract library evaluations, multiple trade and community reviews.

---

*Last updated: 2026-02-17*
