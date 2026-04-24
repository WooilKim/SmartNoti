#!/usr/bin/env bash
# .claude/lib/audit-log-append.sh
#
# Append one audit row to docs/pr-review-log.md OR docs/auto-merge-log.md
# directly on `main`, via a short-lived ops branch + fast-forward push.
# Falls back to opening an audit PR (exit 2) if direct push to main fails
# after retries — caller (project-manager) can then fall back to classic flow.
#
# Usage:
#   audit-log-append.sh --log <auto-merge|pr-review> --row '<markdown row>' \
#                       [--source <agent-name>] [--no-dedupe]
#
# Flags:
#   --log        target audit log (pr-review | auto-merge). Required.
#   --row        the full markdown table row to append. Required.
#   --source     invoking agent (e.g. project-manager, loop-monitor). Optional;
#                when supplied, the helper stamps it into the commit subject
#                for retrospective traceability. Rows themselves are NOT
#                modified — callers control row format.
#   --no-dedupe  opt out of the default dedupe pre-check. Dedupe is ON by
#                default as of MP-1.1 so concurrent callers (PM + loop-monitor)
#                cannot append the same row twice. Supply this only when you
#                intentionally want duplicate rows (no current caller does).
#   --dedupe     explicit no-op alias (pre-MP-1.1 callers may still pass it).
#   --stamp-now  rewrite column 1 of the supplied row with `date -u` taken at
#                the moment the helper runs. Use this when the calling agent
#                cannot be trusted to know real UTC (the LLM context wall
#                clock can lag hours to days). Requires the row to start with
#                `| <placeholder> |` (any non-empty column 1 acts as a
#                placeholder). On malformed rows the helper exits 1 with an
#                explanatory message. Default OFF for backward compat.
#
# Exit codes:
#   0  — row appended to main (or dedupe skip)
#   1  — invalid args / generic failure
#   2  — direct push rejected after retries; audit PR opened as fallback
#
# Environment (test hooks):
#   AUDIT_LOG_PRE_PUSH_HOOK  path to a script executed once before each push
#                             attempt. Used by tests to simulate races.
#   AUDIT_LOG_MAX_RETRIES     default 3; override for tests.

set -eu
set -o pipefail

LOG_KIND=""
ROW=""
DEDUPE=1       # MP-1.1: dedupe is ON by default; callers can opt out.
SOURCE_AGENT="" # optional --source stamp
STAMP_NOW=0    # MP-3 (clock-drift): rewrite column 1 with real UTC.

usage() {
  cat >&2 <<'EOF'
Usage: audit-log-append.sh --log <auto-merge|pr-review> --row '<markdown row>' \
                           [--source <agent-name>] [--no-dedupe] [--stamp-now]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --log)
      LOG_KIND="${2:-}"
      shift 2
      ;;
    --row)
      ROW="${2:-}"
      shift 2
      ;;
    --dedupe)
      # No-op since MP-1.1 (dedupe default-on). Accepted for caller compat.
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
      echo "audit-log-append: unknown arg: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$LOG_KIND" || -z "$ROW" ]]; then
  echo "audit-log-append: --log and --row are required" >&2
  usage
  exit 1
fi

case "$LOG_KIND" in
  pr-review)
    TARGET_FILE="docs/pr-review-log.md"
    ;;
  auto-merge)
    TARGET_FILE="docs/auto-merge-log.md"
    ;;
  *)
    echo "audit-log-append: --log must be pr-review or auto-merge (got: $LOG_KIND)" >&2
    exit 1
    ;;
esac

MAX_RETRIES="${AUDIT_LOG_MAX_RETRIES:-3}"

log() {
  echo "audit-log-append: $*" >&2
}

# MP-3 (clock-drift): apply --stamp-now BEFORE the dedupe pre-check so the
# row that hits dedupe + git is the post-stamp row.
#
# Dedupe semantics with --stamp-now: column 1 holds the timestamp; comparing
# post-stamp full rows would never match (each call has a different second),
# so dedupe instead compares everything AFTER column 1 (the row "body") against
# any existing row on main, regardless of that row's column 1.
#
# Without --stamp-now, dedupe behavior is unchanged: full-row exact match.
ROW_BODY=""           # body = "| col2 | col3 | ... |"  (only set if STAMP_NOW=1)
if [[ $STAMP_NOW -eq 1 ]]; then
  # The row must look like:  | <col1> | <col2> | ... |
  # Validate by requiring at least two `|` separators with non-empty col1 between.
  # Use bash regex over the literal row.
  if [[ ! "$ROW" =~ ^\|[[:space:]]*[^|[:space:]][^|]*\|.*\|[[:space:]]*$ ]]; then
    log "row missing timestamp column; --stamp-now requires column 1 to be a non-empty placeholder bracketed by '|' separators (got: $ROW)"
    exit 1
  fi
  NOW_TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  # Replace the first `| ... |` segment (column 1) with `| $NOW_TS |`.
  # `${ROW#*|}` strips the leading `|`, then we strip the next `|...|` chunk
  # to isolate the body (columns 2..N including the leading `|`).
  REMAINDER="${ROW#*|}"          # strip leading `|`
  ROW_BODY="|${REMAINDER#*|}"    # strip column 1 + its trailing `|`, re-prefix `|`
  ROW="| ${NOW_TS} ${ROW_BODY}"
fi

# Must be run inside a git working tree.
if ! git rev-parse --git-dir >/dev/null 2>&1; then
  log "not inside a git work tree"
  exit 1
fi

# Fetch origin/main so we see any concurrent writers.
if ! git fetch --quiet origin main; then
  log "git fetch origin main failed"
  exit 1
fi

# Dedupe pre-check: if the row is already on origin/main, short-circuit.
# - default mode: exact full-row match
# - --stamp-now mode: any existing row whose body (columns 2+) matches ROW_BODY
if [[ $DEDUPE -eq 1 ]]; then
  if [[ $STAMP_NOW -eq 1 ]]; then
    # Body match: any line ending in ROW_BODY counts as a duplicate, since
    # column 1 will always differ by timestamp. Use grep -F with line anchor
    # via a trailing-suffix match (rows are full lines).
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

# Compute a short-lived ops branch name.
TS="$(date -u +%Y%m%d%H%M%S)"
# Short sha of origin/main — portable (no heredoc needed).
SHORT_SHA="$(git rev-parse --short origin/main 2>/dev/null || echo 'noref')"
OPS_BRANCH="ops/audit-log-${TS}-${SHORT_SHA}"

# Preserve current HEAD to restore at the end.
ORIG_HEAD="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')"
if [[ "$ORIG_HEAD" == "HEAD" ]]; then
  ORIG_HEAD="$(git rev-parse HEAD)"
fi

cleanup() {
  local ec=$?
  # Best-effort restore to original branch; don't let cleanup hide exit code.
  if [[ -n "$ORIG_HEAD" ]]; then
    git checkout --quiet "$ORIG_HEAD" 2>/dev/null || true
  fi
  # Delete local ops branch if it exists.
  git branch -D "$OPS_BRANCH" 2>/dev/null || true
  exit $ec
}
trap cleanup EXIT

# Helper: (re-)cut the ops branch from whatever origin/main currently is and
# apply the single-row append on top. Used on initial cut + every retry so the
# diff is always "one appended line against freshest origin/main" — collapsing
# the race window to the size of one `git push`.
cut_and_append() {
  local attempt_label="$1"
  git checkout --quiet -B "$OPS_BRANCH" origin/main
  if [[ ! -f "$TARGET_FILE" ]]; then
    log "target file missing: $TARGET_FILE"
    return 1
  fi
  # Ensure newline termination before append.
  if [[ -s "$TARGET_FILE" ]] && [[ "$(tail -c1 "$TARGET_FILE" | wc -l | tr -d ' ')" -eq 0 ]]; then
    printf '\n' >> "$TARGET_FILE"
  fi
  printf '%s\n' "$ROW" >> "$TARGET_FILE"
  git add "$TARGET_FILE"
  local ts subject
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  subject="docs(audit): ${LOG_KIND} append 1 row (${ts}${attempt_label})"
  if [[ -n "$SOURCE_AGENT" ]]; then
    subject+=" [source: ${SOURCE_AGENT}]"
  fi
  git commit --quiet -m "$subject"
}

# Initial cut + append.
if ! cut_and_append ""; then
  exit 1
fi

# Attempt fast-forward push to main with rebase-retry on non-ff.
#
# Semantics (MP-1.1 hardened):
#   - ANY non-zero push exit triggers the full refetch + dedupe-recheck +
#     re-cut + re-append cycle, regardless of stderr content.
#   - Before every retry we re-fetch origin/main and re-check dedupe so a
#     concurrent helper that appended our exact row causes us to exit 0
#     cleanly instead of racing into a duplicate.
#   - Freshness is asserted on every retry by re-cutting the ops branch off
#     the newly-fetched origin/main. This means a persistent racer just
#     forces more iterations, never a stale-base contamination.
#   - Fallback path (exit 2, open audit PR) unchanged on exhaustion.
attempt=1
BRANCH_BASE_SHA="$(git rev-parse origin/main)"
while :; do
  if [[ -n "${AUDIT_LOG_PRE_PUSH_HOOK:-}" ]] && [[ -x "${AUDIT_LOG_PRE_PUSH_HOOK}" ]]; then
    "${AUDIT_LOG_PRE_PUSH_HOOK}" || true
  fi

  if git push origin "HEAD:main" 2>/tmp/audit-append-push.err; then
    log "direct-append succeeded on attempt $attempt"
    # Update local main tracking too (best effort).
    git fetch --quiet origin main || true
    exit 0
  fi

  log "push attempt $attempt failed (treating as race, will refetch + re-cut):"
  cat /tmp/audit-append-push.err >&2 || true

  if [[ $attempt -ge $MAX_RETRIES ]]; then
    break
  fi
  attempt=$((attempt + 1))

  # Refetch origin/main so the retry reflects concurrent writers.
  if ! git fetch --quiet origin main; then
    log "fetch failed during retry; aborting"
    break
  fi

  # Dedupe re-check BEFORE re-cutting: if someone (possibly another helper
  # instance) appended our exact row in the meantime, short-circuit success.
  # With --stamp-now we compare on body (column 2+), see initial dedupe block.
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

  # Freshness assertion: origin/main SHOULD have advanced (we hit a non-ff);
  # if it hasn't, the push rejection came from something else (hook, ref
  # protection). Log it but still re-cut and retry — classifying broadly per
  # the MP-1.1 contract.
  NEW_BASE_SHA="$(git rev-parse origin/main)"
  if [[ "$NEW_BASE_SHA" == "$BRANCH_BASE_SHA" ]]; then
    log "freshness: origin/main unchanged ($NEW_BASE_SHA) despite push rejection"
  else
    log "freshness: origin/main advanced $BRANCH_BASE_SHA -> $NEW_BASE_SHA; re-cutting"
  fi
  BRANCH_BASE_SHA="$NEW_BASE_SHA"

  # Re-cut + re-append against the fresh base (discards previous commit).
  if ! cut_and_append " retry ${attempt}"; then
    break
  fi
done

# Fallback: direct push failed. Push ops branch + open a fallback audit PR,
# then exit 2 so caller can switch to the classic audit-PR flow.
log "direct-append failed after ${MAX_RETRIES} attempts; attempting fallback audit PR"

if ! git push --quiet -u origin "$OPS_BRANCH"; then
  log "fallback: unable to push ops branch; giving up"
  exit 1
fi

if command -v gh >/dev/null 2>&1; then
  FALLBACK_BODY="Direct-append to main failed after ${MAX_RETRIES} retries; falling back to audit PR per .claude/lib/audit-log-append.sh. Single row append, docs-only."
  if [[ -n "$SOURCE_AGENT" ]]; then
    FALLBACK_BODY+=$'\n\n'"Source agent: ${SOURCE_AGENT}"
  fi
  gh pr create \
    --base main \
    --head "$OPS_BRANCH" \
    --title "docs(audit): ${LOG_KIND} append (fallback)" \
    --body "$FALLBACK_BODY" \
    >&2 || log "gh pr create failed"
fi

exit 2
