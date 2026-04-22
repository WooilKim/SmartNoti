#!/usr/bin/env bash
# Tests for .claude/lib/audit-log-append.sh
#
# Runs the helper against a throwaway temporary git repo that mocks
# `origin` + `gh`, then asserts on commit content, branch behavior, and exit codes.
#
# Usage: bash .claude/lib/tests/audit-log-append-test.sh
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

# Build a tmp sandbox that looks like a clone of SmartNoti: a "remote" bare repo
# and a working clone. Returns the path to the working clone on stdout.
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

# Shim `gh` so that `gh pr create` succeeds in tests without network.
install_gh_shim() {
  local dir="$1"
  mkdir -p "$dir/bin"
  cat >"$dir/bin/gh" <<'EOF'
#!/usr/bin/env bash
# Minimal gh shim: for `gh pr create`, echo a fake URL and exit 0.
# For anything else, exit 0 silently.
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

# ---------- Test 2: basic append to pr-review log lands on main ----------
test_basic_append_pr_review() {
  echo "TEST: basic append to pr-review-log goes to main"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-04-21T12:00:00Z | #999 | project-manager | approve | test row |'
  (
    cd "$work"
    PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited with $rc, expected 0"
    rm -rf "$root"
    return
  fi
  # fetch remote state
  ( cd "$work" && git fetch --quiet origin main )
  if ( cd "$work" && git show origin/main:docs/pr-review-log.md ) | grep -Fq "$row"; then
    pass "row present on origin/main"
  else
    fail "row missing from origin/main after direct-append"
  fi
  # ensure ops branch was deleted or not left around polluting main history
  if ( cd "$work" && git log origin/main --oneline | grep -q "docs(audit)" ); then
    pass "commit on main with docs(audit) subject"
  else
    fail "no docs(audit) commit on main"
  fi
  rm -rf "$root"
}

# ---------- Test 3: dedupe skip when row already present ----------
test_dedupe_skip() {
  echo "TEST: --dedupe skips when row already present on main"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-04-21T12:05:00Z | #1001 | project-manager | approve | dedupe case |'
  # First append
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" >/dev/null )
  # Second append with dedupe; should detect + no-op, exit 0, no new commit
  local before_sha
  before_sha="$( cd "$work" && git fetch --quiet origin main && git rev-parse origin/main )"
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log pr-review --row "$row" --dedupe )
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

# ---------- Test 4: auto-merge log target works ----------
test_auto_merge_target() {
  echo "TEST: --log auto-merge writes to docs/auto-merge-log.md"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"
  local row='| 2026-04-21T12:10:00Z | journey-tester | #1234 | some-journey | https://example.com/run |'
  ( cd "$work" && PATH="$root/bin:$PATH" bash "$HELPER" --log auto-merge --row "$row" )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "auto-merge append exited $rc, expected 0"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  if ( cd "$work" && git show origin/main:docs/auto-merge-log.md ) | grep -Fq "$row"; then
    pass "row present in auto-merge-log on main"
  else
    fail "row missing from auto-merge-log on main"
  fi
  rm -rf "$root"
}

# ---------- Test 5: non-ff retry recovers after remote advances ----------
test_retry_on_race() {
  echo "TEST: retry recovers when origin/main advances mid-run (race)"
  local root work
  root="$(make_sandbox)"
  work="$root/work"
  install_gh_shim "$root"

  # Create a second clone to simulate a racing writer.
  ( cd "$root" && git clone --quiet remote.git other )
  ( cd "$root/other" && git config user.email other@example.com && git config user.name other )

  # Pre-race: both clones up-to-date.
  # Shim: override `git push origin HEAD:main` in our helper's PATH so the FIRST
  # push attempt races a competing commit, then succeeds on retry.
  mkdir -p "$root/hooks"
  cat >"$root/hooks/race-trigger.sh" <<EOF
#!/usr/bin/env bash
# Introduce a racing commit on the remote if the marker file exists.
MARKER="$root/.race-armed"
if [[ -f "\$MARKER" ]]; then
  rm -f "\$MARKER"
  cd "$root/other"
  git fetch --quiet origin main
  git reset --hard origin/main >/dev/null
  echo "| 2026-04-21T12:15:00Z | #9998 | racer | approve | race row |" >> docs/pr-review-log.md
  git add docs/pr-review-log.md
  git commit --quiet -m "docs(audit): race row"
  git push --quiet origin main
fi
EOF
  chmod +x "$root/hooks/race-trigger.sh"

  # Arm the race.
  touch "$root/.race-armed"

  # Run helper with a pre-push hook that triggers the race before the first push.
  local row='| 2026-04-21T12:15:30Z | #9999 | project-manager | approve | winner row |'
  (
    cd "$work"
    AUDIT_LOG_PRE_PUSH_HOOK="$root/hooks/race-trigger.sh" \
      PATH="$root/bin:$PATH" \
      bash "$HELPER" --log pr-review --row "$row"
  )
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "helper exited $rc on race; expected 0 (retry should recover)"
    rm -rf "$root"
    return
  fi
  ( cd "$work" && git fetch --quiet origin main )
  if ( cd "$work" && git show origin/main:docs/pr-review-log.md ) | grep -Fq "$row"; then
    pass "winner row on main after retry"
  else
    fail "winner row missing from main after race retry"
  fi
  if ( cd "$work" && git show origin/main:docs/pr-review-log.md ) | grep -Fq "race row"; then
    pass "racer row preserved on main (no clobber)"
  else
    fail "racer row lost — retry clobbered remote state"
  fi
  rm -rf "$root"
}

# ---------- Runner ----------
test_helper_exists
test_basic_append_pr_review
test_dedupe_skip
test_auto_merge_target
test_retry_on_race

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
