# Koready MVP API 계약

## 0. 문서 상태

이 문서는 최신 Figma `⭐️ UI` 페이지와 `07_CONFIRMED_PRODUCT_POLICIES.md`를 기준으로 작성한 MVP API 기준본이다.

관리자/OpenAPI 증빙 API는 `09_ADMIN_API_CONTRACT.md`에서 별도 정의한다.

- 작성 기준일: 2026-07-17
- Base URL: `/api/v1`
- 인증: `Authorization: Bearer <accessToken>`
- 콘텐츠 언어: `Accept-Language: ko-KR` 또는 `en-US`
- 서버 기준 시간대: `Asia/Seoul`
- JSON 필드명: `camelCase`
- endpoint와 schema는 `openapi.yaml`을 최종 기준으로 사용한다. 정책은 `07`, 사용자 API 설명은 `08`, 관리자 API 설명은 `09`를 따른다.

### 확정 범위

- 핵심 타깃은 외국인 유학생·교환학생·장기체류 외국인이며 단기 여행자는 확장 사용자다.
- 온보딩은 위치, 여행 스타일 1~4개, 관심 관광지 1~3개 순서이며 방문 목적을 수집하지 않는다.
- GPS를 사용하지 않는다. 위치는 주소/학교/동네 검색 결과만 저장한다.
- 편도 180분 미만만 당일치기 가능이며, 180분 이상은 숙박 권장이다.
- 쪽지 본문은 1~1,000자다.
- 한국어 수준은 `BEGINNER`, `INTERMEDIATE`, `ADVANCED` 세 값만 사용한다.
- 사용자 노출 명칭은 `Hori Tip`이다.
- KTX 한국 여행 가이드는 프론트 정적 콘텐츠이며 API를 만들지 않는다.
- 이동 경로는 TMAP 대중교통 API 결과를 Koready 응답으로 정규화한다.
- 온보딩 선호 여행지 후보는 관리자가 발행한 불변 버전의 10개 큐레이션 세트다.
- 위치 검색은 백엔드가 Kakao Local API를 호출하고, 위치 저장은 단기 `searchResultToken`으로 검증한다.

프론트 개발자를 위한 화면별 호출 순서, 상태 처리, TypeScript 예제는 `10_FRONTEND_API_FLOW_GUIDE.md`를 함께 본다.

---

## 1. 공통 계약

## 1.1 성공 응답

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 성공했습니다.",
  "data": {},
  "traceId": "01J2ABCDEF..."
}
```

목록 응답은 `data.items`, `data.nextCursor`, `data.hasMore`를 사용한다.

아래 endpoint별 JSON 예시는 공통 응답의 `data` 내부만 표시한다.

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 성공했습니다.",
  "data": {
    "items": [],
    "nextCursor": null,
    "hasMore": false
  },
  "traceId": "01J2ABCDEF..."
}
```

## 1.2 오류 응답

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "errors": [
    {
      "field": "travelStyles",
      "reason": "SIZE_OUT_OF_RANGE"
    }
  ],
  "traceId": "01J2ABCDEF..."
}
```

| HTTP | 사용 |
|---:|---|
| 200 | 조회/수정 성공 |
| 201 | 리소스 생성 성공 |
| 204 | 응답 본문 없는 삭제 성공 |
| 400 | 형식/검증 오류 |
| 401 | 인증 필요 또는 토큰 만료 |
| 403 | 소유권/공개범위/차단 정책 위반 |
| 404 | 리소스 또는 경로 없음 |
| 409 | 중복/상태 충돌 |
| 410 | 만료된 임시 경로 결과 |
| 422 | 비즈니스 규칙 위반 |
| 429 | 요청 제한 초과 |
| 503 | 외부 제공자 장애 |

## 1.3 ID와 날짜

- DB 식별자는 JSON number를 사용한다.
- `deckId`, `candidateSetId`, `routeId`, `cursor`는 opaque string으로 사용한다.
- 시각은 ISO 8601 offset 형식으로 반환한다.
- 행사 기간은 `yyyy-MM-dd`의 `LocalDate`로 반환한다.
- 금액은 정수 원 단위와 `currencyCode=KRW`를 사용한다.

## 1.4 주요 enum

```text
LanguageCode = KO | EN
SocialProvider = GOOGLE | APPLE
ExternalApiProvider = KTO | TMAP | KAKAO | AI
TranslationSource = KTO_KO | KTO_EN | AI_TRANSLATED | MANUAL_EDITED

TravelStyle =
  LOCAL_FOOD | LOCAL_FESTIVAL | TRADITIONAL_MARKET |
  CULTURE_EXPERIENCE | NATURE | EXHIBITION_MUSEUM | DRAMA_LOCATION

RecommendationScope = NEARBY | NATIONWIDE
ServiceRegionCode =
  SEOUL | GYEONGGI | GANGWON | CHUNGCHEONG |
  JEOLLA | GYEONGSANG | JEJU

SortType = RECOMMENDED | DEADLINE
DateFilterType = ALL | THIS_WEEK | THIS_MONTH | NEXT_MONTH | CUSTOM
FestivalOccurrenceStatus = UPCOMING | ONGOING | ENDED

RouteMode =
  WALK | BUS | SUBWAY | EXPRESS_BUS | TRAIN | AIRPLANE | FERRY | SHUTTLE_BUS
Difficulty = EASY | NORMAL | HARD
DayTripStatus = DAY_TRIP_AVAILABLE | STAY_RECOMMENDED
HoriTipPlacement = TOP_SUMMARY | AFTER_SEGMENT
HoriTipStatus = DRAFT | ACTIVE | INACTIVE | ARCHIVED
HoriTipScopeType = ALL_ROUTES | DESTINATION_PLACES
HoriTipSource = OPERATOR_CURATED

KoreanLevel = BEGINNER | INTERMEDIATE | ADVANCED
BuddyStyle =
  TRADITIONAL_CULTURE | CAFE_TOUR | FOODIE |
  PHOTOGRAPHY | HANOK_EXPERIENCE | QUIET_TRAVEL
```

`BuddyStyle`은 온보딩 `TravelStyle`과 별도 카탈로그로 관리한다.

---

## 2. 인증과 약관

## 2.1 소셜 로그인

`POST /api/v1/auth/social/login`

```json
{
  "provider": "GOOGLE",
  "idToken": "string",
  "authorizationCode": null,
  "deviceId": "device-uuid",
  "expoPushToken": null
}
```

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresInSeconds": 3600,
  "user": {
    "userId": 1,
    "email": "emma@example.com",
    "profileImageUrl": "https://...",
    "preferredLanguage": null
  },
  "nextStep": "TERMS"
}
```

```text
nextStep = TERMS | LANGUAGE | ONBOARDING | COMPLETED
```

## 2.2 토큰

| Method | URI | 설명 |
|---|---|---|
| POST | `/auth/refresh` | refresh token 회전 및 access token 재발급 |
| POST | `/auth/logout` | 현재 refresh token과 push token 비활성화 |

`POST /auth/refresh`

```json
{
  "refreshToken": "opaque-token",
  "deviceId": "device-uuid"
}
```

성공 시 기존 refresh token을 폐기하고 새 access/refresh token 쌍을 반환한다. 서버에는 refresh token 원문이 아니라 hash와 회전 이력을 저장한다.

`POST /auth/logout`

```json
{
  "refreshToken": "opaque-token",
  "deviceId": "device-uuid"
}
```

## 2.3 약관

| Method | URI | 설명 |
|---|---|---|
| GET | `/terms/required` | 최신 약관과 동의 필요 여부 조회 |
| PUT | `/users/me/term-agreements` | 약관 버전별 동의 상태 저장 |

```json
{
  "agreements": [
    {
      "termVersionId": 10,
      "agreed": true
    },
    {
      "termVersionId": 20,
      "agreed": false
    }
  ]
}
```

필수 약관 미동의 시 `422 REQUIRED_TERMS_NOT_AGREED`를 반환한다.

---

## 3. 사용자와 온보딩

## 3.1 내 정보

| Method | URI | 설명 |
|---|---|---|
| GET | `/users/me` | 기본 사용자/가입 진행 상태 조회 |
| PATCH | `/users/me/language` | KO/EN 선호 언어 변경 |

`PATCH /users/me/language`

```json
{
  "language": "KO"
}
```

응답은 저장된 언어와 서버가 계산한 다음 화면을 함께 반환한다.

```json
{
  "language": "KO",
  "nextStep": "ONBOARDING",
  "updatedAt": "2026-07-19T12:00:00+09:00"
}
```

| 현재 DB 상태 | 저장 뒤 DB 상태 | `nextStep` | 의미 |
|---|---|---|---|
| `NEED_TERMS` | `NEED_TERMS` | `TERMS` | 언어를 저장해도 필수 약관을 건너뛰지 않음 |
| `NEED_LANGUAGE` | `NEED_ONBOARDING` | `ONBOARDING` | 최초 언어 선택 완료 |
| `NEED_ONBOARDING` | `NEED_ONBOARDING` | `ONBOARDING` | 온보딩 중 언어만 변경 |
| `COMPLETED` | `COMPLETED` | `COMPLETED` | 홈 설정에서 언어만 변경 |

프론트는 현재 가입 상태를 직접 조합하지 않고 항상 응답의 `nextStep`을 사용한다. 같은 언어와
같은 상태를 다시 보내면 성공하되 `updatedAt`은 바뀌지 않는다.

## 3.2 온보딩 상태

`GET /api/v1/users/me/onboarding`

구현 상태는 `IMPLEMENTED`다. 보호 API이므로 인증 사용자의 `public_id`를 기준으로 자기 데이터만
조회한다. 소셜 로그인과 실제 token 발급이 연결되기 전에는 백엔드 테스트 principal과 DB
fixture로 검증한다.

```json
{
  "completed": false,
  "currentStep": "LOCATION",
  "currentLocationId": null,
  "travelStyles": [],
  "selectedPreferencePlaceIds": []
}
```

온보딩 단계는 다음 데이터로 구성한다.

1. 현재 머무는 위치
2. 관심 여행 스타일 1~4개
3. 선호 여행지 1~3개

방문 목적, 출신 국가, 이동 범위는 온보딩 API와 DB에 포함하지 않는다. `currentStep`은 `LOCATION | TRAVEL_STYLES | PREFERENCE_PLACES | COMPLETED` 중 하나다.

| `currentStep` | 서버가 확인한 저장 상태 | 프론트 동작 |
|---|---|---|
| `LOCATION` | 활성 상태인 내 위치가 없음 | 위치 입력 화면 표시 |
| `TRAVEL_STYLES` | 위치는 있고 관광 유형이 없음 | 관광 유형 화면 표시 |
| `PREFERENCE_PLACES` | 위치와 관광 유형이 있음 | 후보 10개 조회 뒤 관심 관광지 화면 표시 |
| `COMPLETED` | 가입 상태와 완료 시각이 저장됨 | 온보딩을 다시 열지 않고 홈으로 이동 |

완료된 사용자는 저장 당시의 `candidateSetId`, `candidateSetVersion`,
`selectedPreferencePlaceIds`도 받는다. 이 값은 어떤 후보 버전에서 취향을 선택했는지 추적하기
위한 것으로, 프론트가 최신 후보 세트로 바꾸어 계산하지 않는다.

현재 완료 API는 마지막 버튼에서 전체 값을 한 번에 저장한다. 따라서 서버에 아직 보내지 않은
현재 화면의 임시 선택은 프론트 상태에 남고, GET은 **이미 서버에 저장된 값만** 복구한다.
위치 저장 API가 구현되면 저장된 위치 단계가 이어지고, 관광 유형의 단계별 자동 저장은 별도
계약을 추가하기 전까지 완료 요청 시점에 확정한다.

## 3.3 현재 취향 후보 세트 조회

`GET /api/v1/onboarding/place-candidate-sets/current`

이 API는 사용자별 후보를 생성하지 않는다. 관리자가 검수하고 발행한 현재 버전의 정확히 10개 후보를 모든 사용자에게 반환한다.

```json
{
  "candidateSetId": "onb-curation-2026-07-v1",
  "version": 1,
  "status": "PUBLISHED",
  "publishedAt": "2026-07-13T12:00:00+09:00",
  "minSelection": 1,
  "maxSelection": 3,
  "items": [
    {
      "placeId": 1001,
      "title": "국립중앙박물관",
      "imageUrl": "https://...",
      "serviceRegionCode": "SEOUL",
      "tags": ["역사", "전시"],
      "curatorMessage": "한국의 역사와 현대적인 전시를 함께 만날 수 있어요.",
      "displayOrder": 1
    }
  ]
}
```

- 후보는 항상 정확히 10개다.
- 발행된 세트는 수정하지 않으며 새 버전을 발행한다.
- 프론트는 `candidateSetId`와 `version`을 화면 상태에 함께 보관한다.
- 새 버전이 발행돼도 사용자가 이미 조회한 과거 발행 버전의 유효한 선택은 제출할 수 있다.

## 3.4 온보딩 완료

`PUT /api/v1/users/me/onboarding`

구현 상태는 `IMPLEMENTED`다. 프론트는 후보 화면을 열 때 받은 세트 ID와 버전을 임의로
고치지 않고 선택 장소와 함께 보낸다.

```json
{
  "currentLocationId": 100,
  "travelStyles": ["LOCAL_FOOD", "LOCAL_FESTIVAL"],
  "candidateSetId": "onb-curation-2026-07-v1",
  "candidateSetVersion": 1,
  "selectedPreferencePlaceIds": [1001, 2001]
}
```

검증 규칙:

- 현재 위치는 인증 사용자의 활성 위치여야 한다.
- 여행 스타일은 1~4개이며 중복될 수 없다.
- 선호 여행지는 제출한 `candidateSetId/version`의 10개 후보에 포함된 1~3개여야 한다.
- 현재 세트가 바뀌었더라도 해당 세트가 과거에 발행된 이력이 있으면 제출할 수 있다.
- 보관된 과거 발행 세트는 허용하지만 발행된 적 없는 초안은 허용하지 않는다.
- 약관·언어 단계가 끝나 `NEED_ONBOARDING` 상태인 사용자만 최초 완료할 수 있다.
- 위치, 관광 유형, 후보 선택, 가입 완료 상태는 하나의 DB transaction으로 저장한다.
- 완료 요청은 멱등하게 처리한다. 같은 본문을 다시 보내면 최초 `completedAt`을 반환한다.
- 완료된 프로필을 다른 본문으로 덮어쓰려는 요청은 `409`로 거절한다.

성공 응답 예시:

```json
{
  "completed": true,
  "completedAt": "2026-07-19T14:00:00+09:00",
  "nextStep": "COMPLETED",
  "profile": {
    "currentLocation": {
      "locationId": 100,
      "displayName": "성신여자대학교",
      "serviceRegionCode": "SEOUL"
    },
    "travelStyles": ["LOCAL_FOOD", "LOCAL_FESTIVAL"],
    "selectedPreferencePlaceIds": [1001, 2001],
    "preferenceTags": []
  }
}
```

`preferenceTags`는 프론트가 만들어 보내는 값이 아니다. 태그 마스터와 점수·가중치 정책이
승인되기 전인 현재 MVP 구현은 빈 배열을 반환한다. 장소 선택 원본은 별도 테이블에 보존하므로
정책이 확정된 뒤 다시 계산할 수 있다.

| HTTP | `code` | 의미와 프론트 처리 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 필드, enum, 숫자·배열 형식을 확인한다. 같은 잘못된 본문을 자동 재시도하지 않는다. |
| 401 | `UNAUTHORIZED` | 로그인 연결 뒤에는 token 재발급 또는 로그인 화면으로 이동한다. |
| 409 | `ONBOARDING_ALREADY_COMPLETED` | 이미 다른 선택으로 완료됐다. GET으로 서버 상태를 다시 읽는다. |
| 422 | `ONBOARDING_LOCATION_INVALID` | 위치가 내 활성 위치인지 확인하고 위치 단계로 이동한다. |
| 422 | `ONBOARDING_TRAVEL_STYLES_INVALID` | 1~4개, 중복 없음 규칙을 확인한다. |
| 422 | `ONBOARDING_CANDIDATE_SET_INVALID` | 현재 후보 세트를 다시 조회하고 새 선택을 받는다. |
| 422 | `ONBOARDING_SELECTION_INVALID` | 1~3개인지, 모두 같은 후보 세트에 포함됐는지 확인한다. |
| 422 | `ONBOARDING_PREREQUISITE_INCOMPLETE` | 응답의 가입 단계에 맞춰 약관 또는 언어 화면으로 이동한다. |

네트워크가 끊겨 성공 응답을 못 받은 경우에는 사용자가 선택한 **같은 본문**으로 재시도한다.
서버는 완료 시각과 저장값을 바꾸지 않고 같은 완료 결과를 돌려준다.

---

## 4. 위치

GPS 기반 endpoint와 위도/경도 역지오코딩 endpoint는 제공하지 않는다.

## 4.1 위치 검색

`GET /api/v1/locations/search?query=성신여자대학교&limit=10`

구현 상태는 `IMPLEMENTED`다. `local` 프로필에서는 Kakao 키 없이도 프론트 연동을
진행할 수 있도록 성신여자대학교, 홍익대학교, 전주한옥마을의 비식별 개발 결과를 제공한다.
`staging`과 `prod`는 `LOCATION_SEARCH_PROVIDER=kakao`를 명시하고 Kakao REST API 키와
별도의 위치 token 서명 secret을 환경변수로 주입한 경우에만 실제 Kakao를 호출한다.

백엔드는 Kakao 주소 검색과 키워드 장소 검색을 호출하고 중복 제거, 주소 정규화, 7개 서비스 권역 매핑을 수행한다. 검색 결과 없음은 오류가 아니라 `items=[]`이다.

```json
{
  "items": [
    {
      "searchResultToken": "locsrch_opaque_signed_token",
      "provider": "KAKAO",
      "resultType": "PLACE",
      "providerPlaceId": "123456789",
      "name": "성신여자대학교 기숙사",
      "roadAddress": "서울특별시 성북구 보문로34가길 17",
      "address": "서울특별시 성북구 동선동3가 237",
      "latitude": 37.5913,
      "longitude": 127.0221,
      "sido": "서울특별시",
      "sigungu": "성북구",
      "serviceRegionCode": "SEOUL"
    }
  ]
}
```

- `resultType`은 `ADDRESS` 또는 `PLACE`다.
- `searchResultToken`은 정규화된 검색 결과를 서버가 서명한 단기 token이며 10분 후 만료한다.
- 프론트는 token을 문자열로만 보관하고 내부를 해석하지 않는다.
- 검색어와 Kakao 원본 응답은 장기 로그에 보관하지 않는다.
- provider timeout/장애는 `503 LOCATION_PROVIDER_UNAVAILABLE`, KoReady rate limit은 `429 RATE_LIMITED`다.
- `query`는 공백 제거 후 1~100자, `limit`은 1~20이며 잘못된 값은 `400 INVALID_REQUEST`다.
- Kakao 주소 검색은 요청당 최대 30개, 키워드 검색은 최대 15개라는 provider 제한 안에서 호출한 뒤 합친 결과를 `limit`까지 자른다.
- 장소 결과를 주소 결과보다 먼저 두고 같은 정규화 주소·좌표는 한 번만 반환한다.
- 지원하지 않는 해외 시도나 필수 주소·좌표가 없는 provider 행은 사용자 결과에서 제외한다.
- token은 HMAC-SHA256으로 서명하며 변조되거나 10분이 지난 token은 다음 위치 저장 단계에서 거절한다.

## 4.2 사용자 위치

| Method | URI | 설명 |
|---|---|---|
| GET | `/users/me/locations` | 활성 위치 목록 |
| POST | `/users/me/locations` | 검색 결과를 내 위치로 저장 |
| PUT | `/users/me/locations/{locationId}/default` | 기본 위치 지정 |
| DELETE | `/users/me/locations/{locationId}` | soft delete |

`POST /users/me/locations`

```json
{
  "searchResultToken": "locsrch_opaque_signed_token",
  "customLabel": "학교",
  "setDefault": true
}
```

서버는 token의 서명과 만료를 검증해 검색 당시 주소·좌표·권역을 저장한다. 클라이언트가 주소, 좌표, provider ID 또는 권역 코드를 위치 저장 요청에서 다시 보내지 않는다. token이 만료되면 `410 LOCATION_SEARCH_RESULT_EXPIRED`를 반환하고 프론트는 검색 화면으로 돌아가 재검색한다.

---

## 5. 홈과 월별 추천

## 5.1 홈

`GET /api/v1/home`

```json
{
  "currentLocation": {
    "locationId": 100,
    "displayName": "성북구 보문로30가길",
    "serviceRegionCode": "SEOUL"
  },
  "preferredLanguage": "KO",
  "monthlyRecommendation": {
    "year": 2026,
    "month": 5,
    "totalCount": 18,
    "items": []
  }
}
```

홈의 월별 미리보기는 현재 `year/month`를 명시하고, 진행 중·예정 축제를 종료 축제보다 우선한다. KTX 가이드 카드와 단계별 본문은 응답에 포함하지 않는다.

## 5.2 월별 추천 전체보기

`GET /api/v1/monthly-recommendations`

이 API는 로그인 없이 호출한다. 성공 code는 `MONTHLY_RECOMMENDATIONS_OK`이고, 로그인 기능이 붙기 전까지 모든 카드의 `saved`는 `false`다.

Query:

```text
year=2000..2100
month=1..12
serviceRegionCode=SEOUL|...
dateFilterType=ALL|THIS_WEEK|THIS_MONTH|NEXT_MONTH|CUSTOM
customStartDate=yyyy-MM-dd
customEndDate=yyyy-MM-dd
travelStyles=LOCAL_FESTIVAL,NATURE
sort=RECOMMENDED|DEADLINE
cursor=opaque
size=1..50
```

```json
{
  "year": 2026,
  "month": 5,
  "appliedFilters": {
    "year": 2026,
    "month": 5,
    "serviceRegionCode": null,
    "dateFilterType": "ALL",
    "customStartDate": null,
    "customEndDate": null,
    "travelStyles": ["LOCAL_FESTIVAL"],
    "sort": "RECOMMENDED"
  },
  "totalCount": 18,
  "items": [
    {
      "placeId": 1001,
      "title": "전주 이팝나무 축제",
      "serviceRegionCode": "JEOLLA",
      "serviceRegionName": "전라",
      "addressSummary": "전북 전주시",
      "imageUrl": "https://...",
      "festivalOccurrence": {
        "occurrenceId": 81001,
        "eventYear": 2026,
        "startDate": "2026-04-25",
        "endDate": "2026-04-26",
        "status": "ENDED",
        "dateRangeText": "4.25 - 4.26"
      },
      "travelStyle": "LOCAL_FESTIVAL",
      "tags": ["지역축제", "봄"],
      "saved": false
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

축제 회차는 시작일 6개월 전부터 상세 목록에 노출하며, 종료 뒤에도 해당 개최 `year/month`에는 `ENDED` 상태로 남긴다. 같은 축제가 매년 반복되면 각 개최 회차는 별도 `occurrenceId`, `eventYear`, 시작일, 종료일을 가진다. 상태는 `Asia/Seoul` 조회일 기준으로 계산하고 저장된 상태를 그대로 신뢰하지 않는다.

`dateRangeText`는 `Accept-Language`가 한국어면 `M.d - M.d`, 영어면 `MMM d, yyyy - MMM d, yyyy` 형태로 만든 표시용 값이다. 필터와 상태 계산에는 ISO 형식의 `startDate/endDate`를 사용한다.

`year/month`가 기본 조회 범위다. 기간 겹침은 `행사 시작일 <= 조회 종료일 && 행사 종료일 >= 조회 시작일`로 판정하므로 월말에 시작해 다음 달에 끝나는 행사도 두 달 목록에 각각 포함될 수 있다.

- `ALL`: 선택한 연월 전체
- `THIS_WEEK`: `Asia/Seoul` 오늘이 속한 월요일부터 일요일
- `THIS_MONTH`: `Asia/Seoul` 오늘이 속한 달
- `NEXT_MONTH`: `Asia/Seoul` 오늘의 다음 달
- `CUSTOM`: `customStartDate`부터 `customEndDate`까지, 두 날짜 모두 포함

`dateFilterType` 범위는 선택 연월과 교집합만 반환한다. 교집합이 없으면 오류가 아니라 `items=[]`, `totalCount=0`인 성공 응답이다. `CUSTOM`은 두 날짜가 모두 필요하고 시작일이 종료일보다 늦으면 `400 INVALID_DATE_RANGE`다. 다른 필터에서는 custom 날짜를 보내지 않는다.

`sort=RECOMMENDED`는 `ONGOING`, `UPCOMING`, `ENDED` 순의 상태를 품질 점수보다 먼저 적용한다. `sort=DEADLINE`은 종료일 오름차순이다. `totalCount`는 페이지 크기와 cursor에 관계없는 전체 필터 결과 수다. cursor는 필터·언어·정렬을 묶은 값이므로 조건을 바꾼 뒤 재사용하면 `400 INVALID_CURSOR`다.

---

## 6. K-Local Pick 추천

## 6.1 추천 덱 생성

`POST /api/v1/recommendation-decks`

```json
{
  "scope": "NEARBY",
  "originLocationId": 100,
  "size": 20
}
```

`originLocationId`가 없으면 기본 위치를 사용한다. 기본 위치가 없으면 `422 DEFAULT_LOCATION_REQUIRED`를 반환한다.

```json
{
  "deckId": "deck_01J2ABCDEF",
  "scope": "NEARBY",
  "originLocation": {
    "locationId": 100,
    "displayName": "성북구 보문로30가길",
    "serviceRegionCode": "SEOUL"
  },
  "cards": [
    {
      "placeId": 1001,
      "title": "경주 문화유산 나들이",
      "locationText": "경상북도 경주시",
      "imageUrl": "https://...",
      "saved": false,
      "tags": ["역사 여행", "카페 거리", "인생샷 명소"],
      "shortDescription": "서울을 떠나 한국의 살아있는 박물관을 탐험해 보세요.",
      "serviceRegionCode": "GYEONGSANG",
      "matchRank": 1,
      "matchReason": {
        "travelStyleMatched": true,
        "preferenceTagMatched": true,
        "matchedTagCodes": ["HISTORY", "LOCAL_CAFE"]
      }
    }
  ],
  "nextCursor": "cursor_2",
  "hasMore": true,
  "remainingThreshold": 5,
  "deduplication": {
    "guaranteedWithinDeck": true,
    "suppressionDays": 30
  }
}
```

## 6.2 덱 다음 페이지

`GET /api/v1/recommendation-decks/{deckId}?cursor=cursor_2`

- 동일 `deckId + cursor` 재요청은 동일한 순서와 결과를 반환한다.
- 같은 덱 안에서 `placeId`가 중복되면 안 된다.
- `CARD_SERVED`된 장소는 30일 동안 새 덱 후보에서 제외한다.
- MVP v1은 응답 포함 시각과 `suppressionDays=30`, `policyVersion=recommendation-suppression-v1`을 기록한다.
- 후보 소진이 지나치게 빠르다는 운영 지표가 확인되면 새 정책 버전에서 기록 시점 또는 기간을 조정하되 기존 이력을 소급 변경하지 않는다.

## 6.3 추천 이벤트

`POST /api/v1/recommendation-decks/{deckId}/events`

```json
{
  "placeId": 1001,
  "eventType": "PLACE_SAVED",
  "occurredAt": "2026-07-13T15:00:00+09:00"
}
```

```text
eventType =
  CARD_EXPANDED | CARD_PREVIOUS | CARD_NEXT |
  PLACE_DETAIL_CLICKED | PLACE_SAVED | PLACE_UNSAVED | ROUTE_OPENED
```

`CARD_SERVED`는 서버가 응답에 포함한 시점에만 기록하며 클라이언트 eventType으로 받지 않는다.

성공하면 `201 Created`와 다음 데이터를 반환한다.

```json
{
  "eventId": "recevt_01J2ABCDEF",
  "deckId": "rec_01J2ABCDEF",
  "placeId": 1001,
  "eventType": "PLACE_SAVED",
  "recordedAt": "2026-07-13T06:00:01Z"
}
```

- 프론트는 사용자의 실제 동작 한 번에 이벤트를 한 번만 보낸다. 네트워크 실패 시 자동 재시도하지 않는다.
- 이벤트 기록은 분석 보조 기능이다. 실패해도 카드 이동, 상세 진입, 경로 화면, 저장 결과를 취소하거나 막지 않는다.
- `PLACE_SAVED`와 `PLACE_UNSAVED`는 각각 저장·저장 취소 API가 성공한 뒤 보낸다.
- `occurredAt`은 사용자가 동작한 시각이다. 생략하면 서버 수신 시각으로 저장한다.
- `recordedAt`은 서버가 DB에 기록한 시각이다. 두 시각은 분석에만 쓰며 30일 재노출 제한을 새로 시작하거나 연장하지 않는다.
- 현재 사용자가 소유한 덱에서 이미 응답으로 노출된 `placeId`만 기록할 수 있다.
- 없는 덱, 타인 덱, 덱에 없는 장소, 아직 노출되지 않은 카드는 모두 `404 RECOMMENDATION_DECK_NOT_FOUND`다. 프론트는 이 차이를 구분하지 않는다.
- 요청 누락, 0 이하 `placeId`, 목록에 없는 eventType, 서버 전용 `CARD_SERVED`는 `400 INVALID_REQUEST`다.

---

## 7. 장소와 저장

## 7.1 장소 검색

`GET /api/v1/places/search?query=경주&cursor=...&size=20`

## 7.2 지역별 장소 목록

`GET /api/v1/places?serviceRegionCode=GANGWON&travelStyles=NATURE&sort=RECOMMENDED&cursor=...&size=20`

- `serviceRegionCode`는 필수이며 확정된 7개 권역만 허용한다.
- 프론트의 지역 도형이나 버튼을 클릭할 때 호출한다.
- GPS, 사용자 좌표, `bbox`, `zoom`은 요청하지 않는다.
- `travelStyles`, `sort`, `cursor`, `size`는 선택값이다.
- 응답은 월별 추천 카드와 구분되는 일반 장소 카드 목록이며 `data.items/nextCursor/hasMore` 형식을 사용한다.

## 7.3 장소 상세

`GET /api/v1/places/{placeId}`

```json
{
  "placeId": 1001,
  "title": "김천 김밥축제",
  "address": "경북 김천시 직지사길 130",
  "latitude": 36.0,
  "longitude": 128.0,
  "operatingHours": "10:00~18:00",
  "operatingPeriod": "2026-04-25~2026-04-26",
  "closedDays": null,
  "usageFee": null,
  "parkingInfo": null,
  "images": [
    {
      "imageUrl": "https://...",
      "order": 1
    }
  ],
  "tags": ["지역축제", "음식", "시즌추천"],
  "isSaved": false,
  "description": {
    "headline": "김천의 로컬 김밥 문화를 만나요",
    "paragraphs": [],
    "enjoyPoints": [],
    "relatedPlaces": []
  },
  "availableTabs": ["DESCRIPTION", "ROUTE", "MATES"]
}
```

운영정보 필드는 KTO 콘텐츠 유형별 원천을 내부 표준 문자열로 정규화하며 원천에 값이 없으면 `null`을 반환한다.

## 7.4 저장

| Method | URI | 설명 |
|---|---|---|
| PUT | `/users/me/saved-places/{placeId}` | 멱등 저장 |
| DELETE | `/users/me/saved-places/{placeId}` | 멱등 저장 취소 |
| GET | `/users/me/saved-places?cursor=...&size=20` | 저장 목록 |

저장 요청은 하트를 누른 화면을 본문에 함께 보낸다.

```json
{
  "source": "RECOMMENDATION_CARD"
}
```

```text
source = HOME_MONTHLY | RECOMMENDATION_CARD | PLACE_DETAIL | MAP
```

같은 장소를 연속으로 저장해도 행을 추가하거나 최초 `savedAt`을 변경하지 않는다. 저장을
취소한 뒤 다시 저장한 경우에만 새 `savedAt`과 새 `source`로 복원한다. 목록은
`savedAt` 최신순이며 `nextCursor`는 분해하거나 직접 만들지 않고 다음 요청에 그대로 보낸다.

---

## 8. Buddy Route

## 8.1 경로 생성

`POST /api/v1/routes`

```json
{
  "originLocationId": 100,
  "destinationPlaceId": 1001,
  "departureAt": "2026-07-20T08:00:00+09:00"
}
```

- 출발지는 인증 사용자의 활성 저장 위치만 허용한다.
- GPS 좌표 입력은 허용하지 않는다.
- `departureAt`이 없으면 요청 시각을 사용한다.
- `departureAt`은 Asia/Seoul 기준 TMAP `searchDttm=yyyyMMddHHmm`으로 변환한다.
- 상세 `/transit/routes` 한 번에 `count=3` 후보를 받고 필수 대중교통 leg가 모두 `service=1`인 경로를 우선한다.
- 운행 가능한 후보가 여러 개면 `totalTime`, `transferCount`, `totalWalkDistance` 순으로 UI 대표 경로를 선택한다.
- 모든 후보에 `service=0` 필수 leg가 있으면 422 `ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME`을 반환한다.

```json
{
  "routeId": "route_01J2ABCDEF",
  "provider": "TMAP_TRANSIT",
  "origin": {
    "name": "성신여자대학교",
    "address": "서울특별시 성북구"
  },
  "destination": {
    "name": "김천 김밥축제",
    "address": "경상북도 김천시"
  },
  "fetchedAt": "2026-07-13T15:00:00+09:00",
  "expiresAt": "2026-07-13T15:30:00+09:00",
  "summary": {
    "estimatedOneWayMinutes": 190,
    "estimatedOneWayTimeText": "약 3시간 10분",
    "transferCount": 3,
    "totalWalkDistanceMeters": 1250,
    "totalWalkMinutes": 18,
    "transportModes": ["WALK", "SUBWAY", "TRAIN", "SHUTTLE_BUS"],
    "recommendedTransportText": "지하철 + KTX + 셔틀버스",
    "difficulty": "NORMAL",
    "difficultyAlgorithmVersion": "route-difficulty-v1",
    "dayTripStatus": "STAY_RECOMMENDED",
    "fare": {
      "oneWayEstimated": 35100,
      "roundTripEstimated": 70200,
      "currencyCode": "KRW",
      "coverage": "AVAILABLE_SEGMENTS_ONLY",
      "disclaimer": "실제 요금과 다를 수 있으며 일부 셔틀 비용은 제외될 수 있습니다."
    },
    "horiTips": [
      {
        "code": "TIP_LONG_ONE_WAY_TRIP",
        "source": "OPERATOR_CURATED",
        "title": "Hori Tip",
        "body": "편도 3시간 이상이므로 숙박을 권장해요.",
        "placement": "TOP_SUMMARY"
      }
    ]
  },
  "segments": [],
  "warnings": [],
  "detailAvailable": true
}
```

### 당일치기 확정 규칙

```text
providerTotalTimeSeconds < 10800  -> DAY_TRIP_AVAILABLE
providerTotalTimeSeconds >= 10800 -> STAY_RECOMMENDED
```

`estimatedOneWayMinutes`는 표시용 값이다. 180분 경계 판정에는 반올림된 분 값이 아니라 TMAP 원본 초를 사용한다.

## 8.2 상세 경로

`GET /api/v1/routes/{routeId}`

```json
{
  "routeId": "route_01J2ABCDEF",
  "expiresAt": "2026-07-13T15:30:00+09:00",
  "summary": {},
  "segments": [
    {
      "order": 1,
      "source": "TMAP",
      "startName": "성신여자대학교",
      "endName": "성신여대입구역",
      "mode": "WALK",
      "routeName": null,
      "durationMinutes": 10,
      "distanceMeters": 700,
      "fare": null,
      "instruction": "도보로 이동하세요.",
      "serviceAvailable": true,
      "horiTips": []
    },
    {
      "order": 3,
      "source": "TMAP",
      "startName": "서울역",
      "endName": "김천(구미)역",
      "mode": "TRAIN",
      "routeName": "KTX",
      "durationMinutes": 85,
      "distanceMeters": 220000,
      "fare": 35100,
      "instruction": "김천(구미)역 도착 기준으로 확인하세요.",
      "serviceAvailable": true,
      "horiTips": [
        {
          "code": "TIP_GIMCHEON_GUMI_STATION",
          "source": "OPERATOR_CURATED",
          "title": "Hori Tip",
          "body": "김천역과 김천(구미)역은 다른 역이에요.",
          "placement": "AFTER_SEGMENT"
        }
      ]
    }
  ],
  "warnings": []
}
```

축제 셔틀버스처럼 TMAP에 없는 구간은 Koready 가공 데이터가 있을 때만 `source=KOREADY_CURATED` 구간으로 추가한다.

## 8.3 TMAP 매핑

- 공식 규격: https://transit.tmapmobility.com/docs/routes
- 데이터 보관 제약: https://transit.tmapmobility.com/terms

| TMAP | Koready |
|---|---|
| `totalTime` | `estimatedOneWayMinutes`로 분 단위 변환 |
| `fare.regular.totalFare` | `fare.oneWayEstimated` |
| `transferCount` | 난이도 계산 입력 |
| `totalWalkDistance` | 난이도 계산 입력 |
| `legs[].mode` | `segments[].mode` |
| `legs[].sectionTime` | `segments[].durationMinutes` |
| `legs[].route` | `segments[].routeName` |
| `legs[].start/end` | 구간 출발/도착 정보 |
| `legs[].service` | `serviceAvailable` |
| `legs[].type` | 내부 세부 교통수단 판정. 프론트 미노출 |
| `legs[].routePayment` | 선택적 segment fare |
| `legs[].Lane` 또는 `lane` | 다중 노선 후보. client에서 두 casing 허용 |
| 최상위 `result` + code 11~14 | 422 `ROUTE_NOT_FOUND` |

API 응답의 `RouteMode`는 `BUS/TRAIN`처럼 단순화한다. 난이도와 표시 문구 계산에서는 TMAP `type/route`를 기반으로 시내·마을·시외버스와 KTX/SRT 등을 구분하는 내부 세부 타입을 사용한다.

- 성공은 HTTP 2xx만으로 판정하지 않는다. `metaData.plan.itineraries`가 비어 있지 않고 최상위 provider `result/error`가 없어야 한다.
- 실제 표본에서 공식 표의 `lane`과 달리 `Lane` 대문자 필드가 확인됐다.
- 실제 정상 itinerary 안에도 `service=0` leg가 존재했다. 운행 종료 leg가 포함된 경로를 그대로 성공 응답으로 반환하지 않는다.
- `totalTime`과 `sectionTime`의 표시 분은 `ceil(seconds / 60)`로 계산한다.
- `steps`, `passShape`, `passStopList`, 좌표와 linestring은 필요한 계산 뒤 폐기하고 공개 DTO에 포함하지 않는다.

- TMAP 원본 응답은 영구 저장하지 않는다.
- 시간·요금·구간을 포함한 정규화 결과도 임시 캐시에만 저장한다.
- 상세 경로 캐시는 기본 30분, 절대 최대 24시간 미만이다.
- 만료된 `routeId`는 `410 ROUTE_EXPIRED`를 반환하고 재조회한다.
- 장기 DB에는 요청 시각, 성공 여부, 오류 코드, 지연시간 등 결과를 재현하지 않는 호출 메타데이터만 저장한다.

## 8.4 운영진 Hori Tip 조합

Hori Tip 본문은 TMAP 응답이나 AI로 생성하지 않는다. 운영진이 관리자 API로 작성한 정보를 DB에 저장해 두고 Buddy Route 응답 시점에 경로와 매칭한다.

```text
TMAP 호출 또는 routeId 캐시 조회
  -> 내부 Route summary/segment 정규화
  -> ACTIVE Hori Tip을 목적지 scope와 노출 기간으로 조회
  -> 저장된 trigger를 현재 Route에 평가
  -> TOP_SUMMARY는 summary.horiTips[]에 조합
  -> AFTER_SEGMENT는 처음 일치한 segments[].horiTips[]에 조합
  -> 노출 ID만 buddy_tip_exposures에 기록
```

확정 규칙:

- 사용자 Route 응답의 `source`는 `OPERATOR_CURATED`이며 제목은 `Hori Tip`으로 고정한다.
- `status=ACTIVE`이고 `validFrom <= now < validUntil`인 팁만 후보로 조회한다. 기간이 없으면 해당 경계는 제한하지 않는다.
- `DESTINATION_PLACES` scope는 현재 `destinationPlaceId`가 등록된 팁만, `ALL_ROUTES`는 목적지와 무관하게 조회한다.
- trigger의 값이 있는 조건 그룹은 AND, `*ContainsAny` 배열 안의 값은 OR로 평가한다.
- `AFTER_SEGMENT` 팁은 segment 조건이 하나 이상 필요하고 처음 일치한 segment에 한 번만 붙인다.
- 같은 팁이 여러 조건으로 일치해도 한 route에서 한 번만 노출한다. 정렬은 `priority DESC, code ASC`다.
- 사용자 기본 언어 번역을 사용한다. `ACTIVE` 전환 시 KO/EN 본문을 모두 검증하므로 사용자 응답에서 임의 번역하거나 fallback 문구를 생성하지 않는다.
- 30분 Route 캐시에는 팁 조합 결과를 넣지 않는다. `POST /routes`와 `GET /routes/{routeId}` 모두 응답 직전에 현재 팁을 다시 조합한다.
- 운영진의 생성·수정·상태 변경은 관리자 감사 로그에 남긴다. 물리 삭제 API는 제공하지 않는다.

---

## 9. Buddy Connect

## 9.1 내 Buddy 프로필

| Method | URI | 설명 |
|---|---|---|
| GET | `/users/me/buddy-profile` | 내 프로필 조회 |
| PUT | `/users/me/buddy-profile` | 생성/전체 수정 |

```json
{
  "profileImageUrl": "https://...",
  "nickname": "Emma",
  "nationality": "France",
  "availableLanguages": ["EN", "KO"],
  "koreanLevel": "BEGINNER",
  "bio": "한국 전통 문화와 로컬 맛집을 좋아해요 :)",
  "buddyStyles": ["TRADITIONAL_CULTURE", "FOODIE"],
  "socialLinks": [
    {
      "type": "INSTAGRAM",
      "value": "@emma.travels"
    }
  ],
  "profilePublic": true,
  "snsPublic": true,
  "allowsMessages": true
}
```

검증:

- `nickname`: 1~30자
- `bio`: 최대 500자
- `koreanLevel`: 초급/중급/고급 세 값만 허용
- 비공개 프로필은 메이트 목록에 노출하지 않는다.

## 9.2 여행지 메이트

| Method | URI | 설명 |
|---|---|---|
| GET | `/places/{placeId}/mates?cursor=...&size=20` | 공개 메이트 목록 |
| GET | `/buddy-profiles/{profileId}` | 공개 프로필 상세 |

차단된 사용자, 신고 제재 사용자, `profilePublic=false` 사용자는 제외한다.

## 9.3 쪽지 스레드

| Method | URI | 설명 |
|---|---|---|
| POST | `/message-threads` | 첫 쪽지 전송과 스레드 생성 |
| GET | `/message-threads?cursor=...&size=20` | 쪽지함 |
| GET | `/message-threads/{threadId}?cursor=...&size=50` | 스레드 내용 |
| POST | `/message-threads/{threadId}/messages` | 답장 전송 |
| PUT | `/message-threads/{threadId}/read` | 읽음 처리 |

`POST /message-threads`

```json
{
  "receiverProfileId": 501,
  "placeId": 1001,
  "content": "안녕하세요! 김천 김밥축제에 관심 있어요."
}
```

- `content`: 공백 제거 후 1~1,000자
- 첫 쪽지와 답장 `POST` 모두 `Idempotency-Key` 헤더를 필수로 받는다.
- 서버는 `senderProfileId + Idempotency-Key`를 DB unique로 보장한다. 같은 key와 다른 요청 본문이 오면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
- 자기 자신, 차단 관계, 수신 거부 프로필에는 전송할 수 없다.
- 실시간 채팅이 아니며 발송 성공 후 push 알림은 비동기로 처리한다.

## 9.4 안전 기능

| Method | URI | 설명 |
|---|---|---|
| PUT | `/users/me/blocked-profiles/{profileId}` | 차단 |
| DELETE | `/users/me/blocked-profiles/{profileId}` | 차단 해제 |
| POST | `/reports` | 프로필/쪽지 신고 |

---

## 10. 프론트 전용 기능

다음 기능은 MVP API 범위에 포함하지 않는다.

- 코레일톡 화면을 따라가는 KTX 예매 가이드·시뮬레이션
- KTX 단계별 정적 이미지·문구·버튼·주의사항
- 추천 카드 최초 사용 가이드 오버레이
- 스플래시 애니메이션
- 영상 가이드
- 오디오 가이드

기획은 화면 순서, 단계별 문구, 이미지 예시와 출처를 프론트에 전달하고 프론트가 정적 데이터와 asset으로 구현한다. Buddy Route의 짧은 Hori Tip만 백엔드가 운영·조합한다. KTX 가이드가 운영 중 업데이트 대상이 되면 별도 CMS API를 새 버전으로 추가하며, 현재 계약에는 빈 endpoint도 만들지 않는다.

---

## 11. Endpoint 요약

| 도메인 | Method | URI |
|---|---|---|
| Auth | POST | `/auth/social/login` |
| Auth | POST | `/auth/refresh` |
| Auth | POST | `/auth/logout` |
| Terms | GET | `/terms/required` |
| Terms | PUT | `/users/me/term-agreements` |
| User | GET | `/users/me` |
| User | PATCH | `/users/me/language` |
| Onboarding | GET | `/users/me/onboarding` |
| Onboarding | GET | `/onboarding/place-candidate-sets/current` |
| Onboarding | PUT | `/users/me/onboarding` |
| Location | GET | `/locations/search` |
| Location | GET/POST | `/users/me/locations` |
| Location | PUT | `/users/me/locations/{locationId}/default` |
| Location | DELETE | `/users/me/locations/{locationId}` |
| Home | GET | `/home` |
| Monthly | GET | `/monthly-recommendations` |
| Pick | POST | `/recommendation-decks` |
| Pick | GET | `/recommendation-decks/{deckId}` |
| Pick | POST | `/recommendation-decks/{deckId}/events` |
| Place | GET | `/places/search` |
| Place | GET | `/places?serviceRegionCode=...` |
| Place | GET | `/places/{placeId}` |
| Saved | GET | `/users/me/saved-places` |
| Saved | PUT/DELETE | `/users/me/saved-places/{placeId}` |
| Route | POST | `/routes` |
| Route | GET | `/routes/{routeId}` |
| Admin Hori Tip | GET/POST | `/admin/hori-tips` |
| Admin Hori Tip | GET/PUT | `/admin/hori-tips/{horiTipId}` |
| Admin Hori Tip | PUT | `/admin/hori-tips/{horiTipId}/status` |
| Buddy | GET/PUT | `/users/me/buddy-profile` |
| Buddy | GET | `/places/{placeId}/mates` |
| Buddy | GET | `/buddy-profiles/{profileId}` |
| Message | POST/GET | `/message-threads` |
| Message | GET | `/message-threads/{threadId}` |
| Message | POST | `/message-threads/{threadId}/messages` |
| Message | PUT | `/message-threads/{threadId}/read` |
| Safety | PUT/DELETE | `/users/me/blocked-profiles/{profileId}` |
| Safety | POST | `/reports` |

---

## 12. 핵심 오류 코드

| 코드 | 의미 |
|---|---|
| `REQUIRED_TERMS_NOT_AGREED` | 필수 약관 미동의 |
| `ONBOARDING_INCOMPLETE` | 온보딩 미완료 |
| `DEFAULT_LOCATION_REQUIRED` | 기본 위치 필요 |
| `LOCATION_NOT_OWNED` | 사용자 소유 위치가 아님 |
| `CANDIDATE_SET_EXPIRED` | 온보딩 후보 만료 |
| `INVALID_CANDIDATE_SELECTION` | 후보 외 장소 또는 선택 수 오류 |
| `RECOMMENDATION_EXHAUSTED` | 추천 후보 소진 |
| `PLACE_NOT_FOUND` | 장소 없음 |
| `ROUTE_NOT_FOUND` | 대중교통 경로 없음 |
| `ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME` | 요청 출발 시각에 운행 가능한 후보 없음 |
| `ROUTE_EXPIRED` | 임시 경로 만료 |
| `ROUTE_PROVIDER_UNAVAILABLE` | TMAP 장애/제한 |
| `HORI_TIP_CODE_DUPLICATED` | 이미 사용 중인 운영 팁 code |
| `HORI_TIP_RULE_INVALID` | 운영 팁 scope·trigger·노출 기간 규칙 오류 |
| `HORI_TIP_NOT_EDITABLE` | 보관 상태이거나 version 충돌로 수정 불가 |
| `HORI_TIP_ACTIVATION_INVALID` | 번역·scope·trigger·기간 활성화 조건 미충족 |
| `BUDDY_PROFILE_REQUIRED` | Buddy 프로필 필요 |
| `MESSAGE_NOT_ALLOWED` | 차단/수신거부/비공개 정책 위반 |
| `MESSAGE_TOO_LONG` | 쪽지 1,000자 초과 |
| `RATE_LIMIT_EXCEEDED` | 요청 한도 초과 |

## 13. 구현 수락 기준

- 온보딩 계약에 방문 목적 schema, 필드, 단계 값이 존재하지 않는다.
- 온보딩 요청은 위치, 여행 스타일 1~4개, 관심 관광지 1~3개를 검증한다.
- GPS 관련 endpoint가 존재하지 않는다.
- 편도 179분은 `DAY_TRIP_AVAILABLE`, 180분은 `STAY_RECOMMENDED`다.
- 쪽지 1,000자는 성공하고 1,001자는 거절된다.
- `NONE`, `NATIVE` 한국어 수준은 거절된다.
- 사용자 응답의 팁 제목은 `Hori Tip`이다.
- 사용자 응답의 Hori Tip은 모두 운영진 저장 데이터이며 `source=OPERATOR_CURATED`다.
- 팁 비활성화 후 기존 `routeId`를 다시 조회하면 비활성 팁이 제외된다.
- 홈 응답에 KTX 가이드 본문이 포함되지 않는다.
- 월별 추천은 필수 `year/month`로 회차를 조회하고 `occurrenceId/eventYear/status`를 반환한다.
- 종료 회차는 해당 개최 연도·월 상세 목록에 `ENDED`로 남고 홈에서는 진행 중·예정 회차보다 낮게 정렬된다.
- TMAP 구간은 Koready `RouteSegment`로 정규화된다.
- TMAP HTTP 200 code 11~14는 성공으로 처리하지 않고 `ROUTE_NOT_FOUND`로 변환된다.
- 필수 대중교통 leg의 `service=0`만 있는 후보는 성공 경로로 반환되지 않는다.
- 실제 `Lane`과 문서상의 `lane` casing을 모두 역직렬화할 수 있다.
- 만료된 TMAP 상세 경로는 재사용되지 않는다.
- 같은 추천 덱에 장소가 중복되지 않는다.
- `CARD_SERVED`된 장소는 30일 동안 새 덱에서 제외된다.
- 추천 노출 기록에 적용 정책 버전과 제한 일수가 남는다.
- 삭제 위치는 위치 목록, 추천 기준, 경로 출발지에서 즉시 제외된다.
