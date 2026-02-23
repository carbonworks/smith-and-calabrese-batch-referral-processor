---
type: plan
status: active
project: smith-calabrese-consulting
goal: launch-consulting
cycle: 2026-02
created: 2026-02-17
updated: 2026-02-17
---

# S&C Phase 1 Build Plan — PDF Referral Parser

Command center for the 2-week engagement. Start here for current status, next steps, and decision gates.

---

## Quick Links

**Project files:**
- [Contract](contract-phase1-pdf-parser.md) — scope, deliverables, terms
- [Solution Analysis](solution-analysis.md) — 3 problem areas, option evaluation
- [PDF Structure Dump Guide](pdf-structure-dump-guide.md) — Tyler's analysis tool
- [Project File](smith-calabrese-consulting.md) — engagement context, timeline, milestones

**Scripts** (in `scripts/`):
- `dump_pdf.py` — pdfplumber extraction (batch, outputs `<name>-pdfplumber.json`)
- `dump_pdf_docling.py` — Docling extraction (batch, outputs `<name>-docling.json`)
- `sanitize_extraction.py` — strip PHI from extraction JSON, flag for manual review

**Analysis templates** (in `analysis/`):
- `field-mapping.json` — field names → extraction coordinates/rules (skeleton)
- `extraction-template.json` — reusable template for SSA referral forms (skeleton)
- `library-comparison-notes.md` — side-by-side quality assessment (template)

**Knowledge base:**
- [PDF Extraction Stack (PHI-Compliant)](/reference/knowledge/tools-and-services/pdf-extraction-stack-phi.md) — pdfplumber + Ollama pipeline
- [Docling PDF Processing](/reference/knowledge/tools-and-services/docling-pdf-processing.md) — IBM's AI-driven alternative
- [Windows App Framework Comparison](/reference/knowledge/tools-and-services/windows-app-framework-comparison.md) — C# WinForms vs Python+PyQt6
- [Encrypted Vault Storage](/reference/knowledge/tools-and-services/encrypted-vault-storage-phi.md) — PHI file transfer options

**People:**
- [Smith & Calabrese (client)](/people/clients/smith-and-calabrese/smith-and-calabrese.md)
- [Tyler Calabrese (contact)](/people/contacts/tyler-calabrese/tyler-calabrese.md)

---

## 1. Delivery Schedule

| Date | Delivery | Status |
|------|----------|--------|
| **Thu 2/20** | Checkpoint #1 — PDF analysis results, prototype output, field priority questions | [ ] |
| **Mon 2/24** | Checkpoint #2 — working prototype for Tyler to test, spreadsheet format review | [ ] |
| **Fri 2/28** | Checkpoint #3 / Final Delivery — tool, source code, documentation | [ ] |
| **Mon 3/2** | Hard deadline (contract) | |

---

## 2. Work Breakdown

### 0. Environment Setup

Install Python packages (run from command line):
- [ ] `pip install pdfplumber` — primary PDF extraction
- [ ] `pip install docling` — alternative AI-driven extraction
- [ ] `pip install openpyxl` — XLSX output

Verify scripts work (quick smoke test, no PDFs needed):
- [ ] `python scripts/dump_pdf.py` — should print usage and exit
- [ ] `python scripts/dump_pdf_docling.py` — should print usage and exit
- [ ] `python scripts/sanitize_extraction.py` — should print usage and exit

Already installed:
- Python 3.13.12
- Tesseract 5.4.0

Install later (only if needed):
- [ ] **Ollama** — local LLM fallback, only if deterministic extraction fails on some PDFs
- [ ] **.NET 8 SDK** — only if framework decision goes C# WinForms instead of Python

### A. Analysis

**Randall-driven** (PHI files — Claude does not touch, run, or orchestrate):
- [ ] Run `scripts/dump_pdf.py` on all sample PDFs → `<name>-pdfplumber.json` for each
- [ ] Run `scripts/dump_pdf_docling.py` on all sample PDFs → `<name>-docling.json` for each
- [ ] Run `scripts/sanitize_extraction.py` on both sets of JSON → `<name>-sanitized.json`
- [ ] Review sanitized output to confirm no PHI remains (script flags items for review)
- [ ] **Pull sanitized results into intranet** at `/work/projects/smith-calabrese-consulting/analysis/`

**Claude-assisted** (sanitized data only):
- [ ] Evaluate sanitized JSON output: text quality, table detection, field positions, consistency across samples
- [ ] Write library comparison notes (side-by-side quality assessment)
- [ ] **Feasibility gate**: Can fields be reliably extracted? Go/no-go decision
- [ ] Map target fields to extraction coordinates (pdfplumber) or document regions (Docling)
- [ ] Select primary library based on actual results

### B. Prototype

**Claude-assisted** (writes code; Randall runs it against PHI files):
- [ ] Build extraction script using selected primary library
- [ ] Define template JSON — field names → bounding boxes / extraction rules

**Randall-driven** (PHI files):
- [ ] Validate template against all sample PDFs
- [ ] End-to-end test: PDF in → XLSX out

**Claude-assisted** (sanitized output):
- [ ] Build openpyxl XLSX output — column headings, data types, formatting

### C. Tyler Feedback

- [ ] Send Tyler: PDF analysis summary, prototype XLSX output, field priority questions
- [ ] **At Checkpoint #1 delivery, remind Tyler of outstanding items:**
  - Existing tracking spreadsheet (needed for format matching)
  - Desired fields for XLSX output / field priority list
  - Preferred XLSX column layout and order
- [ ] Request existing tracking spreadsheet for format matching
- [ ] Incorporate Tyler's field priorities — "nice to have" vs "must have"
- [ ] Incorporate Tyler's spreadsheet layout — match column order, headers, formatting

### D. Integration

- [ ] Framework decision: C# WPF vs Python + PyQt6 (follows library choice from Phase A)
- [ ] Apply CW branding to tool UI — colors, typography, window chrome (see Section 12)
- [ ] Build user interface — file selection, progress display, preview before save
- [ ] Batch processing — handle 1-50 PDFs in one run
- [ ] Error handling — corrupt PDFs, missing fields, unexpected layouts
- [ ] Edge cases — multi-page referrals, scanned pages (Tesseract fallback)

### E. Documentation & Delivery

- [ ] Technical specification document
- [ ] User guide with screenshots
- [ ] Packaging — installer or distribution zip
- [ ] Final testing on clean machine
- [ ] Deliver to Tyler: tool, source code, tech spec, user guide

---

## 3. Decision Gates

| Gate | Question | Depends On | Decision Maker | Status |
|------|----------|------------|----------------|--------|
| **Feasibility** | Can target fields be reliably extracted from sample PDFs? | Analysis complete | Randall | [ ] Pending |
| **Library Choice** | pdfplumber (deterministic) or Docling (AI-driven) for production? | Dual-library extraction results | Randall | [ ] Pending |
| **Column Mapping** | Which fields go in which columns, and in what order? | Tyler's feedback on Checkpoint #1 | Tyler | [ ] Pending |
| **Framework** | C# WPF or Python + PyQt6? (Follows library choice — same language as PDF library) | Library choice + Tyler's UX feedback | Randall + Tyler input | [ ] Pending |

---

## 4. Blockers & Open Items

| Item | Status | Notes |
|------|--------|-------|
| Sample SSA referral PDFs (3-5) | **Received 2/17** | Ready for analysis |
| Existing tracking spreadsheet | Pending from Tyler | Needed for format matching |
| Field priority list from Tyler | Not yet requested | Will request at Checkpoint #1 |
| Tyler's preferred XLSX column layout | Not yet requested | Will request at Checkpoint #1 |

---

## 5. Deliverables Checklist

| # | Deliverable | Contract Section | Status |
|---|-------------|-----------------|--------|
| 1 | Batch PDF processing tool for Windows | Section 3.2 | [ ] |
| 2 | XLSX output (`patient-referrals-[date]-[time].xlsx`) | Section 3.3 | [ ] |
| 3 | Spreadsheet formatted for Google Sheets migration | Section 3.4 | [ ] |
| 4 | Full source code | Section 3.5 | [ ] |
| 5 | Technical specification document | Section 3.6 | [ ] |
| 6 | Written user guide | Section 3.7 | [ ] |

**Post-delivery**: 90-day warranty (Section 7) — bug fixes, minor adjustments, format changes.

---

## 6. Dual-Library Extraction & Reuse Strategy

### Why Both Libraries

Running pdfplumber AND Docling on the same sample PDFs serves three purposes:

1. **Informed decision**: Compare actual extraction results, not just feature lists
2. **Fallback readiness**: If the primary library struggles with certain layouts, the alternative is already tested
3. **Reusable reference data**: Sanitized extraction results stored in the intranet become a template for future clients with similar government PDF extraction needs

### Sanitization Protocol

Before pulling extraction results into the intranet:

1. Strip all PHI fields: patient names, SSNs, DOBs, addresses, phone numbers, diagnosis codes
2. Preserve structural data: field positions, bounding boxes, font info, table layouts, page dimensions
3. Replace PHI values with type-labeled placeholders: `[PATIENT_NAME]`, `[SSN]`, `[DOB]`, etc.
4. Review output manually before committing

### Intranet Storage

```
/work/projects/smith-calabrese-consulting/analysis/
  ├── pdfplumber-extraction-sample1.json    (sanitized)
  ├── pdfplumber-extraction-sample2.json    (sanitized)
  ├── docling-extraction-sample1.json       (sanitized)
  ├── docling-extraction-sample2.json       (sanitized)
  ├── field-mapping.json                    (field names → coordinates/rules)
  ├── library-comparison-notes.md           (side-by-side quality assessment)
  └── extraction-template.json              (reusable template for SSA referral forms)
```

### Reuse for Future Clients

If another client needs SSA/government PDF extraction:
- The sanitized structure data shows exactly what these PDFs look like internally
- The field mapping and extraction template can be adapted
- The library comparison notes inform tooling decisions without re-running the analysis
- The build plan itself serves as a project template

---

## 7. Contract Summary

### Scope

**Included** (Section 3):
1. Analysis of sample referral PDFs — fields, structure
2. Batch PDF processing tool for Windows (one or multiple PDFs)
3. XLSX output: `patient-referrals-[date]-[time].xlsx` in same directory as source PDFs
4. Formatted for Google Sheets migration
5. Full source code delivery
6. Technical specification document
7. Written user guide
8. 90-day post-delivery warranty

**Not included** (Section 3):
1. Direct Google Sheets integration
2. Portal automation or web scraping
3. Non-SSA referral documents
4. Ongoing maintenance beyond 90-day warranty

### Client Responsibilities (Section 4)

- [ ] 3-5 sample SSA referral PDFs
- [ ] Copy of existing tracking spreadsheet (can be sanitized)
- [ ] Specify desired fields for XLSX output
- [ ] Designate point of contact (Tyler)
- [ ] Feedback on initial output format before final delivery

### Fee & Payment

| Item | Amount |
|------|--------|
| Standard rate | $1,000 |
| First-engagement rate | **$500** |
| Payment terms | Due upon delivery and acceptance |

### Warranty (Section 7)

90 days from delivery: bug fixes, usage adjustments, format changes (with sample PDFs). Post-warranty: hourly rate, billed in 30-minute increments.

---

## 8. Technical Architecture

> **Update (2026-02-23)**: The Kotlin/Compose Multiplatform decision supersedes the Python/Tauri/WPF options below. Production stack: PDFBox + Tabula-java + Apache POI + Compose Multiplatform. The Python tools and framework evaluation below are preserved as historical context. See the [tech stack decision section](smith-calabrese-consulting.md#tech-stack-decision-2026-02-23) and the development project at `/home/rmdev/projects/smith-and-calabrese-batch-referral-processor/`.

### Primary: pdfplumber (deterministic extraction)

Best for uniform government forms. Template-based coordinate extraction with regex validation.

| Component | Tool | License |
|---|---|---|
| PDF text extraction | pdfplumber 0.11.x | MIT |
| Template definition | Custom JSON schema | N/A |
| XLSX output | openpyxl 3.1.x | MIT |
| Validation | Python `re` + custom rules | stdlib |

### Alternative: Docling (AI-driven extraction)

Better if PDFs have more layout variation than expected. TableFormer for table detection, Granite-Docling for document understanding. All local, no cloud.

| Component | Tool | License |
|---|---|---|
| Document conversion | Docling | MIT |
| Table extraction | TableFormer (bundled) | Permissive (verify) |
| Document understanding | Granite-Docling (bundled) | Apache 2.0 (verify) |

### Fallback: Ollama + NuExtract (local LLM)

For PDFs where deterministic extraction fails. NuExtract (3.8B params) accepts text + JSON template, returns structured JSON. Runs on localhost:11434.

### UI Framework (Client-Facing Shell)

The UI is a thin shell that invokes the Python extraction script and displays results. It does NOT do PDF processing — the extraction engine is always Python regardless of UI choice.

| Framework | Theming | Data Grid | Theme Swap | Installer | Maintainability |
|---|---|---|---|---|---|
| **Tauri** (Rust + HTML/CSS/JS) | Excellent (CSS) | Very Good (HTML/JS table) | Excellent (CSS vars) | **2-8 MB** | Excellent (HTML/CSS/JS — widest talent pool) |
| **C# WPF** (.NET 8) | Excellent (XAML ResourceDictionary) | Excellent (built-in DataGrid) | Excellent (swap XAML dicts) | 25-50 MB | Moderate (XAML/C# — narrower pool) |

**Primary: Tauri** — CSS is the best tool for expressing the CW brand spec. 2-8 MB installer signals craftsmanship. HTML/CSS/JS is the most maintainable codebase for any future developer Tyler might hire. Rust backend is ~20-30 lines of boilerplate (invoke Python, return JSON).

**Alternative: WPF** — Best built-in DataGrid, most structured native theming. Choose if Rust toolchain is unwanted or client's future developers are likely .NET.

**Eliminated**: WinForms (no theming system), Electron (same as Tauri but 80-150 MB), customtkinter (no data grid), ttkbootstrap (theming ceiling too low for client deliverable), PyQt6 (harder to theme than CSS, worse grid than WPF, larger than Tauri). See [Windows App Framework Comparison](/reference/knowledge/tools-and-services/windows-app-framework-comparison.md) for full evaluation.

---

## 9. PHI Compliance

### Contract Requirements (Section 9)

- **Purpose limitation**: PHI used solely for developing and testing the tool
- **Safeguards**: Encrypted, password-protected devices only. No cloud, no third parties, no unencrypted transmission
- **Return/destruction**: Permanently delete all PHI copies at project completion; confirm in writing
- **Breach notification**: 72 hours
- **BAA**: Available if client requests (not yet requested)

### AI Assistant Constraint

Claude **must not be involved in any process that touches PHI files** — not reading, running scripts against, or orchestrating workflows involving them. Even if Claude wouldn't upload file contents, having Claude drive the process means Claude is making decisions about PHI handling. **That decision authority stays with Randall** as long as files contain PHI.

**Boundary rule**: Randall drives all PHI-touching processes. Claude's involvement begins only after Randall has sanitized the output and confirmed it's clean.

The workflow is:

1. **Randall** runs extraction tools locally on his machine — his decision, his process
2. **Randall** sanitizes output (strip PHI, replace with placeholders)
3. **Randall** reviews and confirms no PHI remains
4. **Claude** receives sanitized structural data (field positions, bounding boxes, table layouts) and assists with code, architecture, documentation

### Local Workflow Safeguards

| Tool | Network Access | PHI Exposure |
|---|---|---|
| pdfplumber | None (pure Python) | Local only |
| Docling | None (local models) | Local only |
| openpyxl | None (file I/O) | Local only |
| Tesseract | None (local exe) | Local only |
| Ollama | localhost:11434 only | Local only |

Additional:
- Windows BitLocker enabled for disk encryption
- No logging of extracted PHI values (log file names and error counts only)
- Delete intermediate files after processing
- Output placed in same directory as source PDFs (keeps PHI contained)
- Pin all dependency versions; audit transitive dependencies

---

## 10. Risk Register

| # | Risk | Likelihood | Severity | Mitigation | Status |
|---|------|-----------|----------|------------|--------|
| 1 | PDFs are scanned images, not native text | Low | High | Tesseract OCR fallback; feasibility gate | Monitoring |
| 2 | PDFs are encrypted/DRM-protected | Low | High | Feasibility gate; contract Section 8 allows termination | Monitoring |
| 3 | PDF layout varies significantly across samples | Medium | Medium | Docling as alternative; dual-library extraction informs decision | Monitoring |
| 4 | Tyler delays on feedback | Medium | High | Proactive follow-up; delivery dates shift but hard deadline holds | Monitoring |
| 5 | Framework choice adds unexpected complexity | Low | Medium | Defer decision until extraction is working; simplest option wins | Monitoring |
| 6 | Extraction accuracy insufficient for production use | Low | High | Confidence scoring per field; manual review flag for low-confidence extractions | Monitoring |

---

## 11. Deliverable Specifications

### D1: Batch PDF Processing Tool

- Windows executable or script (depending on framework decision)
- Accepts 1-50 PDFs via context menu, SendTo, drag-drop, or file picker
- Processes all selected PDFs in sequence
- Shows progress during processing
- Preview extracted data before saving

### D2: XLSX Output

- Filename: `patient-referrals-[date]-[time].xlsx`
- Saved in same directory as source PDFs
- One row per PDF / referral
- Column headings matching agreed field list
- Proper data types (dates as dates, numbers as numbers)

### D3: Google Sheets Migration Format

- XLSX formatted so copy-paste or import into Google Sheets preserves column layout
- No Excel-only features (macros, data validation) that break in Sheets
- Column order matches client's existing tracking spreadsheet

### D4: Source Code

- Complete, commented Python (and/or C#) source
- `requirements.txt` or `.csproj` with pinned dependencies
- README with build/run instructions

### D5: Technical Specification

- Architecture overview (extraction pipeline, fallback chain)
- Field mapping documentation (which field → which PDF region)
- Dependency list with versions and licenses
- Known limitations and edge cases
- Maintenance notes (what to change if PDF format changes)

### D6: User Guide

- Step-by-step usage instructions with screenshots
- Installation/setup (one-time)
- Processing PDFs (daily workflow)
- Troubleshooting common issues
- How to update if PDF format changes (point to tech spec)

## 12. Tool Branding

S&C branding was offered to Tyler but not followed up on. Default to **Carbon Works branding** for the delivered tool. If Tyler later provides S&C branding assets, the theme can be swapped.

Source: [Carbon Works Brand Guidelines](/org/brand/carbon-works-brand-guidelines.md)

### Color Palette (Tool UI)

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary | Deep Ink | `#2D3748` | Window title bar, headings, primary buttons |
| Secondary text | Soft Gray | `#718096` | Labels, status text, secondary elements |
| Borders/structure | Light Gray | `#E2E8F0` | Table borders, dividers, subtle backgrounds |
| Background | Warm White | `#FFFAF5` | Main window background (paper-like warmth) |
| Content areas | Clean White | `#FFFFFF` | Data grid, input fields, cards |
| Interactive | Sky Blue | `#4A9FD4` | Links, active buttons, selection highlights |
| Success | Soft Teal | `#38B2AC` | Success messages, completion indicators |
| Accent | Paper Tan | `#D4A574` | Sparingly — progress bars, warm highlights |

### Typography

| Context | Font | Fallback |
|---------|------|----------|
| UI labels, headings | Inter Medium | Segoe UI, system sans-serif |
| Body text, data | Inter Regular | Segoe UI, system sans-serif |
| Monospace (paths, filenames) | System monospace | Consolas, Courier New |

### Branding Elements

- **Window title**: "Carbon Works — PDF Referral Parser" (or similar)
- **About/splash**: CW origami bird logo if available, otherwise text-only "Carbon Works"
- **XLSX output**: No branding in spreadsheet data (client's operational document)
- **Documentation**: CW header/footer on tech spec and user guide

### Design Principles (from brand)

- Clean and uncluttered — generous whitespace, clear visual hierarchy
- Warm, not cold — `#FFFAF5` background instead of pure white
- Approachable — no aggressive styling, no flashy animations
- Crafted — intentional, not generic

---

*Last updated: 2026-02-23 — tech stack decision note in Section 8*
