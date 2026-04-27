---
status: planned
fixes: 478
supersedes: docs/plans/2026-04-27-fix-issue-478-promo-keyword-not-routing.md
---

# Fix #478 (재진단): "(광고)" 가 title/body 가 아닌 extended content 필드에 박혀 있어 KEYWORD 매치 누락

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P0 release-blocker — ADB e2e on `emulator-5554` is mandatory (deferred not allowed). 5-step gate (failing tests → impl → regression → ADB e2e → journey bump) is non-negotiable.

**Goal:** 사용자가 PROMO_QUIETING preset 을 선택한 상태에서 이메일·캐치테이블·네이버·SSF Shop 등 한국 앱이 "(광고)" prefix 로 게시한 알림이 즉시 KEYWORD rule 매치 → DIGEST 분류 → 원본 tray cancel 까지 수행돼서 시스템 알림 센터에서 사라지고 SmartNoti Hidden / Digest 뷰로 이동한다. Issue #478 의 실제 사용자 신고 (multi-app reproduction) 를 끝낸다.

**Architecture:** 선행 plan (`2026-04-27-fix-issue-478-promo-keyword-not-routing.md`) 의 Task 1 RED 6-hypothesis sweep 을 PR #483 implementer 가 ADB 로 추적한 결과, 시드/`enabled`/Category.action/suppression 정책/extraction 회귀는 전부 GREEN 으로 격리됨. 진짜 root cause 는 **classifier 의 매칭 입력 surface 가 너무 좁다**는 것 — `NotificationClassifier.findMatchingRules` 와 priority-keyword 분기는 `content = listOf(input.title, input.body).joinToString(" ")` 만 본다 (classifier.kt 104, 133 line 부근). 한국 KCC (방통위) 광고 표시 가이드라인에 따라 한국 앱 다수가 "(광고)" 를 `EXTRA_SUB_TEXT` (header subtext) / `EXTRA_SUMMARY_TEXT` (style summary) / `EXTRA_BIG_TEXT` (BigTextStyle 본문) / `MessagingStyle.messages[*].text` / `InboxStyle.lines` / `EXTRA_INFO_TEXT` 같은 extended 필드에 넣는다. 이 필드들은 현재 `NotificationPipelineInputBuilder.build` 가 추출하지 않으므로 (line 46-47 EXTRA_TITLE + EXTRA_TEXT 만 read) classifier 까지 도달조차 못함 → KEYWORD 매치 실패 → 다른 heuristic 도 unmatch → SILENT default → DIGEST 가 아니므로 source tray cancel 안 함 → 사용자 트레이에 "(광고)" 가 그대로 남는다. 수정 위치는 `NotificationPipelineInputBuilder` (extras aggregation) + classifier 의 매칭 surface 를 `title/body` 단일 string 에서 "matchable text bundle" 로 확장.

**Tech Stack:** Kotlin, AndroidX `NotificationCompat` (MessagingStyle/InboxStyle/BigTextStyle extractor), Gradle unit tests (Robolectric for `Notification.Builder` fixture posting), ADB on `emulator-5554`. minSdk=26 → MessagingStyle (24+) / InboxStyle (16+) / BigTextStyle (16+) 모두 unconditional 사용 가능.

---

## PR title format: `fix(#478): <one-line>`
## PR body must include `Closes #478` + Task 1 RED test names + Task 4 ADB log

---

## Product intent / assumptions

- 한국 KCC 가이드 준수 앱 다수가 "(광고)" 를 `subText` / `summaryText` / `bigText` / `messages[*].text` / `inboxLines` / `infoText` 등 title/body 외 위치에 둔다. SmartNoti 의 KEYWORD 매치는 **노출 가능한 모든 텍스트 필드** 를 대상으로 해야 한다는 것이 제품 의도 — 사용자 입장에서는 "내 눈에 (광고) 가 보였는데 SmartNoti 가 못 잡았다" 면 버그.
- 기존에 title/body 로 매칭되던 케이스는 회귀하지 않아야 한다 — 새 필드를 합쳐도 기존 매치는 여전히 GREEN.
- DIGEST 결정 후 tray cancel 은 `suppressSourceForDigestAndSilent` global toggle 이 ON (2026-04-24 default ON) 일 때 listener 가 수행. 이 동작은 PR #483 의 ADB 추적으로 이미 PASS 확인됨 — 본 plan 은 cancel 정책을 바꾸지 않는다.
- "(광고)" 가 `extras.android.text` (= EXTRA_TEXT, body) 에 이미 있는 앱은 본 fix 의 scope 밖 — 이미 매치되고 있음. 회귀 가드만 둔다.
- `notification-capture-classify` journey 의 Observable steps 3 ("Extras 에서 EXTRA_TITLE, EXTRA_TEXT, EXTRA_CONVERSATION_TITLE 을 꺼내…") 는 본 fix 와 함께 갱신돼야 한다 — extras 추출 surface 가 확장됨.

---

## Task 1: Failing test suite — extended content field 6종 각각 distinct reproduction

**Objective:** "(광고)" 가 6개 extended 필드 중 어디에 있어도 KEYWORD `광고` 룰이 매치돼 DIGEST 로 라우팅됨을 증명. 모든 sub-test 는 RED 로 시작. RED 인 test 의 이름/메시지를 commit message + PR body 에 그대로 인용.

**Files (신규 또는 보강):**
- `app/src/test/java/com/smartnoti/app/notification/PromoExtendedContentFieldRoutingTest.kt` (신규, 메인 reproduction suite — Robolectric for `Notification.Builder` / `NotificationCompat.Style` fixtures, fake `StatusBarNotification` via reflection 또는 기존 test util 재활용)
- `app/src/test/java/com/smartnoti/app/notification/NotificationPipelineInputBuilderTest.kt` (보강 — extras aggregation 의 회귀 가드 6 case 추가)
- `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationClassifierTest.kt` (보강 — `ClassificationInput` 에 extended content 가 합류한 상태에서 KEYWORD 룰이 매치하는 단위 test)

**Steps (각 sub-step 은 distinct 필드를 격리):**

1. **F1 — `EXTRA_SUB_TEXT` (header subtext, 작은 회색 글씨):** `Notification.Builder(...).setSubText("(광고) 한정 특가").setContentTitle("이메일 도착").setContentText("Sender 이름")` → fake StatusBarNotification → `NotificationPipelineInputBuilder.build(sbn, settings)` → 결과 `CapturedNotificationInput` 의 매칭 surface (title/body 또는 새 확장 필드) 에 "(광고)" 가 포함되는지. 그리고 `NotificationClassifier.classify(input, rules=[Promo광고KeywordRule], categories=[PromoCategory(action=DIGEST, ruleIds=[...])])` 결과 `decision = DIGEST`.
2. **F2 — `EXTRA_SUMMARY_TEXT` (style summary line):** `BigTextStyle.setSummaryText("(광고) 캐치테이블 이벤트")` 로 동일 검증.
3. **F3 — `EXTRA_BIG_TEXT` (BigTextStyle 본문):** `BigTextStyle.bigText("(광고) 오늘만 무료 배송 — 자세히 보기")` 로 동일 검증. EXTRA_TEXT 에는 (광고) 미포함.
4. **F4 — `MessagingStyle.messages[*].text` (대화 메시지):** `MessagingStyle(user).addMessage("(광고) SSF Shop 신상", postTime, Person("브랜드"))` 로 동일 검증. 첫 메시지와 마지막 메시지 두 변형 모두 (각각 sub-test) — 일부 앱은 첫 메시지를 promo summary 로 사용.
5. **F5 — `InboxStyle.lines` (인박스 줄 배열):** `InboxStyle().addLine("일반 알림").addLine("(광고) 네이버 쇼핑 쿠폰")` 로 동일 검증.
6. **F6 — `EXTRA_INFO_TEXT` (오른쪽 정렬 info):** `Notification.Builder(...).setContentInfo("(광고)")` 로 동일 검증. (Android API 24+ 에서 info text 는 deprecated 지만 여전히 일부 앱이 사용)

7. **Negative control (회귀 가드):** "(광고)" 가 EXTRA_TEXT (body) 에 있는 기존-매치 케이스가 여전히 DIGEST 로 매치되는지 1 case.
8. **`./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.PromoExtendedContentFieldRoutingTest" --tests "com.smartnoti.app.notification.NotificationPipelineInputBuilderTest" --tests "com.smartnoti.app.domain.usecase.NotificationClassifierTest"` 로 RED 6건 + GREEN 1건 (negative control) 확인.** RED 인 test 이름은 commit + PR body 에 그대로 인용.

## Task 2: Fix — extras aggregation 확장 + classifier 매칭 surface 확장

**Objective:** Task 1 의 6 RED → GREEN. Negative control + 기존 unit test 모두 GREEN 유지.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/NotificationPipelineInputBuilder.kt` — extras 에서 6개 필드 추가 추출.
- `app/src/main/java/com/smartnoti/app/domain/model/CapturedNotificationInput.kt` 또는 `ClassificationInput.kt` — 매칭 surface 를 담을 새 필드 (e.g. `extendedContent: String` 또는 `additionalMatchableText: List<String>`). 기존 `title` / `body` 는 그대로 유지 (UI 표시 / sender 추론에 필요).
- `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationClassifier.kt` — `findMatchingRules` 와 priority-keyword 분기의 `content` 조립을 `title + body + extendedContent` 로 확장.

**Steps:**
1. `NotificationPipelineInputBuilder.build` 에서 다음을 추출해 single string 또는 list 로 합친다:
   - `extras.getCharSequence(Notification.EXTRA_SUB_TEXT)`
   - `extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)`
   - `extras.getCharSequence(Notification.EXTRA_BIG_TEXT)`
   - `extras.getCharSequence(Notification.EXTRA_INFO_TEXT)`
   - `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)?.messages?.map { it.text?.toString() }` (null safe, API 24+ 보장)
   - `NotificationCompat.InboxStyle` 추출 후 `extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)` (InboxStyle 의 line 배열은 standard extras key 로 노출됨)
2. 합친 결과를 `CapturedNotificationInput` 의 새 필드로 전달.
3. `NotificationClassifier` 의 `content` 변수가 새 필드를 합쳐 KEYWORD/priorityKeywords 매칭에 사용. 다른 분기 (PERSON / APP / SCHEDULE / REPEAT_BUNDLE) 는 기존 입력 그대로 — 매칭 의미가 다름.
4. `NotificationDuplicateContextBuilder` 의 `contentSignature` 계산은 의도적으로 변경하지 않는다 — extended content 를 signature 에 합치면 dedup 키가 폭발해 중복 카운트 정확도가 떨어진다 (별도 plan 후보, Risks 에 명시).

## Task 3: Regression — 기존 characterization / 기존 KEYWORD 매치 + PR #483 가 회귀시켰던 suppression auto-expansion 재GREEN 확인

**Objective:** 새 surface 확장이 어떤 기존 동작도 깨지 않음을 증명. 특히 PR #483 implementer 가 "회귀시켰다" 고 보고한 `SuppressedSourceAppsAutoExpansionPolicy` test 가 본 plan 적용 후에도 GREEN 인지 확인 (PR #483 의 회귀는 wrong-premise 였으므로 본 plan 코드 변경과 독립; 그러나 안전망으로 명시 검증).

**Files:** 변경 없음 — 검증만.

**Steps:**
1. `./gradlew :app:testDebugUnitTest` 전체 GREEN. RED 0건. 특히:
   - `SuppressedSourceAppsAutoExpansionPolicyTest`
   - `NotificationDecisionPipelineCharacterizationTest`
   - `SmartNotiNotificationListenerServiceCategoryInjectionTest`
   - `RuleWithoutCategoryNoOpTest`
   - `NotificationClassifierTest` 의 모든 기존 case
2. `./gradlew :app:assembleDebug` 빌드 통과.
3. PR body 에 RED → GREEN diff (test 이름 list) 첨부.

## Task 4: ADB e2e on `emulator-5554` (P0 — deferred 불가)

**Objective:** 사용자 신고 시나리오를 emulator 에서 실측 재현. 가능하다면 실제 한국 앱 (이메일 / 네이버 / SSF Shop / 캐치테이블 — 사전 설치 가능 시) 의 promo 알림으로 확인; 그 외 fallback 으로 fake StatusBarNotification 6 fixture 를 게시한다.

**Steps:**
```bash
# 0. Fresh debug APK 설치 + onboarding 완료 + PROMO_QUIETING 선택
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm clear com.smartnoti.app
# (onboarding 수동 통과 — PROMO_QUIETING 체크)

# 1. KEYWORD '광고' 룰 시드 확인
adb -s emulator-5554 shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT id,type,matchValue,enabled,categoryId FROM rules WHERE matchValue LIKE '%광고%';"
# expected: 1+ row, type=KEYWORD, enabled=1

# 2-A. (가능하면) 실제 앱 promo 알림 자연 발생 캡처
#    - 해당 앱 설치/로그인 후 실제 promo 알림 도착 대기 또는 앱이 제공하는 테스트 모드 사용.
#    - dumpsys 로 raw notification 구조 캡처:
adb -s emulator-5554 shell dumpsys notification --noredact > /tmp/dumpsys-before.txt
# (광고 알림 도착 후)
adb -s emulator-5554 shell dumpsys notification --noredact > /tmp/dumpsys-after.txt
# (광고) 가 어느 extras key 에 있는지 확인 — F1~F6 중 어디에 해당하는지 PR body 에 인용.

# 2-B. (실측 불가 시 fallback) fake fixtures 6개 게시
#    cmd notification post 의 직접 옵션으로 sub/summary/big/info 를 박을 수 없는 경우,
#    debug-only inject receiver (DebugInjectNotificationReceiver) 를 확장하거나
#    Robolectric Task 1 fixture 를 시드 데이터로 활용. 본 plan 의 ADB 단계는
#    최소한 EXTRA_BIG_TEXT 한 case 는 cmd notification post -S bigtext --big-text 로 검증:
adb -s emulator-5554 shell cmd notification post -S bigtext \
  -t "캐치테이블" PromoBigText_478 "이벤트 안내" --big-text "(광고) 오늘만 한정 50% 할인"
# (다른 5 case 는 Task 1 의 Robolectric test 로 대신 — Task 4 는 가장 흔한 케이스만 e2e)

# 3. DB 캡처 결과 확인 — DIGEST 로 라우팅됐는지
adb -s emulator-5554 shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT title,status,reasonTags,ruleHitIds FROM notifications \
   WHERE postedAtMillis > (strftime('%s','now')*1000 - 60000) \
   ORDER BY postedAtMillis DESC LIMIT 10;"
# expected: status=DIGEST; ruleHitIds 에 keyword:광고,... 포함

# 4. 시스템 tray 에서 원본 cancel 확인
adb -s emulator-5554 shell dumpsys notification --noredact | grep -A3 "PromoBigText_478"
# expected: 항목 부재 (cancelled). SmartNoti replacement digest 가 보이면 OK.

# 5. SmartNoti 앱 Hidden/Digest 뷰에 표시되는지 UI 확인
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 pull /sdcard/ui.xml /tmp/ui.xml
grep -oE '\(광고\)[^"]*' /tmp/ui.xml
# expected: (광고) 오늘만 한정 50% 할인 가 SmartNoti 화면 어딘가에 등장
```

전부 PASS 면 Task 5 진행. 실측 (2-A) 와 fixture (2-B) 모두 reproduce 못 하면 plan-implementer 가 멈추고 보고 — 이 plan 의 hypothesis 가 틀렸을 가능성 (Risks 의 "2nd root cause" 분기) 으로 escalate.

## Task 5: Bump `notification-capture-classify` `last-verified` + Observable steps 갱신

**Objective:** journey 문서 갱신으로 fix 가 contract 상 반영됐음을 audit 에 남김.

**Files:**
- `docs/journeys/notification-capture-classify.md`

**Steps:**
1. Frontmatter `last-verified:` 를 Task 4 ADB sweep 을 실행한 날짜 (system clock `date -u +%Y-%m-%d`) 로 갱신. **모델 컨텍스트의 "오늘 날짜" 신뢰 금지 — `.claude/rules/clock-discipline.md` 적용.**
2. Observable steps 3 갱신: "Extras 에서 `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_CONVERSATION_TITLE` 외에 `EXTRA_SUB_TEXT`, `EXTRA_SUMMARY_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_INFO_TEXT`, `MessagingStyle.messages[*].text`, `InboxStyle` lines 까지 추가 추출해 KEYWORD/priorityKeywords 매칭의 `content` 에 합류시킨다. UI 표시용 title/body 와 sender 추론은 기존 EXTRA_TITLE/EXTRA_TEXT/EXTRA_CONVERSATION_TITLE 만 사용 — 변경 없음."
3. Code pointers 에 `NotificationPipelineInputBuilder` 의 extras aggregation 줄 한 줄 추가 (행 번호 박지 말 것 — `.claude/rules/docs-sync.md` 금지사항).
4. Change log 에 row 추가:
   - `YYYY-MM-DD: fix #478 — "(광고)" prefix 알림 routing 회귀 해소. Root cause: classifier 매칭 surface 가 EXTRA_TITLE+EXTRA_TEXT 만 보고 있어, 한국 앱이 EXTRA_SUB_TEXT/EXTRA_SUMMARY_TEXT/EXTRA_BIG_TEXT/MessagingStyle/InboxStyle/EXTRA_INFO_TEXT 에 박은 "(광고)" 를 못 잡았음. Fix: NotificationPipelineInputBuilder 에서 6개 필드 추가 추출 + ClassificationInput 에 extendedContent 필드 추가 + NotificationClassifier 의 content aggregation 확장. Recipe: PR body 의 Task 4 ADB log + Task 1 Robolectric 6 fixture. last-verified bump 동반.`
5. Known gaps 섹션에 새 gap 을 만들지 않는다 — 회귀가 닫혔으므로.
6. (선택) `digest-suppression.md` Change log 에도 cross-link 한 줄.

---

## Scope

**In:**
- `NotificationPipelineInputBuilder` 의 extras aggregation 6개 필드 추가.
- `CapturedNotificationInput` / `ClassificationInput` 에 extended content 필드 추가.
- `NotificationClassifier` 의 KEYWORD + priorityKeywords 매칭 surface 확장.
- 위 6 필드 각각 distinct 한 Robolectric test + classifier unit test.
- `notification-capture-classify` journey Observable steps + Code pointers + Change log + `last-verified`.

**Out:**
- `contentSignature` 계산 로직 — 의도적으로 기존 title+body 만 유지 (Risks 참고).
- VIP / shopping / quiet-hours / repeat-bundle 분기 — KEYWORD 와 의미가 다름.
- Hidden/Digest 화면 UX (별도 plan).
- 새 keyword 추가 / preset 변경.
- Sticky exclude list (별도 plan `2026-04-26-digest-suppression-sticky-exclude-list.md`).
- 선행 plan (`2026-04-27-fix-issue-478-promo-keyword-not-routing.md`) 의 6-hypothesis sweep — 본 plan 이 supersede.

---

## Risks / open questions

- **Hypothesis 불확실성:** 사용자가 ADB `dumpsys notification` 덤프를 아직 공유하지 않았다 — "(광고)" 가 어느 extras key 에 있는지 직접 확인 못함. Plan 은 6 후보 필드 모두 커버하는 over-shot 전략으로 안전 마진 확보. Task 4 의 fixture (2-B) 가 reproduce 못 하면 plan-implementer 가 멈추고 사용자에게 dumpsys 덤프를 요청해 escalate.
- **2nd root cause 가능성:** 6 필드를 다 합쳐도 reproduce 안 되면, notification grouping summary (그룹 헤더 알림이 child 의 (광고) 를 가린 상태) 또는 `Notification.Builder.setContentText(spannable)` 의 spannable formatting 차이가 추가 root cause 일 수 있음. 그 경우 별도 plan 으로 분기.
- **이미 (광고) 가 body 에 있는 앱:** 부수효과 없음 — 새 필드는 join 으로 합치므로 기존 매치는 그대로. Negative control test 로 가드.
- **`contentSignature` dedup 폭발 위험:** 만약 signature 에 extended content 까지 합치면 같은 (광고) 알림이 매번 다른 본문으로 도착 시 dedup 키가 매번 새로 생겨 LiveDuplicateCountTracker 카운트가 항상 1 이 됨 → REPEAT_BUNDLE 룰 작동 안 함. 따라서 본 plan 은 signature 변경 금지를 명시. 별도로 dedup 키 정책을 손대고 싶다면 별도 plan.
- **MessagingStyle API gating:** minSdk=26 이므로 MessagingStyle (24+) / InboxStyle (16+) / BigTextStyle (16+) 모두 unconditional. NotificationCompat 사용으로 vendor stub 차이도 흡수. 별도 SDK gate 불필요.
- **Open question for the user:** ADB `dumpsys notification --noredact` 덤프를 한 번 공유해 주실 수 있나요? "(광고)" 가 어느 필드에 있는지 직접 확인하면 6 필드 후보 중 진짜 hit 만 좁혀서 surface 확장 폭을 줄일 수 있습니다 (over-fit 방지). 본 plan 은 dumpsys 없이도 안전하게 진행할 수 있도록 6개 모두 커버하지만, dumpsys 가 들어오면 Task 1 의 RED 우선순위가 명확해지고 Task 4 e2e 도 더 신뢰도 높아집니다.
- **시스템 시계 (clock-discipline):** Task 5 의 `last-verified` 와 Change log 날짜는 반드시 `date -u +%Y-%m-%d` 결과 사용 — 모델 컨텍스트 날짜 신뢰 금지 (`.claude/rules/clock-discipline.md`).
- **No-code-write boundary:** 이 plan 은 gap-planner 의 산출물 — plan-implementer 가 다음 단계로 코드 작성. plan-implementer 가 Task 1 RED 결과를 PR body 에 인용하지 않으면 PM 의 traceability gate 에서 reject.

---

## Related journey

- [`docs/journeys/notification-capture-classify.md`](../journeys/notification-capture-classify.md) — 분류 cascade 의 hot-path. Observable steps 3 (extras 추출 surface) 와 Code pointers (`NotificationPipelineInputBuilder`) 가 본 fix 와 함께 갱신됨.
- 보조: [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — DIGEST 분류 결과의 source tray cancel 정책 (변경 없음 — 이미 PASS, PR #483 ADB 추적으로 확인).
- 보조: [`docs/journeys/onboarding-bootstrap.md`](../journeys/onboarding-bootstrap.md) — PROMO_QUIETING preset 시드 경로 (변경 없음 — 이미 PASS).
