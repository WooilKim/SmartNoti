---
name: pr-review
description: Have the project-manager agent review and approve (or request changes on) an agent-opened PR. Arg is a PR number; empty reviews every open agent PR in turn.
---

Invoke the `project-manager` subagent.

- If the argument is empty, tell the subagent `target: all`.
- If the argument is a PR number (digits only, optionally prefixed with `#`), tell the subagent `target: <num>` with the `#` stripped.
- Any other argument (a URL, a branch name, etc.) — pass it verbatim and let the subagent resolve.

Relay the subagent's final report to the user without paraphrasing. Include the review log path (`docs/pr-review-log.md`) on the last line so the user can audit.
