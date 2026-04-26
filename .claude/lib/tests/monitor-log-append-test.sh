#!/usr/bin/env bash
# Tests for .claude/lib/monitor-log-append.sh
#
# Sibling of audit-log-append-test.sh. Covers the happy-path contract for the
# loop-monitor tick-row helper: append exactly one row to docs/loop-monitor-log.md
# on origin/main inside a single call (short-lived ops branch + ff-push), with
# default-on dedupe to keep accidental re-invocations idempotent.
#
# Usage: bash .claude/lib/tests/monitor-log-append-test.sh
#
# Exits 0 if all tests pass, 1 if any test fails.

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

# Build a tmp sandbox that looks like a clone of SmartNoti: a "remote" bare repo
# and a working clone seeded with docs/loop-monitor-log.md.
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
  )
  echo "$root"
}

# Shim `gh` so that `gh pr create` succeeds in tests without network.
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

# ---------- Test 1: helper exists and is executable ----------
test_helper_exists() {
  echo "TEST: helper script exists and is executable"
  if [[ -x "$HELPER" ]]; then
    pass "helper script present"
  else
    fail "helper script missing or not executable at $HELPER"
  fi
}

# ---------- Test 2: basic append lands on main ----------
test_basic_append() {
  echo "TEST: basic append to loop-monitor-log goes to main"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-04-26T05:00:00Z | PASS | 0 | none | basic-append-test |'
  (
    cd "$work"
    PATH="$root/bin:$PATH" bash "$HELPER" --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc, expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  if ( cd "$work" && git show origin/main:docs/loop-monitor-log.md ) | grep -Fq "$row"; then
    pass "row present on origin/main"
  else
    fail "row missing from origin/main after direct-append"
  fi
  if ( cd "$work" && git log origin/main --oneline | grep -q "docs(monitor)" ); then
    pass "commit on main with docs(monitor) subject"
  else
    fail "no docs(monitor) commit on main"
  fi
  # Working tree should not be dirty after the call.
  local dirty
  dirty="$( cd "$work" && git status --porcelain )"
  if [[ -z "$dirty" ]]; then
    pass "working tree clean after helper exit"
  else
    fail "working tree dirty after helper exit: $dirty"
  fi
  rm -rf "$root"
}

# ---------- Test 3: dedupe skip when row already present ----------
test_dedupe_skip() {
  echo "TEST: dedupe (default-on) skips when identical row already present on main"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-04-26T05:05:00Z | PASS | 0 | none | dedupe-test |'
  # First append.
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --row "$row" >/dev/null )
  # Second append; should detect + no-op, exit 0, no new commit.
  local before_sha
  before_sha="$( cd "$work" && git fetch --quiet origin main && git rev-parse origin/main )"
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --row "$row" )
  local rc=$?
  local after_sha
  after_sha="$( cd "$work" && git fetch --quiet origin main && git rev-parse origin/main )"
  if [[ $rc -eq 0 ]]; then
    pass "dedupe run exited 0"
  else
    fail "dedupe run exited $rc, expected 0"
  fi
  if [[ "$before_sha" == "$after_sha" ]]; then
    pass "dedupe did not create a new commit"
  else
    fail "dedupe created a new commit (before=$before_sha after=$after_sha)"
  fi
  rm -rf "$root"
}

# ---------- Test 4: --source flag stamps commit subject ----------
test_source_flag_stamps_commit() {
  echo "TEST: --source <agent> records the invoking agent in the commit subject"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-04-26T05:10:00Z | PASS | 0 | none | source-tagged row |'
  (
    cd "$work"
    PATH="$root/bin:$PATH" bash "$HELPER" --row "$row" --source loop-monitor
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
test_helper_exists
test_basic_append
test_dedupe_skip
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
