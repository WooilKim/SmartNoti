# CLAUDE.md — SmartNoti project context (lightweight)

This file is the minimal entrypoint for Claude Code in the SmartNoti repo. Heavy rules live in `.claude/rules/` and only load when relevant.

## Setup (one-time)

```bash
# Disable Anthropic telemetry for this project
echo 'export DISABLE_TELEMETRY=1' >> ~/.zshrc   # or ~/.bashrc
source ~/.zshrc

# Per-project (optional, overrides shell):
echo 'export DISABLE_TELEMETRY=1' > .envrc   # if direnv is installed
```

Verify: `echo $DISABLE_TELEMETRY` should print `1`.

## What lives where

- `docs/journeys/` — current user-observable contract (shipped features)
- `docs/plans/` — work in flight + history (shipped plans never deleted)
- `.claude/agents/` — 11-agent improvement loop (see `.claude/rules/agent-loop.md`)
- `.claude/rules/` — auto-loaded rules (clock, ui, docs-sync, agent-loop)
- `app/src/main/` — Android Compose app (Kotlin)

## Loop entry

`/journey-loop` runs one tick. See `.claude/commands/journey-loop.md` for the per-tick decision tree.

## Context hygiene

- `context-manager` agent (in `.claude/agents/`) decides when to call `/compact` or `/clear`.
- `.claudeignore` filters build outputs + worktrees from context. Don't add app source there.
- For heavy refactors, use `EnterWorktree` so unrelated changes don't pollute context.

## Hot-loaded rules (deferred — only when relevant)

These large rule files load on demand:
- `.claude/rules/clock-discipline.md` — agents must use `date -u`, never model wall-clock.
- `.claude/rules/docs-sync.md` — journey ↔ code ↔ plan must stay in sync.
- `.claude/rules/agent-loop.md` — 11-agent topology + safety.
- `.claude/rules/ui-improvement.md` — Linear-style polish guidelines.

The agents themselves include the relevant rule pointers in their system prompts; you don't need to load all of these per session.
