# 초기 기반 검증 기록

2026-09-30 · Windows 11 · Temurin JDK 21.0.11 · Gradle 9.3.1

## 통과한 항목

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
| Android 입력 검사 | 7개 통과 | 빈 값·소수점·범위·날짜 검사 |
| Android Lint | 오류 0 | 남은 6개 경고는 고정한 의존성의 새 버전 안내 |
| 실제 PostgreSQL | 연결 확인 | Docker 29.6.2, PostgreSQL 17.11, Flyway V1 성공, health UP, 비인가 기록 조회 401 |
| HTML 안내서 | 브라우저 검사 통과 | 320/390/768/1440px, 5단계 전환·키보드·9개 펼쳐보기·로컬 링크, JS 오류 0 |

서버 **19개**, Android **7개** 테스트가 통과했다. 이 결과는 Google 인증, 실제 사용자의 기록 저장, 개인정보 법적 적합성, 실제 휴대폰 동작을 검증했다는 뜻이 아니다. PostgreSQL은 초기 스키마 적용과 서버 기동까지 실제 환경에서 확인했고 사용자 기록 API 통합은 후속 구현 대상이다.

## 아직 통과하지 않은 항목

| 항목 | 현재 상태 | 다음 확인 |
|---|---|---|
| GitHub Actions | 워크플로 파일 작성, 실제 실행 이력 없음 | 원격 저장소에 반영한 뒤 실행 결과 확인 |
| 에뮬레이터·휴대폰 | 미검증 | 화면·글자 확대·뒤로가기·키보드·통신 실패 확인 |
| Google·Health Connect·S3·OCR·AI | 미연결 | 각 기능 구현·자격증명·권한·개인정보 조건 준비 후 확인 |

Android에서 통과한 명령:

```text
android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

SDK 누락을 해결한 뒤 APK·테스트·Lint를 실행했다. 첫 Lint에서 발견한 API 27 전용 테마 속성은 버전별 리소스로 옮겼고, Debug 로컬 도메인 범위와 Android 12 이후 백업·기기 이전 제외 규칙을 명시했다. 서버/Android Wrapper는 공식 Gradle 9.3.1 배포본 체크섬으로 고정했다.

SDK는 사용자 PC의 기본 `AppData/Local/Android/Sdk`에 설치했으며 사용자 환경변수 `ANDROID_HOME`과 도구 PATH를 설정했다. 개인 경로가 담긴 `android/local.properties`와 임의 로컬 DB 비밀번호가 담긴 `.env`는 Git에서 제외한다. Android Studio·에뮬레이터·시스템 이미지는 이번 설치에 포함하지 않았다.

현재 로컬 소스는 전달받은 `https://github.com/BapEgg/RoutinLog.git` 원격과 연결되어 있으며, 이 검증 기록 작성 시 원격 푸시·앱 배포·유료 인프라 생성은 수행하지 않았다.
