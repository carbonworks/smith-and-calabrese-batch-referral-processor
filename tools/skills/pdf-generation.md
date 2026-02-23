# Skill: PDF Generation

Generate branded Carbon Works PDFs from markdown files using the pandoc + typst pipeline.

---

## When to Use

- Converting markdown deliverables (reports, proposals, service descriptions) to client-ready PDFs
- Any time the user says "generate PDF", "make a PDF", "export to PDF", or uses `/project:pdf`

---

## Prerequisites

The input markdown file **must** have YAML frontmatter with at least `title` and `author`. The full set of supported variables:

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `title` | Yes | Document title (cover page + headers) | `"Technology Consulting Services"` |
| `subtitle` | No | Appears below title on cover page | `"Making sense of technology."` |
| `author` | Yes | Author name(s) with optional affiliation | See format below |
| `date` | Recommended | Date shown on cover page | `February 2026` |
| `client` | No | Client name for "Prepared for:" line | Set via `--client` flag or frontmatter |
| `confidential` | No | If `true`, adds confidentiality notice to cover and footer | `true` |

**Author format** (either simple or structured):

```yaml
# Simple
author: Randall Mitchell

# Structured (with affiliation)
author:
  - name: Randall Mitchell
    affiliation: Carbon Works
```

---

## Process

### Step 1: Validate Input

1. Confirm the input file exists (check the path)
2. Read the file and verify it has YAML frontmatter with at least `title` and `author`
3. Read `/org/brand/pdf-generation-rules.md` — check frontmatter guidance and document type rules for the content being generated
4. If frontmatter is missing or incomplete, tell the user what's needed and stop

### Step 2: Brand Check

Run the brand analysis skill (`/system/skills/brand-analysis.md`) on the source markdown before generating the PDF. This catches issues in the text before they're baked into a client-facing document.

1. **Execute `/system/skills/brand-analysis.md`** with the input file as the target, scoped to **written-copy dimensions only** (Voice & Tone, Messaging, Values Alignment — skip Color Palette, Typography, Visual Identity, and Industry Standards Research)
2. **Adapt the output** for the PDF flow (inline summary, not the full report format):
   - If **no issues**: Note the content passes brand check and continue to Step 3
   - If **minor issues only**: List them, note they're cosmetic, and continue to Step 3
   - If **important or critical issues**: List them with suggested fixes and ask the user:
     - **Fix and proceed** — make the changes in the source markdown, then continue to Step 3
     - **Proceed anyway** — generate the PDF as-is
     - **Stop** — don't generate, let the user handle it

**Skip this step** if the user passes `--no-brand-check` or explicitly says to skip the brand check.

### Step 3: Derive Output Path

Default output location: `system/tools/outputs/<filename>.pdf`

- Strip the `.md` extension from the input filename
- Example: `consulting/service-offerings.md` → `system/tools/outputs/service-offerings.pdf`
- Example: `meetings/.../technology-report.md` → `system/tools/outputs/technology-report.pdf`

If the user specifies a different output path, use that instead.

### Step 4: Generate the PDF

Run the generation script:

```bash
bash system/tools/templates/generate-pdf.sh <input> <output> [--client "Name"]
```

**Examples:**

```bash
# Without client
bash system/tools/templates/generate-pdf.sh consulting/service-offerings.md system/tools/outputs/service-offerings.pdf

# With client
bash system/tools/templates/generate-pdf.sh consulting/service-offerings.md system/tools/outputs/service-offerings.pdf --client "Tyler Calabrese"
```

The script handles:
- PATH setup for pandoc and typst
- Passing the Carbon Works template and logo
- Creating the output directory if needed

### Step 5: Verify Output

1. Confirm the PDF file was created (check it exists and has non-zero size)
2. Report success with the full output path

### Step 6: Design Audit

Run the design analysis skill (`/system/skills/design-analysis.md`) as a post-generation template audit. This checks whether the template's design decisions align with current brand guidelines and industry standards for the specific document type.

1. **Execute `/system/skills/design-analysis.md`** with:
   - **Target**: the PDF template (`system/tools/templates/carbonworks.typ`)
   - **Context**: the input markdown file (so the analysis knows what content patterns — tables, code blocks, heading depth — the template needed to handle)
   - **Scope**: post-generation audit (skip general audit; focus on the content patterns present in this document)
2. **Adapt the output** for the PDF flow (inline summary, not the full design analysis report):
   - If **no issues or gaps**: Note the design passes audit and continue to Step 7
   - If **design system gaps found**: List them briefly with proposed rules and ask the user:
     - **Adopt rules now** — write to `/org/brand/design-decisions.md`, then continue to Step 7
     - **Skip for now** — note the gaps and continue to Step 7
   - If **brand compliance issues found**: List them as informational (the template would need a separate change to fix these — note as follow-up)

**Skip this step** if the user passes `--no-design-check` or explicitly says to skip the design audit.

### Step 7: Report

Tell the user:
- Where the PDF was saved
- What frontmatter was used (title, client, confidential status)
- Brand check summary (from Step 2)
- Design audit summary (from Step 6, if run)
- The file is ready to share/send

### Step 8: Pipeline Rules Growth

Follow the [self-improving validation protocol](/system/skills/protocols/validation-protocol.md).

**Rules file:** `/org/brand/pdf-generation-rules.md`
**Escalation file:** n/a (pipeline rules don't escalate to brand guidelines)

**Gap types to watch for:**
- **Content pattern not documented** — the input markdown contained elements (deeply nested headings, wide tables, images, blockquotes) whose template behavior isn't recorded in the Content Pattern Handling section
- **New document type** — this was a type of document (e.g., case study, meeting summary) not listed in the Document Type Guidance section
- **Frontmatter lesson** — a frontmatter combination that produced unexpected results, or a combination that worked well and should be documented
- **Pipeline issue** — a rendering problem, error, or workaround that should be recorded in Known Limitations

**Process:**
1. After the full pipeline completes (Steps 1-7), review what happened:
   - Did any content patterns render unexpectedly?
   - Was this a document type not covered by existing guidance?
   - Did the frontmatter combination work well or need adjustment?
2. If gaps found, propose rules (using the protocol format: What, Why, Source, Where)
3. Ask the user to adopt, modify, defer, or reject
4. Write adopted rules to `/org/brand/pdf-generation-rules.md`
5. Update the changelog and commit

**Skip this step** if the pipeline ran without any novel situations.

---

## Troubleshooting

| Problem | Likely Cause | Fix |
|---------|-------------|-----|
| `pandoc: command not found` | PATH not set | The script sets PATH automatically — if this fails, check that pandoc is installed at the expected location |
| `typst: command not found` | PATH not set | Same as above for typst |
| Font rendering issues | Missing Inter font | Typst falls back to Calibri → Arial. Install Inter for best results |
| `template not found` | Wrong working directory | Run from the intranet root directory |
| Logo not appearing | Path issue | The script uses a relative path to `brand/assets/logo/` — ensure it exists |

---

## Worked Examples

### Example 1: Service Offerings (no client, clean run)

```
User: /project:pdf consulting/service-offerings.md

Process:
1. Read consulting/service-offerings.md — has title, subtitle, author, date ✓
   Check pdf-generation-rules.md: service description type, frontmatter looks good
2. Brand check — scan content (written-copy dimensions):
   Voice ✓, Messaging ✓, Values ✓ — no issues, proceeding
3. Output: system/tools/outputs/service-offerings.pdf
4. Run: bash system/tools/templates/generate-pdf.sh consulting/service-offerings.md system/tools/outputs/service-offerings.pdf
5. Verify: file exists, non-zero size ✓
6. Design audit — template check for content patterns (headings, paragraphs):
   Brand compliance ✓, no gaps — design passes
7. Report: "PDF saved to system/tools/outputs/service-offerings.pdf"
8. Pipeline rules: no novel situations — skip
```

### Example 2: Technology Report (with client, pipeline learning)

```
User: /project:pdf meetings/2026-01-tyler-calabrese-discovery/technology-report.md --client "Tyler Calabrese"

Process:
1. Read technology-report.md — has title, subtitle, author, date ✓
   Check pdf-generation-rules.md: technology report type documented ✓
2. Brand check — scan content (written-copy dimensions):
   Voice ✓, Messaging ✓, Values ✓ — no issues, proceeding
3. Output: system/tools/outputs/technology-report.pdf
4. Run: bash system/tools/templates/generate-pdf.sh meetings/2026-01-tyler-calabrese-discovery/technology-report.md system/tools/outputs/technology-report.pdf --client "Tyler Calabrese"
5. Verify: file exists, non-zero size ✓
6. Design audit — template check for content patterns (headings, tables, code blocks):
   Brand compliance ✓, all content patterns covered ✓
7. Report: "PDF saved to system/tools/outputs/technology-report.pdf (prepared for Tyler Calabrese)"
8. Pipeline rules:
   Content had a 7-column table — not documented in Content Pattern Handling
   → Proposed rule: "Tables with 6+ columns: consider landscape orientation or
     splitting into multiple tables. At 11pt/10pt in portrait, 6+ columns
     causes text wrapping that hurts readability."
   → User adopts → written to pdf-generation-rules.md
```

### Example 3: Content with brand issues and design gap

```
User: /project:pdf consulting/new-proposal.md --client "Acme Corp"

Process:
1. Read new-proposal.md — has title, author, date ✓
   Check pdf-generation-rules.md: consulting proposal type documented ✓
2. Brand check — scan content (written-copy dimensions):
   Voice: paragraph 2 uses "leverage synergies" and "digital transformation" → Important
   Messaging: "pain points" used twice → Important
   Values ✓
   → 2 Important issues found. Ask user: fix and proceed, proceed anyway, or stop?
   User: "fix and proceed"
   → Replace "leverage synergies" → "work well together"
   → Replace "digital transformation" → "getting your technology sorted out"
   → Replace "pain points" → "frustrations"
   → Re-confirm content looks good
3. Output: system/tools/outputs/new-proposal.pdf
4. Run: bash system/tools/templates/generate-pdf.sh consulting/new-proposal.md system/tools/outputs/new-proposal.pdf --client "Acme Corp"
5. Verify: file exists, non-zero size ✓
6. Design audit — template check for content patterns (headings, tables, blockquotes):
   Gap found: no rule for blockquote styling in design-decisions.md
   → Proposed rule: indented block, left border 3pt sky-blue, soft-gray italic text
   → User: "adopt" → rule written to design-decisions.md
7. Report: "PDF saved to system/tools/outputs/new-proposal.pdf (3 brand fixes, 1 design rule adopted)"
8. Pipeline rules: no additional novel situations — skip
```

---

## Related Files

- **Generation script**: `system/tools/templates/generate-pdf.sh`
- **Pandoc template**: `system/tools/templates/carbonworks.typst`
- **Typst template**: `system/tools/templates/carbonworks.typ`
- **Brand logo**: `brand/assets/logo/png/Color logo - no background.png`
- **Brand guidelines**: `brand/carbon-works-brand-guidelines.md`
- **PDF generation rules** (self-improving): `/org/brand/pdf-generation-rules.md`
- **Brand analysis skill**: `/system/skills/brand-analysis.md` (invoked at Step 2)
- **Design analysis skill**: `/system/skills/design-analysis.md` (invoked at Step 6)
- **Validation protocol**: `/system/skills/protocols/validation-protocol.md` (shared self-improving process)
