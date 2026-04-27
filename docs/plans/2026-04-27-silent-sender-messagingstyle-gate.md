---
status: shipped
shipped: 2026-04-27
superseded-by: docs/journeys/silent-auto-hide.md
---

# SILENT 그룹핑 Sender 키 — MessagingStyle 힌트 게이트

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** SILENT 로 분류된 알림이 트레이의 sender-단위 그룹으로 묶일 때, **메시지/대화형 알림이 아닌 앱**(쇼핑·뉴스·시스템·프로모션 등)이 자기 상품명·기사 제목을 `EXTRA_TITLE` 로 흘리는 경우, 그 title 이 그룹 키 (`SilentGroupKey.Sender`) 로 잘못 채택되어 동일 앱의 서로 다른 알림이 인위적으로 분리되거나 (`상품A` 그룹 1건 + `상품B` 그룹 1건 → 둘 다 N≥2 미달로 그룹 미게시) 의도하지 않은 묶임 (같은 카피를 쓰는 알림이 한 그룹) 이 발생하는 회귀를 막는다. 사용자 관측 결과: 메시지 앱이 아닌 출처는 SILENT 그룹 요약이 패키지명 (App 키) 로 묶여 "쇼핑앱 · 조용히 N건" 처럼 자연스럽게 보인다. 메시지 앱 (카톡/문자/Telegram 등 MessagingStyle 사용) 은 기존처럼 사람 이름으로 묶인다.

**Architecture:** 현재 `SmartNotiNotificationListenerService.processNotification` (라인 219-220) 이 `EXTRA_CONVERSATION_TITLE → EXTRA_TITLE` fallback 으로 무조건 `sender` 를 채우고, 그 값을 `NotificationCaptureProcessor` 가 그대로 `NotificationEntity.sender` 로 영속화한다. `SilentNotificationGroupingPolicy` 는 row 의 `sender` 가 non-blank 면 `Sender` 키를 쓰고, blank 면 `App(packageName)` fallback 을 쓴다. 즉 fallback 분기는 이미 존재하지만, listener 가 항상 title 을 sender 로 흘리기 때문에 발화하지 않는다 — 이것이 정확한 root cause. 본 plan 은 listener 단에서 **MessagingStyle 힌트가 확인될 때만 `EXTRA_TITLE` fallback 을 허용**하도록 게이트한다. 새 helper `MessagingStyleSenderResolver` (순수 함수, `Bundle` extras → `String?`) 를 도입해 (a) `EXTRA_CONVERSATION_TITLE` 우선, (b) MessagingStyle 신호 (`EXTRA_MESSAGES` 또는 `EXTRA_MESSAGES_HISTORIC` non-empty, 또는 `EXTRA_TEMPLATE = "android.app.Notification$MessagingStyle"`) 가 있을 때 `EXTRA_TITLE` fallback, (c) 그 외에는 null 반환. `SilentNotificationGroupingPolicy` 는 무변경 (이미 null/blank → App fallback). `NotificationCaptureProcessor`, `NotificationEntity` 시그니처 무변경.

**Tech Stack:** Kotlin, Android Notification API (`Notification.EXTRA_*` 상수), JUnit (순수 함수 헤드리스 테스트), 기존 listener 진입점.

---

## Product intent / assumptions

- **계약**: `NotificationEntity.sender` 는 "이 알림이 사람 발신자 의미를 갖는가" 의 신호다. messaging app 이 아닌 앱이 자기 광고 카피를 title 로 보내는 건 발신자가 아니므로 `sender = null` 이 맞다.
- 본 plan 은 **SILENT 트레이 그룹핑** 에 직접 영향. 그러나 `sender` 는 `NotificationClassifier` 의 PERSON 매칭 / rules feedback / Detail 화면 표시에도 쓰임 — 단순 PERSON rule 매칭 (예: `엄마`) 은 이미 messaging app 의 conversation title 또는 EXTRA_TITLE 일 때만 의미가 있으므로 본 변경이 기존 PERSON 룰 매칭을 약화시키지 않는다 (오히려 정확도 상승: 쇼핑앱이 `엄마` 라는 상품명을 보내도 PERSON 매치가 잘못 발화하던 케이스 제거).
- **NotificationCompat.MessagingStyle 호환**: 일부 앱은 AndroidX `NotificationCompat` 으로 게시해 `EXTRA_TEMPLATE` 값이 `androidx.core.app.NotificationCompat$MessagingStyle` 로 다를 수 있음. resolver 는 두 패턴 모두 인식한다.
- **기존 row 마이그레이션 없음**: 이미 DB 에 `sender = "상품명"` 으로 저장된 SILENT row 는 그대로 둔다. 새 알림부터 정확한 sender 를 받기 시작하고, 시간이 흐르며 잘못 묶인 그룹은 자연 사라진다 (SILENT 의 휘발성 + 신규 row 가 정상 키로 저장되며 서서히 cohort 가 교체). 마이그레이션 작성 비용 대비 이득이 작음.
- **PRIORITY/DIGEST 영향 없음**: 본 변경은 listener 의 sender 추출 한 줄을 게이트할 뿐, 각 status 의 분류·라우팅 로직을 건드리지 않음. 그룹핑 정책은 SILENT 만 사용 — 변경 효과는 SILENT 트레이 그룹 키 정확성에 한정.

### 사용자 판단이 필요한 결정 (Risks / open questions 으로 옮김)

- **resolver 의 false-negative 임계** — Custom layout / non-standard template 을 쓰는 메신저 앱이 있다면 `EXTRA_TEMPLATE` / `EXTRA_MESSAGES` 둘 다 비어 있어 sender 가 null 로 떨어질 수 있다. 발견되는 대로 resolver 의 수용 셋을 확장한다 (Known gap 으로 등록 예정). 본 plan ship 시점에는 보수적으로 표준 `MessagingStyle` 만 허용.
- **PERSON 룰 회귀 위험** — `엄마` 등 사람 이름으로 PERSON 룰을 만든 사용자가, 해당 sender 를 messaging style 로 보내지 않는 앱(예: 캘린더가 알림 title 에 "엄마 생일") 으로 받던 매칭이 본 변경 후 sender=null 로 떨어져 더 이상 매치하지 않을 수 있다. 의도된 정확도 향상이지만 기대치 변경이 있음 — Risks 에 명시하고 Change log 에서 짧게 안내.

---

## Task 1: Add failing unit tests for `MessagingStyleSenderResolver` [SHIPPED]

**Objective:** sender 추출의 새 계약 (MessagingStyle 게이트) 을 RED-first 로 고정. listener 본체는 Robolectric 의존이 강해 단위 테스트가 어려우므로, 추출 로직을 순수 함수 helper 로 분리해 헤드리스 JUnit 으로 커버.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/notification/MessagingStyleSenderResolverTest.kt`

**Steps:**
1. `MessagingStyleSenderResolver.resolve(extras: Bundle): String?` 시그니처를 가정한 테스트 클래스 작성. `Bundle` 은 Robolectric 없이 JVM 단위 테스트에서 직접 인스턴스화 안 되므로, 테스트 보조용으로 input 을 가벼운 `Map<String, Any?>` 또는 별도 wrapper (`MessagingStyleSenderInput`) 로 받는 시그니처를 추천 — Task 2 의 prod 코드는 listener 가 `Bundle` 을 wrapper 로 정규화해 helper 호출하도록 한다.
2. 다음 케이스 각각에 한 testFun 작성:
   - `EXTRA_CONVERSATION_TITLE = "엄마"` → `"엄마"`.
   - `EXTRA_TITLE = "엄마"` + `EXTRA_TEMPLATE = "android.app.Notification\$MessagingStyle"` → `"엄마"`.
   - `EXTRA_TITLE = "엄마"` + `EXTRA_TEMPLATE = "androidx.core.app.NotificationCompat\$MessagingStyle"` → `"엄마"`.
   - `EXTRA_TITLE = "엄마"` + `EXTRA_MESSAGES = arrayOf(...)` (length ≥ 1) → `"엄마"`.
   - `EXTRA_TITLE = "오늘만 30% 할인"` (template 없음, messages 없음) → `null`.
   - `EXTRA_TITLE = ""` + template messaging → `null` (blank guard).
   - `EXTRA_CONVERSATION_TITLE` 우선순위 검증: `EXTRA_CONVERSATION_TITLE = "팀장"` + `EXTRA_TITLE = "공지"` (template messaging 있음) → `"팀장"`.
   - 둘 다 비어 있음 → `null`.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.MessagingStyleSenderResolverTest"` → 컴파일 실패 (helper 부재) 로 RED.

## Task 2: Implement `MessagingStyleSenderResolver` and rewire listener [SHIPPED]

**Objective:** Task 1 테스트가 GREEN 이 되도록 helper 구현 + listener 의 sender 추출 한 줄을 helper 호출로 교체.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/notification/MessagingStyleSenderResolver.kt`
- 수정: `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` (라인 219-220 의 sender 추출 부분만)

**Steps:**
1. helper 신규 파일 작성:
   - `object MessagingStyleSenderResolver` (순수 함수, 상태 없음).
   - 시그니처: `fun resolve(input: MessagingStyleSenderInput): String?` + `data class MessagingStyleSenderInput(val conversationTitle: String?, val title: String?, val template: String?, val hasMessages: Boolean)`.
   - 본문 로직:
     - `conversationTitle?.takeIf { it.isNotBlank() }?.let { return it }`
     - `val isMessagingStyle = template == "android.app.Notification\$MessagingStyle" || template == "androidx.core.app.NotificationCompat\$MessagingStyle" || hasMessages`
     - `if (isMessagingStyle) title?.takeIf { it.isNotBlank() } else null`
2. listener 수정 (`processNotification`, 라인 215-220 근방):
   - 기존:
     ```
     val sender = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString()
         ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
     ```
   - 신규:
     ```
     val template = extras.getString(android.app.Notification.EXTRA_TEMPLATE)
     val messages = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
     val sender = MessagingStyleSenderResolver.resolve(
         MessagingStyleSenderInput(
             conversationTitle = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
             title = title,  // 이미 위에서 추출됨
             template = template,
             hasMessages = (messages?.isNotEmpty() == true),
         )
     )
     ```
   - `processNotification` 의 다른 sender 사용처 (`sender = sender,` 이후 라인 286 등) 는 이름 그대로 — 동일 변수 재사용.
3. `notifyReplacementNotification(...)` (라인 502-504 근방) 등 동일 추출 패턴이 있는 다른 진입점도 같은 helper 로 정리. (단, scope 가 PRIORITY/DIGEST replacement 알림 게시 경로일 수 있어 sender 가 더 이상 그룹 키로 안 쓰일 수 있다 — 그 경우는 본 plan 에서 무변경으로 두고 SILENT 캡처 경로만 갱신, 실패 회귀 방지가 우선.)
4. `./gradlew :app:testDebugUnitTest` 전체 초록 확인 (Task 1 테스트 GREEN + 기존 NotificationCaptureProcessorTest / SilentNotificationGroupingPolicyTest 등 회귀 없음).

## Task 3: ADB smoke + journey doc sync + ship [SHIPPED]

**Objective:** 실 디바이스에서 (a) messaging app 알림은 사람 이름 그룹으로 묶이고, (b) 비-messaging 앱은 패키지 그룹으로 묶이는지 확인 + journey 문서 동기화 + PR 마감.

**Files:**
- 수정: `docs/journeys/silent-auto-hide.md` — Known gap "MessagingStyle 힌트 미사용" bullet 옆에 `(resolved YYYY-MM-DD, plan ...)` annotation, Change log 에 한 줄 append. Known gap 본문은 그대로 둠 (annotation 만 추가).
- 수정: `app/src/main/java/com/smartnoti/app/domain/usecase/SilentNotificationGroupingPolicy.kt` — kdoc 의 "the 'MessagingStyle hint' from the plan is encoded implicitly by the listener" 주석을 "encoded by listener via `MessagingStyleSenderResolver`" 로 한 줄 정확화. (행동 변경 없음 — 코멘트 정확성만.)

**Steps:**
1. 시스템 시계 한 번 읽고 그 값을 `last-verified` / Change log 에 사용 (`.claude/rules/clock-discipline.md`).
2. `./gradlew :app:assembleDebug` + `adb -s emulator-5554 install -r ...`.
3. ADB 검증 — 두 시나리오:
   - **시나리오 A (messaging style)**: `adb shell cmd notification post --conversation "엄마" -t "엄마" Msg1 "곧 도착해"` → `dumpsys notification --noredact | grep -A3 smartnoti_silent_group` 에서 group key 가 `smartnoti_silent_group_sender:엄마` 로 잡히는지 (N≥2 충족 시).
   - **시나리오 B (비-messaging)**: `adb shell cmd notification post -t "오늘만 30% 할인" Promo1 "지금 사세요"` 를 2건 게시 → group key 가 `smartnoti_silent_group_app:com.android.shell` 형태로 잡히고 sender 그룹 (`...sender:오늘만 30% 할인`) 이 만들어지지 않는지.
4. 실패 시 listener 로그 (`adb logcat | grep SmartNoti`) 로 helper 입력값 확인. 재현 어려운 케이스는 step 5 의 ADB 한계로 명시.
5. journey 문서 갱신:
   - Known gap bullet 에 annotation 추가 (본문 unchanged).
   - Change log 에 `YYYY-MM-DD: SILENT 트레이 sender 그룹 키가 MessagingStyle 힌트 게이트로 좁혀짐 — 비-messaging 앱(쇼핑/뉴스/프로모션)이 자기 카피를 title 로 흘려도 sender 그룹 대신 App fallback 으로 묶임. 관련 plan: docs/plans/2026-04-27-silent-sender-messagingstyle-gate.md` 한 줄.
6. plan frontmatter 를 `status: shipped` + `shipped: YYYY-MM-DD` + `superseded-by: docs/journeys/silent-auto-hide.md` 로 갱신.
7. PR 작성 및 머지 (사람 review 후).

---

## Scope

**In:**
- `MessagingStyleSenderResolver` 신규 helper + 단위 테스트.
- `SmartNotiNotificationListenerService.processNotification` 의 sender 추출 한 단락 교체.
- `silent-auto-hide.md` Known gap annotation + Change log.

**Out:**
- 기존 DB row 의 sender 마이그레이션 (의도적 미수행 — 자연 cohort 교체).
- Cross-status 그룹핑 (별도 Known gap, 별도 plan).
- 그룹핑 정책 (`SilentNotificationGroupingPolicy`) 본체 변경 — fallback 분기는 이미 존재하므로 listener 의 입력만 바로잡으면 자연 발화.
- replacement 알림 (PRIORITY/DIGEST) 경로의 sender 추출 — 본 plan 의 SILENT 그룹 키 정확성 범위 밖 (Task 2 step 3 의 선택적 정리만).
- Custom layout / non-standard template 메신저 false-negative 보강 (Known gap 으로 후속).

## Risks / open questions

- **PERSON 룰 회귀**: `엄마` 등 사람 이름으로 PERSON 룰을 만든 사용자가, 해당 sender 를 messaging style 로 보내지 않는 앱(캘린더 등) 으로 받던 매칭이 본 변경 후 sender=null 로 떨어져 더 이상 매치하지 않을 수 있다. 의도된 정확도 향상이지만 기대 변경 — Change log 에 짧게 안내. **사용자 판단 필요**: PERSON 룰 매칭 정확도 vs 호환성 어느 쪽을 우선할 것인가? plan 기본은 정확도.
- **`EXTRA_MESSAGES` 접근의 deprecation 경고**: `getParcelableArray(String)` 은 API 33+ 에서 deprecated — 본 plan 은 `String?` 이름 기반 호출을 유지하되, 타입 검사 없이 `isNotEmpty()` 만 보므로 동작상 안전. 후속 lint 정리 plan 에서 type-safe API 로 마이그레이션.
- **Custom MessagingStyle template false-negative**: 일부 메신저가 `NotificationCompat` 도 표준 `MessagingStyle` 도 아닌 자체 layout 을 쓰면 본 plan 의 게이트가 sender 를 null 로 떨어뜨려 그룹이 App fallback 으로 묶일 수 있음. 사용자 신고가 들어오면 resolver 의 수용 셋을 넓혀 보강. 본 plan ship 시 silent-auto-hide.md Known gap 에 한 줄 추가.

## Related journey

- [`silent-auto-hide`](../journeys/silent-auto-hide.md) — Known gap "MessagingStyle 힌트 미사용 — `SilentNotificationGroupingPolicy` 는 `sender` 필드만 보고 MessagingStyle 여부를 직접 조회하지 않는다. 쇼핑/뉴스 앱이 상품명/기사 제목을 `sender` 로 흘리면 그 title 이 Sender 그룹 키가 되어 잘못 묶일 수 있음." 항목을 본 plan 이 해소.
