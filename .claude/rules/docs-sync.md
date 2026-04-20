# Docs / Code Sync Rule

SmartNoti 는 두 종류의 문서와 실제 코드가 상시 일치해야 합니다. 이 파일은 그 약속을 보존해서, 새 세션이나 새 에이전트가 들어와도 같은 원칙으로 이어서 일할 수 있게 합니다.

## 세션 시작할 때 반드시 먼저 확인

1. `docs/journeys/README.md` — 현재 구현된 사용자 여정 인덱스 + Verification log. 앱이 "지금 어떻게 동작하는지" 의 계약.
2. `docs/plans/` — 앞으로 할 일 / 진행 중 계획. 파일명이 `YYYY-MM-DD-<slug>.md` 이므로 최근 날짜를 보면 작업 맥락을 가늠할 수 있음.
3. 열린 PR 목록 (`gh pr list`) — 진행 중인 코드 변경.

이 세 가지를 읽고 나서야 새 작업을 시작합니다.

## 두 문서 디렉토리의 역할

| 디렉토리 | 의미 | 언제 쓰는가 |
|---|---|---|
| `docs/journeys/` | **현재 구현의 contract** | 이미 shipped 된 기능. 사용자 관측 동작을 관측 가능한 단계로 서술 |
| `docs/plans/` | **앞으로 할 일** | 구현 전 계획, 의사결정. shipped 된 뒤에도 삭제하지 않고 역사로 남김 |

둘을 섞지 않습니다. plan 에 "현재 동작" 을 쓰지 말고, journey 에 "앞으로 할 일" 을 쓰지 않습니다.

## Journey 문서를 건드려야 하는 순간

사용자 관측 동작이 바뀌는 PR 은 **반드시** 다음 중 하나를 수행:

1. 관련 journey 문서의 Observable steps / Exit state / Code pointers / Known gaps 를 갱신하고 Change log 에 날짜·요약·commit 해시 추가
2. 새 journey 문서를 작성 (`docs/journeys/<id>.md` + README 인덱스에 추가)
3. 더 이상 맞지 않는 기존 journey 를 `status: deprecated` 로 전환 + 이유 명시

코드만 고치고 문서를 안 고치면 다음 세션이 거짓말을 읽게 됩니다. 이걸 막기 위한 규칙입니다.

## Plan 문서의 생애주기

1. 신규 기능 착수 전: `docs/plans/YYYY-MM-DD-<slug>.md` 작성. `status: planned`.
2. 구현 중: plan 상단에 `status: in-progress` 유지. 큰 설계 변경이 있으면 plan 본문도 갱신.
3. Shipped: plan 은 남겨두고, 해당 journey 문서를 새로 만들거나 기존을 갱신. plan 의 frontmatter 는 `status: shipped` + `superseded-by:` 로 journey 링크.
4. Plan 은 절대 지우지 않습니다 (왜 그렇게 만들었는지의 근거가 됨).

## Verification recipe 실행 규칙

- `last-verified` frontmatter 는 **실제로 verification recipe 를 실행한 날짜**만 반영. 단순히 문서를 편집한 날짜로 갱신하지 않습니다.
- Drift (문서 ≠ 실제 동작) 를 발견하면:
  1. 어느 쪽이 맞는지 판단 — 제품 의도를 보고 결정
  2. 코드가 맞다 → 문서 수정
  3. 문서가 맞다 → 코드 수정 (별도 PR 또는 이슈)
  4. 애매하다 → 사용자에게 물어보기
- 드리프트를 조용히 덮지 않습니다. README 의 Verification log 에 날짜 + 결과 + 원인을 기록.

## Claude / 에이전트가 새 세션에서 이어갈 때

다음 체크리스트를 밟습니다:

1. `git status` / `git log origin/main..HEAD` — 현재 브랜치 상태 확인
2. `gh pr list --state open` — 열린 PR 확인, 각 PR 의 요약 읽기
3. `cat docs/journeys/README.md` — 전체 journey 맵과 Verification log 확인
4. 최근 수정된 plan 확인: `ls -lt docs/plans/ | head`
5. 직전 세션이 남긴 TODO 가 있다면 (PR 설명, plan 의 pending stub, journey 의 Known gaps) 우선 거기서 이어갑니다.

## 금지 사항

- 문서 본문에 코드 블록을 통째로 복사하지 않습니다 (금방 썩음). 파일/클래스명 포인터만.
- Journey 문서에 줄 번호를 박지 않습니다 (바로 썩음).
- Verification recipe 를 실행하지도 않은 채 `last-verified` 만 날짜를 올리지 않습니다.
- 드리프트를 무시하고 진행하지 않습니다. 발견 시 반드시 README Verification log 에 남기거나 수정.

## 이 규칙이 생긴 배경

2026-04-20 session 에서 16개 journey 를 문서화하고 첫 verification sweep 을 돌린 결과, 2건의 드리프트 (Silent count 불일치, Home 탭 복귀 회귀) 가 즉시 발견됐습니다. 문서가 **의도대로 잘 동작하고 있는지** 를 5분 안에 검증할 수 있는 실행 가능한 recipe 로 구성되어 있다면, 드리프트는 사용자 신고 대신 개발자가 먼저 잡습니다. 이 규칙은 그 사이클을 계속 돌리기 위한 최소 합의입니다.
