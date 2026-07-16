# Koready 기능별 백엔드 설계서

> 이 문서는 기능 범위 설명 자료다. 확정 정책은 `07`, 사용자/관리자 API는 `08`/`09`, endpoint와 schema는 `openapi.yaml`을 최종 기준으로 사용한다.

## 0. 이 문서의 기준

이 문서는 최신 Figma UI 확인 내용을 진실의 원천으로 두고 작성한 백엔드 설계서다. 기존 제안서/초기 설계와 충돌하는 부분이 있으면 아래 기준을 우선한다.

### 확정된 핵심 방향

- 핵심 타깃: 외국인 유학생, 교환학생, 장기체류 외국인
- 확장 타깃: 단기 여행자
- MVP 언어: 한국어, 영어
- 로그인: Google, Apple 소셜 로그인
- 위치: GPS 사용 안 함
- 사용자의 현재 위치는 직접 검색/선택한 주소를 저장한다.
- 사용자의 현재 위치 주소 입력 기록은 남기고, 사용자가 삭제할 수 있어야 한다.
- 온보딩에서 선호 관광 유형은 최대 4개까지 선택할 수 있다.
- 온보딩에는 음악 추천 서비스처럼 선별된 관광지 후보를 보여주는 취향 보정 단계가 포함된다.
- 관광 데이터는 외부 API를 실시간 화면 데이터로 그대로 쓰지 않고, 백엔드가 호출 후 가공 저장한다.
- 프론트는 Koready 백엔드 API만 호출한다.
- 추천/필터링 기준 마스터는 국문 TourAPI 기반이다.
- 영어 표시는 영문 TourAPI 또는 자체 번역 데이터로 보강한다.
- TMAP은 Buddy Route 이동 경로 확인에만 사용한다.
- 한국여행 꿀팁은 앱 내 정적 콘텐츠로 두고 백엔드 MVP에서 제외한다.

---

## 1. 공통 도메인 개념

## 1.1 서비스 권역

Koready는 전국을 다음 7개 권역으로 나누어 화면/추천/지도 필터의 공통 기준으로 사용한다.

| 권역 코드 | 한글명 | 설명 |
|---|---|---|
| SEOUL | 서울 | 서울특별시 |
| GYEONGGI | 경기 | 경기도와 인천광역시를 포함 |
| GANGWON | 강원 | 강원권 |
| CHUNGCHEONG | 충청 | 대전, 세종, 충북, 충남 |
| JEOLLA | 전라 | 광주, 전북, 전남 |
| GYEONGSANG | 경상 | 부산, 대구, 울산, 경북, 경남 |
| JEJU | 제주 | 제주특별자치도 |

### 설계 메모

- TourAPI의 시도/시군구/법정동 코드를 그대로 화면에 노출하지 않는다.
- 내부에서는 `serviceRegionCode`를 별도로 저장하여 7개 권역 필터를 빠르게 처리한다.
- 행정구역 코드는 원천 데이터 매핑과 상세 주소 처리용으로 유지한다.

---

## 1.2 관광 유형

온보딩/홈 필터/지도 필터/추천에 공통으로 쓰는 사용자 관점 관광 유형이다.

| 코드 | 한글명 | 처리 방식 |
|---|---|---|
| LOCAL_FOOD | 로컬 맛집 | TourAPI 음식점 + 자체 태그 |
| LOCAL_FESTIVAL | 지역 축제 | TourAPI 행사/공연/축제 |
| TRADITIONAL_MARKET | 전통시장 | TourAPI 쇼핑/키워드/AI 태그 보강 |
| CULTURE_EXPERIENCE | 문화 체험 | TourAPI 문화시설/관광지/체험 태그 |
| NATURE | 자연명소 | TourAPI 관광지 + 자연 계열 분류 |
| EXHIBITION_MUSEUM | 전시/미술관 | TourAPI 문화시설 중심 |
| DRAMA_LOCATION | 드라마촬영지 | TourAPI 직접 커버 약함. 키워드/AI/수동 태그 필요 |

---

## 2. 회원가입 / 로그인 / 약관 / 온보딩

## 2.1 기능 정의

사용자는 Google 또는 Apple 소셜 로그인으로 진입한다. 로그인 후에는 약관 동의, 언어 설정, 온보딩을 완료해야 메인 화면에 들어갈 수 있다.

### 필요한 정보

| 구분 | 필드 |
|---|---|
| 소셜 로그인 | provider, providerUserId, email, profileImageUrl |
| 약관 | 약관 종류, 버전, 필수 여부, 동의 여부, 동의 시각 |
| 언어 | preferredLanguage = KO/EN |
| 현재 머무는 곳 | 검색 주소, 상세 주소, 위도/경도, 행정구역, 7권역, 입력/선택 이력 |
| 선호 관광 유형 | LOCAL_FOOD, LOCAL_FESTIVAL, TRADITIONAL_MARKET, CULTURE_EXPERIENCE, NATURE, EXHIBITION_MUSEUM, DRAMA_LOCATION 중 최대 4개 |
| 취향 보정 관광지 | 관리자가 발행한 웰메이드 후보 정확히 10개, 사용자 선택 1~3개 |
| 사용자 취향 태그 | 선택한 관광지의 관광목적/콘셉트 태그를 사용자 선호 태그로 저장 |

## 2.2 설계 포인트

- 약관은 버전업 가능해야 한다.
- 필수 약관 미동의 시 서비스 진입 불가.
- 마케팅 수신 동의는 선택 약관으로 관리한다.
- 사용자는 나중에 마이페이지에서 약관 동의 현황을 조회할 수 있어야 한다.
- 온보딩 현재 위치는 이후 추천의 기본 위치이자 Buddy Route 출발지 기본값이다.
- 현재 위치는 배달앱의 주소 관리처럼 여러 개를 저장하고, 한 개를 기본 위치로 지정하며, 사용자가 개별 삭제할 수 있다.
- 주소·학교·동네 검색은 백엔드가 Kakao Local API를 호출하고 프론트는 단기 `searchResultToken`으로 선택 결과를 저장한다.
- 삭제한 위치는 즉시 목록/추천/경로 출발지에서 제외하고 `deletedAt` 기반 soft delete로 처리한다.
- 기본 위치를 삭제하려면 남은 위치 중 새 기본 위치를 지정해야 한다. 남은 위치가 없으면 기본 위치 없이 삭제하고 다음 추천/경로 진입 전에 위치 등록을 요구한다.
- 온보딩 선호 관광 유형은 1~4개이며 skip할 수 없다.
- 취향 보정 단계는 관리자가 검수·발행한 불변 버전의 관광지 10개를 백엔드가 내려주고, 사용자가 최소 1개~최대 3개를 선택한다.
- 선택된 관광지에 연결된 `place_tags` 중 관광목적/콘셉트성 태그를 `user_preference_tags`로 저장한다.
- 온보딩 순서는 위치 입력, 관광 유형 1~4개, 관심 관광지 1~3개다.
- 온보딩 완료 조건은 약관, 언어, 현재 위치, 선호 관광 유형, 취향 보정 선택을 모두 만족하는 것이다.

## 2.3 온보딩 취향 보정 후보

음악 추천 프로그램의 첫 취향 선택처럼, 사용자가 좋아하는 관광지를 빠르게 고르게 해서 초기 추천 품질을 올린다.

### 후보 구성

- 후보 수: 정확히 10개
- 선택 수: 최소 1개, 최대 3개
- 후보 원천: 이미지/설명/태그 품질이 충분한 `places`
- 후보 선정: 관리자가 7개 관광 유형과 7개 권역이 한쪽으로 쏠리지 않도록 직접 검수한다.
- 표시 콘텐츠: 장소, 순서, 대표 이미지, KO/EN 큐레이터 문구, 화면 태그를 관리한다.
- 버전 정책: `DRAFT -> PUBLISHED -> ARCHIVED`이며 발행본은 수정하지 않고 새 버전으로 교체한다.

### 저장/활용 방식

1. 사용자가 선택한 관광지를 `user_onboarding_place_selections`에 저장한다.
2. 선택 관광지의 태그 중 `STYLE`, `KEYWORD`, `SEASON`, `LOCALITY`, `CONCEPT` 성격의 태그를 추출한다.
3. 추출 태그를 `user_preference_tags`에 저장하고, 같은 태그가 여러 선택 관광지에서 반복되면 weight를 높인다.
4. 이후 K-Local Pick, 월별 추천, 지도 추천에서 관광 유형과 취향 태그를 함께 사용한다.

---

## 3. 홈 화면

## 3.1 기능 정의

홈은 사용자의 현재 위치, 언어 토글, 월별 추천, 한국여행 꿀팁 영역으로 구성된다.

### 백엔드 범위

| 영역 | 백엔드 필요 여부 | 설명 |
|---|---:|---|
| 현재 위치 표시/변경 | O | 사용자가 검색/저장한 위치 목록 관리 |
| 언어 토글 | O | 사용자 선호 언어 저장 및 응답 localization 기준 |
| N월달 추천 | O | 월/지역/날짜/관광유형/정렬 필터 기반 장소 목록 |
| 한국여행 꿀팁 | X | 앱 내 정적 콘텐츠로 처리. 현재 백엔드 제외 |

## 3.2 현재 위치

사용자는 배달앱처럼 위치를 검색해 현재 위치를 추가할 수 있다. 위치 이력은 유저별로 저장하고, 삭제 가능해야 한다.

### 저장 정보

- locationId
- userId
- label 또는 표시명
- roadAddress
- address
- detailAddress
- latitude
- longitude
- sido/sigungu/dong
- serviceRegionCode
- isDefault
- deletedAt

## 3.3 N월달 추천

현재 월 기준으로 “N월달엔 이건 해야지!” 리스트를 제공한다. 전체 보기에서는 1~12월 선택이 가능하다.

### 필터

| 필터 | 값 |
|---|---|
| 연도 | 2000~2100, API 필수. 프론트 초기값은 현재 연도 |
| 월 | 1~12, 기본 현재 월 |
| 지역 | 전체 또는 7개 권역 |
| 날짜 | 전체, 이번주, 이번달, 다음달, 직접 설정 |
| 관광유형 | 7개 관광 유형 |
| 정렬 | 추천순, 마감순 |

### 응답 카드 정보

- placeId
- title
- serviceRegionName
- addressSummary
- imageUrl
- festivalOccurrence: 축제인 경우 개최 회차 ID, 행사 연도, 개최 기간, 상태, 표시 기간
- tags
- saved
- category

### 설계 포인트

- 날짜가 있는 콘텐츠는 행사/축제 중심으로 처리한다.
- 날짜가 없는 명소는 `recommendedMonths`를 별도 가공해서 월별 추천에 넣는다.
- 마감순 정렬 시 종료일이 없는 콘텐츠는 뒤로 보낸다.
- 축제 상세 목록은 행사 시작 6개월 전부터 조회할 수 있다.
- 종료된 축제도 해당 개최 연도·월 목록에는 `ENDED` 상태로 남긴다.
- 홈과 현재 월 추천은 `ONGOING`, `UPCOMING` 축제에 `ENDED`보다 높은 우선순위를 준다.
- 반복 축제는 장소 master와 별개인 연도별 개최 회차로 관리해 이전 연도 기간과 신규 기간을 섞지 않는다.
- 축제 상태는 저장하지 않고 `Asia/Seoul` 조회일과 회차의 시작일/종료일로 `UPCOMING`, `ONGOING`, `ENDED`를 계산한다.
- 시작일 또는 종료일이 없거나 파싱할 수 없는 축제는 날짜 기반 추천에서 제외하고 데이터 보정 대상으로 보낸다.

---

## 4. 지도 화면

## 4.1 기능 정의

지도 화면은 한국을 7개 권역 버튼으로 시각화하고, 사용자가 권역을 누르면 해당 권역 여행지 목록을 제공한다.

### 백엔드 범위

- 7개 권역 메타데이터 제공
- 권역 기반 여행지 목록 제공
- 홈 전체보기와 동일한 추천순/마감순/필터 구조 재사용

### 설계 포인트

- 지도 자체 시각화는 프론트 책임.
- 백엔드는 권역 코드와 권역별 장소 목록만 제공한다.
- 홈 월별 추천과 같은 원천 데이터에서 필터만 다르게 자른다.

---

## 5. MVP1: K-Local Pick 맞춤 관광지 추천

## 5.1 기능 정의

카드더미 방식으로 여행지를 추천한다. 좌/우 스와이프는 좋아요/싫어요가 아니다.

| 동작 | 의미 |
|---|---|
| 좌 스와이프 | 이전 조회 여행지로 돌아가기 |
| 우 스와이프 | 다음 여행지로 넘어가기 |
| 터치 | 여행지 상세보기 |
| 하트 | 저장/좋아요 |

## 5.2 카드 정보

### 기본 카드

- placeId
- imageUrl
- title
- locationText
- saved

### 터치/확장 후 추가 정보

- tags
- shortDescription
- serviceRegionName
- category

## 5.3 추천 범위

| 범위 | 의미 |
|---|---|
| NEARBY | 사용자의 기본 위치가 속한 7개 권역 내부 추천 |
| NATIONWIDE | 전국 7개 권역 전체 추천 |

## 5.4 추천 알고리즘 제안

사용자 제안처럼 거리 기반을 과하게 넣지 않는 방향은 좋다. 다만 완전 랜덤만 쓰면 추천 품질이 떨어질 수 있으므로, 아래처럼 “후보군 필터링 후 무작위”가 적절하다.

### MVP 추천 흐름

1. 사용자 온보딩 정보 조회
2. 추천 범위에 맞는 권역 후보군 생성
3. 사용자가 선택한 선호 관광 유형과 후보 관광지의 관광 유형 매칭
4. 사용자의 취향 태그와 후보 관광지 태그 매칭
5. 후보군을 아래 우선순위 bucket으로 나눈다.
6. 이미지/영문정보/설명 품질이 낮은 장소 제외 또는 감점
7. bucket 내부를 권역/카테고리별로 나누어 섞기
8. 사용자별 seed와 추천 커서로 안정적인 순서 생성
9. 이미 노출한 카드는 view state 기준으로 제외
10. 20개 카드 반환

### 추천 우선순위

| 순위 | 조건 | 설명 |
|---:|---|---|
| 1순위 | 태그 O + 관광 유형 O | 온보딩 취향 태그와 선호 관광 유형이 모두 맞는 후보 |
| 2순위 | 태그 X + 관광 유형 O | 취향 태그는 없지만 사용자가 고른 관광 유형에 맞는 후보 |
| 3순위 | 태그 X + 관광 유형 X | 탐색용 후보. 품질 점수와 다양성 기준으로 제한 노출 |

태그 O + 관광 유형 X 후보는 별도 실험 bucket으로 둘 수 있지만 MVP 기본 우선순위에서는 1순위보다 낮고 2순위와 유사하게 다룬다. 이유는 온보딩에서 사용자가 명시 선택한 관광 유형이 UX상 더 강한 의도이기 때문이다.

### no-repeat / 커서 보장

- 추천 응답은 `deckId`와 `nextCursor`를 함께 내려준다.
- 같은 `deckId` 안에서는 같은 `placeId`가 중복되면 안 된다.
- 다음 페이지 요청 시 `cursor`는 마지막 노출 위치와 seed를 포함한다.
- 서버는 `recommendation_deck_items` 또는 `user_place_recommendation_states`를 기준으로 이미 노출된 카드를 제외한다.
- 클라이언트가 같은 cursor로 재요청하면 같은 결과를 반환하는 것을 목표로 한다.
- 사용자가 앱을 재실행해도 `CARD_SERVED`된 장소는 30일 동안 다시 노출하지 않는다. `suppressUntil = servedAt + 30일`로 계산한다.
- 후보가 부족해도 30일 정책을 줄이지 않는다. 우선순위 3순위/전국 범위로 후보를 넓힌 뒤에도 없으면 `hasMore=false`를 반환한다.

### 왜 완전 랜덤보다 나은가

- 유사사용자 추천으로 넘어갈 때도 `노출 로그`가 있어야 편향을 보정할 수 있다.
- 완전 랜덤은 노출 편향을 줄이지만, 취향과 무관한 카드가 많아져 사용자가 빨리 이탈할 수 있다.
- 따라서 “관심사 기반 후보군 + 탐색용 랜덤”이 MVP에 적합하다.

## 5.5 로그

향후 추천 고도화를 위해 다음 이벤트를 저장한다.

- CARD_SERVED
- CARD_DETAIL_CLICKED
- PLACE_SAVED
- PLACE_UNSAVED
- ROUTE_OPENED
- MATE_TAB_OPENED

좌/우 스와이프 자체는 좋아요/싫어요 의미가 아니므로 긍정/부정 피드백으로 저장하지 않는다. 다만 UX 분석용으로 `CARD_NEXT`, `CARD_PREVIOUS` 정도는 선택적으로 저장할 수 있다.

### 노출 상태 관리

- `CARD_SERVED`는 실제 응답에 포함된 시점에 서버가 기록한다.
- 상세 클릭/저장/루트 열기/메이트 탭 열기는 사용자의 명시 행동 이벤트로 별도 기록한다.
- no-repeat 보장을 위해 단순 이벤트 로그만 믿지 않고, 추천용 view state 또는 deck item 테이블을 둔다.

---

## 6. 여행지 상세 페이지

## 6.1 기능 정의

여행지 상세 페이지는 사진/이름/위치/운영정보/키워드/저장 여부와 3개 탭으로 구성된다.

| 탭 | 기능 |
|---|---|
| 설명 | AI로 정규화한 여행지 설명과 같이 가볼 만한 명소 |
| 이동 | Buddy Route, Hori Tip |
| 메이트 | Buddy Connect |

## 6.2 상단 기본 정보

- placeId
- imageUrls
- title
- locationText
- operatingHours: 있으면 제공
- operatingPeriod: 있으면 제공
- tags
- isSaved

## 6.3 설명 탭

TourAPI 설명을 그대로 노출하기보다, 통일된 포맷으로 가공한다.

### 설명 데이터 구조

- impactTitle: 임팩트 두줄 소개 1줄
- impactSubtitle: 임팩트 두줄 소개 2줄
- introParagraphs: 2~3문단
- enjoyPoints: “이렇게 즐겨보세요” 리스트
- sourceType: KTO_ONLY / AI_GENERATED / MANUAL_EDITED
- generatedAt
- sourceHash

### 생성 방식

1. TourAPI의 overview/detailIntro/detailInfo/event 정보를 수집한다.
2. 원천 데이터 해시를 계산한다.
3. 설명 데이터가 없거나 sourceHash가 바뀐 경우 AI 생성 작업 대상에 넣는다.
4. 생성된 문구는 원천 데이터와 분리해 저장한다.
5. 한국어/영어 각각 localization을 저장한다.

## 6.4 같이 가보면 좋을 명소

우선순위:

1. 관광지별 연관 관광지 정보 API 기반 3개
2. 없으면 같은 권역 + 같은/유사 태그 기반 3개 랜덤
3. 그래도 없으면 같은 권역 인기/추천순 3개

응답 정보:

- placeId
- title
- imageUrl
- shortDescription

---

## 7. MVP2: Buddy Route 이동 가능성 판단

## 7.1 기능 정의

사용자가 선택한 여행지까지의 이동 가능성을 판단한다. 출발지는 GPS가 아니라 사용자의 기본 위치다. 도착지는 관광지 좌표다.

## 7.2 요약 정보

- originName
- destinationName
- recommendedTransportText: 예) `지하철 + KTX + 버스`
- estimatedOneWayTimeText
- estimatedOneWayMinutes
- difficulty: EASY / NORMAL / HARD
- dayTripStatus: DAY_TRIP_AVAILABLE / STAY_RECOMMENDED
- fare.oneWayEstimated
- fare.roundTripEstimated

## 7.3 당일치기 판단

| 기준 | 결과 |
|---|---|
| 편도 180분 미만 | 당일치기 가능 |
| 편도 180분 이상 | 1박 이상 권장 |

판정은 TMAP 원본 `totalTime` 초를 사용한다. `totalTime < 10800`이면 당일치기 가능이며 표시용 분 반올림 결과로 판정하지 않는다.

## 7.4 이동 난이도 계산 초안

사용자 정의 점수 기반으로 시작한다.

| 이동수단 | 점수 |
|---|---:|
| 지하철 | 1 |
| 서울권 시내/마을버스 | 1 |
| 지방권 시내/마을버스 | 2 |
| 셔틀버스 | 2 |
| 기차(KTX, 무궁화, SRT) | 2 |
| 시외/고속버스 | 3 |
| 비행기 | 1 |

### 초기 계산식

```text
baseScore = 도보 제외 segment별 이동수단 점수 합
transferPenalty = transferCount * 0.5 또는 1.0
walkPenalty = totalWalkDistance >= 1000m ? 1 : 0
finalScore = baseScore + transferPenalty + walkPenalty
```

### 초기 threshold 후보

| 점수 | 난이도 |
|---:|---|
| 0 ~ 3 | 쉬움 |
| 3.5 ~ 6 | 보통 |
| 6 초과 | 어려움 |

Threshold는 실제 테스트 경로를 모아 조정한다.

## 7.5 상세 경로

각 segment는 박스 형태로 제공한다.

- startName
- endName
- mode
- routeName
- durationMinutes
- distanceMeters
- fare
- description
- horiTips[]

---

## 8. Hori Tip 이동 관련 툴팁

## 8.1 기능 정의

Hori Tip은 운영진이 직접 작성하고 활성화하는 이동 안내다. TMAP이나 AI가 본문을 생성하지 않으며, TMAP 경로 응답은 저장된 팁의 매칭 조건을 평가하는 재료로만 사용한다. 기존 내부 테이블명 `buddy_tip_*`은 유지할 수 있다.

이 기능은 코레일톡 화면을 따라가는 KTX 예매 가이드와 별개다. Hori Tip은 Buddy Route 응답 안의 짧은 이동 문구만 백엔드가 관리한다. KTX 예매 가이드·시뮬레이션의 단계별 화면, 문구, 이미지는 프론트 정적 콘텐츠이며 백엔드 API나 DB를 만들지 않는다.

예시:

- 김천역과 김천(구미)역은 다른 역이에요.
- KTX를 이용할 때는 반드시 김천(구미)역으로 검색하세요.
- 축제 기간에는 셔틀버스 시간표를 미리 확인하세요.

## 8.2 운영과 조회 흐름

1. 운영진이 관리자 API에서 본문, 노출 위치, 대상 장소, 매칭 조건과 노출 기간을 저장한다.
2. 초안은 사용자에게 노출하지 않고 `ACTIVE` 상태이며 노출 기간 안에 있는 팁만 조회한다.
3. `POST /routes`와 `GET /routes/{routeId}` 응답 직전에 목적지와 정규화된 경로를 기준으로 팁을 매칭한다.
4. `TOP_SUMMARY` 팁은 `summary.horiTips[]`, `AFTER_SEGMENT` 팁은 매칭된 `segments[].horiTips[]`에 조합한다.
5. TMAP 경로 캐시에는 팁을 저장하지 않는다. 같은 `routeId`를 다시 조회해도 현재 활성 상태를 다시 평가한다.

## 8.3 트리거 기준

현재 UI 기준으로는 도착 위치를 중심으로 트리거한다.

### 트리거 후보

- segment.endName contains `김천(구미)역`
- segment.route contains `KTX`
- segment.mode = TRAIN
- segment.mode = BUS and route contains `셔틀`
- totalWalkDistance >= 1000
- transferCount >= 2

## 8.4 저장 정보

- tipTemplateId
- code
- language
- title: Hori Tip 고정
- body
- triggerRuleJson
- priority
- placement: TOP_SUMMARY / AFTER_SEGMENT
- scopeType: ALL_ROUTES / DESTINATION_PLACES
- destinationPlaceIds[]
- status: DRAFT / ACTIVE / INACTIVE / ARCHIVED
- validFrom / validUntil
- version
- createdBy / updatedBy

---

## 9. MVP3: Buddy Connect 여행지 버디 연결

## 9.1 기능 정의

여행지 상세 페이지의 메이트 탭에서 해당 여행지에 관심 있는 사용자를 보여주고, 비실시간 쪽지를 보낼 수 있게 한다.

## 9.2 프로필 설정

필드:

- profileImageUrl
- nickname
- nationality
- availableLanguages[]
- koreanLevel
- bio
- buddyStyles[]
- socialLinks[]
- isProfilePublic
- isSnsPublic
- allowsMessages

## 9.3 메이트 목록

대상:

- 해당 여행지를 하트/저장한 사용자
- 프로필 공개 상태가 true
- 차단/신고 정책에 걸리지 않는 사용자

응답:

- profileId
- nickname
- profileImageUrl
- nationality
- availableLanguages
- koreanLevel
- bio
- buddyStyles
- socialLinks: SNS 공개 true일 때만
- canMessage

## 9.4 쪽지

- 실시간 채팅이 아니다.
- 1000자 이내 메시지.
- 마이페이지 쪽지함에서 조회.
- 전송 시 Expo push 알림을 보낼 수 있다.

### 안전성 추가 제안

Buddy Connect는 사용자 간 연결 기능이므로 MVP라도 최소한 아래는 고려한다.

- 차단 기능
- 신고 기능
- 메시지 수신 거부
- 내 프로필 비공개
- SNS 비공개

---

## 10. 저장 화면

## 10.1 기능 정의

홈 월별 추천, 추천 카드, 상세 페이지 등에서 하트를 누른 여행지를 모아 보여준다.

## 10.2 저장 정보

- userId
- placeId
- savedAt
- source: HOME_MONTHLY / RECOMMENDATION_CARD / PLACE_DETAIL / MAP

## 10.3 응답 카드

- placeId
- title
- imageUrl
- serviceRegionName
- addressSummary
- category
- dateRangeText
- tags

---

## 11. 마이페이지

## 11.1 기능

- 내 정보 조회
- 기본 언어 설정
- 기본 위치 관리
- Buddy Connect 프로필 설정
- 쪽지함 조회
- 약관 동의 현황 조회

---

## 12. MVP 제외/프론트 처리 범위

| 기능 | 처리 |
|---|---|
| KTX 예매 가이드·시뮬레이션 | 프론트 정적 콘텐츠. 단계별 문구와 이미지는 기획이 전달 |
| 체크리스트 탭 | MVP 제외 |
| 실시간 채팅 | MVP 제외. 쪽지로 대체 |
| GPS 현재 위치 | 사용 안 함 |
| 영상 가이드 | MVP 제외 |
| 오디오 가이드 | MVP 제외 |
| 숙박 추천 | 숙박권장 상태만 표시. 숙박 리스트 추천은 후순위 |
