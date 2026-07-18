# KTO 배치 운영 기준

## 목적

Render staging의 약 500MB 메모리 안에서 한국관광공사 데이터를 안정적으로
수집하기 위한 실행 기준이다. 전체 관광정보를 한 응답이나 한 Java 목록에 담지
않는다.

## 메모리 예산

| 항목 | 시작 기준 |
|---|---:|
| Render 컨테이너 메모리 | 약 500MB |
| Java 시작 힙 | 128MB |
| Java 최대 힙 | 256MB |
| Metaspace 상한 | 128MB |
| DB 최대 연결 | 3개 |
| DB 최소 유휴 연결 | 1개 |

힙 외에도 Metaspace, thread stack, HTTP buffer, DB driver와 JVM 자체 메모리가
필요하다. 따라서 컨테이너 메모리의 75%를 힙으로 사용하던 설정은 사용하지 않는다.
메모리 부족이 발생하면 프로세스를 불완전한 상태로 유지하지 않고 즉시 종료해
Render가 다시 시작하게 한다.

## 처리 단위

| 설정 | 기본값 | 허용 범위 |
|---|---:|---:|
| KTO API page | 200건 | 1~500건 |
| HTTP 응답 본문 | 4MiB | 1byte~16MiB |
| 연결 제한 시간 | 3초 | 0초 초과 |
| 응답 읽기 제한 시간 | 10초 | 0초 초과 |
| DB 반영 단위 | 50건 | 1~page 크기 |
| 동시 batch | 1개 | 1개 |

현재 `areaBasedSyncList2`와 `searchFestival2` 국문 API client는 한 번에 한
page만 요청한다. 응답의
`Content-Length`가 4MiB를 넘으면 본문을 읽기 전에 거절하고, 길이를 알 수 없는
응답도 4MiB까지만 축적한 뒤 다음 1byte가 들어오는 시점에 중단한다. 정상 응답은
원문 byte 수와 SHA-256, page 정보, 최대 200개의 정규화된 item만 반환한다. 빈
문자열은 처리용 모델에서 `null`로 바꾼다. 축제 원문 byte는 gzip snapshot을
저장하는 동안만 유지하고 page 처리 후 참조를 해제한다.

자동 scheduler나 관리자 API는 없다. 따라서 Render가 배포되더라도 KTO 호출량이
자동으로 발생하지 않는다. 축제 수집은 개발자 PC의
`scripts/import-kto-festivals.ps1`에서 `local` 또는 `staging`을 명시했을 때만
실행한다. 한 번에 최대 20page까지 허용하지만 기본값은 1page다.

DB 저장 adapter는 private object storage 업로드가 끝난 snapshot 메타데이터만
입력받는다. 실제 원문 JSON이나 service key는 DB adapter로 전달하지 않는다. 한
page는 다음 순서로 하나의 transaction에서 처리한다.

1. 마스킹된 성공 호출 로그와 immutable snapshot 메타데이터를 저장한다.
2. KTO `contentid`로 장소를 upsert하고 국문명과 주소를 반영한다.
3. snapshot별 원천 record와 `CONTENT_ID` 자동 매칭 계보를 저장한다.
4. 모든 단계가 성공한 뒤 page cursor를 단조 증가시킨다.

같은 snapshot storage key와 같은 hash를 다시 저장하면 기존 결과를 반환한다. 같은
key에 다른 hash가 들어오면 immutable 증빙 충돌로 거절한다. `showflag=0`은 장소를
삭제하지 않고 비활성화하며, 알 수 없는 지역코드는 원문 code를 유지하고 7권역만
`null`로 둔다. item source hash는 아직 알지 못하는 신규 필드까지 포함한 원천 item
JSON으로 계산한다.

축제 adapter는 저장소 밖의 `$HOME/.koready/kto-snapshots`에 원문 gzip을 먼저
기록하고, 압축 전·후 SHA-256과 크기를 검증한 metadata만 DB adapter에 전달한다.
같은 파일이 이미 있으면 압축을 풀어 원문 hash를 다시 확인한다. 파일이 바뀌었으면
immutable snapshot 충돌로 중단한다. AWS 전환 전까지 이 로컬 저장소가 임시 private
object storage 역할을 하며, Render에서는 수집 명령을 실행하지 않는다.

`searchFestival2` 저장 시 KTO `contentid`를 축제 series key로 사용하고,
`contentid + 행사 연도`를 개최 회차의 고유 기준으로 사용한다. `visible_from`은
시작일 6개월 전으로 계산한다. KTO의 2026 법정동 코드 `12`와 시군구가 결합된
`36110` 같은 값도 7개 서비스 권역으로 변환한다. 수집 직후 장소는
`active=false`이므로 운영진 검토 없이 사용자 API에 공개되지 않는다.

한 page의 처리 순서는 다음과 같다.

1. KTO page 하나를 요청한다.
2. 원본은 메모리에 장기 보관하지 않고 snapshot 저장 경계로 전달한다.
3. item을 최대 50건씩 DB에 반영한다.
4. page의 모든 item과 원본 보관이 성공한 뒤에만 sync cursor를 다음 page로 옮긴다.
5. page 참조를 해제한 뒤 다음 page를 요청한다.

실패한 page는 완료 처리하지 않는다. 이전 page의 성공 결과와 cursor는 유지하고,
재실행 시 실패한 page부터 이어간다. 전체 약 6.8만 건을 한 transaction이나 한
컬렉션으로 다루지 않는다.

## 금지 사항

- 전체 건수를 요청하는 매우 큰 numOfRows
- 여러 KTO page 병렬 호출
- 전체 응답을 static collection 또는 application cache에 보관
- KTO 원본 JSON을 DB의 큰 JSON 컬럼에 직접 저장
- 실제 API key, Authorization 값 또는 원본 응답 본문을 로그에 출력
- 한 page 실패 때문에 이전 성공 page의 transaction을 되돌리는 방식

## 관찰 항목

배치 실행 계층이 추가되면 page마다 다음 값을 기록한다.

- 응답 byte 수와 item 수
- 처리 시간
- 성공·실패 수
- 처리 전후 heap 사용량
- GC 횟수와 일시정지 시간
- 현재 page와 마지막 성공 cursor

500MB 제한 스모크 테스트에서는 readiness가 UP인지, 컨테이너가 OOM으로
재시작하지 않는지, RSS가 제한을 넘지 않는지 확인한다. 실제 표본에서 여유가
부족하면 동시성은 유지하고 page 크기를 먼저 100건으로 낮춘다.

## 확인된 기준선

2026-07-17 기준으로 현재 이미지를 500MB 제한에서 staging profile로 실행한 결과는
다음과 같다. 이 값은 KTO 배치를 실행하지 않은 서버 기동 기준선이다.

- readiness: `UP`
- OOM 종료: 없음
- 3회 RSS 표본: 329.2~329.7MiB
- KTO 국문 page 1 실호출: 200건, 139,804byte, 771ms
- 당시 제공기관 전체 건수: 68,524건

실호출 응답은 4MiB 상한의 약 3.3%였다. 이후 DB 변환과 snapshot 업로드가 붙으면
같은 500MB 조건에서 다시 RSS를 측정하고, 400MiB를 지속해서 넘으면 page 크기를
100건으로 낮춘다.

같은 날 DB 저장 adapter와 사용하지 않는 JPA/Hibernate ORM을 함께 포함한 이미지를
측정한 결과 readiness와 OOM 상태는 정상이었고, 5회 cgroup 사용량은
377.2~377.7MiB였다. 이 상태의 Render Free 배포에서는 Hibernate 초기화 뒤
readiness가 장시간 열리지 않아 배포를 취소했다.

프로덕션 코드가 `JdbcTemplate`만 사용하는 것을 확인한 뒤 JPA/Hibernate ORM
runtime을 제거하고 다시 측정한 최신 기준선은 다음과 같다.

- Spring Boot 기동 로그: 8.122초
- readiness 도달: 컨테이너 시작 후 10.3초
- OOM 종료: 없음
- 5회 cgroup 사용량: 247.1~247.5MiB
- cgroup peak: 약 250.6MiB
- cgroup file cache: 약 0.1MiB
- readiness와 Swagger HTTP 응답: 모두 200
- Aiven 연결, Flyway V1 검증과 schema 최신 상태 확인

최신 기준선은 JPA 제거 전보다 cgroup 사용량이 약 130MiB 낮고 512MB 제한에서
약 264MiB의 여유가 있다. object storage 업로드까지 연결한 200건 end-to-end
측정에서 400MiB를 지속해서 넘으면 page 크기를 100건으로 낮춘다.

## 축제 수집 검증 기록

2026-07-18 로컬 MySQL과 실제 `searchFestival2` 응답으로 다음을 확인했다.

- 조회 기준일: `eventStartDate=20260701`
- 제공기관 전체 건수: 203건
- page 1: 200건, page 2: 3건
- 저장 결과: 고유 `contentid` 203개, 개최 회차 203개
- 잘못된 날짜: 0건
- 서비스 권역 미매핑: 0건
- 자동 공개된 장소: 0건
- page 2 재실행: `replayedPages=1`, 중복 행 없음
- 월별 API 임시 공개 스모크: 종료 축제가 `ENDED` 상태와 2026 개최 기간으로 조회됨
- JVM 제한: heap 128~256MB, Metaspace 128MB
- non-web Windows 로컬 수집 프로세스 RSS peak: 약 301.7MiB

Windows RSS와 Render cgroup 사용량은 직접 비교할 수 없지만, 현재 로컬 결과는
400MiB 재검토 기준 아래다. Aiven 반영 후에도 Render에서는 조회만 수행하고 수집은
개발자 PC에서 실행한다.
