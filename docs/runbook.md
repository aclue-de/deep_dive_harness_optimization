# Runbook

Step by step: what to do, and how you know it worked. Run all commands from the repo root.

**Test it alone** runs your hook the way Claude Code does, without Claude:

1. Claude Code starts the hook and writes a JSON to its stdin that says what is happening, e.g. "the user sent the prompt `Fix the typo`".
2. `echo '{…}'` prints exactly that JSON.
3. `|` feeds it to the hook.
4. `java .claude/hooks/<YourHook>.java` starts the hook; replace `<YourHook>` with your file name.

The command is the same for everyone: Claude Code defines the JSON, and the expected exit code and JSON fields. Only the wording of your messages may differ.

**Live** is the same check in a real Claude session: you give Claude a task that triggers your hook and watch what happens. After every change to `.claude/settings.json`, restart `claude`; `/hooks` lists the active hooks.

**Let Claude write the hook.** You don't have to type the code yourself. Tell Claude the event, what the hook should do, and that it should use `.claude/hooks/HookInput.java` (it reads the hook JSON: `field`, `flag`, `quote`). Then check the result with the commands below.

Commands work in Git Bash and PowerShell. The exit code is `echo $?` in Git Bash and `$LASTEXITCODE` in PowerShell.

## 0. Setup

| Do | It worked if |
|---|---|
| `java -version` | it shows `22` or newer |
| `java backend/InventoryTest.java` | about 250 `[debug]` lines, six `ok` lines, then `OK` |
| `java backend/Server.java` | http://localhost:8000 shows the item list (keep the terminal open, stop with Ctrl+C) |
| `claude` in a second terminal | Claude Code starts in this folder |

## 1. Prompt secret guard (`UserPromptSubmit`, block)

Build a hook that stops a prompt before it reaches the model when it contains an API key, an IBAN, a payment card number or a list of three or more e-mail addresses. Answer with JSON `{"decision": "block", "reason": "…"}` and exit `0`. Mask the value in the reason, e.g. `DE89...****`, so it is not repeated in full.

Tip: check IBANs with the mod-97 checksum and card numbers with the Luhn checksum, so ordinary numbers don't match.

**Test it alone.** With an IBAN (a public test IBAN):

```bash
echo '{"hook_event_name":"UserPromptSubmit","prompt":"Refund to DE89 3704 0044 0532 0130 00"}' | java .claude/hooks/<YourHook>.java
```

It worked if the exit code is `0`, stdout contains `"decision": "block"`, and the IBAN appears only masked.

With `"prompt":"Mail a@example.com, b@example.com and c@example.com"` it must block too. With `"prompt":"Fix the typo"` or a single e-mail address there must be no output.

**Live.** Send Claude the prompt *"Refund 20 EUR to DE89 3704 0044 0532 0130 00."*

It worked if:
- Claude Code shows your reason and the prompt is not sent,
- Claude does not answer, and the prompt does not appear in the conversation.

This is the only hook in the training that checks what **you** type, not what Claude does: the last point before data leaves your machine.

## 2. AGENTS.md gate (`PostToolUse`)

Build a hook that runs `CheckAgentsMd.java` after an edit of `AGENTS.md` and feeds the findings back to Claude, with the instruction to start the subagent `agents-md-reviewer`.

The AGENTS.md in this repo follows the rules, so the hook has nothing to report until an edit breaks a rule.

**Test it alone.**

```bash
echo '{"hook_event_name":"PostToolUse","tool_name":"Edit","tool_input":{"file_path":"AGENTS.md"}}' | java .claude/hooks/<YourHook>.java
```

It worked if the exit code is `0` and there is no output: the file passes the check.

Now break a rule: add the line `- Use @workspace to find context.` under `## Code` in `AGENTS.md` and run the command again. It worked if the exit code is `2` and stderr lists `VIOLATION … tool-specific syntax '@workspace'`. Undo the change with `git checkout -- AGENTS.md`.

**Live.** Ask Claude: *"Add the rule 'Use @workspace to find context.' to the Code section of AGENTS.md."*

It worked if:
- Claude shows the findings after the edit,
- if your hook message names the subagent `agents-md-reviewer`, Claude offers it or starts it.

Undo the change afterwards with `git checkout -- AGENTS.md`.

The hook does not start the subagent itself, a hook can't. It tells Claude, and Claude decides. PostToolUse runs **after** the edit, so the change is already in `AGENTS.md`: the hook cannot stop or undo it, it can only report problems.

## 3. Harness guard (`PreToolUse`, ask)

Build a hook that asks for your OK before Claude changes `.claude/settings*.json` or anything in `.claude/hooks/`, also when you asked for the change yourself. Answer with JSON `"permissionDecision": "ask"` and a `permissionDecisionReason` that starts with *"Are you sure you want Claude to change …?"* and lists the removed and added lines. Add a warning when a hook is removed or permissions get looser. Shell commands that delete, move, copy into or overwrite these files (`rm`, `mv`, `cp`, `>`, `Set-Content`, …) must ask too; reading them must pass. Register it with `"onFailure": "block"`.

Do this exercise last: once it is active, every change to your other hooks asks first.

**Test it alone.**

```bash
echo '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm .claude/hooks/AgentsMdGate.java"}}' | java .claude/hooks/<YourHook>.java
```

It worked if the exit code is `0` and stdout contains `"permissionDecision": "ask"`. With `"command":"cat .claude/settings.json"` there must be no output.

**Live.** Ask Claude: *"Remove the AGENTS.md gate hook from .claude/settings.json."*

It worked if the confirmation dialog shows your question, the removed lines and the hook warning. Decline it.

Discuss: who guards the guards? An agent that can edit `settings.json` can delete the hook that blocks it.

## A. Optional: Notify (`Notification`, async)

Build a hook that shows a desktop notification when Claude waits for you. Title: `Claude Code - <project folder>`, so you know which session is waiting; text: the event's `message`. Use only Java, so it works on Windows and macOS: e.g. `java.awt.SystemTray` on Windows, `osascript` on macOS (pass the text as arguments, never as code), a beep if nothing works. Register it with `"async": true`, so Claude doesn't wait for it.

**Test it alone.**

```bash
echo '{"hook_event_name":"Notification","message":"Claude needs your permission to use Bash"}' | java .claude/hooks/<YourHook>.java
```

It worked if the exit code is `0` and a desktop notification appears. On macOS, the first one may need permission under System Settings > Notifications.

**Live.** Ask Claude: *"Create a file notes.txt with the text hello."* Switch to another window before the permission prompt appears.

It worked if a notification tells you that Claude needs your permission. Decline the edit afterwards.

## 4. Monitor the prompt cache (slide 45)

Needs Claude Code 2.1.260 or later (`claude --version`).

| Do | It worked if |
|---|---|
| `claude --settings tools/status-line/settings.json` | the status line shows `cache: waiting for the first answer` |
| Ask two short questions | `read` grows, `hit` goes up, `misses` stays `0` |
| `/model`, pick another model, ask once more | `hit` drops, `write` jumps, `misses` goes up: the new model cannot use the old cache |

No status line under the input box? Then Claude Code is older than 2.1.260 or cannot start `java`. The same numbers are also in `/usage`: run it after each step and look at the line `Prompt cache (main)`.
