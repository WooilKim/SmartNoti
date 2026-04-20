# PR Review Log

Append-only record of every verdict cast by the `project-manager` agent. Exists so a regression can be traced from the merged commit back to the review that let it through.

**Every project-manager review MUST add a row here** — approve, request-changes, and defer all get logged. No decision is made silently.

## Format

```
| Date (UTC) | PR | Agent | Verdict | Notes |
|---|---|---|---|---|
| 2026-04-20T12:34:56Z | #123 | journey-tester | approve | docs-only, silent-auto-hide last-verified bumped |
| 2026-04-20T12:40:12Z | #124 | plan-implementer | request-changes | tests missing for new classifier branch |
| 2026-04-20T12:45:00Z | #125 | gap-planner | defer | touches .claude/rules/ — human review required |
```

Newest at the bottom. Never edit past rows. If a verdict turns out to be wrong (approved PR regressed something), add a new row below it with `notes: reverted — <pr#>` linking the revert, rather than rewriting history.

## Verdicts

- **approve** — gates passed, PR may be merged.
- **request-changes** — one or more gates failed; the reviewer added a review-changes comment describing exactly what to fix.
- **defer** — CI still pending, or a carve-out rule (meta-changes, schema changes, too-large diff) triggers required human review. No merge vote cast.

## Scope

The project-manager only reviews agent-origin PRs (branch patterns documented in its agent definition). Human-opened PRs are reviewed through a separate channel and do not appear here.

## Rows

<!-- append rows below this line, newest at the bottom -->

| Date (UTC) | PR | Agent | Verdict | Notes |
|---|---|---|---|---|
