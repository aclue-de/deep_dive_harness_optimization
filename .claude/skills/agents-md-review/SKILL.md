---
name: agents-md-review
description: Review an AGENTS.md against the AGENTS.md rules (size, plain Markdown, Always / Ask first / Never boundaries, failure protocol, no inferable content). Use when AGENTS.md is created or changed, when the AGENTS.md gate hook reports violations, or when asked whether AGENTS.md follows the rules.
---

# AGENTS.md review

Never edit AGENTS.md yourself. Report findings and proposals; a human decides.

## Workflow

1. **Deterministic checks.** Run:
   ```
   java .claude/skills/agents-md-review/scripts/CheckAgentsMd.java AGENTS.md
   ```
   Take every `VIOLATION` and `WARNING` line of the output as a finding.

2. **Content checks.** Read AGENTS.md with line numbers. Go through
   `references/checklist.md` item by item.
   Open `references/agents-md-rules.md` only for the section you are judging.

3. **Verify claims against the repository.** For every command, path or
   technology in AGENTS.md, check that it exists (Glob, Grep, Read).
   Stale or false lines are findings (checklist A1).

4. **Map each finding to a target** using the table in `references/checklist.md`
   (delete, keep, hook, permission, rule, skill, linter).

## Report format

| Line | Finding | Checklist ID | Target | Proposal |
|---|---|---|---|---|

Finish with:
- number of findings per checklist group (D, S, C, A)
- the three most important changes
