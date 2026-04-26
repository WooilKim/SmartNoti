#!/usr/bin/env bash
# Race-stress tests for .claude/lib/monitor-log-append.sh
#
# Sibling of audit-log-append-race-test.sh; covers the same MP-1.1 hardening
# surface (broad-race classification + retry + dedupe-during-retry) applied to
# docs/loop-monitor-log.md instead of the audit logs.
#
# The "racer" simulates a concurrent writer (in practice: project-manager's
# audit-log-append.sh, or a parallel monitor in some hypothetical future) that
# advances origin/main between our cut-and-append and our push.
#
# Usage: bash .claude/lib/tests/monitor-log-append-race-test.sh
# Exits 0 if all tests pass, 1 otherwise.

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER="${SCRIPT_DIR}/../monitor-log-append.sh"

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
    cat >docs/loop-monitor-log.md <<'EOF'
# Loop Monitor Log

| Date (UTC) | Health | Anomalies | Auto-fix | Notes |
|---|---|---|---|---|
| 2026-04-25T00:00:00Z | PASS | 0 | none | seed row |
EOF
    git add docs/loop-monitor-log.md
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

# ---------- Test 1: single-race retry recovers; both rows survive in order ----------
test_retry_on_race() {
  echo "TEST: retry recovers when origin/main advances mid-run; helper row appended AFTER racer row"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

  # Hook fires once: racer commits a row to origin before our push attempt.
  mkdir -p "$root/hooks"
  touch "$root/.race-armed"
  cat >"$root/hooks/race-trigger.sh" <<EOF
#!/usr/bin/env bash
MARKER="$root/.race-armed"
if [[ -f "\$MARKER" ]]; then
  rm -f "\$MARKER"
  cd "$root/other"
  git fetch --quiet origin main
  git reset --hard origin/main >/dev/null 2>&1
  echo "| 2026-04-26T06:00:00Z | PASS | 0 | none | racer row |" >> docs/loop-monitor-log.md
  git add docs/loop-monitor-log.md
  git commit --quiet -m "docs(monitor): racer row"
  git push --quiet origin main
fi
EOF
  chmod +x "$root/hooks/race-trigger.sh"

  local row='| 2026-04-26T06:00:30Z | PASS | 0 | none | winner row |'
  (
    cd "$work"
    MONITOR_LOG_PRE_PUSH_HOOK="$root/hooks/race-trigger.sh" \
      PATH="$root/bin:$PATH" \
      bash "$HELPER" --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc on race; expected 0 (retry should recover)"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  local content
  content="$( cd "$work" && git show origin/main:docs/loop-monitor-log.md )"
  if grep -Fq "$row" <<<"$content"; then
    pass "winner row on main after retry"
  else
    fail "winner row missing from main after race retry"
  fi
  if grep -Fq "racer row" <<<"$content"; then
    pass "racer row preserved on main (no clobber)"
  else
    fail "racer row lost — retry clobbered remote state"
  fi
  # Order check: rebase semantics mean helper's row appears AFTER racer's row.
  local racer_line winner_line
  racer_line="$(grep -n 'racer row' <<<"$content" | head -1 | cut -d: -f1)"
  winner_line="$(grep -n 'winner row' <<<"$content" | head -1 | cut -d: -f1)"
  if [[ -n "$racer_line" && -n "$winner_line" && "$winner_line" -gt "$racer_line" ]]; then
    pass "helper row appended after racer row (rebase semantics, not stale-base overwrite)"
  else
    fail "ordering wrong: racer_line=$racer_line winner_line=$winner_line"
  fi
  rm -rf "$root"
}

# ---------- Test 2: persistent racer for N-1 attempts; helper still succeeds ----------
test_persistent_racer() {
  echo "TEST: persistent racer for 2 attempts; helper succeeds within MAX_RETRIES"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

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
  echo "| 2026-04-26T06:0\${NEXT}:00Z | PASS | 0 | none | persistent race row \${NEXT} |" >> docs/loop-monitor-log.md
  git add docs/loop-monitor-log.md
  git commit --quiet -m "docs(monitor): persistent race row \${NEXT}"
  git push --quiet origin main
fi
EOF
  chmod +x "$root/hooks/persistent-racer.sh"

  local row='| 2026-04-26T06:05:00Z | PASS | 0 | none | persistent winner |'
  (
    cd "$work"
    MONITOR_LOG_MAX_RETRIES=5 \
      MONITOR_LOG_PRE_PUSH_HOOK="$root/hooks/persistent-racer.sh" \
      PATH="$root/bin:$PATH" \
      bash "$HELPER" --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc under persistent race; expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  local content
  content="$( cd "$work" && git show origin/main:docs/loop-monitor-log.md )"
  if grep -Fq "$row" <<<"$content"; then
    pass "winner row on main after persistent race"
  else
    fail "winner row missing from main after persistent race"
  fi
  if grep -Fq "persistent race row 1" <<<"$content" && grep -Fq "persistent race row 2" <<<"$content"; then
    pass "both racer rows preserved (no clobber across retries)"
  else
    fail "racer rows lost — retry clobbered remote state"
  fi
  rm -rf "$root"
}

# ---------- Test 3: dedupe-during-retry — racer inserts our exact row, helper short-circuits ----------
test_dedupe_during_retry() {
  echo "TEST: dedupe exits 0 when racer appends the SAME row before our retry lands"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

  local row='| 2026-04-26T06:10:00Z | PASS | 0 | none | dupe race monitor row |'

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
  echo '$row' >> docs/loop-monitor-log.md
  git add docs/loop-monitor-log.md
  git commit --quiet -m "docs(monitor): dupe race"
  git push --quiet origin main
fi
EOF
  chmod +x "$root/hooks/dupe-racer.sh"

  local before_commits
  before_commits="$( cd "$work" && git fetch --quiet origin main && git rev-list --count origin/main )"

  (
    cd "$work"
    MONITOR_LOG_PRE_PUSH_HOOK="$root/hooks/dupe-racer.sh" \
      PATH="$root/bin:$PATH" \
      bash "$HELPER" --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc on dupe-race; expected 0 (dedupe should short-circuit)"
    rm -rf "$root"
    return
  fi

  ( cd "$work" && git fetch --quiet origin main )
  local content after_commits dupe_count
  content="$( cd "$work" && git show origin/main:docs/loop-monitor-log.md )"
  after_commits="$( cd "$work" && git rev-list --count origin/main )"
  dupe_count="$(grep -Fc "$row" <<<"$content" || true)"

  if [[ "$dupe_count" -eq 1 ]]; then
    pass "row appears exactly once on main (dedupe prevented duplicate)"
  else
    fail "row appears $dupe_count times on main (expected 1)"
  fi

  local delta=$((after_commits - before_commits))
  if [[ "$delta" -eq 1 ]]; then
    pass "only the racer commit landed on main (helper short-circuited on retry)"
  else
    fail "expected 1 new commit on main (racer only); saw $delta"
  fi

  rm -rf "$root"
}

# ---------- Runner ----------
test_retry_on_race
test_persistent_racer
test_dedupe_during_retry

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
