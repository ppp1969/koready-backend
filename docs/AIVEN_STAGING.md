# Aiven MySQL staging 연결

Render의 `koready-backend-staging` 서비스가 Aiven for MySQL을 테스트 DB로 사용하기 위한 기준이다. 실제 연결정보는 저장소에 기록하지 않는다.

## Aiven 준비

1. Aiven 프로젝트의 billing group에 사용할 credit을 적용한다.
2. Aiven for MySQL 서비스를 생성한다.
3. 가능하면 Render와 가까운 cloud region을 선택한다. Aiven Free tier는 provider와 region을 직접 선택할 수 없을 수 있다.
4. Aiven Console의 service Overview에서 host, port, username, password, database를 확인한다.

초기 database는 Aiven 기본값인 `defaultdb`를 사용할 수 있다. 별도 database를 만들면 Render의 `DB_DATABASE` 값도 함께 변경한다.

## Render 환경변수

`render.yaml` Blueprint 생성 과정에서 다음 값을 입력한다.

| Key | Aiven 값 |
|---|---|
| `DB_HOST` | MySQL service host |
| `DB_PORT` | MySQL service port |
| `DB_DATABASE` | 기본 `defaultdb` |
| `DB_USERNAME` | 기본 `avnadmin` 또는 별도 app user |
| `DB_PASSWORD` | 해당 DB user password |
| `DB_SSL_MODE` | `require` |

Spring은 다음 형태의 JDBC URL을 런타임에 구성한다.

```text
jdbc:mysql://<host>:<port>/<database>?sslmode=require
```

## 네트워크 제한

Aiven의 기본 IP filter는 모든 주소를 허용할 수 있다. Render 서비스가 생성된 뒤 Render Dashboard의 Connect > Outbound에서 싱가포르 outbound CIDR 목록을 확인한다.

1. Aiven service settings에서 IP address allowlist를 연다.
2. Render에 표시된 모든 outbound CIDR을 추가한다.
3. 로컬에서 DB에 직접 접속해야 하면 개발자의 현재 공인 IP도 `/32`로 추가한다.
4. 연결 확인 후 `0.0.0.0/0`과 `::/0` 규칙을 제거한다.

Render의 기본 outbound 주소 범위는 같은 region의 다른 서비스와 공유될 수 있다. 현재 단계에서는 개발 속도를 우선하고, 운영 전환 시 전용 네트워크 또는 고정 outbound IP를 다시 검토한다.

## 확인

배포 후 다음 경로가 `UP`인지 확인한다.

```text
GET https://<render-service>.onrender.com/actuator/health/readiness
```

Flyway domain migration은 DB 모델 확정 후 추가한다. migration이 추가되기 전에는 서비스 연결과 schema history 기반만 검증한다.

## 한 달 뒤 재검토

- AWS EC2와 Elastic Beanstalk 중 애플리케이션 실행 플랫폼
- MySQL 이전 대상과 dump/restore 또는 replication 방식
- secret 저장소와 IAM 권한
- 운영 백업, 복구, 모니터링, 롤백 절차
