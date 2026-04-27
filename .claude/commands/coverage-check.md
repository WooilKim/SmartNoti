---
name: coverage-check
description: "[DEPRECATED 2026-04-27] coverage-guardian agent decommissioned per PR #499. PM + plan-implementer tests-first gate covers the equivalent signal. Spec archived at .claude/agents-archive/coverage-guardian.md."
---

> **[DEPRECATED 2026-04-27]** The `coverage-guardian` subagent was decommissioned per [`docs/plans/2026-04-27-meta-coverage-guardian-decommission-or-auto-trigger.md`](../../docs/plans/2026-04-27-meta-coverage-guardian-decommission-or-auto-trigger.md) (PR #499, Option A). Audit findings: 0 invocations / 0 PM consumption / signal ~85% redundant with PM Quality gate + plan-implementer tests-first discipline. This slash command is left in place as a deprecation tombstone — invoking it will fail because the subagent spec lives at `.claude/agents-archive/coverage-guardian.md` and is no longer registered. Resurrection path: `git mv .claude/agents-archive/coverage-guardian.md .claude/agents/coverage-guardian.md` and revert this file.

~~Invoke the `coverage-guardian` subagent.~~

~~- Empty argument → `target: all`.~~
~~- Numeric argument (`#12` or `12`) → `target: <num>` with `#` stripped.~~
~~- Any other argument → pass verbatim and let the subagent resolve.~~

~~Relay the subagent's final report without paraphrasing. Include the `docs/coverage-log.md` path on the last line.~~
