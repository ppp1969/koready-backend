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
| DB 반영 단위 | 50건 | 1~page 크기 |
| 동시 batch | 1개 | 1개 |

한 page의 처리 순서는 다음과 같다.

1. KTO page 하나를 요청한다.
2. 원본은 메모리에 장기 보관하지 않고 snapshot 저장 경계로 전달한다.
3. item을 최대 50건씩 DB에 반영한다.
4. page의 모든 item과 원본 보관이 성공한 뒤에만 sync cursor를 다음 page로 옮긴다.
5. page 참조를 해제한 뒤 다음 page를 요청한다.

실패한 page는 완료 처리하지 않는다. 이전 page의 성공 결과와 cursor는 유지하고,
재실행 시 실패한 page부터 이어간다. 전체 68,493건을 한 transaction이나 한
컬렉션으로 다루지 않는다.

## 금지 사항

- 전체 건수를 요청하는 매우 큰 numOfRows
- 여러 KTO page 병렬 호출
- 전체 응답을 static collection 또는 application cache에 보관
- KTO 원본 JSON을 DB의 큰 JSON 컬럼에 직접 저장
- 실제 API key, Authorization 값 또는 원본 응답 본문을 로그에 출력
- 한 page 실패 때문에 이전 성공 page의 transaction을 되돌리는 방식

## 관찰 항목

실제 KTO client가 추가되면 page마다 다음 값을 기록한다.

- 응답 byte 수와 item 수
- 처리 시간
- 성공·실패 수
- 처리 전후 heap 사용량
- GC 횟수와 일시정지 시간
- 현재 page와 마지막 성공 cursor

500MB 제한 스모크 테스트에서는 readiness가 UP인지, 컨테이너가 OOM으로
재시작하지 않는지, RSS가 제한을 넘지 않는지 확인한다. 실제 표본에서 여유가
부족하면 동시성은 유지하고 page 크기를 먼저 100건으로 낮춘다.
