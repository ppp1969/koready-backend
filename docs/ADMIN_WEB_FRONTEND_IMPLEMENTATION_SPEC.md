# KoReady 관리자 웹 프론트엔드 구현 명세

## 1. 결론

관리자 페이지는 사용자용 Expo 앱과 분리된 별도 저장소와 별도 Vercel 프로젝트로 만든다.

- GitHub 저장소 권장 이름: `koready-admin`
- Vercel 프로젝트 권장 이름: `koready-admin`
- 배포 단위: Next.js 단독 프로젝트
- 백엔드: `https://api.koready.cloud/api/v1`
- 사용자용 앱의 컴포넌트, 상태 관리, 빌드 설정은 재사용하지 않는다.

분리하는 이유는 다음과 같다.

- Expo 앱과 Next.js는 빌드 및 배포 방식이 다르다.
- 관리자 웹은 공개 사용자 화면보다 접근 제어와 감사 가능한 작업 흐름이 중요하다.
- 관리자 웹 배포가 사용자 앱 출시 일정에 영향을 주지 않는다.
- Vercel Preview 환경에서 관리자 기능을 먼저 검증하기 쉽다.

## 2. 확정 기술 구성

구현 속도와 유지보수 난이도를 우선해 아래 구성으로 고정한다.

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| 프레임워크 | Next.js App Router + TypeScript | Vercel 배포와 서버 API 프록시 구성이 가장 단순함 |
| 스타일 | Tailwind CSS | 별도 디자인 시스템 없이 관리 화면을 빠르게 구성 가능 |
| 아이콘 | Lucide React | 버튼 의미를 일관되게 표시 |
| 서버 상태 | TanStack Query | 후보 목록 캐시, 큐 등록, 작업 상태 폴링 구현이 쉬움 |
| 계약 검증 | Zod | 백엔드 응답이 예상 형식과 다를 때 조기에 감지 |
| 폼 | 기본 React 폼 | MVP 폼이 검색과 필터 중심이라 별도 폼 라이브러리 불필요 |
| 단위 테스트 | Vitest + Testing Library | 상태와 화면 분기 검증 |
| E2E | Playwright | 로그인 이후 후보 선택부터 큐 등록까지 검증 |

전역 상태 저장소, Redux, 별도 UI 프레임워크, ORM, Vercel DB는 도입하지 않는다.

## 3. 시스템 구조

브라우저는 KoReady 백엔드를 직접 호출하지 않는다. Next.js Route Handler가 인증과 API 프록시를 담당한다.

```text
관리자 브라우저
  -> koready-admin.vercel.app/api/*
  -> Next.js Route Handler
  -> https://api.koready.cloud/api/v1/*
  -> Aiven MySQL / Spring AI Worker
```

이 구조의 장점:

- 백엔드 access token과 refresh token을 JavaScript에서 읽을 수 없는 쿠키에 보관한다.
- 관리자 웹 도메인과 백엔드 사이의 CORS 설정에 의존하지 않는다.
- 토큰 갱신과 오류 형식을 한 곳에서 처리한다.
- 브라우저 번들에 DB 비밀번호, OpenAI 키, AWS 키가 들어가지 않는다.

## 4. 프로젝트 생성

```bash
npx create-next-app@latest koready-admin \
  --typescript \
  --tailwind \
  --eslint \
  --app \
  --src-dir \
  --import-alias "@/*"

cd koready-admin
npm install @tanstack/react-query zod lucide-react
npm install -D vitest @testing-library/react @testing-library/jest-dom playwright
```

패키지 매니저는 `npm`으로 통일하고 `package-lock.json`을 커밋한다.

## 5. 디렉터리 구조

```text
src/
  app/
    (auth)/
      login/page.tsx
    (console)/
      layout.tsx
      places/page.tsx
      places/[placeId]/page.tsx
      jobs/page.tsx
    api/
      session/login/route.ts
      session/logout/route.ts
      admin/editorial/candidates/route.ts
      admin/editorial/candidates/[placeId]/route.ts
      admin/editorial/places/[placeId]/queue/route.ts
      admin/editorial/jobs/route.ts
      places/[placeId]/route.ts
    layout.tsx
    page.tsx
  components/
    console-shell.tsx
    filter-bar.tsx
    place-card.tsx
    status-badge.tsx
    empty-state.tsx
    error-state.tsx
    pagination-controls.tsx
  features/
    auth/
    editorial-candidates/
    editorial-jobs/
  lib/
    backend-api.ts
    session.ts
    contracts.ts
    query-client.ts
    format.ts
  middleware.ts
```

API 응답 타입을 화면 파일 안에 중복 선언하지 않는다. `lib/contracts.ts`에 Zod 스키마와 추론 타입을 함께 둔다.

## 6. 인증 설계

### 6.1 로그인 흐름

1. `/login`에서 Google Identity Services로 Google ID Token을 받는다.
2. 브라우저가 ID Token을 `POST /api/session/login`에 전달한다.
3. Next.js 서버가 다음 백엔드 API를 호출한다.

```http
POST https://api.koready.cloud/api/v1/auth/google
Content-Type: application/json

{
  "idToken": "<google-id-token>",
  "deviceId": "admin-web-<random-uuid>"
}
```

4. 응답의 access token, refresh token, device ID를 `HttpOnly`, `Secure`, `SameSite=Lax` 쿠키에 저장한다.
5. `/api/v1/admin/editorial/**`를 한 번 호출해 권한을 확인한다.
6. `403`이면 세션을 폐기하고 "관리자 권한이 없는 계정" 화면을 표시한다.

### 6.2 세션 쿠키

| 쿠키 | 용도 | 브라우저 JavaScript 접근 |
| --- | --- | --- |
| `kr_admin_access` | KoReady access token | 금지 |
| `kr_admin_refresh` | KoReady refresh token | 금지 |
| `kr_admin_device` | refresh에 사용하는 device ID | 금지 |

백엔드 요청이 `401`이면 Next.js 서버가 `/auth/refresh`를 한 번 호출하고, 회전된 두 토큰을 쿠키에 다시 저장한 뒤 원 요청을 한 번만 재시도한다. refresh도 실패하면 쿠키를 삭제하고 `/login`으로 보낸다.

### 6.3 Google 설정

관리자 웹 전용 Google Web OAuth Client를 만든다. 사용자용 Android/iOS 클라이언트를 재사용하지 않는다.

- 승인된 JavaScript 원본: 운영 Vercel 도메인
- Preview 로그인도 필요하면 고정 Preview 도메인을 별도로 등록
- 발급된 Web Client ID를 백엔드 `GOOGLE_OAUTH_CLIENT_IDS`에도 추가
- 실제 관리자 사용자의 `users.role`은 `ADMIN`

## 7. 화면 범위

### 7.1 `/places`: 장소 후보

첫 화면이며 마케팅용 홈은 만들지 않는다.

제공 기능:

- 한글·영문 장소명 검색
- 상태 필터
- 대표 이미지, 한글명, 영문명, 권역, 원문 보유 여부 표시
- 상세 화면 이동
- 카드에서 바로 `AI 가공 요청`
- 커서 기반 다음 페이지

상태 필터:

- `NOT_REQUESTED`
- `QUEUED`
- `PROCESSING`
- `READY`
- `FAILED`
- `STALE`

큐 등록 버튼은 `QUEUED`와 `PROCESSING`에서는 비활성화한다. `READY`는 원천 데이터나 프롬프트가 바뀌지 않았다면 백엔드가 기존 작업을 반환하므로 비용이 다시 발생하지 않는다.

### 7.2 `/places/[placeId]`: 후보 상세

표시 정보:

- 우선순위가 높은 순서의 이미지 최대 4장
- 한글·영문 장소명
- KTO 한국어 원문
- 주소와 권역
- KoReady 여행 유형
- 현재 편집 상태와 최근 요청 시각

명령:

- `AI 가공 요청`
- 실패 또는 오래된 작업의 `다시 시도`
- `READY`일 때 공개 장소 상세 확인

상세 조회만으로는 AI 큐를 만들지 않는다. 관리자가 명시적으로 버튼을 눌러야 `HIGH` 우선순위 작업이 생성된다.

### 7.3 `/jobs`: 작업 큐

표시 열:

- 작업 ID와 장소 ID
- 상태
- `PM_CURATED` 또는 `USER_DETAIL`
- 우선순위
- 시도 횟수
- 요청·시작·완료 시각
- 실패 코드와 안전한 오류 요약

`QUEUED`와 `PROCESSING` 작업이 있으면 5초마다 자동 갱신한다. 처리 중인 작업이 없으면 자동 갱신을 중단한다.

### 7.4 가공 결과

별도 편집 화면은 MVP에 만들지 않는다. 후보 상세 화면에서 공개 장소 상세 API를 호출해 한글과 영어 탭으로 결과를 읽기 전용 표시한다.

- 관광 목적 태그 2개
- 주제
- 한줄설명
- 간단소개
- 이렇게 즐겨보세요 3~5개
- 콘텐츠 버전

수동 문구 수정, 승인, 숨김, 큐 취소, 일괄 등록은 후속 범위다.

## 8. 백엔드 API 연결

Next.js 프록시가 아래 API만 사용한다.

| 기능 | 백엔드 API |
| --- | --- |
| Google 로그인 | `POST /auth/google` |
| 토큰 갱신 | `POST /auth/refresh` |
| 로그아웃 | `POST /auth/logout` |
| 후보 목록 | `GET /admin/editorial/candidates` |
| 후보 상세 | `GET /admin/editorial/candidates/{placeId}` |
| PM 큐 등록 | `POST /admin/editorial/places/{placeId}/queue` |
| 작업 목록 | `GET /admin/editorial/jobs` |
| 공개 결과 확인 | `GET /places/{placeId}` |

실제 기본 URL은 `KOREADY_API_BASE_URL=https://api.koready.cloud/api/v1`이다.

백엔드 응답은 공통 envelope 안의 `data`를 사용한다. `401`, `403`, `404`, `409`, `429`, `5xx`를 같은 오류로 뭉개지 않는다.

| 상태 | 관리자 화면 처리 |
| --- | --- |
| `401` | refresh 1회 후 로그인 이동 |
| `403` | 관리자 권한 없음 |
| `404` | 장소가 없거나 공개 후보 조건 미충족 |
| `409` | 화면 상태를 새로 조회하고 충돌 안내 |
| `429` | 잠시 후 재시도 안내 |
| `5xx` | trace ID와 함께 서버 오류 표시 |

## 9. Vercel 환경변수

```text
KOREADY_API_BASE_URL=https://api.koready.cloud/api/v1
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<admin-web-google-client-id>
ADMIN_SESSION_SECRET=<32-byte-or-longer-random-secret>
```

Vercel에 넣지 않는 값:

- Aiven 주소, 사용자명, 비밀번호
- OpenAI API Key
- AWS access key
- KoReady JWT secret
- 운영 사용자 데이터

Preview와 Production은 같은 백엔드를 사용할 수 있지만, Preview 배포 접근 권한은 Vercel 보호 기능으로 팀원에게만 허용하는 것을 권장한다.

## 10. UI 원칙

- 첫 화면부터 장소 후보 테이블 또는 목록을 보여준다.
- 좌측 내비게이션은 `장소 후보`, `작업 큐` 두 항목만 둔다.
- 데스크톱은 비교가 쉬운 조밀한 목록, 모바일은 한 열 카드로 전환한다.
- 상태는 색만으로 구분하지 않고 텍스트와 아이콘을 함께 사용한다.
- 큐 등록처럼 비용이 생길 수 있는 명령은 로딩 중 중복 클릭을 막는다.
- 이미지가 없으면 장식용 이미지를 만들지 않고 명확한 빈 상태를 표시한다.
- 내부 구현이나 키보드 단축키 설명을 화면에 노출하지 않는다.

## 11. 테스트 기준

단위/컴포넌트 테스트:

- 각 편집 상태의 배지와 버튼 활성 조건
- 후보 목록의 빈 결과, 오류, 다음 페이지
- 큐 등록 성공과 중복 응답 `created=false`
- `401` refresh 성공·실패
- 일반 사용자 `403`

Playwright E2E:

1. 관리자 로그인 후 `/places` 진입
2. 후보 검색 및 상세 이동
3. 큐 등록 후 `QUEUED` 표시
4. `/jobs`에서 같은 작업 확인
5. 세션 만료 시 refresh 후 원 요청 성공

실제 OpenAI 호출은 E2E 조건에 포함하지 않는다. 백엔드 큐 등록과 상태 조회까지만 검증한다.

## 12. 구현 순서

1. 별도 비공개 저장소 `koready-admin` 생성
2. Next.js 프로젝트와 Vercel 프로젝트 연결
3. Google Web OAuth Client와 세션 프록시 구현
4. 후보 목록과 상세 화면 구현
5. 큐 등록과 작업 목록 폴링 구현
6. 공개 가공 결과 읽기 전용 표시
7. Playwright 스모크 테스트 구성
8. Vercel Production 배포

관리자 웹 구현은 지금 시작할 수 있다. Spring AI 실제 실행 여부와 관계없이 후보 조회와 큐 등록 UI를 먼저 완성할 수 있으며, 워커가 비활성 상태이면 작업은 `QUEUED`에 안전하게 남는다.
