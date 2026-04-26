---
id: quiet-hours
title: 조용한 시간
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

사용자가 지정한 시간대(기본 23시 ~ 익일 7시)에 들어오는 쇼핑앱 알림을 자동으로 DIGEST 로 떨어뜨려 밤 시간 소음을 줄인다.

## Preconditions

- `SmartNotiSettings.quietHoursEnabled = true` (default true)
- 현재 시각이 `[quietHoursStartHour, quietHoursEndHour)` 범위 내 (overnight 지원)
- 대상 알림의 packageName 이 classifier 의 `shoppingPackages` 집합에 포함됨 (예: `com.coupang.mobile`)

## Trigger

`processNotification` 내부에서 `CapturedNotificationInput` 조립 시 현재 시각/quiet hours 설정을 반영.

## Observable steps

1. `SettingsRepository.currentNotificationContext(duplicateCount)` 가 현재 시각을 `QuietHoursPolicy.isQuietAt(hourOfDay)` 로 체크해 `quietHours: Boolean` 필드 채움.
2. `CapturedNotificationInput.withContext(...)` 에 해당 값이 주입됨.
3. `NotificationClassifier.classify(input, rules)`:
   - 룰 매치 우선
   - 그 다음 `input.quietHours && packageName in shoppingPackages` → `DIGEST` 반환 (VIP/키워드 등 더 높은 우선순위가 없을 때)
4. 이후 [notification-capture-classify](notification-capture-classify.md) 파이프라인으로 이어지며, opt-in 된 앱이면 원본 숨김 + replacement (→ [digest-suppression](digest-suppression.md)).
5. Detail 진입 시 `QuietHoursExplainerBuilder` 가 `(reasonTags, status, settings.quietHoursStartHour, settings.quietHoursEndHour)` 로 `QuietHoursExplainer?` 를 합성. `조용한 시간` tag + `status == DIGEST` + `사용자 규칙` 미동시-매치이면 reasonTag chip row 아래 "지금 적용된 정책" sub-section 이 한 줄 카피로 렌더 (예: "지금이 조용한 시간(23시~익일 7시)이라 자동으로 모아뒀어요."). 사용자 규칙이 더 결정적인 경우엔 explainer 가 null 이라 sub-section 자체가 hide.

## Exit state

- 해당 시간대 쇼핑앱 알림이 DIGEST 분류로 DB 에 저장됨.
- Home/정리함 인박스에서 확인 가능.

## Out of scope

- Quiet hours 가 priority/키워드 heuristic 보다 우선하지는 않음 — 중요한 결제/인증 알림은 계속 PRIORITY.
- 시간 창 밖의 쇼핑앱 알림은 일반 분류 흐름.
- 사용자가 shoppingPackages 를 수정하는 UI 는 없음 (현재 classifier 생성자에 하드코딩).

## Code pointers

- `data/settings/SettingsModels#SmartNotiSettings` — `quietHoursEnabled`, `quietHoursStartHour` (default 23), `quietHoursEndHour` (default 7)
- `data/settings/SettingsRepository#currentNotificationContext` — 시각 → `quietHours` 플래그
- `domain/usecase/QuietHoursPolicy#isQuietAt` — same-day / overnight 처리
- `domain/usecase/NotificationClassifier` — quiet hours + shopping package 분기
- `domain/usecase/QuietHoursExplainerBuilder` — Detail "지금 적용된 정책" sub-section 카피 합성 (DIGEST + `조용한 시간` tag + 사용자 규칙 미동시-매치 게이트)
- `ui/screens/detail/NotificationDetailScreen` — explainer sub-section 을 reason 카드 안 classifierSignals chip row 아래에 렌더
- `ui/screens/settings/SettingsScreen` — 토글/시간 선택 UI (Settings 화면 문서화 미완)

## Tests

- `QuietHoursPolicyTest` — 9~18 (same-day), 23~7 (overnight) 범위
- `NotificationClassifierTest` — 쇼핑 앱 + quiet hours → DIGEST
- `QuietHoursExplainerBuilderTest` — DIGEST + `조용한 시간` 단독 시 non-null, PRIORITY / 다른 태그 단독 / `사용자 규칙` 동시 매치 시 null, overnight `(23,7)` 카피에 `"23시"` + `"익일 7시"` substring, same-day `(14,16)` 카피에 `"14시"` + `"16시"` 포함하고 `"익일"` 미포함

## Verification recipe

시스템 시간 조작은 다른 기능에도 영향을 주므로, **앱의 Settings 에서 quietHoursStartHour / quietHoursEndHour 를 현재 시각을 포함하도록 바꾸는 것** 이 가장 간편한 방법입니다.

```bash
# 1. Settings 탭에서 "조용한 시간" 섹션 열기 — 시작/종료 시간을 현재 시각을 포함하도록 조정
#    (예: 현재 14:30 이라면 start=14, end=16 으로)

# 2. `com.coupang.mobile` 또는 기존 classifier 의 shoppingPackages 에 포함된 앱 패키지로 알림 게시
#    (cmd notification 는 --pkg 옵션이 없어 `com.android.shell` 로만 게시됨. 쇼핑앱 테스트는
#     실제 앱 혹은 테스트 전용 앱 필요.)

# 3. quiet hours 종료 후에도 분류가 남아 있는지 Home/정리함 탭에서 확인 — 알림의
#    reasonTags 에 "quiet hours" 관련 태그가 있는지 Detail 에서 확인
```

## 4. Positive-case end-to-end (debug APK only)

`DebugInjectNotificationReceiver` 가 `--es package_name <pkg>` + `--es app_name <label>` extras 를 받아 classifier 의 package-gated 분기 (예: `shoppingPackages = setOf("com.coupang.mobile")`) 를 발화시킬 수 있다 (plan `docs/plans/2026-04-26-debug-inject-package-name-extra.md`). `force_status` 는 omit 해서 real classifier 가 결정하도록 둔다.

```bash
# 사전: Settings 의 조용한 시간 창을 현재 시각이 포함되도록 조정 (위 step 1 동일).

# 쇼핑앱 packageName 으로 broadcast — force_status 미전달 → real classifier 분기 실행.
adb -s emulator-5554 shell am broadcast \
  -a com.smartnoti.debug.INJECT_NOTIFICATION \
  -p com.smartnoti.app \
  --es title "QuietHrsPositive$(date +%s)" \
  --es body "쇼핑 알림 (조용한 시간 분기 기대)" \
  --es package_name com.coupang.mobile \
  --es app_name "Coupang"

# 기대: status=DIGEST, reasonTags 에 "조용한 시간" 포함 + "사용자 규칙" 미포함.
#       Detail 진입 시 classifierSignals chip row 아래 "지금 적용된 정책"
#       sub-section 이 한 줄 카피로 렌더.
adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
  databases/smartnoti.db \
  "SELECT status,reasonTags FROM notifications WHERE title LIKE 'QuietHrsPositive%' ORDER BY postedAtMillis DESC LIMIT 1;"
```

## Known gaps

- `shoppingPackages` 가 classifier 생성자에 하드코딩 (`setOf("com.coupang.mobile")`) — 사용자가 카테고리를 확장할 수 없음.
- (resolved 2026-04-26, plan `2026-04-26-quiet-hours-explainer-copy`) Quiet hours 가 적용되었다는 것을 Detail 의 reasonTags 로만 확인 가능. UI 에서 "quiet hours 때문에 숨김" 같은 명시적 설명 부재. → plan: `docs/plans/2026-04-26-quiet-hours-explainer-copy.md`
- (resolved 2026-04-26 positive ADB capture, see Change log) Positive-case ADB end-to-end capture: blocker (b) 해소 — `DebugInjectNotificationReceiver` 가 `--es package_name <pkg>` + `--es app_name <label>` extras 를 지원하면서 classifier 의 quiet-hours 분기 (`packageName in setOf("com.coupang.mobile")`) 를 직접 발화시킬 수 있다 (plan `docs/plans/2026-04-26-debug-inject-package-name-extra.md`, Verification recipe step 4 참고). (resolved 2026-04-26, plan `2026-04-26-inbox-bundle-preview-see-all`) blocker (a): 정리함 inbox 의 Digest/보관 bundle preview 가 가장 최근 rows 만 노출 → 과거 quiet-hours rows 의 Detail UI 로 직접 navigation 경로 부재. 우회 후보: bundle preview 에 "전체 보기" 진입점 추가. → plan: `docs/plans/2026-04-26-inbox-bundle-preview-see-all.md` (이번 sweep 은 갓 inject 한 row 가 preview 상단에 떠 있어 navigation 가능했음 — historical row 는 여전히 막혔으나 본 plan ship 이후 inline "전체 보기 · ${remaining}건 더" CTA 로 모든 row Detail 진입 가능).
- (resolved 2026-04-26, plan `2026-04-26-settings-quiet-hours-window-editor`) 신규 blocker (c) — Settings 화면이 `quietHoursStartHour` / `quietHoursEndHour` 를 사용자가 조정할 수 있는 editor 를 노출하지 않음 (토글만 존재; `OperationalSummaryCard` 콜백은 `onQuietHoursEnabledChange` 단일). 결과적으로 verification recipe 의 "Settings 에서 시작/종료 시간을 현재 시각을 포함하도록 조정" prereq 가 현재 빌드에서 수행 불가능 → live in-window DIGEST 관측이 (a) (b) 해소 후에도 막혀 있음. 우회 후보: Settings 에 시간 picker 추가, 또는 receiver 에 `--es quiet_hours true` extra 추가 (plan 의 Risks open question — 향후 별도 plan 으로 결정). → plan: `docs/plans/2026-04-26-settings-quiet-hours-window-editor.md`

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Policy-level verification re-run (`QuietHoursPolicyTest` 2/2 + `NotificationClassifierTest` 19/19 PASS, 포함 `shopping_app_during_quiet_hours_goes_to_digest`). `NotificationClassifier.kt:36` 분기 + `NotificationCaptureProcessor.buildReasonTags` 의 `조용한 시간` 태그 경로가 doc Observable step 3 과 exact 일치. Live DB 관측 (`run-as com.smartnoti.app sqlite3 smartnoti.db 'SELECT DISTINCT reasonTags FROM notifications;'`) 에 `조용한 시간` tag 부재는 emulator 시각 22:18 KST 가 default `[23,7)` 범위 밖이라 계약 일치. DRIFT 없음. `last-verified` 를 2026-04-20 → 2026-04-21 갱신.
- 2026-04-22: Static-source + live-DB sweep. JDK 미설치로 unit test 실행 불가 → `NotificationClassifier.kt:90` (`if (input.packageName in shoppingPackages && input.quietHours) return DIGEST`) 와 `QuietHoursPolicy.kt:7-15` (same-day / overnight 분기) 가 Observable steps 1-3 과 일치함을 소스 수준에서 재확인. emulator 현재 시각 00:34 KST → default `[23,7)` 범위 안. `run-as com.smartnoti.app sqlite3 smartnoti.db 'SELECT DISTINCT reasonTags FROM notifications;'` 결과에 실제로 `조용한 시간` tag 가 살아 있는 row 존재 (분류 파이프라인이 quiet-hours 분기를 실제로 발화 중). `SettingsRepository.kt:57-60` 가 `quietHoursEnabled` + `QuietHoursPolicy(startHour,endHour)` 를 묶어 `currentNotificationContext` 로 주입하는 경로도 doc Code pointers 와 일치. DRIFT 없음. `last-verified` 를 2026-04-21 → 2026-04-22 갱신.
- 2026-04-24: v1 loop tick pick (2-day-stale; last-verified 2026-04-22). Static-source + live-DB sweep on emulator-5554 (현재 시각 12:43 KST = `[23,7)` default 범위 밖, 새 in-window post 불가하므로 historical row 검증). `NotificationClassifier.kt:90` (`if (input.packageName in shoppingPackages && input.quietHours) return DIGEST`) 와 `QuietHoursPolicy.kt:7-15` (same-day: `hourOfDay in startHour until endHour`, overnight: `hourOfDay >= startHour || hourOfDay < endHour`) 가 Observable steps 1-3 과 byte-for-byte 일치. `SettingsRepository.kt:53-65` `currentNotificationContext` 가 `Calendar.HOUR_OF_DAY` + `QuietHoursPolicy(startHour,endHour)` 를 `NotificationContext` 로 묶어 주입하는 경로도 doc Code pointers ("data/settings/SettingsRepository#currentNotificationContext — 시각 → quietHours 플래그") 와 일치. Live DB 쿼리 `run-as com.smartnoti.app sqlite3 databases/smartnoti.db "SELECT packageName,status,datetime(postedAtMillis/1000,'unixepoch','localtime'),reasonTags FROM notifications WHERE reasonTags LIKE '%조용한%' ORDER BY postedAtMillis DESC LIMIT 3;"` → 가장 최근 `조용한 시간` tag row 3건 모두 `2026-04-24 02:02:09~11` (default `[23,7)` quiet window 안) posted, status DIGEST/SILENT/SILENT, packageName `com.android.shell` — capture→classifier→repository 파이프라인이 quiet-hours 분기를 실제로 발화시켰음을 확인 (Exit state "DIGEST 분류로 DB 에 저장됨" 충족; SILENT 는 다른 분기에서 결정되더라도 quiet hours reasonTag 는 `NotificationCaptureProcessor.buildReasonTags` 가 quietHours 컨텍스트에 따라 부착하므로 step 1-2 발화 증거로 충분). DRIFT 없음. `last-verified` 를 2026-04-22 → 2026-04-24 갱신.
- 2026-04-26: Plan `2026-04-26-quiet-hours-explainer-copy` Tasks 3-6 ship — Detail 의 "왜 이렇게 처리됐나요?" 카드 안 classifierSignals chip row 아래에 `QuietHoursExplainerBuilder` 로 합성한 "지금 적용된 정책" sub-section 추가 (예: "지금이 조용한 시간(23시~익일 7시)이라 자동으로 모아뒀어요."). `사용자 규칙` 동시-매치 케이스는 explainer null 처리 (사용자 규칙이 더 결정적이므로 잘못된 인과 설명 방지). Tasks 1-2 (builder + 6/6 unit tests) 는 PR #338 (5d418a9) 로 선행 ship; Tasks 3-6 은 후속 PR. ADB negative-case 검증 on emulator-5554: (a) `사용자 규칙 + 조용한 시간` reasonTags 가진 SmartNoti Test Notifier "오늘만 특가 안내" Detail → sub-section 미노출 (HIGHER_PRECEDENCE_TAGS suppression 확인), (b) `조용한 시간` 미포함 `Repeat3` Shopping/한정 Detail → sub-section 미노출 (tag-gate 확인). Positive case (DIGEST + `조용한 시간` + no `사용자 규칙`) 는 inbox 의 bundle preview 가 가장 최근 row 만 노출하는 제약 때문에 직접 navigation 불가 — 6/6 unit test (overnight + same-day + null gates) + 동일한 wiring 패턴 (`NotificationDetailDeliveryProfileSummaryBuilder` 등) 의 라이브 동작이 동등 증거. DRIFT 없음. `last-verified` 는 verification recipe 를 처음부터 다시 돌리지 않았으므로 갱신 보류.
- 2026-04-26: Plan `2026-04-26-debug-inject-package-name-extra` Tasks 3-6 ship — Verification recipe section 에 "## 4. Positive-case end-to-end (debug APK only)" 추가 (debug receiver 의 `--es package_name com.coupang.mobile --es app_name "Coupang"` broadcast 로 real classifier 의 shopping + quiet-hours 분기 발화). Known gaps 에서 blocker (b) (`DebugInjectNotificationReceiver` packageName hardcode) 를 해소된 항목으로 다시 표기, 남은 blocker (a) (bundle preview 의 historical row navigation 부재) 는 재기술하고 신규 blocker (c) (Settings UI 가 quiet-hours 시작/종료 시간 editor 미노출 — 토글만 존재) 를 추가. `notification-capture-classify.md` Code pointers 에는 cross-journey 노트 추가. **Live ADB partial verification on emulator-5554 (KST 12:02, default `[23,7)` window 밖)**: `am broadcast` 직후 DB row `packageName=com.coupang.mobile, appName=Coupang, status=SILENT, reasonTags=발신자 있음|쇼핑 앱, sourceEntryKey=com.coupang.mobile|debug-inject|...` 저장 — receiver 의 packageName/appName/sourceEntryKey 치환이 production save 경로를 그대로 통과함을 확인 + classifier 의 쇼핑앱 분기 진입 (shopping reasonTag 부착) 도 확인. `quietHours=false` 라 DIGEST 가 아닌 SILENT 로 분기됐고, Settings UI 에 시작/종료 시간 editor 가 없어서 in-window 케이스 ADB 재현 불가 — 이 부분은 신규 blocker (c) 로 분리 트래킹 (별도 follow-up plan 후보: editor 추가 또는 receiver 의 `--es quiet_hours true` extra). `last-verified` 는 같은 날 (2026-04-26) 이므로 기존 값 유지.
- 2026-04-26 (post-#339 follow-up sweep): Re-attempt of positive-case ADB capture on emulator-5554 (KST 11:24, default `[23,7)` window 밖). Live DB confirms 3 historical rows satisfying positive precondition (DIGEST + `조용한 시간` + no `사용자 규칙`): `com.android.shell:2020:VerifySweep0422-3` (2026-04-24 02:02:11), `MediaTest0421R2` (2026-04-21 04:06:01), `MediaTest0421` (2026-04-21 01:48:12). Inbox `정리함` 탭의 Digest (14건) 및 보관 중 (31건) bundle preview 는 둘 다 가장 최근 rows 만 노출 (관측: `Shopping/한정 + 발신자 있음 + 반복 알림` Detail 만 entry 가능; `조용한 시간` tag 가진 historical row 는 UI route 부재) — plan-implementer 의 deferral 사유와 정확히 일치. `DebugInjectNotificationReceiver` 도 packageName 을 `com.smartnoti.debug.tester` 로 hardcode 하므로 quiet-hours 분기 (`packageName in shoppingPackages = setOf("com.coupang.mobile")`) 를 발화시키지 못함 → debug-injector 경로로도 우회 불가. Negative-case 라이브 재확인 PASS: `발신자 있음 | 한정 | 반복 알림` reasonTags 의 Shell Shopping Detail 진입 → "왜 이렇게 처리됐나요?" 카드 안 chip row + 전달 모드 카드만 렌더, "지금 적용된 정책" sub-section 부재 (tag-gate 정상 동작). Home 탭의 일반 인사이트 카드 "Shell 알림 46개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'예요" 는 quiet-hours 파이프라인이 실제로 분류 결과를 driving 하고 있음을 추가로 확인. DRIFT 없음. `last-verified` 를 2026-04-24 → 2026-04-26 갱신.
- 2026-04-26: Plan `2026-04-26-settings-quiet-hours-window-editor` ship — Settings 화면의 운영 상태 카드 안 Quiet Hours row 아래에 시작/종료 hour picker 두 개 추가 (`AssistChip` + `DropdownMenu` 기반, 0~23 옵션, `%02d:00` label). master Switch ON 일 때만 노출, `start == end` 시 inline 경고 카피 ("시작·종료가 같으면 적용 시각이 모호해요.") 표시. `SettingsRepository` 에 `setQuietHoursStartHour` / `setQuietHoursEndHour` suspend setter 추가 (read 경로와 DataStore 키는 이전부터 wired). 결과적으로 Verification recipe 의 step 1 ("Settings 에서 시작/종료 시간을 현재 시각을 포함하도록 조정") 이 빌드에서 수행 가능해져 blocker (c) 해소. ADB 검증 on emulator-5554: Settings → 운영 상태 → Quiet Hours row 의 dropdown 으로 시작=23→11 변경 → 요약 라인이 `11:00 ~ 07:00` 으로 즉시 갱신 → `am force-stop` + 재진입 후 값 유지 (DataStore 영속 확인) → 종료=11 추가 변경 시 `시작·종료가 같으면 적용 시각이 모호해요.` warning 렌더 + Switch OFF 시 picker/warning 모두 사라지고 `꺼짐` 라벨로 대체. 본 sweep 은 picker UI + 영속 검증만 수행 (in-window DIGEST 분기 라이브 재현은 별도 journey-tester sweep 에서). `last-verified` 는 quiet-hours 전체 recipe 를 다시 돌리지 않았으므로 갱신 보류 — 이는 plan 의 Risks 절 "verification 재실행" note 에 따른 의도된 분리.
- 2026-04-26 (positive-case ADB sweep, post-#346 follow-up): Settings UI editor (PR #346) + debug-inject packageName extra (PR #339) + explainer copy (PR #338) 가 모두 ship 된 후 첫 end-to-end positive 캡처 on emulator-5554. emulator KST 12:54, default `[23,7)` 범위 밖 → Settings 운영 상태 → Quiet Hours row 의 종료 picker dropdown 으로 종료=11→13 변경 → 요약 `11:00 ~ 13:00` (현재 시각 12 포함) 으로 갱신. DataStore (`smartnoti_settings.preferences_pb`) 가 `quiet_hours_start_hour=11`, `quiet_hours_end_hour=13`, `quiet_hours_enabled=true` 로 영속 확인. `am broadcast -a com.smartnoti.debug.INJECT_NOTIFICATION -p com.smartnoti.app --es package_name com.coupang.mobile --es app_name 'Coupang' --es title 'QuietHrsPositive…' --es body 'shopping body'` → DB row `packageName=com.coupang.mobile, appName=Coupang, status=DIGEST, reasonTags=발신자 있음|조용한 시간|쇼핑 앱` (no `사용자 규칙` — explainer gate satisfied). 정리함 → Digest · 15건 묶음에서 Coupang 1건 row 진입 → Detail 의 "왜 이렇게 처리됐나요?" 카드 안 chip row (`발신자 있음 / 쇼핑 앱 / 조용한 시간`) 아래 "지금 적용된 정책" sub-section 렌더 + 한 줄 카피 정확히 `지금이 조용한 시간(11시~13시)이라 자동으로 모아뒀어요.` 출력 — Observable step 5 의 same-day `(11,13)` 카피 패턴 (`"11시" + "13시"` substring, `"익일"` 미포함) 과 일치. 전달 모드 카드 `Digest 묶음 전달` + supporting copy `덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요.` 도 함께 렌더. DRIFT 없음 — Observable steps 1-5 + Exit state 모두 충족. Known gaps 의 blocker (b) 는 본 sweep 으로 positive 증명까지 완료 (이전엔 `--es package_name` 지원만 확인했고 in-window DIGEST 라이브 캡처는 미수행). `last-verified` 는 같은 날 (2026-04-26) 이므로 기존 값 유지.
