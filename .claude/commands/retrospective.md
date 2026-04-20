---
name: retrospective
description: Run a loop retrospective — history dump, health analysis, or meta-plan proposals. Arg is "history", "analyze" (default), or "propose [focus]".
---

Invoke the `loop-retrospective` subagent.

- Empty argument → `target: analyze`.
- `history` → `target: history`.
- `propose` or `propose <focus>` → pass verbatim.
- `analyze` → `target: analyze`.

Relay the subagent's final report without paraphrasing. If meta-plans were drafted, surface their paths on the last line.
