# Koready 관리자 및 OpenAPI 증빙 API 계약

## 0. 문서 상태

이 문서는 공모전 제출 증빙, OpenAPI 사용 내역 확인, 원천 데이터 계보 추적, 수집 배치 운영을 위한 관리자 API 기준본이다.

- Base URL: `/api/v1/admin`
- 공통 응답/오류 형식: `08_API_CONTRACT.md`를 따른다.
- 인증: 일반 access token과 동일한 JWT를 사용하되 관리자 role을 검사한다.
- 일반 사용자 앱에는 관리자 endpoint를 노출하지 않는다.
- OpenAPI 로그와 증빙 데이터는 append-only를 기본으로 한다.

## 1. 권한

```text
AdminRole = ADMIN | OPERATOR | AUDITOR
```

| 기능 | ADMIN | OPERATOR | AUDITOR |
|---|:---:|:---:|:---:|
| 현황/로그 조회 | O | O | O |
| 원천 스냅샷 조회/다운로드 | O | O | O |
| 증빙 번들 생성/다운로드 | O | O | O |
| 배치 수동 실행/재시도 | O | O | X |
| 온보딩 큐레이션 조회 | O | O | O |
| 온보딩 큐레이션 편집/발행/보관 | O | O | X |
| Hori Tip 조회 | O | O | O |
| Hori Tip 작성/수정/상태 변경 | O | O | X |
| 동기화 cursor 활성/초기화 | O | X | X |
| 관리자 감사 로그 조회 | O | X | O |

- 관리자 role이 없으면 `403 ADMIN_FORBIDDEN`을 반환한다.
- 관리자 계정은 허용된 소셜 계정에 role을 부여하는 방식으로 시작한다.
- 운영 환경에서 role 변경은 DB 직접 수정이 아니라 감사 가능한 내부 절차를 사용한다.

---

## 2. 증빙 데이터 원칙

## 2.1 호출 로그

모든 외부 API 호출은 성공/실패와 관계없이 `open_api_call_logs`에 기록한다.

- provider와 operation
- secret이 제거된 endpoint와 요청 파라미터
- 시작/종료 시각과 소요시간
- HTTP status와 외부 result code
- 성공 여부와 오류 정보
- 응답 byte 크기와 item 수
- 관련 batch/job/item
- 원천 스냅샷 존재 여부

`serviceKey`, `appKey`, access token, idToken, authorization header는 저장하거나 응답하지 않는다.

TMAP/KAKAO처럼 사용자 요청이 섞이는 provider는 좌표, 상세 주소, 검색어를 장기 호출 로그에서 제거한다. 대신 내부 `originLocationId`, `placeId`, 서비스 권역처럼 원문을 복원할 수 없는 참조값만 남긴다.

## 2.2 원천 스냅샷

KTO 성공 응답은 가공 전에 원천 응답을 immutable snapshot으로 저장한다.

- 본문은 DB JSON 컬럼이 아니라 private object storage에 `gzip`으로 저장한다.
- DB에는 storage key, 원문 SHA-256, 실제 gzip 객체 SHA-256, byte 크기, content type, 수집 시각을 저장한다.
- snapshot은 생성 후 수정할 수 없다.
- 가공 레코드는 가능한 경우 `rawSnapshotId` 또는 call log ID를 가진다.
- 다운로드는 짧은 만료시간의 signed URL로만 제공한다.

## 2.3 provider별 보관 정책

| Provider | 호출 로그 | 원천 스냅샷 | 증빙 번들 |
|---|---|---|---|
| KTO | 장기 보관 | 공모전 증빙 기간 보관 | 포함 가능 |
| TMAP | 결과 비재현 메타데이터만 장기 보관 | 24시간 미만 임시 보관 | 원천 제외, 호출 사실만 포함 |
| KAKAO | 마스킹된 호출 메타데이터 | 기본 미보관 | 호출 통계만 포함 |
| AI/번역 | 모델/버전/비용 메타데이터 | prompt/응답 원문은 별도 정책 | 기본 제외 |

TMAP 시간·요금·구간과 정규화 결과는 24시간 전에 삭제한다. KTO 원천 데이터 증빙 정책을 TMAP에 그대로 적용하지 않는다.

## 2.4 증빙 계보

```text
batch_jobs
  -> batch_job_items
  -> open_api_call_logs
  -> open_api_raw_snapshots
  -> places / localizations / images / tags
  -> evidence_bundles
```

증빙 번들은 각 파일의 SHA-256과 위 ID 연결을 manifest로 제공한다.

---

## 3. 온보딩 큐레이션 관리

관리자는 KTO에서 수집·가공된 장소 중 정확히 10개를 골라 온보딩 선호 여행지 세트를 발행한다. 프론트 화면 품질을 보장하기 위한 기능이며 사용자별 추천 배치가 아니다.

```text
CandidateSetStatus = DRAFT | PUBLISHED | ARCHIVED
```

| Method | URI | 설명 |
|---|---|---|
| GET | `/onboarding/place-candidate-sets` | 상태별 세트 목록 |
| POST | `/onboarding/place-candidate-sets` | 빈 초안 또는 기존 발행본 복제 초안 생성 |
| GET | `/onboarding/place-candidate-sets/{candidateSetId}` | 세트와 10개 후보 상세 |
| PUT | `/onboarding/place-candidate-sets/{candidateSetId}` | 초안의 장소·순서·표시 콘텐츠 전체 수정 |
| POST | `/onboarding/place-candidate-sets/{candidateSetId}/publish` | 발행 조건 검증 후 불변 버전 발행 |
| POST | `/onboarding/place-candidate-sets/{candidateSetId}/archive` | 신규 사용자 노출 중지 |

`POST /api/v1/admin/onboarding/place-candidate-sets`

```json
{
  "title": "2026 여름 온보딩 큐레이션",
  "copyFromSetId": "onb-curation-2026-06-v3"
}
```

`PUT /api/v1/admin/onboarding/place-candidate-sets/{candidateSetId}`

```json
{
  "title": "2026 여름 온보딩 큐레이션",
  "items": [
    {
      "placeId": 1001,
      "displayOrder": 1,
      "representativeImageId": null,
      "curatorMessageKo": "한국의 로컬 음식 문화를 가볍게 경험해 보세요.",
      "curatorMessageEn": "A friendly introduction to Korea's local food culture.",
      "displayTags": ["로컬음식", "축제", "체험"]
    }
  ]
}
```

발행 시 다음 조건을 모두 검사한다.

- item이 정확히 10개이며 `placeId`, `displayOrder`가 중복되지 않는다.
- 장소가 활성/표출 가능하고 한국어 제목, 주소, 좌표, 대표 이미지가 있다.
- 선택한 `representativeImageId`가 해당 장소 이미지에 속한다.
- KO 큐레이터 문구는 필수, EN 문구는 누락 시 번역 보강 대상으로 표시한다.
- 기존 `PUBLISHED` 세트는 수정하지 않고 새 세트만 현재 발행본으로 전환한다.
- 발행과 보관은 관리자 ID, 이전/이후 상태와 함께 감사 로그에 기록한다.

현재 MVP 저장소에는 별도 장소 이미지 자산 테이블이 없으므로 `representativeImageId`는 `null`로 보내고 `places.first_image_url`을 사용한다. 임의의 이미지 ID가 들어오면 소유권을 증명할 수 없으므로 `CURATION_PLACE_NOT_READY`로 발행을 막는다. 장소별 이미지 자산 관리가 추가되면 같은 필드에서 소유권 검증을 확장한다.

현재 발행본을 보관하면 current 포인터는 비워진다. 과거 발행본을 자동으로 다시 current로 되돌리지 않으며, 운영진이 새 DRAFT를 검수해 발행할 때까지 사용자 current 조회는 `404 CURATION_SET_NOT_FOUND`다.

---

## 4. Hori Tip 운영

Hori Tip은 운영진이 직접 작성하는 Route 안내다. TMAP과 AI는 팁 본문을 만들지 않고, 저장된 매칭 규칙을 평가하는 경로 정보만 제공한다.

2026-07-19 기준 아래 5개 운영 API와 MySQL 저장·상태 변경·감사 이력은 구현되었다. Buddy Route 응답에 현재 `ACTIVE` 팁을 매칭하는 4.3 단계는 Route 기능 구현 때 연결한다.

```text
HoriTipStatus = DRAFT | ACTIVE | INACTIVE | ARCHIVED
HoriTipScopeType = ALL_ROUTES | DESTINATION_PLACES
HoriTipPlacement = TOP_SUMMARY | AFTER_SEGMENT
```

| Method | URI | 설명 |
|---|---|---|
| GET | `/hori-tips` | 상태·code·목적지별 운영 팁 목록 |
| POST | `/hori-tips` | `DRAFT` 팁 생성 |
| GET | `/hori-tips/{horiTipId}` | 번역·scope·trigger 포함 상세 |
| PUT | `/hori-tips/{horiTipId}` | 전체 수정과 version 검증 |
| PUT | `/hori-tips/{horiTipId}/status` | 활성화·비활성화·보관 |

물리 삭제 API는 제공하지 않는다. 사용을 끝낸 팁은 `ARCHIVED`로 전환해 과거 노출과 감사 이력을 유지한다.

### 4.1 작성과 수정

`POST /api/v1/admin/hori-tips`

```json
{
  "code": "TIP_GIMCHEON_GUMI_STATION",
  "placement": "AFTER_SEGMENT",
  "priority": 100,
  "scope": {
    "scopeType": "DESTINATION_PLACES",
    "destinationPlaceIds": [1001]
  },
  "trigger": {
    "segmentModes": ["TRAIN"],
    "routeNameContainsAny": ["KTX", "KTX-산천", "KTX이음"],
    "segmentStartNameContainsAny": [],
    "segmentEndNameContainsAny": ["김천(구미)역"],
    "minProviderTotalTimeSeconds": null,
    "minTransferCount": null,
    "minTotalWalkDistanceMeters": null
  },
  "translations": [
    {"language": "KO", "body": "김천역과 김천(구미)역은 다른 역이에요."},
    {"language": "EN", "body": "Gimcheon Station and Gimcheon-Gumi Station are different stations."}
  ],
  "validFrom": null,
  "validUntil": null,
  "operatorNote": "김천 KTX 도착 경로 안내"
}
```

- 생성 결과는 항상 `DRAFT`다.
- `code`는 `TIP_` prefix의 대문자·숫자·underscore만 허용하고 영구 unique다.
- `code`는 생성 후 변경할 수 없다. `PUT`에서는 code를 제외한 위 편집 필드 전체와 현재 `version`을 보내며, version이 다르면 `409 HORI_TIP_NOT_EDITABLE`이다.
- `ACTIVE` 팁을 `PUT`으로 수정할 때도 활성화 조건을 모두 다시 검증하고 실패하면 기존 값을 유지한 채 422를 반환한다.
- `title`은 운영진 입력값이 아니라 사용자 노출 브랜드명 `Hori Tip`으로 고정한다.
- trigger 문자열은 일반 contains 값이며 정규식을 허용하지 않는다.

### 4.2 상태 변경

`PUT /api/v1/admin/hori-tips/{horiTipId}/status`

```json
{
  "status": "ACTIVE",
  "version": 3,
  "reason": "운영 검수 완료"
}
```

`ACTIVE` 전환 전에 다음 조건을 모두 검증한다.

- KO와 EN 본문이 모두 있고 언어가 중복되지 않는다.
- `validUntil`이 있으면 `validFrom`보다 뒤다.
- `DESTINATION_PLACES` scope는 활성 장소 ID가 1개 이상 있다.
- `ALL_ROUTES` scope도 trigger 조건이 최소 1개 있어야 한다.
- `AFTER_SEGMENT` placement는 mode, routeName, startName, endName 중 segment 조건이 최소 1개 있다.
- 참조한 `destinationPlaceIds`가 모두 존재하고 Route 탭을 제공할 수 있다.

`ARCHIVED`에서는 다른 상태로 되돌릴 수 없다. 상태 변경 actor, 사유, 이전·이후 값과 version을 감사 로그에 남긴다.

### 4.3 Route 조회 시점 조합

```text
정규화 Route 준비
  -> status=ACTIVE + 노출 기간 + 목적지 scope로 DB 후보 조회
  -> summary/segment trigger 평가
  -> 사용자 언어의 운영진 본문 선택
  -> TOP_SUMMARY 또는 AFTER_SEGMENT 배열에 조합
  -> 노출 template ID 기록
```

- Hori Tip 조합 결과는 30분 Route 캐시에 저장하지 않는다.
- `POST /routes`와 `GET /routes/{routeId}` 모두 응답 직전에 현재 팁을 다시 평가한다.
- `AFTER_SEGMENT`는 처음 일치한 segment에 한 번만 붙이고, 같은 template은 route 전체에서 중복 노출하지 않는다.
- 응답 정렬은 `priority DESC, code ASC`다.
- 운영진이 비활성화하면 이미 발급된 `routeId`의 다음 조회부터 즉시 제외된다.

---

## 5. OpenAPI 현황

현재 5.1~6.2의 조회 API는 구현되어 있습니다. `ADMIN`, `OPERATOR`, `AUDITOR`가
조회할 수 있고 수정·삭제 기능은 제공하지 않습니다. 기간을 생략한 요약 조회는
현재 시각 기준 최근 30일을 사용합니다. 목록 cursor는 모든 검색 조건과 묶인 불투명
문자열이므로 프론트에서 해석하거나 필터를 바꾼 요청에 재사용하지 않습니다.

## 5.1 요약 대시보드

`GET /api/v1/admin/open-api/summary`

Query:

```text
from=2026-07-01T00:00:00+09:00
to=2026-07-31T23:59:59+09:00
provider=KTO
```

```json
{
  "period": {
    "from": "2026-07-01T00:00:00+09:00",
    "to": "2026-07-31T23:59:59+09:00"
  },
  "totalCalls": 3240,
  "successCalls": 3198,
  "failureCalls": 42,
  "successRate": 98.7,
  "rawSnapshotCount": 3198,
  "providers": [
    {
      "provider": "KTO",
      "calls": 3200,
      "success": 3170,
      "failure": 30,
      "lastSuccessAt": "2026-07-13T09:12:24+09:00"
    }
  ],
  "recentFailures": []
}
```

## 5.2 호출 로그 목록

`GET /api/v1/admin/open-api/calls`

Query:

```text
provider=KTO|TMAP|KAKAO|AI
apiName=KOR_TOUR
operation=areaBasedList2
success=true|false
httpStatus=200
from=<ISO-8601>
to=<ISO-8601>
relatedJobId=3001
hasRawSnapshot=true|false
cursor=<opaque>
size=1..100
```

```json
{
  "items": [
    {
      "callLogId": 70001,
      "provider": "KTO",
      "apiName": "KOR_TOUR",
      "operation": "areaBasedList2",
      "requestStartedAt": "2026-07-13T09:12:23+09:00",
      "responseReceivedAt": "2026-07-13T09:12:24+09:00",
      "durationMs": 932,
      "success": true,
      "httpStatus": 200,
      "externalResultCode": "0000",
      "itemCount": 100,
      "responseBytes": 281442,
      "rawSnapshotStatus": "AVAILABLE",
      "relatedJobId": 3001
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

## 5.3 호출 로그 상세

`GET /api/v1/admin/open-api/calls/{callLogId}`

```json
{
  "callLogId": 70001,
  "provider": "KTO",
  "apiName": "KOR_TOUR",
  "operation": "areaBasedList2",
  "endpoint": "https://apis.data.go.kr/B551011/KorService2/areaBasedList2",
  "requestStartedAt": "2026-07-13T09:12:23+09:00",
  "responseReceivedAt": "2026-07-13T09:12:24+09:00",
  "durationMs": 932,
  "success": true,
  "httpStatus": 200,
  "requestParamsMasked": {
    "serviceKey": "***",
    "pageNo": "1",
    "numOfRows": "100",
    "_type": "json"
  },
  "responseSummary": {
    "resultCode": "0000",
    "resultMessage": "OK",
    "totalCount": 421,
    "itemCount": 100
  },
  "error": null,
  "relatedJob": {
    "jobId": 3001,
    "jobType": "KTO_DAILY_SYNC"
  },
  "rawSnapshot": {
    "snapshotId": 80001,
    "status": "AVAILABLE",
    "rawContentSha256": "9c56...",
    "storedObjectSha256": "42ab...",
    "byteSize": 281442,
    "downloadable": true
  }
}
```

---

## 6. 원천 스냅샷

## 6.1 목록

`GET /api/v1/admin/open-api/snapshots`

Query:

```text
provider=KTO
operation=areaBasedList2
retentionClass=COMPETITION_EVIDENCE
from=<ISO-8601>
to=<ISO-8601>
cursor=<opaque>
size=1..100
```

## 6.2 상세 메타데이터

`GET /api/v1/admin/open-api/snapshots/{snapshotId}`

```json
{
  "snapshotId": 80001,
  "callLogId": 70001,
  "provider": "KTO",
  "apiName": "KOR_TOUR",
  "operation": "areaBasedList2",
  "storageKey": "kto/KOR/searchFestival2/example.json.gz",
  "storageFormat": "JSON_GZIP",
  "contentType": "application/json",
  "rawContentSha256": "9c56...",
  "storedObjectSha256": "42ab...",
  "byteSize": 281442,
  "compressedByteSize": 42123,
  "itemCount": 100,
  "capturedAt": "2026-07-13T09:12:24+09:00",
  "retentionClass": "COMPETITION_EVIDENCE",
  "retentionUntil": null,
  "immutable": true,
  "downloadable": true
}
```

`storageKey`는 private 저장소 안의 객체 위치이며 공개 URL이 아닙니다. API는 원천
본문을 반환하지 않고, `storageFormat`은 `JSON_GZIP` 또는 `XML_GZIP`, 보관 등급은
`COMPETITION_EVIDENCE`, `DEBUG_TEMPORARY`, `PROVIDER_RESTRICTED` 중 하나입니다.

## 6.3 다운로드 URL

아래 API는 구현되어 있습니다. `KTO_SNAPSHOT_STORAGE=s3`이고 snapshot이 만료되지
않았으며 provider 보관 정책이 허용할 때 목록·상세의 `downloadable`이 `true`입니다.
local/test 기본값에서는 `false`이므로 프론트는 다운로드 버튼을 비활성화합니다.

`POST /api/v1/admin/open-api/snapshots/{snapshotId}/download-url`

```json
{
  "downloadUrl": "https://private-storage/...signed...",
  "expiresAt": "2026-07-13T15:05:00+09:00",
  "fileName": "koready-kto-snapshot-11.json.gz",
  "rawContentSha256": "9c56...",
  "storedObjectSha256": "42ab..."
}
```

- signed URL은 기본 5분 후 만료하고 설정 범위도 1~15분으로 제한한다.
- 프론트는 URL을 받으면 즉시 GET하고 앱 DB, 분석 이벤트, 오류 로그에 저장하지 않는다.
- 발급 행위는 `admin_audit_logs`에 기록하되 URL·서명·AWS 자격증명은 기록하지 않는다.
- 감사 로그에는 실행자, snapshot ID, provider, operation, expiresAt과 두 SHA-256만 남긴다.
- URL만 만료된 경우 같은 API를 다시 호출한다.
- snapshot 보관기간이 끝났으면 `410 RAW_SNAPSHOT_EXPIRED`로 재발급을 거절한다.
- provider 보관 제한은 `422 PROVIDER_RETENTION_RESTRICTED`, S3 비활성 또는 서명 실패는
  `503 RAW_SNAPSHOT_DOWNLOAD_UNAVAILABLE`이다.
- 일반 API 응답으로 원천 JSON 전체를 직접 반환하지 않는다.

---

## 7. 배치와 동기화

## 7.1 배치 목록과 상세

아래 3개 GET API는 구현되어 있으며 `ADMIN`, `OPERATOR`, `AUDITOR`가 조회할 수
있습니다. DB 원문 message와 item 오류는 그대로 반환하지 않고 상태 기반 안전 문구로
바꿉니다. parameters에서도 key·token·password를 마스킹하고 검색어·주소·좌표를
제거합니다.

| Method | URI | 설명 |
|---|---|---|
| GET | `/batch-jobs` | 상태/유형/기간별 배치 목록 |
| GET | `/batch-jobs/{jobId}` | 배치 요약과 작업 통계 |
| GET | `/batch-jobs/{jobId}/items` | 실패/성공 item 목록 |

Query:

```text
jobType=KTO_DAILY_SYNC
status=PENDING|RUNNING|COMPLETED|FAILED|PARTIAL_FAILED
triggerSource=SCHEDULED|ADMIN_MANUAL|RETRY
from=<ISO-8601>
to=<ISO-8601>
cursor=<opaque>
size=1..100
```

```text
jobType =
  KTO_DAILY_SYNC | KTO_DETAIL_ENRICHMENT | KTO_EN_SYNC |
  AI_TRANSLATION | IMAGE_ENRICHMENT
```

item 목록은 DB에 실제 존재하는 아래 필드만 사용합니다.

```text
status=PENDING|RUNNING|COMPLETED|FAILED
targetType=API_PAGE|PLACE|IMAGE|TRANSLATION
itemId, targetType, targetId, status, errorMessage, createdAt, updatedAt
```

`attemptCount`, `errorCode`, `relatedCallLogId`, item별 `finishedAt`은 현재 DB에 없으므로
응답에 포함하지 않습니다.

## 7.2 수동 실행 (후속 범위)

아래 POST API는 현재 Swagger에서 `PLANNED`이며 호출할 수 없습니다.

`POST /api/v1/admin/batch-jobs`

```json
{
  "jobType": "KTO_DAILY_SYNC",
  "parameters": {
    "operations": ["areaBasedSyncList2"],
    "pageSize": 500
  },
  "reason": "공모전 제출 전 최종 동기화"
}
```

- `OPERATOR` 이상만 실행할 수 있다.
- 동일한 배타 job이 실행 중이면 `409 BATCH_ALREADY_RUNNING`을 반환한다.
- `reason`은 필수이며 감사 로그에 남긴다.
- API는 `202 Accepted`와 생성된 `jobId`를 반환한다.

## 7.3 실패 item 재시도 (후속 범위)

아래 POST API도 현재 `PLANNED`입니다.

`POST /api/v1/admin/batch-jobs/{jobId}/retry`

```json
{
  "scope": "FAILED_ITEMS",
  "reason": "일시적인 KTO timeout 재시도"
}
```

원본 job을 변경하지 않고 새 retry job을 만든다.

## 7.4 동기화 cursor

조회 API는 구현되어 있으며 `ADMIN`, `OPERATOR`, `AUDITOR`가 사용할 수 있습니다.
전체 행을 provider → apiName → operation → cursorType 순서로 반환합니다. cursor 수는
operation별 소수의 관리 행이므로 별도 pagination은 사용하지 않습니다.

| Method | URI | 설명 |
|---|---|---|
| GET | `/open-api/sync-cursors` | operation별 동기화 위치와 성공·실패 현황 조회 (구현) |
| PUT | `/open-api/sync-cursors/{cursorId}/enabled` | ADMIN 전용 동기화 활성/비활성 (구현) |
| POST | `/open-api/sync-cursors/{cursorId}/reset` | ADMIN 전용 지정 cursor 초기화 (구현) |

응답은 실제 `tour_api_sync_cursors` 열만 사용합니다.

```text
cursorId, provider, apiName, operation, cursorType, cursorValue,
lastSuccessAt, lastFailureAt, failureCount, enabled, createdAt, updatedAt

cursorType = MODIFIED_TIME | PAGE | DATE_RANGE | MANUAL
```

`cursorValue`, `lastSuccessAt`, `lastFailureAt`은 아직 값이 없으면 `null`입니다. DB에 없는
`OPAQUE`, `lastErrorCode`, `lastErrorMessage`, `nextRunAt`은 반환하지 않습니다. cursor 값은
서버 내부 진행 위치이므로 프론트가 파싱하거나 수정하지 않고 그대로 표시합니다.

두 변경 API는 `ADMIN` 전용입니다. `OPERATOR`, `AUDITOR`는 조회만 가능하며 변경 시
`403 ADMIN_FORBIDDEN`을 받습니다. 활성 변경은 `enabled`, 초기화는 공백 제거 후 1~500자의
`cursorValue`를 받고 두 요청 모두 공백 제거 후 1~500자의 `reason`이 필수입니다.

cursor 초기화 요청은 다음과 같습니다.

```json
{
  "cursorValue": "20260701000000",
  "reason": "누락 기간 재수집"
}
```

변경 대상 행은 잠근 뒤 update와 감사 로그 insert를 같은 transaction에서 수행합니다.
활성 변경은 cursorValue·성공/실패 시각·failureCount를 유지하고, 초기화는 enabled와 같은
운영 이력을 유지한 채 cursorValue만 바꿉니다. 같은 값을 다시 제출해도 성공하며 실행자,
사유, 변경 전후 JSON이 새 감사 로그로 남습니다. 이 API 자체는 배치나 외부 호출을 시작하지
않습니다. reason에는 키·토큰·개인 위치정보를 입력하지 않습니다. 없는 cursorId는
`404 SYNC_CURSOR_NOT_FOUND`입니다.

---

## 8. 공모전 증빙 번들

## 8.1 생성

`POST /api/v1/admin/evidence-bundles`

```json
{
  "name": "2026 공모전 OpenAPI 사용 증빙",
  "from": "2026-07-01T00:00:00+09:00",
  "to": "2026-07-31T23:59:59+09:00",
  "providers": ["KTO"],
  "operations": [],
  "includeRawSnapshots": true,
  "rawSampleLimitPerOperation": 3
}
```

`202 Accepted`:

```json
{
  "bundleId": "evidence_01J2ABCDEF",
  "status": "QUEUED"
}
```

## 8.2 번들 구성

```text
manifest.json
open_api_calls.csv
batch_jobs.csv
sync_cursors.json
data_quality_summary.json
raw_snapshots/<provider>/<operation>/<snapshotId>.json.gz
SHA256SUMS
```

`SHA256SUMS`는 ZIP에 실제 포함된 파일 byte를 기준으로 계산한다. `manifest.json`에는 원천 응답의 `rawContentSha256`과 저장된 gzip 파일의 `storedObjectSha256`을 함께 기록한다.

`manifest.json`은 다음을 포함한다.

- 생성 조건과 기간
- provider/operation별 호출·성공·실패 수
- 포함 snapshot ID와 call log ID
- snapshot별 SHA-256/크기/수집 시각
- 제외된 데이터와 사유
- 생성자와 생성 시각

증빙 번들에는 user ID, 이메일, 위치 좌표/상세 주소, 쪽지, social token 등 사용자 개인정보를 포함하지 않는다.

TMAP 원천 응답은 장기 증빙 번들에서 제외하고 호출 횟수/상태 메타데이터만 포함한다.

## 8.3 조회와 다운로드

| Method | URI | 설명 |
|---|---|---|
| GET | `/evidence-bundles` | 생성 이력 |
| GET | `/evidence-bundles/{bundleId}` | 상태/manifest 요약 |
| POST | `/evidence-bundles/{bundleId}/download-url` | 완성 번들 signed URL |

```json
{
  "bundleId": "evidence_01J2ABCDEF",
  "status": "COMPLETED",
  "createdAt": "2026-07-13T15:00:00+09:00",
  "finishedAt": "2026-07-13T15:01:14+09:00",
  "fileName": "koready-openapi-evidence-202607.zip",
  "sha256": "72ab...",
  "byteSize": 18421422,
  "callCount": 3200,
  "rawSnapshotCount": 45,
  "excluded": [
    {
      "provider": "TMAP",
      "reason": "PROVIDER_RETENTION_RESTRICTED"
    }
  ]
}
```

---

## 9. 데이터 품질과 감사

## 9.1 데이터 품질 요약

`GET /api/v1/admin/data-quality/summary`

- 구현 상태: 구현 완료
- 허용 권한: `ADMIN`, `OPERATOR`, `AUDITOR`
- Query parameter: 없음
- 호출 시점: 관리자 품질 화면 진입 또는 사용자가 새로고침을 누른 시점
- 자동 반복 호출: 하지 않음. 이 응답은 요청 시점의 DB 집계이며 외부 API를 새로 호출하지 않는다.

```json
{
  "success": true,
  "code": "DATA_QUALITY_SUMMARY_OK",
  "message": "OK",
  "data": {
    "generatedAt": "2026-07-13T06:00:00Z",
    "places": {
      "total": 42000,
      "active": 40120,
      "missingImage": 3100,
      "missingEnglish": 8200,
      "missingCoordinates": 12,
      "missingAddress": 43,
      "curationReady": 31650
    },
    "localization": {
      "ktoEnglish": 20106,
      "aiTranslated": 13694,
      "manualEdited": 120
    },
    "lastSuccessfulSyncAt": "2026-07-13T00:12:24Z"
  },
  "traceId": "01K0EXAMPLETRACEID"
}
```

관광지 숫자는 다음 기준으로 계산한다.

| 필드 | 쉬운 의미 | 정확한 포함 기준 |
|---|---|---|
| `total` | DB에 저장된 전체 관광지 | 활성·비활성 여부와 관계없이 `places` 전체 |
| `active` | 현재 서비스에 보여 줄 수 있는 관광지 | `active=true`와 `showFlag=true`를 모두 만족 |
| `missingImage` | 대표 이미지 보완 필요 | `active` 관광지 중 대표 이미지 URL이 null 또는 빈 값 |
| `missingEnglish` | 영어 정보 보완 필요 | `active` 관광지 중 EN 현지화 행이 없음 |
| `missingCoordinates` | 지도 좌표 보완 필요 | `active` 관광지 중 위도 또는 경도 하나 이상이 없음 |
| `missingAddress` | 주소 보완 필요 | `active` 관광지 중 도로명 주소, 기본 주소, KO 현지화 주소가 모두 없음 |
| `curationReady` | 추천 후보로 사용할 기본 자료가 준비됨 | `active` 조건과 KO 제목, 주소, 위도·경도, 대표 이미지, 서비스 권역을 모두 만족 |

`missingImage`, `missingEnglish`, `missingCoordinates`, `missingAddress`는 서로 겹칠 수 있다. 예를 들어 한 관광지가 이미지와 영어 정보를 모두 잃었다면 두 숫자에 각각 1씩 포함된다. 따라서 누락 숫자를 합계로 보거나 `active`에서 빼서 `curationReady`를 계산하면 안 된다.

현지화 숫자는 관광지가 아니라 `place_localizations` 행을 센다.

| 필드 | `translationSource` 값 | 의미 |
|---|---|---|
| `ktoEnglish` | `KTO_EN` | 한국관광공사 영어 원문을 사용한 현지화 행 |
| `aiTranslated` | `AI_TRANSLATED` | AI 번역 결과를 사용한 현지화 행 |
| `manualEdited` | `MANUAL_EDITED` | 운영진이 직접 편집한 현지화 행 |

- `generatedAt`은 서버가 집계 응답을 만든 시각이다.
- `lastSuccessfulSyncAt`은 모든 동기화 cursor 중 가장 최근 성공 시각이다. 성공 이력이 없으면 `null`이다.
- 데이터가 전혀 없으면 모든 숫자는 `0`이고 `lastSuccessfulSyncAt`만 `null`이다.
- 현재 응답은 집계 숫자만 제공한다. 문제가 있는 개별 관광지 목록이나 주소·좌표 원문을 내려 주는 기능은 후속 범위다.

## 9.2 관리자 감사 로그

`GET /api/v1/admin/audit-logs`

Query:

```text
actorUserId=1
action=RAW_SNAPSHOT_DOWNLOAD_URL_ISSUED
from=<ISO-8601>
to=<ISO-8601>
cursor=<opaque>
size=1..100
```

다음 행위는 반드시 감사 로그에 남긴다.

- 원천 snapshot 다운로드 URL 발급
- 증빙 번들 생성/다운로드
- 배치 수동 실행/재시도
- sync cursor 활성 변경/초기화
- Hori Tip 생성/수정/상태 변경
- role 변경

---

## 10. Endpoint 요약

| Method | URI |
|---|---|
| GET | `/open-api/summary` |
| GET | `/onboarding/place-candidate-sets` |
| POST | `/onboarding/place-candidate-sets` |
| GET | `/onboarding/place-candidate-sets/{candidateSetId}` |
| PUT | `/onboarding/place-candidate-sets/{candidateSetId}` |
| POST | `/onboarding/place-candidate-sets/{candidateSetId}/publish` |
| POST | `/onboarding/place-candidate-sets/{candidateSetId}/archive` |
| GET | `/hori-tips` |
| POST | `/hori-tips` |
| GET | `/hori-tips/{horiTipId}` |
| PUT | `/hori-tips/{horiTipId}` |
| PUT | `/hori-tips/{horiTipId}/status` |
| GET | `/open-api/calls` |
| GET | `/open-api/calls/{callLogId}` |
| GET | `/open-api/snapshots` |
| GET | `/open-api/snapshots/{snapshotId}` |
| POST | `/open-api/snapshots/{snapshotId}/download-url` |
| GET | `/batch-jobs` |
| POST | `/batch-jobs` |
| GET | `/batch-jobs/{jobId}` |
| GET | `/batch-jobs/{jobId}/items` |
| POST | `/batch-jobs/{jobId}/retry` |
| GET | `/open-api/sync-cursors` |
| PUT | `/open-api/sync-cursors/{cursorId}/enabled` |
| POST | `/open-api/sync-cursors/{cursorId}/reset` |
| GET | `/evidence-bundles` |
| POST | `/evidence-bundles` |
| GET | `/evidence-bundles/{bundleId}` |
| POST | `/evidence-bundles/{bundleId}/download-url` |
| GET | `/data-quality/summary` |
| GET | `/audit-logs` |

## 11. 오류 코드

| 코드 | 의미 |
|---|---|
| `ADMIN_FORBIDDEN` | 관리자 권한 없음 |
| `RAW_SNAPSHOT_NOT_FOUND` | snapshot 없음 |
| `RAW_SNAPSHOT_EXPIRED` | provider 보관기간 만료 |
| `PROVIDER_RETENTION_RESTRICTED` | provider 정책상 다운로드/증빙 불가 |
| `RAW_SNAPSHOT_DOWNLOAD_UNAVAILABLE` | S3 다운로드 비활성 또는 일시적인 URL 서명 실패 |
| `EVIDENCE_BUNDLE_NOT_READY` | 번들 생성 중 |
| `EVIDENCE_BUNDLE_FAILED` | 번들 생성 실패 |
| `BATCH_ALREADY_RUNNING` | 동일 배타 batch 실행 중 |
| `BATCH_NOT_RETRYABLE` | 재시도 불가 상태 |
| `CURATION_SET_NOT_EDITABLE` | 발행/보관 세트를 수정하려고 함 |
| `CURATION_SET_REQUIRES_TEN_ITEMS` | 후보가 정확히 10개가 아님 |
| `CURATION_PLACE_NOT_READY` | 비표출 또는 필수 카드 데이터 누락 장소 포함 |
| `CURATION_ITEM_DUPLICATED` | 장소 또는 표시 순서 중복 |
| `HORI_TIP_CODE_DUPLICATED` | 이미 사용 중인 Hori Tip code |
| `HORI_TIP_RULE_INVALID` | scope·trigger·노출 기간 규칙 오류 |
| `HORI_TIP_NOT_EDITABLE` | 보관 상태 또는 version 충돌 |
| `HORI_TIP_ACTIVATION_INVALID` | 번역·scope·trigger·기간 활성화 조건 미충족 |
| `SYNC_CURSOR_RESET_FORBIDDEN` | cursor 초기화 권한 없음 |
| `SYNC_CURSOR_NOT_FOUND` | 변경할 동기화 cursor 없음 |

## 12. 구현 수락 기준

- 일반 사용자 token으로 `/admin/**` 호출 시 403을 반환한다.
- 성공한 KTO 호출은 call log와 raw snapshot이 연결된다.
- 실패한 KTO 호출은 call log가 남고 snapshot은 없을 수 있다.
- 모든 request secret은 저장 전 마스킹된다.
- TMAP/KAKAO 호출의 좌표, 상세 주소, 사용자 검색어는 장기 로그와 증빙에서 제거된다.
- snapshot의 `rawContentSha256`이 실제 압축 해제 원문과 일치한다.
- snapshot의 `storedObjectSha256`이 private object storage의 gzip 객체와 일치한다.
- snapshot은 관리자 API로 수정/삭제할 수 없다.
- TMAP 원천/정규화 데이터는 24시간 전에 만료된다.
- TMAP 원천은 장기 증빙 번들에서 제외된다.
- 후보 9개 또는 11개 세트는 발행할 수 없다.
- 발행된 큐레이션 버전은 수정할 수 없고 새 버전으로만 교체된다.
- 일반 사용자 API가 조회한 과거 발행 버전은 새 버전 발행 후에도 유효한 온보딩 제출을 검증할 수 있다.
- DRAFT/INACTIVE/ARCHIVED Hori Tip은 사용자 Route 응답에 포함되지 않는다.
- Hori Tip 본문은 운영진 번역에서만 오며 TMAP 또는 AI 응답으로 생성하지 않는다.
- Hori Tip code는 생성 후 수정하거나 과거 code를 새 template에 재사용할 수 없다.
- ACTIVE 팁 수정은 KO/EN·scope·trigger·기간 검증에 실패하면 원자적으로 취소된다.
- Hori Tip 비활성화 후 기존 `routeId`를 재조회하면 해당 팁이 제외된다.
- Hori Tip 생성·수정·상태 변경이 actor와 이전·이후 값으로 감사 로그에 남는다.
- 증빙 ZIP의 `SHA256SUMS`가 포함 파일과 일치한다.
- snapshot/증빙 다운로드 URL 발급이 감사 로그에 남는다.
- 배치 재시도는 원본 job을 수정하지 않고 새 job을 생성한다.
- cursor 초기화는 ADMIN과 사유 입력 없이는 실행되지 않는다.
- 증빙 ZIP에는 사용자 개인정보가 포함되지 않는다.
