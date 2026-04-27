---
status: planned
fixes: 488
---

# Fix #488: contentSignature normalizer for amount-only-差 repeat alerts

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P1 release-prep — 5-step gate (failing tests → impl → wiring → settings UI → ADB e2e on `R3CY2058DLJ`) is non-negotiable. The normalizer ships **opt-in default OFF** to bound the over-bundling risk; do not flip the default without user sign-off.

**Goal:** A SmartNoti user opting in to "비슷한 알림 묶기 (숫자 차이 무시)" sees high-frequency micro-amount notifications — the canonical fixture is 네이버페이 `[현장결제]` 포인트뽑기 (8/12/16/28원이 적립되었어요, 15회 in 3 minutes) — collapse into a single `repeat_bundle` DIGEST after the user-configured threshold. The base `duplicate-suppression` heuristic also fires more reliably for the same shape. With the toggle OFF (default), today's behavior is preserved exactly.

**Architecture:** The bug surfaces in `DuplicateNotificationPolicy.contentSignature` (currently lowercase + whitespace-collapse only). 네이버페이 fixtures share an identical template ("N원이 적립되었어요") but the embedded amount mutates per notification, so signatures hash uniquely and the rolling-window count never crosses 3 → `repeat_bundle:3` rule (issue body) and the base `duplicateDigestThreshold` heuristic both miss. Fix is a new `ContentSignatureNormalizer` (separate Kotlin class, pure-function unit-testable) that applies an **optional** post-lowercase pipeline: digit/currency token replacement (`8원`/`12원` → `<num>원`), bare-digit run replacement (`123` → `<num>`), and time-of-day token replacement (`23:47` → `<time>`). Wiring point is `NotificationDuplicateContextBuilder.build`, where the policy's signature is computed today; the builder reads a new `SmartNotiSettings.normalizeNumericTokensInSignature: Boolean = false` and threads it into a normalizer instance. UI surfaces the toggle in the existing `SettingsDuplicateSection` (중복 알림 묶기 row), under the two existing dropdowns.

Two notification-classification gaps from issue #488 are deliberately split:
- **Bug 2 (signature)** — this plan.
- **Bug 1 (resolver picks PRIORITY for `[현장결제]` body that hits both 결제/IMPORTANT and 적립·포인트/PROMO keywords)** is *out of scope* and tracked separately. See Risks for the rationale and the parallel bracket-marker extension hook.

**Tech Stack:** Kotlin (pure-function normalizer + builder wiring), Room (no schema change), DataStore (one new boolean key + sibling repository setter), Jetpack Compose (one Switch row in Settings), Gradle unit tests, ADB on real device `R3CY2058DLJ`. DiagnosticLogger (#480, just-shipped) provides the e2e evidence.

---

## PR title format: `fix(#488): <one-line>`
## PR body must include `Closes #488` + plan link + Task 5 ADB transcript

---

## Product intent / assumptions

- **Default OFF (safety-first).** The normalizer collapses meaningful amount differences (e.g. `100원 결제` vs `100000원 결제` both → `<num>원 결제`) into the same signature. If a user trusts SmartNoti enough to bundle "8원 적립" and "28원 적립" as equivalent, they will also accept the loss of distinction between "10원 결제" and "10만원 결제". This is a per-user judgment call → the toggle exists, but new installs and existing users see no behavior change until they opt in. **(User sign-off captured in the issue body: "기본값 OFF (정확도 우선)".)**
- **Toggle scope is the duplicate-burst signature only.** It does not change `NotificationEntity.contentSignature` storage semantics (still stored verbatim post-normalization), nor does it change rule-match keyword text. `repeat_bundle:N` rule fires off the duplicate-burst count which is signature-driven, so the toggle naturally feeds both code paths.
- **Normalizer order matters and is fixed in code.** Lowercase → whitespace-collapse (existing) → time-of-day (`\d{1,2}:\d{2}`) → currency-suffixed digits (`\d+(?:,\d{3})*(?:원|krw|\$|usd|￦)?` post-lowercase) → bare digit runs (`\d+`). Currency-suffix step runs before bare-digit so "8원" maps to `<num>원` (preserving the unit) rather than `<num>원`. We retain the unit suffix because hashing "결제" vs "적립" alone is too coarse — the unit token still carries semantic weight when present.
- **Configurable patterns are out of scope for v1.** The toggle is binary. A future plan may expose user-defined regex patterns (issue body suggests this); deferred behind UX risk.
- **No new journey doc.** This change extends the existing `duplicate-suppression` journey contract (Observable steps already mention "title/body 정규화"); the normalization step extends that bullet. The journey gets a Change log line on ship.
- **Bug 1 routing is left to the resolver path.** Even with Bug 1 unfixed, a successful Bug 2 fix demoted these alerts from "15 separate PRIORITY" to "1 PRIORITY + 14 bundled into a DIGEST roll-up" — i.e. the noise drops by 93% even though the first one still routes wrong. This makes Bug 2 release-prep-worthy on its own.

---

## Task 1: Failing tests for normalizer + duplicate-burst path (RED first)

**Objective:** Pin both the pure normalizer contract and the end-to-end duplicate-burst behavior in tests that **fail before the fix**.

**Files (new or extended):**

- `app/src/test/java/com/smartnoti/app/notification/ContentSignatureNormalizerTest.kt` (new) — pure-function regex matrix:
  - 5 네이버페이 포인트뽑기 fixtures with bodies "8원이 적립되었어요", "12원이 적립되었어요", "16원이 적립되었어요", "28원이 적립되었어요", "1,234원이 적립되었어요" and identical title `[현장결제]` → all five normalized signatures equal each other.
  - 1 negative fixture: same title, body `"포인트가 사라졌어요"` (no number) → signature is **different** from the bundle above.
  - 1 negative fixture: title `[현장결제]`, body `"5,000원이 결제되었습니다"` (different verb stem 결제 vs 적립) → signature differs.
  - Time-of-day matrix: bodies `"23:47에 알림"` and `"08:01에 알림"` collapse to the same signature; `"23일에 알림"` (no `:`) does not collapse with them.
  - Toggle-OFF fixture: when normalizer is constructed with `enabled = false`, the same bundle of 5 yields 5 distinct signatures (regression guard against accidental always-on).
  - Currency variants: `"$10 paid"` and `"$10,000 paid"` collapse (acknowledged over-bundling — this is the feature; the negative is documented in the test name).
- `app/src/test/java/com/smartnoti/app/domain/usecase/DuplicateNotificationPolicyNormalizerIntegrationTest.kt` (new) — wires `DuplicateNotificationPolicy` (or whichever integration shim Task 2/3 lands) with normalizer enabled, asserts that 5 네이버페이 포인트뽑기 inputs produce the same signature.
- `app/src/test/java/com/smartnoti/app/notification/NotificationDuplicateContextBuilderNormalizerTest.kt` (new) — integration through the builder seam: calling `build` 5 times with the same title and 5 amount-mutating bodies returns `duplicateCount == 5` (or `>= 3`, depending on tracker init in the test) when `settings.normalizeNumericTokensInSignature = true`, and `duplicateCount == 1` per call when `false`.
- `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationClassifierRepeatBundleNormalizerTest.kt` (new or extension of existing classifier test) — `repeat_bundle:3` rule fires for the 5-amount fixture batch when normalization is on; does not fire when off.
- `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryNormalizerToggleTest.kt` (new) — round-trip: `setNormalizeNumericTokensInSignature(true)` then read `SmartNotiSettings.normalizeNumericTokensInSignature` returns `true`; default is `false`.

**Steps:**

1. Define `ContentSignatureNormalizer` (가칭) contract — `class ContentSignatureNormalizer(private val enabled: Boolean) { fun normalize(signature: String): String }` where `enabled = false` returns the input unchanged. Pure-function: no settings reads, no I/O.
2. Encode the 네이버페이 fixture data as raw constants in the test (do **not** copy from the device DB into the codebase — re-derive from the issue body). Use the exact strings from the issue 488 table: `"8원이 적립되었어요"`, etc.
3. Tracker init in integration tests: pre-seed `LiveDuplicateCountTracker` with the equivalent of "previous N occurrences in the window" via the `persistedDuplicateCount` functional port so the test does not have to actually post 15 notifications.
4. Run `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.ContentSignatureNormalizerTest" --tests "com.smartnoti.app.domain.usecase.DuplicateNotificationPolicyNormalizerIntegrationTest" --tests "com.smartnoti.app.notification.NotificationDuplicateContextBuilderNormalizerTest" --tests "com.smartnoti.app.domain.usecase.NotificationClassifierRepeatBundleNormalizerTest" --tests "com.smartnoti.app.data.settings.SettingsRepositoryNormalizerToggleTest"` → all RED. Quote at least the normalizer-test failure string into the implementer's commit message and PR body as RED-evidence.

## Task 2: Implement `ContentSignatureNormalizer`

**Objective:** Task 1's `ContentSignatureNormalizerTest` GREEN.

**Files:**

- `app/src/main/java/com/smartnoti/app/notification/ContentSignatureNormalizer.kt` (new) — pure Kotlin class.

**Steps:**

1. Constructor: `class ContentSignatureNormalizer(private val enabled: Boolean)`. When `enabled=false`, `normalize(s) = s`.
2. Regex pipeline (apply in this fixed order on a non-null input that is **already** lowercased + whitespace-collapsed by the policy):
   - **Time of day:** `Regex("\\b\\d{1,2}:\\d{2}\\b") → "<time>"`.
   - **Currency-suffixed digits:** `Regex("\\d{1,3}(?:,\\d{3})+(?:원|krw|usd)?|\\d+(?:원|krw|usd)?|\\$\\d+(?:,\\d{3})*") → "<num>"`. The replacement intentionally drops the suffix to maximize bundling — alternative is `<num>원`; pick `<num>` per fixture-driven assertion.
   - **Bare digit runs:** `Regex("\\b\\d+\\b") → "<num>"`. Catches anything left over (sequence numbers, IDs).
3. No allocation per call beyond the regex output strings; the three `Regex` instances are class fields.
4. ReDoS guard: all three patterns are linear-time on input length. Input is whatever the caller passes (already title+body concatenation, max ~1KB in practice). No nested quantifiers.
5. No knowledge of language: the normalizer does not try to detect Korean vs English. The toggle is a global on/off.

## Task 3: Wire normalizer into `NotificationDuplicateContextBuilder`

**Objective:** Task 1's `DuplicateNotificationPolicyNormalizerIntegrationTest` and `NotificationDuplicateContextBuilderNormalizerTest` GREEN. The duplicate-burst path uses the normalized signature when the toggle is on.

**Files:**

- `app/src/main/java/com/smartnoti/app/notification/NotificationDuplicateContextBuilder.kt` — read `settings.normalizeNumericTokensInSignature`, build `ContentSignatureNormalizer(enabled = ...)`, apply `normalizer.normalize(...)` to the policy's `contentSignature(title, body)` output **before** the persistent suffix is appended.
- `app/src/main/java/com/smartnoti/app/notification/NotificationPipelineInputBuilder.kt` — no change to surface; the builder already returns `contentSignature` and that string flows through unchanged.

**Steps:**

1. Insert the normalizer between `policy.contentSignature(title, body)` and the persistent-suffix concat. The persistent suffix already encodes per-notification identity, so do not normalize the suffix.
2. Construct the normalizer per call (cheap — three `Regex` fields). Alternative is to inject it; defer DI until profiling shows allocation pressure.
3. Confirm `NotificationDao.countRecentDuplicates(packageName, contentSignature, sinceMillis)` keeps using the same (now-normalized) string — no schema change needed because the column stores whatever signature the listener computed at write time. Toggling on mid-life means new rows match other new rows; old rows remain un-matchable. **This is acceptable** (rolling window expires them) but document in the toggle's helper text.
4. Re-run Tasks 1's tests — RED → GREEN.

## Task 4: Settings UI toggle + repository setter + migration-free wiring

**Objective:** `SettingsRepositoryNormalizerToggleTest` GREEN. The Settings 중복 알림 묶기 row gains a Switch labelled "비슷한 알림 묶기 (숫자 차이 무시)" with helper copy explaining over-bundling risk.

**Files:**

- `app/src/main/java/com/smartnoti/app/data/settings/SmartNotiSettings.kt` — add `val normalizeNumericTokensInSignature: Boolean = false` with a multi-line comment citing this plan + the user-judgment rationale.
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsDuplicateRepository.kt` — add `Keys.NORMALIZE_NUMERIC_TOKENS = booleanPreferencesKey("duplicate_normalize_numeric_tokens")` + `suspend fun setNormalizeNumericTokens(enabled: Boolean)`.
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — wire the new key into the flow read + add a facade `suspend fun setNormalizeNumericTokensInSignature(enabled: Boolean) = duplicate.setNormalizeNumericTokens(enabled)`. **No `applyPendingMigrations` entry** — default `false` matches "never set" so existing installs naturally land on the safe value.
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsDuplicateSection.kt` — append a `Switch`-bearing row inside the existing `Column` after the two pickers. Helper Text: "켜면 ‘8원/28원 적립’처럼 숫자만 다른 알림을 같은 알림으로 인식해 묶음 처리해요. 결제 금액 차이도 같이 묶일 수 있어요." (calm tone per `ui-improvement.md`).
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` (or whichever ViewModel feeds the section) — pass through the new value + onChange handler. Pattern matches the existing two dropdowns in the same row.
- (optional) `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsDuplicateSectionTest.kt` (extend if exists, else skip) — Compose snapshot or visibility test.

**Steps:**

1. DataStore key only — no migration. Default behavior is unchanged.
2. UI label string lives inline (matches the existing `SettingsDuplicateSection` literal-string pattern). If the codebase has a strings.xml convention for this section, follow it; cursory grep shows literals are used today.
3. Toggle visibility: always shown inside the "중복 알림 묶기" row (no gating on the duplicate-burst master). The two existing pickers are already visible there; this is a peer.
4. Wire ViewModel test if convenient; not blocking.

## Task 5: Real-device ADB e2e on `R3CY2058DLJ` (P1 release-prep gate)

**Objective:** Reproduce the issue 488 scenario on the user's real device with the toggle ON, verify `repeat_bundle:3` fires and the 4th–15th 포인트뽑기 alerts collapse into a single DIGEST. With the toggle OFF, verify the bug is **still reproducible** (regression guard for the safety default).

**Steps:**

```bash
# 0. Verify device + fresh debug APK
adb devices | grep R3CY2058DLJ
./gradlew :app:assembleDebug
adb -s R3CY2058DLJ install -r app/build/outputs/apk/debug/app-debug.apk

# 1. Toggle OFF baseline — confirm bug still reproduces (default branch)
adb -s R3CY2058DLJ shell pm clear com.smartnoti.app
# (manual: complete onboarding, do NOT toggle the new switch)
for amt in 8 12 16 28 100 200 300 400 500 600 700 800 900 1000 1100; do
  adb -s R3CY2058DLJ shell cmd notification post -S bigtext \
    -t "[현장결제]" "PointTest$amt" "${amt}원이 적립되었어요"
done
adb -s R3CY2058DLJ shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT title, body, status FROM notifications WHERE body LIKE '%적립되었어요' \
   ORDER BY postedAtMillis DESC LIMIT 15;"
# expected: 15 distinct rows, all status=PRIORITY (or CAPTURED) — bug intact, default safe

# 2. Toggle ON — open Settings → 중복 알림 묶기 → enable "비슷한 알림 묶기 (숫자 차이 무시)"
# Then re-run the same loop:
for amt in 8 12 16 28 100 200 300 400 500 600 700 800 900 1000 1100; do
  adb -s R3CY2058DLJ shell cmd notification post -S bigtext \
    -t "[현장결제]" "PointTest$amt" "${amt}원이 적립되었어요"
done
adb -s R3CY2058DLJ shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT title, body, status FROM notifications WHERE body LIKE '%적립되었어요' \
   ORDER BY postedAtMillis DESC LIMIT 15;"
# expected: 1st-2nd row PRIORITY (below threshold), 3rd onward DIGEST.
# (Bug 1 means PRIORITY routing is still wrong for the first two; that's #488 Bug 1 scope, NOT this plan.)

# 3. DiagnosticLogger evidence (#480)
adb -s R3CY2058DLJ shell run-as com.smartnoti.app cat files/diagnostic.log | grep -E "signature|repeat_bundle|적립"
# expected: lines showing identical normalized contentSignature for all 15 inputs

# 4. SmartNoti 정리함 verification — 1 bundled DIGEST card visible, not 13 separate cards
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s R3CY2058DLJ pull /sdcard/ui.xml /tmp/ui.xml
grep -oE '적립[^"]*' /tmp/ui.xml
# expected: <= 2 matches (digest card title + maybe a count badge)
```

If any expected condition fails: plan-implementer stops and reports — do not patch over it. If the toggle-OFF baseline does NOT reproduce the bug, the test fixture has drifted and the issue's premise is re-questioned.

## Task 6: Bump `duplicate-suppression` journey + Change log

**Objective:** Document the new normalizer step in the journey contract.

**Files:**

- `docs/journeys/duplicate-suppression.md`

**Steps:**

1. Frontmatter `last-verified: <date -u +%Y-%m-%d>` (use system clock, not model context — `.claude/rules/clock-discipline.md`).
2. Observable steps: extend step 1 to mention "if `settings.normalizeNumericTokensInSignature` is on, the signature additionally collapses digit/currency/time tokens via `ContentSignatureNormalizer`". Keep the existing copy intact otherwise.
3. Code pointers: add `notification/ContentSignatureNormalizer` line.
4. Change log entry: `YYYY-MM-DD: fix #488 — opt-in signature normalizer for amount-only-差 알림 (e.g. 네이버페이 포인트뽑기). New SmartNotiSettings.normalizeNumericTokensInSignature (default OFF). UI: SettingsDuplicateSection 의 새 Switch. Plan: docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md.`
5. Known gaps: leave existing two bullets in place. Add a new bullet acknowledging Bug 1 (resolver picks PRIORITY for 결제+적립 keyword overlap) is still open and is tracked as a separate issue (file the issue if it does not exist; reference it).

---

## Scope

**In:**

- `ContentSignatureNormalizer` new pure class + tests.
- `NotificationDuplicateContextBuilder` wiring change (single insertion site).
- `SmartNotiSettings.normalizeNumericTokensInSignature` field + `SettingsDuplicateRepository` key/setter + `SettingsRepository` facade method.
- `SettingsDuplicateSection` Switch row + ViewModel/Screen plumbing.
- `duplicate-suppression` journey `last-verified` + Change log + Code pointers + new Known-gap bullet for Bug 1.

**Out:**

- **Bug 1 (resolver promotes 결제+적립 overlap to PRIORITY)** — separate issue/plan. The plan's Risks section explains why splitting is the right call.
- Extending `KoreanAdvertisingPrefixDetector` (PR #489) to recognize `[현장결제]` / `[알림]` / `[혜택]` as promo bracket markers — this is a Bug 1 fix angle and belongs in the Bug 1 plan.
- User-defined regex patterns in Settings (issue body suggests; deferred for UX safety review).
- Storage-format change for `NotificationEntity.contentSignature` (the toggle changes what gets written but not the column type or rolling-window semantics).
- Per-rule normalizer override (every rule shares the global toggle for v1).
- i18n / locale-specific currency formatting (the regex covers 원/krw/usd/$ which matches the fixtures; add more in a future plan if the user reports another currency).

---

## Risks / open questions

- **Over-bundling.** The chosen design intentionally collapses `100원 결제` and `100,000원 결제` into the same signature. A user who turns this on to silence 포인트 적립 noise might also lose distinction between meaningfully-different payment alerts. **Mitigation:** default OFF + helper text in the Settings toggle that explicitly names this trade-off ("결제 금액 차이도 같이 묶일 수 있어요"). **Open question deferred to user:** is this trade-off acceptable as a binary switch, or do we need a per-Category opt-in (much larger scope)? Current plan says binary.
- **Bug 1 left open.** The 첫 한두 건이 여전히 PRIORITY 로 가는 점이 사용자 신고의 부분이라면, Bug 2 fix 만으로는 100% 만족스럽지 않을 수 있음. **Open question for the user (raise in PR body):** "Bug 2 fix 후 첫 2건의 PRIORITY 라우팅 (Bug 1) 도 같은 release 에 포함할까, 아니면 별도 PR 로 follow-up?" Recommendation in this plan: split. Reasoning: Bug 1 needs a different surface (resolver / prefix detector extension) and a different test matrix; cramming both into one PR jeopardizes the 5-step gate timeline. The 93% noise reduction from Bug 2 alone is materially shipping-worthy.
- **Mid-life toggle.** Turning the toggle ON does not retroactively re-normalize signatures of rows already written. A user could see "I turned it on but the next 10 minutes still show separate cards" (the rolling window expires the old un-matchable rows). **Mitigation:** helper text mentions "곧 들어올 알림부터 적용". Implementer surfaces this in the journey Known gaps if it's user-confusing in practice.
- **Regex correctness on edge fixtures.** `\d{1,2}:\d{2}` collides with bare `8:30` time-of-day intent but also with non-time `8:30 AM 진행 중` (still time, fine) and `key:val` config-style strings (rare in notifications, low risk). The `\b` word-boundaries should keep currency-only fixtures unaffected, but the test matrix in Task 1 must include at least one negative case for each pipeline step to lock the boundary.
- **`NotificationEntity.contentSignature` storage drift.** Column stores whatever the listener computed at write time. Toggling on creates a partial-population gap; toggling off later means new rows are un-normalized while the rolling window holds normalized rows. We accept this — the rolling window is short (default 10 min) so divergence is bounded.
- **Real device dependency.** Task 5 needs `R3CY2058DLJ`. Plan-implementer reports immediately if the device is unreachable instead of substituting an emulator (the bug's premise is real-app fixtures; emulator simulation via `cmd notification post` is sufficient for the reproduction shape but the user's release-prep gate explicitly names this device).
- **Clock discipline.** Task 6's `last-verified` and Change log dates **must** come from `date -u +%Y-%m-%d`, not the model's notion of today (`.claude/rules/clock-discipline.md`).

---

## Related journey

- [`docs/journeys/duplicate-suppression.md`](../journeys/duplicate-suppression.md) — Owner of the duplicate-burst signature contract. This plan extends Observable steps + Code pointers + Change log; no new journey doc.
- Cross-link only: [`docs/journeys/notification-capture-classify.md`](../journeys/notification-capture-classify.md) — feeds the listener that calls `NotificationDuplicateContextBuilder`. No edit needed here unless implementer notices a drift while wiring.
