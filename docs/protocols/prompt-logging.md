# Protocol: Prompt Logging

When making a commit, also log the session that led to it.

---

## Location

`/archive/prompts/YYYY/MM.md` (e.g., `/archive/prompts/2026/02.md`)

## Format

```markdown
## YYYY-MM-DD

### Commit message summary (as header)

**Session log:**

1. **User**: "First user prompt..."
   - What was done in response
   - Key decisions made, files affected

2. **User**: "Second user prompt..."
   - What changed based on this input
   - Key decision: chose X over Y

3. **User**: "Third user prompt..."
   - Final adjustments, verification steps

---
```

This interleaved format captures the conversation arc — what each prompt triggered, what decisions were made at each step, and how the session evolved.

*Note: Commit hash isn't known until after commit, so use the commit message as the section header.*

## Purpose

These logs will be analyzed later for:
- Identifying workflow improvement opportunities
- Understanding usage patterns
- Training data for workflow optimization

## Rules

- **Never shorten or remove user prompts.** Log each user prompt verbatim (or as close to verbatim as possible). Do not paraphrase, truncate, or omit any part of what the user said. The full prompt text is essential for later analysis.

## Process

1. Build the log incrementally — append each user prompt and your response summary as work happens, rather than reconstructing the session at commit time
2. Before committing, review the log for completeness
3. Then make the git commit
