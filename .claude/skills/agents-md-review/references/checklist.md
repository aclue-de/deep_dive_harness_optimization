# Checklist: AGENTS.md rules

## D – Deterministic (covered by scripts/CheckAgentsMd.java)

| ID | Check |
|---|---|
| D1 | AGENTS.md exists in the repository root |
| D2 | AGENTS.md is tracked by version control |
| D3 | At most 200 lines (target 100–120) |
| D4 | Plain Markdown: no tool-specific syntax (`@workspace`, `@codebase`, `@terminal`, `@cursor`, `@file`, `#file`) and no `@`-imports |
| D5 | Section "Failure Protocol" exists |

## S – Structure

| ID | Check |
|---|---|
| S1 | Starts with a short domain summary (2–3 lines): what the system does, key domain concepts |
| S2 | Boundaries in three tiers: Always / Ask first / Never |
| S3 | Every boundary names a concrete action, command or file, not a category ("run `<cmd>` before every commit", not "make sure tests pass") |
| S4 | Failure protocol: stop retrying, summarize what was tried and the exact error, ask for the specific input needed |

## C – Content

| ID | Check |
|---|---|
| C1 | No inferable content: plain directory trees, technology choices declared in manifest files, style rules enforced by a linter or formatter |
| C2 | No vague instructions an agent cannot act on ("write clean code", "be careful with security") |
| C3 | Domain invariants and non-obvious constraints are present |
| C4 | Non-standard build and test commands are listed with their context (flags, profiles) |
| C5 | Tells the agent to derive versions from build and configuration files |
| C6 | No contradictions with other lines, the code or other team rules |

## A – Audit questions (per line)

| ID | Question | If "no" |
|---|---|---|
| A1 | Still true? | update or delete |
| A2 | Still relevant? | delete |
| A3 | Free of contradictions? | resolve |
| A4 | Still not inferable from the repo? | delete |

## Target mapping

| Line type | Target |
|---|---|
| Inferable (tree, stack, versions) | delete |
| Style rule a tool can check | linter / formatter, optionally run by a hook |
| "Always run X" | keep in AGENTS.md **and** enforce with a hook |
| "Never do X" | keep in AGENTS.md **and** enforce with a hook or permission deny rule |
| Convention for certain files only | `.claude/rules/` with `paths` |
| How-to for one kind of task | skill |
| Domain, invariants, non-standard commands, failure protocol | keep |
