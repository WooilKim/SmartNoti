# SmartNoti Onboarding Quick Start Recommendations Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add a second onboarding step that lets users enable high-value starter rules and clearly see how their notification experience will change before they begin using the app.

**Architecture:** Keep onboarding on the existing `Routes.Onboarding` route, but evolve `OnboardingScreen` from a single permission gate into a two-step flow: (1) permission gate and (2) quick-start recommendations. Keep recommendation content, impact preview copy, and rule-application logic outside Compose in pure builders/appliers so the screen remains simple and the behavior is unit-testable. Reuse `RulesRepository` and `RuleDraftFactory` so selected recommendations become real persistent rules rather than demo-only UI state.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, existing `RulesRepository`, existing `SettingsRepository`, JVM unit tests via Gradle.

---

## Product intent

The onboarding should stop feeling like “grant permissions and hope for the best.”

The quick-start step should immediately communicate:
1. **What will get quieter**
2. **What will still come through**
3. **Why SmartNoti is useful across multiple apps at once**

The most important UX requirement is the **expected change preview**. Users should understand the result in plain language before accepting the presets.

---

## Proposed UX

### Step 1 — Permission gate
Keep the current permission step, but change the CTA behavior:
- when all requirements are met, advance to the quick-start step
- do **not** complete onboarding immediately

### Step 2 — Quick start recommendations
Header copy:
- Title: `먼저 이런 알림부터 정리해볼까요?`
- Body: `앱마다 따로 설정하지 않아도 여러 앱에 한 번에 적용돼요. 무엇이 조용해지고, 무엇은 그대로 보이는지 바로 확인할 수 있어요.`

Recommended preset cards:
1. `프로모션 알림 조용히`
2. `반복 알림 묶기`
3. `중요한 알림은 바로 전달`

Each card should show:
- title
- one-line value description
- reduced examples (`조용해지는 알림` / `줄어드는 알림`)
- preserved examples (`그대로 보이는 알림` / `바로 보이는 알림`)
- a short footer explaining the outcome
- default enabled state

Bottom summary:
- `쿠폰·세일 같은 알림은 덜 방해되게 정리하고, 결제·배송·인증은 바로 보여드릴게요`

Primary CTA:
- `이대로 시작할게요`

Secondary CTA:
- `직접 설정할게요`

Footnote:
- `선택한 추천은 나중에 Rules에서 언제든 바꿀 수 있어요`

---

## Data/model direction

### Recommendation 1 — Promo quieting
Use rule presets that bias toward quieter handling without hiding critical transactional events.

Suggested first implementation:
- keyword rule, action `DIGEST`
- match values: `광고,프로모션,쿠폰,세일,특가,이벤트,혜택`
- title: `프로모션 알림`

Important note:
- Start with `DIGEST`, not `SILENT`, to build trust in onboarding.
- The “important alerts remain visible” promise is supported by a separate priority preset, not by adding complex negative matching to the first version.

### Recommendation 2 — Repeat bundling
- repeat bundle rule, action `DIGEST`
- threshold: `3`
- title: `반복 알림`

### Recommendation 3 — Important alerts immediately
- keyword rule, action `ALWAYS_PRIORITY`
- match values: `인증번호,결제,배송,출발`
- title: `중요 알림`

---

## Files to modify or create

### Existing files likely to modify
- Modify: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`

### New domain/UI helper files
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartModels.kt`
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartPresetBuilder.kt`
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSelectionSummaryBuilder.kt`
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartRuleApplier.kt`

### New tests
- Create: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartPresetBuilderTest.kt`
- Create: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSelectionSummaryBuilderTest.kt`
- Create: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartRuleApplierTest.kt`

---

## Task 1: Add quick-start preset UI models

**Objective:** Define the pure UI-ready model for recommendation cards and their impact preview.

**Files:**
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartModels.kt`
- Test: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartPresetBuilderTest.kt`

**Step 1: Write failing test**
Add a test that expects a preset model to contain:
- id
- title
- description
- defaultEnabled
- reduced examples
- preserved examples
- footer text

Also assert the promo preset uses preview language that clearly separates quieter alerts from preserved alerts.

**Step 2: Run test to verify failure**
Run:
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'; \
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'; \
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'; \
export PATH="$JAVA_HOME/bin:$PATH"; \
./gradlew testDebugUnitTest --tests 'com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartPresetBuilderTest'
```
Expected: FAIL because the model/builder does not exist yet.

**Step 3: Write minimal implementation**
Create compact data classes such as:
- `OnboardingQuickStartPresetUiModel`
- `OnboardingQuickStartImpactSection`

Keep them presentation-focused and independent from Compose.

**Step 4: Run test to verify pass**
Run the same targeted test.

---

## Task 2: Build the default recommendation presets

**Objective:** Produce the three recommended starter cards as pure, testable data.

**Files:**
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartPresetBuilder.kt`
- Test: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartPresetBuilderTest.kt`

**Step 1: Write failing test**
Add tests that assert:
- exactly 3 presets are returned
- order is promo quieting -> repeat bundling -> important priority
- promo card contains examples like `(광고) 오늘만 특가` and preserved examples like `결제가 완료됐어요`
- repeat card uses before/after-style explanatory text
- important card emphasizes immediate delivery

**Step 2: Run test to verify failure**
Run the targeted test command from Task 1.
Expected: FAIL because the builder is incomplete.

**Step 3: Write minimal implementation**
Implement `OnboardingQuickStartPresetBuilder.buildDefaultPresets()` returning static first-pass content.

**Step 4: Run test to verify pass**
Run the targeted test again.

---

## Task 3: Add summary builder for the bottom CTA explanation

**Objective:** Turn the current selected presets into one short “what will change” summary sentence.

**Files:**
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSelectionSummaryBuilder.kt`
- Test: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSelectionSummaryBuilderTest.kt`

**Step 1: Write failing test**
Add tests for:
- all three selected -> summary mentions quieter promo alerts + preserved important alerts
- only repeat selected -> summary focuses on repeated notification grouping
- none selected -> fallback summary like `필요한 규칙만 나중에 직접 고를 수 있어요`

**Step 2: Run test to verify failure**
Run:
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'; \
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'; \
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'; \
export PATH="$JAVA_HOME/bin:$PATH"; \
./gradlew testDebugUnitTest --tests 'com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartSelectionSummaryBuilderTest'
```
Expected: FAIL.

**Step 3: Write minimal implementation**
Keep the builder pure. Accept selected preset ids or selected preset models and output one sentence.

**Step 4: Run test to verify pass**
Re-run the targeted test.

---

## Task 4: Add rule applier that converts selections into persistent rules

**Objective:** Convert selected quick-start presets into real `RuleUiModel`s and persist them through `RulesRepository`.

**Files:**
- Create: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartRuleApplier.kt`
- Modify: `app/src/main/java/com/smartnoti/app/domain/usecase/RuleDraftFactory.kt` only if a small helper extraction makes the preset mapping cleaner
- Test: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartRuleApplierTest.kt`

**Step 1: Write failing test**
Add tests that assert:
- selecting promo creates a keyword digest rule with promo keywords
- selecting repeat creates a repeat-bundle digest rule with threshold `3`
- selecting important creates a keyword priority rule with important keywords
- applying no presets produces an empty rule list

**Step 2: Run test to verify failure**
Run:
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'; \
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'; \
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'; \
export PATH="$JAVA_HOME/bin:$PATH"; \
./gradlew testDebugUnitTest --tests 'com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartRuleApplierTest'
```
Expected: FAIL.

**Step 3: Write minimal implementation**
Implement an applier that:
- maps preset ids to `RuleDraftFactory.create(...)`
- returns `List<RuleUiModel>`
- leaves persistence itself to the caller or provides a suspend helper using `RulesRepository`

Prefer deterministic ids from `RuleDraftFactory` rather than custom onboarding-specific ids.

**Step 4: Run test to verify pass**
Re-run the targeted test.

---

## Task 5: Change onboarding flow from 1 step to 2 steps

**Objective:** Prevent onboarding from completing immediately after permissions are granted.

**Files:**
- Modify: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`

**Step 1: Refactor `OnboardingScreen` API**
Change the screen contract so it can differentiate between:
- advancing from permissions to quick-start
- finishing onboarding after quick-start

A clean option is:
- keep one `onCompleted` callback
- manage the local step state entirely inside `OnboardingScreen`
- only call `onCompleted()` after the quick-start CTA is confirmed

**Step 2: Implement local onboarding step state**
Inside `OnboardingScreen`, add a small local enum/state such as:
- `PERMISSIONS`
- `QUICK_START`

Behavior:
- start at `PERMISSIONS`
- when all requirements are met and user taps the current CTA, advance to `QUICK_START`
- once on `QUICK_START`, apply selected presets then call `onCompleted`

**Step 3: Keep AppNavHost completion wiring unchanged if possible**
If `OnboardingScreen` still only emits a final `onCompleted`, `AppNavHost.kt` can stay nearly unchanged apart from imports/signature alignment.

**Step 4: Manual verification**
Confirm the flow is:
- app start -> permissions screen
- after permissions -> quick-start screen
- after confirmation -> `settings.setOnboardingCompleted(true)` and navigate to Home

---

## Task 6: Implement the quick-start Compose UI

**Objective:** Render the recommendation cards, impact previews, summary line, and final CTA in a way that feels trustworthy and easy to scan.

**Files:**
- Modify: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingScreen.kt`

**Step 1: Reuse the existing dark onboarding visual language**
Do not introduce a second unrelated style. Keep the same dark-first, calm, trustworthy treatment.

**Step 2: Add the quick-start section hierarchy**
Recommended structure:
- title
- explanation text
- three small chips/badges near the top
- recommendation cards
- bottom summary sentence
- primary CTA + secondary action

**Step 3: Make each card communicate expected change clearly**
For each card show:
- title
- one-line explanation
- one section for quieter/reduced alerts
- one section for preserved/immediate alerts
- footer text
- toggle or selectable card state

**Step 4: Keep copy stable and product-safe**
Avoid “차단” or “완전 숨김” phrasing. Prefer:
- `조용히 정리해요`
- `모아 보여줘요`
- `바로 보여줘요`

---

## Task 7: Persist selected presets during completion

**Objective:** When the user taps the final CTA, save the chosen quick-start rules before onboarding completion.

**Files:**
- Modify: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` only if callback shape changes

**Step 1: Add repository access inside onboarding**
Use:
- `RulesRepository.getInstance(context)`
- `RuleDraftFactory()`
- `OnboardingQuickStartRuleApplier()`

**Step 2: Persist selected rules before completion**
On final CTA:
1. convert selected presets into rules
2. `upsertRule(...)` for each selected rule
3. then trigger final onboarding completion

**Step 3: Preserve “skip customization” behavior safely**
If the user chooses the secondary action:
- complete onboarding without adding preset rules
- or, if product decides later, keep the current default rules only

The first implementation should prefer clarity over hidden automatic behavior.

---

## Task 8: Run verification and document results

**Objective:** Verify both the new builders and the existing unit suite.

**Files:**
- No code changes required unless test failures expose follow-up fixes

**Step 1: Run targeted onboarding tests**
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'; \
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'; \
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'; \
export PATH="$JAVA_HOME/bin:$PATH"; \
./gradlew testDebugUnitTest --tests 'com.smartnoti.app.ui.screens.onboarding.*'
```
Expected: PASS.

**Step 2: Run full unit test suite**
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'; \
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'; \
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'; \
export PATH="$JAVA_HOME/bin:$PATH"; \
./gradlew testDebugUnitTest
```
Expected: PASS.

**Step 3: Manual device verification**
Check:
- onboarding still blocks entry before permissions
- after permissions, quick-start recommendations appear
- the preview language is understandable without opening Rules
- after choosing presets, matching rules appear in the Rules screen
- onboarding completes only after quick-start confirmation or explicit skip

---

## Acceptance criteria

- Onboarding no longer ends immediately after permissions are granted
- Users see a quick-start recommendation step before entering the app
- Each recommendation clearly shows what changes and what stays visible
- Selected recommendations create real persistent rules in `RulesRepository`
- Important alerts remain explicitly positioned as preserved/immediate in onboarding copy
- New pure builders/appliers are covered by unit tests
- `./gradlew testDebugUnitTest` passes

---

## Notes and non-goals for the first pass

### Keep first pass intentionally simple
- Do not build per-app smart suggestions yet
- Do not add captured-notification analytics into onboarding yet
- Do not redesign onboarding navigation into multiple routes unless the local-step version becomes unmanageable

### Good future follow-ups after this plan
- use captured real-device notifications to suggest top noisy apps after a day of use
- evolve promo quieting from broad keywords into more nuanced app/context-aware logic
- let users preview the exact rules that will be created before confirming
