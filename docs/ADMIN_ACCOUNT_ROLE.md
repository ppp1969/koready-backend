# 운영 관리자 계정 역할 설정

KoReady 운영 계정의 단일 역할은 Aiven MySQL `defaultdb`의 `users.role`에서 관리한다.
역할 변경 API와 관리자 UI는 아직 없으므로 승인된 운영자가 DB에서 직접 변경한다.

## 역할

| 값 | 권한 |
|---|---|
| `USER` | 일반 사용자 API |
| `ADMIN` | 전체 관리자 API와 ADMIN 전용 변경 |
| `OPERATOR` | 큐레이션, KTO 운영과 허용된 변경 |
| `AUDITOR` | 관리자 조회 전용 |

모든 기존 사용자와 신규 Google 사용자는 `USER`가 기본값이다. DB CHECK 제약이 위 네 값
이외의 저장을 거부한다.

## 관리자 승격

1. 관리자용 Google 계정으로 KoReady 로그인을 한 번 완료한다.
2. 로그인 응답의 `user.publicId`를 안전한 운영 채널에서 확인한다.
3. Aiven `defaultdb`에 접속한다.
4. 아래 트랜잭션의 `replace-with-user-public-id`를 대상 public ID로 바꿔 실행한다.
5. 변경된 계정에서 로그아웃 후 다시 Google 로그인한다.
6. 일반 사용자 토큰은 관리자 API에서 `403`, 새 관리자 토큰은 허용되는지 확인한다.

```sql
SET @target_public_id = 'replace-with-user-public-id';

START TRANSACTION;

SELECT id, public_id, role, signup_status
FROM users
WHERE public_id = @target_public_id
  AND deleted_at IS NULL
FOR UPDATE;

UPDATE users
SET role = 'ADMIN',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE public_id = @target_public_id
  AND deleted_at IS NULL;

SELECT ROW_COUNT() AS updated_users;

UPDATE auth_refresh_sessions
SET revoked_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE user_id = (
    SELECT id
    FROM users
    WHERE public_id = @target_public_id
)
  AND revoked_at IS NULL;

COMMIT;
```

SQL 문을 순서대로 실행하고 `updated_users=1`을 확인한 뒤 나머지 SQL 문을 실행한다. 값이 1이
아니면 즉시 `ROLLBACK`하고 public ID를 다시 확인한다. 운영 변경 기록에는 실행자, 시각,
대상 public ID, 변경 전후 역할과 사유를 남긴다. 이메일, Google subject, 토큰과 DB
비밀번호는 기록하지 않는다.

## 강등과 역할 변경

승격 SQL의 `ADMIN`을 원하는 역할로 바꿔 실행한다. `USER`로 강등할 때도 활성 refresh
session을 모두 폐기한다.

JWT는 발급 시점의 역할을 최대 15분 동안 보유한다. DB 변경과 refresh session 폐기만으로
이미 발급된 access token을 즉시 무효화할 수는 없다.

- 승격: DB 변경 후 다시 로그인하면 새 역할을 사용할 수 있다.
- 강등: refresh session을 폐기하고 마지막 관리자 access token 발급 시점부터 최대 15분을
  기다린 뒤 권한 차단을 확인한다.
- 긴급 강등: 현재 구조에서는 JWT secret 교체 외에 전체 access token을 즉시 폐기하는
  수단이 없다. 개별 토큰 즉시 폐기가 필요하면 token version 또는 denylist를 후속 구현한다.

## 확인 쿼리

```sql
SET @target_public_id = 'replace-with-user-public-id';

SELECT public_id, role, signup_status, updated_at
FROM users
WHERE public_id = @target_public_id
  AND deleted_at IS NULL;
```

역할을 바꾼 뒤 기존 access token으로 결과를 판단하지 않는다. 반드시 새 Google 로그인
또는 유효한 refresh 흐름으로 새 access token을 발급해 확인한다.
