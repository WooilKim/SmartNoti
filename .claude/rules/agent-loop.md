# Three-agent Improvement Loop

SmartNoti 는 세 개의 사용자 정의 subagent 로 계속 개선되는 구조입니다. 이 문서는 세 agent 가 어떻게 협조하는지 한 페이지로 정리합니다 — 새 세션/새 에이전트가 들어와도 이 그림만 읽으면 루프를 이어갈 수 있게.

## 세 agent

| Agent | 입력 | 하는 일 | 출력 | 건드리는 범위 |
|---|---|---|---|---|
| [`journey-tester`](../agents/journey-tester.md) | journey id 또는 "all" | Verification recipe 실행, 관측 결과 분류 | PR — journey 의 `last-verified` 갱신 또는 Known gaps 에 드리프트 기록 | `docs/journeys/*.md` 만 |
| [`gap-planner`](../agents/gap-planner.md) | focus 영역 or 빈 값 | Known gaps 중 가장 leverage 높은 한 건 골라 plan 문서 드래프트 | PR — 새 `docs/plans/YYYY-MM-DD-<slug>.md` | `docs/plans/` + 선택적으로 journey 의 Known-gap bullet 옆에 plan 링크 |
| [`plan-implementer`](../agents/plan-implementer.md) | plan 경로 | Task 순서대로 tests-first 구현 + journey 갱신 | PR — 실제 코드 변경 + journey 문서 동기화 | 해당 plan 이 명시한 파일 + 연결된 journey |

## 협조는 문서를 통해

세 agent 는 서로 직접 통신하지 않습니다. 문서가 공용 큐 역할을 합니다:

```
gap-planner              plan-implementer                  journey-tester
      |                          |                                |
      v                          v                                v
docs/plans/*.md  ──────────▶  코드 + journeys/*.md  ──────────▶  journeys/*.md
(status: planned)             (status: shipped)                (last-verified 갱신)
      ▲                                                             |
      └──── drift 발견 → Known gaps → gap-planner 가 집어감 ────────┘
```

- `docs-sync.md` 가 각 agent 의 공통 규약. 세 agent 모두 system prompt 에서 이 규칙을 읽고 따릅니다.
- 각 agent 는 **PR 을 여는 것이 최대**. `main` 에 직접 push 하지 않고, PR merge 는 사람이 결정합니다. 이것이 전체 시스템의 안전 경계.

## 오케스트레이션 옵션

- **수동 (v0, 지금)**: 사용자가 `/journey-test`, `/gap-plan`, `/plan-implement` 슬래시 커맨드로 호출.
- **반자동 (v1)**: `ScheduleWakeup` 으로 tester 를 주기 실행 (예: 하루 한 번). 드리프트 발견 시 보고서에서 "→ route to gap-planner" 메시지 확인 후 사용자가 planner 실행.
- **자동 (v2)**: CronCreate 로 tester 주간 실행 → 드리프트 발견 시 planner 자동 트리거 → plan PR 생성 → 사람 리뷰 후 approved 상태에서 implementer 실행.

v0 부터 시작해 agent 하나씩 신뢰도를 검증한 뒤 다음 단계로 승격합니다.

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
```

이 루프의 전체 목표는 "plan → implement → verify → 다음 plan" 이 사람의 승인 지점에서만 멈추고 돌아가는 것입니다.
