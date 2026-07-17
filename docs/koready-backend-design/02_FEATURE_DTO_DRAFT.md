# Koready 기능별 DTO 초안

이 문서는 화면 기준 DTO 초안이다. 실제 Java record/class 명칭은 개발 컨벤션에 맞게 조정한다.

> 이 문서의 예시는 설명용이다. endpoint와 schema는 `openapi.yaml`, 확정 사용자/관리자 계약은 `08_API_CONTRACT.md`와 `09_ADMIN_API_CONTRACT.md`를 최종 기준으로 사용한다.

## 0. 공통 응답

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

## 0.1 공통 enum

```text
LanguageCode = KO | EN
SocialProvider = GOOGLE | APPLE
ServiceRegionCode = SEOUL | GYEONGGI | GANGWON | CHUNGCHEONG | JEOLLA | GYEONGSANG | JEJU
TravelStyle = LOCAL_FOOD | LOCAL_FESTIVAL | TRADITIONAL_MARKET | CULTURE_EXPERIENCE | NATURE | EXHIBITION_MUSEUM | DRAMA_LOCATION
BuddyStyle = TRADITIONAL_CULTURE | CAFE_TOUR | FOODIE | PHOTOGRAPHY | HANOK_EXPERIENCE | QUIET_TRAVEL
RecommendationScope = NEARBY | NATIONWIDE
SortType = RECOMMENDED | DEADLINE
DateFilterType = ALL | THIS_WEEK | THIS_MONTH | NEXT_MONTH | CUSTOM
FestivalOccurrenceStatus = UPCOMING | ONGOING | ENDED
Difficulty = EASY | NORMAL | HARD
DayTripStatus = DAY_TRIP_AVAILABLE | STAY_RECOMMENDED
TransportMode = WALK | SUBWAY | CITY_BUS | LOCAL_BUS | INTERCITY_BUS | EXPRESS_BUS | SHUTTLE_BUS | TRAIN | KTX | SRT | AIRPLANE | ETC
PreferenceCandidateSource = ONBOARDING_CALIBRATION
PreferenceTagSource = ONBOARDING_PLACE_SELECTION | MANUAL | BEHAVIOR_INFERRED
ExternalApiProvider = KTO | TMAP | KAKAO | AI
TourApiOperation = areaCode2 | categoryCode2 | ldongCode2 | lclsSystmCode2 | areaBasedList2 | locationBasedList2 | searchKeyword2 | searchFestival2 | searchStay2 | detailCommon2 | detailIntro2 | detailInfo2 | detailImage2 | areaBasedSyncList2 | detailPetTour2
TranslationSource = KTO_KO | KTO_EN | AI_TRANSLATED | MANUAL_EDITED
ImageSourceType = KTO_TOURAPI | KTO_PHOTO | MANUAL | S3
BatchJobStatus = PENDING | RUNNING | COMPLETED | FAILED | PARTIAL_FAILED
```

---

## 1. Auth / 회원가입

## 1.1 SocialLoginRequest

```json
{
  "provider": "GOOGLE",
  "idToken": "string",
  "authorizationCode": "string",
  "deviceId": "string",
  "expoPushToken": "string"
}
```

## 1.2 AuthResponse

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "user": {
    "userId": 1,
    "email": "emma@example.com",
    "profileImageUrl": "https://...",
    "preferredLanguage": "EN"
  },
  "nextStep": "TERMS"
}
```

```text
nextStep = TERMS | LANGUAGE | ONBOARDING | COMPLETED
```

---

## 2. 약관

## 2.1 TermsRequiredResponse

```json
{
  "terms": [
    {
      "termId": 1,
      "code": "SERVICE_TERMS",
      "title": "서비스 이용약관",
      "required": true,
      "latestVersionId": 10,
      "version": "1.0",
      "contentUrl": "string",
      "alreadyAgreed": false,
      "needsAgreement": true
    }
  ]
}
```

## 2.2 AgreeTermsRequest

```json
{
  "agreements": [
    {
      "termId": 1,
      "termVersionId": 10,
      "agreed": true
    },
    {
      "termId": 3,
      "termVersionId": 30,
      "agreed": false
    }
  ]
}
```

## 2.3 UserTermsStatusResponse

```json
{
  "agreements": [
    {
      "termCode": "PRIVACY_POLICY",
      "title": "개인정보처리방침",
      "required": true,
      "agreedVersion": "1.1",
      "latestVersion": "1.1",
      "agreedAt": "2026-06-28T12:00:00+09:00",
      "needsReAgreement": false
    }
  ]
}
```

---

## 3. 언어 설정 / 온보딩

## 3.1 UpdateLanguageRequest

```json
{
  "language": "EN"
}
```

## 3.2 OnboardingRequest

```json
{
  "currentLocationId": 100,
  "travelStyles": ["LOCAL_FOOD", "LOCAL_FESTIVAL", "TRADITIONAL_MARKET"],
  "candidateSetId": "onb-curation-2026-07-v1",
  "candidateSetVersion": 1,
  "selectedPreferencePlaceIds": [1001, 2001]
}
```

Validation:

```text
travelStyles: 1~4개
selectedPreferencePlaceIds: 1~3개
candidateSetId/version: 관리자가 발행한 세트와 일치
selectedPreferencePlaceIds: 해당 발행 버전의 10개 후보에 포함
currentLocationId: 현재 사용자에게 속한 삭제되지 않은 위치
```

## 3.3 OnboardingResponse

```json
{
  "completed": true,
  "profile": {
    "currentLocation": {
      "locationId": 100,
      "displayName": "성북구 보문로30가길",
      "serviceRegionCode": "SEOUL"
    },
    "travelStyles": ["LOCAL_FOOD", "LOCAL_FESTIVAL"],
    "preferenceTags": [
      {
        "tagId": 31,
        "code": "LOCAL_MARKET",
        "name": "로컬시장",
        "weight": 2.0
      }
    ]
  }
}
```

## 3.4 OnboardingPreferenceCandidateResponse

온보딩 중 음악 추천 서비스처럼 정확히 10개의 관광지 후보를 보여주기 위한 응답이다. 후보는 사용자별로 생성하지 않고 관리자가 검수해 발행한 불변 버전이다.

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
      "title": "김천 김밥축제",
      "imageUrl": "https://...",
      "serviceRegionCode": "GYEONGSANG",
      "serviceRegionName": "경상",
      "travelStyle": "LOCAL_FESTIVAL",
      "tags": ["지역축제", "로컬음식", "체험"],
      "curatorMessage": "김밥을 주제로 먹고, 만들고, 즐기는 지역 축제예요.",
      "displayOrder": 1
    }
  ]
}
```

## 3.5 OnboardingPreferenceSelectionRequest

온보딩을 단계별 저장으로 구현할 경우 사용한다. 최종 `OnboardingRequest`에 포함해 한 번에 저장해도 된다.

```json
{
  "candidateSetId": "onb-curation-2026-07-v1",
  "candidateSetVersion": 1,
  "selectedPlaceIds": [1001, 2001]
}
```

Validation:

```text
selectedPlaceIds: 1~3개
selectedPlaceIds는 candidateSetId/version에 포함된 후보여야 함
발행 버전은 불변이므로 사용자가 조회한 뒤 새 버전이 발행돼도 기존 버전 선택을 허용
```

## 3.6 OnboardingPreferenceSelectionResponse

```json
{
  "selectedPlaceIds": [1001, 2001],
  "preferenceTags": [
    {
      "tagId": 31,
      "code": "LOCAL_MARKET",
      "name": "로컬시장",
      "weight": 2.0,
      "source": "ONBOARDING_PLACE_SELECTION"
    }
  ]
}
```

---

## 4. 위치 검색 / 위치 이력

## 4.1 LocationSearchRequest

```json
{
  "keyword": "성신여대",
  "language": "KO"
}
```

## 4.2 LocationSearchResponse

```json
{
  "items": [
    {
      "searchResultToken": "locsrch_opaque_signed_token",
      "provider": "KAKAO",
      "resultType": "PLACE",
      "providerPlaceId": "123456789",
      "name": "성신여자대학교",
      "roadAddress": "서울 성북구 보문로34다길 2",
      "address": "서울 성북구 돈암동 173-1",
      "latitude": 37.5913,
      "longitude": 127.0221,
      "sido": "서울특별시",
      "sigungu": "성북구",
      "dong": "돈암동",
      "serviceRegionCode": "SEOUL"
    }
  ]
}
```

## 4.3 CreateUserLocationRequest

```json
{
  "searchResultToken": "locsrch_opaque_signed_token",
  "customLabel": "학교",
  "setDefault": true
}
```

`searchResultToken`은 검색 응답의 정규화된 주소·좌표·권역을 서버가 서명한 단기 token이다. 프론트는 주소나 좌표를 위치 저장 요청에서 다시 보내지 않는다.

## 4.4 UserLocationResponse

```json
{
  "locationId": 100,
  "displayName": "성북구 보문로30가길",
  "roadAddress": "서울 성북구 보문로30가길 ...",
  "latitude": 37.59,
  "longitude": 127.02,
  "serviceRegionCode": "SEOUL",
  "default": true
}
```

## 4.5 UserLocationListResponse

온보딩과 마이페이지에서 사용자의 주소 입력/선택 기록을 보여준다. 삭제된 위치는 기본 응답에 포함하지 않는다.

```json
{
  "items": [
    {
      "locationId": 100,
      "displayName": "성북구 보문로30가길",
      "roadAddress": "서울 성북구 보문로30가길 ...",
      "latitude": 37.59,
      "longitude": 127.02,
      "serviceRegionCode": "SEOUL",
      "default": true,
      "createdAt": "2026-07-13T12:00:00+09:00"
    }
  ]
}
```

## 4.6 DeleteUserLocationResponse

```json
{
  "locationId": 100,
  "deleted": true,
  "deletedAt": "2026-07-13T12:10:00+09:00"
}
```

Validation:

```text
기본 위치 삭제 시 다른 위치를 기본값으로 지정하거나, 온보딩/추천 진입 전 새 기본 위치를 요구한다.
삭제는 soft delete로 처리한다.
삭제된 위치는 위치 목록, 추천 기준 위치, Buddy Route 출발지에서 즉시 제외한다.
사용자는 여러 위치를 저장할 수 있지만 삭제되지 않은 기본 위치는 최대 1개다.
```

---

## 5. 홈 화면

## 5.1 HomeResponse

```json
{
  "currentLocation": {
    "locationId": 100,
    "displayName": "성북구 보문로30가길",
    "serviceRegionCode": "SEOUL"
  },
  "preferredLanguage": "EN",
  "monthlyRecommendation": {
    "year": 2026,
    "month": 6,
    "title": "6월달엔 이건 해야지!",
    "items": []
  }
}
```

## 5.2 MonthlyRecommendationQuery

```json
{
  "year": 2026,
  "month": 6,
  "serviceRegionCode": "GYEONGSANG",
  "dateFilterType": "THIS_MONTH",
  "customStartDate": null,
  "customEndDate": null,
  "travelStyles": ["LOCAL_FESTIVAL"],
  "sort": "RECOMMENDED",
  "cursor": null,
  "size": 20
}
```

응답 언어는 query가 아니라 공통 `Accept-Language` 헤더로 전달한다.

축제 노출 공통 정책:

```text
zoneId = Asia/Seoul
visibleFrom = eventStartDate - 6개월
today < visibleFrom 이면 미노출
today < eventStartDate 이면 UPCOMING
eventStartDate <= today <= eventEndDate 이면 ONGOING
today > eventEndDate 이면 ENDED
visibleFrom 이후에는 종료돼도 해당 eventYear/month 상세 목록에 ENDED로 유지
홈과 현재 추천은 ONGOING/UPCOMING을 ENDED보다 우선
시작일/종료일 누락 또는 파싱 실패 시 날짜 기반 목록에서 제외
```

## 5.3 PlaceListCardResponse

```json
{
  "placeId": 1001,
  "title": "김천 김밥축제",
  "serviceRegionCode": "GYEONGSANG",
  "serviceRegionName": "경상",
  "addressSummary": "경상북도 김천시",
  "imageUrl": "https://...",
  "festivalOccurrence": {
    "occurrenceId": 81001,
    "eventYear": 2026,
    "startDate": "2026-04-25",
    "endDate": "2026-04-26",
    "status": "ENDED",
    "dateRangeText": "4.25(토)~4.26(일)"
  },
  "travelStyle": "LOCAL_FESTIVAL",
  "tags": ["지역축제", "음식", "시즌추천"],
  "saved": false
}
```

---

## 6. 지도 화면

## 6.1 ServiceRegionListResponse

```json
{
  "regions": [
    {
      "code": "SEOUL",
      "nameKo": "서울",
      "nameEn": "Seoul",
      "displayOrder": 1,
      "mapAssetKey": "region_seoul"
    }
  ]
}
```

## 6.2 RegionPlaceListQuery

```json
{
  "serviceRegionCode": "GANGWON",
  "dateFilterType": "ALL",
  "travelStyles": ["NATURE"],
  "sort": "RECOMMENDED",
  "cursor": null,
  "size": 20
}
```

응답은 `PlaceListCardResponse[]`를 재사용한다.

7개 권역 도형과 버튼은 프론트 정적 asset이다. 서버는 `serviceRegionCode` 기반 장소 목록만 제공하며 GPS, `bbox`, `zoom` 파라미터를 받지 않는다.

---

## 7. K-Local Pick 추천 카드

## 7.1 RecommendationDeckRequest

```json
{
  "scope": "NEARBY",
  "size": 20,
  "cursor": null,
  "language": "EN"
}
```

`cursor`는 서버가 발급한 opaque string이다. 클라이언트는 값을 해석하지 않고 그대로 다음 요청에 전달한다.

`CARD_SERVED`된 장소의 재노출 금지 기간은 30일로 고정한다. 후보가 부족해도 이 기간을 줄이지 않으며, 반환할 후보가 없으면 `hasMore=false`를 내려준다.

## 7.2 RecommendationDeckResponse

```json
{
  "deckId": "rec-20260628-user1-001",
  "scope": "NEARBY",
  "nextCursor": "eyJwYWdlIjoyfQ==",
  "remainingThreshold": 5,
  "deduplication": {
    "guaranteedWithinDeck": true,
    "suppressionDays": 30
  },
  "cards": [
    {
      "placeId": 1001,
      "title": "김천 김밥축제",
      "locationText": "경상북도 김천시",
      "imageUrl": "https://...",
      "saved": false,
      "tags": ["지역축제", "로컬음식", "인생샷"],
      "shortDescription": "김밥을 주제로 먹고, 만들고, 즐기는 지역 축제예요.",
      "serviceRegionCode": "GYEONGSANG",
      "travelStyle": "LOCAL_FESTIVAL",
      "matchRank": 1,
      "matchReason": {
        "travelStyleMatched": true,
        "preferenceTagMatched": true,
        "matchedTagCodes": ["LOCAL_FOOD", "HANDS_ON"]
      }
    }
  ]
}
```

```text
matchRank = 1 | 2 | 3
1: 태그 O + 관광 유형 O
2: 태그 X + 관광 유형 O
3: 태그 X + 관광 유형 X
```

## 7.3 RecommendationEventRequest

```json
{
  "deckId": "rec-20260628-user1-001",
  "placeId": 1001,
  "eventType": "CARD_DETAIL_CLICKED",
  "cursor": "eyJwYWdlIjoxfQ=="
}
```

```text
eventType = CARD_NEXT | CARD_PREVIOUS | PLACE_DETAIL_CLICKED | ROUTE_OPENED | MATE_TAB_OPENED
```

`CARD_SERVED`는 클라이언트 요청으로 받지 않고 추천 응답 시 서버가 기록한다.

---

## 8. 저장/하트

## 8.1 SavePlaceRequest

```json
{
  "source": "RECOMMENDATION_CARD"
}
```

```text
source = HOME_MONTHLY | RECOMMENDATION_CARD | PLACE_DETAIL | MAP
```

## 8.2 SavePlaceResponse

```json
{
  "placeId": 1001,
  "saved": true,
  "savedAt": "2026-06-28T12:00:00+09:00"
}
```

## 8.3 SavedPlaceListResponse

```json
{
  "items": [
    {
      "placeId": 1001,
      "title": "김천 김밥축제",
      "imageUrl": "https://...",
      "addressSummary": "경상북도 김천시",
      "dateRangeText": "4.25(토)~4.26(일)",
      "tags": ["지역축제", "음식"],
      "savedAt": "2026-06-28T12:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

---

## 9. 여행지 상세

## 9.1 PlaceDetailResponse

```json
{
  "placeId": 1001,
  "title": "김천 김밥축제",
  "serviceRegionCode": "GYEONGSANG",
  "locationText": "경상북도 김천시",
  "address": "경상북도 김천시 ...",
  "latitude": 36.0,
  "longitude": 128.0,
  "imageUrls": ["https://..."],
  "operatingHours": "10:00~18:00",
  "operatingPeriod": "2026-04-25~2026-04-26",
  "tags": ["지역축제", "로컬음식", "인생샷"],
  "saved": true,
  "tabs": {
    "descriptionAvailable": true,
    "routeAvailable": true,
    "mateAvailable": true
  }
}
```

## 9.2 PlaceDescriptionResponse

```json
{
  "placeId": 1001,
  "impactTitle": "김천에서 만나는",
  "impactSubtitle": "가장 맛있는 한 줄 여행",
  "introParagraphs": [
    "김천 김밥축제는 김밥을 주제로 먹고, 만들고, 즐길 수 있는 지역 축제예요.",
    "평소에 먹던 김밥과는 조금 다른 김천만의 재미있는 김밥 문화를 만날 수 있어요."
  ],
  "enjoyPoints": [
    "다양한 김밥 부스에서 김밥 맛보기",
    "김밥 만들기 체험 참여하기",
    "김밥 포토존에서 사진 찍기"
  ],
  "sourceType": "AI_GENERATED",
  "relatedPlaces": [
    {
      "placeId": 2001,
      "title": "직지사",
      "imageUrl": "https://...",
      "shortDescription": "김천에서 함께 둘러보기 좋은 전통 명소예요."
    }
  ]
}
```

---

## 10. Buddy Route / Hori Tip

## 10.1 RouteRequest

```json
{
  "originLocationId": 100,
  "destinationPlaceId": 1001,
  "departureAt": "2026-07-20T08:00:00+09:00"
}
```

출발 좌표와 임의 `customOrigin`은 받지 않는다. 사용자 소유의 활성 위치 ID만 허용한다.

## 10.2 RouteSummaryResponse

```json
{
  "routeId": "route_01J2ABCDEF",
  "provider": "TMAP_TRANSIT",
  "origin": {"name": "성신여자대학교", "address": "서울특별시 성북구"},
  "destination": {"name": "김천 김밥축제", "address": "경상북도 김천시"},
  "fetchedAt": "2026-07-13T15:00:00+09:00",
  "expiresAt": "2026-07-13T15:30:00+09:00",
  "summary": {
    "recommendedTransportText": "지하철 + KTX + 축제 셔틀버스",
    "estimatedOneWayMinutes": 190,
    "estimatedOneWayTimeText": "약 3시간 10분",
    "transferCount": 3,
    "totalWalkDistanceMeters": 1250,
    "totalWalkMinutes": 18,
    "difficulty": "NORMAL",
    "difficultyAlgorithmVersion": "route-difficulty-v1",
    "dayTripStatus": "STAY_RECOMMENDED",
    "fare": {
      "oneWayEstimated": 35100,
      "roundTripEstimated": 70200,
      "currencyCode": "KRW",
      "coverage": "AVAILABLE_SEGMENTS_ONLY",
      "disclaimer": "실제 요금과 다를 수 있습니다."
    },
    "transportModes": ["WALK", "SUBWAY", "TRAIN", "SHUTTLE_BUS"],
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

`dayTripStatus`는 TMAP 원본 `totalTime` 초를 기준으로 `10800`초 미만인지 판정한다. `estimatedOneWayMinutes`는 표시용이며 경계 판정에 사용하지 않는다.

## 10.3 RouteDetailResponse

```json
{
  "summary": {
    "recommendedTransportText": "지하철 + KTX + 축제 셔틀버스",
    "estimatedOneWayMinutes": 190,
    "difficulty": "NORMAL",
    "dayTripStatus": "STAY_RECOMMENDED",
    "fare": {
      "oneWayEstimated": 35100,
      "roundTripEstimated": 70200,
      "currencyCode": "KRW",
      "coverage": "AVAILABLE_SEGMENTS_ONLY",
      "disclaimer": "실제 요금과 다를 수 있습니다."
    }
  },
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
      "fare": 27600,
      "instruction": "김천역이 아니라 김천(구미)역 도착 기준으로 확인하세요.",
      "serviceAvailable": true,
      "horiTips": [
        {
          "code": "TIP_GIMCHEON_GUMI_STATION",
          "source": "OPERATOR_CURATED",
          "title": "Hori Tip",
          "body": "김천역과 김천(구미)역은 다른 역이에요. KTX를 이용할 때는 반드시 김천(구미)역으로 검색하세요.",
          "placement": "AFTER_SEGMENT"
        }
      ]
    }
  ],
  "warnings": [],
  "detailAvailable": true
}
```

TMAP HTTP 200 응답이어도 최상위 `result`가 있거나 itinerary가 비어 있으면 성공이 아니다. `service=0` 필수 대중교통 구간만 있는 후보도 성공 경로로 반환하지 않는다.

`horiTips`는 TMAP이나 AI가 생성하는 값이 아니다. 운영진이 저장한 현재 `ACTIVE` 팁을 Route 조회 시점에 목적지와 경로 조건으로 매칭한 결과이며, `source`는 항상 `OPERATOR_CURATED`다.

---

## 11. Buddy Connect

## 11.1 BuddyProfileUpsertRequest

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
    },
    {
      "type": "KAKAOTALK",
      "value": "emma_kr"
    }
  ],
  "profilePublic": true,
  "snsPublic": true,
  "allowsMessages": true
}
```

```text
koreanLevel = BEGINNER | INTERMEDIATE | ADVANCED
socialLink.type = INSTAGRAM | KAKAOTALK | THREADS | TIKTOK | ETC
```

## 11.2 PlaceMateListResponse

```json
{
  "placeId": 1001,
  "items": [
    {
      "profileId": 501,
      "nickname": "Emma",
      "profileImageUrl": "https://...",
      "nationality": "France",
      "availableLanguages": ["EN", "KO"],
      "koreanLevel": "BEGINNER",
      "bio": "한국 전통 문화와 로컬 맛집을 좋아해요 :)",
      "buddyStyles": ["TRADITIONAL_CULTURE", "FOODIE"],
      "socialLinks": [
        {
          "type": "INSTAGRAM",
          "displayValue": "@emma.travels"
        }
      ],
      "canMessage": true
    }
  ]
}
```

## 11.3 SendMessageRequest

```json
{
  "receiverProfileId": 501,
  "placeId": 1001,
  "content": "안녕하세요! 저도 김천 김밥축제에 관심 있어요. 같이 이야기해볼 수 있을까요?"
}
```

Validation:

```text
content: 1~1000자
receiverProfile.allowsMessages = true
senderProfile exists
sender != receiver
```

## 11.4 MessageResponse

```json
{
  "messageId": 9001,
  "threadId": "thread_01J2ABCDEF",
  "senderProfileId": 500,
  "receiverProfileId": 501,
  "placeId": 1001,
  "content": "안녕하세요!...",
  "sentAt": "2026-06-28T12:00:00+09:00",
  "read": false
}
```

## 11.5 MessageBoxResponse

```json
{
  "boxType": "INBOX",
  "items": [
    {
      "threadId": "thread_01J2ABCDEF",
      "placeId": 1001,
      "placeTitle": "김천 김밥축제",
      "otherProfile": {
        "profileId": 500,
        "nickname": "Jay",
        "profileImageUrl": "https://..."
      },
      "preview": "안녕하세요! 저도 김천 김밥축제에...",
      "sentAt": "2026-06-28T12:00:00+09:00",
      "read": false
    }
  ]
}
```

---

## 12. 마이페이지

## 12.1 MyPageResponse

```json
{
  "user": {
    "userId": 1,
    "email": "emma@example.com",
    "preferredLanguage": "EN"
  },
  "defaultLocation": {
    "locationId": 100,
    "displayName": "성북구 보문로30가길"
  },
  "buddyProfile": {
    "exists": true,
    "profilePublic": true,
    "allowsMessages": true
  },
  "unreadMessageCount": 2,
  "termsNeedReAgreement": false
}
```

---

## 13. 내부 운영 / OpenAPI 수집 로그

일반 앱 화면에는 노출하지 않는다. 개발/운영자 확인, 공모전 증빙, 동기화 오류 디버깅용이다.

## 13.1 OpenApiCallLogResponse

```json
{
  "logId": 70001,
  "provider": "KTO",
  "apiName": "국문 관광정보 서비스",
  "operation": "areaBasedList2",
  "endpoint": "https://apis.data.go.kr/B551011/KorService2/areaBasedList2",
  "requestStartedAt": "2026-07-05T02:00:00+09:00",
  "responseReceivedAt": "2026-07-05T02:00:01+09:00",
  "durationMs": 932,
  "success": true,
  "httpStatus": 200,
  "requestParamsMasked": {
    "areaCode": "1",
    "numOfRows": "100",
    "pageNo": "1",
    "serviceKey": "***"
  },
  "responseSummary": {
    "resultCode": "0000",
    "totalCount": 421,
    "numOfRows": 100,
    "pageNo": 1
  },
  "errorMessage": null,
  "relatedJobId": 3001
}
```

## 13.2 BatchJobResponse

```json
{
  "jobId": 3001,
  "jobType": "KTO_DAILY_SYNC",
  "status": "COMPLETED",
  "startedAt": "2026-07-05T02:00:00+09:00",
  "finishedAt": "2026-07-05T02:13:42+09:00",
  "processedCount": 1200,
  "successCount": 1188,
  "failureCount": 12,
  "message": "국문 관광정보 일일 동기화 완료"
}
```

## 13.3 TourApiSyncCursorResponse

```json
{
  "operation": "areaBasedSyncList2",
  "cursorType": "MODIFIED_TIME",
  "lastSyncedModifiedTime": "20260704235959",
  "lastSuccessAt": "2026-07-05T02:13:42+09:00",
  "nextRunAt": "2026-07-06T02:00:00+09:00"
}
```
