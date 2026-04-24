# Clock Discipline Rule

LLM context wall clock is unreliable. It can lag real UTC by hours to days. Every SmartNoti agent that writes a date or timestamp into a file, PR, audit row, log entry, or commit subject MUST take that value from the system clock — never from "what date the model thinks it is right now".

## The rule

**At task start, run the system clock once and reuse that value for the entire task.**

```bash
# For dates (journey `last-verified`, plan filenames, frontmatter `shipped:`):
TODAY="$(date -u +%Y-%m-%d)"

# For timestamps (audit rows, log entries, commit subjects):
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
```

Use `$TODAY` and `$NOW` everywhere you would otherwise type a literal date. Do not infer the date from training context, journal entries, or recent commit messages — those reflect when the model last saw real time, not when YOU are running.

## What this protects

- **Journey `last-verified`** bumps. A drifted bump silently lies to every future session about when verification was last run.
- **Plan filenames** (`docs/plans/YYYY-MM-DD-<slug>.md`). A drifted filename misorders the plan history and confuses `loop-retrospective`'s 30-day windows.
- **Plan frontmatter** `shipped:` dates and Change log entries.
- **Audit rows** in `docs/pr-review-log.md` and `docs/auto-merge-log.md`. Drifted timestamps misorder verdicts and corrupt retrospective metrics.
- **Loop-monitor rows** in `docs/loop-monitor-log.md`.
- **Retrospective entries** in `docs/loop-retrospective-log.md`.
- **Commit subjects** that embed dates or timestamps.

## Mechanical enforcement (where possible)

`.claude/lib/audit-log-append.sh` accepts `--stamp-now` to rewrite column 1 of the row with `date -u` at append time. Project-manager and loop-monitor invocations always use `--stamp-now`. This is the only spot in SmartNoti where clock discipline is enforced by code — every other date-write site is unguarded and depends on the agent following this rule.

If you build a new helper that writes dates on behalf of an agent, prefer the same `--stamp-now`-style pattern (helper reads `date -u` itself) over trusting the caller.

## Background

This rule exists because of two anomalies that loop-monitor flagged 30+ times in April 2026:

- **AGENT_CLOCK_DRIFT** — sub-agents (gap-planner, plan-implementer, journey-tester) wrote `last-verified` and plan filenames using the model's context wall clock, often 1–2 days behind real UTC.
- **AUDIT_HELPER_TIMESTAMP_DRIFT** — PM and loop-monitor passed pre-formatted timestamps to `audit-log-append.sh`, which honored them faithfully even when they drifted hours ahead of UTC.

Both anomalies share the same root cause: trusting the model's notion of "now" instead of the system clock. The fix is twofold: F1 (the `--stamp-now` helper flag, mechanical) + F2 (this rule, applied to every agent that writes a date the helper cannot reach). See `docs/plans/2026-04-24-meta-agent-clock-drift-fix.md` for the full plan.

## When you violate this rule

If you notice you wrote a stale date into a doc that has already shipped:

1. Do NOT silently rewrite history — that loses the audit trail of "the agent thought it was X on day Y".
2. Open a small follow-up PR that corrects the affected file with a Change log entry like "rules-feedback-loop YYYY-MM-DD date-correction".
3. Add a row to `docs/loop-monitor-log.md` flagging the drift so retrospective can spot patterns.

If you notice the rule itself is being ignored repeatedly: that is a meta-plan, not a one-off fix.
