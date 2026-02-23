# S&C Batch Referral Processor — AI Instructions

## Project Purpose

Phase 1 deliverable for the Smith & Calabrese consulting engagement: a batch PDF data extraction tool that processes SSA/DDS consultative examination referral PDFs and outputs structured data to XLSX spreadsheets.

**Client**: Smith & Calabrese Assessments, LLC (psychological assessment firm)
**Engagement managed in**: CarbonWorks Intranet (`/home/rmdev/projects/CarbonWorksIntranet/work/projects/smith-calabrese-consulting/`)

---

## Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Language | Kotlin | Primary language (Randall's expertise) |
| UI framework | Compose Multiplatform (Desktop) | Cross-platform Windows + macOS |
| PDF text extraction | Apache PDFBox | Core text/coordinate extraction from PDFs |
| Table extraction | Tabula-java | Structured table data from PDFs |
| XLSX output | Apache POI | Spreadsheet generation |
| OCR fallback | Tess4J | JNI wrapper for Tesseract (scanned pages) |
| Build system | Gradle (Kotlin DSL) | Build, package, distribute |

**Target platforms**: Windows (primary — 6 office machines), macOS (secondary)
**Distribution**: jpackage .msi installer (~60-100MB with bundled JRE)

---

## PHI Constraints (CRITICAL)

This project processes Protected Health Information (PHI) from SSA disability referral PDFs.

- **NEVER include real PHI in AI context** — no patient names, SSNs, DOBs, addresses, diagnosis codes
- **NEVER commit real PHI to the repository** — sample files use sanitized/masked data only
- PHI processing happens locally on Randall's machine only
- All extraction development uses sanitized structural data (field positions, bounding boxes, layouts)
- See `docs/hipaa/hipaa-compliance-solo-consultant.md` for compliance requirements

---

## Project Structure

```
docs/                    # Project documentation, research, brand assets
reference/
  python-scripts/        # Python prototype scripts (reference only, not production)
  sample-output/         # Sanitized extraction output samples
tools/                   # PDF generation toolchain for deliverable documents
src/                     # Kotlin/Compose Multiplatform application source
```

---

## Key Documents

| Need | Location |
|------|----------|
| Build plan & timeline | `docs/build-plan.md` |
| Solution analysis | `docs/solution-analysis.md` |
| Tech stack decision | `docs/tech-stack-decision.md` |
| Field mapping | `docs/field-mapping.json` |
| JVM library research | `docs/research/jvm-pdf-extraction-research.md` |
| HIPAA compliance | `docs/hipaa/hipaa-compliance-solo-consultant.md` |
| Brand guidelines | `docs/brand/carbon-works-brand-guidelines.md` |
| Python prototypes | `reference/python-scripts/` (reference implementations) |

---

## Git Practices

- Commit early, commit often
- Descriptive commit messages in imperative mood
- Do NOT include `Co-Authored-By` trailers
- Stage files explicitly (never `git add .` or `git add -A`)

---

## Intranet Relationship

This repository is the source of truth for **code and technical documentation**.
The CarbonWorks Intranet is the source of truth for the **consulting engagement** (timeline, milestones, client communications, billing).

Do not duplicate engagement management here. Reference the intranet project file for:
- Client communications and email threads
- Contract terms and payment tracking
- Milestone status and delivery schedule
- Discovery questions and meeting notes
