# Deep Dive Harness Optimization – Exercise Repo

Small warehouse app in Java with a rule-compliant AGENTS.md and a hard-coded API key. You build the hooks yourself.

**Requirements:** JDK 22 or newer (we recommend 25, the current LTS), git and Claude Code. No build tool: to keep the lab simple, every program runs straight from source, e.g. `java backend/Server.java`.

## Check Java first

```bash
java -version          # must show 22 or newer
```

Shows 21 or older? Then `java backend/Server.java` fails with `cannot find symbol … class Inventory` (German JDK: `Symbol nicht gefunden`). Older JDKs compile only the one file you start, not its neighbours. Install JDK 25 (any JDK from 22 on works):

| OS | Install | Which `java` comes first? |
|---|---|---|
| Windows | `winget install EclipseAdoptium.Temurin.25.JDK` | `where.exe java` |
| macOS | `brew install --cask temurin@25` | `which java` |
| Linux | `sdk install java 25-tem` (SDKMAN) or your package manager | `which java` |

Then **close the whole program your terminal runs in** (VS Code with all its windows, Windows Terminal, IntelliJ, …) and start it again: a new tab keeps the old `PATH`. Check `java -version` again. Still the old version? An older JDK is earlier on the `PATH`: move the new JDK to the front or remove the old one.

## Start

```bash
java backend/Server.java                                  # app on http://localhost:8000
claude                                                    # in a second terminal, in this folder
```

Run both from the repo root: the server looks for `frontend/` relative to the current folder.

## Structure

```
backend/            Server.java (API + frontend), Inventory.java, InventoryTest.java (chatty on purpose)
frontend/           static page that calls the API
tools/status-line/  status line showing the prompt cache (slide 45)
AGENTS.md           instructions for coding agents, checked against the AGENTS.md rules
docs/               exercises.md (overview per slide), runbook.md (steps and checks)
.claude/
├── settings.json   register your hooks here
├── hooks/HookInput.java     helper: reads the hook JSON from stdin (field, flag, quote)
├── agents/agents-md-reviewer.md  subagent: review of AGENTS.md (read-only)
└── skills/agents-md-review/   skill incl. scripts/CheckAgentsMd.java (deterministic check)
```

## Exercises

The exercises come from the slides; the repo provides what they need. Overview: [docs/exercises.md](docs/exercises.md).

**Chapter 05 Hooks (slide 34):** three exercises, one optional for fast groups or for later

| # | Hook | Event | Task |
|---|---|---|---|
| 1 | Prompt secret guard | `UserPromptSubmit` | Block a prompt that contains an API key, an IBAN, a card number or three or more e-mail addresses, before it reaches the model. |
| 2 | AGENTS.md gate | `PostToolUse` | After an edit of `AGENTS.md`, run `CheckAgentsMd.java` and feed its findings back to Claude, with the hint to start the subagent `agents-md-reviewer`. |
| 3 | Harness guard | `PreToolUse` | Before Claude changes `.claude/settings*.json` or `.claude/hooks/`, ask the user (`"permissionDecision": "ask"`) and say what changes. Do this one last. |
| A | Notify (optional) | `Notification` | Show a desktop notification when Claude waits for you. Register it with `"async": true`. |

**Chapter 06 Context management**

| Slide | Task |
|---|---|
| 45 | Start `claude --settings tools/status-line/settings.json`, ask two short questions, then switch the model with `/model` and ask once more. Watch `hit` and `misses`. |

Write the hooks as `.claude/hooks/<Name>.java`, register them in `.claude/settings.json` as `java "$CLAUDE_PROJECT_DIR/.claude/hooks/<Name>.java"`. You don't have to type the code yourself: let Claude write it, and tell it to use `.claude/hooks/HookInput.java` to read the hook JSON.

Overview with slide titles and what each exercise shows: [docs/exercises.md](docs/exercises.md). Steps, test inputs and how you know it worked: [docs/runbook.md](docs/runbook.md).

## Check without an LLM

```bash
java backend/InventoryTest.java
java .claude/skills/agents-md-review/scripts/CheckAgentsMd.java AGENTS.md
```

Test a hook alone (exit code: `echo $?` in Git Bash, `$LASTEXITCODE` in PowerShell):

```bash
echo '{"hook_event_name":"UserPromptSubmit","prompt":"Refund to DE89 3704 0044 0532 0130 00"}' | java .claude/hooks/<Name>.java
```

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `cannot find symbol … class Inventory` (`Symbol nicht gefunden`) | JDK older than 22 | see [Check Java first](#check-java-first) |
| `java -version` still shows 21 after installing a newer JDK | the program your terminal runs in keeps the old `PATH` | close it completely (VS Code: all windows) and start it again |
| `BindException: Address already in use` | port 8000 is taken | Git Bash: `PORT=8001 java backend/Server.java`, PowerShell: `$env:PORT=8001; java backend/Server.java` |
| http://localhost:8000 shows `not found` | server started from another folder, e.g. `backend/` | stop it and start `java backend/Server.java` from the repo root |
| Hook does not run at all | not registered, `settings.json` is invalid JSON, or Claude started before the change | `/hooks` in Claude lists the active hooks; restart `claude` |
| Hook fails with `java: command not found` | Claude Code runs hooks in Git Bash, which does not find `java` | run `java -version` in Git Bash; fix its `PATH` |
| Test command fails in PowerShell | `\` line breaks and `$?` are Bash syntax | one line, exit code via `$LASTEXITCODE` |
| JSON from the hook is ignored | Claude Code reads JSON only on exit 0; on exit 2 it reads stderr | JSON → exit 0, message on stderr → exit 2 |
| Every change to a hook asks for confirmation | that is your harness guard (exercise 3) working | activate it after the other exercises |
| No desktop notification | the OS blocks it | macOS: allow notifications for "Script Editor"; Windows: check Focus Assist |
| Every prompt or edit takes about a second longer | each hook starts a JVM | normal |
| `AGENTS.md is not tracked by git` | the file is not committed | `git add AGENTS.md` and commit |
