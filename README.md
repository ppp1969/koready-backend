# KoReady Backend

KoReady는 2026 관광공모전 참가를 목표로 만드는 외국인 방한·체류자 맞춤형 한국 로컬 여행 준비 서비스입니다. 한국관광공사 TourAPI 데이터를 기반으로 사용자의 체류 위치, 여행 목적, 관심 스타일을 반영해 관광지·축제·로컬 경험을 추천하고, 대중교통 이동 안내와 여행 메이트 연결까지 확장하는 백엔드입니다.

## Product Scope

- 온보딩: 방문 목적, 현재 체류 위치, 여행 스타일, 선호 장소 수집
- K-Local Pick: 한국관광공사 관광정보를 기반으로 한 개인화 추천 카드
- 월별 추천: 축제·행사 기간과 계절 힌트를 반영한 월별 여행 후보
- 장소 탐색: 7개 서비스 권역 기준의 장소 목록, 검색, 상세 조회
- Buddy Route: 저장 위치에서 목적지까지 대중교통 경로 요약과 Hori Tip 제공
- Buddy Connect: 여행지 기반 공개 프로필, 메이트 조회, 쪽지 스레드
- Admin Evidence: 외부 API 호출 로그와 마스킹된 증빙 번들 관리

## Tech Stack

- Java 21
- Spring Boot 3.5
- Gradle Wrapper
- Spring Web, Validation, Security, OAuth2 Client
- Spring Data JPA
- MySQL runtime
- H2 test runtime
- JUnit 5

## External Data Sources

- 한국관광공사 국문 관광정보 서비스
- 한국관광공사 영문 관광정보 서비스
- 한국관광공사 관광사진 정보
- 한국관광공사 관광공모전 사진 수상작 정보
- 한국관광공사 관광지별 연관 관광지 정보
- TMAP 대중교통 API
- Kakao 주소/장소 검색 API

외부 API 키와 OAuth 토큰은 로컬 환경변수 또는 git에서 제외된 `.env.local`에만 둡니다. 저장소에는 실제 키를 커밋하지 않습니다.

## Local Development

```bash
./gradlew test
./gradlew bootRun
```

Windows PowerShell에서 Java 경로가 꼬인 경우:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot'
./gradlew test
```

로컬 환경변수 예시는 `.env.example`을 참고합니다. 실제 값은 `.env.local`에 작성하고 커밋하지 않습니다.

## API Conventions

- Base URL: `/api/v1`
- Auth: `Authorization: Bearer <accessToken>`
- Language: `Accept-Language: ko-KR` 또는 `en-US`
- Timezone: `Asia/Seoul`
- JSON field naming: `camelCase`
- List response: `data.items`, `data.nextCursor`, `data.hasMore`

주요 계약 문서는 `docs/koready-backend-design` 아래에서 관리합니다. 문서를 공개 저장소에 올릴 때는 API 키, 토큰, 개인 위치 정보, 이메일, 원본 provider payload가 제거됐는지 먼저 확인합니다.

## GitHub Workflow

1. 이슈로 작업 범위와 완료 조건을 남깁니다.
2. 브랜치는 `feature/`, `fix/`, `docs/`, `chore/`, `refactor/` 접두어를 사용합니다.
3. PR에는 관련 이슈, 변경 요약, 테스트 결과, 리뷰 포인트를 적습니다.
4. `main` 반영은 PR 리뷰와 CI 통과 후 진행합니다.

## Secret Handling

- `.env`, `.env.local`, `.env.*.local`은 git에서 제외합니다.
- `.env.example`에는 placeholder만 둡니다.
- `serviceKey`, `appKey`, OAuth token, JWT, refresh token, authorization header는 로그·문서·snapshot·API 응답에 원문으로 남기지 않습니다.
- 외부 API 호출 증빙에는 secret과 PII를 마스킹한 메타데이터만 포함합니다.
- public repo 전환 전에는 `git grep` 기반 민감정보 스캔과 GitHub Secret Scanning 설정을 확인합니다.

