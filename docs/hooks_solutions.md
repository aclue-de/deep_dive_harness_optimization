# Hook Solutions – Trainer Guide

Solutions for the hook exercises of chapter 05 (slide 34). For each hook: what it does and how, the prompt that makes Claude generate it, the code, and the steps to make it run.

| # | Hook | Event · matcher | Answer | What it does |
|---|---|---|---|---|
| 1 | [PromptSecretGuard](#1-promptsecretguard) | `UserPromptSubmit` | `{"decision": "block"}` | Prompts with API keys, IBANs, card numbers or e-mail lists are not sent |
| 2 | [AgentsMdGate](#2-agentsmdgate) | `PostToolUse` · `Edit\|Write` | exit 2, stderr to Claude | After an edit of AGENTS.md: AGENTS.md check, findings and "start `agents-md-reviewer`" back to Claude |
| 3 | [HarnessGuard](#3-harnessguard) | `PreToolUse` · `Edit\|Write\|Bash\|PowerShell` | `"permissionDecision": "ask"` | Changes to hooks or settings need your OK; the dialog lists what changes |
| A | [Notify](#a-notify-optional) (optional) | `Notification` · async | none | Desktop notification when Claude waits for you |

All four are in `.claude/hooks/` and registered in `.claude/settings.json` on this branch. The participants' steps and checks are in [runbook.md](runbook.md); this document is the trainer's side.

## Contents

| Hook | Jump to |
|---|---|
| [1. PromptSecretGuard](#1-promptsecretguard) | [prompt](#prompt-for-claude) · [make it run](#make-it-run) · [solution](#solution) |
| [2. AgentsMdGate](#2-agentsmdgate) | [prompt](#prompt-for-claude-1) · [make it run](#make-it-run-1) · [solution](#solution-1) |
| [3. HarnessGuard](#3-harnessguard) | [prompt](#prompt-for-claude-2) · [make it run](#make-it-run-2) · [solution](#solution-2) |
| [A. Notify (optional)](#a-notify-optional) | [prompt](#prompt-for-claude-3) · [make it run](#make-it-run-3) · [solution](#solution-3) |

Also: [Before you start](#before-you-start) (ground rules, how to make a hook run, HookInput.java, complete settings.json) · [Tests without an LLM](#tests-without-an-llm)

## Before you start

### Ground rules for every hook

- **Java only.** Every hook is started as `java "$CLAUDE_PROJECT_DIR/.claude/hooks/<Name>.java"`. That runs the same on Windows and macOS: no bash scripts, no `jq`, no PowerShell scripts.
- **JDK 25** (22+ is the minimum): the hook and `HookInput.java` are launched together as a multi-file source program, so there is no compile step.
- **Input** is JSON on stdin. **Output** is one of:
  - exit 0, no output: pass
  - exit 0 with JSON: a decision (`permissionDecision` allow/ask/deny, `decision: block`)
  - exit 2 with stderr: feedback to Claude (JSON on stdout is ignored then)
  - for `UserPromptSubmit` and `SessionStart`: plain stdout is added to the context
- **Shell guards** match `Bash|PowerShell`, because Claude Code on Windows can run commands through either tool.
- **Speed:** each hook starts a JVM, about 1 s.

### How to make a hook run (the same for all four)

1. **Generate it.** Start `claude` in the repo root and paste the hook's prompt from below. Claude writes `.claude/hooks/<Name>.java` and adds the entry to `.claude/settings.json`.
2. **Check the files.** `.claude/hooks/<Name>.java` exists, and `.claude/settings.json` is valid JSON with the entry shown below.
3. **Test it alone**, without Claude: pipe the JSON from the hook's "Make it run" steps into the hook. Exit code: `echo $?` in Git Bash, `$LASTEXITCODE` in PowerShell.
4. **Restart `claude`.** Hooks added or changed during a session may only take effect after a restart. Then run `/hooks`: the hook must be listed under its event.
5. **Live check** in Claude, as described for each hook.
6. **All hooks at once:** `java tests/harness/HooksTest.java` (14 tests, last line `OK`).

### Shared helper: HookInput.java

The JDK has no JSON parser. `.claude/hooks/HookInput.java` (provided on `main` too) reads one string field by name (`field`), a boolean flag (`flag`), and quotes strings for JSON output (`quote`). Every prompt below tells Claude to use it; without that hint Claude tends to write its own parser or add a JSON library, which AGENTS.md forbids.

### The complete settings.json

```json
{
  "permissions": {
    "defaultMode": "default"
  },
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/PromptSecretGuard.java\""
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/AgentsMdGate.java\""
          }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Edit|Write|Bash|PowerShell",
        "hooks": [
          {
            "type": "command",
            "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/HarnessGuard.java\"",
            "onFailure": "block"
          }
        ]
      }
    ],
    "Notification": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/Notify.java\"",
            "async": true
          }
        ]
      }
    ]
  }
}
```

[↑ Contents](#contents)

---

## 1. PromptSecretGuard

A prompt that contains a secret or personal data is stopped before it reaches the model. It is the only hook in the training that checks what *you* type, not what Claude does: the last point before data leaves your machine.

| Event | Matcher | Answer |
|---|---|---|
| `UserPromptSubmit` | none | `{"decision": "block", "reason": "…"}`, exit 0 |

### What it does and how

1. Reads `prompt` from the hook JSON and searches it for:
   - **API keys and tokens:** Anthropic, OpenAI, AWS, Google, GitHub, private keys, and `api_key|secret|token|password|passwort` followed by `:` or `=` and a value of 8+ characters
   - **IBANs**, confirmed with the mod-97 checksum, so random text doesn't match
   - **payment card numbers:** Visa, Mastercard or Amex prefix plus the Luhn checksum
   - **e-mail lists:** 3 or more different addresses (a single address, your own, passes)
2. Nothing found → exit 0 without output; the prompt goes through unchanged.
3. Found → `{"decision": "block", "reason": …}` on stdout, exit 0. The prompt is not sent and not kept in the conversation. The reason lists what was found with each value masked (`DE89...****`), so the secret is not repeated in full.

**Limits:** it catches known formats, not every secret. A plain password without a label passes.

### Prompt for Claude

```text
Create a Claude Code hook .claude/hooks/PromptSecretGuard.java for the event UserPromptSubmit.
Single-file Java program, no libraries, uses the existing .claude/hooks/HookInput.java (field("prompt"), quote()).

It blocks a prompt that contains:
- API keys and tokens, one named regex each: Anthropic (sk-ant-...), OpenAI (sk-... / sk-proj-...),
  AWS access key (AKIA + 16 characters), Google API key (AIza + 35 characters), GitHub token (ghp_/gho_/ghu_/
  ghs_/ghr_ + 36 characters), private key (-----BEGIN ... PRIVATE KEY-----), and
  "api_key|secret|token|password|passwort" followed by : or = and a value of 8+ characters (case-insensitive).
- IBANs: regex for country code + 2 digits + 11-30 characters (spaces allowed), confirmed with the mod-97
  checksum (BigInteger). Try shorter lengths too, because the match can run into the next word.
- Payment card numbers: 13-19 digits with optional spaces or dashes, Visa/Mastercard/Amex prefix, Luhn check.
- 3 or more different e-mail addresses (case-insensitive).

Nothing found -> exit 0 without output.
Found -> print {"decision": "block", "reason": "..."} on stdout and exit 0. The reason lists what was found,
masks each value (first 4 characters, then "...****"), and tells the user to remove the value and to refer to
secrets by their environment variable name.

Register it in .claude/settings.json under UserPromptSubmit and test it with piped JSON:
one prompt per finding type, plus one clean prompt and one with a single e-mail address that must pass.
```

### settings.json entry

```json
"UserPromptSubmit": [
  {
    "hooks": [
      { "type": "command", "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/PromptSecretGuard.java\"" }
    ]
  }
]
```

### Make it run

1. Generate the hook with the prompt above, or copy the solution below to `.claude/hooks/PromptSecretGuard.java`.
2. Add the settings.json entry.
3. Test it alone:
   ```bash
   echo '{"hook_event_name":"UserPromptSubmit","prompt":"Refund to DE89 3704 0044 0532 0130 00"}' | java .claude/hooks/PromptSecretGuard.java
   ```
   Expected: exit `0` and `{"decision": "block", "reason": "Prompt not sent: it contains IBAN (DE89...****). …"}`.
   With `"prompt":"Mail a@example.com, b@example.com and c@example.com"`: block too. With `"prompt":"Fix the typo"` or a single e-mail address: no output.
4. Restart `claude`, check `/hooks`.
5. Live: send *"Refund 20 EUR to DE89 3704 0044 0532 0130 00."* Claude Code shows the reason; the prompt is not sent and Claude does not answer.

### Solution

```java
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hook: a prompt with a secret or personal data is stopped before it reaches the model.
 *
 * Event:   UserPromptSubmit, no matcher
 * Flow:    1. Search the prompt for
 *               API keys and tokens,
 *               IBANs (checked with the mod-97 checksum, so random text does not match),
 *               payment card numbers (Visa/Mastercard/Amex prefix plus Luhn checksum),
 *               lists of e-mail addresses (MAX_EMAILS or more different addresses).
 *          2. Nothing found -> exit 0, the prompt goes through unchanged.
 *          3. Found -> JSON {"decision": "block", "reason": ...}. The prompt is not sent and not kept in the
 *             context; the user sees the reason. The reason masks the value, so it is not repeated in full.
 * Teaches: a prompt is input like any other, and the last point before data leaves the machine.
 */
public class PromptSecretGuard {

    static final int MAX_EMAILS = 3;
    static final Map<String, Pattern> KEYS = new LinkedHashMap<>();

    static {
        KEYS.put("Anthropic API key", Pattern.compile("sk-ant-[A-Za-z0-9_-]{20,}"));
        KEYS.put("OpenAI API key", Pattern.compile("sk-(proj-)?[A-Za-z0-9]{32,}"));
        KEYS.put("AWS access key", Pattern.compile("AKIA[0-9A-Z]{16}"));
        KEYS.put("Google API key", Pattern.compile("AIza[0-9A-Za-z_-]{35}"));
        KEYS.put("GitHub token", Pattern.compile("gh[pousr]_[A-Za-z0-9]{36}"));
        KEYS.put("private key", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"));
        KEYS.put("password or secret", Pattern.compile(
                "(?i)(api[_-]?key|secret|token|password|passwort)\\w*\\s*[:=]\\s*[\"']?[^\"'\\s]{8,}"));
    }

    static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30}\\b");
    static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");
    static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    public static void main(String[] args) throws Exception {
        String prompt = HookInput.fromStdin().field("prompt");
        if (prompt == null) {
            System.exit(0);
        }

        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, Pattern> key : KEYS.entrySet()) {
            Matcher m = key.getValue().matcher(prompt);
            if (m.find()) {
                findings.add(key.getKey() + " (" + mask(m.group()) + ")");
            }
        }
        Matcher m = IBAN.matcher(prompt);
        while (m.find()) {
            if (validIban(m.group())) {
                findings.add("IBAN (" + mask(m.group().replace(" ", "")) + ")");
            }
        }
        m = CARD.matcher(prompt);
        while (m.find()) {
            String digits = m.group().replaceAll("[ -]", "");
            if (digits.matches("4\\d{12}(\\d{3})?|5[1-5]\\d{14}|2[2-7]\\d{14}|3[47]\\d{13}") && luhn(digits)) {
                findings.add("payment card number (" + mask(digits) + ")");
            }
        }
        Set<String> emails = new LinkedHashSet<>();
        m = EMAIL.matcher(prompt);
        while (m.find()) {
            emails.add(m.group().toLowerCase(Locale.ROOT));
        }
        if (emails.size() >= MAX_EMAILS) {
            findings.add(emails.size() + " e-mail addresses (personal data)");
        }
        if (findings.isEmpty()) {
            System.exit(0);
        }

        String reason = "Prompt not sent: it contains " + String.join(", ", findings) + ". "
                + "Remove the value and send the prompt again. Refer to secrets by their environment variable name, "
                + "and use made-up test data instead of real personal data.";
        System.out.println("{\"decision\": \"block\", \"reason\": " + HookInput.quote(reason) + "}");
    }

    /** First 4 characters, the rest hidden. */
    static String mask(String value) {
        return value.length() <= 6 ? "***" : value.substring(0, 4) + "..." + "*".repeat(4);
    }

    /** The match may run into a following word ("DE89... AND"), so try every length from long to short. */
    static boolean validIban(String text) {
        String compact = text.replace(" ", "");
        for (int length = Math.min(34, compact.length()); length >= 15; length--) {
            if (checksum(compact.substring(0, length))) {
                return true;
            }
        }
        return false;
    }

    static boolean checksum(String iban) {
        StringBuilder digits = new StringBuilder();
        for (char c : (iban.substring(4) + iban.substring(0, 4)).toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
```

[↑ Contents](#contents)

---

## 2. AgentsMdGate

After every edit of `AGENTS.md`, the hook runs the deterministic AGENTS.md check. On violations it sends the findings back to Claude and tells it to start the `agents-md-reviewer` subagent.

| Event | Matcher | Answer |
|---|---|---|
| `PostToolUse` | `Edit\|Write` | exit 2, stderr goes to Claude |

### What it does and how

1. Reads `file_path`. Not a file named `AGENTS.md` → exit 0, nothing happens.
2. Runs `.claude/skills/agents-md-review/scripts/CheckAgentsMd.java` on the file. It checks the size (max 200 lines), tool-specific syntax (`@workspace`, `#file`), @-imports, the Always / Ask first / Never tiers and the failure protocol. Exit 0 = passed → the hook exits 0.
3. Violations → the findings plus "Next step: start the subagent 'agents-md-reviewer' …" on stderr, exit 2. Only exit 2 sends stderr back to Claude.

**Important:**
- The hook does **not** start the subagent; a hook can't. It instructs Claude, and Claude decides to call the Agent tool. The `description` of `.claude/agents/agents-md-reviewer.md` ("Use when the AGENTS.md gate hook reports violations") makes that likely.
- It is `PostToolUse`: the edit has already happened. The hook can't stop or undo it, only report.
- The AGENTS.md in this repo follows the rules, so the hook stays silent until an edit breaks a rule.

**Prerequisites** (already in the repo on `main`): the skill `.claude/skills/agents-md-review/` with `scripts/CheckAgentsMd.java`, and the subagent `.claude/agents/agents-md-reviewer.md`.

### Prompt for Claude

```text
Create a Claude Code hook .claude/hooks/AgentsMdGate.java for the event PostToolUse with matcher Edit|Write.
Single-file Java program, no libraries, uses the existing .claude/hooks/HookInput.java (field("file_path")).

Behavior:
- If the edited file's name is not AGENTS.md, exit 0.
- Otherwise run the existing check script
  $CLAUDE_PROJECT_DIR/.claude/skills/agents-md-review/scripts/CheckAgentsMd.java with the file path as
  argument (java <script> <path>), stdout and stderr merged. Exit code 0 means passed -> exit 0.
- On violations, print the findings to stderr, then the line:
  "Next step: start the subagent 'agents-md-reviewer' for the full analysis (what, where, which rule,
  proposal). Only change AGENTS.md after the user has agreed to the proposals."
  and exit with code 2, so the feedback reaches Claude.

Register it in .claude/settings.json under PostToolUse with matcher Edit|Write and test it with piped JSON
for AGENTS.md and for another file.
```

### settings.json entry

```json
"PostToolUse": [
  {
    "matcher": "Edit|Write",
    "hooks": [
      { "type": "command", "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/AgentsMdGate.java\"" }
    ]
  }
]
```

### Make it run

1. Generate the hook with the prompt above, or copy the solution below to `.claude/hooks/AgentsMdGate.java`.
2. Add the settings.json entry.
3. Test it alone:
   ```bash
   echo '{"hook_event_name":"PostToolUse","tool_name":"Edit","tool_input":{"file_path":"AGENTS.md"}}' | java .claude/hooks/AgentsMdGate.java
   ```
   Expected: exit `0`, no output (the file is compliant).
4. Break a rule: add `- Use @workspace to find context.` under `## Code` in `AGENTS.md` and run the command again. Expected: exit `2`, stderr lists `VIOLATION … tool-specific syntax '@workspace'` and the "Next step" line. Undo with `git checkout -- AGENTS.md`.
5. Restart `claude`, check `/hooks`.
6. Live: *"Add the rule 'Use @workspace to find context.' to the Code section of AGENTS.md."* Claude shows the findings after the edit and offers or starts `agents-md-reviewer`. Undo with `git checkout -- AGENTS.md`.

### Solution

```java
import java.nio.file.Path;
import java.util.List;

/**
 * Hook: AGENTS.md gate for AGENTS.md.
 *
 * Event:  PostToolUse, matcher Edit|Write
 * Flow:   1. Not about AGENTS.md -> exit 0, nothing happens.
 *         2. Deterministic AGENTS.md checks (script from the skill agents-md-review).
 *         3. Violation -> exit 2. stderr goes to Claude: findings plus the instruction
 *            to start the subagent agents-md-reviewer for the content analysis.
 *
 * So the subagent is only started when the deterministic checks fail.
 * PostToolUse cannot undo the edit; it gives Claude feedback.
 */
public class AgentsMdGate {

    public static void main(String[] args) throws Exception {
        String path = HookInput.fromStdin().field("file_path");
        if (path == null || !Path.of(path).getFileName().toString().equals("AGENTS.md")) {
            System.exit(0);
        }

        Path script = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", "."),
                ".claude", "skills", "agents-md-review", "scripts", "CheckAgentsMd.java");
        Process check = new ProcessBuilder(List.of("java", script.toString(), path))
                .redirectErrorStream(true).start();
        String findings = new String(check.getInputStream().readAllBytes());
        if (check.waitFor() == 0) {
            System.exit(0);
        }

        System.err.println(findings.strip());
        System.err.println();
        System.err.println("Next step: start the subagent 'agents-md-reviewer' for the full analysis "
                + "(what, where, which rule, proposal). Only change AGENTS.md "
                + "after the user has agreed to the proposals.");
        System.exit(2); // only exit 2 sends stderr back to Claude
    }
}
```

[↑ Contents](#contents)

---

## 3. HarnessGuard

Claude can't quietly change or switch off its own hooks and settings: every change asks for your OK, and the dialog says what changes. Do it last: once it is active, every change to the other hooks asks first.

| Event | Matcher | Answer |
|---|---|---|
| `PreToolUse` | `Edit\|Write\|Bash\|PowerShell` | `"permissionDecision": "ask"`, registered with `"onFailure": "block"` |

### What it does and how

1. **Edit or Write** on `.claude/settings*.json` or anything in `.claude/hooks/` → `ask`, also when you asked for the change yourself. The reason starts with *"Are you sure you want Claude to change <file>?"* and shows:
   - the removed and added lines (unchanged lines and bare brackets left out, at most 8 each). For `Write` the hook compares with the file on disk.
   - `WARNING: this removes or changes a hook.` if a removed line contains a hook command
   - `WARNING: this loosens permissions or fail-closed protection.` if `bypassPermissions`, `acceptEdits`, `dontAsk` or `"allow"` is added, or `"deny"` or `onFailure` is removed
2. **Shell command:** split at `&&`, `||`, `;`, `|` and checked part by part. It asks if a harness path is
   - the target of a redirect: `echo {} > .claude/settings.json`
   - the destination of a copy: `cp x .claude/hooks/` (copying *from* the harness passes)
   - an argument of a changing command: `rm`, `mv`, `tee`, `sed -i`, `Set-Content`, `Remove-Item`, …
   - an argument of `git rm`, `git mv`, `git checkout` or `git restore`
3. Reading (`cat .claude/settings.json`, also with `2>/dev/null`) and everything else → exit 0, no output.

The dialog itself is Claude Code's permission dialog: the hook supplies the text, Claude Code the buttons. **Yes** → the change is made. **No** → it is not made, Claude gets "the user doesn't want to proceed" and stops. The hook never sees which button was clicked, and the order of the buttons can't be changed.

`"onFailure": "block"` makes it fail closed: if the hook can't run (deleted, broken, wrong JDK), every matching tool call is blocked instead of let through.

Example dialog:

```text
Are you sure you want Claude to change settings.json? It configures the hooks and permissions that guard this session.
WARNING: this removes or changes a hook.
Removed:
  - "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/AgentsMdGate.java\""
Allow only if you asked for exactly this change.
```

**Why:** who guards the guards? An agent that may edit `settings.json` can delete the hook that blocks it.
**Limits:** the shell check is a heuristic. `cd .claude && rm hooks/x`, or a script that writes the file, isn't recognized.

### Prompt for Claude

```text
Create a Claude Code hook .claude/hooks/HarnessGuard.java for PreToolUse with matcher Edit|Write|Bash|PowerShell.
Single-file Java program, no libraries, uses the existing .claude/hooks/HookInput.java
(field("tool_name"), field("file_path"), field("command"), field("old_string"), field("new_string"),
field("content"), quote()).

Goal: Claude must not change its own hooks or settings without the user's confirmation.

1. Edit or Write on a file matching (^|/)\.claude/(settings[^/]*\.json$|hooks/) (normalize \ to /):
   answer with permissionDecision "ask". The permissionDecisionReason starts with
   "Are you sure you want Claude to change <file name>?" and then summarizes the change:
   - Edit: compare old_string and new_string. Write: compare the file on disk (resolve relative paths against
     $CLAUDE_PROJECT_DIR) with content.
   - List removed and added lines (trimmed, skip empty lines and lines that are only brackets or commas),
     at most 8 each, lines cut at 100 characters.
   - Add "WARNING: this removes or changes a hook." if a removed line contains .claude/hooks/ or "command".
   - Add "WARNING: this loosens permissions or fail-closed protection." if an added line contains
     bypassPermissions, acceptEdits, "allow" or dontAsk, or a removed line contains "deny" or onFailure.
   - End with "Allow only if you asked for exactly this change."
2. Bash or PowerShell: only if the command mentions .claude/settings or .claude/hooks (/ or \).
   Split it at && || ; | and newlines and check each part. Answer "ask" if in that part a harness path is
   - the target of a > or >> redirect,
   - the last path argument of cp/copy/Copy-Item (the destination; copying FROM the harness passes),
   - an argument of rm, del, mv, move, Remove-Item, Move-Item, Rename-Item, Set-Content, Out-File,
     Add-Content, Clear-Content, New-Item, tee, truncate, chmod, touch (case-insensitive, strip .exe and paths),
   - an argument of sed -i or perl -i,
   - an argument of git rm, git mv, git checkout or git restore.
   Skip quotes, leading VAR=value assignments and the PowerShell call operator &.
   Reading, e.g. cat .claude/settings.json 2>/dev/null, must pass.
3. Everything else: exit 0 without output.

Output format for ask:
{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "ask", "permissionDecisionReason": "..."}}

Register it in .claude/settings.json under PreToolUse with matcher Edit|Write|Bash|PowerShell and
"onFailure": "block". Test it with piped JSON: rm of a hook (ask), cat of settings (pass),
cp from hooks to elsewhere (pass), cp into hooks (ask), an Edit that removes a hook command (ask + WARNING).
```

### settings.json entry

```json
"PreToolUse": [
  {
    "matcher": "Edit|Write|Bash|PowerShell",
    "hooks": [
      {
        "type": "command",
        "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/HarnessGuard.java\"",
        "onFailure": "block"
      }
    ]
  }
]
```

### Make it run

1. Do this after hooks 1 and 2: from now on every change to them asks first.
2. Generate the hook with the prompt above, or copy the solution below to `.claude/hooks/HarnessGuard.java`.
3. Test it alone:
   ```bash
   echo '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm .claude/hooks/AgentsMdGate.java"}}' | java .claude/hooks/HarnessGuard.java
   ```
   Expected: exit `0` and `"permissionDecision": "ask"` on stdout. With `"command":"cat .claude/settings.json"`: no output.
4. Restart `claude`, check `/hooks`. Without the restart the hook may not be active yet, and changes go through without the dialog.
5. Live: *"Remove the AGENTS.md gate hook from .claude/settings.json."* The confirmation dialog shows the question, the removed lines and the hook warning. Click No: nothing changes and Claude stops.
6. Fail closed (optional demo): rename `HarnessGuard.java` in your editor and ask Claude for any edit. It is blocked with `failed; blocking because onFailure is "block"`. Rename it back.

### Solution

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hook: Claude may not switch off its own guards without the user noticing.
 *
 * Event:     PreToolUse, matcher Edit|Write|Bash|PowerShell, "onFailure": "block"
 * Flow:      1. Edit/Write on .claude/settings*.json or .claude/hooks/** -> "ask", also when the user asked for it.
 *               The dialog says what changes: removed and added lines (max 8 each), plus a WARNING if a hook is
 *               removed or changed, or permissions get looser (bypassPermissions, "allow", no "deny", no onFailure).
 *            2. Shell command: each part (split at && || ; |) is checked on its own. "ask" if the harness path is
 *                 the target of a redirect               echo {} > .claude/settings.json
 *                 the destination of a copy              cp x .claude/hooks/      (copying FROM it passes)
 *                 an argument of a changing command      rm, mv, tee, sed -i, Set-Content, Remove-Item, ...
 *                 an argument of git rm/mv/checkout/restore
 *            3. Anything else -> exit 0, no output. Reading passes, also with "2>/dev/null" in the command.
 * Teaches:   "who guards the guards?". An agent that may edit settings.json can delete the hook that blocks it.
 * Gap:       a heuristic: "cd .claude && rm hooks/x" or a script that writes the file are not recognised.
 */
public class HarnessGuard {

    static final Pattern HARNESS_FILE = Pattern.compile("(^|/)\\.claude/(settings[^/]*\\.json$|hooks/)");
    static final Pattern HARNESS = Pattern.compile("\\.claude[/\\\\]+(settings|hooks)");
    static final Pattern REDIRECT_TO_HARNESS = Pattern.compile(
            ">{1,2}\\s*['\"]?[^\\s;&|>]*\\.claude[/\\\\]+(settings|hooks)");
    static final Pattern SEGMENTS = Pattern.compile("&&|\\|\\||[;|\\n]");
    static final Pattern ASSIGNMENT = Pattern.compile("\\w+=\\S*");
    static final Pattern LOOSER = Pattern.compile("bypassPermissions|acceptEdits|\"allow\"|dontAsk");
    static final int MAX_LINES = 8;

    static final Set<String> COPY = Set.of("cp", "copy", "copy-item", "cpi");
    static final Set<String> CHANGE = Set.of("rm", "del", "erase", "mv", "move", "remove-item", "ri", "move-item",
            "mi", "rename-item", "ren", "set-content", "out-file", "add-content", "clear-content", "new-item", "ni",
            "tee", "truncate", "chmod", "touch");
    static final Set<String> GIT_CHANGE = Set.of("rm", "mv", "checkout", "restore");

    public static void main(String[] args) throws Exception {
        HookInput input = HookInput.fromStdin();
        String tool = input.field("tool_name");

        if ("Edit".equals(tool) || "Write".equals(tool)) {
            String file = input.field("file_path");
            if (file != null && HARNESS_FILE.matcher(file.replace('\\', '/')).find()) {
                askWithChanges(file, input, "Write".equals(tool));
            }
            System.exit(0);
        }

        String command = input.field("command");
        if (command == null || !HARNESS.matcher(command).find()) {
            System.exit(0);
        }
        for (String segment : SEGMENTS.split(command)) {
            if (HARNESS.matcher(segment).find() && changesHarness(segment)) {
                ask(segment.strip());
            }
        }
        System.exit(0);
    }

    static boolean changesHarness(String segment) {
        if (REDIRECT_TO_HARNESS.matcher(segment).find()) {
            return true;
        }
        List<String> words = words(segment);
        if (words.isEmpty()) {
            return false;
        }
        String program = words.get(0).toLowerCase().replaceFirst("^.*[/\\\\]", "").replaceFirst("\\.exe$", "");
        List<String> arguments = words.subList(1, words.size());
        List<String> paths = arguments.stream().filter(a -> !a.startsWith("-")).toList();

        if (COPY.contains(program)) {
            return !paths.isEmpty() && HARNESS.matcher(paths.get(paths.size() - 1)).find(); // destination
        }
        if (CHANGE.contains(program)) {
            return paths.stream().anyMatch(p -> HARNESS.matcher(p).find());
        }
        if (program.equals("sed") || program.equals("perl")) {
            boolean inPlace = arguments.stream().anyMatch(a -> a.matches("-[a-z]*i.*"));
            return inPlace && paths.stream().anyMatch(p -> HARNESS.matcher(p).find());
        }
        if (program.equals("git") && !paths.isEmpty() && GIT_CHANGE.contains(paths.get(0))) {
            return paths.stream().anyMatch(p -> HARNESS.matcher(p).find());
        }
        return false;
    }

    /** Words of a command part without quotes, VAR=value prefixes and the PowerShell call operator. */
    static List<String> words(String segment) {
        List<String> words = new ArrayList<>();
        for (String word : segment.trim().split("\\s+")) {
            String token = word.replace("\"", "").replace("'", "");
            if (token.isEmpty() || (words.isEmpty() && (token.equals("&") || ASSIGNMENT.matcher(token).matches()))) {
                continue;
            }
            words.add(token);
        }
        return words;
    }

    /**
     * "ask" with a short summary for the confirmation dialog: which lines go, which come, and a warning
     * if the change removes a hook or loosens permissions. Edit: old_string vs new_string.
     * Write: the file on disk vs the new content.
     */
    static void askWithChanges(String file, HookInput input, boolean write) {
        String before;
        String after;
        if (write) {
            Path path = Path.of(file);
            if (!path.isAbsolute()) {
                path = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", ".")).resolve(path);
            }
            try {
                before = Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
            } catch (Exception e) {
                before = "";
            }
            after = input.field("content");
        } else {
            before = input.field("old_string");
            after = input.field("new_string");
        }
        List<String> removed = onlyIn(before, after);
        List<String> added = onlyIn(after, before);

        StringBuilder reason = new StringBuilder("Are you sure you want Claude to change ")
                .append(Path.of(file).getFileName()).append("? It configures the hooks and permissions that guard "
                        + "this session.");
        if (removed.stream().anyMatch(l -> l.contains(".claude/hooks/") || l.contains("\"command\""))) {
            reason.append("\nWARNING: this removes or changes a hook.");
        }
        if (added.stream().anyMatch(l -> LOOSER.matcher(l).find())
                || removed.stream().anyMatch(l -> l.contains("\"deny\"") || l.contains("onFailure"))) {
            reason.append("\nWARNING: this loosens permissions or fail-closed protection.");
        }
        reason.append(summary("Removed", "- ", removed)).append(summary("Added", "+ ", added));
        if (removed.isEmpty() && added.isEmpty()) {
            reason.append("\nNo visible change (only whitespace or brackets).");
        }
        reason.append("\nAllow only if you asked for exactly this change.");
        System.out.println("{\"hookSpecificOutput\": {\"hookEventName\": \"PreToolUse\", "
                + "\"permissionDecision\": \"ask\", \"permissionDecisionReason\": " + HookInput.quote(reason.toString())
                + "}}");
        System.exit(0);
    }

    /** Lines of "text" that are not in "other", trimmed, without lines that are only brackets or commas. */
    static List<String> onlyIn(String text, String other) {
        List<String> otherLines = new ArrayList<>(lines(other));
        List<String> result = new ArrayList<>();
        for (String line : lines(text)) {
            if (!otherLines.remove(line)) {
                result.add(line);
            }
        }
        return result;
    }

    static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.matches("[\\[\\]{}(),;]+")).toList();
    }

    static String summary(String title, String prefix, List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("\n").append(title).append(":");
        for (String line : lines.subList(0, Math.min(MAX_LINES, lines.size()))) {
            out.append("\n  ").append(prefix).append(line.length() > 100 ? line.substring(0, 97) + "..." : line);
        }
        if (lines.size() > MAX_LINES) {
            out.append("\n  ... and ").append(lines.size() - MAX_LINES).append(" more lines");
        }
        return out.toString();
    }

    static void ask(String target) {
        String reason = "This changes the harness that guards this session (hooks or settings): " + target + ". "
                + "A hook could be switched off this way. Tell the user what you want to change and why.";
        System.out.println("{\"hookSpecificOutput\": {\"hookEventName\": \"PreToolUse\", "
                + "\"permissionDecision\": \"ask\", \"permissionDecisionReason\": " + HookInput.quote(reason) + "}}");
        System.exit(0);
    }
}
```

[↑ Contents](#contents)

---

## A. Notify (optional)

A desktop notification appears when Claude waits for you (a permission prompt, or idle for input), so you don't have to keep watching the terminal.

| Event | Matcher | Answer |
|---|---|---|
| `Notification` | none | none, only shows a notice; registered with `"async": true` |

### What it does and how

1. Claude Code fires `Notification` when it needs a permission or has been waiting for input. The event carries `message`.
2. Title: `Claude Code - <project folder>`, so you know which session is waiting. Messages over 200 characters are cut.
3. It shows the notice the way the OS supports it:
   - **Windows:** a tray notification through `java.awt.SystemTray` (pure Java, about 6 s visible)
   - **macOS:** `osascript` `display notification`; title and text are passed as arguments, never as code
   - **Linux:** `notify-send`, if installed
   - otherwise a system beep
4. Always exit 0, never blocks. `CLAUDE_NOTIFY=off` disables it; `NOTIFY_DRY_RUN=1` prints `title | message` instead (used by the tests).

**Limits:** register it with `"async": true`, or Claude waits about 6 s on Windows. On macOS the first notification may need permission under System Settings > Notifications ("Script Editor"). On Windows, Focus Assist can hide it.

### Prompt for Claude

```text
Create a Claude Code hook .claude/hooks/Notify.java for the event Notification.
Single-file Java program, no libraries, uses the existing .claude/hooks/HookInput.java (field("message"), field("title")).
It must work on Windows and macOS without any extra tool or script.

Behavior:
- If the environment variable CLAUDE_NOTIFY is "off", exit 0.
- Title: the event's "title" if present, otherwise "Claude Code - <name of the $CLAUDE_PROJECT_DIR folder>".
  Message: "message", otherwise "Claude is waiting for you"; cut at 200 characters.
- If NOTIFY_DRY_RUN=1, print "title | message" and exit 0 (for tests).
- Windows: java.awt.SystemTray + TrayIcon.displayMessage with a small icon drawn in code, keep it 6 seconds,
  then remove the icon. Check GraphicsEnvironment.isHeadless() and SystemTray.isSupported() first.
- macOS: run osascript with "on run argv" / "display notification (item 2 of argv) with title (item 1 of argv)";
  pass title and message as arguments, never build AppleScript code from the text.
- Linux: notify-send if /usr/bin/notify-send exists.
- If nothing worked: Toolkit beep. Always end with System.exit(0), because AWT keeps the JVM alive.

Register it in .claude/settings.json under Notification with "async": true, and test it with piped JSON.
```

### settings.json entry

```json
"Notification": [
  {
    "hooks": [
      { "type": "command", "command": "java \"$CLAUDE_PROJECT_DIR/.claude/hooks/Notify.java\"", "async": true }
    ]
  }
]
```

### Make it run

1. Generate the hook with the prompt above, or copy the solution below to `.claude/hooks/Notify.java`.
2. Add the settings.json entry with `"async": true`.
3. Test it alone:
   ```bash
   echo '{"hook_event_name":"Notification","message":"Claude needs your permission to use Bash"}' | java .claude/hooks/Notify.java
   ```
   Expected: exit `0` and a desktop notification "Claude Code - \<folder>". To check only the text: Git Bash `NOTIFY_DRY_RUN=1 java …`, PowerShell `$env:NOTIFY_DRY_RUN=1; java …` prints `Claude Code - <folder> | Claude needs your permission to use Bash`.
4. Restart `claude`, check `/hooks`.
5. Live: *"Create a file notes.txt with the text hello."* Switch to another window before the permission prompt appears. A notification tells you Claude needs your permission. Decline the edit.

### Solution

```java
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hook: desktop notification when Claude waits for you (a permission prompt, or idle for input).
 *
 * Event:   Notification, no matcher, "async": true (showing the notice takes a few seconds; Claude must not wait)
 * Flow:    1. Read "message" (and "title" if present) from the event. Title: "Claude Code - <project folder>",
 *             so you know which of several sessions is waiting.
 *          2. Show it the way the OS supports it:
 *               Windows -> Java AWT tray notification (pure Java, no extra tool)
 *               macOS   -> osascript "display notification" (built into macOS; text passed as arguments, not code)
 *               Linux   -> notify-send, if installed
 *             If none works: a system beep.
 *          3. Never blocks, never decides: exit 0.
 * Config:  CLAUDE_NOTIFY=off disables it. NOTIFY_DRY_RUN=1 prints "title | message" instead (used by the tests).
 */
public class Notify {

    static final int MAX_LENGTH = 200;
    static final int SHOW_MILLIS = 6000;

    public static void main(String[] args) throws Exception {
        HookInput input = HookInput.fromStdin();
        if ("off".equalsIgnoreCase(System.getenv("CLAUDE_NOTIFY"))) {
            System.exit(0);
        }

        String project = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", "."))
                .toAbsolutePath().normalize().getFileName().toString();
        String title = input.field("title") != null ? input.field("title") : "Claude Code - " + project;
        String message = input.field("message") != null ? input.field("message") : "Claude is waiting for you";
        if (message.length() > MAX_LENGTH) {
            message = message.substring(0, MAX_LENGTH - 3) + "...";
        }

        if ("1".equals(System.getenv("NOTIFY_DRY_RUN"))) {
            System.out.println(title + " | " + message);
            System.exit(0);
        }

        String os = System.getProperty("os.name").toLowerCase();
        boolean shown;
        if (os.contains("mac")) {
            shown = run("osascript", "-e", "on run argv", "-e",
                    "display notification (item 2 of argv) with title (item 1 of argv)", "-e", "end run",
                    title, message);
        } else if (os.contains("win")) {
            shown = tray(title, message);
        } else {
            shown = Files.isExecutable(Path.of("/usr/bin/notify-send")) && run("notify-send", title, message);
        }
        if (!shown) {
            beep();
        }
        System.exit(0); // AWT keeps the JVM alive otherwise
    }

    /** Windows: balloon/toast through the system tray, shown for a few seconds, then removed again. */
    static boolean tray(String title, String message) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                return false;
            }
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xD9, 0x77, 0x57));
            g.fillOval(1, 1, 14, 14);
            g.dispose();
            TrayIcon icon = new TrayIcon(image, title);
            icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            Thread.sleep(SHOW_MILLIS);
            SystemTray.getSystemTray().remove(icon);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void beep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception | Error e) {
            // headless: nothing we can do
        }
    }
}
```

[↑ Contents](#contents)

---

## Tests without an LLM

`tests/harness/HooksTest.java` runs every hook with fixed JSON input, in a temporary folder, and checks exit code and output. No Claude, no network.

```bash
java tests/harness/HooksTest.java
```

Expected: 14 `ok` lines and `OK` (exit code 0). It covers:
- PromptSecretGuard: the four block cases, masking, ordinary prompts passing
- AgentsMdGate: the compliant AGENTS.md, AGENTS.md with the `@workspace` line, another file
- HarnessGuard: Edit, Write and shell commands, the warnings, reading passing
- Notify: title and message in dry-run mode, long messages, `CLAUDE_NOTIFY=off`

[↑ Contents](#contents)
