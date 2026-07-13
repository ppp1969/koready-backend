# KTO API Probe Notes

KoReady 백엔드 개발 전에 한국관광공사 승인 API의 실제 JSON 응답 구조를 확인하기 위한 probe 산출물이다.

## How To Run

```powershell
python scripts\probe_kto_apis.py --rows 5
```

- 인증키는 로컬 `KTO_SERVICE_KEY` 환경변수 또는 `.env.local`에서 읽는다.
- `.env.local`은 git에 올리지 않는다.
- 생성 파일의 `serviceKey` 값은 `***`로 마스킹된다.

## Output Layout

- `LATEST.md`: 로컬 실행 후 생성되는 가장 최근 결과 링크이며 git에는 올리지 않는다.
- `<timestamp>/SUMMARY.md`: operation별 성공 여부, totalCount, sample field 요약
- `<timestamp>/manifest.json`: 호출 파라미터, HTTP 상태, resultCode, fieldNames 메타데이터
- `<timestamp>/raw/*.json`: 실제 원본 응답
- `<timestamp>/analysis/*_fields.json`: field 목록, sample item, sample 기반 unique value 요약

## Latest Observations

Latest checked run: `20260713_073344`

### KorService2

- 주요 목록/검색/상세 호출이 JSON으로 정상 응답한다.
- `areaBasedList2`, `searchKeyword2`, `searchFestival2`, `areaBasedSyncList2`는 관광지/축제 후보 수집의 기본 소스가 된다.
- `detailCommon2`는 목록에서 얻은 `contentId`만으로 정상 조회됐다.
- `detailIntro2`, `detailInfo2`, `detailImage2`는 `contentId`와 필요 시 `contentTypeId` 기반으로 상세/반복 정보와 이미지를 보강한다.
- `detailPetTour2`는 호출 자체는 성공하지만, 선택한 sample 관광지에서는 item이 없었다.

### EngService2

- 국문 서비스와 유사하게 목록/검색/상세/이미지 호출이 정상 응답한다.
- 영문 상세와 국문 상세를 `contentId` 기준으로 묶어 다국어 관광지 저장 모델을 설계하면 된다.
- `detailInfo2`는 sample content에서는 0건이 나올 수 있다.

### PhotoGalleryService1

- `galleryList1`, `gallerySearchList1`, `galleryDetailList1` 모두 정상 응답한다.
- 대표 필드는 `galContentId`, `galTitle`, `galPhotographyLocation`, `galPhotographyMonth`, `galSearchKeyword`, `galWebImageUrl`이다.
- `galleryDetailList1`은 `title` 파라미터 중심으로 호출하는 쪽이 안전했다.

### PhokoAwrdService

- operation명은 `ldongCode`, `phokoAwrdList`, `phokoAwrdSyncList` 형태로 동작한다.
- 수상작 데이터는 국문/영문 제목, 촬영지, 키워드, 원본/썸네일 이미지 URL을 함께 제공한다.
- 대표 필드는 `contentId`, `koTitle`, `koFilmst`, `koKeyWord`, `enTitle`, `enFilmst`, `enKeyWord`, `orgImage`, `thumbImage`, `cpyrhtDivCd`이다.

### TarRlteTarService1

- `areaBasedList1`은 `baseYm`, `areaCd`, `signguCd`가 필요하다.
- 응답은 기준 관광지와 연관 관광지의 코드/이름/지역/카테고리/순위를 포함한다.
- 대표 필드는 `tAtsCd`, `tAtsNm`, `rlteTatsCd`, `rlteTatsNm`, `rlteCtgryLclsNm`, `rlteCtgryMclsNm`, `rlteCtgrySclsNm`, `rlteRank`이다.
- `searchKeyword1`은 선택한 월/지역/키워드 조합에 따라 정상 성공이어도 0건일 수 있다.

## Development Use

- DTO 초안 작성 시 `analysis/*_fields.json`의 `fieldNames`, `sampleItems`, `uniqueSummary`를 우선 참고한다.
- enum 후보는 `uniqueSummary`에서 뽑되, sample 기반이므로 DB 제약으로 바로 고정하지 않는다.
- 저장형 ingestion은 raw 응답 저장 후 processed table로 정규화하는 방식이 좋다.
- 추천 로직에는 Kor/Eng 관광지 기본정보, PhotoGallery 이미지, PhokoAwrd 고품질 이미지, related_tour 연관 순위를 별도 feature로 합성한다.

## Deep Analysis

5건 응답 구조 확인 이후의 전수 유니크/결측/주요값과 KoReady 기능별 적용 결론은 다음 문서를 사용한다. `LATEST.md` 두 파일은 분석 스크립트를 실행한 로컬 환경에서 생성되며, 저장소에는 고정된 프로젝트 적용 결론만 커밋한다.

- `../kto-api-analysis/LATEST.md`
- `../kto-api-analysis/supplements/LATEST.md`
- `../koready-backend-design/05_KTO_DATA_PROFILE_AND_PROJECT_USAGE.md`
