# KoReady AI 개발 지침

## 프로젝트 기준

- KoReady는 외국인 방한·체류자를 위한 한국 로컬 여행 준비 및 추천 백엔드다.
- 공개 저장소의 제품 개요와 개발 명령은 `README.md`를 우선한다.
- `docs/koready-backend-design` 문서는 기능 설계 자료지만, 제목에 초안으로 표시된 정책과 DB 모델은 확정본으로 간주하지 않는다.
- `docs/kto-api-probes`의 원본 응답은 공개 안전성 검토 전까지 커밋하지 않는다.

## 실행 환경

- Java 21, Spring Boot 4.1.x, Gradle Wrapper를 사용한다.
- 자동 테스트는 `test`, 로컬 개발은 `local`, Render+Aiven은 `staging`, 향후 AWS 운영은 `prod` 프로필을 사용한다.
- 로컬 MySQL은 `docker compose up -d mysql`로 실행한다.
- 전체 환경은 `docker compose --profile full up --build`로 검증한다.

## 구현 규칙

- API는 `/api/v1` 아래에 둔다.
- 패키지는 기능 도메인을 기준으로 나누고 controller, application, domain, infrastructure 의존 방향을 유지한다.
- 외부 API 응답을 controller 또는 JPA entity에 직접 노출하지 않는다.
- DB 변경은 Flyway migration으로만 수행하며 적용된 migration 파일은 수정하지 않는다.
- 운영 코드에서 `ddl-auto=create`, `create-drop`, `update`를 사용하지 않는다.
- 새 동작에는 정상 흐름과 핵심 실패 흐름 테스트를 함께 추가한다.

## 보안 규칙

- 키, 토큰, 쿠키, Authorization 헤더, 개인 위치정보를 코드·문서·fixture·로그에 넣지 않는다.
- `.env.local`은 로컬 전용이며 `.env.example`에는 placeholder만 둔다.
- 외부 API fixture는 secret과 PII를 제거한 최소 응답만 저장한다.
- AI 도구에는 운영 AWS 자격증명, 운영 DB 계정, 실제 사용자 데이터를 제공하지 않는다.
- Aiven과 Render의 실제 연결정보는 각 서비스의 secret 환경변수로만 관리한다.

## 완료 조건

- `./gradlew clean test`가 통과한다.
- 배포 관련 변경은 `docker build -t koready-backend:local .`까지 통과한다.
- 설정 변경 시 `local`, `test`, `staging`, `prod` 영향 범위를 확인한다.
- API 계약 또는 운영 방식이 바뀌면 README와 관련 설계 문서를 함께 갱신한다.
