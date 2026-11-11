# Exercises

The exercises come from the slides of the deep dive; this repo provides what they need. Each row links to the steps in the [runbook](runbook.md). Before the first exercise: [0. Setup](runbook.md#0-setup).

| Chapter | Slide | Exercise | What it shows | Steps |
|---|---|---|---|---|
| 05 Hooks | 34 | 1 Prompt secret guard | A `UserPromptSubmit` hook blocks: a prompt with an API key, IBAN, card number or e-mail list never reaches the model | [1. Prompt secret guard](runbook.md#1-prompt-secret-guard-userpromptsubmit-block) |
| 05 Hooks | 34 | 2 AGENTS.md gate | A `PostToolUse` hook checks AGENTS.md after every edit, reports violations back, and Claude starts the reviewer subagent | [2. AGENTS.md gate](runbook.md#2-agentsmd-gate-posttooluse) |
| 05 Hooks | 34 | 3 Harness guard | A `PreToolUse` hook with `ask`: Claude may not change its own hooks or settings without your OK, and the dialog says what changes | [3. Harness guard](runbook.md#3-harness-guard-pretooluse-ask) |
| 05 Hooks | 34 | A Notify (optional) | A `Notification` hook with `async`: a desktop notice when Claude waits for you | [A. Notify](runbook.md#a-optional-notify-notification-async) |
| 06 Context management | 45 "Exercise: Monitor the prompt cache" | Warm up the cache, switch the model, watch the status line | Switching the model breaks the cache, and that costs tokens | [4. Monitor the prompt cache](runbook.md#4-monitor-the-prompt-cache-slide-45) |

The optional exercise A is for fast groups or for later. Do exercise 3 last: once it is active, every change to your other hooks asks first.
