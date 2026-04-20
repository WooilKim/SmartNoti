# SmartNoti User Journeys

이 디렉토리는 **현재 구현된 사용자 여정의 contract**를 기록합니다. 각 문서는 실제로 앱이 하고 있는 일을 관측 가능한 단계로 서술해, 드리프트 감지와 신규 기능 설계의 근거로 쓰입니다.

## `docs/plans/` 와의 차이

- `docs/plans/` — 앞으로 할 일 (구현 계획, 의사결정)
- `docs/journeys/` — **지금 일어나고 있는 일** (현재 동작의 계약)

새 기능이 shipped 되면 plan은 완료 상태로 두고 journey 문서를 작성/갱신합니다.

## 규칙

- 사용자 관측 동작이 바뀌는 PR은 관련 journey 문서도 수정하거나, 새 journey 추가, 혹은 기존을 `deprecated`
- Frontmatter `last-verified`는 실제로 Verification recipe를 실행한 뒤에만 갱신
- 파일당 200줄 이하 유지. 길어지면 out-of-scope로 쪼갠다
- 코드를 본문에 복사하지 않는다 — 경로/클래스명 포인터만
- "관측 가능한 단계"만 쓴다. "시스템이 분류한다" (X) → "`NotificationEntity` row가 `status=SILENT` 로 저장된다" (O)

## Active journeys

| ID | Title | Status | Last verified |
|---|---|---|---|
| [notification-capture-classify](notification-capture-classify.md) | 알림 캡처 및 분류 | shipped | 2026-04-20 |
| [onboarding-bootstrap](onboarding-bootstrap.md) | 첫 온보딩 및 기존 알림 부트스트랩 | shipped | 2026-04-20 |
| [priority-inbox](priority-inbox.md) | 중요 알림 인박스 | shipped | 2026-04-20 |
| [silent-auto-hide](silent-auto-hide.md) | 조용히 분류된 알림 자동 숨김 | shipped | 2026-04-20 |
| [protected-source-notifications](protected-source-notifications.md) | 미디어/통화/포그라운드 서비스 보호 | shipped | 2026-04-20 |

## Pending stubs (shipped in code, journey 문서 미작성)

후속 PR에서 채울 예정. 당장은 기존 문서의 out-of-scope 링크가 이 항목들을 향합니다.

| ID | Title | Priority to document |
|---|---|---|
| digest-suppression | 디제스트 자동 묶음 및 원본 교체 | high |
| hidden-inbox | 숨긴 알림 인박스 (Hidden 화면) | high |
| digest-inbox | 정리함 인박스 | medium |
| home-overview | 홈 요약 + 인사이트 | medium |
| notification-detail | 알림 상세 + 피드백 액션 | medium |
| rules-management | 규칙 CRUD | medium |
| rules-feedback-loop | 알림 피드백 → 룰 저장 | medium |
| insight-drilldown | 인사이트 드릴다운 | low |
| quiet-hours | 조용한 시간 | low |
| persistent-notification-protection | 지속 알림 키워드 기반 보호 | low |
| duplicate-suppression | 중복 알림 감지/묶음 | low |

## Deprecated

(없음)

## Journey 문서 작성 가이드

지금 있는 5개 파일을 예시로 사용. 기본 섹션:

1. **Frontmatter** — `id`, `title`, `status` (planned/in-progress/shipped/deprecated), `owner`, `last-verified`
2. **Goal** — 제품 의도 한두 문장. 바뀌면 사실상 새 journey
3. **Preconditions** — 이 journey가 시작되려면 성립해야 할 상태
4. **Trigger** — 무엇이 이 journey를 시작시키는가
5. **Observable steps** — 검증 가능한 문장만. 각 단계는 사람/ADB가 확인 가능해야 함
6. **Exit state** — 성공했을 때 어떤 관측 가능한 상태가 되는가
7. **Out of scope** — 이 journey가 책임지지 **않는** 일 (비대화 방지). 인접 journey로 링크
8. **Code pointers** — 파일/클래스명 (줄 번호는 금방 썩으니 최소화)
9. **Tests** — 관련 유닛/UI 테스트 클래스
10. **Verification recipe** — ADB 명령이나 수동 체크리스트로 journey가 여전히 작동하는지 5분 안에 확인 가능
11. **Known gaps** — 의도적 한계나 미해결 엣지 케이스
12. **Change log** — 연/월/일 + 짧은 설명 + PR/커밋 해시
