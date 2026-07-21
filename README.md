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

$env:SPRING_PROFILES_ACTIVE='local'
$env:DB_URL='jdbc:mysql://localhost:3306/koready?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul'
$env:DB_USERNAME='koready'
$env:DB_PASSWORD='koready-local'
./gradlew.bat bootRun
```

서버가 `http://localhost:8080/actuator/health/readiness`에서 `200`을 반환하면 두 번째
PowerShell에서 아래 seed를 실행합니다. 새 DB에서는 서버 시작 시 Flyway가 스키마를 먼저
생성해야 하므로 seed를 서버보다 먼저 실행하지 않습니다.

```powershell
./scripts/seed-local-places.ps1
./scripts/seed-local-user.ps1
./scripts/seed-local-buddy.ps1
```

로컬 시드는 화면 연동 확인용 장소 13개, 상태·정렬 확인용 축제 회차 4개와
`local-user` 개발 사용자, 공개 프로필 `local-buddy-demo`를 Docker Compose의 `mysql`
서비스에만 넣습니다. 개발 사용자는 서울 기본 위치와 `LOCAL_FOOD`, `NATURE` 관광 유형을
가지며 실제 개인정보는 포함하지 않습니다. Buddy seed는 실행 결과에 `profileId`를 출력하므로
`GET /api/v1/buddy-profiles/{profileId}`와 차단·해제 API를 Swagger에서 바로 시험할 수 있습니다.
또한 첫 번째 표출 장소를 demo Buddy가 저장하므로 해당 `placeId`의 `/places/{placeId}/mates`에서
공개 메이트 목록과 쪽지 가능 상태를 확인할 수 있습니다.
세 스크립트 모두 반복 실행해도 같은 행을 중복 생성하지 않으며 Aiven과 Render에는 적용되지 않습니다.

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
`contentId`, 공식 국문명, 관광 타입을 다시 확인한 뒤 주소·좌표와 서로 다른 이미지
4장이 모두 있는 경우에만 공개 가능한 장소로 저장합니다. 운영진이 연결을 확인한
관광공모전 수상작을 최우선으로 두고 KTO 대표 이미지, 상세 이미지 순으로 보충합니다.
10곳이 전부 준비된 후에만
`onb-kto-curated-v1` 후보 세트를 현재 버전으로 발행합니다.

```powershell
# 로컬 Docker MySQL에 등록
./scripts/bootstrap-curated-onboarding.ps1 -Profile local

# Aiven staging에 등록하는 명시적 일회성 작업
./scripts/bootstrap-curated-onboarding.ps1 -Profile staging
```

`staging` 실행은 `KTO_SNAPSHOT_STORAGE=s3`, `KTO_SNAPSHOT_S3_BUCKET`, AWS 인증을 갖춘
수집 환경에서만 허용된다. 이 설정이 없으면 외부 KTO 호출 전에 중단한다. Aiven에 저장된
원문 증빙이 개인 PC 경로를 가리키지 않도록 하기 위한 안전장치이며, S3 설정 전에는
`local` 프로필에서만 초기 등록을 검증한다.

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

Render와 같은 메모리 경계에서 이미지 부팅, Flyway, readiness를 한 번에 확인하려면
Git Bash 또는 WSL에서 다음 스모크를 실행합니다. 테스트는 격리된 Compose project와
로컬 전용 DB 값만 사용하고 종료 시 컨테이너와 volume을 제거합니다.

```bash
docker build --tag koready-backend:local .
./scripts/smoke-docker.sh koready-backend:local
```

기본 포트는 `8080`, 상태 확인 경로는 `/actuator/health`와 `/actuator/health/readiness`입니다.
Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인합니다.

### 로컬 Swagger 인증

실제 Google·Apple 로그인과 JWT가 연결되기 전까지 `local` 프로필에서는 Swagger 상단의
**Authorize**에 아래 공개 개발값 중 하나를 입력해 보호 API를 호출할 수 있습니다. Swagger가
`Bearer` 접두사를 붙이므로 값만 입력합니다.

| 입력값 | principal | role | 용도 |
|---|---|---|---|
| `local-user` | `local-user` | `USER` | 사용자·온보딩·추천 API |
| `local-operator` | `local-operator` | `OPERATOR` | 관리자 조회와 운영 편집 |
| `local-auditor` | `local-auditor` | `AUDITOR` | 관리자 읽기 전용 검토 |
| `local-admin` | `local-admin` | `ADMIN` | 관리자 전체 권한 검증 |

이 값은 secret이나 실제 access token이 아니다. filter는 `local` 프로필만 활성화되고
`staging`, `prod` 프로필이 함께 활성화되지 않았으며
`LOCAL_DEV_AUTH_ENABLED=true`가 동시에 적용될 때만 생성된다. `staging`, `prod`, 일반 `test`
프로필에서는 같은 값을 보내도 401이며, 임의 사용자명이나 role을 조립할 수 없다. 로컬에서도
실제 인증 흐름을 확인할 때는 `LOCAL_DEV_AUTH_ENABLED=false`로 끈다.

현재 로그인 없이 사용할 수 있는 구현 API는 다음과 같습니다.

- `GET /api/v1/monthly-recommendations`: 연월·날짜·권역·관광유형별 축제 추천
- `GET /api/v1/places`: 서비스 권역·관광유형별 장소 목록
- `GET /api/v1/places/search`: KoReady 장소 검색
- `GET /api/v1/places/{placeId}`: 장소 상세

인증·역할 검증까지 구현된 API는 다음과 같습니다.

- `PATCH /api/v1/users/me/language`: KO/EN 기본 언어와 가입 다음 단계 갱신
- `GET /api/v1/users/me/buddy-profile`: 내 Buddy 프로필 편집값 또는 `exists=false` 조회
- `PUT /api/v1/users/me/buddy-profile`: 언어·한국어 수준·Buddy 스타일·SNS 설정 전체 저장
- `GET /api/v1/places/{placeId}/mates`: 장소를 저장한 공개 Buddy 프로필의 최근순 cursor 목록
- `GET /api/v1/buddy-profiles/{profileId}`: 공개·SNS·차단 정책을 적용한 Buddy 프로필 상세
- `PUT /api/v1/users/me/blocked-profiles/{profileId}`: Buddy 프로필 멱등 차단과 최초 차단 시각 반환
- `DELETE /api/v1/users/me/blocked-profiles/{profileId}`: Buddy 프로필 멱등 차단 해제
- `GET /api/v1/message-threads`: 최신순 쪽지함, 미읽음 수, 차단·답장 가능 상태 조회
- `POST /api/v1/message-threads`: 장소별 첫 쪽지와 1:1 스레드의 멱등 생성
- `GET /api/v1/message-threads/{threadId}`: 과거 방향 cursor와 화면 표시 순서로 스레드 조회
- `POST /api/v1/message-threads/{threadId}/messages`: 참여자 답장 전송과 멱등 재시도
- `PUT /api/v1/message-threads/{threadId}/read`: 수신 메시지의 명시적·멱등 읽음 처리
- `POST /api/v1/reports`: 프로필·수신 메시지의 멱등 신고 접수와 운영 증빙 보존
- `GET /api/v1/users/me/onboarding`: 저장된 온보딩 진행 단계와 선택값 복구
- `PUT /api/v1/users/me/onboarding`: 위치 소유권·후보 버전·선택값 검증 후 멱등 완료
- `GET /api/v1/locations/search`: Kakao 주소·장소 정규화 검색과 10분 유효 서명 token 발급
- `GET /api/v1/users/me/locations`: 기본 위치 우선으로 저장 위치 목록 조회
- `POST /api/v1/users/me/locations`: 서명된 검색 결과를 주소·좌표 위변조 없이 저장
- `PUT /api/v1/users/me/locations/{locationId}/default`: 내 활성 위치를 기본 위치로 변경
- `DELETE /api/v1/users/me/locations/{locationId}`: 위치 soft delete와 대체 기본 위치 자동 지정
- `GET /api/v1/home`: 기본 위치·선호 언어·현재 월 축제 추천 미리보기
- `POST /api/v1/recommendation-decks`: 위치·여행 스타일 기반 K-Local Pick 덱 생성
- `GET /api/v1/recommendation-decks/{deckId}`: 고정된 추천 덱 페이지 조회
- `POST /api/v1/recommendation-decks/{deckId}/events`: 노출된 추천 카드의 탐색 행동 기록
- `GET /api/v1/users/me/saved-places`: 저장한 장소 최신순 cursor 목록
- `PUT /api/v1/users/me/saved-places/{placeId}`: 화면 출처를 포함한 멱등 장소 저장
- `DELETE /api/v1/users/me/saved-places/{placeId}`: 멱등 장소 저장 취소
- `GET /api/v1/onboarding/place-candidate-sets/current`: 현재 발행된 온보딩 관광지 후보 10개
- `/api/v1/admin/onboarding/place-candidate-sets/**`: 관리자 후보 세트 초안·조회·수정·발행·보관
- `/api/v1/admin/hori-tips/**`: 운영진 Hori Tip 초안·조회·수정·활성·비활성·보관과 변경 이력 기록
- `GET /api/v1/admin/open-api/summary`: 외부 API 기간별 성공·실패·snapshot 현황
- `GET /api/v1/admin/open-api/calls/**`: 마스킹된 호출 로그 목록과 상세
- `GET /api/v1/admin/open-api/snapshots/**`: 원천 본문을 제외한 immutable snapshot 메타데이터
- `POST /api/v1/admin/open-api/snapshots/{snapshotId}/download-url`: 감사 기록이 남는 5분 private S3 GET URL
- `GET /api/v1/admin/open-api/sync-cursors`: 외부 API operation별 동기화 위치와 성공·실패 현황
- `PUT /api/v1/admin/open-api/sync-cursors/{cursorId}/enabled`: ADMIN 전용 자동 동기화 활성·비활성 변경
- `POST /api/v1/admin/open-api/sync-cursors/{cursorId}/reset`: ADMIN 전용 지정 cursor 위치 초기화와 감사 기록
- `GET /api/v1/admin/batch-jobs/**`: 배치 작업·item의 안전한 실행 이력 조회
- `POST /api/v1/admin/batch-jobs`: KTO 일일 동기화·축제 수집 수동 접수
- `POST /api/v1/admin/batch-jobs/{jobId}/retry`: 실패·부분 실패 작업의 새 재시도 작업 접수
- `GET /api/v1/admin/data-quality/summary`: 관광지 준비도·누락 항목·번역 출처의 읽기 전용 집계

소셜 로그인과 JWT 발급은 후속 범위이므로 `staging`과 향후 `prod`에서 위 보호 API를 익명 또는 로컬 개발값으로 호출하면 `401`입니다. 로컬에서만 위 개발 인증 하네스를 사용할 수 있습니다. 위치 검색도 로컬에서는 비식별 fixture를 사용하며 실제 Kakao 호출은 `LOCATION_SEARCH_PROVIDER=kakao`, `KAKAO_REST_API_KEY`, 32바이트 이상의 `LOCATION_SEARCH_TOKEN_SECRET`을 secret 환경변수로 설정한 환경에서만 활성화합니다. private S3 snapshot은 `POST /api/v1/admin/open-api/snapshots/{snapshotId}/download-url`로 기본 5분짜리 GET URL을 발급합니다. `KTO_SNAPSHOT_STORAGE=s3`일 때 보관 정책과 만료 시각이 허용되는 KTO snapshot만 `downloadable=true`이며 URL과 AWS 자격증명은 감사 로그에 저장하지 않습니다. KTO 수동 배치는 일일 동기화와 축제 수집만 접수하며, `PENDING` 또는 `RUNNING` 작업이 있으면 새 작업을 `409`로 막습니다. 접수 응답 `202`는 완료가 아니라 대기열 등록이므로 `jobId`로 상태를 조회합니다. 일일 동기화는 `startPage`·`maxPages`, 축제 수집은 여기에 `eventStartDate`를 추가로 받으며 실패 또는 부분 실패 작업만 새 retry job으로 재시도할 수 있습니다. 공모전 증빙 ZIP은 `POST /api/v1/admin/evidence-bundles`로 접수하며 서버가 한 번에 하나씩 생성합니다. ZIP에는 마스킹된 호출·배치·cursor·품질 집계와 허용된 KTO 원본 표본만 들어가고, TMAP 원본·사용자 정보·토큰은 제외합니다. local 저장소에서는 생성·상태 조회까지 가능하며, `KTO_SNAPSHOT_STORAGE=s3` 환경에서만 완성 번들의 5분 signed URL을 발급합니다. sync cursor 변경 API는 외부 호출이나 배치를 시작하지 않고 관리 상태만 변경하며 실행자·사유·전후 값을 감사 로그에 남깁니다. 데이터 품질 요약은 외부 API를 새로 호출하지 않고 저장된 DB의 집계값만 반환합니다. 추천 덱은 테스트 principal과 MySQL fixture로 사용자별 고정 순서, 30일 재노출 제한, 소유권을 검증합니다. 온보딩 완료도 테스트 principal과 MySQL fixture로 검증하며, 태그 점수 정책 승인 전에는 `preferenceTags=[]`를 반환합니다. Buddy 프로필 PUT은 현재 form 전체를 교체합니다. 공개 상세와 장소별 메이트 목록은 프로필·SNS 공개 설정과 양방향 차단을 서버에서 적용하고 메시지 가능 여부를 계산합니다. 메이트 후보는 해당 장소의 활성 저장 기록만 사용하며 온보딩 취향 선택은 공개하지 않습니다. 차단 관계는 방향성 있게 저장하고 반복 PUT에서도 최초 차단 시각을 유지하며, 반복 DELETE는 기록 유무와 관계없이 204를 반환합니다. 첫 쪽지와 답장은 발신자별 `Idempotency-Key`를 DB에서 유일하게 보장하고 공개·수신 허용·양방향 차단을 전송 시점에 다시 검증합니다. 프로필 이미지 업로드, 소셜 로그인/JWT, TMAP Route와 관리자 제재 처리는 후속 범위입니다. 프론트는 Swagger 계약으로 먼저 연동하고, 백엔드는 MockMvc에서 사용자·관리자 역할별 계약을 검증합니다.

`local`과 `staging` 프로필에서는 `/swagger-ui.html`에서 프론트 연동용 Swagger UI를 제공합니다. UI는 `docs/koready-backend-design/openapi.yaml`을 빌드 시 포함한 단일 계약 파일을 표시합니다.

PM·디자인·프론트 회의에서는 [2026-07-19 개발 현황 및 협의 보고서](docs/MEETING_PROGRESS_REPORT_2026-07-19.md)에서 구현 범위, 신뢰도, 전체 API 입력·출력, 다음 결정 항목을 함께 확인합니다.

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
- KTO 수집 원본 gzip은 Git 저장소 밖의 로컬 snapshot 디렉터리에 저장하거나,
  명시적으로 선택한 수집 프로세스에서 서울 리전 private S3에 저장합니다.

기여 절차는 `CONTRIBUTING.md`, AI 개발 규칙은 `AGENTS.md`, 자동 품질 게이트는 `docs/AI_DEVELOPMENT_HARNESS.md`, 공개 가능한 데이터 기준은 `docs/PUBLIC_DATA_POLICY.md`를 참고합니다.
