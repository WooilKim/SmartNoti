#!/usr/bin/env bash
# Race-stress tests for .claude/lib/audit-log-append.sh
#
# Covers regressions for AUDIT_APPEND_RACE (incidents: PR #278
# BACKFILL_CONTAMINATION, PR #280 race-close) beyond the single-race case in
# audit-log-append-test.sh:
#
#   1. Persistent racer — a competing writer appends a DIFFERENT row on every
#      push attempt for N-1 attempts, then stops. Helper must retry, re-cut
#      each time, and land the winner row within MAX_RETRIES. All racer rows
#      must survive (append-only, no clobber).
#
#   2. Dedupe-during-retry — same row appears on origin/main via a racer after
#      our first attempt. With --dedupe, helper must detect the appearance on
#      the retry path and exit 0 without a second commit.
#
#   3. --source flag stamping (T+4 decision: YES) — when invoked with
#      --source <agent>, the commit message records the source so retrospective
#      can distinguish PM appends from monitor backfills.
#
# Usage: bash .claude/lib/tests/audit-log-append-race-test.sh
# Exits 0 if all tests pass, 1 otherwise.

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER="${SCRIPT_DIR}/../audit-log-append.sh"

PASS=0
FAIL=0
FAILURES=()

pass() {
  PASS=$((PASS + 1))
  echo "  PASS: $1"
}

fail() {
  FAIL=$((FAIL + 1))
  FAILURES+=("$1")
  echo "  FAIL: $1"
}

# Build a tmp sandbox that looks like a clone of SmartNoti: a "remote" bare
# repo, a working clone, and a second clone for the racer.
make_sandbox() {
  local root
  root="$(mktemp -d)"
  (
    set -e
    cd "$root"
    git init --bare --initial-branch=main remote.git >/dev/null
    git clone --quiet remote.git work
    cd work
    git config user.email test@example.com
    git config user.name test
    mkdir -p docs
    cat >docs/pr-review-log.md <<'EOF'
# PR Review Log

## Rows

EOF
    cat >docs/auto-merge-log.md <<'EOF'
# Auto-merge Log

## Rows

EOF
    git add docs/pr-review-log.md docs/auto-merge-log.md
    git commit --quiet -m "init"
    git push --quiet origin main
    cd ..
    git clone --quiet remote.git other
    cd other
    git config user.email other@example.com
    git config user.name other
  )
  echo "$root"
}

install_gh_shim() {
  local dir="$1"
  mkdir -p "$dir/bin"
  cat >"$dir/bin/gh" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "pr" && "${2:-}" == "create" ]]; then
  echo "https://github.com/fake/repo/pull/999"
  exit 0
fi
exit 0
EOF
  chmod +x "$dir/bin/gh"
}

# ---------- Test 1: persistent racer forces 2 retries, helper still succeeds ----------
test_persistent_racer() {
  echo "TEST: persistent racer for N-1 attempts; helper succeeds within MAX_RETRIES"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

  # Hook state: tick counter capped at 2 arming events.
  mkdir -p "$root/hooks"
  echo "0" > "$root/.race-count"
  cat >"$root/hooks/persistent-racer.sh" <<EOF
#!/usr/bin/env bash
COUNT_FILE="$root/.race-count"
COUNT="\$(cat "\$COUNT_FILE" 2>/dev/null || echo 0)"
if [[ "\$COUNT" -lt 2 ]]; then
  NEXT=\$((COUNT + 1))
  echo "\$NEXT" > "\$COUNT_FILE"
  cd "$root/other"
  git fetch --quiet origin main
  git reset --hard origin/main >/dev/null 2>&1
  echo "| 2026-04-22T10:0\${NEXT}:00Z | #90\${NEXT} | racer | approve | race row \${NEXT} |" >> docs/pr-review-log.md
  git add docs/pr-review-log.md
  git commit --quiet -m "docs(audit): race row \${NEXT}"
  git push --quiet origin main
fi
EOF
  chmod +x "$root/hooks/persistent-racer.sh"

  local row='| 2026-04-22T10:05:00Z | #9999 | project-manager | approve | winner row |'
  (
    cd "$work"
    AUDIT_LOG_MAX_RETRIES=5 \
      AUDIT_LOG_PRE_PUSH_HOOK="$root/hooks/persistent-racer.sh" \
      PATH="$root/bin:$PATH" \
      bash "$HELPER" --log pr-review --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc under persistent race; expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  local content
  content="$( cd "$work" && git show origin/main:docs/pr-review-log.md )"
  if grep -Fq "$row" <<<"$content"; then
    pass "winner row on main after persistent race"
  else
    fail "winner row missing from main after persistent race"
  fi
  if grep -Fq "race row 1" <<<"$content" && grep -Fq "race row 2" <<<"$content"; then
    pass "both racer rows preserved (no clobber across retries)"
  else
    fail "racer rows lost — retry clobbered remote state (content=$content)"
  fi
  rm -rf "$root"
}

# ---------- Test 2: dedupe detects race-inserted duplicate during retry ----------
test_dedupe_during_retry() {
  echo "TEST: --dedupe exits 0 when racer appends the SAME row before our retry lands"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

  local row='| 2026-04-22T10:10:00Z | #8888 | project-manager | approve | dupe race row |'

  # Racer writes OUR EXACT row once on first push-hook fire.
  mkdir -p "$root/hooks"
  touch "$root/.dupe-armed"
  cat >"$root/hooks/dupe-racer.sh" <<EOF
#!/usr/bin/env bash
MARKER="$root/.dupe-armed"
if [[ -f "\$MARKER" ]]; then
  rm -f "\$MARKER"
  cd "$root/other"
  git fetch --quiet origin main
  git reset --hard origin/main >/dev/null 2>&1
  echo '$row' >> docs/pr-review-log.md
  git add docs/pr-review-log.md
  git commit --quiet -m "docs(audit): dupe race"
  git push --quiet origin main
fi
EOF
  chmod +x "$root/hooks/dupe-racer.sh"

  # Capture commit count on main BEFORE our run.
  local before_commits
  before_commits="$( cd "$work" && git fetch --quiet origin main && git rev-list --count origin/main )"

  (
    cd "$work"
    AUDIT_LOG_PRE_PUSH_HOOK="$root/hooks/dupe-racer.sh" \
      PATH="$root/bin:$PATH" \
      bash "$HELPER" --log pr-review --row "$row" --dedupe
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc on dupe-race; expected 0 (dedupe should short-circuit)"
    rm -rf "$root"
    return
  fi

  ( cd "$work" && git fetch --quiet origin main )
  local content after_commits dupe_count
  content="$( cd "$work" && git show origin/main:docs/pr-review-log.md )"
  after_commits="$( cd "$work" && git rev-list --count origin/main )"
  dupe_count="$(grep -Fc "$row" <<<"$content" || true)"

  if [[ "$dupe_count" -eq 1 ]]; then
    pass "row appears exactly once on main (dedupe prevented duplicate)"
  else
    fail "row appears $dupe_count times on main (expected 1)"
  fi

  # Racer's commit must exist; helper must NOT have added a second identical row.
  # i.e. the commit delta should be the racer only (1), not racer + helper (2).
  local delta=$((after_commits - before_commits))
  if [[ "$delta" -eq 1 ]]; then
    pass "only the racer commit landed on main (helper short-circuited on retry)"
  else
    fail "expected 1 new commit on main (racer only); saw $delta"
  fi

  rm -rf "$root"
}

# ---------- Test 3: --source flag stamps commit message ----------
test_source_flag_stamps_commit() {
  echo "TEST: --source <agent> records the invoking agent in the commit message"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

  local row='| 2026-04-22T10:20:00Z | #7777 | loop-monitor | backfill | source-tagged row |'
  (
    cd "$work"
    PATH="$root/bin:$PATH" bash "$HELPER" \
      --log pr-review --row "$row" --source loop-monitor
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc with --source; expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  local subject
  subject="$( cd "$work" && git log origin/main -1 --pretty=%s )"
  if grep -Fq "loop-monitor" <<<"$subject"; then
    pass "commit subject records source ($subject)"
  else
    fail "commit subject missing source tag ($subject)"
  fi
  rm -rf "$root"
}

# ---------- Runner ----------
test_persistent_racer
test_dedupe_during_retry
test_source_flag_stamps_commit

echo
echo "Results: $PASS passed, $FAIL failed"
if [[ $FAIL -gt 0 ]]; then
  echo "Failed cases:"
  for f in "${FAILURES[@]}"; do
    echo "  - $f"
  done
  exit 1
fi
exit 0
