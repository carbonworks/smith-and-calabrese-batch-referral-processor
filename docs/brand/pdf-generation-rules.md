---
type: reference
status: active
area: brand
created: 2026-02-02
updated: 2026-02-03
---

# PDF Generation Rules

Rules for the PDF generation pipeline — which content patterns the template handles, how different document types should be structured, and frontmatter guidance. Discovered and maintained by the [PDF Generation skill](/system/skills/pdf-generation.md) through the [self-improving validation protocol](/system/skills/protocols/validation-protocol.md).

New rules are proposed when the pipeline encounters content patterns it doesn't handle well or new document types that need specific guidance, and adopted on user approval.

**Relationship to other rules files:**
- `/org/brand/design-decisions.md` — how the template *looks* (colors, typography, spacing)
- This file — how the template *behaves* with different content (what works, what breaks, what needs special handling)
- `/org/brand/brand-rules.md` — content/copy rules that apply before the content reaches the pipeline

---

## Content Pattern Handling

Rules for how specific markdown content patterns render in the PDF template, and any special handling needed.

| Content Pattern | Template Behavior | Guidance | Discovered |
|-----------------|-------------------|----------|------------|
| Tables (basic) | Dark-teal header, zebra striping, 0.5pt borders | Works well. Keep tables under 5 columns for readability at 11pt. | Template review |
| Code blocks | Warm-white background, light-gray border, Consolas 10pt | Works well. No syntax highlighting — if color coding is needed, use inline descriptions. | Template review |
| Heading depth (H1-H3) | Styled with distinct sizes, weights, and colors | Works well. Avoid H4+ — template has no distinct styling below H3. | Template review |
| Emphasis (italic) | Renders in soft-gray | Be aware: italic text is de-emphasized visually (lighter color), not just stylistically. Use bold for emphasis that should stand out. | Template review |
| Links | Sky-blue text | Visible in PDF but not clickable in print. For print-intended documents, consider adding URLs inline or as footnotes. | Template review |

*New entries are added below this line as content patterns are encountered.*

| Signature blocks | Stacked text blocks render correctly; tables render as data tables (wrong visual weight) | Use stacked Signature/Name/Date text per party, separated by `&nbsp;`. Do NOT use markdown tables for signature blocks — they render with header styling and zebra striping, which looks like a data table, not a signing area. | 2026-02-13 S&C contract |
| Trailing tagline / closing text | Renders as body text after last content | Do NOT place taglines or slogans after the last content section. They get visually associated with whatever precedes them (e.g., client signature block). Branding belongs on the cover page only. | 2026-02-13 S&C contract |
| Internal horizontal rules (`---` in body content) | Renders as a thin line across the page; looks especially bad near page breaks | **Never include internal horizontal rules in document body content.** They have not rendered presentably in any document to date. Remove all `---` from source markdown before PDF generation. The template's heading styles, spacing, and section numbering provide sufficient visual separation. Note: the header/footer rules built into the typst template are fine — this rule applies only to markdown `---` in the document body. | 2026-02-13 S&C contract — persistent rendering issues |

---

## Document Type Guidance

Rules for structuring specific types of documents for optimal PDF output.

| Document Type | Recommended Structure | Frontmatter Notes | Discovered |
|---------------|----------------------|-------------------|------------|
| Consulting proposal | Executive summary → Problem/Opportunity → Approach → Deliverables → Timeline → Investment | Use `client`, `confidential: true`. Subtitle should be the engagement theme. | Pipeline experience |
| Technology report | Summary → Findings → Recommendations → Appendix (if needed) | Use `client`. Keep heading depth to H1/H2 for clarity. | Pipeline experience |
| Service description | Overview → What's included → How it works → Next steps | No `client` needed. Use subtitle for the tagline or positioning. | Pipeline experience |

*New entries are added below this line as new document types are encountered.*

| Services agreement / contract | Situation → Objectives → Scope → Client Responsibilities → Timeline → Cost → Support → Legal clauses → Acceptance | Use `client`, `confidential: true`. Numbered lists for obligations and deliverables. Add plain-language summaries for legal sections where possible. | 2026-02-13 S&C Phase 1 contract |

---

## Frontmatter Guidance

Rules for which frontmatter combinations work well and common pitfalls.

| Rule | Detail | Discovered |
|------|--------|------------|
| Always include `date` | Cover page looks incomplete without it; use month + year format ("February 2026") | Pipeline experience |
| Use structured `author` for client deliverables | Include affiliation ("Carbon Works") to reinforce brand on every document | Pipeline experience |
| Set `confidential: true` for client-specific content | Adds legal notice to cover and footer; appropriate for proposals, reports, assessments | Pipeline experience |
| `client` flag overrides frontmatter | If both `--client "Name"` and frontmatter `client:` are set, the flag wins | Template behavior |

*New entries are added below this line as frontmatter patterns are discovered.*

---

## Known Limitations

Content patterns or configurations that the template doesn't handle well. These may spawn template improvement tasks.

| Limitation | Impact | Workaround | Status |
|------------|--------|------------|--------|
| No H4+ styling | H4 and below render as body text size | Restructure content to stay within H1-H3 | Known, accepted |
| No image sizing/captioning | Images render at natural size with no caption support | Resize images before including; add caption as italic text below | Known, needs template work |
| No blockquote styling | Blockquotes render as plain indented text | Avoid blockquotes in PDFs, or use bold/italic for emphasis instead | Known, needs template work |
| No footnote rendering | Pandoc footnotes may not render correctly via typst | Use inline parenthetical references instead | Needs verification |
| ~~Horizontal rules (`---`) cause errors~~ | ~~Pandoc generates `#horizontalrule` which failed due to typst scoping~~ | **Fixed 2026-02-03**: Template bug corrected — root cause was `$body$` literal in comment causing template corruption | ~~2026-02-03~~ Resolved |

---

## Changelog

| Date | Change | Source |
|------|--------|--------|
| 2026-02-02 | Initial structure — documented known content patterns and limitations from template review | PDF generation rules file creation |
| 2026-02-03 | Added horizontal rule limitation — causes typst scoping errors | Workflow optimization PDF generation |
| 2026-02-03 | Fixed horizontal rule limitation — root cause was `$body$` literal in carbonworks.typst comment corrupting template output | Template investigation |
| 2026-02-13 | Added "Services agreement / contract" document type guidance | S&C Phase 1 contract generation |
| 2026-02-13 | Added signature block convention (stacked, not tables) and trailing tagline rule (cover page only) | S&C contract PDF review |
| 2026-02-13 | Added: never include horizontal rules in generated documents — persistent rendering issues | S&C contract + prior experience |
