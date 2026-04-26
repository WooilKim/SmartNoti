---
status: in-progress
---

# Quiet hours 적용 사유를 사용자에게 명시적으로 설명 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 알림이 `조용한 시간` 분기로 인해 DIGEST 로 떨어졌을 때, 사용자가 Detail 화면 안에서 단순 chip ("조용한 시간") 외에 사람-언어로 된 한 줄 설명 — 예: "지금이 조용한 시간(23~7시)이라 자동으로 모아뒀어요" — 을 볼 수 있게 한다. 즉 "왜 이 알림이 정리함으로 갔는지" 를 chip 의미를 추론하지 않고도 즉시 이해할 수 있어야 한다. 현재는 `조용한 시간` reasonTag chip 만 노출되고, 그 chip 이 곧 "이 알림은 quiet-hours 분기로 DIGEST 됐다" 는 의미라는 것이 어디에도 명시되지 않아 사용자가 reasonTag 의 의미와 분류 결과의 인과를 직접 이어 붙여야 한다.

**Architecture:**
- 신규 pure 함수 `QuietHoursExplainerBuilder` 가 `(reasonTags: List<String>, status: NotificationStatusUi, settings: SmartNotiSettings)` 를 받아 `QuietHoursExplainer?` (nullable) 를 반환. `조용한 시간` tag + `status == DIGEST` 조합일 때만 non-null 메시지를 합성하고, 그 외 (다른 태그가 더 결정적이거나, status 가 PRIORITY 등) 는 null. 메시지는 settings 의 `quietHoursStartHour` / `quietHoursEndHour` 를 한국어 시간 표기로 보간.
- `NotificationDetailScreen` 의 "왜 이렇게 처리됐나요?" 카드 안 — 기존 `classifierSignals` chip row 바로 아래에 new sub-section "지금 적용된 정책" (또는 동등 카피) 으로 한 줄 `Text` 를 렌더. explainer 가 null 이면 sub-section 자체 hide.
- `SettingsRepository.observeSettings()` 는 이미 Detail 에서 다른 builder (예: `NotificationDetailDeliveryProfileSummaryBuilder`) 가 사용하는 패턴이 존재 — 그 wiring 을 모방.
- 기존 `NotificationDetailReasonSectionBuilder` 의 classifierSignals 필터 로직은 유지. `조용한 시간` chip 은 계속 노출되며 explainer 는 chip 옆이 아닌 별도 sub-section 으로 보완 (chip = "신호" / explainer = "결과 설명" 의 분리).

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`Text`, `Card` sub-section), JUnit unit tests (pure-function explainer).

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `quiet-hours` 의 두 번째 Known gap "Quiet hours 가 적용되었다는 것을 Detail 의 reasonTags 로만 확인 가능. UI 에서 'quiet hours 때문에 숨김' 같은 명시적 설명 부재." 를 해소.
- **결정 필요**: explainer 가 노출될 surface — 본 plan 은 **Detail 만** 에 추가하는 것을 채택. 이유: list row (`NotificationCard`) 는 이미 `ReasonChipRow` + body preview 로 텍스트 밀도가 높다. 같은 카피를 list 마다 풀어 쓰면 시각적 잡음. Detail 은 사용자가 의도적으로 깊이 보고자 하는 자리이므로 거기서 한 줄 더 보여주는 것이 자연스럽다. List 에도 풀어 쓰는 결정은 별도 plan.
- **결정 필요**: explainer 가 `status == DIGEST` 일 때만 노출 — 룰 매치나 키워드 등 더 우선순위 높은 분기가 결과를 결정한 경우엔 quiet-hours 가 reasonTag 로 남아있어도 실제 분류 사유가 아니다. classifier 의 분기 우선순위 (`NotificationClassifier.kt:90` 의 `if (input.packageName in shoppingPackages && input.quietHours) return DIGEST`) 와 일치시키기 위해, "DIGEST 이면서 `조용한 시간` 태그가 있는 케이스" 만으로 좁힌다. PRIORITY 알림에 quietHours 컨텍스트가 있더라도 explainer 는 노출되지 않음.
- **결정 필요**: 시간 표기 — `quietHoursStartHour=23` / `quietHoursEndHour=7` 일 때 카피는 "조용한 시간(23시~익일 7시)" 형태. same-day 범위 (예: 14~16) 일 때는 "조용한 시간(14시~16시)". `formatHour(23)` 등 헬퍼는 `SettingsOperationalSummaryBuilder.kt:24` 에 이미 존재하므로 재사용.
- **결정 필요**: 카피 단일안 — "지금이 조용한 시간(23시~익일 7시)이라 자동으로 모아뒀어요." 후속 검토에서 톤이 어색하면 PR 본문에 한 줄로 후보 카피 제시. 다국어는 본 plan scope 외.
- **결정 필요**: 같은 카드 안에 explainer 외 다른 정책-기반 설명도 있을 수 있는가 — 본 plan 은 quiet-hours 만 다룬다. duplicate-suppression / digest-suppression / persistent-protection 등 다른 분기는 별도 plan 으로 같은 패턴 (분기별 explainer builder + Detail sub-section) 으로 확장 가능. 현 plan 은 그 확장 가능성을 architecture 로만 열어두고 실제 분기는 quiet-hours 1건만 구현.
- 본 plan 은 classifier 동작 / reasonTag 부착 / DB schema / settings 모델은 변경하지 않는다 — 순수 view layer + 신규 pure builder 1개.
- 카피/접근성: `Text` 한 줄은 자동으로 TalkBack 으로 읽힘. 별도 contentDescription 불필요.

---

## Task 1: Failing tests for `QuietHoursExplainerBuilder` [IN PROGRESS via PR #338]

**Objective:** explainer 의 합성/필터 규칙을 unit test 로 고정. 테스트 표면이 가장 명확한 곳부터 짠다.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/domain/usecase/QuietHoursExplainerBuilderTest.kt`

**Steps:**
1. 다음 시나리오를 assertion 으로 작성:
   - `reasonTags = ["조용한 시간"]`, `status = DIGEST`, `settings.quietHoursStartHour = 23`, `quietHoursEndHour = 7` → non-null. message 가 `"23시"` + `"7시"` substring 을 포함하고 `"자동으로 모아뒀"` substring 포함.
   - `reasonTags = ["조용한 시간"]`, `status = DIGEST`, same-day window `start=14, end=16` → message 가 `"14시"` + `"16시"` 포함, `"익일"` 미포함.
   - `reasonTags = ["조용한 시간"]`, `status = PRIORITY` → null (status gate).
   - `reasonTags = ["발신자 있음"]`, `status = DIGEST` → null (tag 없음).
   - `reasonTags = []`, `status = DIGEST` → null.
   - `reasonTags = ["조용한 시간", "사용자 규칙"]`, `status = DIGEST` → null (사용자 규칙이 더 결정적이므로 quiet-hours 는 부수 신호로 간주). 이 분기를 추가할지 여부는 implementer 가 한 번 더 검토 — 단순화하고 싶으면 본 케이스는 explainer 노출도 허용하고 본문에 "다른 룰도 함께 적용됨" 정도의 부연 카피를 붙이는 안도 가능. PR 본문에 채택 안 명시.
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.QuietHoursExplainerBuilderTest"` → 모두 RED.

## Task 2: Implement `QuietHoursExplainerBuilder` [IN PROGRESS via PR #338]

**Objective:** Task 1 테스트 모두 GREEN.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/domain/usecase/QuietHoursExplainerBuilder.kt`

**Steps:**
1. 다음 형태로 작성:
   ```
   data class QuietHoursExplainer(val message: String)

   class QuietHoursExplainerBuilder {
       fun build(
           reasonTags: List<String>,
           status: NotificationStatusUi,
           startHour: Int,
           endHour: Int,
       ): QuietHoursExplainer? { ... }
   }
   ```
2. 로직:
   - tag 미포함이거나 status != DIGEST → null.
   - same-day 여부 판정: `startHour < endHour` ⇒ same-day, else overnight.
   - 시간 포맷: `"${startHour}시"` / `"${endHour}시"` (`SettingsOperationalSummaryBuilder.formatHour` 가 더 정교한 라벨을 만들면 그 헬퍼를 internal 로 노출해 재사용; 그게 부담이면 plan 안에서 단순 `"${h}시"` 만 사용).
   - 메시지 합성 (overnight): `"지금이 조용한 시간(${startHour}시~익일 ${endHour}시)이라 자동으로 모아뒀어요."`
   - 메시지 합성 (same-day): `"지금이 조용한 시간(${startHour}시~${endHour}시)이라 자동으로 모아뒀어요."`
3. signature 에 `Settings` 객체 전체를 받지 말고 두 Int 만 받는 편이 test fixture 가 단순해진다 — wiring 사이트에서 `settings.quietHoursStartHour` 두 번만 풀어 넘김.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.QuietHoursExplainerBuilderTest"` → 모두 GREEN.

## Task 3: Wire explainer into `NotificationDetailScreen`

**Objective:** Detail 의 "왜 이렇게 처리됐나요?" 카드 안에 explainer sub-section 이 적절한 케이스에 한 줄로 노출.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt`

**Steps:**
1. `SettingsRepository` 를 Detail 의 다른 repository 들과 같은 패턴으로 `remember(context)` 로 획득. `observeSettings()` 를 `collectAsState(initial = SmartNotiSettings.DEFAULT)` 로 구독 (Detail 의 다른 builder 가 사용하는 패턴 모방 — 정확한 default 객체 이름은 코드에서 확인 후 사용; 첫 emission 전 잠깐 explainer 가 null 로 보이는 것은 의도된 무해 동작).
2. `remember { QuietHoursExplainerBuilder() }` 추가.
3. reasonTag chip row 가 렌더되는 자리 (현 `sections.classifierSignals` block) 하단에 explainer sub-section 을 추가:
   ```
   val explainer = remember(notification.reasonTags, notification.status, settings) {
       quietHoursExplainerBuilder.build(
           reasonTags = notification.reasonTags,
           status = notification.status,
           startHour = settings.quietHoursStartHour,
           endHour = settings.quietHoursEndHour,
       )
   }
   if (explainer != null) {
       ReasonSubSection(
           title = "지금 적용된 정책",
           description = "사용자가 설정한 시간 정책에 따라 자동으로 분류됐어요",
       ) {
           Text(explainer.message, style = MaterialTheme.typography.bodyMedium)
       }
   }
   ```
   `ReasonSubSection` 헬퍼가 이미 같은 파일 안에 존재 (line ~578) — 그 시그니처 (`title`, `description`, `content`) 를 그대로 재사용. 부합하지 않으면 implementer 가 가장 가까운 인접 섹션 (예: `delivery profile` summary card) 패턴을 모방.
4. classifierSignals chip row 의 `조용한 시간` chip 자체는 그대로 둔다 — explainer 는 보강이지 대체가 아님.
5. `./gradlew :app:assembleDebug` 통과.

## Task 4: Manual ADB verification on emulator-5554

**Objective:** 두 흐름 (overnight default 23~7 / same-day temp 14~16) 에서 explainer 가 의도된 카케스에만 노출되는지 시각 확인.

**Steps:**
1. APK 빌드 후 설치: `./gradlew :app:installDebug`.
2. **Same-day 흐름 (검증 환경 통제)**:
   - Settings → "조용한 시간" → start/end 를 현재 시각을 포함하도록 조정 (예: 현재 14:30 KST → start=14, end=16).
   - shoppingPackages 에 포함된 앱이 없는 환경에서는 `cmd notification post` 로는 quiet-hours 분기를 발화시키기 어려움 — `com.coupang.mobile` 설치 또는 classifier 의 `shoppingPackages` 에 임시로 `com.android.shell` 을 추가하는 패치를 로컬에 두고 확인. 패치는 PR 에 포함하지 않음.
   - 대안: 기존 DB 에 `조용한 시간` reasonTag + `status=DIGEST` row 가 있는지 `run-as com.smartnoti.app sqlite3 databases/smartnoti.db "SELECT id FROM notifications WHERE reasonTags LIKE '%조용한%' AND status='DIGEST' LIMIT 1;"` 로 확인하고 그 row 의 Detail 을 직접 열어 검증.
3. Detail 에서 explainer sub-section 이 chip row 아래에 한 줄로 보이는지 screenshot/uiautomator dump 캡처. 카피가 `"23시~익일 7시"` 또는 `"14시~16시"` 형태인지 확인.
4. **반례 검증**: PRIORITY status row 또는 `조용한 시간` 태그 없는 row 의 Detail 을 열고 sub-section 이 **노출되지 않는** 것을 확인.
5. 결과를 `docs/journeys/quiet-hours.md` Change log + Verification log (README) 에 캡처와 함께 기록.

## Task 5: Update `quiet-hours` journey

**Objective:** Observable steps + Code pointers + Known gaps + Tests + Change log 동기화.

**Files:**
- `docs/journeys/quiet-hours.md`

**Steps:**
1. Observable steps 4 다음에 Detail explainer 노출 한 단계 추가 (의역 가능): "Detail 진입 시 `QuietHoursExplainerBuilder` 가 reasonTags + status + settings 로 explainer 를 합성, non-null 이면 reasonTag chip row 아래 '지금 적용된 정책' sub-section 이 한 줄 카피로 렌더 (예: '지금이 조용한 시간(23시~익일 7시)이라 자동으로 모아뒀어요.')."
2. Known gaps 의 두 번째 bullet ("Quiet hours 가 적용되었다는 것을 Detail 의 reasonTags 로만 확인 가능. UI 에서 'quiet hours 때문에 숨김' 같은 명시적 설명 부재.") 을 **(resolved YYYY-MM-DD, plan `2026-04-26-quiet-hours-explainer-copy`)** prefix 로 마킹 — `.claude/rules/docs-sync.md` 의 "Known gap 본문은 그대로, plan 링크만 annotate" 규칙을 따른다. 본문 텍스트 변경 금지.
3. Code pointers 에 `domain/usecase/QuietHoursExplainerBuilder` + Detail 의 explainer sub-section 위치 추가.
4. Tests 에 `QuietHoursExplainerBuilderTest` 항목 추가.
5. Change log 에 본 PR 항목 append (날짜는 implementer 가 작업한 실제 UTC 날짜 = `date -u +%Y-%m-%d`, 요약, plan link, PR link merge 시 채움).
6. `last-verified` 는 ADB recipe 를 처음부터 끝까지 다시 돌리지 않았다면 **갱신하지 않음**.

## Task 6: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 명시:
  1. Task 1 의 "사용자 규칙도 함께 매치된 케이스" 분기를 어떻게 처리했는지 (null vs 부연 카피).
  2. ADB 검증 결과 (positive/negative case 각 1건 screenshot or uiautomator dump 발췌).
  3. 시간 포맷 헬퍼 — `SettingsOperationalSummaryBuilder.formatHour` 를 재사용했는지 / 본 plan 에서만 쓰는 단순 `"${h}시"` 를 직접 작성했는지.
- PR 제목 후보: `feat(detail): explain quiet-hours classification with plain-language sub-section`.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/quiet-hours.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- 신규 pure `QuietHoursExplainerBuilder` (data class `QuietHoursExplainer` + builder).
- `NotificationDetailScreen` 의 reasonTag 카드 안 explainer sub-section 추가 (`ReasonSubSection` 헬퍼 재사용).
- `SettingsRepository.observeSettings()` 구독 wiring (Detail 의 기존 builder 패턴 모방).
- 신규 unit test.
- Journey 문서 동기화 (Observable / Code pointers / Known gaps annotate / Tests / Change log).

**Out:**
- list row (`NotificationCard`) 에 같은 카피를 풀어 노출 — 별도 plan.
- duplicate-suppression / digest-suppression / persistent-protection 등 다른 분기의 explainer — 본 plan 의 architecture pattern 을 따라가는 후속 plan.
- classifier 동작 / reasonTag 부착 / DB schema / settings 모델 변경.
- 다국어 / locale 별 시간 표기.
- 사용자가 카피를 직접 수정할 수 있는 in-app 옵션.

---

## Risks / open questions

- **카피 톤**: "지금이 조용한 시간(...)이라 자동으로 모아뒀어요." 가 다른 Detail 카피 (예: `DetailReclassifyConfirmationMessageBuilder`, `NotificationDetailDeliveryProfileSummaryBuilder`) 와 어조가 일관한지 implementer 가 grep 후 한 줄 확인. 어긋나면 PR 본문에 비교 표 첨부.
- **`사용자 규칙` + `조용한 시간` 동시 매치 케이스**: classifier 우선순위상 사용자 규칙이 이긴 경우라도 reasonTags 에 둘 다 남는다. 이때 explainer 를 보여주면 사용자가 "사용자 규칙 vs quiet-hours 중 무엇이 결정적이었나" 헷갈릴 수 있음 — 본 plan 은 default 로 null (숨김) 을 권장하나 implementer 가 단순화 안을 채택하면 PR 본문에 명시.
- **`SmartNotiSettings.DEFAULT` 가 존재하지 않으면**: Detail 진입 직후 settings flow 의 첫 emission 전에 explainer 가 잠깐 빈 상태일 수 있음. 다른 builder 가 동일 패턴으로 처리하는지 implementer 가 확인 — 처리하지 않는다면 본 plan 도 동일하게 null fallback (sub-section 미노출) 으로 둔다. 시각적으로 "한 박자 뒤 한 줄이 추가되는" 모양이지만 동일 카드 안의 다른 builder 도 같은 race 를 갖는 것으로 보이므로 일관성 유지.
- **shoppingPackages 하드코딩**: 본 plan 은 quiet-hours 분기가 발화하는 패키지 셋 (`setOf("com.coupang.mobile")`) 자체를 손대지 않는다 — 사용자 정의 가능 옵션은 `quiet-hours.md` 의 첫 Known gap (별도 plan).
- **Same-day vs overnight 경계**: `startHour == endHour` 케이스 (e.g., 둘 다 0) 는 의도가 모호 (24시간 quiet? 0시간 quiet?). `QuietHoursPolicy.isQuietAt` 의 동작 (`hourOfDay in startHour until endHour` 는 빈 range 가 되어 false 반환) 과 동일한 정의를 explainer 도 따른다 — 즉 explainer 도 null (실제 분기가 발화하지 않으므로 reasonTag 자체가 없음).
- **테스트 fragility**: `quietHoursExplainer` 의 카피가 향후 시간 표기 헬퍼 변경으로 깨지면 unit test 가 substring 매치 (`"23시"`, `"7시"`, `"자동으로 모아뒀"`) 로만 검증하므로 wording 바뀜에 비교적 robust. 시간 포맷이 통째로 바뀌면 (예: `"오후 11시"`) test 도 같이 갱신 필요.

---

## Related journey

- [quiet-hours](../journeys/quiet-hours.md) — Known gaps 두 번째 bullet ("UI 에서 'quiet hours 때문에 숨김' 같은 명시적 설명 부재") 을 해소. 본 PR ship 후 해당 bullet 을 (resolved …) 로 마킹하고 Change log 에 PR 링크 추가. [notification-detail](../journeys/notification-detail.md) Observable step 4 의 "왜 이렇게 처리됐나요?" 카드 sub-section 구성에도 한 줄 기재 (cross-link).
