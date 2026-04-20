---
name: gap-plan
description: Pick the highest-leverage Known gap from docs/journeys/ and draft a new docs/plans/ file. Arg is a journey id, theme keyword, "draft only", or empty for full scan.
---

Invoke the `gap-planner` subagent to draft a new plan doc from the journey Known gaps.

- If the argument is empty, instruct the subagent to scan all journeys.
- Pass the argument verbatim (could be a journey id, theme, or `draft only`).

Relay the subagent's final report without paraphrasing. If a PR URL is returned, surface it on the last line.
