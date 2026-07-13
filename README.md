# KoReady Backend

KoReady 백엔드 작업을 시작하기 위한 Spring Boot 스타터 레포지토리입니다.

## 기본 정보

- Java 21
- Spring Boot 3.5
- Gradle
- MySQL 런타임 드라이버
- H2 테스트 런타임

## 작업 흐름

1. 이슈를 먼저 만들고 작업 범위, 완료 조건, 우선순위를 적습니다.
2. 브랜치는 `feature/`, `fix/`, `docs/`, `chore/` 접두어를 사용합니다.
3. PR에는 관련 이슈, 변경 요약, 테스트 결과, 검토 포인트를 남깁니다.
4. `main` 반영은 PR 리뷰와 CI 통과 후 진행합니다.

## 자주 쓰는 명령

```bash
./gradlew test
./gradlew build
```

## 라벨 기준

이슈와 PR 라벨은 `.github/labels.yml`을 기준으로 관리합니다. 라벨 이름과 설명은 한국어로 작성되어 있으며, 한눈에 구분할 수 있도록 이모지를 붙였습니다.

