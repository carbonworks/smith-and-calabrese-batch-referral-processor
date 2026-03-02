---
type: plan
status: active
project: smith-calabrese-consulting
goal: launch-consulting
cycle: 2026-02
created: 2026-02-17
updated: 2026-02-23
---

# S&C Phase 1 Build Plan — PDF Referral Parser

Command center for the 2-week engagement. Start here for current status, next steps, and decision gates.

---

## Quick Links

**Project files:**
- [Contract](contract-phase1-pdf-parser.md) — scope, deliverables, terms
- [Solution Analysis](solution-analysis.md) — 3 problem areas, option evaluation
- [Tech Stack Decision](tech-stack-decision.md) — Kotlin/Compose Multiplatform rationale
- [JVM Library Research](research/jvm-pdf-extraction-research.md) — PDFBox, Tabula-java, POI evaluation

**Reference:**
- `reference/python-scripts/` — Python prototype scripts (extraction logic reference, not production code)
- `reference/sample-output/` — Sanitized extraction output samples

**Analysis templates:**
- `field-mapping.json` — field names → extraction coordinates/rules (skeleton)
- `extraction-template.json` — reusable template for SSA referral forms (skeleton)

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

### A. Project Scaffolding

- [ ] Initialize Kotlin/Gradle project with Compose Multiplatform Desktop plugin
- [ ] Add dependencies: PDFBox, Tabula-java, Apache POI, Tess4J
- [ ] Basic Compose window — app shell with CW branding (title, colors, typography)
- [ ] Verify clean build on Windows and macOS

### B. PDF Extraction Engine

Port extraction logic from Python reference scripts (`reference/python-scripts/`) to Kotlin:

- [ ] PDFBox text extraction — `PDFTextStripper` with coordinate filtering
- [ ] Tabula-java table extraction — structured table data
- [ ] Field mapping — coordinate-based field identification using extraction template
- [ ] Regex parsing — SSN, dates, case numbers, names (port from `extract_referral_fields.py`)
- [ ] Confidence scoring — flag low-confidence extractions for manual review

**Randall-driven** (PHI files — Claude does not touch):
- [ ] Run extraction on all sample PDFs, review results
- [ ] Validate field mapping against all samples

### C. XLSX Output

- [ ] Apache POI spreadsheet generation — column headings, one row per referral
- [ ] Data types — dates as dates, numbers as numbers, strings as strings
- [ ] Filename format: `patient-referrals-[date]-[time].xlsx`
- [ ] Output to same directory as source PDFs
- [ ] Google Sheets compatibility — no Excel-only features

### D. Desktop UI

- [ ] File picker — select one or multiple PDFs
- [ ] Batch processing — handle 1-50 PDFs in sequence
- [ ] Progress display — show extraction progress
- [ ] Data preview — review extracted data before saving
- [ ] CW branding — colors, typography, layout per Section 12
- [ ] Drag-and-drop support

### E. Packaging & Delivery

- [ ] jpackage .msi installer for Windows (bundled JRE, ~60-100MB)
- [ ] jpackage .dmg for macOS (secondary)
- [ ] Clean-machine test — install and run on fresh Windows machine
- [ ] Technical specification document
- [ ] User guide with screenshots
- [ ] Deliver to Tyler: installer, source code, tech spec, user guide

---

## 3. Decision Gates

| Gate | Question | Depends On | Decision Maker | Status |
|------|----------|------------|----------------|--------|
| **Feasibility** | Can target fields be reliably extracted from sample PDFs? | Extraction engine tested against samples | Randall | [ ] Pending |
| **Column Mapping** | Which fields go in which columns, and in what order? | Tyler's feedback on Checkpoint #1 | Tyler | [ ] Pending |

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

## 6. Technical Architecture

See [Tech Stack Decision](tech-stack-decision.md) for full rationale and trade-off analysis.

| Component | Library | Purpose |
|-----------|---------|---------|
| PDF text extraction | Apache PDFBox | Core text and coordinate extraction |
| Table extraction | Tabula-java | Structured table data from PDFs |
| XLSX output | Apache POI | Spreadsheet generation |
| OCR fallback | Tess4J | JNA wrapper for Tesseract (scanned pages) |
| Desktop UI | Compose Multiplatform | Cross-platform Windows + macOS |
| Build/packaging | Gradle + jpackage | Build, bundle JRE, create .msi/.dmg |

### Extraction Pipeline

```
PDF → PDFBox (text + coordinates) → Field mapping (template) → Regex parsing → Structured data
  └→ Tabula-java (tables) ──────────┘
  └→ Tess4J (scanned pages) ────────┘
```

### Reference Implementation

The Python prototype scripts in `reference/python-scripts/` contain the extraction logic being ported to Kotlin. Key files:
- `extract_referral_fields.py` — core field extraction with regex parsing (572 lines)
- `dump_pdf.py` — pdfplumber extraction (batch processing)
- `sanitize_extraction.py` — PHI stripping utility

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

## 8. PHI Compliance

### Contract Requirements (Section 9)

- **Purpose limitation**: PHI used solely for developing and testing the tool
- **Safeguards**: Encrypted, password-protected devices only. No cloud, no third parties, no unencrypted transmission
- **Return/destruction**: Permanently delete all PHI copies at project completion; confirm in writing
- **Breach notification**: 72 hours
- **BAA**: Available if client requests (not yet requested)

### AI Assistant Constraint

Claude **must not be involved in any process that touches PHI files** — not reading, running scripts against, or orchestrating workflows involving them. **That decision authority stays with Randall** as long as files contain PHI.

**Boundary rule**: Randall drives all PHI-touching processes. Claude's involvement begins only after Randall has sanitized the output and confirmed it's clean.

The workflow is:

1. **Randall** runs extraction tools locally on his machine — his decision, his process
2. **Randall** sanitizes output (strip PHI, replace with placeholders)
3. **Randall** reviews and confirms no PHI remains
4. **Claude** receives sanitized structural data (field positions, bounding boxes, table layouts) and assists with code, architecture, documentation

### Local Workflow Safeguards

All extraction libraries run locally with no network access:

| Tool | Network Access | PHI Exposure |
|---|---|---|
| Apache PDFBox | None (JVM library) | Local only |
| Tabula-java | None (JVM library) | Local only |
| Apache POI | None (JVM library) | Local only |
| Tess4J / Tesseract | None (local native) | Local only |

Additional:
- Windows BitLocker enabled for disk encryption
- No logging of extracted PHI values (log file names and error counts only)
- Delete intermediate files after processing
- Output placed in same directory as source PDFs (keeps PHI contained)
- Pin all dependency versions; audit transitive dependencies

---

## 9. Risk Register

| # | Risk | Likelihood | Severity | Mitigation | Status |
|---|------|-----------|----------|------------|--------|
| 1 | PDFs are scanned images, not native text | Low | High | Tess4J OCR fallback; feasibility gate | Monitoring |
| 2 | PDFs are encrypted/DRM-protected | Low | High | Feasibility gate; contract Section 8 allows termination | Monitoring |
| 3 | PDF layout varies significantly across samples | Medium | Medium | Flexible field mapping; confidence scoring flags edge cases | Monitoring |
| 4 | Tyler delays on feedback | Medium | High | Proactive follow-up; delivery dates shift but hard deadline holds | Monitoring |
| 5 | Extraction accuracy insufficient for production use | Low | High | Confidence scoring per field; manual review flag for low-confidence extractions | Monitoring |

---

## 10. Deliverable Specifications

### D1: Batch PDF Processing Tool

- Windows .msi installer (bundled JRE via jpackage)
- Accepts 1-50 PDFs via file picker or drag-and-drop
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

- Complete Kotlin source code
- Gradle build files with pinned dependencies
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

## 11. Tool Branding

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

*Last updated: 2026-02-23 — rewritten for Kotlin/Compose Multiplatform stack*
