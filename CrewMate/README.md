# CrewMate — 함께 타고, 각자의 목적지까지

> **공모전용 개발 프로토타입 (2026-11-07)** · Kotlin Android 운전자 앱 + Node.js LAN 서버 + 동승자 모바일 웹

동승자의 목적지/경유지 변경을 운전자 대신 입력하고, 차량 화면에서 승인을 받아 공동 귀가 계획을 갱신하며, 하차가 확인되면 접근 권한을 회수하는 앱입니다.

## 현재 구현된 것

| 기능 | 구현 여부 | 비고 |
| --- | --- | --- |
| Kotlin Android 운전자 대시보드 | ✅ 소스 구현 | Android Studio/CI 빌드 검증 필요 |
| 3명 동승자와 하차 경로 표시 | ✅ | 시뮬레이션 예시 장소 |
| 목적지 변경 요청 승인/거절 | ✅ | 오프라인 + LAN 모드 |
| 동승자 휴대폰 실시간 요청 | ✅ 서버 구현 | 같은 Wi-Fi/LAN 서버 필요, 약 2.5초 폴링 |
| 동승자별 경유지 제안 권한 | ✅ | 서버에서 권한 검사 |
| 하차 시 세션/권한 회수 | ✅ | 서버 토큰 무효화 |
| 소규모 하차 순서 계산 | ✅ | 직선거리 기반, 도로 경로 아님 |
| 별도 디자인 시안 | ✅ | 앱에서 ‘디자인 시안’ 선택 |
| Pleos NaviHelper SDK 실제 제어 | ⏳ 미연동 | SDK/퍼미션/에뮬레이터 별도 필요 |
| 자동차 주행상태 인식 | ⏳ 미연동 | 운전자 승인에는 정차 확인 필요 |

**주의:** 이 앱은 일반 Android API 기반 데모로 제작되었습니다. 실제 운행 차량에서 사용하지 마세요. 주행 중 조작 제한 및 정식 SDK 권한 검증이 완료되지 않았습니다.

## 폴더 구조

```
CrewMate/
├── android-app/              Kotlin Android Studio 프로젝트
│   └── app/src/main/java/io/crewmate/app/
│       ├── MainActivity.kt      실제 운전자 대시보드
│       ├── DriverState.kt       오프라인 시연 상태
│       ├── DemoApi.kt           LAN 서버 통신
│       ├── PreviewActivity.kt   웹 디자인 시안
│       └── core/RouteOptimizer.kt
├── server/
│   ├── server.mjs            외부 의존성 없는 Node HTTP API
│   ├── public/passenger.html 동승자 모바일 웹
│   └── tests/server.test.mjs 자동 테스트
├── tests/kotlin/             순수 Kotlin 경로 계산 테스트
├── apk/                      GitHub Actions 빌드 성공 시 APK 생성
└── README.md
```

기존 [차량 + 동승자 웹 시안](../index.html)은 별도로 유지합니다.

## 1. 앱 단독으로 실행 (가장 간단)

1. Android Studio에서 **`CrewMate/android-app` 폴더**를 프로젝트로 엽니다.
2. Android SDK API 34, JDK 17, Gradle 8.10.2를 설치합니다.
3. **Run** 버튼으로 Android Emulator 또는 Pleos Emulator(API 34)에 설치합니다.
4. 앱 첫 화면에서 기본 제공되는 **DEMO MODE**를 사용합니다.
5. `테스트 요청 생성 → 승인 → 하차 완료`를 눌러 기능을 확인합니다.

**중요:** 개발 환경에 Android SDK가 없어 로컬 APK 빌드를 아직 확인하지 못했습니다. `.github/workflows/crewmate-android.yml`이 GitHub Actions의 Android 빌드와 서명을 수행합니다. 빌드 통과 여부 및 산출물을 반드시 확인해 주세요.

## 2. 동승자 휴대폰을 실제로 연결 (LAN 모드)

컴퓨터에 Node.js 20 이상이 필요합니다. **별도 npm install은 필요하지 않습니다.**

```bash
cd CrewMate/server
npm start
```

터미널에 출력되는 **Driver key**를 따로 복사하세요.

Android 앱에서 `LAN 서버 연결` 버튼을 누르고:

- 기본 Android 에뮬레이터: `http://10.0.2.2:8787`
- 실제 Android 단말 / 일부 Pleos 에뮬레이터: `http://컴퓨터의-LAN-IP:8787`
- Driver key: 서버 터미널에 출력된 키 입력

동승자 휴대폰은 **같은 Wi-Fi**에 연결한 후 브라우저에서 다음 주소를 엽니다.

`http://컴퓨터의-LAN-IP:8787/?code=CREW26`

동승자 이름 입력 → 참여 → 목적지 변경/경유지 요청 → 운전자 화면에서 승인/거절 → 동승자 현황 반영.

안드로이드 에뮬레이터의 `10.0.2.2` 주소는 **에뮬레이터에서 호스트 컴퓨터를 가리키는 전용 주소**로, 다른 휴대폰에서는 사용할 수 없습니다. 휴대폰에서는 컴퓨터의 실제 로컬 IPv4 주소를 사용하세요. 방화벽에서 8787 포트가 허용되어야 합니다.

### LAN 데모 보안 주의

- **외부 인터넷에 공개하거나 포트 포워딩하지 마세요.** 암호화된 외부 접속/정식 인증이 없는 LAN 전용 데모입니다.
- 서버는 시작할 때 임의 Driver key를 생성합니다. 동승자에게 공유하지 마세요.
- 동승자 토큰은 브라우저 `sessionStorage`에 저장되고, 하차 시 서버에서 폐기됩니다.
- 서버를 종료하면 시연 데이터는 사라집니다.
- 제출용으로 실제 주소나 개인 신상 정보 대신 예시 지점을 사용하세요.

## 3. 자동 테스트

```bash
cd CrewMate/server
npm test
```

Kotlin 경로 알고리즘 테스트는 별도 Kotlin 컴파일러가 있는 환경에서 다음처럼 실행할 수 있습니다.

```bash
cd CrewMate
kotlinc android-app/app/src/main/java/io/crewmate/app/core/RouteOptimizer.kt tests/kotlin/CoreSmokeTest.kt -include-runtime -d /tmp/crewmate-test.jar
java -jar /tmp/crewmate-test.jar
```

## 4. APK 빌드 및 위치

GitHub 저장소의 **Actions → CrewMate Android Build** 워크플로를 실행합니다. 성공하면 설치 가능한 테스트용 release APK가 다음 위치에 생성됩니다.

`CrewMate/apk/CrewMate_CrewMate_release.apk`

Actions 아티팩트 **CrewMate-release-demo-apk**로도 내려받을 수 있습니다. 워크플로는 결과물을 `main` 브랜치에 커밋하도록 시도합니다. 빌드가 실패하면 APK가 생성되지 않으므로 `Actions` 로그를 확인하세요.

APK는 매 빌드 생성되는 **임시 데모 서명키**로 서명합니다. 정식 배포/업데이트 서명에 부적합하며 동일 앱을 다시 설치할 때 삭제가 필요할 수 있습니다. 공모전 최종 release는 팀이 관리하는 고정 서명키로 다시 생성해야 합니다.

## 5. Pleos 연동 다음 단계

1. Pleos Connect Emulator v2.1.0 이상과 Android SDK API 34 설치
2. NaviHelper SDK v3.0.5 패키지를 문서대로 가져오기
3. 실제 승인받은 Navi 권한만 AndroidManifest에 추가
4. 하차 순서 계산의 **직선거리 거리행렬을 실제 도로 기반 경로 탐색**으로 교체
5. 운전자가 정차했을 때만 경로 승인/권한 변경 가능하도록 driving-state API 연동
6. 실제 내비게이션 변경 성공/실패 콜백 처리와 회귀 검증

공식 문서:
- https://document.pleos.ai/docs/pleos-only/getting-started
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro

## 제출 자료 기재 시 정확한 표현

> CrewMate Android(Kotlin) 운전자 앱 소스, 동승자 모바일 웹 및 LAN 통신 서버, 경로 계산·세션 권한 검증 로직 구현. 실제 Pleos NaviHelper 연동은 후속 개발 예정. GitHub Actions 빌드 통과 시 APK 및 링크 제출.
