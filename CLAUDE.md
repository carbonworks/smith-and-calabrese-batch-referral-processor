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

---

## Project Structure

```
archive/
  prompts/               # AI session prompt logs (YYYY/MM.md)
docs/                    # Project documentation, research, brand assets
  protocols/             # Workflow protocols (prompt logging, etc.)
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
| Architecture | `docs/architecture/architecture.md` |
| Architectural review | `docs/architecture/architectural-review.md` |
| Build plan & timeline | `docs/spec/build-plan.md` |
| Solution analysis | `docs/spec/solution-analysis.md` |
| Tech stack decision | `docs/spec/tech-stack-decision.md` |
| Field mapping | `docs/spec/field-mapping.json` |
| JVM library research | `docs/research/jvm-pdf-extraction-research.md` |
| Brand guidelines | `docs/brand/carbon-works-brand-guidelines.md` |
| Consulting | `docs/consulting/` |
| Python prototypes | `reference/python-scripts/` (reference implementations) |
| Work packages | `work-packages.md` (parallel agent development) |
| Prompt logging protocol | `docs/protocols/prompt-logging.md` |
| Prompt logs | `archive/prompts/YYYY/MM.md` |

---

## Git Practices

- Commit early, commit often
- Descriptive commit messages in imperative mood
- Do NOT include `Co-Authored-By` trailers
- Stage files explicitly (never `git add .` or `git add -A`)
- When making a commit, also log the session. See protocol: `docs/protocols/prompt-logging.md`

## Code Change Policy

**All code changes must go through worktree agents.** Do not edit source files (`src/`, `build.gradle.kts`) directly in the main working tree. Instead, launch a Task with `isolation: "worktree"` and merge the result. This keeps the main conversation window free for orchestration, backlog management, and user interaction.

Exceptions: documentation-only changes (`docs/`, `work-packages.md`, `CLAUDE.md`) and trivial non-code fixes can be made directly.

## Permission Hygiene

- **Never bundle AI memory/settings edits with project edits.** Edits to files under `.claude/` (memory, settings, etc.) must be requested in a separate tool call from edits to project source files. This allows the user to grant blanket permission for one category without being forced to approve the other.
- **Work package agents must run in the background.** Never run a worktree implementation agent in the foreground — it blocks the main conversation and forces the user to sit through serial edit approvals.

---

## Parallel Agent Workflow

This project supports parallel development via git worktree-isolated subagents. The user can ask Claude Code to launch multiple agents working on independent packages simultaneously.

### Work Packages

All implementation work is defined in `work-packages.md`. Each package specifies:
- **Owns**: Files the agent creates or heavily modifies (exclusive ownership)
- **Reads**: Spec docs to reference (read-only)
- **Touches**: Shared files where small additions are expected (merge conflicts acceptable)
- **Depends on**: Packages that must be merged first

### How to Launch Agents

When the user asks to run work packages (e.g., "run WP-0 and WP-2" or "kick off Wave 1"):

1. **Read `work-packages.md`** to get the current status and scope of each requested package.
2. **Check dependencies** — only launch packages whose dependencies are merged (status: `done`).
3. **Launch each package as a Task** with `isolation: "worktree"`, `subagent_type: "general-purpose"`, and **`run_in_background: true`**. Each agent gets its own git worktree (isolated branch). Work package agents must always run asynchronously so the main conversation stays responsive and the user is not blocked approving edits serially for extended periods.
4. **Provide each agent a clear prompt** including:
   - The full text of the work package scope and acceptance criteria
   - The list of spec docs to read (from the "Reads" field)
   - Instructions to commit their work on the worktree branch when done
   - The project's commit conventions (imperative mood, no Co-Authored-By)
5. **Monitor agents** — you will be notified when background agents complete. Do not poll or sleep-wait. When agents complete, their worktree branches contain the work.
6. **Merge completed branches** into `main` one at a time. Resolve any conflicts (most likely in shared files listed under "Touches"). Use standard `git merge` — do not squash, so the branch history is preserved.
7. **Update `work-packages.md`** — set the merged package's status to `done`.
8. **After merging a wave**, check for newly unblocked packages. Report this to the user.

### Agent Prompt Template

When launching a worktree agent for a work package, use this structure:

```
You are implementing work package WP-{N} for the S&C Batch Referral Processor.

## Your Task
{paste the full Scope section from work-packages.md}

## Acceptance Criteria
{paste the Acceptance section}

## Key References
Read these files before writing code:
- {list from Reads field}
- CLAUDE.md (project conventions)

## File Ownership
You OWN (create/modify freely): {Owns list}
You may ADD SMALL CHANGES to: {Touches list}
Do NOT modify any other files.

## When Done
- Commit your work with an imperative-mood message summarizing what you built.
- Do not add Co-Authored-By or any AI attribution.
- Do not push — just commit locally on this branch.
```

### Conflict Resolution

Conflicts are most likely in files listed under "Touches" for multiple packages (primarily `Main.kt`). When merging:
- These are typically additive (adding imports, init calls) — take both sides.
- If logic conflicts occur, the work package's scope description is the authority for its subsystem.

### Running Agents Concurrently

Launch all independent packages in a single message with multiple Task tool calls. For example, Wave 1 (WP-0, WP-2) can run simultaneously since they have no dependencies on each other.

---

## Intranet Relationship

This repository is the source of truth for **code and technical documentation**.
The CarbonWorks Intranet is the source of truth for the **consulting engagement** (timeline, milestones, client communications, billing).

Do not duplicate engagement management here. Reference the intranet project file for:
- Client communications and email threads
- Contract terms and payment tracking
- Milestone status and delivery schedule
- Discovery questions and meeting notes
