---
status: shipped
superseded-by: docs/journeys/digest-suppression.md
---

# digest-suppression Verification Recipe via `com.smartnoti.testnotifier`

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `journey-tester` 가 `digest-suppression` journey 의 Verification recipe 를 돌릴 때 `com.android.shell` ranker-group 의 NMS per-package 50건 quota 에 걸려 연속해서 SKIP 되는 현상을 끝낸다. Recipe 를 `com.smartnoti.testnotifier` 의 `PROMO_DIGEST` 시나리오 경유로 재작성하면, shell 과 별개의 quota budget 을 쓰기 때문에 누적 stale 알림 없이 깔끔하게 원본 → replacement 교체를 관측할 수 있다. 사용자가 관측하는 앱 동작은 바뀌지 않는다 (SmartNoti 코드/설정 변경 없음).

**Architecture:** 변경 범위는 `docs/journeys/digest-suppression.md` 의 Verification recipe 섹션 한 곳 + Known gaps 의 2026-04-22 bullet 옆에 plan 링크 annotation 한 줄만. SmartNoti 코드는 touch 하지 않는다. Recipe 는 `com.smartnoti.testnotifier` APK (이미 `/Users/wooil/source/SmartNotiTestNotifier` 에 존재, `PROMO_DIGEST` 시나리오가 이미 "쿠폰·세일·이벤트 키워드" 를 포함한 DIGEST candidate payload 를 발사함) 를 설치하고 `MainActivity` 에서 `PROMO_DIGEST` 시나리오를 실행하거나 동등한 `am broadcast` / `am start` 경로로 notify 를 트리거하는 형태로 재작성한다. 각 sweep 직전에 `cmd notification clear com.smartnoti.testnotifier` 로 테스트 앱의 tray 만 선제 정리해 quota 누적을 0 에서 출발시킨다.

**Tech Stack:** Bash ADB recipe, journey markdown, `com.smartnoti.testnotifier` APK (build 은 별도 repo 에서 이미 되어 있음).

---

## Product intent / assumptions

- 이 plan 은 **recipe-hardening 전용** — SmartNoti 측에는 어떤 코드/설정 변화도 없다. 제품 동작 (A+C 하이브리드 default-ON + empty-set opt-out 의미) 은 이미 #292 로 shipped 되어 PR #292 ADB Scenario A 로 PASS 가 확인된 상태. 누락된 것은 오직 journey-tester 가 이 경로를 live 로 재검증할 수 있는 recipe 뿐.
- `com.smartnoti.testnotifier` 가 real third-party app 관점에서 notification 을 posting 하므로 `com.android.shell` ranker-group 의 quota saturation 문제와 완전히 분리된다. 이것이 plan #272 의 `FORCE_STATUS` 디버그 hook 과 구별되는 핵심 — 272 는 classifier 결과를 pin 하기 위한 debug-only 경로였고, 이번은 **real-app originator 교체**로 production 분류 경로를 그대로 관찰하는 접근이다.
- `PROMO_DIGEST` 시나리오의 기본 payload ("오늘만 특가 안내" + "멤버십 쿠폰…이번 주말 세일 혜택") 가 onboarding 의 PROMO_QUIETING 프리셋이 생성하는 KEYWORD → DIGEST 룰 ("쿠폰", "세일", "이벤트") 에 의해 DIGEST 로 분류될 것으로 기대됨. Recipe 선결조건에 "onboarding 이 PROMO_QUIETING 프리셋 포함 상태로 완료되어 있을 것" 을 명시한다.
- Recipe 는 emulator 에 testnotifier APK 가 설치되어 있지 않을 때를 대비해 **install 단계를 recipe 에 포함** 한다. 별도 repo 이므로 경로는 절대 경로로 `/Users/wooil/source/SmartNotiTestNotifier/app/build/outputs/apk/debug/app-debug.apk` 를 참조하되, 없으면 `cd /Users/wooil/source/SmartNotiTestNotifier && ./gradlew :app:assembleDebug` 로 빌드하라는 fallback 문구를 함께 남긴다.
- DIGEST 확정을 위해 가장 결정적인 UI 시그널은 `dumpsys notification --noredact | grep smartnoti_replacement_digest` — 원본 testnotifier 알림이 사라지고 SmartNoti replacement channel 만 남는지 한 줄로 판정 가능.
- **Open question for user (Risks 섹션):** testnotifier 의 `MainActivity` UI 를 ADB 로 click 없이 트리거하려면 별도 scenario-launch Intent 가 필요한지, 아니면 `am start -n com.smartnoti.testnotifier/.MainActivity` 로 들어가 사람이 버튼을 탭해야 하는지. 후자는 tester agent 의 unattended 성격과 맞지 않음 — R3 (testnotifier 쪽에 headless scenario broadcast 추가) 을 후속 plan 으로 따로 제안할지 여부.

---

## Task 1: Capture the current recipe baseline + draft replacement

**Objective:** 변경 전후를 PR 본문에서 대비해 보여줄 수 있게, 현재 recipe 전문과 새 recipe 전문을 한 곳에 준비한다.

**Files:**
- `docs/journeys/digest-suppression.md` — Verification recipe 섹션 (line 71–87 근방).

**Steps:**
1. 현재 recipe 전문을 복사해 PR 본문의 "Before" blockquote 에 넣는다.
2. 새 recipe 초안을 작성 (아래 Task 2 에 수록). 파일 수정은 Task 2 에서.
3. `com.smartnoti.testnotifier` 가 설치되어 있지 않을 때를 대비한 install fallback step 을 recipe 상단에 포함.

## Task 2: Rewrite Verification recipe in `digest-suppression.md`

**Objective:** recipe 를 testnotifier 경유로 재작성. 코드 변경 없음.

**Files:**
- `docs/journeys/digest-suppression.md` — Verification recipe 코드 블록 + 앞뒤 한두 줄의 설명 문장만 수정.

**Steps:**
1. 기존 recipe 의 "(1) Settings 토글 ON → 앱 체크" 선결 문구는 유지하되, "※ cmd notification 은 com.android.shell 패키지로 게시되므로 테스트에 적합" 주석을 제거하고 **"※ testnotifier (`com.smartnoti.testnotifier`) 로 게시하면 shell ranker-group 의 per-package quota 와 분리되어 누적 stale 알림 걱정 없이 검증 가능"** 로 교체.
2. 새 recipe step 제안 (구현 시 미세 조정 허용):
   ```bash
   # 0. 선결: SmartNoti debug APK 설치 + onboarding 완료 (PROMO_QUIETING 프리셋 선택).
   # 1. (최초 1회) testnotifier APK 빌드 + 설치
   TESTNOTI=/Users/wooil/source/SmartNotiTestNotifier
   [ -f "$TESTNOTI/app/build/outputs/apk/debug/app-debug.apk" ] \
     || (cd "$TESTNOTI" && ./gradlew :app:assembleDebug)
   adb -s emulator-5554 install -r "$TESTNOTI/app/build/outputs/apk/debug/app-debug.apk"

   # 2. sweep 직전 quota 초기화 (testnotifier 쪽만 clear — SmartNoti 측은 건드리지 않음)
   adb -s emulator-5554 shell cmd notification clear com.smartnoti.testnotifier 2>/dev/null || true

   # 3. MainActivity 진입 후 PROMO_DIGEST 시나리오 실행
   #    (현재 MainActivity 는 버튼 탭이 필요. headless 실행을 위한 추가 entry 가 필요하면
   #    후속 plan R3 으로 분기)
   adb -s emulator-5554 shell am start -n com.smartnoti.testnotifier/.MainActivity
   # 사용자 (또는 UI automator) 가 "프로모션 알림 1건 보내기" 탭
   # 또는 headless hook 이 추가되면:
   #   adb -s emulator-5554 shell am broadcast \
   #     -n com.smartnoti.testnotifier/.notification.ScenarioBroadcastReceiver \
   #     --es scenario PROMO_DIGEST

   # 4. 원본이 tray 에서 제거되고 SmartNoti replacement 가 게시됐는지 확인
   adb -s emulator-5554 shell dumpsys notification --noredact \
     | grep -E "pkg=com.smartnoti.(testnotifier|app)|smartnoti_replacement_digest" \
     | head
   # 기대: com.smartnoti.testnotifier 의 "오늘만 특가 안내" 는 tray 에 없고,
   #       com.smartnoti.app 의 smartnoti_replacement_digest_* 채널 entry 만 남음.

   # 5. replacement 의 "Digest 로 유지" 액션 탭 → Rules 탭에 com.smartnoti.testnotifier 룰 추가 확인
   ```
3. recipe 마지막에 "누적 quota 문제로 SKIP 하지 않는다" 한 줄 주석 추가.

## Task 3: Annotate Known-gap bullet with plan link

**Objective:** Known gap 의 원문은 유지한 채 이 plan 을 참조 링크로 덧붙여, 다음 tester / PM 이 진행 중임을 인지하게 한다.

**Files:**
- `docs/journeys/digest-suppression.md` — Known gaps 의 2026-04-22 bullet (line 92).

**Steps:**
1. 해당 bullet 의 본문은 한 글자도 바꾸지 않는다.
2. bullet 바로 아래에 `  → plan: docs/plans/2026-04-22-digest-suppression-testnotifier-recipe.md` 한 줄만 추가.
3. 다른 Known gap (auto-expansion sticky 제외 항목) 은 건드리지 않는다.

## Task 4: Dry-run the new recipe on emulator-5554

**Objective:** recipe 를 실제로 돌려서 SKIP 없이 PASS 가 나오는지 확인하고 PR 본문에 evidence 를 첨부한다. (구현 agent 가 수행; gap-planner 는 수행 안 함.)

**Steps:**
```bash
# 환경 변수 세팅
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

# 위 Task 2 의 step 1~4 를 순서대로 실행. step 3 의 UI 탭은
#   adb shell input tap <x> <y>  또는  uiautomator 로 대체 가능 (좌표는 실행 시 확인).
# step 4 의 grep 결과가 기대와 일치하면 recipe PASS 로 간주.
```
`dumpsys` 출력을 PR 본문에 fenced block 으로 첨부.

## Task 5: Journey Change log + last-verified bump on PASS

**Objective:** Drift 가 recipe-hardening 으로 drainage 되었음을 journey 문서에 반영.

**Files:**
- `docs/journeys/digest-suppression.md` — Change log 에 `2026-04-22: Verification recipe 를 com.smartnoti.testnotifier PROMO_DIGEST 경유로 재작성 (shell quota saturation 회피). Plan: docs/plans/2026-04-22-digest-suppression-testnotifier-recipe.md` entry 추가.
- PASS 가 확인되면 frontmatter `last-verified` 를 실행 날짜로 bump. SKIP/FAIL 이면 그대로 두고 PR 본문에 원인 기록.
- Known gaps 의 2026-04-22 bullet 은 Change log 로 이동 (Known gaps 섹션에서 제거).

## Task 6: Self-review + PR

- PR 제목: `docs(journeys): rewrite digest-suppression recipe via com.smartnoti.testnotifier`.
- PR 본문: Before/After recipe diff + emulator run evidence (dumpsys grep 결과) + link to this plan.
- PR 라벨: docs-only. `project-manager` 의 docs-only self-merge lane 에 올라갈 수 있음.

---

## Scope

**In:**
- `docs/journeys/digest-suppression.md` 의 Verification recipe 섹션 재작성.
- 같은 파일의 Known gaps 2026-04-22 bullet 옆에 plan 링크 annotation 한 줄.
- Change log + last-verified 갱신 (PASS 시).
- 이 plan 파일 자체.

**Out:**
- SmartNoti 측 코드/설정 변경 (product 동작은 #292 로 이미 fixed).
- testnotifier APK 에 새로운 scenario 나 broadcast receiver 추가 (→ 별도 plan R3 후보).
- `com.smartnoti.debug.FORCE_STATUS` 같은 debug-inject marker 추가 (plan #272 와 동일 패턴이 필요하다면 별도 plan R2).
- 다른 journey 의 recipe (priority-inbox 는 #273 에서 해결, home-uncategorized-prompt / silent-auto-hide 는 별도).
- auto-expansion 의 "sticky 제외" 리스트 (기존 Known gap 유지).

---

## Risks / open questions

- **testnotifier 의 headless 실행 필요성 (사용자 결정):** 현재 testnotifier 는 `MainActivity` 의 버튼 탭으로 시나리오를 발사한다. journey-tester 는 unattended 로 돌므로 `adb shell input tap` 좌표 의존은 emulator skin / resolution 변경에 취약. 선택지:
  - (a) Recipe 에 uiautomator 기반 탭을 명시하고 좌표 fallback 을 적어둔다 (이 plan 의 기본).
  - (b) testnotifier 에 `am broadcast` 로 시나리오를 트리거하는 `ScenarioBroadcastReceiver` 를 추가하는 후속 plan R3 을 연다 (testnotifier repo 에 1 commit 필요).
  - 기본 권장은 (b) 를 후속 plan 으로 별도 분리. 이 plan 은 (a) 로 ship 하되 Risks 에 (b) 의 필요성을 명시.
- **PROMO_QUIETING 프리셋 의존:** recipe 가 PASS 하려면 onboarding 에서 PROMO_QUIETING 프리셋이 선택되어 있어야 한다. fresh install 후 onboarding 을 어떻게 결정적으로 완료시킬지는 이미 plan #272 에서 다뤄진 영역. 필요 시 별도 recipe-preflight plan 으로 분리.
- **키워드 매칭의 언어 의존:** PROMO_DIGEST payload 의 본문이 한국어이므로 classifier 키워드 룰이 한국어 프리셋을 쓴다는 가정. 기본값에서 영어 프리셋으로 바꾸면 drift 가능 — recipe 선결 문구에 명시.
- **testnotifier repo 위치 하드코딩:** `/Users/wooil/source/SmartNotiTestNotifier` 는 원작자 machine 경로. 다른 사람 machine 에서는 경로가 다를 수 있음. recipe 는 env var `TESTNOTI` 로 추상화하고 상단에 "본인 환경에 맞게 export" 주석을 넣는다.

---

## Ranking rationale (why this plan now)

Scoring gap "2026-04-22 live digest-suppression recipe SKIP":

- **User impact = 2/3** — drift 가 아니라 verification debt 이다. 실제 유저 동작은 #292 로 이미 fixed. 다만 unverified 상태가 연속 tick SKIP 으로 누적되어 regression 감지망에 구멍을 내고 있음. 재발 시 첫 피해자는 유저.
- **Specificity = 3/3** — 원인이 명확히 특정됨 (shell ranker-group per-package quota). 해결책 후보 3개 중 R1 은 journey 파일 1개의 블록 1개만 수정. testnotifier APK 와 시나리오 payload 가 이미 존재.
- **Feasibility = 3/3** — SmartNoti 코드 변경 없음. docs-only. 블록 불확정성은 testnotifier 의 headless 실행 방식 하나인데 (a) uiautomator 로 당장 ship 가능.

Total = 8/9. 다른 Known gap 후보들은 모두 아래:
- `digest-suppression` auto-expansion sticky 제외 리스트: impact 2 / spec 2 / feas 2 = 6/9 (설계 결정 필요).
- `home-uncategorized-prompt` recipe (`last-verified: —`): impact 2 / spec 2 / feas 2 = 6/9 (3-package 조건 재현 recipe 필요, testnotifier 확장 선행).
- `inbox-unified` recipe (`last-verified: —`): impact 2 / spec 2 / feas 2 = 6/9.

가장 최근 드리프트 기록 (2026-04-22 tick #293 SKIP) 이 있는 digest-suppression 이 tiebreak 기준으로도 우선.

---

## Related journey

- `docs/journeys/digest-suppression.md` — Known gap 의 2026-04-22 bullet 이 이 plan 으로 drainage. Shipping 시 해당 bullet 은 Change log 항목으로 이동하고, recipe PASS 시 `last-verified` 가 bump 된다.
- 참조: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md` — 유사한 recipe-hardening 문제의 선행 plan (해결 방식은 debug-inject marker, 이번은 real-app originator 교체).
- 참조: `docs/journeys/duplicate-suppression.md` — testnotifier 의 `REPEAT_DIGEST` 시나리오도 향후 이 journey 의 recipe hardening 에 재사용 가능.
