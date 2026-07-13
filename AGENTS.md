# KoReady AI 개발 지침

## 프로젝트 기준

- KoReady는 외국인 방한·체류자를 위한 한국 로컬 여행 준비 및 추천 백엔드다.
- 공개 저장소의 제품 개요와 개발 명령은 `README.md`를 우선한다.
- 제목에 초안으로 표시된 정책과 DB 모델은 확정본으로 간주하지 않는다.
- 외부 API 원본 응답은 공개 안전성 검토 전까지 커밋하지 않는다.

## 작업 시작 조건

- 구현 전에 GitHub 이슈를 만들고 목표, 범위, 제외 범위, 인수 조건을 확정한다.
- 기능 구현에는 `🤖 AI 개발 명세` 이슈 폼을 우선 사용한다.
- API·DB·외부 연동·보안 영향과 확인이 필요한 결정을 이슈에 명시한다.
- 요구사항이 모호하거나 결정 대기 항목이 있으면 추측해서 구현하지 않는다.
- 브랜치와 PR은 하나의 이슈와 하나의 목적만 다룬다.

## 필수 개발 루프

1. **RED**: 인수 조건을 재현하는 실패 테스트를 먼저 작성하고 예상한 이유로 실패하는지 확인한다.
2. **GREEN**: 테스트를 통과시키는 최소 구현만 추가한다.
3. **REFACTOR**: 테스트가 통과하는 상태에서 중복과 이름, 의존성을 정리한다.
4. `./gradlew clean check`로 전체 품질 게이트를 실행한다.
5. 변경이 배포 결과에 영향을 주면 Docker 이미지 빌드와 스테이징 스모크 테스트를 수행한다.

테스트를 삭제하거나 assertion을 약화하거나 무조건 성공하도록 바꿔 검증을 통과시키지 않는다. 버그 수정은 회귀 테스트가 먼저 실패해야 한다.

## 테스트 규칙

- 순수 도메인과 application 로직은 빠른 단위 테스트로 검증한다.
- controller 계약과 인증·인가 동작은 MockMvc 기반 슬라이스 또는 애플리케이션 테스트로 검증한다.
- repository, Flyway, MySQL 고유 동작은 Testcontainers 통합 테스트로 검증한다.
- Docker 통합 테스트에는 `@Tag("integration")`을 붙이고 클래스 이름은 `*IntegrationTest`로 끝낸다.
- 외부 API는 직접 호출하지 않고 port 또는 client 경계를 가짜 구현으로 대체한다.
- 새 동작에는 정상 흐름과 핵심 실패 흐름을 함께 추가한다.
- 전체 프로덕션 코드 라인 커버리지는 80% 이상을 유지한다.

## 아키텍처 규칙

- 패키지는 기능 도메인을 기준으로 나누고 그 아래에 `controller`, `application`, `domain`, `infrastructure`를 둔다.
- `domain`은 Spring, JPA와 바깥 계층에 의존하지 않는다.
- `application`은 `controller`와 `infrastructure`에 의존하지 않는다.
- `controller`와 `infrastructure`는 서로 직접 의존하지 않는다.
- `@RestController` 클래스는 `controller` 패키지에 둔다.
- 기능 패키지 사이에 순환 의존성을 만들지 않는다.
- 외부 API 응답과 JPA entity를 controller 응답으로 직접 노출하지 않는다.

위 규칙은 `ArchitectureRulesTest`에서 자동 검증한다. 예외가 필요하면 테스트를 끄지 말고 이슈에서 구조 변경을 먼저 합의한다.

## DB 및 설정 규칙

- DB 변경은 새 Flyway migration으로만 수행하고 적용된 migration 파일은 수정하지 않는다.
- 운영 코드에서 `ddl-auto=create`, `create-drop`, `update`를 사용하지 않는다.
- 자동 테스트는 `test`, 로컬 개발은 `local`, Render+Aiven은 `staging`, 향후 AWS 운영은 `prod` 프로필을 사용한다.
- 설정 변경 시 네 프로필의 영향 범위와 기본값을 확인한다.

## 보안 규칙

- 키, 토큰, 쿠키, Authorization 헤더, 개인 위치정보를 코드·문서·fixture·로그에 넣지 않는다.
- `.env.local`은 로컬 전용이며 `.env.example`에는 placeholder만 둔다.
- 외부 API fixture는 secret과 PII를 제거한 최소 응답만 저장한다.
- AI 도구에는 운영 AWS 자격증명, 운영 DB 계정, 실제 사용자 데이터를 제공하지 않는다.
- Aiven과 Render의 실제 연결정보는 각 서비스의 secret 환경변수로만 관리한다.

## 검증 명령

```powershell
# 빠른 피드백
./gradlew test

# Docker 기반 통합 테스트
./gradlew integrationTest

# PR 전 필수 품질 게이트
./gradlew clean check

# 배포 영향이 있는 변경
docker build --tag koready-backend:local .
```

## 완료 조건

- 이슈의 모든 인수 조건이 자동 테스트 또는 명시된 수동 검증으로 확인된다.
- `./gradlew clean check`와 GitHub 필수 체크가 통과한다.
- PR에 RED-GREEN-REFACTOR 기록과 검증 결과가 남아 있다.
- API 계약 또는 운영 방식이 바뀌면 README와 관련 설계 문서를 함께 갱신한다.
- 미해결 결정, 검증하지 못한 부분과 후속 작업을 숨기지 않고 PR에 기록한다.
