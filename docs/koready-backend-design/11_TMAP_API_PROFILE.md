# TMAP 대중교통 API 프로파일

## 0. 상태

- 프로파일 일자: 2026-07-13 (Asia/Seoul)
- 대상: `POST https://apis.openapi.sk.com/transit/routes`
- 실제 호출: 3회
- 요청당 후보 수: `count=1`
- 언어: `lang=0` (한국어)
- 원본 응답 저장: 없음
- secret 출력/저장: 없음

이 문서는 TMAP 원본 응답을 보관하지 않고 실제 호출에서 확인한 구조와 Koready 정규화 결론만 기록한다. 시간, 요금, 좌표, 상세 경로 문자열은 기록하지 않는다.

공식 기준:

- 상세 경로 규격: https://transit.tmapmobility.com/docs/routes
- 요약 경로 규격: https://transit.tmapmobility.com/docs/routes/sub
- 이용약관과 24시간 제한: https://transit.tmapmobility.com/terms

---

## 1. 호출 구성

| 시나리오 | 확인 목적 | 결과 |
|---|---|---|
| 서울 도심 이동 | 기본 WALK/BUS 구조 | 정상 경로 |
| 서울에서 김천 장거리 이동 | SUBWAY/TRAIN, 광역 요금, 운행 flag | 정상 경로 |
| 매우 가까운 출발지/도착지 | HTTP 200 안의 provider 오류 | 경로 없음 |

Probe는 `scripts/probe_tmap_transit.py`를 사용한다.

- 기본 실행은 계획만 출력한다.
- `--execute`를 명시해야 실제 호출한다.
- 한 번 실행할 때 최대 3회로 제한한다.
- 응답은 메모리에서 구조만 요약하고 원본 파일을 생성하지 않는다.
- App Key, 원본 request parameter, 응답 좌표와 경로 값은 출력하지 않는다.

---

## 2. 정상 응답 구조

실제 정상 응답은 다음 계층이었다.

```text
metaData
  requestParameters
  plan
    itineraries[]
      pathType: integer
      totalTime: integer
      totalDistance: integer
      totalWalkTime: integer
      totalWalkDistance: integer
      transferCount: integer
      fare.regular.totalFare: integer
      fare.regular.currency: object
      legs[]
```

`requestParameters`에는 출발지와 목적지 좌표가 다시 포함되므로 장기 로그, snapshot, 증빙 번들에 저장하지 않는다.

### 2.1 itinerary

| TMAP 필드 | 확인 타입 | Koready 사용 |
|---|---|---|
| `pathType` | integer | 내부 진단/통계 전용 |
| `totalTime` | integer, 초 | 표시 시간과 당일치기 판정 |
| `totalDistance` | integer, m | 현재 화면에서는 미노출 |
| `totalWalkTime` | integer, 초 | 난이도 계산 |
| `totalWalkDistance` | integer, m | 난이도 계산 |
| `transferCount` | integer | 난이도 계산과 안내 |
| `fare.regular.totalFare` | integer | 편도 예상 요금 |
| `legs` | array | Koready `RouteSegment[]` |

분 표시값은 `ceil(seconds / 60)`로 계산한다. 다만 당일치기 경계는 표시용 분이 아니라 원본 `totalTime` 초로 판정한다.

### 2.2 leg

이번 표본에서 확인한 mode는 다음과 같다.

```text
WALK | BUS | SUBWAY | TRAIN
```

공식 규격의 나머지 mode도 parser에서 허용한다.

```text
EXPRESSBUS | AIRPLANE | FERRY
```

| 실제 필드 | 관찰 내용 | 처리 |
|---|---|---|
| `mode` | WALK/BUS/SUBWAY/TRAIN | `RouteMode`로 정규화 |
| `distance` | integer | `distanceMeters` |
| `sectionTime` | integer, 초 | `durationMinutes`로 올림 |
| `start/end` | name, lat, lon | 이름만 응답, 좌표는 장기 저장 금지 |
| `route` | 대중교통 구간에 존재 | `routeName` |
| `routeId` | 대중교통 구간에 존재 | 서버 내부 진단용 |
| `routeColor` | 대중교통 구간에 존재 | 현재 MVP 미사용 |
| `type` | mode별 노선 코드 | 내부 세부 교통수단 판정 |
| `routePayment` | TRAIN에서 선택적으로 존재 | segment fare 후보 |
| `service` | 대중교통 구간에 0 또는 1 | 출발 시각 운행 가능 여부 |
| `steps` | WALK에서 선택적 array | 현재 MVP에서는 원문 미노출 |
| `passShape` | mode에 따라 선택적 | 현재 MVP에서는 원문 미노출 |
| `passStopList` | 대중교통 구간에서 선택적 | Hori Tip 계산 후 원문 폐기 |

### 2.3 문서와 실제 응답의 차이

공식 표에는 다중 노선 필드가 `lane`으로 설명되지만 실제 표본에서는 `Lane`으로 내려왔다.

```text
실제: legs[].Lane
문서: legs[].lane
```

TMAP client DTO는 `@JsonProperty("Lane")`와 `@JsonAlias("lane")`를 함께 사용한다. 전역 대소문자 무시 설정으로 해결하지 않는다.

---

## 3. mode/type 매핑

외부 `mode`는 다음처럼 공개 DTO로 단순화한다.

| TMAP mode | Koready RouteMode |
|---|---|
| `WALK` | `WALK` |
| `BUS` | `BUS` |
| `SUBWAY` | `SUBWAY` |
| `EXPRESSBUS` | `EXPRESS_BUS` |
| `TRAIN` | `TRAIN` |
| `AIRPLANE` | `AIRPLANE` |
| `FERRY` | `FERRY` |

실제 표본과 공식 코드표를 대조한 내부 세부 타입은 다음과 같다.

| mode | type | 공식 의미 | 공개 DTO |
|---|---:|---|---|
| BUS | 1 | 일반 | BUS |
| BUS | 11 | 간선 | BUS |
| SUBWAY | 4 | 4호선 | SUBWAY |
| TRAIN | 11 | SRT | TRAIN |
| TRAIN | 15 | ITX새마을 | TRAIN |

프론트에는 세부 type 정수를 노출하지 않는다. `recommendedTransportText`, `routeName`, 운영진 Hori Tip trigger 매칭에서만 사용한다.

---

## 4. 운행 가능 여부

정상 itinerary 안에도 `service=0`인 대중교통 leg가 포함될 수 있었다. 따라서 `HTTP 200 + itineraries 존재`만으로 사용 가능한 경로라고 판단하면 안 된다.

생성 규칙:

1. 운영 호출은 한 번의 요청에서 `count=3` 후보를 받는다.
2. WALK를 제외한 모든 필수 leg가 `service=1`인 후보를 우선한다.
3. 후보가 여러 개면 `totalTime`, `transferCount`, `totalWalkDistance` 순으로 선택한다.
4. 모든 후보에 `service=0` 필수 leg가 있으면 경로를 성공으로 반환하지 않는다.
5. 이 경우 Koready는 422 `ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME`을 반환한다.

`departureAt`은 Asia/Seoul 기준 `yyyyMMddHHmm`의 TMAP `searchDttm`으로 변환한다. 값이 없으면 요청 시각을 사용한다.

---

## 5. 오류 응답

가까운 거리 표본은 HTTP 200이었지만 `metaData.plan`이 없고 최상위 `result` 객체가 존재했다.

성공 판정은 다음 조건을 모두 만족해야 한다.

```text
HTTP 2xx
AND metaData.plan.itineraries is non-empty array
AND provider result/error object is absent
```

공식 provider 오류를 Koready 오류로 변환한다.

| TMAP code | HTTP | 의미 | Koready |
|---:|---:|---|---|
| 11 | 200 | 출발지/도착지가 너무 가까움 | 422 `ROUTE_NOT_FOUND` |
| 12 | 200 | 출발지 정류장 매핑 실패 | 422 `ROUTE_NOT_FOUND` |
| 13 | 200 | 도착지 정류장 매핑 실패 | 422 `ROUTE_NOT_FOUND` |
| 14 | 200 | 대중교통 경로 없음 | 422 `ROUTE_NOT_FOUND` |
| 21/22/23/24 | 400 | 요청 형식·범위·지역·시각 오류 | 내부 요청 검증 오류로 기록, 사용자에게 422 |
| 31/32 | 500 | timeout/기타 provider 오류 | 503 `ROUTE_PROVIDER_UNAVAILABLE` |

provider 원문 message는 사용자에게 그대로 노출하지 않고 call log의 제한된 오류 코드로만 남긴다.

---

## 6. Koready Route DTO 결론

```text
RouteResponse
  routeId
  provider
  origin / destination
  fetchedAt / expiresAt
  summary
    estimatedOneWayMinutes
    transferCount
    totalWalkDistanceMeters
    difficulty / dayTripStatus
    fare
    transportModes
    horiTips
  segments[]
    source
    startName / endName
    mode / routeName
    durationMinutes / distanceMeters
    fare
    serviceAvailable
    instruction
    horiTips
  warnings[]
  detailAvailable
```

- `fare`는 `AVAILABLE_SEGMENTS_ONLY` 범위를 명시한다.
- TMAP `linestring`, stop 목록, provider route ID와 type은 공개 DTO에 포함하지 않는다.
- `RouteMode`는 안정적인 Koready enum이며 TMAP 신규 type은 내부 mapping table로 흡수한다.
- 축제 셔틀처럼 TMAP에 없는 구간만 `source=KOREADY_CURATED`로 추가한다.
- `horiTips` 본문은 TMAP 응답에서 만들지 않는다. TMAP 정규화 결과는 운영진이 저장한 `OPERATOR_CURATED` 팁의 trigger 평가에만 사용한다.

---

## 7. 보관과 로그

- App Key는 `.env.local`/배포 secret에서만 읽는다.
- TMAP 원본 응답은 파일이나 DB에 저장하지 않는다.
- 정규화된 경로는 기본 30분 cache, 절대 24시간 미만으로 만료한다. Hori Tip 조합 결과는 cache에 넣지 않고 Route 응답 시 현재 ACTIVE 팁을 다시 조회한다.
- 장기 로그에는 provider, operation, HTTP status, provider code, 성공 여부, 지연시간만 남긴다.
- 좌표, 상세 주소, 경로 시간·요금·구간, 검색 시각 원문은 장기 로그와 증빙 번들에 포함하지 않는다.

---

## 8. 아직 확인하지 않은 범위

3회 표본으로 응답 계약 구현을 시작하기에는 충분하지만 통계적 성능·가용성 검증은 아니다.

- `EXPRESSBUS`, `AIRPLANE`, `FERRY` 실제 응답
- `lang=1` 영문 경로 문구
- `count=3` 복수 후보의 정렬 안정성
- 미래 `searchDttm`의 `service` 변화
- `/transit/routes/sub` 요약 endpoint의 실효성

MVP는 상세 endpoint 한 번으로 summary와 segments를 함께 만들고, 위 항목은 Route 기능 구현 직전에 제한된 추가 probe로 확인한다.
