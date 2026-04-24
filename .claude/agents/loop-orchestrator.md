---
name: loop-orchestrator
description: "[DEPRECATED 2026-04-22] One-tick controller for the SmartNoti improvement loop. NO LONGER INVOKED — the per-tick decision tree is inlined in .claude/commands/journey-loop.md because the Claude Code harness blocks nested Agent tool calls in sub-agents. See docs/plans/2026-04-22-meta-inline-orchestration.md for the diagnosis."
tools: Read
---

# DEPRECATION NOTICE — do not invoke this agent

This agent was the per-tick orchestrator for the SmartNoti improvement loop until **2026-04-22**. It is no longer used.

## Why deprecated

The Claude Code harness blocks the `Agent` (Task) tool from being loaded into sub-agent tool palettes, even when the agent's `tools:` frontmatter lists it. When `/journey-loop` spawned this orchestrator, it could not in turn spawn `journey-tester` / `gap-planner` / `plan-implementer` / `project-manager` / `loop-monitor`. Its fallback path (`claude --dangerously-skip-permissions` via Bash) was correctly blocked by the security gate. Net result: every tick faulted at Phase A with the verbatim error `Without a Task tool exposed`.

Surfaced 2026-04-22T08:50Z. Three subsequent inlined ticks (#255 categories-management, #256 inbox-unified, #257 priority-inbox) confirmed the inlined approach works.

## Where the logic lives now

The full Phase A decision tree, Phase B PM drainage, and loop-monitor handoff are inlined in:

- **`.claude/commands/journey-loop.md`** — the wrapper command, executed by the main session which DOES have the Agent tool.

## Full plan / diagnosis

- **`docs/plans/2026-04-22-meta-inline-orchestration.md`** — diagnosis, Tasks 1–4, trade-off rationale (Option A inline vs. Option B decider-only).

## If you want to re-enable this agent

Do not. Two preconditions would have to change first:

1. The Claude Code harness would need to support nested Agent tool calls in sub-agents (not currently the case as of 2026-04-22).
2. A new meta-plan would need to weigh whether the ~1500-token-per-tick context savings justify re-introducing the indirection (the inlined approach has been working without issue).

If both are true, restore this agent's body from git history (look for the pre-2026-04-22 version of this file) and shrink `journey-loop.md` back to its `[spawn loop-orchestrator → relay]` form. **When you do, also add the "Read the wall clock first" bullet from `.claude/rules/clock-discipline.md` to its "Before you start" section** — every agent that writes a date or timestamp must follow that rule (MP-3 clock-drift fix, 2026-04-24).

## Sub-agent palette of this file

`tools: Read` — kept as the minimal safe surface so accidental invocation cannot do anything destructive. Even if invoked, this agent would only be able to read files; it would not be able to spawn other agents, edit anything, or run Bash.
