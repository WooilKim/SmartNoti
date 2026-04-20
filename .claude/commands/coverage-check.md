---
name: coverage-check
description: Analyze test-to-prod ratio on a PR and post an informational signal. Arg is a PR number; empty sweeps every open agent + code-touching PR.
---

Invoke the `coverage-guardian` subagent.

- Empty argument → `target: all`.
- Numeric argument (`#12` or `12`) → `target: <num>` with `#` stripped.
- Any other argument → pass verbatim and let the subagent resolve.

Relay the subagent's final report without paraphrasing. Include the `docs/coverage-log.md` path on the last line.
