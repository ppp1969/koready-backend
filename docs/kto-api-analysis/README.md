# KTO API Analysis

KoReady 개발용 한국관광공사 API 실제 응답 프로필이다. 인증키는 `KTO_SERVICE_KEY` 환경변수 또는 git에서 제외된 `.env.local`에서만 읽는다.

## Latest Outputs

- 전수 데이터 프로필: 로컬 실행 후 생성되는 `LATEST.md`
- 추천 준비도/국문-영문 매칭: 로컬 실행 후 생성되는 `supplements/LATEST.md`
- 프로젝트 적용 결론: `../koready-backend-design/05_KTO_DATA_PROFILE_AND_PROJECT_USAGE.md`

타임스탬프 산출물과 `LATEST.md` 포인터는 재현 가능한 로컬 조사 자료이므로 git에 올리지 않는다. 저장소에서는 프로젝트 적용 결론 문서를 안정적인 참조로 사용한다.

## Run

```powershell
python scripts\profile_kto_data.py --rows-per-page 1000
python scripts\analyze_kto_recommendation_readiness.py --preferred-rows 70000 --fallback-rows 1000
```

첫 스크립트는 코드북, 국문/영문, 관광사진, 수상작, 연관 관광지 표본, 유형별 상세 필드를 집계한다. 두 번째 스크립트는 행 단위 결합이 필요한 추천 후보 퍼널과 국문-영문 매칭을 계산한다.

생성 파일의 호출 파라미터에서는 `serviceKey`가 마스킹된다. 전수 원문은 저장하지 않고 정확한 유니크 수, 결측 수, 상위값과 최소 표본만 저장한다.
