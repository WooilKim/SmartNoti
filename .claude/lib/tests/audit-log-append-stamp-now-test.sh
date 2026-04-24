#!/usr/bin/env bash
# Tests for .claude/lib/audit-log-append.sh --stamp-now behavior.
#
# Companion to audit-log-append-test.sh. Covers the F1 fix from
# docs/plans/2026-04-24-meta-agent-clock-drift-fix.md: helper stamps
# column 1 of the row with real UTC from `date -u` so callers cannot
# poison the log with stale-context timestamps.
#
# Usage: bash .claude/lib/tests/audit-log-append-stamp-now-test.sh
#
# Exits 0 if all tests pass, 1 if any test fails.

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

# Sandbox + gh shim helpers mirror audit-log-append-test.sh.
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

# ---------- Test 1: default (no --stamp-now) preserves caller timestamp ----------
test_default_preserves_caller_stamp() {
  echo "TEST: without --stamp-now, caller-supplied timestamp is preserved"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-01-01T00:00:00Z | foo | bar |'
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc; expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  if ( cd "$work" && git show origin/main:docs/pr-review-log.md ) | grep -Fq '| 2026-01-01T00:00:00Z |'; then
    pass "caller timestamp 2026-01-01T00:00:00Z preserved"
  else
    fail "caller timestamp missing — helper modified row without --stamp-now"
  fi
  rm -rf "$root"
}

# ---------- Test 2: --stamp-now overrides column 1 with real UTC ----------
test_stamp_now_overrides_column_1() {
  echo "TEST: --stamp-now replaces column 1 with current UTC; old stamp disappears"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| OLD-TS | foo | bar-stamp-now-test |'
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" --stamp-now )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc; expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  local appended
  appended="$( cd "$work" && git show origin/main:docs/pr-review-log.md | grep 'bar-stamp-now-test' || true )"
  if [[ -z "$appended" ]]; then
    fail "row not found on main"
    rm -rf "$root"
    return
  fi
  # column 1 must match ISO8601 UTC pattern
  if [[ "$appended" =~ ^\|\ 2[0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z\ \| ]]; then
    pass "column 1 rewritten to ISO8601 UTC"
  else
    fail "column 1 not rewritten: $appended"
  fi
  if echo "$appended" | grep -Fq 'OLD-TS'; then
    fail "OLD-TS still present in appended row: $appended"
  else
    pass "OLD-TS no longer appears in appended row"
  fi
  rm -rf "$root"
}

# ---------- Test 3: --stamp-now on malformed row (no leading | ts |) exits 1 ----------
test_stamp_now_rejects_malformed_row() {
  echo "TEST: --stamp-now on a row without a leading timestamp column exits 1"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  # Only one column separator worth of content — no room for column 1 to be rewritten.
  local row='no-pipes-at-all'
  local rc
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" --stamp-now ) >/dev/null 2>&1
  rc=$?
  if [[ $rc -eq 1 ]]; then
    pass "malformed row rejected with exit 1"
  else
    fail "expected exit 1, got $rc"
  fi
  rm -rf "$root"
}

# ---------- Test 4: dedupe runs on post-stamp row ----------
test_dedupe_runs_on_post_stamp_row() {
  echo "TEST: with --stamp-now, dedupe compares the post-stamp row (two back-to-back calls => second is a dedupe no-op)"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| PENDING_STAMP | foo | bar-dedupe-stamp-now |'

  # First call: appends with fresh stamp.
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" --stamp-now )
  local rc1=$?
  if [[ $rc1 -ne 0 ]]; then
    fail "first call exited $rc1; expected 0"
    rm -rf "$root"
    return
  fi

  # Sleep 1s so the second call's `date -u` output differs by at least one second
  # — this proves dedupe is based on the ROW BODY (post-stamp content minus ts),
  # not on the exact post-stamp string.
  sleep 1

  # Grab SHA before second call.
  local before_sha after_sha
  before_sha="$( cd "$work" && git fetch --quiet origin main && git rev-parse origin/main )"
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" --stamp-now )
  local rc2=$?
  after_sha="$( cd "$work" && git fetch --quiet origin main && git rev-parse origin/main )"

  if [[ $rc2 -ne 0 ]]; then
    fail "second call exited $rc2; expected 0 (dedupe skip)"
  fi

  # Count appearances of the trailing marker on main. A correctly-deduped
  # second call leaves exactly 1.
  local count
  count="$( cd "$work" && git show origin/main:docs/pr-review-log.md | grep -c 'bar-dedupe-stamp-now' || true )"
  if [[ "$count" == "1" ]]; then
    pass "exactly one appended row after two --stamp-now calls (dedupe matched on body)"
  else
    fail "expected 1 matching row on main, got $count (before_sha=$before_sha after_sha=$after_sha)"
  fi
  rm -rf "$root"
}

# ---------- Runner ----------
test_default_preserves_caller_stamp
test_stamp_now_overrides_column_1
test_stamp_now_rejects_malformed_row
test_dedupe_runs_on_post_stamp_row

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
