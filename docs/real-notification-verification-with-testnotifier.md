# SmartNoti Real Notification Verification with SmartNotiTestNotifier

## 목적
`adb shell cmd notification post` 대신 **실제 앱 패키지**에서 발생한 알림으로 SmartNoti의 캡처/분류/홈 효과 반영을 검증한다.

## 왜 별도 앱이 필요한가
- `adb shell cmd notification post ...` 는 `com.android.shell` 알림으로 들어간다.
- 시스템 알림 목록에는 보여도 SmartNoti의 실제 사용자 시나리오와 동일한 외부 앱 알림으로 보기 어렵다.
- 따라서 반복 알림, 프로모션 알림, 중요한 알림 효과를 신뢰성 있게 검증하려면 별도 앱 패키지에서 실제 알림을 보내야 한다.

## 검증 앱 위치
- 별도 sibling 프로젝트 예시: `/Users/wooil/source/SmartNotiTestNotifier`
- 패키지: `com.smartnoti.testnotifier`
- 이 문서는 **로컬 예시 경로/에뮬레이터 값**을 사용한다. 다른 환경에서는 프로젝트 경로, SDK 경로, emulator serial을 자신의 값으로 바꿔서 실행한다.
- SmartNotiTestNotifier 프로젝트가 미리 준비되어 있어야 한다.

## 준비
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
# 예시 serial: emulator-5554
```

## 1. SmartNoti 빌드/설치
```bash
cd /Users/wooil/source/SmartNoti
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## 2. SmartNoti 온보딩 완료
```bash
adb -s emulator-5554 shell pm clear com.smartnoti.app
adb -s emulator-5554 shell pm grant com.smartnoti.app android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb -s emulator-5554 shell cmd notification allow_listener \
  com.smartnoti.app/com.smartnoti.app.notification.SmartNotiNotificationListenerService
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
```

확인 포인트:
- 권한 게이트가 보인다.
- `빠른 시작 추천 보기` 로 넘어갈 수 있다.
- `이대로 시작할게요` 이후 Home 으로 진입한다.

## 3. SmartNotiTestNotifier 빌드/설치
```bash
cd /Users/wooil/source/SmartNotiTestNotifier
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.smartnoti.testnotifier/.MainActivity
```

## 4. 검증 시나리오 발송
테스트 앱에서 다음 시나리오를 보낸다.
- 프로모션 1건
- 반복 알림 3건
- 중요 알림 1건

또는 `전체 시나리오 보내기` 를 사용한다.

시스템 레벨에서 실제로 올라왔는지 확인:
```bash
adb -s emulator-5554 shell dumpsys notification --noredact | grep -n 'com.smartnoti.testnotifier'
```

## 5. SmartNoti Home 검증
```bash
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/smartnoti_ui.xml >/dev/null
adb -s emulator-5554 shell cat /sdcard/smartnoti_ui.xml
```

확인 포인트:
- Home 상단 요약 카드 수치가 바뀐다.
- `실제 알림 상태` 카드가 최근 실제 반영 수치를 보여준다.
- 빠른 시작 효과 카드가 pending 문구 대신 `최근 효과`와 실제 효과 문구를 보여줄 수 있다.

## 6. DB 확인
반드시 `db + wal + shm` 를 같이 복사한다.
```bash
mkdir -p /tmp/smartnoti-db-check
adb -s emulator-5554 exec-out run-as com.smartnoti.app cat databases/smartnoti.db > /tmp/smartnoti-db-check/smartnoti.db
adb -s emulator-5554 exec-out run-as com.smartnoti.app cat databases/smartnoti.db-wal > /tmp/smartnoti-db-check/smartnoti.db-wal
adb -s emulator-5554 exec-out run-as com.smartnoti.app cat databases/smartnoti.db-shm > /tmp/smartnoti-db-check/smartnoti.db-shm
python3 - <<'PY'
import sqlite3
conn = sqlite3.connect('/tmp/smartnoti-db-check/smartnoti.db')
cur = conn.cursor()
print(cur.execute('select count(*) from notifications').fetchall())
print(cur.execute('select status, count(*) from notifications group by status order by status').fetchall())
for row in cur.execute("select packageName,title,body,status,reasonTags from notifications order by postedAtMillis desc limit 10"):
    print(row)
PY
```

## 반복 알림 검증에서 중요했던 점
실제 에뮬레이터 검증에서 반복 알림 3건이 전부 `SILENT` 로 남는 문제가 있었다.

원인:
- listener 가 중복 수를 Room `countRecentDuplicates(...)` 결과만으로 계산하면
- 빠르게 연속 도착한 알림은 앞선 알림이 아직 DB 에 반영되기 전이라
- 뒤 알림이 중복으로 카운트되지 않을 수 있다.

해결:
- `NotificationListenerService` 안에서
- `packageName + contentSignature` 기준의 in-memory live duplicate tracker 를 함께 사용해
- persisted duplicate count 와 live in-flight count 를 같이 반영한다.

이 수정 이후 실제 repeat 시나리오에서:
- 프로모션 1건 → `DIGEST`
- 중요 알림 1건 → `PRIORITY`
- 반복 알림 3건 중 최소 1건 → `DIGEST` + `반복 알림`
- Home 빠른 시작 효과 카드에 `반복 알림 ... Digest로 묶였어요` 문구가 나타나는 것을 확인했다.

## 성공 기준
- `com.smartnoti.testnotifier` 알림이 시스템에 실제로 올라온다.
- SmartNoti Home 수치가 0에서 실제 데이터 기준으로 변한다.
- 빠른 시작 효과 카드가 프로모션/중요/반복 효과를 실제 데이터 기준으로 보여준다.
- DB 조회 시 상태 분포와 reason tags 가 UI와 일관된다.
