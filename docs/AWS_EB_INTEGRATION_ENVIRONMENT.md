# AWS Elastic Beanstalk 통합 환경

## 목적

프론트와 PM이 같은 Swagger 및 Aiven 데이터를 확인할 수 있도록 Render의 공유 `staging`
환경을 AWS Elastic Beanstalk(EB) 서울 리전으로 옮긴다. 이 환경은 로그인과 실제 사용자
운영이 완료되기 전의 **통합 검증 환경**이다. 따라서 Spring profile은 `prod`가 아니라
`staging`을 사용한다.

EB는 Render보다 큰 인스턴스에서 Docker 애플리케이션을 실행하므로, KTO 원본의 private S3
보관과 Aiven 반영을 위한 기반을 제공한다. 단, 단일 인스턴스 환경은 고가용성 운영 환경이
아니다. 실제 공개 운영 전에는 로드 밸런서, 다중 인스턴스, 배포 롤백과 장애 대응을 별도
이슈에서 확정한다.

## 구성

```text
Frontend / PM
      |
      v
Elastic Beanstalk single instance (Seoul, Docker, staging)
      |                         | \
      |                         |  \-- private S3 snapshots
      v                         |
Aiven MySQL --------------------+
```

- 인스턴스: x86 `t3.small` 1대부터 시작한다.
- 컨테이너: 저장소의 `Dockerfile`을 사용하며 서비스 포트는 `8080`이다.
- 상태 확인: `/actuator/health/readiness`가 `UP`일 때만 정상으로 판단한다.
- DB: 기존 Aiven `staging` DB를 재사용한다. Flyway가 앱 시작 시 migration을 적용한다.
- 원본 보관: 기존 CloudFormation stack의 private S3 bucket을 사용한다.

## EB 환경 생성 기준

1. 리전을 `ap-northeast-2`(서울)로 선택한다.
2. Docker on AL2023 플랫폼, Single instance 환경, x86 `t3.small`을 선택한다.
3. 애플리케이션 health check URL은 `/actuator/health/readiness`로 설정한다. 저장소의
   `.ebextensions/01-healthcheck.config`도 같은 값을 배포한다.
4. `koready-kto-snapshot-storage` CloudFormation stack의 **instance profile 출력값**을
   EB EC2 instance profile로 연결한다.
5. EB에 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, AWS CLI 장기 key를 넣지 않는다.
   AWS SDK는 instance profile의 임시 자격증명을 자동으로 사용한다.

## 환경 변수

아래 목록의 실제 값은 EB Console의 Environment properties에만 입력한다. 저장소, issue,
PR, 스크린샷에는 값 자체를 적지 않는다.

| 구분 | 변수 | 설정 기준 |
|---|---|---|
| profile | `SPRING_PROFILES_ACTIVE` | `staging` |
| DB | `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD` | Aiven service 정보 |
| DB | `DB_SSL_MODE` | `require` |
| DB | `DB_POOL_SIZE`, `DB_MIN_IDLE` | `5`, `1` |
| KTO | `KTO_SERVICE_KEY` | data.go.kr 발급 key |
| KTO | `KTO_BATCH_PAGE_SIZE`, `KTO_BATCH_FLUSH_SIZE`, `KTO_BATCH_MAX_CONCURRENCY` | `200`, `50`, `1` |
| KTO | `KTO_MAX_RESPONSE_BYTES`, `KTO_CONNECT_TIMEOUT`, `KTO_READ_TIMEOUT` | `4194304`, `3s`, `10s` |
| S3 | `KTO_SNAPSHOT_STORAGE`, `KTO_SNAPSHOT_S3_BUCKET`, `AWS_REGION` | `s3`, CloudFormation bucket 출력값, `ap-northeast-2` |
| location | `LOCATION_SEARCH_PROVIDER`, `KAKAO_REST_API_KEY`, `LOCATION_SEARCH_TOKEN_SECRET` | `kakao`, Kakao key, 32 byte 이상 random secret |
| integration | `TMAP_APP_KEY`, `JWT_SECRET` | 발급 key와 32 byte 이상 random secret |
| JVM | `JAVA_OPTS` | `-Xms256m -Xmx768m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8` |

`KTO_MANUAL_BATCH_WORKER_ENABLED=true`은 기본값이므로 필요할 때만 명시한다. KTO의
page size와 concurrency는 빠른 수집보다 Aiven 안정성을 우선해 유지한다.

## 배포와 전환 순서

1. EB에 source bundle을 배포한다. `.ebignore`가 `.env.local`, key 파일, build 결과를
   제외하는지 먼저 확인한다.
2. EB Console에서 secret 환경 변수를 입력하고 instance profile을 연결한다.
3. `https://<eb-domain>/actuator/health/readiness`가 `UP`인지 확인한다.
4. `https://<eb-domain>/swagger-ui/index.html`와 `/openapi/koready.yaml`이 HTTP 200인지
   확인한다.
5. 장소, 축제, 월간 추천 등 공개 GET API가 기존 Aiven 데이터로 응답하는지 확인한다.
6. private S3 snapshot metadata와 signed download URL은 관리자 인증이 준비된 뒤 별도
   권한 계정으로 점검한다. URL이나 AWS 자격증명을 로그에 남기지 않는다.
7. 위 확인이 끝난 뒤 프론트의 base URL을 EB 도메인으로 바꾸고, Render는 즉시 삭제하지
   않고 롤백용으로 유지한다.

## KTO 수집 범위

EB instance profile을 연결하면 S3 원본 저장과 Aiven 연결을 모두 수행할 수 있다. 다만
현재 `staging`의 관리자 수집 API는 실제 로그인/권한 체계가 적용되기 전까지 공개 Swagger
에서 실행하지 않는다. 기존 Aiven 데이터는 EB에서 그대로 조회할 수 있으며, 추가 수집을
EB에서 자동화하는 작업은 로그인·운영자 권한과 함께 별도 이슈로 진행한다.

즉, 이번 이전의 완료 기준은 “KTO 데이터가 들어 있는 Aiven을 EB Swagger로 안정적으로
조회하고, EB가 private S3에 접근할 최소 권한 기반을 갖춘 상태”다. 대량·정기 수집을
웹 요청으로 실행하는 것은 이 범위에 포함하지 않는다.
