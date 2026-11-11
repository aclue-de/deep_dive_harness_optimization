---
name: agents-md-generator
description: Create a compliant, tool-agnostic AGENTS.md for any software repository through structured elicitation with smart defaults. Only use when the user explicitly asks to create or generate an AGENTS.md file.
---
 
# AGENTS.md Generator
 
Create a compliant AGENTS.md for the current repository through a phased pipeline: discover what the repo already tells an agent, elicit what it cannot infer, draft, validate, and write.
 
## Hard Rules
 
These constraints follow the open AGENTS.md format (agents.md) and research on context files (arXiv:2602.11988).
 
- Plain Markdown only. No tool-specific syntax (`@workspace`, `@codebase`, `@cursor`, `@file`, `#file`, `<tool_use>`, `@terminal`). Tool-specific syntax fragments the file across tooling choices — AGENTS.md is read by all major AI coding tools, so it must stay tool-agnostic.
- Maximum 200 lines. Target 100-120 lines. Files approaching 200 lines almost always contain inferable content. Studies show that bloated context files increase inference costs >20% and reduce task success rates (arXiv:2602.11988).
- Only non-inferable content. Exclude anything the agent can derive from the repo: directory structure, tech stack stated in manifests, style rules enforced by linters, boilerplate descriptions of the project. Duplicating what the agent can already derive wastes tokens and actively hurts performance.
- Include an explicit instruction to derive framework, language, and infrastructure versions from build and configuration files (e.g., `build.gradle.kts`, `pom.xml`, `package.json`, `docker-compose.yml`) rather than falling back to training-data defaults.
- Three behavioral boundary tiers required: **Always**, **Ask first**, **Never**. Each item must name a specific action, command, file, or tool — never a vague principle. Without explicit tiers, agents default to asking about everything (too cautious) or acting on everything (too risky). Named tiers let the agent self-classify actions.
- **Failure Protocol** section required. Must instruct the agent to: stop retrying after a defined number of attempts, summarize what was tried and the exact error, request specific human input. Without this, agents enter retry loops that burn tokens and time without making progress.
- File lives at the repository root, committed to version control. An outdated AGENTS.md actively degrades agent performance — deleting it is preferable to leaving it stale.
 
## Checklist
 
Complete these phases in order:
 
1. **Discovery** — scan the repo to build an inferability map
2. **Elicitation** — ask targeted questions, one at a time, with smart defaults from discovery
3. **Draft** — assemble answers into AGENTS.md
4. **Validate** — run compliance checks
5. **Write** — write AGENTS.md to the repo root
 
---
 
## Phase 1: Discovery
 
Scan the repository to understand what an agent can already infer. This determines what to EXCLUDE from AGENTS.md and what smart defaults to offer.
 
Scan for these categories. Record findings internally — do not output a report to the user.
 
### Build System & Package Managers
Look for: `package.json`, `pom.xml`, `build.gradle`, `build.gradle.kts`, `Cargo.toml`, `go.mod`, `pyproject.toml`, `setup.py`, `Makefile`, `CMakeLists.txt`, `Gemfile`, `mix.exs`, `build.sbt`, `composer.json`, `pubspec.yaml`.
 
Extract: available scripts/targets, dependency versions, build commands.
 
### Language & Framework Versions
Look for: version fields in manifests, `.tool-versions`, `.node-version`, `.python-version`, `.ruby-version`, `.java-version`, `runtime.txt`, `Dockerfile` base images.
 
Extract: pinned versions of languages, frameworks, and runtimes.
 
### CI/CD
Look for: `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`, `.circleci/`, `azure-pipelines.yml`, `bitbucket-pipelines.yml`, `.travis.yml`.
 
Extract: pipeline stages, test commands, deploy steps, required checks.
 
### Linters & Formatters
Look for: `.eslintrc*`, `.prettierrc*`, `biome.json`, `.editorconfig`, `checkstyle.xml`, `spotless`, `.rubocop.yml`, `.flake8`, `ruff.toml`, `pyproject.toml [tool.ruff]`, `.golangci.yml`, `clippy.toml`, `ktlint`, `detekt.yml`.
 
Extract: which style rules are already enforced automatically (these are inferable — exclude from AGENTS.md).
 
### Test Setup
Look for: test directories (`test/`, `tests/`, `spec/`, `__tests__/`, `src/test/`), test config files (`jest.config.*`, `pytest.ini`, `phpunit.xml`, `vitest.config.*`), test scripts in manifests.
 
Extract: test commands, profiles, fixtures setup.
 
### Existing AI Context Files
Look for: `CLAUDE.md`, `.cursorrules`, `GEMINI.md`, `COPILOT.md`, `AGENTS.md`, `.github/copilot-instructions.md`.
 
If AGENTS.md already exists: inform the user and ask whether to update it or start fresh.
 
### Database & Migrations
Look for: migration directories (`db/migrate/`, `migrations/`, `alembic/`, `flyway/`), ORM config, database config files.
 
Extract: migration tooling in use.
 
### API Definitions
Look for: OpenAPI/Swagger specs, GraphQL schemas, protobuf definitions, API versioning files.
 
Extract: whether a public API contract exists.
 
After discovery, present a brief summary to the user:
 
> "I scanned the repo and found [build system], [language/framework versions], [N CI pipelines], [linter/formatter tools], [test setup], [migration tooling if any], [API specs if any]. I'll use this to filter out inferable content and offer smart defaults. Let's build your AGENTS.md."
 
---
 
## Adapting the Flow
 
- **Empty or new repo:** Skip discovery, note that defaults will be generic, and focus elicitation on the user's intended stack and workflow.
- **Monorepo with multiple stacks:** Ask whether one root AGENTS.md covers the whole repo or if subdirectory-level files are needed. If root-only, scope boundaries to the shared rules and note stack-specific exceptions.
- **User arrives with a draft:** Skip elicitation for sections already covered. Validate the draft against Phase 4 checks and ask about gaps only.
- **Existing AGENTS.md update:** Diff current content against discovery results, flag stale or inferable lines, and ask the user what to add/remove rather than starting from scratch.
 
---
 
## Phase 2: Elicitation
 
Ask questions **one at a time**. Prefer **multiple choice** when possible. Use discovery results to offer concrete, pre-filled recommendations that the user can accept, modify, or reject.
 
### Section: Domain
 
Goal: obtain a brief application and domain summary (2–3 lines).
 
Ask the user to describe: what the system does, who it serves, and which domain concepts shape naming and design. If discovery found enough context (e.g., from README, package descriptions, or API specs), offer a draft summary for the user to accept, modify, or replace.
 
Example exchange:
> "Based on the repo, this looks like an order management service for an online shop. Could you give me a 2–3 line summary — what the system does, who it serves, and the key domain concepts that shape naming?"
 
### Section: Boundaries — Always
 
Goal: identify actions the agent should perform without asking.
 
Start with discovered test/build commands — present what you found and ask which ones should run automatically before commits. Offer the discovered commands as options, allow custom combinations, and let the user decline all.
 
Then check for CI commands worth running locally, branching strategy preferences, and any other automatic actions (changelog updates, pre-commit hooks, doc regeneration).
 
Example exchange:
> "I found `npm test` and `npm run lint` in your scripts. Should the agent run either or both before every commit?"
 
### Section: Boundaries — Ask First
 
Goal: identify actions that need explicit human approval before the agent acts.
 
Start with high-risk areas discovered in the repo — migrations, API contract changes, CI/CD configuration. For each, ask whether the agent should always ask, sometimes ask, or act freely. Then cover common approval-worthy actions based on the stack: dependency upgrades, infrastructure changes, deployment steps.
 
Finish with an open-ended question for anything project-specific.
 
Example exchange:
> "I found database migrations in `db/migrate/`. Should the agent ask before creating or running migrations?"
 
### Section: Boundaries — Never
 
Goal: establish hard prohibitions tailored to the detected stack.
 
Present common "never" rules relevant to the project's languages and frameworks. Include universal rules (no force-push to default branch, no committed secrets) and stack-specific ones (e.g., no `eval()` with dynamic input for JS/TS, no `shell=True` with user input for Python, no floating point for money in Java/Kotlin, no ignored errors in Go, no bare `.unwrap()` in Rust production paths).
 
Present these as a checklist for the user to confirm or reject, then ask for project-specific prohibitions — domain invariants, security boundaries, data handling constraints.
 
### Section: Build & Test
 
Goal: capture commands, flags, and prerequisites that are NOT already visible in standard scripts.
 
Present the discovered build/test commands and ask whether anything is missing: environment variables, local services that must be running, commands not in the manifest, required flags that aren't obvious.
 
### Section: Stack
 
Goal: ensure agents derive versions from build/config files instead of falling back to training-data defaults.
 
Confirm that a "derive from manifests" instruction will be included in AGENTS.md (default: yes). Then ask whether there are version constraints *not* captured in any manifest or config file that should be called out explicitly (e.g., minimum database version required by production, deployment-specific runtime version).
 
Example exchange:
> "I'll include an instruction telling agents to derive versions from your build files rather than guessing. Are there any version constraints not in your manifests — e.g., a minimum PostgreSQL version required in production?"
 
### Section: Conventions
 
Goal: capture team coding standards that no linter or formatter enforces.
 
Ask about: architectural rules, naming conventions for domain concepts, module/layer dependency rules, state management rules, error handling patterns. Provide concrete examples to illustrate the level of specificity expected (e.g., "domain exceptions must not cross module boundaries", "service classes must not hold mutable state").
 
### Section: Failure Protocol
 
Goal: define what the agent should do when stuck.
 
Offer a sensible default — stop after two failed fix attempts, summarize what was tried and the exact error, request specific human input, do not retry. Let the user accept, adjust the retry count, add escalation steps (e.g., "tag @oncall in the PR"), or write a custom protocol.
 
---
 
## Phase 3: Draft Assembly
 
Assemble the answers into AGENTS.md using this structure:
 
```markdown
# AGENTS.md
 
## Domain
 
[2–3 line app/domain summary from elicitation]
 
## Boundaries
 
**Always:**
- [specific actions from elicitation]
 
**Ask first:**
- [specific actions from elicitation]
 
**Never:**
- [specific prohibitions from elicitation]
 
## Build & Test
 
[non-obvious commands, flags, prerequisites from elicitation]
 
## Stack
 
[derive-from-manifests instruction; any non-manifest version constraints from elicitation]
 
## Conventions
 
[team standards not enforced by tooling, from elicitation]
 
## Failure Protocol
 
[failure protocol from elicitation]
```
 
### Quality Filter
 
Before presenting the draft, apply these filters:
 
1. **Remove inferable content:** Cross-reference every line against discovery results. If a line restates what the agent can find in a manifest, config, or linter rule — remove it.
2. **Remove vague principles:** Every boundary item must name a specific action, command, file, or tool. Remove anything like "write clean code", "follow best practices", "be careful with security".
3. **Remove directory structure:** Never include project tree listings.
4. **Remove project descriptions:** Never include "This is a [language] project using [framework]".
5. **Check line count:** If over 120 lines, identify and remove the least critical items. If over 200, aggressively trim.
 
Present the draft to the user:
 
> "Here's your AGENTS.md draft ([N] lines). Please review:"
> ```markdown
> [full draft]
> ```
> "Would you like to change anything before I run validation?"
 
---
 
## Phase 4: Validation
 
Run these automated compliance checks on the draft:
 
### Check 1: Line Count
- Count lines. Must be <= 200. Warn if > 120.
 
### Check 2: No Tool-Specific Syntax
- Scan for: `@workspace`, `@codebase`, `@terminal`, `@cursor`, `@file`, `#file:`, `<tool_use>`, `</tool_use>`, any XML-style agent tags.
- If found: remove them and inform the user.
 
### Check 3: Required Sections and Content
- Must contain: Boundaries section with all three tiers (Always, Ask first, Never), Failure Protocol section.
- Each boundary tier must contain at least one item with a backticked command or explicit file path — confirming the concrete-example requirement.
- Failure Protocol must contain all three required elements: (1) a defined retry limit after which the agent stops, (2) instruction to summarize what was tried and the exact error, (3) instruction to request specific human input. If any element is missing, add it.
- If a section is missing entirely: add a placeholder and ask the user to fill it.
 
### Check 4: Boundary Specificity
- Each boundary item must contain at least one of: a command (backticked), a file path, a specific tool name, a concrete action verb with an object.
- Flag any item that reads like a general principle (e.g., "follow security best practices").
 
### Check 5: Non-Inferability Spot-Check
- Compare each line against discovery results.
- Flag any line that restates inferable content (detected language, framework choice, directory structure, linter rules).
 
### Check 6: Plain Markdown
- Verify the file uses only standard Markdown syntax (headings, lists, code blocks, bold, italic).
 
### Reporting
Present a validation summary:
 
> **Validation Results:**
> - Line count: [N]/200 [PASS/WARN/FAIL]
> - Tool-specific syntax: [PASS/FAIL — list violations]
> - Required sections: [PASS/FAIL — list missing]
> - Boundary specificity: [PASS/WARN — list vague items]
> - Failure Protocol completeness: [PASS/FAIL — list missing elements]
> - Non-inferability: [PASS/WARN — list inferable content]
> - Plain Markdown: [PASS/FAIL]
 
If any check fails, fix automatically where possible, and show the user what changed. Re-validate after fixes.
 
---
 
## Phase 5: Write Files
 
After validation passes and the user approves:
 
1. **Write `AGENTS.md`** to the repository root.
 
2. **Post-write verification:**
   - Confirm `AGENTS.md` exists at the repo root and is non-empty.
   - Check if the file is tracked by version control (`git ls-files AGENTS.md`). If not tracked, instruct the user to stage and commit it: `git add AGENTS.md`.
 
3. **Present summary:**
> "Created:"
> - `AGENTS.md` — [N] lines, [M] boundary rules, all checks passed
>
> "Next steps:"
> - Stage and commit: `git add AGENTS.md && git commit -m "Add AGENTS.md"`
> - Submit via pull request for team review
> - Audit regularly. For each line, ask: (1) Still true? (2) Still relevant? (3) Contradicts another instruction? (4) Still not inferable from the repo?
