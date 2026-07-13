# KoReady Backend

KoReady는 2026 관광공모전 참가를 위해 개발하는 외국인 방한·체류자 맞춤형 한국 로컬 여행 준비 및 추천 서비스입니다. 한국관광공사 데이터를 기반으로 체류 위치와 여행 취향에 맞는 장소를 추천하고, 대중교통 이동과 여행 메이트 연결까지 지원합니다.

## Product Scope

- 온보딩: 방문 목적, 체류 위치, 여행 스타일, 선호 장소 수집
- K-Local Pick: 사용자 취향과 지역을 반영한 개인화 추천
- 월별 추천: 축제 기간과 계절성을 반영한 여행 후보
- 장소 탐색: 7개 서비스 권역 기반 검색과 상세 조회
- Buddy Route: TMAP 기반 대중교통 경로와 Hori Tip
- Buddy Connect: 여행지 기반 공개 프로필, 메이트 탐색, 쪽지
- Admin Evidence: 외부 API 호출 증빙과 마스킹된 운영 자료 관리

## Tech Stack

- Java 21
- Spring Boot 4.1, Spring Framework 7
- Spring Web MVC, Validation, Security, OAuth2 Client
- Spring Data JPA, Flyway
- MySQL 8.x, H2 test runtime, Testcontainers
- Gradle Wrapper
- Docker, Render staging, Aiven for MySQL

## Profiles

| Profile | Purpose | Database |
|---|---|---|
| `test` | Gradle 자동 테스트 | H2 또는 Testcontainers MySQL |
| `local` | 로컬 개발 | Docker MySQL |
| `staging` | Render 테스트 환경 | Aiven for MySQL |
| `prod` | 향후 AWS 운영 환경 | 인프라 결정 보류 |

`staging`은 `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`가 없으면 시작하지 않습니다. Aiven 연결에는 기본적으로 `sslmode=require`를 적용합니다.

## Local Development

Java 21과 Docker가 필요합니다. 환경변수 이름은 `.env.example`을 기준으로 관리합니다.

```powershell
Copy-Item .env.example .env.local
docker compose --env-file .env.local up -d mysql
$env:SPRING_PROFILES_ACTIVE='local'
$env:DB_PASSWORD='replace-with-local-mysql-password'
./gradlew bootRun
```

애플리케이션까지 컨테이너로 실행하려면:

```powershell
docker compose --env-file .env.local --profile full up --build
```

기본 포트는 `8080`, 상태 확인 경로는 `/actuator/health`와 `/actuator/health/readiness`입니다.

## Verification

```powershell
./gradlew clean test
docker build --tag koready-backend:local .
```

Docker를 사용할 수 있으면 Testcontainers MySQL 통합 테스트가 실행됩니다. Docker가 없는 환경에서는 해당 테스트만 건너뜁니다.

## Deployment

- `main` CI 통과 후 Render의 `staging` 환경을 자동 배포하고 Aiven MySQL에 연결합니다.
- Render 설정 초안은 `render.yaml`에서 관리하며 secret 값은 저장소에 기록하지 않습니다.
- Aiven 연결 및 IP 허용목록 절차는 `docs/AIVEN_STAGING.md`에서 관리합니다.
- AWS EC2/Elastic Beanstalk를 포함한 운영 인프라는 빠른 기능 개발 이후 약 한 달 뒤 다시 결정합니다.

## API Conventions

- Base URL: `/api/v1`
- Auth: `Authorization: Bearer <accessToken>`
- Language: `Accept-Language: ko-KR` 또는 `en-US`
- Timezone: `Asia/Seoul`
- JSON field naming: `camelCase`
- List response: `data.items`, `data.nextCursor`, `data.hasMore`

## Secret Handling

- `.env`, `.env.local`, `.env.*.local`은 Git에서 제외합니다.
- `.env.example`에는 placeholder만 둡니다.
- API 키, OAuth/JWT 토큰, Authorization 헤더, 개인 위치정보를 로그·문서·fixture에 기록하지 않습니다.
- 원본 외부 API 응답은 공개 안전성 검토와 마스킹을 통과한 경우에만 저장합니다.

기여 절차는 `CONTRIBUTING.md`, AI 개발 규칙은 `AGENTS.md`, 공개 가능한 데이터 기준은 `docs/PUBLIC_DATA_POLICY.md`를 참고합니다.
