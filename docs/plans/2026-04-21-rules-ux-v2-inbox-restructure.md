---
id: rules-ux-v2-inbox-restructure
title: Rules UX v2 + Inbox restructure — hierarchical rules, reason-tag traceability, passthrough tab removal
status: planned
owner: @wooilkim
journey: priority-inbox, rules-management, notification-detail, home-overview
created: 2026-04-21
---

## Motivation

세 개의 독립적으로 나온 사용자 피드백이 같은 근본 문제 — "SmartNoti 의 결정을 사용자가 이해하고 조정하기 어렵다" — 를 가리킨다. 한 플랜으로 묶어서 v2 의 일관된 UX 를 설계한다.

### 피드백 1: 즉시 전달 탭은 사실상 "no-op" 인데 독립 탭으로 존재한다

현재 네 가지 분류의 개입 수준이 완전히 다름:

| 분류 | OS tray | SmartNoti 동작 | 개입 |
|---|---|---|---|
| 즉시 전달 (PRIORITY) | 원본 그대로 유지 | DB row 저장만 | **개입 안 함** |
| 정리함 (DIGEST) | 원본 cancel + 묶음 replacement | 적극 개입 | ✅ |
| 조용히 (ARCHIVED) | 낮은 importance 로 유지, 그룹 summary | 적극 개입 | ✅ |
| 조용히 (PROCESSED) | 원본 cancel | 이력 저장 | ✅ |

즉시 전달 탭은 **"SmartNoti 가 아무것도 하지 않은 알림 리스트"**. OS 가 이미 동일하게 보여주고 있으므로 탭으로서의 고유 가치가 약함. 진짜 가치는 "이 판단이 맞나 검토" 피드백 루프인데, 현재 framing ("중요한 알림") 은 그걸 감춤.

### 피드백 2: "발신자 있음" reasonTag 가 근거로 작용하는데 규칙 탭에서 찾을 수 없다

현재 reasonTag 는 두 종류가 구분 없이 섞여 있음:
- **Classifier signals** (내부 factoid) — "발신자 있음", "조용한 시간", "반복 알림"
- **Rule hits** (실제 규칙 발동 근거) — "사용자 규칙", "중요한 사람", "중요 키워드"

UI 는 둘 다 동일한 chip 으로 렌더. 사용자가 "발신자 있음" 을 근거로 읽고 규칙 탭에서 수정/비활성화하려 하지만, 그건 수정할 수 있는 것이 아니라 분류기 내부 metadata. 투명성 실패.

### 피드백 3: 규칙이 flat first-match-wins 라 계층적 조건을 못 만든다

사용자 예: "결제 키워드 → PRIORITY" 가 기본이지만, "결제 AND 광고" 일 때는 SILENT 로 가고 싶음. 현재 모델로는 불가. 규칙끼리 충돌 시 우선순위도 불명확.

## Goal

- **Phase A (Inbox restructure)**: 즉시 전달 탭 제거. Home 에 "SmartNoti 가 건드리지 않은 알림 N건 — 검토" 카드. 탭 2개만 남김 (정리함 + 숨김). 사용자가 passthrough 알림을 빠르게 재분류하거나 규칙으로 만들 수 있는 액션 강화.
- **Phase B (Reason-tag traceability)**: Detail 의 reasonTag 를 두 섹션으로 분리 — "SmartNoti 가 본 신호" (classifier factoids, 편집 불가) + "적용된 규칙" (user rules, 해당 규칙으로 딥링크). 사용자가 "이 chip 은 수정 가능한 건가?" 를 즉시 판단 가능하게.
- **Phase C (Hierarchical rules v2)**: **override rule** 개념 도입. Rule A 밑에 Rule B 를 "A 의 예외" 로 tier. 둘 다 매치하면 override 가 우선. 규칙 탭에서 visual indent / branch 로 계층 표현. 동일 tier 에서 충돌 시 드래그 재배치로 우선순위 지정.

세 phase 는 독립 merge 가능 (순서 바뀌어도 됨). 작은 PR 로 쪼개서 plan-implementer 가 task-by-task shipping.

## Non-goals

- 분류기 알고리즘 자체 재작성 — 여전히 rule → VIP → keyword → quiet+shopping → dup → SILENT 순서.
- Regex / full expression language — override 만 지원. 더 복잡한 불리언 식은 후속 plan.
- 탭의 애니메이션/전환 세부 디자인 — 기본 Material 3 동작.
- 데이터 마이그레이션 — Phase A/B 는 스키마 변경 없음. Phase C 는 rule 에 `overrideOf: String?` 필드 추가 (nullable → 기존 룰 영향 없음).

## Scope

### 건드리는 파일 (Phase 별)

**Phase A (passthrough 탭 제거):**
- `ui/screens/priority/PriorityInboxScreen.kt` — 전체 삭제 혹은 Home 임베드용으로 리팩토링
- `navigation/Routes.kt` — `Routes.Priority` 삭제 (back-compat: deep-link 는 Home 으로 redirect)
- `navigation/AppNavHost.kt` — Priority composable 제거, BottomNav 항목 삭제
- `ui/screens/home/HomeScreen.kt` — 새 "검토 대기" 카드 + 탭 액션
- `ui/components/HomePassthroughReviewCard.kt` (신규) — 카드 컴포넌트
- `docs/journeys/priority-inbox.md` — deprecated 전환 + Change log
- `docs/journeys/home-overview.md` — Observable steps 에 새 카드 추가

**Phase B (reason-tag 분리):**
- `ui/screens/detail/NotificationDetailScreen.kt` — "왜 이렇게 처리됐나요?" 섹션을 두 개로 분리
- `domain/model/NotificationUiModel.kt` — `reasonTags` 를 두 리스트로 split (`classifierSignals: List<String>`, `ruleHits: List<RuleReference>`) 또는 metadata flag 추가
- `domain/usecase/NotificationClassifier.kt` — 분류 결과에 어떤 규칙이 hit 했는지 정보를 반환
- `data/local/NotificationEntity.kt` — `ruleHitIds: String?` (comma-separated rule IDs) 컬럼 추가 + 마이그레이션
- `data/local/NotificationRepository.kt` — upsert 시 rule hit 저장
- `docs/journeys/notification-detail.md` — Observable steps 갱신

**Phase C (hierarchical rules):**
- `domain/model/RuleUiModel.kt` — `overrideOf: String?` 필드 추가 (다른 규칙의 id 참조)
- `data/rules/RulesRepository.kt` — override chain 저장/로드, circular reference 방지
- `domain/usecase/NotificationClassifier.kt#findMatchingRule` — override 가 있을 때 base rule 대신 override 액션 적용. 여러 override 매치 시 가장 specific (더 많은 조건) 우선, tie-break 은 사용자 지정 priority.
- `ui/screens/rules/RulesScreen.kt` — override 계층 시각화 (indent + connecting line). 드래그-앤드-드롭 priority 재배치.
- `ui/screens/rules/RuleEditorDialog.kt` — 새 규칙 생성 시 "기존 규칙의 예외로 만들기" 옵션
- `ui/screens/rules/RuleListPresentationBuilder.kt` — flat → tree 변환 로직
- `domain/usecase/RuleConflictResolver.kt` (신규) — 동일 tier 충돌 시 우선순위 결정, circular override 감지
- `docs/journeys/rules-management.md` — Observable steps 재작성
- `docs/journeys/notification-capture-classify.md` — 분류 순서에서 override 처리 단계 추가

### 건드리지 않는 파일

- `NotificationRoutingPolicy` — 분류 결과에만 반응, 분류 자체에는 관여 안 함.
- DIGEST / SILENT 경로 전체 — 이 plan 은 분류 UI 와 rule 시스템만 건드림.
- Hidden 탭, Digest 탭 — 그대로 유지.
- Rules-feedback-loop (sender/package 자동 룰 생성) — 그대로 유지. 생성된 룰은 기본 tier 로 들어감.

## Phase A — Tasks (Inbox restructure)

1. **Home passthrough 카드 컴포넌트 (tests-first)** [shipped via #140]
   - `HomePassthroughReviewCard(count, onReviewClick)` 신규 composable.
   - Preview + 단위 테스트 (count=0 empty state, count>0 active state).
2. **Home 에 카드 마운트** [shipped via #141]
   - `HomeScreen` 에서 `NotificationRepository.observeAll()` 로 PRIORITY 개수 집계 → 카드에 전달.
   - "검토하기" 탭 → `Routes.Priority` 혹은 신규 `Routes.PriorityReview` 열기 (Phase A 중반부).
3. **Priority 탭을 review 화면으로 리팩토링** [shipped via #142]
   - `PriorityInboxScreen` 이 "중요 알림" 대신 "SmartNoti 가 건드리지 않은 알림" framing 으로 변경. 탭 자체는 BottomNav 에서 제거 (아래 task 4), 접근은 Home 카드 tap 으로만.
   - 카드마다 inline 재분류 액션 (→ Digest / → 조용히 / → 규칙 만들기) 표시.
4. **BottomNav 에서 Priority 탭 제거** [shipped via #143]
   - `AppNavHost` 의 BottomNav 3 탭 (정리함, 숨김, 규칙, 설정) 유지, Priority 제거.
   - 기존 deep-link `smartnoti://priority` 는 Home + PriorityReview 경유로 redirect.
   - 실제 적용: `bottomNavItems` 에서 "중요" 엔트리 삭제 (4개 탭: 홈 / 정리함 / 규칙 / 설정). `Routes.Priority` 는 `AppNavHost` 의 `composable` 로 그대로 등록되어 Home 카드 탭 (`onPriorityClick`) 및 PRIORITY replacement 알림 parent route 로 계속 사용. 외부 `smartnoti://` URL 딥링크는 아직 Manifest 에 등록된 적이 없으므로 별도 redirect 코드는 불필요.
5. **Journey 문서 갱신** [IN PROGRESS]
   - `priority-inbox.md` → Home card tap 으로 여전히 진입 가능하므로 `deprecated` 가 아니라 "검토용" framing 으로 재작성 (status: shipped 유지). Observable steps 를 새 title/subtitle + 인라인 재분류 액션 + "Priority 탭 아님" 을 반영하도록 갱신, Change log 에 Phase A 마일스톤 추가.
   - `home-overview.md` Observable steps 에 `HomePassthroughReviewCard` 추가 + Change log.
   - `last-verified` 는 bump 하지 않음 (실제 recipe 를 재실행한 것은 아니므로, per docs-sync.md).

## Phase B — Tasks (Reason-tag traceability)

1. **Room 스키마 확장 (tests-first)** [shipped via #145]
   - `NotificationEntity` 에 `ruleHitIds: String?` 추가. SCHEMA_VERSION 7→8. Nullable 이라 기존 row 영향 없음.
   - `NotificationRepositoryTest` 에서 round-trip 확인.
2. **Classifier 결과 확장** [IN PROGRESS via PR #146]
   - `NotificationDecision` 에 `matchedRuleIds: List<String>` 필드 추가 (이전엔 단순 enum).
   - `classify()` 가 rule hit 시 그 rule id 를 함께 반환.
   - 기존 classifier signal 은 별도 `reasonSignals: List<String>` 로 유지.
   - 실제 구현: 새 wrapper `NotificationClassification(decision, matchedRuleIds)` 를 도입하고 `NotificationClassifier.classify()` 가 그것을 반환. `NotificationDecision` 은 enum 그대로 유지 — 라우팅/상태 전이는 영향 없음. `NotificationCaptureProcessor` 가 wrapper 를 풀어 decision 을 delivery-profile 로 전달하고 `matchedRuleIds` 를 `NotificationUiModel` 로 threading. `NotificationEntityMapper` 가 `matchedRuleIds` 를 Phase B Task 1 의 `ruleHitIds` 컬럼으로 comma-separated 로 persist. 기존 free-form `reasonTags` 는 그대로 유지 (Task 3 에서 Detail UI 가 두 섹션으로 분리할 때 소비).
3. **Detail UI 분리**
   - "왜 이렇게 처리됐나요?" 섹션 아래에 두 서브섹션:
     - **SmartNoti 가 본 신호** — classifier signals (기존 회색 chip, 클릭 불가)
     - **적용된 규칙** — rule hits (파란색 chip, 클릭 시 Rules 탭 → 해당 규칙 하이라이트)
   - 어느 하나가 비어 있으면 섹션 자체 hide.
4. **Rules 탭 딥링크**
   - `Routes.Rules` 에 `highlightRuleId: String?` 쿼리 파람 추가.
   - `RulesScreen` 은 해당 rule 을 자동 스크롤 + 깜빡임 애니메이션.

## Phase C — Tasks (Hierarchical rules)

1. **Data model 확장 (tests-first)**
   - `RuleUiModel.overrideOf: String?` 필드.
   - `RulesRepository` upsert 시 circular reference 감지 (A → B → A 는 reject, 에러 로그).
   - `RuleConflictResolverTest` 신규: 동일 tier 충돌 시 priority 필드 (또는 rule 순서) 기준 선택 테스트.
2. **Classifier override 처리**
   - `findMatchingRule` 재작성: flat loop 대신 tier-aware traversal.
   - 매치 시: base rule + override candidates 모두 수집 → 가장 specific override (더 많은 조건 매치) → tie 는 priority → base 로 fallback.
   - 테스트: 사용자 예 (`결제 → PRIORITY`, `결제+광고 → SILENT`) 재현.
3. **Rules 탭 UI — 계층 시각화**
   - `RuleListPresentationBuilder` 가 flat list 를 tree 로 변환 (base rule + nested overrides).
   - `RuleRow` 렌더 시 indent + "이 규칙의 예외" 라벨.
   - Override 가 깨진 상태 (base 삭제됨 등) 의 visual warning.
4. **Rule editor dialog — override 만들기**
   - 규칙 편집 AlertDialog 에 "기존 규칙의 예외로 만들기" 스위치 + "어느 규칙의 예외인가요?" dropdown.
   - 선택 시 매치 조건은 base 의 superset 이어야 함 (validator 가 경고).
5. **Drag-to-reorder priority**
   - 동일 tier 의 rule 들을 드래그로 재배치. `RulesRepository.moveRule` 은 이미 존재, extend to override-aware.
6. **Journey 문서 갱신**
   - `rules-management.md` Observable steps 에 override UX 추가. Change log.
   - `notification-capture-classify.md` Observable steps 의 분류 순서에 override 단계 추가.

## Verification recipe (Phase C 기준, 나머지는 Phase 내 task 마다)

```bash
# 1. 기본 룰 생성: 결제 → ALWAYS_PRIORITY
# 2. Override 룰 생성: 결제 AND 광고 → SILENT
# 3. 알림 1: "결제 완료 안내" → Priority 로 분류 (base rule hit)
adb -s emulator-5554 shell cmd notification post -S bigtext -t Bank N1 "결제 완료 안내"
# 4. 알림 2: "결제 프로모션 광고" → Silent 로 분류 (override hit)
adb -s emulator-5554 shell cmd notification post -S bigtext -t Promo N2 "결제 프로모션 광고 쿠폰"
# 5. Detail 열기 → reasonTag 섹션이 "SmartNoti 가 본 신호" + "적용된 규칙" 으로 분리됨 확인
# 6. 적용된 규칙 chip 탭 → Rules 탭에서 해당 룰 하이라이트
# 7. 알림 3: 결제 키워드 없이 "안녕하세요" → Silent default (no rule hit) — Home 카드에서 N+1 반영
```

## Open questions

1. **Priority 탭 완전 제거 vs deprecate**: v1 호환성을 위해 탭은 남기고 deep link 만 Home 경유로? 아니면 clean remove?
   - 권고: **clean remove**. SmartNoti 는 아직 사용자 베이스 작고 v1 롤백은 git revert 로 충분.

2. **Override 의 "specific" 판단 기준**: "더 많은 조건 매치" 는 어떻게 정량화하나? 매치한 토큰 수? rule 길이?
   - 권고: 매치한 distinct condition 수 (KEYWORD 는 1, KEYWORD+SCHEDULE 은 2). 시작은 단순하게, heuristic 로 발전.

3. **Override chain 깊이 제한**: A → B → C 까지 가능? 얼마나 deep?
   - 권고: 처음엔 **1-level (base + one override)** 만 허용. 사용자 요청 생기면 확장.

4. **기존 "중요한 사람" / "중요 키워드" reasonTag 의 운명**: 이 chip 은 rule hit 기반이므로 Phase B 에서 ruleHits 섹션으로 이동. 호환성 훅 필요?
   - 권고: 자동 migration. 이전 tag 는 새 UI 의 ruleHits 에 동등하게 매핑.

## Relationships

- Source journeys:
  - [priority-inbox](../journeys/priority-inbox.md) — Phase A 가 deprecated 로 전환
  - [home-overview](../journeys/home-overview.md) — Phase A 가 새 카드 추가
  - [notification-detail](../journeys/notification-detail.md) — Phase B 가 reasonTag 섹션 재구성
  - [rules-management](../journeys/rules-management.md) — Phase C 가 계층 UI 추가
  - [notification-capture-classify](../journeys/notification-capture-classify.md) — Phase C 가 분류 순서에 override 단계 추가
- 선행: 없음. 이 plan 은 v1 완성된 feature 들 위에서 재구조.
- 후행 plan 후보:
  - Rule 기반 automation / scheduled reviews (기본 구조가 확장 가능해야)
  - Override chain N-level 허용 (현재는 1-level 제한)
  - Cross-status 그룹핑 (현재는 SILENT only — tray-sender-grouping 의 후속)

## Risks

1. **Priority 탭 제거로 인한 사용자 혼선**: "중요 알림 어디 갔나요?" 초기 피드백 가능. 완화: 첫 실행시 Home 카드에 "중요 알림은 이제 여기에 표시돼요" 온보딩 배지.
2. **Override 의 specific 판정이 직관과 다를 수 있음**: 룰이 복잡해지면 사용자가 "이 예외가 왜 base 대신 적용됐지?" 헷갈림. 완화: Detail 의 "적용된 규칙" chip 에 "base + override" 를 함께 표기, 호버/탭 시 "이 룰이 이 기본 룰 대신 적용됐어요" 설명.
3. **스키마 마이그레이션 (Phase B Room 7→8)**: `ruleHitIds` 는 nullable 이라 기존 row 영향 없음. destructive fallback 이미 세팅되어 있음.
4. **Phase 간 순서 독립성**: Phase B 가 먼저 머지돼도 Phase A/C 진행 가능. Phase A 가 먼저 머지돼도 Phase B/C 진행 가능. Phase 내 task 순서는 유지.

## Decision log

- 2026-04-21: 사용자 피드백 3건 (Option C, 발신자 있음 trace, hierarchical rules) 을 통합 plan 으로 묶음. 세 phase 독립 merge.
