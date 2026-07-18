# AI 개발 하네스

## 목적

AI 개발 하네스는 에이전트의 코드 생성 능력보다 입력 계약과 자동 피드백을 먼저 고정합니다. KoReady의 모든 기능 작업은 같은 이슈 형식, 테스트 분류, 아키텍처 규칙과 완료 조건을 사용합니다.

핵심 흐름은 다음과 같습니다.

```text
이슈 명세 -> RED -> GREEN -> REFACTOR -> ./gradlew check -> 로컬 스모크 테스트 -> PR 자체 리뷰 -> CI -> main
```

## 실행 환경의 역할

- `local`: Docker MySQL과 로컬 Spring 서버로 가장 빠르게 구현·테스트하는 기본 개발 환경
- `staging`: main에 합쳐진 Swagger와 API를 프론트·PM이 함께 확인하는 Render+Aiven 공유 환경
- `prod`: 약 한 달 뒤 AWS Elastic Beanstalk 검토와 함께 확정할 운영 환경

기능 브랜치의 반복 확인을 위해 Render 배포를 기다리지 않습니다. 로컬에서 자동 테스트와 스모크 테스트를
완료한 뒤 PR을 만들고, main 통합본을 공유해야 할 때만 Render 결과를 확인합니다.

## 자동 품질 게이트

| 게이트 | 실행 위치 | 실패 조건 |
|---|---|---|
| 단위·슬라이스·아키텍처 테스트 | `test` | 동작 또는 구조 규칙 위반 |
| MySQL 통합 테스트 | `integrationTest` | Testcontainers 기반 애플리케이션 구동 실패 |
| JaCoCo | `jacocoTestCoverageVerification` | 전체 라인 커버리지 80% 미만 |
| 통합 검증 | `check` | 위 검증 중 하나라도 실패 |
| 컨테이너 빌드 | GitHub Actions | Docker 이미지 생성 실패 |
| secret 검사 | GitHub Actions | 자격증명 또는 민감정보 패턴 검출 |

GitHub의 필수 체크 이름은 기존 브랜치 보호 규칙과 호환되도록 `Gradle Test`, `Docker Build`, `Gitleaks`를 유지합니다. `Gradle Test` 내부에서는 `./gradlew clean check`를 실행합니다.

## 이슈에서 고정할 계약

구현 작업에는 `.github/ISSUE_TEMPLATE/development_spec.yml`을 사용합니다.

- 목표와 사용자 가치
- 구현 범위와 제외 범위
- 관찰 가능한 인수 조건
- API, 오류, DB와 외부 연동 계약
- 먼저 실패시킬 테스트와 테스트 계층
- 보안, 개인정보, 비용과 배포 위험
- 구현 전에 사람의 확인이 필요한 결정

결정 대기 항목이 있으면 해당 범위는 구현하지 않습니다. 에이전트가 합리적으로 보이는 정책을 임의로 확정하지 않도록 하는 장치입니다.

## 테스트 전략

| 종류 | 대상 | 규칙 |
|---|---|---|
| 단위 테스트 | domain, application 정책 | Spring context 없이 실행하고 정상·실패 흐름을 함께 검증 |
| 웹 테스트 | controller 계약, validation, 인증·인가 | MockMvc를 사용하고 상태 코드와 응답 계약을 검증 |
| 저장소 테스트 | JDBC RowMapper, SQL 쿼리 | MySQL과 차이가 중요한 경우 Testcontainers 사용 |
| 통합 테스트 | Flyway, profile, 실제 MySQL 구동 | `@Tag("integration")`, `*IntegrationTest` 이름 사용 |
| 아키텍처 테스트 | 계층 의존성, 순환 참조 | `ArchitectureRulesTest`에서 공통 검증 |

외부 API 테스트는 네트워크를 호출하지 않습니다. application 계층이 정의한 port를 가짜 구현으로 대체하고, 실제 client의 직렬화 계약만 별도 fixture로 검증합니다. fixture에는 secret과 개인 데이터를 넣지 않습니다.

## 명령

```powershell
# 빠른 개발 루프: integration 태그 제외
./gradlew test

# Docker 기반 MySQL 통합 테스트만 실행
./gradlew integrationTest

# PR 생성 전 필수: 전체 테스트 + 커버리지 검증
./gradlew clean check

# 배포 결과에 영향을 주는 경우
docker build --tag koready-backend:local .
```

HTML 테스트 결과는 `build/reports/tests`, 커버리지 결과는 `build/reports/jacoco/test/html`에서 확인합니다. CI는 실패 여부와 관계없이 보고서를 7일간 artifact로 보관합니다.

## AI 자체 리뷰

PR을 만들기 전에 다음을 확인합니다.

1. 이슈 범위를 넘어선 코드와 리팩터링이 없는가.
2. 실패 테스트가 실제 요구사항을 검증하고 우연히 실패한 것은 아닌가.
3. 테스트 삭제, 과도한 mock, 느슨한 assertion으로 통과시키지 않았는가.
4. controller, application, domain, infrastructure 의존 방향을 지켰는가.
5. API 키, 사용자 데이터, 운영 연결정보와 원본 외부 API 응답이 포함되지 않았는가.
6. 검증하지 못한 항목과 사람의 결정이 필요한 항목을 PR에 남겼는가.

## 예외 처리

품질 게이트가 잘못된 규칙 때문에 막히는 경우에도 테스트를 임시 비활성화하거나 커버리지 기준을 낮추지 않습니다. 별도 이슈에서 규칙의 목적, 대안과 영향 범위를 검토한 뒤 하네스 자체를 변경합니다.
