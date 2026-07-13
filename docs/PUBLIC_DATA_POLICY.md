# 공개 저장소 데이터 정책

KoReady 저장소는 공개 저장소다. 외부 API 조사 결과와 테스트 자료는 아래 기준을 통과한 경우에만 커밋한다.

## 커밋 가능

- 필드명과 자료형만 남긴 schema
- 호출 건수, 성공률처럼 개인과 요청을 재구성할 수 없는 집계값
- secret과 개인정보를 제거한 최소 fixture
- 공개 문서 URL과 직접 작성한 분석·설계 문서

## 커밋 금지

- 원본 API 응답과 전체 provider payload
- `serviceKey`, `appKey`, access/refresh token, Authorization 헤더
- 실제 사용자의 이메일, 위치, 소셜 계정, 메시지
- secret이 포함될 수 있는 요청 URL, curl 명령, 로그, screenshot
- 운영 DB dump와 클라우드 자격증명

## 공개 전 확인

1. `.env.local`의 실제 값이 산출물에 포함되지 않았는지 검사한다.
2. 원본 응답을 최소 fixture로 줄이고 식별 가능한 값을 합성값으로 교체한다.
3. `git diff --cached`와 Gitleaks 결과를 확인한다.
4. 데이터 출처와 라이선스를 문서에 기록한다.

날짜별 KTO probe 및 분석 산출물은 `.gitignore`로 제외한다. 공개할 결론은 별도의 요약 문서로 작성한다.
