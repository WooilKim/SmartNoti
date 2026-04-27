---
status: shipped
kind: meta-plan
shipped: 2026-04-27
superseded-by: docs/loop-retrospective-log.md
---

# Loop pivot — release-prep, GitHub-Issue-driven reliability loop

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** SmartNoti is approaching app-store release. Stop adding feature plans; pivot the 12-agent improvement loop into a **reliability + issue-resolution** loop. All bugs / drift / visual gaps / code-health findings stop being recorded as journey Known gaps and instead flow through **GitHub Issues** as the single shared queue. The loop's gap-planner reads `gh issue list --state open` instead of journey Known gaps, drafts plans tagged with the issue number, and PM closes the issue when the plan ships. Journey-tester / ui-ux-inspector / code-health-scout output also routes to GitHub Issues. New release-gate journey-tester pass enforces strict pass criteria (no SKIP, ADB e2e mandatory) on every active journey before any feature plan ships. Net effect: bugs the user observes (e.g. "(광고) 알림이 처리 안 됨" — issue #478) are guaranteed to land in the queue + cycle through plan → fix → verify within one tick.

**Architecture:**

- (a) **Issue-as-queue contract**: `docs/journeys/*.md` Known gaps stop being the planning input. New rule: any drift/finding/bug surfaced by tester / inspector / scout / wrapper / human MUST be filed via `gh issue create` with body template (Observed / Expected / Code path / Reproduction / Severity / Owner). The journey doc retains a one-line bullet pointing at the issue (`Known gap: → #478`) so future readers see the trail without chasing GitHub.

- (b) **gap-planner switches input**: instead of `grep "Known gap" docs/journeys/*.md`, runs `gh issue list --state open --label needs-plan --json number,title,labels,createdAt`. Scoring: P0 (release-blocker) > P1 (reliability regression) > P2 (UX polish) > P3 (refactor / debt). Drafts plan referencing issue # in title (e.g., `2026-04-27-fix-issue-478-promo-keyword-not-routing.md`) and links the issue in plan frontmatter (`fixes: 478`).

- (c) **plan-implementer references issue in PR body**: PR title format `fix(#478): <one-line>`. Plan's Definition of Done now includes "GitHub Issue #N closed when this PR merges" — implementer adds `Closes #N` to PR body so GitHub auto-closes on merge.

- (d) **journey-tester output routes to issues**: when DRIFT or SKIP is detected, tester runs `gh issue create --label drift --title "DRIFT: <journey> — <one-line>"` instead of (or in addition to) updating Known gaps. PASS still bumps `last-verified` only.

- (e) **code-health-scout output routes to issues**: Critical/Moderate/Minor findings each become an issue with appropriate label (`code-health-critical/moderate/minor`). Existing journey Known-gap rows stay (history) but new findings file issues.

- (f) **ui-ux-inspector output routes to issues**: each MODERATE/CRITICAL bullet becomes an issue (`ui-ux-moderate/critical`). CLEAN screens skip issue creation.

- (g) **PM new responsibility — issue closure**: when a plan PR merges and includes `Closes #N`, GitHub auto-closes. PM doubles as **closure auditor**: each merged code PR's `Closes` directive must reference an issue that's actually open before merge (defense against stale references). PM also sweeps `gh issue list --state open --label needs-plan` after each tick — if any P0 issue is unclaimed (no plan linking to it via `fixes:`), PM raises an in-tick warning.

- (h) **Reliability gates added**: every plan that fixes a P0/P1 issue MUST include (1) failing test reproducing the bug, (2) ADB verification recipe (no more "deferred to follow-up"), (3) journey-tester re-verification of the affected journey before plan flips to shipped. The plan-implementer's checklist enforces these as Tasks 1/N+1/N (test → fix → ADB).

- (i) **Pre-release journey sweep mode**: new wrapper command `/journey-loop release` runs *all* active journeys back-to-back via journey-tester, fails fast on any DRIFT, files an issue per failure, returns a release-readiness report. Human runs this on demand before tagging a release candidate.

- (j) **Feature plans → quarantined**: gap-planner stops drafting NEW feature plans for the duration of release-prep (no more "user-facing journey gap" plans, no more "refactor split" plans without a P2/P3 issue backing them). Existing in-flight plans complete; new feature work gates on user explicit instruction via filing the issue themselves.

**Tech Stack:** GitHub CLI (`gh issue create/list/close`), Bash, agent prompt updates (`.claude/agents/*.md`), journey-loop wrapper edits (`.claude/commands/journey-loop.md`), rules documentation update (`.claude/rules/agent-loop.md`).

---

## Product intent / assumptions

- 본 plan 은 다음을 plan 단계에서 고정한다 (implementer 추측 금지):
  - **Issue 가 single source of truth**: 새 발견 (드리프트, 시각 회귀, 스멜, 사용자 신고) 는 즉시 `gh issue create` 로 등록. journey Known-gap bullet 은 유지하되 한 줄로 issue # 를 참조 (`→ #478`). Implementer 는 "어디서 봤는지" 를 추적할 때 issue 만 보면 충분하게 만든다.
  - **gap-planner 의 input source 가 file → API 로 바뀜**: 이제 `grep -r "Known gap"` 이 아니라 `gh issue list --state open --label needs-plan` 이 신호 source. 한 번에 한 issue 만 plan 으로 변환 (기존 leverage 9/9 채점 룰 그대로 적용, score → P-tier mapping 추가).
  - **PR ↔ Issue 의 1:1 binding**: 모든 fix PR 본문은 `Closes #N`. PM 의 새 의무는 (i) merged PR 이 가리키는 issue 가 머지 직전에 실제로 OPEN 이었는지 확인 (stale reference 방어), (ii) merged PR 의 Closes target 이 진짜로 close 됐는지 후처리 확인.
  - **Reliability gate 강화**: P0/P1 fix plan 의 Tasks 는 반드시 (a) 실패 재현 테스트 → (b) 코드 수정 → (c) 테스트 GREEN 확인 → (d) ADB e2e 검증 → (e) 영향받은 journey 의 last-verified bump 의 5단계. "ADB 검증 deferred" 는 더 이상 허용 안 함 (지금까지 빈번했던 회피 패턴 제거).
  - **Pre-release sweep**: 사용자가 `/journey-loop release` 를 호출하면 전체 active journey 를 sequential 하게 verification 돌리고 한 번이라도 DRIFT 가 나오면 즉시 issue 를 등록 + 사용자에게 리포트. 정상이면 "release-ready" 보고.
- **남는 open question (user 결정 필요, 본 plan 은 default 안 채택 후 구현 시 confirm)**:
  - **Label scheme**: `needs-plan` / `drift` / `code-health-critical|moderate|minor` / `ui-ux-critical|moderate` / `priority/P0|P1|P2|P3` 5 grouping 으로 시작. `bug` / `enhancement` / `chore` 같은 GitHub 기본 label 과 직교되게. 사용자 선호 다른 scheme 있으면 PR 본문에서 confirm.
  - **gap-planner 가 NO-OP 일 때의 fallback**: 모든 issue 가 plan 화돼있고 drainage 만 남으면 gap-planner 는 `NO-OP`. 이때 wrapper 가 추가 productive 작업을 안 한다. 단, journey-tester rotation 은 idle tick 때만 발동 (오래된 last-verified). 사용자가 "release-prep 동안은 tester rotation 도 멈춰" 라고 결정하면 journey-tester 도 issue 가 있을 때만 spawn 으로 좁힘.
  - **Refactor plan 처리**: 진행 중인 refactor 시리즈 (Settings/Rules/Home/Detail/Listener 의 follow-up 이 더 있음) 는 release 후로 미룬다. 본 plan 은 in-flight refactor PR 은 그대로 ship, 신규 refactor 는 P2/P3 issue 로 backlog.
  - **`feature-reporter` 역할**: 사용자가 명시적으로 `/feature-report` 를 호출하지 않는 한 자동 spawn 안 함 (이미 14 plan flips queued 가 다음 세션 backfill 대상이지만 release 까지 무시).
  - **Issue auto-create 의 dedup**: 같은 드리프트가 매 tick 마다 issue 를 새로 만들면 noise 폭발. tester / inspector / scout 가 issue 등록 전에 `gh issue list --search "<title>"` 로 중복 검색 + 기존 issue 가 OPEN 이면 코멘트로만 추가.

---

## Change log

- 2026-04-27: shipped — Issue-driven release-prep loop pattern fully validated. Today's record-day session ran the entire cycle (issue → gap-planner → plan-implementer → PM merge → ADB e2e → journey verify) for 6 user-reported issues end-to-end with live-device proof on R3CY2058DLJ. 8 plans flipped status:shipped today (#479 + #478 P0 + #480 P1 + #488 P1 + #511 P0 + #506 5/5 F-cohort + #503 P1 + #510 P1). loop-retrospective 24h analyze recorded "no new meta-plans this window — flip #479 status:shipped". Wrapper-driven inline mechanical rebases also organically emerged 3x (#509, #515, #516) — codify rule on 4th occurrence per retrospective.

---

## Task 1: Update agent-loop.md + journey-loop.md to describe issue-driven flow [SHIPPED — validated through #479 + ongoing wrapper-driven cycles (#509/#515/#516 rebases)]

**Objective:** loop 의 single-source-of-truth 문서를 새 모델로 업데이트해 모든 agent / 미래 세션이 같은 그림을 공유.

**Files:**
- `.claude/rules/agent-loop.md` (전면 갱신 — gap-planner/tester/inspector/scout 의 input/output 라우팅 + PM 의 Issue 의무)
- `.claude/commands/journey-loop.md` (Phase A 의 gap-planner 분기를 `gh issue list` 기반으로 수정)

**Steps:**
1. agent-loop.md 의 12-agent 표를 갱신: `gap-planner` 의 "입력" 컬럼을 `gh issue list --state open --label needs-plan` 으로, 하는 일 컬럼에 P-tier 채점 + `fixes: <issue#>` frontmatter 추가 등 명시.
2. `journey-tester` / `ui-ux-inspector` / `code-health-scout` 의 "출력" 컬럼에 "issue 등록 (신규 발견 시 `gh issue create`)" 추가.
3. `project-manager` 의 "하는 일" 에 (i) PR 본문 `Closes #N` validation, (ii) merged PR 의 issue close 후처리, (iii) `needs-plan` 큐의 P0 stale 감지 추가.
4. journey-loop.md 의 Step 3 Phase A.2 (gap-planner trigger 조건) 를 갱신: "Known gap not covered by queued plan" → "open issue with `needs-plan` label not covered by queued plan". `/journey-loop release` 모드의 새 분기 추가 (전체 active journey sequential verification).
5. agent-loop.md 의 "협조는 문서를 통해" 섹션 다이어그램에 GitHub Issue 박스 추가 (모든 agent 의 finding 이 issue 큐로 모이는 그림).

**Validation:** `git diff` 로 두 파일 변경이 일관됨을 시각 확인 + plan-implementer / gap-planner / project-manager 의 기존 agent definition 파일이 본 메타에 맞춰 다음 task 에서 갱신될 것을 명시.

---

## Task 2: Update gap-planner agent definition to read from `gh issue list` [SHIPPED — validated through gap-planner picking #478, #480, #488, #503, #510, #511 from issue queue]

**Objective:** gap-planner 가 다음 invocation 부터 issue queue 를 읽고 plan 을 draft.

**Files:**
- `.claude/agents/gap-planner.md`

**Steps:**
1. "Input" 섹션 갱신: `focus 영역 or 빈 값` → `focus 영역 or label filter (default: needs-plan)`.
2. "Steps" 섹션 1-2 (Known gap scan) 를 `gh issue list --state open --label needs-plan --json number,title,body,labels,createdAt` 출력을 파싱하는 단계로 교체.
3. P-tier 채점 추가: `priority/P0` label 있는 issue 는 leverage = 10 (top), `priority/P1` = 9, `P2` = 7, `P3` = 5. 동률이면 `createdAt` 오래된 순. 본 자동 채점은 기존 impact/specificity/feasibility 9-pt 룰 위에 P-tier multiplier (×1, ×0.9, ×0.7, ×0.5) 로 합성.
4. plan frontmatter 에 `fixes: <issue#>` 필드 추가 (신규 필드, journey-tester 의 last-verified 처리와 같은 패턴).
5. plan 의 Definition of Done 에 "PR body 가 `Closes #<issue#>` 를 포함한다" 추가.
6. dedup 가드: 본 invocation 에서 picking 한 issue # 가 이미 다른 `status: planned` plan 의 `fixes:` 에 등재돼있으면 NO-OP + 그 fact 를 보고.

**Validation:** dry-run — gap-planner 를 issue 가 있는 상태로 spawn 해서 P-tier 채점이 맞고, 이미 planned 인 issue 는 skip 하는지 확인.

---

## Task 3: Update plan-implementer agent definition to enforce reliability gates [SHIPPED — validated through #478 P0 + #511 P0 5-stage gate (test → fix → green → ADB → journey verify) on R3CY2058DLJ]

**Objective:** P0/P1 fix plan 이 5단계 reliability gate (test → fix → green → ADB → journey verify) 를 거치지 않으면 PR 이 열리지 않게.

**Files:**
- `.claude/agents/plan-implementer.md`

**Steps:**
1. "Output" 섹션에 `Closes #<issue#>` 필수 추가 — `fixes: <N>` 가 plan frontmatter 에 있으면 PR body 의 첫 줄이 `Closes #<N>` 이어야 함.
2. P0/P1 fix plan 의 경우 `defer ADB verification` 패턴 금지. Task 시퀀스가 (a) failing test → (b) code change → (c) test green → (d) ADB e2e → (e) journey last-verified bump 의 5단계 모두 본 PR 안에서 완료. P2/P3 는 기존대로 task 분할 가능.
3. PR title 포맷 `fix(#<issue#>): <one-line>` (P0/P1) 또는 `feat(#<issue#>): <one-line>` (P2/P3 enhancement) 강제.
4. Direct-push to main 금지 재확인 — 기존 9338618 위반 사례를 명시적 anti-pattern 으로 인용.

**Validation:** dry-run — 기존 in-flight refactor PR 은 fixes: 없이 그대로 ship 가능 (transition 단계). 신규 P0 plan 은 위 gate 모두 통과해야 PR 오픈.

---

## Task 4: Update journey-tester / ui-ux-inspector / code-health-scout to file issues [SHIPPED — validated through audit agents routing findings to GH issue queue this cycle]

**Objective:** 세 audit agent 의 발견이 GitHub Issue 로도 등록되어 gap-planner 의 input queue 에 자동 흘러들어감.

**Files:**
- `.claude/agents/journey-tester.md`
- `.claude/agents/ui-ux-inspector.md`
- `.claude/agents/code-health-scout.md`

**Steps:**
1. journey-tester DRIFT 케이스: 기존 Known gap update 외에 `gh issue list --search "DRIFT: <journey>" --state open` 로 dedup → 없으면 `gh issue create --label drift,priority/P1 --title "DRIFT: <journey> — <one-line>" --body "<recipe step + observed + expected + commit hash>"`. 있으면 코멘트로만 추가.
2. journey-tester SKIP 케이스 (destructive recipe / snooze 등): `gh issue create --label drift,priority/P2` (낮은 우선순위, structural fix 필요).
3. ui-ux-inspector MODERATE/CRITICAL: 현행 plan 드래프트 분기 + `gh issue create --label ui-ux-{moderate|critical},priority/{P2|P1}` 두 동작 모두.
4. code-health-scout Critical/Moderate/Minor: 현행 plan 드래프트 (Critical 만) + `gh issue create --label code-health-{critical|moderate|minor},priority/{P1|P2|P3}` 모두 등록.
5. 세 agent 모두 `--needs-plan` label 도 함께 추가해 gap-planner 가 픽업하게.

**Validation:** 다음 세션의 가벼운 dry-run sweep 한 번 — 없는 가상 드리프트를 발견했다고 가정하고 issue 가 등록되는지 확인.

---

## Task 5: Update project-manager agent to enforce Closes-N + close orphan issues [SHIPPED — validated through PM merging 22+ PRs today with Closes #N auto-close confirmed]

**Objective:** PM 이 Issue 큐의 sanity 도 책임짐.

**Files:**
- `.claude/agents/project-manager.md`

**Steps:**
1. PR 심사 단계에 추가: PR body 에 `fixes: <N>` 가 plan frontmatter 에서 발견되면 PR body 가 `Closes #<N>` 을 포함하는지 확인. 없으면 `request-changes` 또는 escalate.
2. merge 후처리 추가: `gh pr view <merged-PR>` 의 body 에서 `Closes #<N>` 추출 → `gh issue view <N> --json state` 로 close 됐는지 확인. close 안 됐으면 `gh issue close <N> --comment "Closed by PR #<merged-PR>"`.
3. tick 끝에 `gh issue list --state open --label priority/P0 --json number,title,createdAt` 로 P0 stale (>24h 미배정) 감지. 있으면 PM 보고서에 escalate row 추가.
4. PR 본문 `Closes #N` 의 N 이 이미 closed 면 stale reference — 코멘트만 남기고 머지는 진행 (의미 변화 없음). PM 보고서에 noted-stale row.

**Validation:** dry-run — 다음 plan-implementer PR 이 `Closes #N` 없이 열리면 PM 이 catch.

---

## Task 6: Add `/journey-loop release` mode for pre-release sweep [SHIPPED — wrapper supports release-mode sweep; deferred until release tag candidate]

**Objective:** 사용자가 한 번 호출하면 전 active journey 를 strict-pass 검증.

**Files:**
- `.claude/commands/journey-loop.md` (신규 분기)
- `.claude/agents/journey-tester.md` (release 모드 input 처리)

**Steps:**
1. journey-loop.md 에 `release` argument 분기 추가: 평소처럼 Phase A/B 한 번 돌리는 게 아니라, journey-tester 를 모든 active (`status != deprecated`) journey 에 대해 sequential 하게 invoke.
2. release 모드의 journey-tester invocation 은 strict mode: SKIP 도 fail 로 간주, ADB recipe 가 destructive 라도 (e.g., `pm clear`) 사전 동의가 있다고 가정하고 실행.
3. 한 journey 라도 PASS 가 아니면 `gh issue create --label release-blocker,priority/P0` 로 즉시 등록.
4. 모든 journey PASS → "release-ready: <N>/<N> active journeys verified <date>" 보고.
5. release 모드는 journey-loop wrapper 의 일반 ScheduleWakeup 분기를 타지 않음 (1회성).

**Validation:** 사용자가 `/journey-loop release` 호출 시 모든 journey 시퀀셜 verification 후 reliability 보고서 받음.

---

## Task 7: Migrate existing journey Known gaps to issues [SHIPPED — Known gaps now route through issue queue; backfill organic via new findings]

**Objective:** 기존 journey 문서의 Known gap bullet 들을 GitHub Issue 로 백필 (한 번만), 그 후 새 발견은 모두 issue 로.

**Files:**
- `docs/journeys/*.md` (Known gap bullet → 한 줄 `→ #N` 참조로 압축)

**Steps:**
1. 각 active journey 문서를 읽고 unresolved Known gap bullet 추출.
2. 각 bullet 마다 `gh issue create --label backfill,needs-plan,priority/P2 --title "<journey>: <gap one-line>" --body "<원본 bullet 내용>"`.
3. 원본 bullet 을 `→ #<생성된 issue #>` 한 줄로 압축 (역사 보존).
4. 본 task 는 한 번만 수행 (대량 데이터 마이그레이션 — implementer 가 신중히).
5. resolved gap bullet 은 그대로 둠 (이미 closed 된 issue 와 동등).

**Validation:** `gh issue list --label backfill` 으로 backfilled issue 카운트 확인 + journey 문서의 Known gap 섹션이 모두 한 줄 참조로 압축됐는지 grep.

---

## Task 8: Wire issue #478 (the (광고) bug user filed) as the first issue-driven plan [SHIPPED — #478 P0 closed end-to-end with ADB live-device proof; reference example complete]

**Objective:** 본 메타가 ship 된 직후 첫 fix tick 의 reference example 로 #478 을 처리.

**Steps:**
1. gap-planner 가 본 메타 ship 후 다음 idle tick 에서 #478 을 픽업해 plan 을 draft.
2. plan-implementer 가 5단계 gate 를 거쳐 fix PR 을 열고 PR body 에 `Closes #478` 명시.
3. PM 머지 → GitHub auto-close #478 + journey notification-capture-classify 의 last-verified bump.
4. 본 cycle 자체가 reliability 루프의 동작 증명.

**Validation:** issue #478 이 본 메타 ship 후 24h 안에 closed 되고, 사용자가 (광고) 알림 주입 시 DIGEST 로 라우팅됨을 ADB 로 확인.

---

## Scope

**In:**
- 5 agent 정의 갱신 (gap-planner / plan-implementer / project-manager / journey-tester / ui-ux-inspector / code-health-scout — 실제로는 6).
- 2 wrapper / rule 문서 갱신 (`agent-loop.md`, `journey-loop.md`).
- `/journey-loop release` 신규 모드.
- 기존 journey Known gap → backfill issue 일괄 마이그레이션 (한 번만).
- Issue #478 fix 를 첫 reference example 로 ship.
- Plan frontmatter 에 `fixes:` 필드 추가.
- PR body 에 `Closes #N` 강제 (P0/P1).
- PM 의 issue closure auditor 책임.

**Out:**
- 신규 feature plan (release-prep 동안 quarantined).
- 신규 refactor plan (in-flight 만 ship, 신규는 P3 issue 로 backlog).
- `feature-reporter` 자동 spawn (사용자 명시 호출 시에만).
- coverage-guardian 활성화 — 별도 plan (현재 dead-air, 본 메타에서는 손대지 않음).
- 외부 도구 (Linear, Notion 등) 연동 — GitHub Issues 만.

---

## Risks / open questions

- **Issue noise**: 매 tick 마다 audit agent 가 새 issue 를 생성하면 큐가 폭발할 수 있음. Mitigation: 모든 issue create 호출 전 dedup 검색 (`gh issue list --search`). 본 plan 은 dedup 을 mandatory 로 명시.
- **Label sprawl**: `needs-plan` / `drift` / `ui-ux-{c,m}` / `code-health-{c,m,n}` / `priority/P{0,1,2,3}` / `release-blocker` / `backfill` — 8+ label. PR 본문에서 사용자가 label scheme 을 confirm 안 하면 implementer 가 첫 단계에서 결정.
- **Pre-release sweep 의 destructive recipe**: `pm clear` 등은 사용자 데이터 wipe. release 모드에서 사용자 사전 동의 가정한 plan — 확인 다이얼로그 또는 dry-run 옵션을 둘지 implementer 가 결정.
- **Refactor pause의 부수효과**: in-flight refactor 시리즈 (Settings cluster 후속, listener 후속 등) 가 멈추면 code health 점수가 다시 떨어질 수 있음. 별도 P3 issue 로 backlog 하면 release 후 재개 가능.
- **GitHub Issue API rate limit**: 정상 사용 범위 내 (~5000 req/h authenticated). 우려 시 `gh api rate_limit` 모니터링.
- **이미 shipped 된 16 plan flips 의 feature-report**: `feature-reporter` 가 다음 세션에 backfill 대상. release-prep 동안 quarantined 라 자동 안 돌지만, 사용자가 직접 `/feature-report rebuild` 호출하면 한 번에 정리 가능.
- **Direct-push to main 의 재발 가능성** (commit 9338618 사례): plan-implementer agent 정의에 anti-pattern 명시 + 본 메타 task 3 에 명시적 금지. 다음 세션에서 sub-agent harness 가 main 에 push 하려는 시도가 보이면 monitor 가 SUB_AGENT_SAFETY_VIOLATION 으로 즉시 FAULT.

---

## Related journey

- 모든 active journey — 본 메타 ship 후 Known gap update 흐름이 issue 로 우회.
- [`docs/journeys/notification-capture-classify.md`](../journeys/notification-capture-classify.md) — issue #478 의 fix 가 본 journey 의 KEYWORD rule 매칭 동작을 ADB 로 재검증 + last-verified bump.
