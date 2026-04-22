#!/usr/bin/env bash
# .claude/lib/audit-log-append.sh
#
# Append one audit row to docs/pr-review-log.md OR docs/auto-merge-log.md
# directly on `main`, via a short-lived ops branch + fast-forward push.
# Falls back to opening an audit PR (exit 2) if direct push to main fails
# after retries — caller (project-manager) can then fall back to classic flow.
#
# Usage:
#   audit-log-append.sh --log <auto-merge|pr-review> --row '<markdown row>' [--dedupe]
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
DEDUPE=0

usage() {
  cat >&2 <<'EOF'
Usage: audit-log-append.sh --log <auto-merge|pr-review> --row '<markdown row>' [--dedupe]
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
      DEDUPE=1
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

# Dedupe: if the row is already on origin/main, short-circuit.
if [[ $DEDUPE -eq 1 ]]; then
  if git show "origin/main:$TARGET_FILE" 2>/dev/null | grep -Fqx -- "$ROW"; then
    log "dedupe: row already present on origin/main, skipping"
    exit 0
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

# Create the ops branch from the freshest origin/main.
git checkout --quiet -B "$OPS_BRANCH" origin/main

# Ensure the target file exists (with a trailing newline so append is clean).
if [[ ! -f "$TARGET_FILE" ]]; then
  log "target file missing: $TARGET_FILE"
  exit 1
fi

# Append the row. Ensure newline termination.
# If file does not end with newline, add one first.
if [[ -s "$TARGET_FILE" ]] && [[ "$(tail -c1 "$TARGET_FILE" | wc -l | tr -d ' ')" -eq 0 ]]; then
  printf '\n' >> "$TARGET_FILE"
fi
printf '%s\n' "$ROW" >> "$TARGET_FILE"

git add "$TARGET_FILE"
SWEEP_TAG="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git commit --quiet -m "docs(audit): ${LOG_KIND} append 1 row (${SWEEP_TAG})"

# Attempt fast-forward push to main with rebase-retry on non-ff.
attempt=1
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

  log "push attempt $attempt failed:"
  cat /tmp/audit-append-push.err >&2 || true

  if [[ $attempt -ge $MAX_RETRIES ]]; then
    break
  fi
  attempt=$((attempt + 1))

  # Refetch fresh origin/main; then redo our single-row append on top.
  # We discard our commit and re-append from scratch so concurrent appends
  # to the same log file don't fight over trailing-line merge context.
  if ! git fetch --quiet origin main; then
    log "fetch failed during retry; aborting"
    break
  fi
  # Dedupe re-check: if the row was appended by someone else already, skip.
  if [[ $DEDUPE -eq 1 ]] && git show "origin/main:$TARGET_FILE" 2>/dev/null | grep -Fqx -- "$ROW"; then
    log "dedupe: row appeared on origin/main during retry, skipping"
    exit 0
  fi
  git checkout --quiet -B "$OPS_BRANCH" origin/main
  if [[ -s "$TARGET_FILE" ]] && [[ "$(tail -c1 "$TARGET_FILE" | wc -l | tr -d ' ')" -eq 0 ]]; then
    printf '\n' >> "$TARGET_FILE"
  fi
  printf '%s\n' "$ROW" >> "$TARGET_FILE"
  git add "$TARGET_FILE"
  SWEEP_TAG="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  git commit --quiet -m "docs(audit): ${LOG_KIND} append 1 row (${SWEEP_TAG}, retry ${attempt})"
done

# Fallback: direct push failed. Push ops branch + open a fallback audit PR,
# then exit 2 so caller can switch to the classic audit-PR flow.
log "direct-append failed after ${MAX_RETRIES} attempts; attempting fallback audit PR"

if ! git push --quiet -u origin "$OPS_BRANCH"; then
  log "fallback: unable to push ops branch; giving up"
  exit 1
fi

if command -v gh >/dev/null 2>&1; then
  gh pr create \
    --base main \
    --head "$OPS_BRANCH" \
    --title "docs(audit): ${LOG_KIND} append (fallback)" \
    --body "Direct-append to main failed after ${MAX_RETRIES} retries; falling back to audit PR per .claude/lib/audit-log-append.sh. Single row append, docs-only." \
    >&2 || log "gh pr create failed"
fi

exit 2
