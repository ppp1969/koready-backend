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

- `startAfterPlaceId`: 이 내부 장소 ID보다 크면서 상세 수집이 필요하거나 30일
  갱신 기한이 지난 장소부터 처리한다. 기본값은 `0`이다.
- `maxPlaces`: 한 작업의 장소 수다. 기본값 `10`, 최댓값 `50`이다.
- `autoContinue`: 남은 장소의 다음 작업을 자동 등록한다. 기본값은 `false`다.

장소마다 외부 API를 네 번 호출하므로 처음에는 `maxPlaces=10`,
`autoContinue=false`로 DB 반영, S3 원본, API 할당량을 확인한다. 자동 연속 실행은
샘플 결과와 호출량을 확인한 뒤에만 켠다.

## 저장 및 재실행

- 네 응답의 압축 원문을 private S3에 각각 저장하고 hash와 메타데이터만 DB에 기록한다.
- KTO 기본 목록으로 새로 들어온 장소는 `show_flag=false`로 저장한다. 네 상세 API 응답과
  S3·DB 저장이 같은 트랜잭션에서 모두 완료된 뒤에만 `show_flag=true`로 전환되어 공개 장소
  목록, 검색, 추천, 상세 API에 노출된다.
- 갱신 대상이었던 기존 공개 장소는 갱신 실패 시에도 마지막 검증 데이터를 계속 노출한다.
  실패·대기 중인 신규 장소는 공개 API에 나타나지 않으며, 관리자는 batch job과
  `GET /api/v1/admin/kto/detail-coverage`로 상태를 확인한다.
- 한 장소의 네 응답을 모두 확보한 뒤 하나의 DB transaction으로 반영한다.
- 성공한 장소는 네 snapshot ID, 이미지 수, 완료 시각, 다음 갱신 시각을
  `kto_place_detail_sync_status`에 기록한다.
- 완료 후 30일 동안은 같은 장소를 다시 선택하지 않는다. 따라서 제한 배치를
  `startAfterPlaceId=0`으로 반복해도 다음 미완료 장소 구간으로 진행한다.
- 30일이 지나면 변경된 운영 정보와 이미지를 다시 확인할 수 있도록 수집 대상에
  자동으로 포함한다.
- 같은 storage key와 같은 hash의 재실행은 성공한 멱등 재실행으로 처리한다.
- 같은 storage key에 다른 hash가 들어오거나 네 응답 중 일부 snapshot만 존재하면
  불완전한 재실행으로 판단하고 중단한다.
- 앞 장소의 성공 transaction은 뒤 장소 실패 때문에 되돌리지 않는다. 실패한 장소
  직전 cursor부터 새 작업으로 재개할 수 있다.
- V26 migration은 기존 DB에서 상세 API 네 종류의 snapshot이 모두 확인되는 장소만
  완료 상태로 backfill한다. 일부 snapshot만 있는 장소는 다시 수집한다.

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

## EB 일일 호출 예산

수동 전수 실행 대신 EB에서는 하루 최대 800곳을 자동 등록할 수 있다. TourAPI 개발계정의
오퍼레이션별 일일 1,000건 한도에 여유를 남기는 기본값이며, 한 장소당 KTO 상세 API를
네 번 호출한다. 각 API 오퍼레이션은 최대 800회/일로 예상한다.

- `KTO_DETAIL_DAILY_SCHEDULE_ENABLED=true`: 자동 등록을 켠다. 기본값은 `false`다.
- `KTO_DETAIL_DAILY_PLACES=800`: 하루에 처리할 장소 수다. 최댓값은 `900`이다.
- `KTO_DETAIL_DAILY_CHUNK_PLACES=50`: 한 batch job에서 처리할 장소 수다. 최댓값은 `50`이다.
- `KTO_DETAIL_DAILY_SCHEDULE_ZONE=Asia/Seoul`: 일일 중복 판단 기준 시간대다.
- `KTO_DETAIL_DAILY_SCHEDULE_CRON=0 */30 * * * *`: 30분마다 등록 가능 여부를
  확인한다.

같은 날짜의 예약 키는 DB에서 한 번만 허용한다. 다른 KTO 배치가 실행 중이면 활성
작업 슬롯 충돌로 등록하지 않고 다음 30분 주기에 다시 확인한다. 자동 작업은
`startAfterPlaceId=0`부터 시작하되, `remainingDailyPlaces`를 작업 파라미터에 저장해
50개 단위로 다음 작업을 이어 등록한다. 완료 장소는 기존 체크포인트가 건너뛰므로 서버
재시작 또는 다음 날 재실행에도 중복 적재하지 않는다. KTO quota 오류는
`KTO_QUOTA_EXCEEDED`로 기록되고, 해당 작업은 이어 실행되지 않아 다음 날 새 일일 예산으로
미완료 장소부터 재개한다.

한 장소의 전송 오류 또는 일시적인 KTO 5xx 응답은 해당 장소 실패로만 집계하고 다음
장소를 처리한다. 성공 장소가 하나 이상인 `PARTIAL_FAILED` 청크는 시도한 장소 수만큼
일일 잔여 예산을 차감하고 다음 청크를 등록한다. 실패 장소에는 상세 체크포인트를 만들지
않으므로 다음 날 다시 대상이 된다. 세 장소가 연속으로 실패하면 제공자 장애 가능성이
높다고 보고 현재 체인을 중단한다. quota 코드 `22`와 HTTP 429는 재시도하지 않고 즉시
중단해 일일 한도를 보호한다. 운영 로그에는 내부 장소 ID, 오퍼레이션 이름과 예외 종류만
남기며 service key, 요청 URL과 원문 응답은 기록하지 않는다.

Render는 Swagger 공유 환경이므로 `KTO_MANUAL_BATCH_WORKER_ENABLED=false`와
`KTO_DETAIL_DAILY_SCHEDULE_ENABLED=false`를 함께 유지한다.

## 진행률과 사진 커버리지

`GET /api/v1/admin/kto/detail-coverage`는 외부 API를 다시 호출하지 않고 현재 DB를
한 번 집계해 다음 값을 반환한다.

- 전체 KTO 상세 대상, 완료, 미완료, 30일 갱신 기한 도래 장소 수
- 상세 수집 완료율
- KTO `detailImage2` 원본 기준 0·1·2·3·4장 이상 장소 수
- 관광사진 수상작, 관리자 이미지, KTO 이미지와 대표 이미지 fallback을 반영한
  실제 상세 화면 기준 0·1·2·3·4장 이상 장소 수
- 각 기준에서 4장 미만인 장소 수와 비율

이미지 분포의 분모는 상세 수집이 완료된 장소만 사용한다. 미완료 장소를 이미지
0장으로 합산하지 않으므로, 수집 진행률과 사진 품질 부족률을 서로 구분해서 본다.
