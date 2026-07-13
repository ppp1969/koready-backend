# Koready Backend 설계 문서 인덱스

이 폴더는 Figma UI를 직접 확인한 최신 요구사항을 기준으로 정리한 Koready 백엔드 설계 문서입니다. endpoint와 schema는 `openapi.yaml`, 정책은 `07_CONFIRMED_PRODUCT_POLICIES.md`, 설명은 `08_API_CONTRACT.md`와 `09_ADMIN_API_CONTRACT.md`를 기준으로 확인합니다.

## 확인 기준

- 작성/정리 기준일: 2026-07-13
- 원천 기획 자료: `koready_design_docs (1).zip`
- 최종 UI 기준: [KOREADY Figma - UI 페이지](https://www.figma.com/design/7iibIcjbXL8lVn5J8DMuo1/KOREADY?node-id=58-2)
- 외부 API 기준: 공공데이터포털 한국관광공사 국문/영문 관광정보 서비스, 관광사진 정보, 관광지별 연관 관광지 정보, TMAP 대중교통 API 공식 문서
- 전체 문서는 MVP 백엔드 설계 초안이며, `07_CONFIRMED_PRODUCT_POLICIES.md`에 기록된 항목은 확정 정책이다.

## 문서 목록

1. `01_FEATURE_BACKEND_DESIGN.md`
   - 기능별 백엔드 범위
   - 필요한 정보
   - 추천/상세/이동/메이트/저장/마이페이지 설계 방향
   - MVP 제외 범위

2. `02_FEATURE_DTO_DRAFT.md`
   - 기능별 Request/Response DTO 초안
   - enum 후보
   - 화면 기준 응답 구조

3. `03_TOURAPI_COVERAGE_INGESTION.md`
   - 한국관광공사 OpenAPI 커버리지 판단
   - API별 저장 필드
   - 가공/정규화 방식
   - 스케줄링 업데이트 전략

4. `04_PROCESSED_DB_MODEL_DRAFT.md`
   - 가공 저장형 DB 모델 초안
   - 핵심 테이블 후보
   - OpenAPI 호출 로그/AI 가공/Hori Tip 운영·매칭/메이트/쪽지 구조

5. `05_KTO_DATA_PROFILE_AND_PROJECT_USAGE.md`
   - 승인 API 실제 전수/확대 표본 분석
   - 필드별 유니크 개수와 주요값
   - 추천 후보 품질 퍼널과 7개 관광유형 공급량
   - 국문/영문 매칭, 코드북 불일치, 기능별 활용 방안

6. `06_FINAL_UI_SCREEN_ANALYSIS.md`
   - 최신 `⭐️ UI` 페이지 기준 화면 흐름
   - 화면값과 확정 정책 간 보정 목록

7. `07_CONFIRMED_PRODUCT_POLICIES.md`
   - 인천의 경기권 포함
   - 위치 저장/기본 위치/삭제
   - 추천 30일 재노출 금지
   - AI 번역 허용과 축제 6개월 노출 규칙
   - GPS 미사용, 경로/당일치기, 쪽지, Hori Tip 정책

8. `08_API_CONTRACT.md`
   - MVP endpoint 기준본
   - Request/Response DTO와 검증 규칙
   - TMAP 정규화 및 임시 경로 정책
   - 오류 코드와 구현 수락 기준

9. `09_ADMIN_API_CONTRACT.md`
   - 관리자 권한과 OpenAPI 호출 로그 조회
   - KTO 원천 snapshot과 SHA-256 계보
   - 배치 실행/재시도와 동기화 cursor 관리
   - 공모전 증빙 번들과 관리자 감사 로그

10. `openapi.yaml`
   - 사용자/관리자 endpoint의 기계 판독 가능한 최종 계약
   - 공통 enum, 요청/응답 schema, 오류 응답

11. `10_FRONTEND_API_FLOW_GUIDE.md`
   - 프론트 개발자를 위한 화면별 API 호출 순서
   - 공통 fetch wrapper, token 갱신, cursor, idempotency 처리
   - 성공/실패 후 화면 행동과 Figma 대조 체크리스트

12. `11_TMAP_API_PROFILE.md`
   - TMAP 상세 경로 API 3회 제한 probe 결과
   - 실제 응답 casing, mode/type, service flag와 HTTP 200 오류 구조
   - Route DTO 정규화, 오류 변환, 24시간 미만 보관 정책

## 설계 원칙

- 프론트는 외부 API를 직접 호출하지 않고, Koready 백엔드 API만 호출한다.
- 관광지 추천/필터링의 기준 데이터는 국문 TourAPI 기반으로 가공 저장한다.
- 영어 표시는 영문 TourAPI 또는 자체 번역/AI 번역 데이터로 보강한다.
- GPS는 사용하지 않는다. 사용자가 검색/선택한 현재 위치를 추천과 이동 가능성 판단의 기준으로 사용한다.
- 추천은 초기에 고급 AI 추천이 아니라 룰베이스 + 후보군 내 무작위/탐색을 섞어 시작한다.
- TMAP 대중교통 응답 원본은 DB에 저장하지 않고, 실시간 호출 후 정규화한 결과만 응답한다.
- 추천 카드와 Hori Tip은 구분한다. Hori Tip은 이동 경로 기반 안내다.
- KTX 한국 여행 가이드는 프론트 정적 콘텐츠이며 백엔드 MVP에서 제외한다.

## 요구사항 대응표

| 요구사항 | 주 문서 |
|---|---|
| 확정 제품 정책 | `07_CONFIRMED_PRODUCT_POLICIES.md` |
| MVP API endpoint/DTO/오류 계약 | `08_API_CONTRACT.md` |
| 관리자/OpenAPI 증빙 API 계약 | `09_ADMIN_API_CONTRACT.md` |
| 기계 판독 API endpoint/schema | `openapi.yaml` |
| 프론트 화면별 호출 흐름과 오류 처리 | `10_FRONTEND_API_FLOW_GUIDE.md` |
| TMAP 실제 응답 구조와 Route 매핑 | `11_TMAP_API_PROFILE.md` |
| 기능별 백엔드 범위 | `01_FEATURE_BACKEND_DESIGN.md` |
| 화면별 필요한 정보 | `01_FEATURE_BACKEND_DESIGN.md`, `02_FEATURE_DTO_DRAFT.md` |
| 기능별 Request/Response DTO 초안 | `02_FEATURE_DTO_DRAFT.md` |
| 한국관광공사 OpenAPI 커버 가능/불가능 판단 | `03_TOURAPI_COVERAGE_INGESTION.md` |
| TourAPI 각 operation별 저장/가공 방식 | `03_TOURAPI_COVERAGE_INGESTION.md`, `04_PROCESSED_DB_MODEL_DRAFT.md` |
| 국문/영문/이미지/연관 관광지 데이터 처리 방식 | `03_TOURAPI_COVERAGE_INGESTION.md` |
| 월별 추천, 지도 추천, K-Local Pick 추천 로직 방향 | `01_FEATURE_BACKEND_DESIGN.md`, `03_TOURAPI_COVERAGE_INGESTION.md` |
| Buddy Route 난이도/당일치기 판단 기준 | `01_FEATURE_BACKEND_DESIGN.md`, `02_FEATURE_DTO_DRAFT.md` |
| Hori Tip 운영·저장·매칭 구조 | `01_FEATURE_BACKEND_DESIGN.md`, `04_PROCESSED_DB_MODEL_DRAFT.md`, `08_API_CONTRACT.md`, `09_ADMIN_API_CONTRACT.md` |
| Buddy Connect 프로필/쪽지 구조 | `01_FEATURE_BACKEND_DESIGN.md`, `02_FEATURE_DTO_DRAFT.md`, `04_PROCESSED_DB_MODEL_DRAFT.md` |
| OpenAPI 호출 로그 구조 | `03_TOURAPI_COVERAGE_INGESTION.md`, `04_PROCESSED_DB_MODEL_DRAFT.md` |
| 가공 저장형 DB 모델 초안 | `04_PROCESSED_DB_MODEL_DRAFT.md` |
| 스케줄링/동기화 전략 | `03_TOURAPI_COVERAGE_INGESTION.md`, `04_PROCESSED_DB_MODEL_DRAFT.md` |
| 실제 API 유니크값/결측률/주요값 | `05_KTO_DATA_PROFILE_AND_PROJECT_USAGE.md`, `../kto-api-analysis/LATEST.md` |
| 실제 데이터 기반 추천 후보/영문 매칭 | `05_KTO_DATA_PROFILE_AND_PROJECT_USAGE.md`, `../kto-api-analysis/supplements/LATEST.md` |

## 참고 공식 자료

- [한국관광공사 국문 관광정보 서비스_GW](https://www.data.go.kr/data/15101578/openapi.do)
- [한국관광공사 영문 관광정보서비스_GW](https://www.data.go.kr/data/15101753/openapi.do)
- [한국관광공사 관광사진 정보_GW](https://www.data.go.kr/data/15101914/openapi.do)
- [한국관광공사 관광지별 연관 관광지 정보](https://www.data.go.kr/data/15128560/openapi.do)
- [TMAP 대중교통 API](https://transit.tmapmobility.com/)
