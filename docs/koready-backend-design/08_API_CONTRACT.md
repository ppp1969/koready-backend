# Koready MVP API 계약

## 0. 문서 상태

이 문서는 최신 Figma `⭐️ UI` 페이지와 `07_CONFIRMED_PRODUCT_POLICIES.md`를 기준으로 작성한 MVP API 기준본이다.

관리자/OpenAPI 증빙 API는 `09_ADMIN_API_CONTRACT.md`에서 별도 정의한다.

- 작성 기준일: 2026-07-13
- Base URL: `/api/v1`
- 인증: `Authorization: Bearer <accessToken>`
- 콘텐츠 언어: `Accept-Language: ko-KR` 또는 `en-US`
- 서버 기준 시간대: `Asia/Seoul`
- JSON 필드명: `camelCase`
- endpoint와 schema는 `openapi.yaml`을 최종 기준으로 사용한다. 정책은 `07`, 사용자 API 설명은 `08`, 관리자 API 설명은 `09`를 따른다.

### 확정 범위

- GPS를 사용하지 않는다. 위치는 주소/학교/동네 검색 결과만 저장한다.
- 편도 180분 미만만 당일치기 가능이며, 180분 이상은 숙박 권장이다.
- 쪽지 본문은 1~1,000자다.
- 한국어 수준은 `BEGINNER`, `INTERMEDIATE`, `ADVANCED` 세 값만 사용한다.
- 사용자 노출 명칭은 `Hori Tip`이다.
- KTX 한국 여행 가이드는 프론트 정적 콘텐츠이며 API를 만들지 않는다.
- 이동 경로는 TMAP 대중교통 API 결과를 Koready 응답으로 정규화한다.
- 온보딩 선호 여행지 후보는 관리자가 발행한 불변 버전의 10개 큐레이션 세트다.
- 위치 검색은 백엔드가 Kakao Local API를 호출하고, 위치 저장은 단기 `searchResultToken`으로 검증한다.

초보 프론트 개발자를 위한 화면별 호출 순서, 상태 처리, TypeScript 예제는 `10_FRONTEND_API_FLOW_GUIDE.md`를 함께 본다.

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

TravelPurpose =
  EXCHANGE_STUDENT | LANGUAGE_STUDY | SHORT_TRIP |
  DEGREE_PROGRAM | INTERN_WORK | WORKING_HOLIDAY | ETC

TravelStyle =
  LOCAL_FOOD | LOCAL_FESTIVAL | TRADITIONAL_MARKET |
  CULTURE_EXPERIENCE | NATURE | EXHIBITION_MUSEUM | DRAMA_LOCATION

RecommendationScope = NEARBY | NATIONWIDE
ServiceRegionCode =
  SEOUL | GYEONGGI | GANGWON | CHUNGCHEONG |
  JEOLLA | GYEONGSANG | JEJU

SortType = RECOMMENDED | DEADLINE
DateFilterType = ALL | THIS_WEEK | THIS_MONTH | NEXT_MONTH | CUSTOM

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

## 3.2 온보딩 상태

`GET /api/v1/users/me/onboarding`

```json
{
  "completed": false,
  "currentStep": "TRAVEL_PURPOSE",
  "travelPurpose": null,
  "currentLocationId": null,
  "travelStyles": [],
  "selectedPreferencePlaceIds": []
}
```

온보딩 단계는 다음 데이터로 구성한다.

1. 한국 방문 목적
2. 현재 머무는 위치
3. 관심 여행 스타일
4. 선호 여행지 1~3개

출신 국가와 이동 범위는 온보딩 API에 포함하지 않는다.

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

```json
{
  "travelPurpose": "EXCHANGE_STUDENT",
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
- 완료 요청은 멱등하게 처리한다.

---

## 4. 위치

GPS 기반 endpoint와 위도/경도 역지오코딩 endpoint는 제공하지 않는다.

## 4.1 위치 검색

`GET /api/v1/locations/search?query=성신여자대학교&limit=10`

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
    "month": 5,
    "totalCount": 18,
    "items": []
  }
}
```

KTX 가이드 카드와 10단계 본문은 응답에 포함하지 않는다.

## 5.2 월별 추천 전체보기

`GET /api/v1/monthly-recommendations`

Query:

```text
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
  "month": 5,
  "totalCount": 18,
  "items": [
    {
      "placeId": 1001,
      "title": "전주 이팝나무 축제",
      "serviceRegionCode": "JEOLLA",
      "serviceRegionName": "전라",
      "addressSummary": "전북 전주시",
      "imageUrl": "https://...",
      "startDate": "2026-04-25",
      "endDate": "2026-04-26",
      "dateRangeText": "4.25(토)~4.26(일)",
      "travelStyle": "LOCAL_FESTIVAL",
      "isSaved": false
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

축제는 시작일 6개월 전부터 종료일까지 노출한다.

`month`가 기본 조회 범위다. `dateFilterType`을 함께 보내면 선택한 월 범위와 날짜 필터 범위의 교집합만 반환한다. `CUSTOM`일 때는 `customStartDate/customEndDate`가 모두 필수다.

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
  "items": [
    {
      "placeId": 1001,
      "title": "경주 문화유산 나들이",
      "locationText": "경상북도 경주시",
      "imageUrl": "https://...",
      "isSaved": false,
      "tags": ["역사 여행", "카페 거리", "인생샷 명소"],
      "descriptionParagraphs": [
        "서울을 떠나 한국의 살아있는 박물관을 탐험해 보세요."
      ],
      "routeAvailable": true
    }
  ],
  "nextCursor": "cursor_2",
  "hasMore": true
}
```

## 6.2 덱 다음 페이지

`GET /api/v1/recommendation-decks/{deckId}?cursor=cursor_2`

- 동일 `deckId + cursor` 재요청은 동일한 순서와 결과를 반환한다.
- 같은 덱 안에서 `placeId`가 중복되면 안 된다.
- `CARD_SERVED`된 장소는 30일 동안 새 덱 후보에서 제외한다.

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

- KTX 예매 10단계 가이드
- KTX 단계별 정적 이미지/문구
- 추천 카드 최초 사용 가이드 오버레이
- 스플래시 애니메이션

KTX 가이드가 운영 중 업데이트 대상이 되면 별도 CMS API를 새 버전으로 추가한다. 현재 계약에는 빈 endpoint도 만들지 않는다.

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

- GPS 관련 endpoint가 존재하지 않는다.
- 편도 179분은 `DAY_TRIP_AVAILABLE`, 180분은 `STAY_RECOMMENDED`다.
- 쪽지 1,000자는 성공하고 1,001자는 거절된다.
- `NONE`, `NATIVE` 한국어 수준은 거절된다.
- 사용자 응답의 팁 제목은 `Hori Tip`이다.
- 사용자 응답의 Hori Tip은 모두 운영진 저장 데이터이며 `source=OPERATOR_CURATED`다.
- 팁 비활성화 후 기존 `routeId`를 다시 조회하면 비활성 팁이 제외된다.
- 홈 응답에 KTX 가이드 본문이 포함되지 않는다.
- TMAP 구간은 Koready `RouteSegment`로 정규화된다.
- TMAP HTTP 200 code 11~14는 성공으로 처리하지 않고 `ROUTE_NOT_FOUND`로 변환된다.
- 필수 대중교통 leg의 `service=0`만 있는 후보는 성공 경로로 반환되지 않는다.
- 실제 `Lane`과 문서상의 `lane` casing을 모두 역직렬화할 수 있다.
- 만료된 TMAP 상세 경로는 재사용되지 않는다.
- 같은 추천 덱에 장소가 중복되지 않는다.
- `CARD_SERVED`된 장소는 30일 동안 새 덱에서 제외된다.
- 삭제 위치는 위치 목록, 추천 기준, 경로 출발지에서 즉시 제외된다.
