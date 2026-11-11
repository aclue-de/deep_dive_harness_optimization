# AGENTS.md rules

The rules this repo uses to judge an AGENTS.md. They build on two public sources:

- [agents.md](https://agents.md): the open format, a plain Markdown file in the repository root that many coding agents read.
- Gloaguen et al., *Evaluating AGENTS.md: Are Repository-Level Context Files Helpful for Coding Agents?* ([arXiv:2602.11988](https://arxiv.org/abs/2602.11988), 2026): context files raise inference cost by over 20% on average and do not reliably raise task success. Every line has to earn its place.

## Form

- One `AGENTS.md` in the repository root, tracked by git.
- Plain Markdown. No tool-specific syntax (`@workspace`, `@codebase`, `@terminal`, `@cursor`, `@file`, `#file`) and no `@`-imports: every agent must be able to read the same file.
- At most 200 lines, aim for 100–120. A file near 200 lines almost always holds content the agent could find on its own.

## Required content

- **Boundaries in three tiers.** *Always* (do without asking), *Ask first* (needs a human OK), *Never* (forbidden). Each item names a command, file or concrete action. Without tiers an agent either asks about everything or nothing.
- **Failure protocol.** After a fixed number of failed attempts: stop, summarize what was tried and the exact error, ask for the one input that is missing. Without it agents loop and burn tokens.

## What belongs in it

Only what the agent cannot derive from the repository:

- a 2–3 line domain summary
- domain invariants and non-obvious constraints
- team conventions no linter or formatter enforces
- build and test commands that are not in the standard scripts, with their flags or profiles
- the instruction to read framework, language and infrastructure versions from build and configuration files instead of assuming them

## What does not belong in it

- directory trees without comment
- the tech stack as declared in manifest files ("This is a Spring Boot app written in Java")
- style rules a linter or formatter already checks
- vague advice an agent cannot act on ("write clean code", "be careful with security")

## Keeping it current

A stale AGENTS.md misleads every run; deleting it is better than leaving it wrong. Ask of every line:

1. Still true?
2. Still relevant?
3. Free of contradictions with other lines or the code?
4. Still not inferable from the repo?

A "no" means update or delete. If the team corrects the same agent behavior again and again, add the missing rule.

AGENTS.md is a nudge, not enforcement. What must always hold belongs in a hook, a permission rule, a linter or CI; AGENTS.md covers what cannot be checked mechanically.

## What the script checks

`scripts/CheckAgentsMd.java` covers the mechanical part: file present and tracked, line count, tool-specific syntax and `@`-imports, a "Failure Protocol" section, and the three boundary tiers. Everything else in this file needs judgement and is part of the review.
