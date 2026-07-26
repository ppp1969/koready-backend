# KTO 장소 상세 보강 배치

## 목적

KTO 전체 장소 목록 수집은 장소 식별자, 이름, 주소, 좌표, 대표 이미지 같은 목록
정보를 저장한다. 상세 화면에 필요한 운영시간, 운영기간, 휴무일, 이용요금, 주차
정보와 추가 이미지는 별도의 상세 API에서 가져온다.

`KTO_DETAIL_ENRICHMENT`는 이미 저장된 KTO 장소를 작은 구간으로 나누어 상세
정보를 보강하는 관리자 수동 배치다. Render에서는 수집 worker를 비활성화하고,
KTO 접근 키와 private S3 권한이 있는 EB 환경에서만 실행한다.

## 장소별 호출 흐름

한 장소마다 다음 API를 순서대로 한 번씩 호출한다.

1. `detailCommon2`: 제목, 설명, 주소, 좌표, 전화, 홈페이지, 대표 이미지
2. `detailIntro2`: 관광 타입별 운영시간, 휴무일, 요금, 주차 등
3. `detailInfo2`: 반복 상세 정보와 이용요금 보조 정보
4. `detailImage2`: 원본 이미지 URL, 썸네일, 이미지 이름, 저작권 구분

각 응답은 최대 4MiB까지만 읽고, transport 오류와 KTO 일시 제한 응답은 지수
간격으로 최대 4회 재시도한다. 서비스 키와 원문은 로그에 남기지 않는다.

## 실행 파라미터

```json
{
  "jobType": "KTO_DETAIL_ENRICHMENT",
  "parameters": {
    "startAfterPlaceId": 0,
    "maxPlaces": 10,
    "autoContinue": false
  },
  "reason": "KTO 장소 상세 10건 운영 확인"
}
```

- `startAfterPlaceId`: 이 내부 장소 ID보다 큰 장소부터 처리한다. 기본값은 `0`이다.
- `maxPlaces`: 한 작업의 장소 수다. 기본값 `10`, 최댓값 `50`이다.
- `autoContinue`: 남은 장소의 다음 작업을 자동 등록한다. 기본값은 `false`다.

장소마다 외부 API를 네 번 호출하므로 처음에는 `maxPlaces=10`,
`autoContinue=false`로 DB 반영, S3 원본, API 할당량을 확인한다. 자동 연속 실행은
샘플 결과와 호출량을 확인한 뒤에만 켠다.

## 저장 및 재실행

- 네 응답의 압축 원문을 private S3에 각각 저장하고 hash와 메타데이터만 DB에 기록한다.
- 한 장소의 네 응답을 모두 확보한 뒤 하나의 DB transaction으로 반영한다.
- 같은 storage key와 같은 hash의 재실행은 성공한 멱등 재실행으로 처리한다.
- 같은 storage key에 다른 hash가 들어오거나 네 응답 중 일부 snapshot만 존재하면
  불완전한 재실행으로 판단하고 중단한다.
- 앞 장소의 성공 transaction은 뒤 장소 실패 때문에 되돌리지 않는다. 실패한 장소
  직전 cursor부터 새 작업으로 재개할 수 있다.

## 장소 상세 API 반영

`GET /api/v1/places/{placeId}`는 저장된 상세값을 다음 필드에 제공한다.

- `operatingHours`
- `operatingPeriod`
- `closedDays`
- `usageFee`
- `parkingInfo`
- `images` 최대 4장

이미지는 관광사진 수상작, 관리자 등록 이미지, KTO 상세 이미지 순으로 우선순위를
적용한다. 같은 URL은 한 번만 반환하고 네 장이 없으면 있는 만큼만 내려준다.
AI 번역과 AI 소개문 생성은 이 배치의 범위가 아니다.
