# 프로젝트 검증 기록

## 현재 상태 · Android 디자인 이식 · 2026-09-30

기존 HTML 67개 화면을 Kotlin Compose 화면으로 구현했다. 색상·카드 계층·SUIT 글꼴·Lucide 아이콘·하단 내비게이션·편집 바텀시트를 옮기고 모바일 입력/선택 흐름을 연결했다. 네이버 로그인을 제외하고 E01은 온라인 전용 연결 재시도 안내로 바꿨다. 화면 구현과 실제 계정·서버 연결은 별도 단계다.

| 확인 | 결과 |
|---|---|
| Android 빌드·Lint | 통과, 오류 0. 경고 11개는 라이브러리/빌드 도구 갱신 안내 8개와 공통 함수 Modifier 인자 스타일 3개 |
| Android 글꼴 | 로컬 SUIT TTF 4개 굵기(400·500·600·700)를 Compose 테마에 적용, 라이선스 동봉 |
| JVM 단위 검사 | 18개 통과: 측정 입력 7, 화면 상태 4, 영양·레시피 7 |
| API 36 에뮬레이터 UI 검사 | 5개 통과: 전체 67화면/뒤로가기, 게스트 탭, 200g 식품량, 필수동의 차단, 세트 완료→휴식→다음 세트 |
| 실제 앱 화면 캡처 | 67개 화면 캡처 완료, HTML 디자인 시안과 별도의 Android 갤러리로 제공 |
| 화면 배치 확인 | 320dp 화면과 약 393dp·글자 130%에서 홈·컨디션·식품량·프로그램·세트 기록·리포트·설정 확인. 큰 글씨의 리포트 버튼 줄바꿈 수정 |
| HTML 갤러리 | 320·390·768·1440px에서 이미지 67개 로드, 분류, 확대, 키보드 탐색 통과. JS 오류·가로 넘침 없음 |
| 원격 CI | 코드 8e084ba · [실행 36686771964](https://github.com/BapEgg/RoutinLog/actions/runs/36686771964) 서버·Android 모두 통과. UI 테스트 APK는 CI에서 빌드하고, 5개 UI 테스트 실행은 로컬 에뮬레이터에서 확인 |
| 실제 값 변화 | 음식 양·구성 합계, 부분 영양합계/null, 끼니 편집, 세트 입력·타이머, 날짜별 컨디션·측정, 설정 단위 변환을 메모리 상태로 연결 |

실행 명령: `android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --no-daemon`.

실제 Android 캡처는 [화면 갤러리](android-preview.html)에서 본다. 테스트용 `preview_route` Intent는 Debug 빌드에서만 처리한다. UI 상태는 메모리에만 있으며 프로세스 종료 시 초기화된다. 영구 저장이나 서버 응답을 대신하지 않는다. 신체·식사·운동·리포트의 기본 데이터는 가상 데이터이며, Google 인증·건강 권한·OCR·실제 데이터 삭제·AI의 성공 상태를 연출하지 않았다.

테스트 결과 파일은 `android/app/build/test-results/testDebugUnitTest/`와 `android/app/build/outputs/androidTest-results/connected/`에 생성된다. 이 검사는 모든 시각·접근성·실기기·운영 조건을 검증했다는 뜻은 아니다.

제공된 개발용 OAuth 설정은 Git에서 제외된 로컬 환경 파일에만 보관했다. 비밀값은 Android 소스·리소스·BuildConfig·이 문서에 넣지 않는다. 실제 로그인 연결은 후속 단계이며 웹 클라이언트 정보만으로 Android 패키지/서명 등록이 자동 완성되지는 않는다.

## 후속 검증

| 항목 | 현재 상태 | 다음 확인 |
|---|---|---|
| 실제 Android 휴대폰 | 미검증 | 기기별 화면·키보드·뒤로가기·통신 실패·배터리 확인 |
| 접근성·화면 대응 | 접근성 이름 적용, 위 7개 화면의 320dp·글자 130% 배치 확인 | 실제 TalkBack, 더 큰 글자·기기별 흐름 확인 |
| Google·Health Connect·S3·OCR·AI | 미연결 | 각 기능 구현·자격증명·권한·개인정보 조건 준비 후 확인 |
| 개인 기록 저장·실제 주간 집계 | 미연결 | 로그인 → 저장 → 다시 조회, 날짜·단위·목표 버전·누락값 검증 |

---

## 초기 기반 구축 이력 · 현재 Android 결과와 구분

아래는 화면 전체 이식 전의 검증 기록이다. 당시 Android는 시스템 글꼴의 초기 샘플 화면과 7개 입력 테스트로 시작했다. 현재는 위의 SUIT 적용·67개 화면·18개 단위 테스트·5개 에뮬레이터 UI 테스트 결과를 따른다. 원격 CI 결과는 아래에 명시된 커밋을 검증한 기록이며, 현재 로컬 UI 검증과 범위를 구분한다.

2026-09-30 · Windows 11 · Temurin JDK 21.0.11 · Gradle 9.3.1

### 초기 단계에서 통과한 항목

실행 명령: `server/gradlew -p server test bootJar --no-daemon`

| 검증 | 결과 | 범위 |
|---|---|---|
| Kotlin 서버 컴파일 | 통과 | Spring Boot 4.0.8, Kotlin 2.2.21 |
| 실행 JAR 생성 | 통과 | `routinlog-server-0.0.1-SNAPSHOT.jar` |
| 영양 계산 | 8개 통과 | 비례 계산, 원본 보존, 미확인 null/실제 0, 잘못된 기준량·범위 |
| 보안 경계 | 7개 통과 | 상태/health 공개, 개인 API·임의 토큰·관리 정보 차단, 기본 로그인 없음 |
| 스키마 제약 | 4개 통과 | H2 PostgreSQL 모드 + Flyway SQL + JPA validate, 중복/범위/계정 관계 |
| Android SDK | 설치 완료 | SDK Platform 36 revision 2, Build Tools 36.0.0, Platform Tools 37.0.1 |
| Android APK | 생성 통과 | Debug APK, SDK 36 / 최소 SDK 26 |
| Android 입력 검사 | 7개 통과 | 빈 값·소수점·범위·입력 날짜 보존 |
| Android Lint | 오류 0 | 남은 6개 경고는 고정한 의존성의 새 버전 안내 |
| 실제 PostgreSQL | 연결 확인 | Docker 29.6.2, PostgreSQL 17.11, Flyway V1 성공, health UP, 비인가 기록 조회 401 |
| HTML 안내서 | 브라우저 검사 통과 | 320/390/768/1440px, 5단계 전환·키보드·9개 펼쳐보기·로컬 링크, JS 오류 0 |
| GitHub Actions | 서버·Android 모두 통과 | 코드 5778df9, [실행 36681071379](https://github.com/BapEgg/RoutinLog/actions/runs/36681071379): 서버 테스트·실제 PostgreSQL 기동, Android 빌드·테스트·Lint |

초기 단계에서 서버 **19개**, Android **7개** 테스트가 통과했다. 서버 테스트 범위는 그대로 유지되고, 현재 Android 검사는 상단의 **18개 단위 + 5개 UI 테스트**로 늘었다. 이 결과는 Google 인증, 실제 사용자의 기록 저장, 개인정보 법적 적합성, 실제 휴대폰 동작을 검증했다는 뜻이 아니다. PostgreSQL은 초기 스키마 적용과 서버 기동까지 실제 환경에서 확인했고 사용자 기록 API 통합은 후속 구현 대상이다.

초기 Android 검증 명령:

```text
android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

SDK 누락을 해결한 뒤 APK·테스트·Lint를 실행했다. 첫 Lint에서 발견한 API 27 전용 테마 속성은 버전별 리소스로 옮겼고, Debug 로컬 도메인 범위와 Android 12 이후 백업·기기 이전 제외 규칙을 명시했다. 서버/Android Wrapper는 공식 Gradle 9.3.1 배포본 체크섬으로 고정했다.

SDK는 사용자 PC의 기본 `AppData/Local/Android/Sdk`에 설치했으며 사용자 환경변수 `ANDROID_HOME`과 도구 PATH를 설정했다. 개인 경로가 담긴 `android/local.properties`와 임의 로컬 DB 비밀번호가 담긴 `.env`는 Git에서 제외한다. 초기 SDK 설치에는 에뮬레이터·시스템 이미지를 포함하지 않았으나, 이후 API 36 에뮬레이터 환경을 추가해 상단의 UI 검사와 화면 캡처를 완료했다. Android Studio 설치 여부는 이 검증의 대상이 아니다.

초기 코드 `d2056bf7597df37477cb89ef1725c8bf513df721`를 `https://github.com/BapEgg/RoutinLog.git`의 main에 푸시했다. 첫 CI에서 SDK 설치 도구의 기본값이 폐기된 `tools` 패키지를 요청해 Android 준비 단계가 실패했다. `5778df9`에서 필요한 SDK 패키지를 명시한 뒤 서버·Android CI가 모두 통과했다. 이후 안내서 상태 갱신만 포함하는 커밋은 문서 경로 제외 규칙에 따라 CI를 다시 실행하지 않는다. 앱 스토어 배포·유료 인프라 생성은 수행하지 않았다. 문서 안내의 시작점은 Markdown README 대신 [ELI5 HTML 안내서](index.html)다.
