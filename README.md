# KoReady Backend

KoReady는 2026 관광공모전 참가를 위해 개발하는 외국인 유학생·교환학생·장기체류 외국인 중심의 한국 로컬 여행 준비 및 추천 서비스입니다. 한국관광공사 데이터를 기반으로 체류 위치와 여행 취향에 맞는 장소를 추천하고, 대중교통 이동과 여행 메이트 연결까지 지원합니다. 단기 여행자는 핵심 타깃 검증 이후 확장할 사용자군입니다.

## Product Scope

- 온보딩: 체류 위치, 여행 스타일, 선호 장소 수집
- K-Local Pick: 사용자 취향과 지역을 반영한 개인화 추천
- 월별 추천: 축제 기간과 계절성을 반영한 여행 후보
- 장소 탐색: 7개 서비스 권역 기반 검색과 상세 조회
- Buddy Route: TMAP 기반 대중교통 경로와 운영진 큐레이션 Hori Tip 조합
- Buddy Connect: 여행지 기반 공개 프로필, 메이트 탐색, 쪽지
- Admin Evidence: 외부 API 호출 증빙과 마스킹된 운영 자료 관리

## Tech Stack

- Java 21
- Spring Boot 4.1, Spring Framework 7
- Spring Web MVC, Validation, Security, OAuth2 Client
- Spring JDBC, Flyway
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

Java 21과 Docker가 필요합니다. 빠른 개발 피드백은 로컬 Docker MySQL과 로컬 Spring 서버를 기준으로 합니다.
아래 명령은 `.env.local`을 읽지 않으므로 Aiven 연결정보와 로컬 DB가 섞이지 않습니다.

```powershell
docker compose -p koready-local up -d mysql
./scripts/seed-local-places.ps1

$env:SPRING_PROFILES_ACTIVE='local'
$env:DB_URL='jdbc:mysql://localhost:3306/koready?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul'
$env:DB_USERNAME='koready'
$env:DB_PASSWORD='koready-local'
./gradlew.bat bootRun
```

로컬 시드는 화면 연동 확인용 장소 13개와 상태·정렬 확인용 축제 회차 4개를 Docker Compose의 `mysql` 서비스에만 넣습니다.
여러 번 실행해도 같은 장소를 갱신하며 Aiven과 Render에는 적용되지 않습니다.

### KTO 축제 수동 수집

KTO `searchFestival2` 축제는 Render에서 자동 실행하지 않고 개발자 PC에서 작은
페이지 단위로 수집합니다. `.env.local`의 `KTO_SERVICE_KEY`만 읽으며 키와 원본
응답은 콘솔이나 Git에 남기지 않습니다.

```powershell
# 2026-07-01 이후 축제의 첫 1페이지(최대 200건)
./scripts/import-kto-festivals.ps1 `
  -EventStartDate 20260701 `
  -StartPage 1 `
  -MaxPages 1 `
  -Profile local

# 다음 페이지부터 이어서 실행
./scripts/import-kto-festivals.ps1 `
  -EventStartDate 20260701 `
  -StartPage 2 `
  -MaxPages 1 `
  -Profile local
```

스크립트는 KoReady Docker MySQL의 공개 포트를 자동으로 찾고 Java 21을 사용합니다.
JVM은 시작 힙 128MB, 최대 힙 256MB, Metaspace 128MB로 제한되며 페이지는 직렬로
처리합니다. 원본 gzip은 기본적으로 저장소 밖의
`$HOME/.koready/kto-snapshots`에 저장됩니다. 같은 원본을 다시 실행하면 기존
snapshot과 DB 결과를 재사용합니다.

수집된 축제는 `show_flag=true`, `active=false`로 저장되어 자동 공개되지 않습니다.
운영진 검토 또는 별도 공개 작업 전까지 장소·월별 추천 API에는 노출되지 않습니다.
Aiven 소량 반영은 같은 명령에 `-Profile staging`을 명시한 경우에만 실행되며,
Render에는 scheduler를 두지 않습니다.

### 온보딩 대표 관광지 10곳 초기 등록

위치 입력 다음 단계에서 보여줄 대표 관광지는 운영 승인된 아래 10곳으로 고정합니다.

1. 경복궁
2. 광장시장
3. 국립중앙박물관
4. 한국민속촌
5. 경포해수욕장
6. 공주 공산성
7. 보령머드축제
8. 전북 전주 한옥마을
9. 부산 감천문화마을
10. 성산일출봉

초기 등록 명령은 이 목록 외의 검색 결과를 저장하지 않습니다. 각 장소의 KTO
`contentId`, 공식 국문명, 관광 타입을 다시 확인한 뒤 주소·좌표·대표 이미지가 모두
있는 경우에만 공개 가능한 장소로 저장합니다. 10곳이 전부 준비된 후에만
`onb-kto-curated-v1` 후보 세트를 현재 버전으로 발행합니다.

```powershell
# 로컬 Docker MySQL에 등록
./scripts/bootstrap-curated-onboarding.ps1 -Profile local

# Aiven staging에 등록하는 명시적 일회성 작업
./scripts/bootstrap-curated-onboarding.ps1 -Profile staging
```

같은 명령을 다시 실행하면 KTO `contentId`와 고정 후보 세트 ID를 기준으로 기존
데이터를 갱신하며 장소나 후보 세트를 중복 생성하지 않습니다. 이미 발행된 후보
세트의 카드 순서나 문구가 승인 목록과 다르면 자동으로 덮어쓰지 않고 실패합니다.
KTO 영문 API는 국문 `contentId`를 그대로 조회할 수 없으므로 영문 표시는 이 승인
목록에서 관리하는 이름을 `MANUAL_EDITED` 출처로 저장합니다.

이 작업도 Render 시작 시 자동 실행되지 않습니다. JVM은 heap 128~256MB,
Metaspace 128MB로 제한하고 검색 1회와 상세 1회를 장소별로 순차 호출합니다.

PC의 기존 MySQL이 3306을 사용 중이면 다음처럼 KoReady MySQL만 3307로 띄웁니다.

```powershell
$env:DB_PORT='3307'
docker compose -p koready-local up -d mysql
$env:DB_URL='jdbc:mysql://localhost:3307/koready?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul'
```

애플리케이션까지 컨테이너로 실행하려면:

```powershell
docker compose -p koready-local --profile full up --build
```

기본 포트는 `8080`, 상태 확인 경로는 `/actuator/health`와 `/actuator/health/readiness`입니다.
Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인합니다.

현재 로그인 없이 사용할 수 있는 구현 API는 다음과 같습니다.

- `GET /api/v1/monthly-recommendations`: 연월·날짜·권역·관광유형별 축제 추천
- `GET /api/v1/places`: 서비스 권역·관광유형별 장소 목록
- `GET /api/v1/places/search`: KoReady 장소 검색
- `GET /api/v1/places/{placeId}`: 장소 상세

인증·역할 검증까지 구현된 API는 다음과 같습니다.

- `GET /api/v1/home`: 기본 위치·선호 언어·현재 월 축제 추천 미리보기
- `POST /api/v1/recommendation-decks`: 위치·여행 스타일 기반 K-Local Pick 덱 생성
- `GET /api/v1/recommendation-decks/{deckId}`: 고정된 추천 덱 페이지 조회
- `GET /api/v1/onboarding/place-candidate-sets/current`: 현재 발행된 온보딩 관광지 후보 10개
- `/api/v1/admin/onboarding/place-candidate-sets/**`: 관리자 후보 세트 초안·조회·수정·발행·보관

소셜 로그인과 JWT 발급은 후속 범위이므로 현재 실행 환경에서는 위 보호 API를 익명으로 호출하면 `401`입니다. 추천 덱은 테스트 principal과 MySQL fixture로 사용자별 고정 순서, 30일 재노출 제한, 소유권을 검증합니다. 프론트는 Swagger 계약으로 먼저 연동하고, 백엔드는 MockMvc에서 사용자·관리자 역할별 계약을 검증합니다.

`local`과 `staging` 프로필에서는 `/swagger-ui.html`에서 프론트 연동용 Swagger UI를 제공합니다. UI는 `docs/koready-backend-design/openapi.yaml`을 빌드 시 포함한 단일 계약 파일을 표시합니다.

## Verification

```powershell
# 빠른 단위·슬라이스·아키텍처 테스트
./gradlew test

# Docker 기반 MySQL 통합 테스트
./gradlew integrationTest

# PR 전 필수 품질 게이트와 80% 커버리지 검증
./gradlew clean check

docker build --tag koready-backend:local .
```

`test`는 빠른 피드백을 위해 `integration` 태그를 제외합니다. `check`는 일반 테스트, Testcontainers MySQL 통합 테스트, ArchUnit 의존성 규칙과 JaCoCo 라인 커버리지 80% 기준을 모두 실행합니다. Docker가 없는 환경에서는 Testcontainers 테스트만 건너뜁니다.

## Deployment

- 기능 개발과 API 스모크 테스트는 로컬에서 먼저 완료합니다.
- `main` CI 통과 후 Render의 `staging` 환경을 자동 배포하고 Aiven MySQL에 연결합니다.
- Render는 프론트·PM이 통합된 Swagger와 API를 확인하는 공유 환경이며 일상 개발 서버로 사용하지 않습니다.
- Render 설정 초안은 `render.yaml`에서 관리하며 secret 값은 저장소에 기록하지 않습니다.
- Aiven 연결 및 IP 허용목록 절차는 `docs/AIVEN_STAGING.md`에서 관리합니다.
- Render 약 500MB 환경의 KTO 페이지·동시성·JVM 메모리 기준은
  `docs/KTO_BATCH_OPERATIONS.md`에서 관리합니다.
- KTO 원본 snapshot용 서울 리전 private S3와 IAM 기준은
  `docs/AWS_S3_SNAPSHOT_STORAGE.md`에서 관리합니다. local 저장이 기본이며 S3는
  명시적으로 선택한 수집 프로세스에서만 사용합니다.
- AWS EC2/Elastic Beanstalk를 포함한 운영 인프라는 빠른 기능 개발 이후 약 한 달 뒤 다시 결정합니다.

## API Conventions

- Base URL: `/api/v1`
- Auth: 기본은 `Authorization: Bearer <accessToken>`, 문서에서 `security: []`인 읽기 API는 익명 호출 가능
- Language: `Accept-Language: ko-KR` 또는 `en-US`
- Timezone: `Asia/Seoul`
- JSON field naming: `camelCase`
- List response: `data.items`, `data.nextCursor`, `data.hasMore`

## Secret Handling

- `.env`, `.env.local`, `.env.*.local`은 Git에서 제외합니다.
- `.env.example`에는 placeholder만 둡니다.
- Aiven 연결정보가 든 `.env.local`을 `docker compose --env-file`로 전달하지 않습니다.
- API 키, OAuth/JWT 토큰, Authorization 헤더, 개인 위치정보를 로그·문서·fixture에 기록하지 않습니다.
- 원본 외부 API 응답은 공개 안전성 검토와 마스킹을 통과한 경우에만 저장합니다.
- KTO 수집 원본 gzip은 Git 저장소 밖의 로컬 snapshot 디렉터리에만 저장하고,
  향후 AWS 전환 시 private S3로 교체합니다.

기여 절차는 `CONTRIBUTING.md`, AI 개발 규칙은 `AGENTS.md`, 자동 품질 게이트는 `docs/AI_DEVELOPMENT_HARNESS.md`, 공개 가능한 데이터 기준은 `docs/PUBLIC_DATA_POLICY.md`를 참고합니다.
