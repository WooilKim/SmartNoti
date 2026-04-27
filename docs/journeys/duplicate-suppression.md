---
id: duplicate-suppression
title: 중복 알림 감지 및 DIGEST 강등
status: shipped
owner: @wooilkim
last-verified: 2026-04-27
---

## Goal

같은 content signature(title+body 정규화) 의 알림이 사용자 설정 창(기본 10분, Settings 에서 5/10/15/30/60 분 중 선택) 안에 사용자 설정 임계값(기본 3회, Settings 에서 2/3/4/5/7/10 회 중 선택) 이상 반복되면 DIGEST 로 떨어뜨려 반복 알림 소음을 완화한다. 룰 매치나 우선순위 키워드가 없는 일반 알림에만 적용.

## Preconditions

- 알림이 posted 됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 같은 content signature 알림이 최근 `SmartNotiSettings.duplicateWindowMinutes` (기본 10분, 사용자 설정 가능) 안에 최소 `SmartNotiSettings.duplicateDigestThreshold` (기본 3회, 사용자 설정 가능) 건 존재 (in-memory + DB 합산)

## Trigger

`processNotification` 내 `LiveDuplicateCountTracker.recordAndCount(...)` 가 중복 카운트를 반환.

## Observable steps

1. `DuplicateNotificationPolicy.contentSignature(title, body)` — title/body 를 소문자화 + 공백 정규화해 signature 생성. policy 인스턴스는 매 `processNotification` 호출 시 `settings.duplicateWindowMinutes * 60_000L` 로 새로 빌드 — 사용자가 dropdown 을 바꾸면 다음 알림부터 적용.
2. `windowStart = sbn.postTime - settings.duplicateWindowMinutes * 60_000L` 계산 (기본 10분, Settings 에서 사용자 변경 가능).
3. `NotificationRepository.countRecentDuplicates(packageName, signature, windowStart)` → Room DAO 에서 사용자 설정 창 내 persisted count.
4. `LiveDuplicateCountTracker.recordAndCount(...)`:
   - 같은 sourceEntryKey 의 재게시면 기존 live entry 의 postedAtMillis 만 갱신 (count 증가 없음)
   - 새 sourceEntryKey 면 live entry 에 추가, window 밖 entry 는 prune
   - 반환값 = `max(persistedDuplicateCount + 1, live 엔트리 수)` — 가장 보수적인 카운트
5. persistent 알림이면 duplicateCount 는 1 로 고정 (반복 알림이라도 지속 알림은 숨기지 않음).
6. `NotificationClassifier.classify(input, rules)`:
   - 룰 매치 우선
   - 이어 VIP/키워드 heuristic 체크
   - 그 외일 때 `duplicateCountInWindow >= input.duplicateThreshold` (기본 3, Settings 의 `duplicateDigestThreshold` 에서 2/3/4/5/7/10 중 선택) 이면 DIGEST 반환
7. 이후 opt-in 된 앱이면 [digest-suppression](digest-suppression.md) 경로로 원본 숨김 + replacement.

## Exit state

- 반복 알림 3번째부터는 DIGEST 로 저장되어 정리함 인박스나 숨김 흐름으로 모임.
- 실제 내용은 `NotificationEntity.contentSignature` 로 함께 저장되어 동일 내용 카운트가 누적.

## Out of scope

- 지속 알림의 반복 횟수 카운팅 (의도적으로 1 로 고정)
- REPEAT_BUNDLE 룰 — 사용자가 명시적으로 묶음 임계를 지정하는 별도 메커니즘 (→ [rules-management](rules-management.md))
- 중복 판정 전에 매칭되는 VIP/키워드 heuristic 은 PRIORITY 로 승격

## Code pointers

- `domain/usecase/DuplicateNotificationPolicy` — signature 정규화. `windowMillis` 는 caller-injected (default 없음); listener 가 `settings.duplicateWindowMinutes * 60_000L` 로 매 호출 build.
- `domain/usecase/LiveDuplicateCountTracker` — in-memory + persisted 카운트 병합
- `domain/usecase/NotificationClassifier` — `duplicateCountInWindow >= input.duplicateThreshold` 체크 (사용자 설정 임계값)
- `domain/model/CapturedNotificationInput.duplicateThreshold` / `domain/model/ClassificationInput.duplicateThreshold` — 사용자 임계값 전파
- `data/settings/SettingsRepository` — 새 키 `duplicate_digest_threshold` / `duplicate_window_minutes` + setters `setDuplicateDigestThreshold` / `setDuplicateWindowMinutes` (`coerceAtLeast(1)` 가드)
- `data/settings/SmartNotiSettings.duplicateDigestThreshold` / `duplicateWindowMinutes` — 기본 3 / 10
- `ui/screens/settings/DuplicateThresholdEditorSpecBuilder` — Settings 카드 dropdown 옵션 (`2/3/4/5/7/10` × `5/10/15/30/60`) 의 단일 source-of-truth
- `data/local/NotificationDao#countRecentDuplicates` — SQL 쿼리
- `notification/SmartNotiNotificationListenerService#processNotification` — tracker 호출부, settings-driven policy build

## Tests

- `DuplicateNotificationPolicyTest` — signature 정규화 (대소문자/공백)
- `LiveDuplicateCountTrackerTest` — distinct key 증가, 같은 key 유지, persisted count 병합, window pruning
- `NotificationClassifierTest` — duplicate >= 3 → DIGEST

## Verification recipe

```bash
# 1. 같은 title+body 알림을 3번 이상 빠르게 게시
for i in 1 2 3; do
  adb shell cmd notification post -S bigtext -t "Shopping" "Repeat$i" "한정 특가"
done

# 2. 세 번째부터 DIGEST 로 분류되어 정리함/숨김에 들어가는지 확인
adb shell am start -n com.smartnoti.app/.MainActivity
```

## Known gaps

- signature 는 title+body 만 사용, 이미지/chrono 같은 시각 신호는 무시.
- 서비스가 재시작되면 in-memory live entry 가 사라지고 persisted count 로만 재집계됨 — 경계 케이스에서 1건 차이 가능.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-26: **Threshold 와 window 가 사용자 설정 가능** — `SmartNotiSettings.duplicateDigestThreshold` (기본 3, Settings dropdown 2/3/4/5/7/10) + `duplicateWindowMinutes` (기본 10, dropdown 5/10/15/30/60). `DuplicateNotificationPolicy.DEFAULT_WINDOW_MILLIS` 제거 (caller-injected only); `NotificationClassifier` 의 하드코딩 `>= 3` 은 `>= input.duplicateThreshold` 로 일반화. Listener 가 매 `processNotification` 호출에서 settings snapshot 으로 policy 인스턴스를 새로 build, `CapturedNotificationInput.duplicateThreshold` 를 통해 classifier 에 전달. UI 는 OperationalSummaryCard 안에 두 dropdown 추가 (반복 N회 / 최근 N분). Plan: `docs/plans/2026-04-26-duplicate-threshold-window-settings.md`. `last-verified` 는 ADB 검증 후 별도 갱신.
