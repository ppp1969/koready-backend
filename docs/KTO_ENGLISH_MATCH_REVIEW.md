# KTO 영문 매칭 검토 운영 가이드

## 목적

KTO 영문 전수 수집은 영문 `contentId`가 국문 `contentId`와 항상 같지 않다. KoReady는
대표 이미지 경로와 좌표·콘텐츠 유형을 비교해 안전한 건만 자동 연결하고, 근거가 충돌하거나
없는 항목은 운영자가 확인하도록 보류한다.

이 API는 보류된 항목을 근거와 함께 조회하고 수동 확정 또는 거절하기 위한 관리자 기능이다.
AI 번역이나 자동 매칭 기준 완화는 이 범위에 포함하지 않는다.

## 상태

| 상태 | 뜻 | 가능한 운영 작업 |
| --- | --- | --- |
| `REVIEW_REQUIRED` | matcher 후보가 하나 이상 있지만 근거가 충돌함 | 후보 중 하나로 확정 또는 거절 |
| `UNMATCHED` | 이미지·좌표 근거 후보가 없음 | 거절 |
| `MANUAL_CONFIRMED` | 운영자가 matcher 후보 중 하나를 확정함 | 버전을 확인한 뒤 재검토 가능 |
| `REJECTED` | 운영자가 연결하지 않기로 결정함 | 버전을 확인한 뒤 재검토 가능 |

## 호출 흐름

1. `GET /api/v1/admin/kto/english-match-reviews`로 미처리 목록을 조회한다.
2. 항목을 선택하면 `GET /api/v1/admin/kto/english-match-reviews/{sourceRecordId}`를 호출한다.
3. 영문 원본의 제목·주소·이미지·좌표와 모든 국문 후보의 근거를 사람이 비교한다.
4. 확정할 때는 상세 응답 `candidates[].placeId` 중 하나만 선택한다.
5. `PUT /api/v1/admin/kto/english-match-reviews/{sourceRecordId}/decision`에
   상세 응답의 `decisionVersion`을 `expectedVersion`으로 그대로 보낸다.
6. 409이면 다른 운영자가 먼저 수정한 것이므로 상세를 다시 조회한다.

## 데이터 보호

- 후보 목록에 없는 장소로 임의 확정할 수 없다.
- 기존 영문 문구의 `translationSource`가 `MANUAL_EDITED`이면 KTO 원문으로 덮어쓰지 않는다.
- 확정된 영문에는 KTO source contentId와 source hash를 남긴다.
- 원본 영문은 공개 API 응답을 DB에 중복 저장하지 않고 비공개 S3 snapshot에서 읽는다.
- 모든 결정은 작업자, 사유, 이전/새 상태, 장소 ID, 결정 버전과 시각을 감사 이력에 추가한다.

## 권한과 오류

- 목록·상세: `ADMIN`, `OPERATOR`, `AUDITOR`
- 확정·거절: `ADMIN`, `OPERATOR`
- `409 KTO_ENGLISH_REVIEW_CONFLICT`: 화면의 버전이 오래됐거나 새 KTO 원본이 수집됨
- `422 KTO_ENGLISH_REVIEW_CANDIDATE_REQUIRED`: matcher 후보 밖의 장소를 선택함
- `503 KTO_ENGLISH_REVIEW_SOURCE_UNAVAILABLE`: S3 원본을 일시적으로 읽지 못함

Swagger의 `Admin KTO English Review` 태그에는 각 요청·응답 필드와 확정·거절 예시가 포함돼 있다.
