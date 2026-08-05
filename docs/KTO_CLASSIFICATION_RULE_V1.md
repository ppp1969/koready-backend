# KTO 장소 분류 Rule Base v1

## 목적

KTO의 신분류 코드 `lclsSystm1/2/3`를 KoReady의 7개 여행 유형 후보로
안전하게 변환한다. 넓은 상위 분류만으로 추측하지 않고 PM이 승인한 세부
범위가 확인되는 경우에만 자동 분류한다.

규칙 버전은 `kto-place-style-v1`이다.

## 확정 규칙

| KoReady 유형 | 포함하는 KTO 코드 | 제외 기준 |
|---|---|---|
| `LOCAL_FOOD` | `FD01` 한식, `FD02` 외국식, `FD03` 간이음식 | `FD04` 주점, `FD05` 카페·찻집 제외 |
| `LOCAL_FESTIVAL` | `EV01` 축제 | `EV02` 공연, `EV03` 행사 제외 |
| `TRADITIONAL_MARKET` | `SH06` 시장 | 백화점·쇼핑몰·마트·면세점·전문매장·기타쇼핑 제외 |
| `CULTURE_EXPERIENCE` | `EX01` 전통체험, `EX02` 공예체험, `EX03` 농·산·어촌 체험, `EX04` 산사체험 | 웰니스·산업관광·기타체험 제외 |
| `NATURE` | `NA` 자연관광 전체 | 없음 |
| `EXHIBITION_MUSEUM` | `VE070100` 박물관, `VE070300` 전시관, `VE070500` 과학관, `VE070600` 미술관·화랑 | 기념관·컨벤션센터 제외 |
| `DRAMA_LOCATION` | 자동 분류 없음 | 운영자가 근거를 확인한 장소만 수동 등록 |

## 판정 원칙

- `contentTypeId`만으로는 카페, 공연, 일반 쇼핑 등을 구분할 수 없으므로
  자동 분류 근거로 사용하지 않는다.
- 세부 LCLS 코드가 없거나 알 수 없는 값이면 미분류로 남긴다.
- 역사관광, 레저스포츠, 일반 문화관광, 추천 여행코스는 원본 코드만
  보존하고 7개 유형에 강제로 매핑하지 않는다.
- 자동 분류와 기존 `MANUAL` 분류는 Dry Run 통계에서 별도로 집계한다.
- `MANUAL` 분류는 이후 일괄 적용 작업에서도 자동 규칙이 덮어쓰지 않는다.
- 한 장소의 복수 유형은 허용하지만 대표 유형 선정은 Dry Run의 실제 중복
  조합을 확인한 뒤 확정한다.

## Dry Run

Dry Run은 KTO 외부 API를 호출하지 않고 현재 DB의 `places`와
`place_style_mappings`를 읽기만 한다.

```powershell
./gradlew reportKtoClassificationDryRun `
  -Pprofile=local `
  -PpageSize=500 `
  -PexampleLimit=5
```

기본 결과 파일은 다음 위치에 생성된다.

```text
build/reports/kto-classification-dry-run.json
```

보고서에는 다음 정보가 포함된다.

- 전체 KTO 장소 수
- 이미지가 한 장 이상인 장소 수
- 현재 `active`와 `show_flag`가 모두 켜진 장소 수
- 유형별 자동 분류 후보 수와 이미지 보유 수
- 자동 규칙 기준 미분류 수
- 기존 수동 분류를 합친 뒤의 미분류 수
- 자동 규칙과 기존 수동 분류를 합친 최종 분류 장소 수
- 최종 분류 장소 중 이미지가 있는 수와 현재 공개 중인 수
- 기존 수동 분류 장소와 매핑 수
- 복수 유형 조합별 수와 제한된 KTO 장소 예시

## 안전장치

- 실행 프로필은 `local`, `staging`만 허용한다.
- 전용 실행 구성은 Flyway 자동 실행과 스케줄러를 사용하지 않는다.
- ID 커서로 최대 1,000곳씩 읽어 전국 데이터를 한 번에 메모리에 올리지
  않는다.
- 보고서 예시는 KTO 공개 장소 ID와 장소명만 포함하며 사용자 정보는
  조회하지 않는다.
- Dry Run 실행 전후에 `places`와 `place_style_mappings`의 데이터가
  동일해야 한다.
- Dry Run 결과만으로 사용자 API 필터를 먼저 켜지 않는다. 확정 규칙을 DB에
  저장하고 결과를 검증한 뒤 공개 조회 조건을 적용한다.
