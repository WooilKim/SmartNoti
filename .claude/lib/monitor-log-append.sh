#!/usr/bin/env bash
# .claude/lib/monitor-log-append.sh
#
# Append one row to docs/loop-monitor-log.md directly on `main`, via a
# short-lived ops branch + fast-forward push. Falls back to opening an ops
# PR (exit 2) if direct push to main fails after retries — caller (loop-monitor)
# can then surface MONITOR_LOG_APPEND_DEFERRED and let the human merge.
#
# Sibling of .claude/lib/audit-log-append.sh. Mirrors MP-1 / MP-1.1 architecture
# (initial cut → append → ff-push with broad race classification → fallback PR
# on exhaustion). Target file is fixed (no --log flag).
#
# Why this exists: see docs/plans/2026-04-26-meta-monitor-log-commit-push-helper.md
# (MP-1.2). Closes MONITOR_LOG_COMMIT_RECURRENCE — the failure mode where
# loop-monitor bare-edited the log file but never committed, polluting the
# next session's working tree and forcing ad-hoc ops PRs.
#
# Usage:
#   monitor-log-append.sh --row '<markdown row>' \
#                         [--source <agent-name>] [--no-dedupe] [--stamp-now]
#
# Flags:
#   --row        the full markdown table row to append. Required.
#   --source     invoking agent (defaults to loop-monitor). Stamped into the
#                commit subject so retrospective can distinguish writers if a
#                second writer ever joins.
#   --no-dedupe  opt out of the default dedupe pre-check. Dedupe is ON by
#                default (matches audit helper MP-1.1) so a re-invoked monitor
#                on the same tick with the same row body short-circuits.
#   --dedupe     explicit no-op alias (callers may still pass it).
#   --stamp-now  rewrite column 1 of the supplied row with `date -u` taken at
#                the moment the helper runs (clock-discipline rule). Requires
#                column 1 to be a non-empty placeholder bracketed by `|`. On
#                malformed rows the helper exits 1 with an explanatory message.
#                Default OFF for backward compat / call-site clarity.
#
# Exit codes:
#   0  — row appended to main (or dedupe skip)
#   1  — invalid args / generic failure
#   2  — direct push rejected after retries; fallback ops PR opened
#
# Environment (test hooks):
#   MONITOR_LOG_PRE_PUSH_HOOK  path to a script executed once before each push
#                              attempt. Used by tests to simulate races.
#   MONITOR_LOG_MAX_RETRIES    default 3; override for tests.

set -eu
set -o pipefail

ROW=""
DEDUPE=1       # default-on, mirrors audit helper MP-1.1.
SOURCE_AGENT="loop-monitor"  # default source; overridable.
STAMP_NOW=0

TARGET_FILE="docs/loop-monitor-log.md"

usage() {
  cat >&2 <<'EOF'
Usage: monitor-log-append.sh --row '<markdown row>' \
                             [--source <agent-name>] [--no-dedupe] [--stamp-now]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --row)
      ROW="${2:-}"
      shift 2
      ;;
    --dedupe)
      DEDUPE=1
      shift
      ;;
    --no-dedupe)
      DEDUPE=0
      shift
      ;;
    --source)
      SOURCE_AGENT="${2:-}"
      shift 2
      ;;
    --stamp-now)
      STAMP_NOW=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "monitor-log-append: unknown arg: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$ROW" ]]; then
  echo "monitor-log-append: --row is required" >&2
  usage
  exit 1
fi

MAX_RETRIES="${MONITOR_LOG_MAX_RETRIES:-3}"

log() {
  echo "monitor-log-append: $*" >&2
}

# Apply --stamp-now BEFORE dedupe so the row that hits dedupe + git is the
# post-stamp row. Same dedupe semantics as audit helper:
#   - default mode: full-row exact match
#   - --stamp-now mode: compare ROW BODY (columns 2..N) only, since column 1
#     always differs by second between calls.
ROW_BODY=""
if [[ $STAMP_NOW -eq 1 ]]; then
  if [[ ! "$ROW" =~ ^\|[[:space:]]*[^|[:space:]][^|]*\|.*\|[[:space:]]*$ ]]; then
    log "row missing timestamp column; --stamp-now requires column 1 to be a non-empty placeholder bracketed by '|' separators (got: $ROW)"
    exit 1
  fi
  NOW_TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  REMAINDER="${ROW#*|}"          # strip leading `|`
  ROW_BODY="|${REMAINDER#*|}"    # strip column 1 + its trailing `|`, re-prefix `|`
  ROW="| ${NOW_TS} ${ROW_BODY}"
fi

# Must be run inside a git working tree.
if ! git rev-parse --git-dir >/dev/null 2>&1; then
  log "not inside a git work tree"
  exit 1
fi

if ! git fetch --quiet origin main; then
  log "git fetch origin main failed"
  exit 1
fi

# Dedupe pre-check.
if [[ $DEDUPE -eq 1 ]]; then
  if [[ $STAMP_NOW -eq 1 ]]; then
    if git show "origin/main:$TARGET_FILE" 2>/dev/null | grep -Fq -- "$ROW_BODY"; then
      log "dedupe: row body already present on origin/main, skipping"
      exit 0
    fi
  else
    if git show "origin/main:$TARGET_FILE" 2>/dev/null | grep -Fqx -- "$ROW"; then
      log "dedupe: row already present on origin/main, skipping"
      exit 0
    fi
  fi
fi

TS="$(date -u +%Y%m%d%H%M%S)"
SHORT_SHA="$(git rev-parse --short origin/main 2>/dev/null || echo 'noref')"
OPS_BRANCH="ops/monitor-log-${TS}-${SHORT_SHA}"

ORIG_HEAD="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')"
if [[ "$ORIG_HEAD" == "HEAD" ]]; then
  ORIG_HEAD="$(git rev-parse HEAD)"
fi

cleanup() {
  local ec=$?
  if [[ -n "$ORIG_HEAD" ]]; then
    git checkout --quiet "$ORIG_HEAD" 2>/dev/null || true
  fi
  git branch -D "$OPS_BRANCH" 2>/dev/null || true
  exit $ec
}
trap cleanup EXIT

# (Re-)cut the ops branch from current origin/main and apply the single-row
# append. Used on initial cut + every retry so the diff is always "one
# appended line against freshest origin/main".
cut_and_append() {
  local attempt_label="$1"
  git checkout --quiet -B "$OPS_BRANCH" origin/main
  if [[ ! -f "$TARGET_FILE" ]]; then
    log "target file missing: $TARGET_FILE"
    return 1
  fi
  if [[ -s "$TARGET_FILE" ]] && [[ "$(tail -c1 "$TARGET_FILE" | wc -l | tr -d ' ')" -eq 0 ]]; then
    printf '\n' >> "$TARGET_FILE"
  fi
  printf '%s\n' "$ROW" >> "$TARGET_FILE"
  git add "$TARGET_FILE"
  local ts subject
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  subject="docs(monitor): loop-monitor-log append 1 row (${ts}${attempt_label})"
  if [[ -n "$SOURCE_AGENT" ]]; then
    subject+=" [source: ${SOURCE_AGENT}]"
  fi
  git commit --quiet -m "$subject"
}

if ! cut_and_append ""; then
  exit 1
fi

# Push loop with broad race classification (any non-zero exit triggers refetch
# + dedupe re-check + freshness re-assert + re-cut + retry). Same MP-1.1
# hardening as audit helper.
attempt=1
BRANCH_BASE_SHA="$(git rev-parse origin/main)"
while :; do
  if [[ -n "${MONITOR_LOG_PRE_PUSH_HOOK:-}" ]] && [[ -x "${MONITOR_LOG_PRE_PUSH_HOOK}" ]]; then
    "${MONITOR_LOG_PRE_PUSH_HOOK}" || true
  fi

  if git push origin "HEAD:main" 2>/tmp/monitor-append-push.err; then
    log "direct-append succeeded on attempt $attempt"
    git fetch --quiet origin main || true
    exit 0
  fi

  log "push attempt $attempt failed (treating as race, will refetch + re-cut):"
  cat /tmp/monitor-append-push.err >&2 || true

  if [[ $attempt -ge $MAX_RETRIES ]]; then
    break
  fi
  attempt=$((attempt + 1))

  if ! git fetch --quiet origin main; then
    log "fetch failed during retry; aborting"
    break
  fi

  if [[ $DEDUPE -eq 1 ]]; then
    if [[ $STAMP_NOW -eq 1 ]]; then
      if git show "origin/main:$TARGET_FILE" 2>/dev/null | grep -Fq -- "$ROW_BODY"; then
        log "dedupe: row body appeared on origin/main during retry, skipping"
        exit 0
      fi
    else
      if git show "origin/main:$TARGET_FILE" 2>/dev/null | grep -Fqx -- "$ROW"; then
        log "dedupe: row appeared on origin/main during retry, skipping"
        exit 0
      fi
    fi
  fi

  NEW_BASE_SHA="$(git rev-parse origin/main)"
  if [[ "$NEW_BASE_SHA" == "$BRANCH_BASE_SHA" ]]; then
    log "freshness: origin/main unchanged ($NEW_BASE_SHA) despite push rejection"
  else
    log "freshness: origin/main advanced $BRANCH_BASE_SHA -> $NEW_BASE_SHA; re-cutting"
  fi
  BRANCH_BASE_SHA="$NEW_BASE_SHA"

  if ! cut_and_append " retry ${attempt}"; then
    break
  fi
done

# Fallback: direct push failed. Push ops branch + open a fallback PR.
log "direct-append failed after ${MAX_RETRIES} attempts; attempting fallback ops PR"

if ! git push --quiet -u origin "$OPS_BRANCH"; then
  log "fallback: unable to push ops branch; giving up"
  exit 1
fi

if command -v gh >/dev/null 2>&1; then
  FALLBACK_BODY="Direct-append to main failed after ${MAX_RETRIES} retries; falling back to ops PR per .claude/lib/monitor-log-append.sh. Single row append, docs-only."
  if [[ -n "$SOURCE_AGENT" ]]; then
    FALLBACK_BODY+=$'\n\n'"Source agent: ${SOURCE_AGENT}"
  fi
  FALLBACK_BODY+=$'\n\n'"Plan: docs/plans/2026-04-26-meta-monitor-log-commit-push-helper.md (MP-1.2)"
  gh pr create \
    --base main \
    --head "$OPS_BRANCH" \
    --title "docs(monitor): loop-monitor-log append (fallback)" \
    --body "$FALLBACK_BODY" \
    >&2 || log "gh pr create failed"
fi

exit 2
