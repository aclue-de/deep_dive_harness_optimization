# AGENTS.md

This file contains instructions for AI coding agents that work in this repository.

## Domain

Warehouse app: create items (SKU, name, quantity) and book goods in and out. Data lives in memory only.
Invariants: stock never becomes negative, SKUs are unique, a booking delta is never 0.

## Boundaries

**Always:**
- Run `java backend/InventoryTest.java` after every change in `backend/` and keep it green (exit code 0, last line `OK`).

**Ask first:**
- Changing `backend/InventoryTest.java`: the tests are the specification of the invariants.
- Changing the API contract in `backend/Server.java` (paths, methods, JSON fields) that `frontend/app.js` relies on.
- Adding a build tool (Maven, Gradle, npm), a framework or any external library.

**Never:**
- Commit API keys or tokens as literals in code or config (e.g. `SUPPLIER_API_KEY`); read them with `System.getenv(...)`. Never commit `.env`.
- Run `git push --force` on `main`.
- Delete, comment out or weaken a test in `backend/InventoryTest.java` to make the run green.

## Build & Test

- No build tool: no `mvn`, no `gradle`. Every program runs from source, e.g. `java backend/Server.java`.
- Run all `java` commands from the repo root; the server resolves `frontend/` relative to the working directory.

## Stack

- Derive versions from the source and config files, not from training-data defaults. JDK 22+ is required; no build file states it.

## Code

- `frontend/`: make every API call through `call()` in `app.js`, then re-read the list with `load()`.
- `frontend/`: insert item data with `textContent` or `createElement`, never `innerHTML`. `row()` still does: known XSS debt, do not extend it.
- `backend/`: domain rules live in `Inventory.java`; `Server.java` only parses, routes and maps `InventoryException` to HTTP 400 `{"error": "..."}`.
- `backend/`: JSON is hand-written: use `stringField`, `intField`, `toJson` and `error` in `Server.java`; no JSON library.
- Indent with 4 spaces in Java and 2 spaces in JavaScript; keep lines under 120 characters.

## Failure Protocol

- After 3 failed attempts to fix the same problem, stop. Do not try a fourth time.
- Then escalate with a failure report:
  1. Write `docs/failures/<yyyy-mm-dd>-<topic>.md` with the sections `Goal`, `Attempts` (one line per attempt: change and result), `Exact error` (verbatim, in a code block) and `Needed input`.
  2. Open an issue: `gh issue create --label agent-stuck --title "<topic>" --body-file docs/failures/<file>.md`.
  3. In the chat, link the issue and ask the one specific question from `Needed input`.
