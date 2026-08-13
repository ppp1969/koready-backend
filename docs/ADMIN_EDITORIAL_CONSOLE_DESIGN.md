# KoReady AI 장소 가공 관리자 페이지 설계

> 독립 관리자 웹의 확정 기술 구성과 구현 순서는
> `ADMIN_WEB_FRONTEND_IMPLEMENTATION_SPEC.md`를 기준으로 한다. 이 문서는 제품 정책과
> 화면 요구사항을 설명하는 참고 문서다.

## 1. 목적

관리자가 사진과 KTO 원문을 보고 먼저 가공할 장소를 선별한다. 관리자는 최종 문구를 승인하지 않는다. 큐에 등록된 장소는 AI 자동 검증을 통과하면 `READY`가 되고 추천·필터 화면에 사용할 수 있다.

사용자가 검색으로 찾은 미가공 장소의 상세 화면에 들어와도 같은 큐에 자동 등록된다. 관리자 요청은 `HIGH`, 사용자 요청은 `NORMAL` 우선순위다.

## 2. 프로젝트 경계

- 별도 저장소와 별도 Vercel 프로젝트로 배포한다.
- 권장 구성은 Next.js App Router, TypeScript, TanStack Query다.
- 관리자 웹은 DB와 AI 제공자를 직접 호출하지 않는다.
- 모든 데이터 변경은 KoReady 백엔드 관리자 API를 통한다.
- 실제 Spring AI worker는 백엔드 프로젝트에 두고 관리자 웹과 분리한다.

## 3. 사용자와 권한

MVP 사용자는 `ADMIN` 한 종류다. KoReady Google 로그인으로 받은 백엔드 access token을 사용한다. 일반 사용자 토큰으로 `/api/v1/admin/editorial/**`를 호출하면 `403`이어야 한다.

권장 세션 처리:

1. Google ID Token을 KoReady 로그인 API에 전달한다.
2. Vercel 서버 영역에서 KoReady access/refresh token을 보관한다.
3. 브라우저에는 `HttpOnly`, `Secure`, `SameSite=Lax` 세션 쿠키만 둔다.
4. 관리자 브라우저 번들에 DB 비밀번호, AI 키, 백엔드 운영 secret을 넣지 않는다.

## 4. 화면 구조

### 4.1 장소 후보

경로: `/places`

목적은 PM이 사진이 좋고 서비스에 적합한 장소를 빠르게 찾는 것이다.

필수 요소:

- 검색: 한글·영문 장소명
- 상태 필터: 전체, 미요청, 대기, 처리 중, 완료, 실패, 오래됨
- 카드 정보: 대표 사진, 한글명, 영문명, 권역, 한국어 원문 보유 여부, 가공 상태
- 동작: 상세 보기, AI 가공 큐 등록
- 커서 기반 다음 페이지

`READY` 장소를 다시 등록해도 새 비용이 발생하지 않아야 한다. 원문 지문과 프롬프트 버전이 같으면 백엔드가 기존 작업을 반환한다.

### 4.2 장소 후보 상세

경로: `/places/[placeId]`

화면은 두 개의 세로 영역으로 구성한다.

- 원본 영역: 최대 4장 이미지, KTO 한국어 원문, 한글·영문 제목, 주소, 권역, KoReady 분류
- 처리 영역: 현재 상태, 요청 시각, 트리거, 우선순위, 실패 사유

주요 버튼:

- `AI 가공 요청`: HIGH 우선순위 등록
- `다시 시도`: FAILED 작업을 같은 API로 재등록
- `공개 화면 보기`: READY인 경우에만 공개 상세 링크 제공

후보 상세 조회 자체는 큐를 등록하지 않는다. 공개 장소 상세 조회와 이 동작을 분리해야 PM의 단순 검토가 AI 비용으로 이어지지 않는다.

### 4.3 작업 큐

경로: `/jobs`

필수 열:

- 작업 ID
- 장소 ID와 장소명
- 상태
- 트리거 `PM_CURATED` 또는 `USER_DETAIL`
- 우선순위
- 시도 횟수
- 요청·시작·완료 시각
- 실패 코드와 요약

기본 정렬은 처리 대상 기준으로 `priority DESC`, `requestedAt ASC`다. 화면 조회 API의 커서는 ID 기반이며, 실제 worker polling 순서는 백엔드가 보장한다.

### 4.4 가공 결과

경로: `/places/[placeId]/result`

`READY` 결과를 한국어·영어 탭으로 확인한다.

- 관광목적 태그 정확히 2개
- 주제
- 한줄설명
- 간단소개
- 이렇게 즐겨보세요 3~5개
- 프롬프트 버전
- 생성 시각

MVP에는 승인 버튼을 두지 않는다. 운영상 잘못된 내용이 발견될 때 사용할 `숨김`과 `재생성` 기능은 후속 범위다.

## 5. 백엔드 API 계약

기본 URL은 배포 환경변수 `KOREADY_API_BASE_URL`로 주입한다.

### 후보 목록

```http
GET /api/v1/admin/editorial/candidates?query=김천&status=NOT_REQUESTED&size=20
Authorization: Bearer <admin-access-token>
```

응답 핵심 필드:

```json
{
  "items": [
    {
      "placeId": 123,
      "titleKo": "김천김밥축제",
      "titleEn": "Gimcheon Gimbap Festival",
      "region": "GYEONGSANG",
      "imageUrl": "https://...",
      "hasKoreanOverview": true,
      "editorialStatus": "NOT_REQUESTED",
      "requestedAt": null
    }
  ],
  "nextCursor": "123",
  "hasMore": true
}
```

### 후보 상세

```http
GET /api/v1/admin/editorial/candidates/{placeId}
Authorization: Bearer <admin-access-token>
```

이미지 목록, KTO 한국어 원문, 한·영 제목, 주소, 권역, 여행 유형과 작업 상태를 반환한다. 이 API는 큐를 만들지 않는다.

### PM 큐 등록 및 실패 재시도

```http
POST /api/v1/admin/editorial/places/{placeId}/queue
Authorization: Bearer <admin-access-token>
```

```json
{
  "jobId": "uuid",
  "placeId": 123,
  "status": "QUEUED",
  "priority": "HIGH",
  "triggerType": "PM_CURATED",
  "requestedAt": "2026-08-13T00:00:00Z",
  "created": true
}
```

`created=false`면 동일한 원문·프롬프트 작업이 이미 있다는 뜻이다.

### 작업 목록

```http
GET /api/v1/admin/editorial/jobs?status=FAILED&size=20
Authorization: Bearer <admin-access-token>
```

### 공개 장소 상세

```http
GET /api/v1/places/{placeId}
Accept-Language: ko
```

미가공 장소면 기본 장소 정보와 함께 다음 값을 반환하고 `NORMAL` 큐를 중복 없이 등록한다.

```json
{
  "editorialStatus": "QUEUED",
  "tags": [],
  "description": null
}
```

가공 완료 후:

```json
{
  "editorialStatus": "READY",
  "tags": [
    { "code": "FOOD", "label": "#음식" },
    { "code": "EXPERIENCE", "label": "#체험" }
  ],
  "description": {
    "topic": "김천에서 만나는 가장 맛있는 한 줄 여행",
    "oneLineDescription": "김밥을 주제로 먹고 만들며 즐기는 지역 축제예요.",
    "shortIntroduction": "익숙한 김밥을 색다른 방식으로 만나볼 수 있어요.",
    "enjoyPoints": [
      "다양한 김밥 부스에서 맛보기",
      "김밥 만들기 체험 참여하기",
      "축제 포토존에서 사진 찍기"
    ],
    "contentVersion": "koready-place-editorial-v1"
  }
}
```

## 6. 상태 처리

| 상태 | 화면 의미 | 관리자 동작 |
| --- | --- | --- |
| `NOT_REQUESTED` | 아직 비용 미발생 | 큐 등록 가능 |
| `QUEUED` | 처리 대기 | 중복 등록 금지 |
| `PROCESSING` | AI 호출 중 | 상태만 표시 |
| `READY` | 자동 검증 통과 | 공개 화면 확인 |
| `FAILED` | 생성 또는 검증 실패 | 실패 사유 확인, 재시도 |
| `STALE` | KTO 원문 또는 프롬프트 변경 | 재생성 요청 |

## 7. 비용 및 안전 장치

- 중복 키: `placeId + sourceFingerprint + promptVersion`
- PM 큐는 HIGH, 사용자 상세 큐는 NORMAL
- 검색 결과 목록 조회만으로는 큐를 만들지 않음
- 공개 상세 접근에서만 사용자 큐 등록
- worker 일일 처리량과 예상 비용 하드 제한
- 동시 처리 1~2건부터 시작
- 실패 재시도 최대 2회
- 한국어와 영어를 한 번의 구조화 응답으로 생성
- AI 출력은 JSON Schema와 서버 검증을 모두 통과해야 READY
- KTO 원문이 부족하면 내용을 만들지 않고 `INSUFFICIENT_SOURCE` 실패

## 8. Vercel 환경변수

```text
KOREADY_API_BASE_URL=https://api.koready.cloud/api/v1
KOREADY_GOOGLE_CLIENT_ID=<admin web oauth client id>
ADMIN_SESSION_SECRET=<random secret>
```

DB 접속 정보, Aiven 인증정보, AI provider key는 Vercel에 넣지 않는다.

## 9. 배포 순서

1. 백엔드 V41 migration과 관리자 API 배포
2. 관리자 Vercel 프로젝트 생성 및 Google 로그인 연결
3. 후보 목록·상세·큐·작업 화면 연결
4. Spring AI worker와 자동 검증 구현
5. PM 선별 장소를 우선 생성
6. READY 데이터 수와 품질 확인
7. EB의 `EDITORIAL_PUBLICATION_FILTER_ENABLED=true` 전환

7번 전까지 기존 추천 API는 현재 공개 조건을 유지한다. 전환 후 장소 목록·월별 추천·추천 덱은 READY 장소만 사용하고, 키워드 검색은 미가공 장소도 계속 반환한다.
