---
name: feature-report
description: Run the feature-reporter agent to update docs/feature-report.md with newly shipped features. Default mode is `update`; pass `rebuild`, `since YYYY-MM-DD`, or an area name to override.
---

Spawn the `feature-reporter` subagent (`subagent_type: feature-reporter`) with the user-supplied input as the prompt.

If the user gave no argument, pass `update` as the input.

If the user passed:
- `rebuild` — full regeneration from scratch
- `since YYYY-MM-DD` — update window starting at that date
- a single word matching a capability area (e.g. `categories`, `digest`, `quiet-hours`) — update only that section
- anything else — pass it verbatim and let the agent interpret

Wait for the agent's reply (≤ 100 words per its contract) and surface it verbatim. Do not re-summarize.

If the agent opens a PR, that PR is docs-only and should auto-merge under the `project-manager` carve-out same as plan / gap-planner PRs. Do not call PM yourself — the next `/journey-loop` tick's Phase B will pick it up.

If the agent returns `NOOP`, the report is already current. Nothing else to do.
