---
name: plan-implement
description: Implement a docs/plans/ file task by task and open a PR. Required arg is the plan's slug or path.
---

Invoke the `plan-implementer` subagent to ship one plan.

- If the argument is empty, tell the user "specify a plan path or slug" and stop — never pick a plan yourself.
- Otherwise pass the argument verbatim as the plan target.

Relay the subagent's final report without paraphrasing. Surface the PR URL on the last line.
