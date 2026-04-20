---
name: code-scout
description: Scan app/src/main/** for code smells (large files, long funcs, aged TODOs, duplication) and file Moderate findings as Known gaps or Critical findings as refactor plans. Arg is a path prefix, theme keyword, or "all".
---

Invoke the `code-health-scout` subagent.

- Empty argument → `target: all`.
- A path prefix (starts with `app/` or `docs/`) → pass as `target: <path>`.
- Any other argument (theme keyword) → pass verbatim.

Relay the subagent's final report without paraphrasing. If it drafted plan docs or opened a PR, surface those URLs/paths on the last line.
