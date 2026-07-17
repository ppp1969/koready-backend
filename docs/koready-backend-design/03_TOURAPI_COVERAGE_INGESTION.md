# 한국관광공사 OpenAPI 커버리지 / 저장 / 가공 / 스케줄링 설계

## 0. 전제

Koready는 한국관광공사 OpenAPI를 프론트에서 직접 호출하지 않는다. 백엔드가 API를 호출하고, 서비스 화면/추천에 필요한 형태로 가공 저장한다.

### 공식 확인 기준

- 확인일: 2026-07-05
- 국문 관광정보 서비스: 공공데이터포털 기준 수정일 2026-02-26, JSON/XML REST, 개발계정 일 1,000건, 15종 약 26만 건
- 영문 관광정보 서비스: 공공데이터포털 기준 수정일 2026-02-26, JSON/XML REST, 개발계정 일 1,000건, 14종 약 8만 건
- 관광사진 정보: 포토코리아 기반 사진 제목/촬영일/촬영지/촬영자/키워드/웹용 이미지 URL 보강용
- 관광지별 연관 관광지 정보: TMAP 내비게이션 데이터 기반 연관 관광지. 중심 관광지별 전체/관광지/음식/숙박 유형별 최대 50위
- TMAP 대중교통 API: 출발/도착 좌표 기반 대중교통 상세/요약 경로. 상세 endpoint는 `/transit/routes`, 요약 endpoint는 `/transit/routes/sub`

## 0.1 핵심 판단

| 영역 | TourAPI 커버 가능 여부 | 판단 |
|---|---:|---|
| 관광지명/주소/좌표 | 높음 | 국문 TourAPI 목록/상세로 가능 |
| 이미지 | 높음 | detailImage2, firstimage 활용. 부족하면 관광사진 API 보강 |
| 지역/카테고리 필터 | 높음 | areaBasedList2, lclsSystmCode2, 법정동코드 활용 |
| 행사/축제 기간 | 높음 | searchFestival2 활용 |
| 영문 표시 | 중간~높음 | 영문 TourAPI 활용. 누락 시 자체 번역 필요 |
| 전통시장 | 중간 | 쇼핑/키워드/AI 태그 보강 필요 |
| 드라마촬영지 | 낮음~중간 | TourAPI만으로 안정적 커버 어려움. 키워드/AI/수동 태그 필요 |
| AI 해시태그 | 직접 커버 X | 자체 태그 pool + AI/룰 매칭 필요 |
| 통일된 상세 설명 | 직접 커버 X | TourAPI 원문 기반 AI 재가공 필요 |
| 관련 명소 | 중간 | 관광지별 연관 관광지 API 우선, 없으면 자체 fallback |
| Buddy Route | 커버 X | TMAP 대중교통 API 필요 |
| Hori Tip | 커버 X | 자체 DB 트리거 룰 필요 |
| Buddy Connect/쪽지 | 커버 X | 자체 DB 기능 |
| 약관/온보딩/저장 | 커버 X | 자체 DB 기능 |

---

## 1. 사용할 API 후보

## 1.1 국문 관광정보 서비스

국문 TourAPI는 Koready 관광지 마스터의 기준이다. 공공데이터포털 기준 국문 관광정보 서비스는 지역코드, 서비스분류코드, 법정동코드, 분류체계코드, 지역/위치/키워드/행사/숙박/상세/이미지/동기화/반려동물 정보를 제공한다. Koready에서는 국문 데이터를 정규화 기준으로 삼고, 영문/이미지/관련 명소는 보강 계층으로 붙인다.

### operation별 저장/가공 방식

| 오퍼레이션 | 사용 목적 | 저장/가공 방식 | MVP 우선순위 |
|---|---|---|---:|
| `areaCode2` | 시도/시군구 지역코드 조회 | `administrative_regions`에 area/sigungu 코드 저장. 7개 `serviceRegionCode` 매핑의 보조 데이터로 사용 | 1 |
| `categoryCode2` | 기존 서비스 분류 코드 조회 | `tour_api_category_codes` 또는 seed enum으로 저장. `contentTypeId` 보조 분류와 레거시 매핑 검증에 사용 | 2 |
| `ldongCode2` | 법정동 코드 조회 | `administrative_regions`에 법정동 시도/시군구/동 코드 저장. 주소 정규화와 권역 매핑 품질 보강 | 1 |
| `lclsSystmCode2` | 신분류체계 코드 조회 | `tour_api_lcls_codes` 또는 `administrative/taxonomy` 테이블로 저장. 7개 관광 유형 매핑의 주요 근거 | 1 |
| `areaBasedList2` | 지역/카테고리 기반 관광정보 목록 | `places` upsert의 기본 수집원. contentId, contentTypeId, 좌표, 주소, firstImage, modifiedTime 저장 | 1 |
| `locationBasedList2` | 좌표 기반 주변 관광정보 목록 | GPS 미사용이므로 핵심 아님. 사용자 저장 위치 주변 품질 확인/지도 추천 fallback에 제한 사용 | 3 |
| `searchKeyword2` | 키워드 기반 관광정보 검색 | 전통시장, 드라마촬영지, 로컬 키워드 후보 보강. 결과는 `places` upsert 후 `place_tags`/`place_style_mappings`에 키워드 출처 저장 | 1 |
| `searchFestival2` | 행사/축제 정보 검색 | 월별 추천 핵심. `places`, `festival_series`, `place_event_occurrences`, `place_monthly_features` 저장 | 1 |
| `searchStay2` | 숙박 정보 검색 | MVP에서는 숙박 리스트 추천 제외. Buddy Route가 `STAY_RECOMMENDED`일 때 향후 숙박 추천 후보로 확장 | 4 |
| `detailCommon2` | 공통 상세 조회 | `places`, `place_localizations(KO)` 보강. overview, homepage, tel, 주소, 좌표, modifiedTime hash 계산에 사용 | 1 |
| `detailIntro2` | 타입별 소개정보 조회 | `place_intro_details` key-value 저장. 운영시간, 휴무일, 주차, 요금, 행사 세부정보를 표준 필드로 매핑 | 1 |
| `detailInfo2` | 반복 상세정보 조회 | 코스, 객실, 기타 반복 항목을 `place_intro_details` 또는 별도 JSON detail로 저장. MVP 화면 필드에 필요한 항목만 표준화 | 2 |
| `detailImage2` | 이미지 목록 조회 | `place_images` 저장. 대표 이미지 선정, 카드 품질 점수, 이미지 fallback 판단에 사용 | 1 |
| `areaBasedSyncList2` | 변경/삭제/비노출 동기화 | `tour_api_sync_cursors`, `places.active/show_flag/source_modified_time` 갱신. 삭제/비노출은 soft inactive 처리 | 1 |
| `detailPetTour2` | 반려동물 동반여행 상세 | MVP 핵심은 아님. 추후 반려동물 친화 태그/필터로 확장하려면 `place_pet_infos` 별도 저장 | 4 |

## 1.2 영문 관광정보 서비스

영문 TourAPI는 추천/필터링 기준이 아니라 표시 데이터 보강용이다. 영문 서비스는 공공데이터포털 기준 국내 관광정보 14종 약 8만 건을 제공하므로, 국문보다 누락이 있을 수 있다는 전제를 둔다.

### 저장 방향

- 국문 contentId와 매칭되면 `place_localizations(language=EN)`에 저장
- contentId가 다르면 좌표/주소/명칭 기반 매칭 필요
- 영문 데이터가 없으면 AI 번역을 허용하고, 필요한 경우 수동 번역으로 교정
- 번역 출처를 반드시 저장
- 영문 API가 제공한 필드는 `KTO_EN`, 국문 기반 자체 번역은 `AI_TRANSLATED`, 운영자가 수정하면 `MANUAL_EDITED`로 승격한다.
- 추천/필터링/권역 매핑은 항상 국문 master 기준으로 수행하고, 응답 직전에 localization만 선택한다.

```text
translationSource = KTO_EN | AI_TRANSLATED | MANUAL_EDITED
```

AI 번역은 확정 허용 정책이다. 원문 hash, 모델명, prompt version, 생성 시각을 보존하고 국문 원문이 바뀌면 재생성 대상으로 표시한다. 영문 TourAPI 매칭 결과가 있으면 `KTO_EN`을 우선하고, AI 번역은 fallback으로 사용한다.

## 1.3 관광사진 정보 API

TourAPI 이미지가 부족하거나 카드 품질이 낮을 때 보강용으로 사용한다.

### 저장 방향

- 원본 이미지 URL 저장
- 썸네일 URL이 있으면 별도 저장
- 출처/저작권/공공누리 유형 저장
- 자체 S3 복사는 MVP에서 필수 아님
- 카드/상세에 사용할 수 없는 이미지가 없으면 추천 점수를 감점하고, 이미지 없는 장소는 K-Local Pick 카드 후보에서 후순위로 둔다.
- 이미지 라이선스/출처 표시 문구는 `place_images.license_text`에 저장하고 프론트에서 필요한 경우 노출할 수 있게 한다.

## 1.4 관광지별 연관 관광지 정보 API

상세 설명 탭 하단 “같이 가보면 좋을 명소”의 1순위 데이터로 사용한다.

공식 설명상 이 API는 TMAP 내비게이션 데이터를 기반으로 산출된 연관 관광지이며, 전체/관광지/음식/숙박 유형별 최대 50위 정보를 제공한다. 단, 차량 이동 기반의 연관성이므로 실제 대중교통 동선이나 외국인 여행자 동선과 다를 수 있다.

### 저장 방향

- sourcePlaceId
- relatedPlaceId
- relationType
- score 또는 rank
- source = KTO_RELATED
- lastSyncedAt
- 원천의 중심 관광지와 Koready `places` 매칭은 이름/주소/좌표 기반으로 수행한다.
- 매칭 실패 원천 row는 버리지 말고 `open_api_raw_snapshots` 또는 별도 staging에 보관해 수동 매칭 후보로 남긴다.

API 결과가 없으면 자체 fallback을 사용한다.

---

## 2. 기능별 API 커버리지

## 2.1 홈 월별 추천

| 화면 필드 | 원천/API | 저장 테이블 | 가공 방식 |
|---|---|---|---|
| 제목 | 국문/영문 TourAPI | place_localizations | language별 title 저장 |
| 지역 | TourAPI area/sigungu/lDong | places, administrative_regions | 7권역 serviceRegionCode 매핑 |
| 사진 | firstimage/detailImage2/관광사진 API | place_images | 대표 이미지 선정 |
| 개최 회차 | searchFestival2 | festival_series, place_event_occurrences | 행사 연도와 start/end date를 회차별 저장, 상태/dateRangeText 계산 |
| 관광유형 | contentTypeId/lcls/keyword | place_style_mappings | 사용자용 7개 유형으로 매핑 |
| 추천순 | 자체 점수 | place_recommendation_scores | 이미지/영문/태그/월 적합성 점수 |
| 마감순 | eventEndDate | place_event_occurrences | 진행/예정 회차 우선, endDate null은 뒤로 정렬 |

축제 회차는 `eventStartDate - 6개월`부터 조회 가능하다. 종료 뒤에도 해당 `eventYear/month` 상세 목록에는 `ENDED`로 남기며, 홈과 현재 추천은 `ONGOING`, `UPCOMING`에 더 높은 우선순위를 준다. 상태는 `Asia/Seoul`의 조회일과 회차 기간으로 계산한다. 날짜 누락/파싱 실패 행은 날짜 기반 추천에서 제외한다.

## 2.2 K-Local Pick

| 화면 필드 | 원천/API | 저장 테이블 | 가공 방식 |
|---|---|---|---|
| 사진 | detailImage2 | place_images | 대표 이미지 1장 |
| 이름 | TourAPI | place_localizations | 언어별 title |
| 위치 | TourAPI address | places/localizations | 시군구 단위 표시 |
| 하트 여부 | 자체 DB | user_saved_places | userId/placeId 조회 |
| 키워드 | 자체 AI/룰 | place_tags | 태그 pool 기반 매칭 |
| 설명 | TourAPI + AI | place_ai_descriptions | 짧은 카드 설명 생성 |
| 사용자 취향 태그 | 온보딩 선택 관광지 | user_preference_tags | 선택 관광지의 태그를 사용자 weight로 변환 |
| 추천 우선순위 | 자체 계산 | recommendation_deck_items | 태그/관광유형 매칭 여부로 1/2/3순위 bucket |
| 중복 방지 | 자체 DB | recommendation_decks, user_place_recommendation_states | deck 내 중복 금지, suppressUntil 전 재노출 방지 |

MVP v1의 `suppressUntil`은 `CARD_SERVED` 시각부터 정확히 30일로 계산하며 후보 부족을 이유로 단축하지 않는다. `servedAt`, `suppressionDays=30`, `policyVersion=recommendation-suppression-v1`, 후보 소진율을 함께 남긴다. 서비스 테스트에서 후보가 지나치게 빨리 줄어드는 근거가 확인되면 새 정책 버전에서 기록 시점 또는 기간을 조정하고 기존 노출 이력을 소급 변경하지 않는다.

### K-Local Pick 우선순위

1. 태그 O + 관광 유형 O
2. 태그 X + 관광 유형 O
3. 태그 X + 관광 유형 X

온보딩에서 저장한 `user_preference_tags`는 1순위 후보를 만들기 위한 가중치로 쓰고, 사용자가 직접 선택한 `user_travel_styles`는 1순위와 2순위 후보를 나누는 기본 필터로 쓴다.

## 2.3 상세 설명 탭

| 화면 필드 | 원천/API | 저장 테이블 | 가공 방식 |
|---|---|---|---|
| 기본 사진/정보 | TourAPI | places/place_images | 그대로 표시 가능한 필드 정규화 |
| 운영시간 | detailIntro2 | place_intro_details | type별 필드 flatten 또는 key-value 저장 |
| 운영기간 | searchFestival2/detailIntro2 | place_event_occurrences | 행사 연도·회차별 날짜 텍스트와 상태 생성 |
| 임팩트 소개 | TourAPI + AI | place_ai_descriptions | AI 생성 |
| 간단 소개 | TourAPI + AI | place_ai_descriptions | 2~3문단 생성 |
| 즐기는 포인트 | TourAPI + AI | place_ai_description_points | 리스트 생성 |
| 같이 가볼 명소 | 연관 관광지 API | place_relations | 3개 우선 제공 |

## 2.4 Buddy Route / Hori Tip

TMAP 대중교통 API는 `POST https://apis.openapi.sk.com/transit/routes`로 상세 경로를, `POST https://apis.openapi.sk.com/transit/routes/sub`로 요약 경로를 제공한다. 요청은 출발/도착 WGS84 좌표를 기준으로 하고, 응답에는 `totalTime`(초), `transferCount`, `totalWalkDistance`, `totalFare`, `legs[].mode`, `legs[].route`, `legs[].sectionTime`, `legs[].distance` 같은 가공 가능한 필드가 포함된다.

| 화면 필드 | 원천/API | 저장 테이블 | 가공 방식 |
|---|---|---|---|
| 출발지 | 사용자 위치 | user_locations | 기본 위치 사용 |
| 도착지 | places 좌표 | places | 관광지 좌표 사용 |
| 예상 시간 | TMAP | 24시간 미만 임시 캐시 | 판정은 원본 `totalTime` 초, 표시는 분 단위 반올림 |
| 교통수단 | TMAP | 영구 저장 안 함. 응답 DTO만 구성 | `legs[].mode/route`를 내부 `TransportMode`로 정규화 |
| 난이도 | 자체 계산 | 24시간 미만 임시 캐시 | mode score + transferCount + totalWalkDistance |
| 당일치기 여부 | 자체 계산 | 24시간 미만 임시 캐시 | 편도 180분 기준 |
| 예상 교통비 | TMAP | 24시간 미만 임시 캐시 | `fare.regular.totalFare` |
| Hori Tip | 운영진 자체 DB | buddy_tip_* | 운영진 작성 본문을 목적지와 segment.endName/route/mode/transfer/walk 조건으로 매칭 |

### TMAP 응답 가공 규칙

- 운영 요청은 상세 `/transit/routes` 한 번에 `count=3` 후보를 받고, `departureAt`을 Asia/Seoul 기준 `searchDttm=yyyyMMddHHmm`으로 변환한다.
- 시간은 TMAP 초 단위 값을 `ceil(seconds / 60)`로 올림해 분 단위로 표시한다.
- 당일치기 판정은 표시용 분 값이 아니라 원본 초 값으로 계산한다. `totalTime < 10800`이면 `DAY_TRIP_AVAILABLE`, 그 외에는 `STAY_RECOMMENDED`다.
- `legs[].mode`는 `WALK`, `BUS`, `SUBWAY`, `EXPRESSBUS`, `TRAIN`, `AIRPLANE`, `FERRY`를 처리하고, KTX/SRT 등은 mode별 공식 `type` 코드와 `route`를 함께 사용한다.
- 실제 응답의 다중 노선 필드는 공식 표의 `lane`과 달리 `Lane`으로 내려올 수 있으므로 client DTO에서 두 casing을 모두 허용한다.
- 정상 itinerary에도 `service=0` leg가 존재할 수 있다. 필수 대중교통 leg가 모두 `service=1`인 후보를 우선하고, 그런 후보가 없으면 422 `ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME`으로 변환한다.
- HTTP 200이어도 최상위 `result`가 있고 `metaData.plan.itineraries`가 없으면 성공이 아니다. provider code 11~14는 422 `ROUTE_NOT_FOUND`로 변환한다.
- TMAP 원본 `legs`, `steps`, `linestring` 전체는 기본 DB에 영구 저장하지 않는다.
- 동일 사용자/출발지/목적지의 반복 호출은 기본 30분 short cache를 사용한다. TMAP 기반 원본/정규화 값은 24시간 전에 반드시 삭제한다.
- Hori Tip 본문은 TMAP 또는 AI로 생성하지 않는다. 운영진이 관리자 API로 저장한 `ACTIVE` 팁만 사용한다.
- short cache에는 정규화 경로만 저장하고 Hori Tip 조합 결과는 저장하지 않는다. `POST /routes`와 `GET /routes/{routeId}` 응답 시점마다 현재 활성 팁을 조회해 조합한다.
- `route_query_logs`에는 경로 시간·요금·구간을 남기지 않고 호출 성공 여부, 오류 코드, 지연시간만 남긴다.
- API 오류는 `open_api_call_logs`와 `route_query_logs.error_message`에 남기고, 사용자에게는 "대중교통 경로를 찾지 못했어요" 수준의 일반화된 메시지를 반환한다.

## 2.5 Buddy Connect

TourAPI 커버 불가. 자체 DB 기능이다.

---

## 3. TourAPI 원천 → 가공 저장 모델

## 3.1 places

TourAPI contentId 단위의 핵심 관광지 마스터.

저장 필드 후보:

- placeId: 내부 PK
- ktoContentId
- ktoContentTypeId
- titleKo: 임시/검색용. 정식 표시는 place_localizations 사용
- serviceRegionCode
- areaCode
- sigunguCode
- lDongRegnCd
- lDongSignguCd
- lDongCode
- lclsSystm1/2/3
- address
- roadAddress
- latitude
- longitude
- tel
- homepage
- firstImageUrl
- sourceModifiedTime
- showFlag
- active
- dataQualityScore
- createdAt/updatedAt

## 3.2 place_localizations

언어별 표시 정보.

- placeLocalizationId
- placeId
- language
- title
- overview
- addressText
- homepage
- translationSource
- sourceContentId
- sourceHash
- updatedAt

## 3.3 place_images

이미지 정보.

- imageId
- placeId
- sourceType: KTO_TOURAPI / KTO_PHOTO / MANUAL / S3
- imageUrl
- thumbnailUrl
- copyrightType
- licenseText
- originTitle
- displayOrder
- isPrimary
- active

### 대표 이미지 선정 기준

1. TourAPI firstimage가 있으면 우선
2. detailImage2 이미지 중 가로형/해상도 좋은 이미지 우선
3. 관광사진 API 이미지 보강
4. 없으면 추천 점수 감점 또는 기본 이미지 사용

## 3.4 festival_series / place_event_occurrences

매년 반복되는 축제의 공통 정체성과 실제 개최 회차를 분리한다.

`festival_series`:

- festivalSeriesId
- seriesKey
- canonicalPlaceId
- titleKo/titleEn

`place_event_occurrences`:

- occurrenceId
- festivalSeriesId
- placeId
- eventYear
- occurrenceSequence
- startDate
- endDate
- startTime
- endTime
- eventPlace
- playTime
- sponsor
- useFee
- provider/sourceContentId
- sourceOperation
- sourceHash

KTO 회차는 `(provider, sourceContentId, eventYear, occurrenceSequence)`, 시리즈 회차는 `(festivalSeriesId, eventYear, occurrenceSequence)`를 unique로 둔다. 공급자가 개최 기간을 정정하면 같은 회차의 날짜를 갱신하고 원천 snapshot/hash 이력으로 변경 전 값을 추적한다. `UPCOMING/ONGOING/ENDED`는 DB에 고정 저장하지 않고 조회 시 계산한다.

## 3.5 place_intro_details

detailIntro2 타입별 필드 저장.

방식 A: 타입별 컬럼 테이블

- festival_intro_details
- food_intro_details
- attraction_intro_details

방식 B: key-value 저장

- placeId
- contentTypeId
- key
- value
- language

### 추천

MVP에서는 key-value 방식이 빠르다. 화면에 필요한 필드만 service layer에서 표준 필드로 매핑한다.

표준 필드:

- operatingHours
- restDate
- useFee
- parking
- eventStartDate
- eventEndDate
- eventPlace
- playTime

## 3.6 place_tags

AI/룰 기반 태그.

- placeId
- tagId
- source: KTO_CATEGORY / RULE_BASED / AI_GENERATED / MANUAL
- confidence
- generatedAt

## 3.7 place_ai_descriptions

상세 설명 탭용 AI 가공 데이터.

- placeId
- language
- impactTitle
- impactSubtitle
- introText
- sourceHash
- generationStatus
- modelName
- promptVersion
- reviewed
- generatedAt

즐기는 포인트는 별도 테이블 또는 JSON 배열로 저장한다.

## 3.8 place_monthly_features

월별 추천용 보조 데이터.

- placeId
- month
- reasonType: EVENT_OCCURRENCE / PHOTO_SEASON_HINT / AI_SEASON / MANUAL
- reasonText
- score

---

## 4. 관광 유형 매핑 전략

사용자 화면의 7개 관광 유형은 TourAPI의 contentTypeId/lcls/category와 1:1로 깔끔하게 떨어지지 않는다. 따라서 내부 매핑 테이블을 둔다.

## 4.1 1차 매핑

| 사용자 유형 | 1차 매핑 |
|---|---|
| 로컬 맛집 | contentTypeId=39 음식점 |
| 지역 축제 | contentTypeId=15 행사/공연/축제 |
| 전통시장 | 쇼핑/시장 키워드/전통시장 태그 |
| 문화 체험 | 문화시설/관광지 + 체험 키워드 |
| 자연명소 | 관광지 + 자연 분류 |
| 전시/미술관 | 문화시설 + 전시/미술관 분류 |
| 드라마촬영지 | 키워드/AI/수동 태그 |

## 4.2 AI 태깅 필요 항목

- 역사여행
- 카페 거리
- 인생샷 명소
- 드라마촬영지
- 외국인 방문 난이도
- 로컬성
- 계절성
- 실내/실외

---

## 5. 스케줄링 전략

## 5.1 단계별 파이프라인

```text
1. 코드 데이터 동기화
2. 관광지 목록 수집
3. 상세 정보 보강
4. 이미지 보강
5. 영문 데이터 보강
6. 관련 명소 동기화
7. AI 태그/설명/월별 특성 생성
8. 추천 점수 계산
9. 비노출/삭제 데이터 처리
```

## 5.2 스케줄 후보

| 작업 | 주기 | 설명 |
|---|---|---|
| 법정동/분류코드 동기화 | 주 1회 또는 수동 | 자주 바뀌지 않음 |
| 행사/축제 동기화 | 매일 새벽 | 월별 추천 핵심 |
| 관광지 목록 동기화 | 매일 새벽 또는 2~3일 1회 | 개발계정 quota 고려 |
| 상세정보 보강 | 목록 수집 후 queue 처리 | 신규/수정 content만 |
| 이미지 보강 | 매일/주 1회 | 이미지 없는 장소 우선 |
| 영문 데이터 보강 | 국문 upsert 후 | contentId 매칭 검증 필요 |
| 관련 명소 동기화 | 매일/주 1회 | 상세 페이지 하단 추천 |
| AI 태그 생성 | 야간 batch | 태그 없는 장소 우선 |
| AI 설명 생성 | 야간 batch | sourceHash 변경 시 재생성 |
| 추천 점수 재계산 | batch 종료 후 | 카드/홈/지도 추천순에 사용 |

## 5.3 개발계정 quota 대응

공공데이터 개발계정 호출 제한이 있으므로 MVP 개발 중에는 전체 전국 풀스캔을 피한다.

권장 전략:

1. 7개 권역별 대표 시군구 또는 공모전 시연용 지역을 먼저 수집한다.
2. `numOfRows`를 크게 잡기보다 페이지 단위로 호출 로그를 남긴다.
3. 변경 동기화는 `areaBasedSyncList2`를 우선 검토한다.
4. 상세/이미지/영문/AI 보강은 모든 장소가 아니라 추천 후보 우선순위가 높은 장소부터 한다.
5. API 호출 로그를 통해 공모전 증빙과 디버깅을 동시에 처리한다.

---

## 6. OpenAPI 호출 로그

모든 한국관광공사 API 호출은 증빙용 로그를 남긴다.

저장 후보:

- provider: KTO
- apiName
- operation
- endpoint
- requestStartedAt
- responseReceivedAt
- durationMs
- success
- httpStatus
- requestParamsMasked
- responseSummary
- errorMessage
- relatedJobId

주의:

- serviceKey는 저장하지 않는다.
- 성공한 KTO 응답은 파싱 전에 수신한 원본 byte를 private object storage에 gzip으로 저장한다.
- 원본 snapshot은 call log ID, 원문 SHA-256, 저장 객체 SHA-256, byte 크기, 수집 시각, 보관 등급과 연결한다.
- KTO 원본 snapshot은 공모전 증빙 대상이며 immutable로 취급한다.
- TMAP/KAKAO 원문에는 KTO 보관 정책을 적용하지 않는다. TMAP 경로 데이터는 24시간 전에 삭제하고 KAKAO 사용자 검색 원문은 기본 보관하지 않는다.

---

## 7. Fallback 전략

## 7.1 영문 정보 없음

1. 영문 TourAPI 매칭 시도
2. 없으면 국문 데이터를 AI 번역
3. 중요한 장소는 수동 검수 가능하도록 `MANUAL_EDITED` 출처 지원

## 7.2 이미지 없음

1. detailImage2 조회
2. 관광사진 API 조회
3. 그래도 없으면 추천 점수 감점
4. 기본 이미지 사용

## 7.3 관련 명소 없음

1. 같은 권역 + 같은 관광 유형
2. 같은 시군구 + 다른 유형
3. 같은 권역 랜덤 추천

## 7.4 AI 설명 생성 실패

1. TourAPI overview를 그대로 표시
2. 짧은 기본 설명 템플릿 사용
3. 다음 batch에서 재시도
