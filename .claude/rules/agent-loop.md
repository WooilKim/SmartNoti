# Nine-agent Improvement Loop

SmartNoti 는 열 개의 사용자 정의 subagent 로 계속 개선되는 구조입니다. 이 문서는 열 agent 가 어떻게 협조하는지 한 페이지로 정리합니다 — 새 세션/새 에이전트가 들어와도 이 그림만 읽으면 루프를 이어갈 수 있게. 시스템 자체도 `loop-retrospective` 가 주기적으로 돌아보며 meta-plan 으로 개선 제안을 올리기 때문에, **구조는 고정이 아니라 자기 진화함**.

장시간 무인 실행을 위해 `loop-orchestrator` 가 한 tick 의 모든 결정을 담당. `/journey-loop` 호출 시 메인 세션은 stop-check + orchestrator spawn + schedule 만 수행. orchestrator 가 내부적으로 Phase A (tester→gap-planner→plan-implementer fall-through) + Phase B (PM drainage) 를 실행하고 한 줄 요약만 반환.

## 열 agent

| Agent | 입력 | 하는 일 | 출력 | 건드리는 범위 |
|---|---|---|---|---|
| [`journey-tester`](../agents/journey-tester.md) | journey id 또는 "all" | Verification recipe 실행, 관측 결과 분류 | PR — journey 의 `last-verified` 갱신 또는 Known gaps 에 드리프트 기록 | `docs/journeys/*.md` 만 |
| [`ui-ux-inspector`](../agents/ui-ux-inspector.md) | screen/journey id 또는 "all" | 앱 스크린샷 캡처 후 `ui-improvement.md` 규칙 대비 시각 감사 | PR — journey Known gaps 에 visual drift 기록, 큰 리워크는 새 plan 문서 | `docs/journeys/*.md` + `docs/plans/` |
| [`code-health-scout`](../agents/code-health-scout.md) | path prefix / theme / "all" | 코드 스멜 정적 스캔 (파일/함수 크기, aged TODO, import 수, class 수) | PR — Moderate → journey Known gaps, Critical → refactor plan | `docs/journeys/*.md` + `docs/plans/` |
| [`gap-planner`](../agents/gap-planner.md) | focus 영역 or 빈 값 | Known gaps 중 가장 leverage 높은 한 건 골라 plan 문서 드래프트 | PR — 새 `docs/plans/YYYY-MM-DD-<slug>.md` | `docs/plans/` + 선택적으로 journey 의 Known-gap bullet 옆에 plan 링크 |
| [`plan-implementer`](../agents/plan-implementer.md) | plan 경로 | Task 순서대로 tests-first 구현 + journey 갱신 | PR — 실제 코드 변경 + journey 문서 동기화 | 해당 plan 이 명시한 파일 + 연결된 journey |
| [`coverage-guardian`](../agents/coverage-guardian.md) | PR 번호 또는 "all" | 각 PR 의 prod/test 라인 ratio 분석, OK/ADEQUATE/THIN/MISSING 분류 | PR 코멘트 + `docs/coverage-log.md` 행. 리뷰는 안 함 — PM 에게 신호만 | `docs/coverage-log.md` 만 (comment 는 GitHub 쪽) |
| [`project-manager`](../agents/project-manager.md) | PR 번호 또는 "all" | 다른 agent 가 연 PR 을 scope / traceability / CI / carve-out gate 로 심사 | `gh pr review --approve` 또는 `--request-changes`, `docs/pr-review-log.md` 에 audit row | 어떤 파일도 수정하지 않음 (log 1줄만) |
| [`loop-manager`](../agents/loop-manager.md) | `status` / `stop [reason]` / `resume` / `tune <k>=<v>` | 감사 로그 + 열린 PR + Known gap 을 읽어 dashboard 한 블록으로 보고. `stop` 은 `.claude/loop-paused` flag 를 raise (advisory), `tune` 은 loop 파라미터 변경 PR | status 텍스트 또는 ops PR (`ops/loop-*` 브랜치) | `.claude/loop-paused` flag + loop 설정 파일 (PR 통해서만) |
| [`loop-retrospective`](../agents/loop-retrospective.md) | `analyze` (default) / `propose [focus]` / `history` | 30일치 audit log + PR + plan + journey 를 읽어 per-agent 및 meta 지표를 계산, HEALTHY / QUIET / NOISY / BLOCKING 로 분류. `propose` 모드는 주요 finding 을 meta-plan 으로 초안화 | retrospective 텍스트 블록 + `docs/loop-retrospective-log.md` 행, 선택적으로 `docs/plans/YYYY-MM-DD-meta-<slug>.md` | `docs/loop-retrospective-log.md` + 선택적 meta plan (agent / rule 직접 수정 금지) |

## 협조는 문서를 통해

열 agent 는 서로 직접 통신하지 않습니다 (한 가지 예외: `loop-orchestrator` 만 다른 sub-agent 를 spawn 할 수 있음 — 그 외에는 모두 문서가 공용 큐):

```
gap-planner   plan-implementer   journey-tester   ui-ux-inspector   code-health-scout
     |               |                 |                |                  |
     v               v                 v                v                  v
 plans/*.md ─▶ 코드 + journeys/*.md ─▶ journeys/*.md + Known gaps ◀────────┘
                        │                        │
                        │                        └─▶ gap-planner
                        │
                        ▼
              ┌──────────────────────┐          ┌──────────────────────┐
              │   coverage-guardian  │  ───▶    │   project-manager    │
              │ (test/prod ratio 신호)│          │  (approve / request) │
              └──────────────────────┘          └──────────┬───────────┘
                                                           ▼
                                               approve / request-changes
                                                           │
                                                           └─▶ 사람 or agent merge
                                                                    │
                                                                    ▼
                                   ┌───────────────────────────────┐
                                   │        loop-manager           │ ◀─ /loop-status
                                   │  status · stop · resume · tune│
                                   └───────────────────────────────┘

                                   ┌───────────────────────────────┐
                                   │     loop-retrospective        │ ◀─ /retrospective
                                   │  30일 history → meta-plan 제안 │     (구조 자기진화)
                                   └───────────┬───────────────────┘
                                               │
                                               └─▶ docs/plans/*-meta-*.md
                                                   (PM carve-out → 사람 review)
```

- **행동 드리프트** (recipe ≠ 실제) 는 `journey-tester`, **시각 드리프트** (UI ≠ `ui-improvement.md`) 는 `ui-ux-inspector`, **코드 스멜** (정적 분석 기반) 은 `code-health-scout` 가 감지. 세 소스 모두 Known gaps 큐에 쌓아 `gap-planner` 가 소화.
- **PR 심사**는 `project-manager` 가 담당 — 다른 agent 가 연 PR 의 scope / traceability / CI / carve-out 을 확인하고 `--approve` 또는 `--request-changes` 를 공식 vote 로 남김. `coverage-guardian` 은 그 앞단에서 test/prod ratio 신호를 comment 로 제공.
- **관찰·제어**는 `loop-manager` 담당. 감사 로그 (`docs/auto-merge-log.md`, `docs/pr-review-log.md`) + 열린 PR + Known gap 을 읽어 상태 요약. 사용자는 `stop` / `resume` / `tune` 으로 개입 가능 — 모두 PR 로 수행, 직접 변경 없음.
- `docs-sync.md` 가 여섯 agent 의 공통 규약. 모두 system prompt 에서 이 규칙을 읽고 따릅니다.
- 각 agent 는 **PR 을 여는 것이 최대** (`project-manager` 는 리뷰까지, `loop-manager` 는 읽기 + ops PR 까지). `main` 에 직접 push 금지, merge 는 자기 scope 안에서만 허용 (`journey-tester` / `ui-ux-inspector` 는 docs-only self-merge, 나머지는 사람 merge).

## 오케스트레이션 옵션

- **수동 (v0)**: 사용자가 `/journey-test`, `/ui-inspect`, `/code-scout`, `/gap-plan`, `/plan-implement`, `/coverage-check`, `/pr-review`, `/loop-status`, `/retrospective` 로 직접 호출.
- **반자동 (v1, 지금)**: [`/journey-loop`](../commands/journey-loop.md) Ralph tick 이 `ScheduleWakeup` 으로 6시간마다 자기 자신을 재기동해 `journey-tester` 를 반복 실행. 열린 agent-origin PR 이 2개 이상 쌓이면 자동 정지. `ui-ux-inspector`, `gap-planner`, `plan-implementer` 는 여전히 수동 — 시각/plan/코드 생성은 사람 리뷰가 boundary.
- **자동 (v2, 나중)**: planner 를 "드리프트 감지 + 열린 plan 없음" 조건에서만 자동 트리거. implementer 는 사용자가 명시적으로 승인한 plan 만 자동 실행. PR merge 는 여전히 사람.
- **완전 자동 (v3)**: CI 통과한 agent-origin PR 을 agent 가 자동 머지. 추가 세팅 필요 — branch protection rule + required CI job + `gh` 머지 권한 확장 + agent 에 merge 가능 범위 명시.

v1 은 `/journey-loop` 을 한 번 호출하면 시작됩니다. 중단하려면 열린 agent-origin PR 을 두 개 이상 남겨두거나 대화에서 "stop journey loop" 로 명시.

## 각 agent 의 공통 안전 규칙

- `main` 에 직접 push 금지 — PR 만 연다.
- PR merge 금지 — 사람이 결정.
- `git push --force` / `git reset --hard` / `rm -rf` / `--no-verify` 금지 (rebase 충돌 해결 제외 — 그것도 `--force-with-lease`).
- 제품 의도가 불분명한 결정은 agent 가 단독으로 내리지 않고 보고서에 open question 으로 올린다.
- 테스트가 깨진 채로 커밋 금지.

## 실패 시 동작

- agent 가 예상 외 상황을 만나면 **멈추고 보고**. 추측으로 계속 진행하지 않는다.
- 빌드 실패 / 테스트 실패가 agent 의 변경과 명확히 무관해 보이면 → 실패 내용만 보고하고 손대지 않는다.
- PR 이 거부되면 → 보고만 하고 자동 재시도 하지 않는다 (권한 이슈일 가능성 高).

## 새 세션 체크리스트

```
1. git status 깨끗한지
2. gh pr list --state open  — 진행 중 PR 파악
3. docs/journeys/README.md Verification log 최근 섹션 — 최근 드리프트 유무
4. docs/plans/ 최신 파일 — 진행 중인 plan 확인
5. 필요 시 /journey-test all 로 현재 상태 스냅샷
6. v1 loop 이 돌고 있는지 확인: 직전 대화에서 `/journey-loop` 이 ScheduleWakeup 걸었으면 다음 tick 예정. 멈추려면 "stop journey loop" 혹은 agent-origin PR 2개 이상 남겨두기
```

이 루프의 전체 목표는 "plan → implement → verify → 다음 plan" 이 사람의 승인 지점에서만 멈추고 돌아가는 것입니다.

## 머지 자동화 (v3 로 승격 시 필요한 것)

현재 v1 은 PR 머지까지 사람이 함. agent 가 머지까지 수행하려면 다음을 추가 세팅:

1. **Branch protection**: `main` 에 "require PR, require passing CI, require review" 설정. agent 도 같은 규칙 통과해야 머지 가능.
2. **CI job**: `.github/workflows/` 에 unit test + build 자동 실행. 실패하면 머지 차단.
3. **Agent 에 머지 가능 범위 명시**: `.claude/agents/journey-tester.md` 에 "docs only + CI green + no human review requested 조건에서만 `gh pr merge --auto --squash`". 코드 변경 포함 PR 은 여전히 사람 리뷰 필수.
4. **`gh` 권한**: 로컬 gh token 이 `pull_request:write` 를 포함해야 머지 가능. 현재 세팅은 기본적으로 PR 개설만.
5. **회수 경로**: agent 가 머지한 PR 을 사용자가 revert 할 수 있도록, agent 는 머지 후 해당 PR 번호를 별도 로그 파일에 기록 (`docs/auto-merge-log.md`).

이 세팅 없이 agent 에 `gh pr merge` 허용만 하면 안 된다 — CI 가 없으면 agent 가 자기 변경을 검증도 안 한 채 main 에 밀어넣게 됨.
