---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/persistent-notification-protection.md
---

# Persistent Bypass Keyword 매칭을 substring contains 에서 word-boundary 로 강화 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 "지속 알림 원본 자동 숨김" 을 켰을 때, 진짜 통화/내비/녹화/마이크/카메라 같은 critical persistent 알림은 그대로 tray 에 남기되, "전화번호 변경 안내", "오늘 회의 녹화본 업로드", "통화 내역 정리" 같이 단어가 우연히 포함된 일반 마케팅/안내 persistent 알림은 더 이상 잘못 bypass 되지 않게 한다. 사용자가 관측하는 변화: 가짜 keyword 매치로 살아남던 persistent 마케팅 알림이 정상적으로 DIGEST 로 묶이고 tray 에서 사라진다. 진짜 통화/내비 알림은 그대로 유지된다.

**Architecture:**
- `PersistentNotificationPolicy.shouldBypassPersistentHiding(...)` 의 매칭 로직만 바꾼다 — 단순 `keyword in normalizedText` 대신 정규식 `\b<keyword>\b` (단어 경계) 또는 한글에 대해서는 인접 글자가 한글이 아닌 경우만 매치되는 동등 규칙으로 강화. `BYPASS_KEYWORDS` 의 set 자체는 유지하되 각 keyword 별로 안전한 토큰화 함수를 한 번 거치도록 재구성.
- 한글 word boundary 는 자바 정규식 `\b` 만으로는 정확하지 않다 (한글 문자는 모두 word character 라 "전화번호" 안의 "전화" 도 boundary 매치됨). 따라서 한글 키워드는 별도 처리 — 매치 후보 위치의 직전/직후 문자가 한글 (`\p{IsHangul}`) 이거나 한글 자모 (`\p{InHangul_Jamo}`) 이면 매치 무효 처리. 영문 키워드는 `\b` 만으로 충분.
- 기존 호출 사이트 (`SmartNotiNotificationListenerService#processNotification` line 230-239) 는 시그니처가 동일하므로 wiring 불변. policy 내부 변경만.

**Tech Stack:** Kotlin (정규식 + Unicode Block API), JUnit unit tests. Android dependency 없음 — pure domain.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `persistent-notification-protection` 의 Known gap 둘째 bullet "키워드 매치가 substring contains 기반이라 오탐 가능성 (예: "전화번호" 라는 단어만 있어도 bypass 됨)" 은 사용자가 직접 본문에 등록한 항목. "예: 전화번호" 가 명시적 false-positive 사례 — 본 plan 은 그 예시를 첫 회귀 테스트 케이스로 박는다.
- **결정 필요**: 매칭 강화의 강도 — 본 plan 은 "단어 경계" 만 채택. "통화 중", "마이크 사용 중" 같이 공백을 포함한 multi-token keyword 는 양 끝만 word-boundary 로 보면 충분하다 (사이는 공백이라 자연스럽게 토큰 분리). 더 엄격한 방식 (예: 의미적 분류, MediaSession 기반 보호) 은 [protected-source-notifications](../journeys/protected-source-notifications.md) journey 가 이미 담당 — 본 plan 은 텍스트 폴백의 정확도만 끌어올린다.
- **결정 필요**: 한글 word boundary 정의 — 본 plan 은 "매치된 substring 의 직전 문자와 직후 문자가 모두 한글 또는 한글 자모가 아닐 때만 유효" 규칙 채택. 예:
   - "통화 중" 본문에서 "통화" 매치 → 직후가 공백 → OK.
   - "전화번호 변경 안내" 에서 "전화" 매치 → 직후가 "번" (한글) → 무효.
   - "내 통화기록을 봤다" 에서 "통화" 매치 → 직후가 "기" (한글) → 무효.
   - "통화" 한 단어로 끝나는 본문 → 직후가 문자열 끝 → OK.
   - 의도된 결과: 한국어 구문이 "통화" 를 명사 그대로 노출할 때만 매치. 합성어 "전화번호", "통화기록", "녹화본", "내비게이션은" 등은 모두 매치 무효.
- **결정 필요**: `lowercase()` 호출은 그대로 유지 — 영문 키워드 (예: "Call", "MAPS") 가 다양한 case 로 와도 대응.
- **결정 필요**: 기존 7개 회귀 테스트가 모두 GREEN 으로 유지되어야 한다. 본 plan 의 변경은 false-positive 차단만 목적이고 진짜 bypass 케이스는 동작 동일. 만약 어떤 기존 케이스가 새 규칙으로 RED 가 되면 → 그건 원래 테스트 자체가 false-positive 케이스를 잘못 보호하고 있었다는 신호이므로, 본 plan 의 PR 본문에 명시적으로 보고하고 사용자 결정을 요청한다 (silent rewrite 금지).
- **결정 필요**: 다국어 키워드 (일본어 "通話", 중국어 "通话" 등) 추가는 본 plan scope 외 — `persistent-notification-protection` Known gap 첫 bullet ("키워드가 한국어/영어 혼합으로 하드코딩 — 다국어 대응 미흡") 은 별도 plan 에서 처리. 본 plan 은 매칭 알고리즘만 강화.
- **결정 필요**: 매칭 함수 위치 — `PersistentNotificationPolicy` 내부 private 함수로 두는 게 가장 자연스러움. 별도 top-level utility 로 빼면 다른 곳에서 재사용 가능하지만 현재는 한 곳에서만 쓰이므로 over-extraction. 만약 `ProtectedSourceNotificationDetector` 도 향후 동일 매칭이 필요하면 그 plan 이 추출하면 된다.
- 본 plan 은 Compose / Settings / DataStore 변경 없음. 사용자 설정 옵션 추가 없음. 순수 정책 강화.

---

## Task 1: Add failing tests for keyword false-positive cases [IN PROGRESS via PR #399]

**Objective:** 사용자가 gap 본문에 박은 "전화번호" 케이스를 비롯해 합성어/접미사 false-positive 를 테스트로 고정. 구현 전에 RED 확인.

**Files:**
- `app/src/test/java/com/smartnoti/app/domain/usecase/PersistentNotificationPolicyTest.kt` (기존 파일에 케이스 append)

**Steps:**
1. 다음 케이스를 `@Test` 로 추가 (모두 `assertFalse(... shouldBypassPersistentHiding(...))` — 즉 합성어 false-positive 는 bypass 가 일어나면 안 됨):
   - **전화번호 안내**: title `"전화번호 변경 안내"`, body `"고객님의 전화번호가 변경되었습니다"` → 현재는 "전화" 가 매치되어 bypass=true (원치 않음). 새 규칙에서는 false 여야 함.
   - **녹화본 업로드**: title `"오늘 회의 녹화본 업로드 완료"`, body `"공유 링크를 확인하세요"` → "녹화" 가 합성어 안에 있어 bypass=true 가 일어남. 새 규칙에서 false.
   - **통화기록 정리**: title `"통화기록을 정리할 수 있어요"`, body `"이번 달 사용량 안내"` → "통화" false positive. 새 규칙에서 false.
   - **내비게이션 광고**: title `"새로운 내비게이션 앱 출시"`, body `"길안내 + 실시간 교통"` → "내비" + "길안내" 모두 합성어 안. 새 규칙에서 false (단, "길안내" 가 단독 명사로 분리돼 있으므로 본문 끝의 "길안내" 는 매치될 가능성도 — 위 본문은 "길안내 + 실시간" 로 단어 boundary 라 사실 매치 유효. 본 케이스의 의도는 title 에서 "내비게이션" 합성어 false-positive 만 검증. body 는 title 만 검증하도록 수정 가능).
   - **maps 광고**: title `"Maps Pro 출시 이벤트"`, body `"오늘만 50% 할인"`, packageName `"com.example.maps_promotion"` → "maps" 매치되지만 단독 단어로 word boundary 통과 → 새 규칙에서도 true 가 유지될 수 있다. 이 케이스는 본 plan 의 한계를 명시하기 위한 negative documentation: "단독 영단어로 등장하면 여전히 bypass". 사용자 결정 필요할 수 있음 — 일단 테스트는 작성하되 expected 를 `true` 로 두고 주석에 "한계 — Maps 같은 영문 단독어는 합성어 분리 불가, 후속 plan" 명시.
   - **camera in use 합성어**: body `"new camera in use feature launched"` → "camera in use" 가 영문 토큰 사이에서 boundary 통과. 새 규칙에서도 true 유지 (한계 — multi-word 영문 keyword 는 의미 분리 어려움). 테스트 expected `true` + 주석.

2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.PersistentNotificationPolicyTest"` → 처음 4개 (전화번호, 녹화본, 통화기록, 내비게이션) RED 확인. 마지막 2개 (maps, camera in use) 는 expected=true 로 미리 GREEN.

3. 기존 7개 테스트는 그대로 두고 변경하지 않음 — 본 plan 의 매칭 변경이 그것들을 깨면 안 됨이 회귀 테스트의 핵심.

## Task 2: Implement word-boundary matcher with Hangul-aware adjacency check [IN PROGRESS via PR #399]

**Objective:** Task 1 의 처음 4개 케이스가 GREEN, 기존 7개 + 마지막 2개도 GREEN 이 되게.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/usecase/PersistentNotificationPolicy.kt`

**Steps:**
1. `shouldBypassPersistentHiding(...)` 의 마지막 라인 (`return BYPASS_KEYWORDS.any { keyword -> keyword in normalizedText }`) 을 새 private helper `containsAsWord(text, keyword)` 호출로 교체.

2. `containsAsWord(text: String, keyword: String): Boolean` 구현:
   - `var fromIndex = 0`
   - 루프: `val idx = text.indexOf(keyword, fromIndex)`. `if (idx < 0) return false`.
   - 직전 문자 `prev = text.getOrNull(idx - 1)`, 직후 문자 `next = text.getOrNull(idx + keyword.length)`.
   - `if (isWordBoundary(prev) && isWordBoundary(next)) return true`
   - else `fromIndex = idx + 1` continue.

3. `isWordBoundary(c: Char?): Boolean` 구현:
   - `c == null` (문자열 끝/시작) → true.
   - 한글 또는 한글 자모 (`Character.UnicodeBlock.of(c)` 가 `HANGUL_SYLLABLES`, `HANGUL_JAMO`, `HANGUL_COMPATIBILITY_JAMO`, `HANGUL_JAMO_EXTENDED_A`, `HANGUL_JAMO_EXTENDED_B`) → false.
   - 알파벳/숫자 (`c.isLetterOrDigit()`) → false.
   - 그 외 (공백, 구두점, 기호) → true.

4. `BYPASS_KEYWORDS` set 은 그대로 유지 — 각 keyword 가 한글이든 영문이든 multi-word 든 같은 helper 한 곳에서 처리됨.

5. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.PersistentNotificationPolicyTest"` → 전체 GREEN.

6. 만약 기존 7개 중 어떤 케이스가 RED 가 되면:
   - **즉시 멈추고 PR 본문에 보고**. silent rewrite 금지.
   - 가능성 높은 시나리오: `"통화 중"` keyword 매치 — 본 plan 의 helper 는 substring 내부의 boundary 를 본다. `"통화 중"` 자체는 공백을 포함한 단일 keyword 라 양 끝 (`통` 직전, `중` 직후) 만 boundary 검증. 정상 동작이어야 함.
   - 만약 그래도 RED 라면 helper 의 boundary 정의 (`isWordBoundary`) 가 너무 엄격한 것 — 케이스 별 디버깅 후 PR 본문에 결정 기록.

## Task 3: Add micro-benchmark sanity check

**Objective:** 새 매칭 로직이 hot path 에서 측정 가능한 regression 을 일으키지 않는지 확인. SmartNoti 의 알림 처리 hot path 는 `processNotification` 인데, 한 알림당 `BYPASS_KEYWORDS.size` (=15) × `containsAsWord` 호출. 이전 `keyword in text` 보다 약간 느리지만 문제가 될 수준은 아니어야 함.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/PersistentNotificationPolicyBenchmarkTest.kt` (선택 — 별도 클래스로 분리해서 일반 unit test 시간을 늘리지 않음)

**Steps:**
1. JMH 같은 정식 benchmark 도구 도입은 over-engineering. 단순히 `System.nanoTime()` 으로 1000 회 반복 호출 평균을 측정하고, 한 호출당 1ms 이내 (대략 안전 마진) 인지 assert.
2. 케이스: 가장 긴 본문 (현실적으로 알림 body 최대 ~1KB 가정) + 모든 keyword false-positive 시나리오 → 가장 느린 path.
3. 시간 assertion 은 환경 의존이 크므로 PR 본문에 평균만 기록하고, 테스트는 실제로 fail 시키지 않고 println 로만 보고 — implementer 가 결정. 또는 단순히 함수 호출 1만 회가 1초 이내에 완료되는지 정도의 거친 cap.
4. 이 task 가 과한 작업이라 판단되면 implementer 가 Task 3 을 SKIP 하고 PR 본문에 "벤치마크 생략 — hot path 변경이 미미하다고 판단, 이유: …" 한 줄만 기록 가능.

## Task 4: Manual verification on real persistent notifications

**Objective:** Policy 단위 테스트만으로는 실제 listener hot path 의 통합 동작을 보장 못 한다. 진짜 persistent 알림 두 개 (통화/녹화) + false-positive 후보 한 개 (마케팅 SMS / 광고) 로 end-to-end 확인.

**Steps:**
1. APK 빌드 후 설치: `./gradlew :app:installDebug`.
2. **Negative — 진짜 통화 알림 보호 유지**: 에뮬레이터 dialer 가 없다면 SmartNoti 의 `PersistentNotificationPolicyTest` 의 unit verification 으로 갈음. 또는 실기기에서 실제 통화 발신 후 SmartNoti 설정 "지속 알림 원본 자동 숨김" 켠 상태로 tray 에 통화 알림이 유지되는지 확인.
3. **Positive — false-positive 차단**: ADB 로 persistent 합성어 알림 게시:
   ```
   adb shell cmd notification post -S bigtext -t "전화번호변경" PersistTest "고객님의 전화번호가 곧 변경됩니다. 확인 부탁드립니다."
   ```
   - 단, `cmd notification post` 는 `FLAG_ONGOING_EVENT` 직접 세팅 불가 (verification recipe 의 알려진 한계). isPersistent 판정 자체가 발화 안 되어 본 plan 의 효과 측정 불가.
   - 우회: 테스트용 forground service 가 있는 sample 앱이 있다면 그것으로 ongoing 알림을 띄운 뒤 본문에 "전화번호" 가 들어간 케이스 시뮬레이션.
   - 우회 가능 도구가 없으면 **policy unit test 만으로 검증** 하고 manual verification 은 SKIP — `persistent-notification-protection` Verification recipe 가 이미 명시한 환경 한계와 동일.
4. SKIP 이라도 PR 본문에 해당 사실을 명시하고 unit test 결과 (전체 GREEN, 추가 4 케이스 PASS) 를 attach.

## Task 5: Update `persistent-notification-protection` journey

**Objective:** Observable steps + Code pointers + Known gaps + Tests + Change log 동기화.

**Files:**
- `docs/journeys/persistent-notification-protection.md`

**Steps:**
1. Observable step 3 의 키워드 검사 라인에 "매칭은 단어 경계 (한글: 인접 문자가 한글/자모가 아닐 때, 영문: `\b`) 를 적용해 합성어 false-positive (예: "전화번호") 를 차단" 한 문장 추가.
2. Known gaps 의 두 번째 bullet ("키워드 매치가 substring contains 기반이라 오탐 가능성 (예: "전화번호" 라는 단어만 있어도 bypass 됨).") 을 **(resolved 2026-04-26, plan `2026-04-26-persistent-bypass-keyword-word-boundary`)** prefix 로 마킹. 본문 텍스트는 그대로 유지 — `.claude/rules/docs-sync.md` 의 annotate-only 규칙.
3. Tests 라인에 "합성어 false-positive 회귀 케이스 4종 추가 (전화번호 / 녹화본 / 통화기록 / 내비게이션)" 한 줄 추가.
4. Code pointers 에 변경 없음 (파일 경로 동일, helper 는 같은 클래스 내부 private).
5. Change log 에 본 PR 항목 append (날짜는 implementer 가 작업한 실제 UTC 날짜, 요약, plan link, PR link merge 시 채움).
6. `last-verified` 는 manual verification 이 SKIP 됐다면 갱신하지 않음. policy unit test 7→11 으로 늘어 GREEN 이라면 `last-verified` 는 최종 갱신 가능 (현재 verification recipe 가 unit test 로 충분하다고 명시함).

## Task 6: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 명시:
  1. 채택한 word-boundary 정의 (한글: Hangul block 인접 검사, 영문: `isLetterOrDigit`) 와 그 한계 (Maps / camera in use 같은 단독 영단어는 여전히 false-positive 가능).
  2. 기존 7개 회귀 테스트 GREEN 유지 확인.
  3. 신규 false-positive 케이스 4개 RED→GREEN 전환 확인.
  4. Manual verification 결과 또는 SKIP 사유 (cmd notification 의 `FLAG_ONGOING_EVENT` 한계).
- PR 제목 후보: `fix(persistent): match bypass keywords by word boundary instead of substring`.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/persistent-notification-protection.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- `PersistentNotificationPolicy.shouldBypassPersistentHiding` 내부 매칭 알고리즘 변경 — substring `contains` → word-boundary `containsAsWord`.
- 한글-aware boundary helper 추가 (private to policy class).
- 기존 `BYPASS_KEYWORDS` set 유지 (변경 없음).
- 신규 unit test 4종 (false-positive 회귀) + 기존 7종 회귀 GREEN 유지.
- Journey 문서 동기화 (Observable step 3 + Known gaps annotate + Tests + Change log).

**Out:**
- 다국어 키워드 추가 (일본어/중국어) — `persistent-notification-protection` Known gap 첫 bullet, 별도 plan.
- 사용자가 키워드 set 을 편집하는 UI — 동일 journey Out-of-scope 에 명시.
- `ProtectedSourceNotificationDetector` 와의 통합 (Known gap 셋째 bullet) — 별도 plan.
- `cmd notification post` 의 ongoing flag 한계를 우회하는 ADB 헬퍼 — verification recipe 에 이미 알려진 환경 한계로 명시되어 있음.
- 영문 multi-word keyword (`"camera in use"`, `"screen record"`) 의 의미적 분리 — 본 plan 의 word-boundary 는 양 끝만 검증하므로 token 사이가 합성어 일부일 가능성은 미해결.

---

## Risks / open questions

- **단독 영단어 false-positive 잔존**: `"Maps Pro 출시"`, `"new camera in use feature"` 같은 케이스는 word boundary 로도 매치된다 (영단어 단독으로 분리되어 있음). 현실에서 마케팅 SMS 가 영단어 keyword 를 단독으로 쓰는 빈도가 얼마나 되는지 데이터 없음. 만약 사용자 신고가 누적되면 후속 plan 이 keyword set 을 한국어 위주로 좁히거나 의미적 분류 (예: title 에 "이벤트", "할인", "출시" 같은 마케팅 토큰이 있으면 bypass 무효) 를 추가해야 할 수 있다.
- **한글 word boundary 정의의 정확도**: 본 plan 은 "인접 문자가 한글/자모가 아니면 boundary" 로 정의. 그러나 한국어는 조사 ("을", "이") 가 토큰 끝에 붙는 교착어라, 예컨대 "통화를 시도했어요" 같은 본문에서 "통화" 매치 시 직후가 "를" (한글) → 무효. 의도와 일치하지만 — "통화" 단어를 보호하는 사례가 줄어들 가능성도 있다. 그러나 critical persistent 알림 (실제 통화 / 녹화) 의 system-emitted text 는 보통 짧고 단독 명사 또는 "통화 중", "녹화 중" 형태라 영향 적음. 사용자 신고 시 재검토.
- **합성어 keyword 추가 시의 의도 보존**: 향후 keyword set 에 "긴급통화" 같은 합성어 자체를 keyword 로 추가하면 — 본 plan 의 boundary helper 는 그것 자체를 단어로 본다. 의도가 "긴급통화" 라는 명확한 시그널일 때만 매치되므로 OK. 단, 합성어를 그냥 keyword 에 박는 게 의미론적으로 맞는지는 별도 검토 필요.
- **테스트 fragility**: 한글 boundary 가 `Character.UnicodeBlock` API 에 의존. JVM 버전 차이로 block 매핑이 달라질 가능성은 사실상 없음 (Android JVM 호환성 보장). 단, Kotlin/Native 또는 Multiplatform 으로 가면 재검토 필요 — 현재 SmartNoti 는 JVM 단독.
- **Helper 위치 결정**: private helper 로 두되, 향후 `ProtectedSourceNotificationDetector` 가 동일 매칭이 필요하다고 판단되면 그 plan 이 `KeywordWordBoundaryMatcher` 같은 top-level 로 추출. 본 plan 은 추출하지 않는다 (premature abstraction).
- **카피 변경 없음 / 사용자 노출 카피 무영향**: 본 plan 은 텍스트가 보이는 위치를 건드리지 않는다 — 사용자 관측 가능한 변화는 "예전엔 살아남던 광고 알림이 이제 사라짐" 의 행동 차이만. 카피 회귀 위험 없음.

---

## Related journey

- [persistent-notification-protection](../journeys/persistent-notification-protection.md) — Known gaps 둘째 bullet ("키워드 매치가 substring contains 기반이라 오탐 가능성") 을 해소. 본 PR ship 후 해당 bullet 을 (resolved …) 로 마킹하고 Change log 에 PR 링크 추가.
