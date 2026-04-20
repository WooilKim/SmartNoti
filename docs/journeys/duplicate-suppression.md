---
id: duplicate-suppression
title: 중복 알림 감지 및 DIGEST 강등
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

같은 content signature(title+body 정규화) 의 알림이 일정 창(기본 10분) 안에 3회 이상 반복되면 DIGEST 로 떨어뜨려 반복 알림 소음을 완화한다. 룰 매치나 우선순위 키워드가 없는 일반 알림에만 적용.

## Preconditions

- 알림이 posted 됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 같은 content signature 알림이 최근 `DEFAULT_WINDOW_MILLIS` (10분) 안에 최소 3건 존재 (in-memory + DB 합산)

## Trigger

`processNotification` 내 `LiveDuplicateCountTracker.recordAndCount(...)` 가 중복 카운트를 반환.

## Observable steps

1. `DuplicateNotificationPolicy.contentSignature(title, body)` — title/body 를 소문자화 + 공백 정규화해 signature 생성.
2. `windowStart = sbn.postTime - DEFAULT_WINDOW_MILLIS` 계산.
3. `NotificationRepository.countRecentDuplicates(packageName, signature, windowStart)` → Room DAO 에서 10분 내 persisted count.
4. `LiveDuplicateCountTracker.recordAndCount(...)`:
   - 같은 sourceEntryKey 의 재게시면 기존 live entry 의 postedAtMillis 만 갱신 (count 증가 없음)
   - 새 sourceEntryKey 면 live entry 에 추가, window 밖 entry 는 prune
   - 반환값 = `max(persistedDuplicateCount + 1, live 엔트리 수)` — 가장 보수적인 카운트
5. persistent 알림이면 duplicateCount 는 1 로 고정 (반복 알림이라도 지속 알림은 숨기지 않음).
6. `NotificationClassifier.classify(input, rules)`:
   - 룰 매치 우선
   - 이어 VIP/키워드 heuristic 체크
   - 그 외일 때 `duplicateCountInWindow >= DUPLICATE_DIGEST_THRESHOLD (3)` 이면 DIGEST 반환
7. 이후 opt-in 된 앱이면 [digest-suppression](digest-suppression.md) 경로로 원본 숨김 + replacement.

## Exit state

- 반복 알림 3번째부터는 DIGEST 로 저장되어 정리함 인박스나 숨김 흐름으로 모임.
- 실제 내용은 `NotificationEntity.contentSignature` 로 함께 저장되어 동일 내용 카운트가 누적.

## Out of scope

- 지속 알림의 반복 횟수 카운팅 (의도적으로 1 로 고정)
- REPEAT_BUNDLE 룰 — 사용자가 명시적으로 묶음 임계를 지정하는 별도 메커니즘 (→ [rules-management](rules-management.md))
- 중복 판정 전에 매칭되는 VIP/키워드 heuristic 은 PRIORITY 로 승격

## Code pointers

- `domain/usecase/DuplicateNotificationPolicy` — signature 정규화, `DEFAULT_WINDOW_MILLIS` (10분)
- `domain/usecase/LiveDuplicateCountTracker` — in-memory + persisted 카운트 병합
- `domain/usecase/NotificationClassifier` — 임계 3회 체크
- `data/local/NotificationDao#countRecentDuplicates` — SQL 쿼리
- `notification/SmartNotiNotificationListenerService#processNotification` — tracker 호출부

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

- Threshold 3 과 window 10분은 하드코딩 — 사용자 커스터마이징 불가.
- signature 는 title+body 만 사용, 이미지/chrono 같은 시각 신호는 무시.
- 서비스가 재시작되면 in-memory live entry 가 사라지고 persisted count 로만 재집계됨 — 경계 케이스에서 1건 차이 가능.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
