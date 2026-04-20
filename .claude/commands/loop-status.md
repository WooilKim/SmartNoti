---
name: loop-status
description: Report the improvement loop's health, or stop / resume / tune it. Arg is empty (or "status"), "stop [reason]", "resume", or "tune <key>=<value>".
---

Invoke the `loop-manager` subagent.

- If the argument is empty or starts with `status`, pass `target: status`.
- If the argument starts with `stop`, pass `target: stop` and any remaining text as the reason.
- If the argument is `resume`, pass `target: resume`.
- If the argument is `tune <key>=<value>` (or `tune <key> <value>`), pass `target: tune <key>=<value>` verbatim.
- Anything else — pass verbatim and let the subagent refuse if unsupported.

Relay the subagent's final report without paraphrasing. If any PR was opened (pause / resume / tune), surface its URL on the last line.
