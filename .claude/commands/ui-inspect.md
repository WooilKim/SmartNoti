---
name: ui-inspect
description: Sweep the running app's UI against .claude/rules/ui-improvement.md and record findings on relevant journey Known gaps or a new plan doc. Arg is a journey id, "all", or a theme keyword; empty defaults to "all".
---

Invoke the `ui-ux-inspector` subagent.

- If the argument is empty, pass `target: all`.
- Otherwise pass `target: <argument>` verbatim.

Relay the subagent's final report to the user without paraphrasing. If it opened a PR or drafted a plan, surface those URLs / paths on the last line.
