# AWS S3 Snapshot 저장소

## 목적

KTO 성공 응답 원본을 가공 전에 gzip으로 압축해 서울 리전의 private S3에 보관한다.
애플리케이션은 원문 SHA-256, 실제 gzip 객체 SHA-256, object key와 수집 시각만
Aiven MySQL의 snapshot 메타데이터에 연결한다.

## 환경별 사용 방식

| 환경 | 저장소 | 자격증명 |
|---|---|---|
| 자동 테스트 | fake/mock | 없음 |
| 로컬 기본 | 저장소 밖 로컬 디렉터리 | 없음 |
| 로컬에서 staging 수집 | S3 선택 가능 | AWS CLI/SSO 기본 profile |
| Render web staging | local 기본, 수집 비활성 | AWS 자격증명 없음 |
| 향후 EB | S3 | EC2 instance profile |

Render 웹 프로세스에는 장기 AWS access key를 넣지 않는다. AWS SDK는 별도 key
설정 없이 default credential chain을 사용하며, EB에서는 EC2 instance profile의
임시 자격증명을 사용한다.

## CloudFormation

템플릿은 `infra/aws/kto-snapshot-storage.yaml`이다. 서울 리전
`ap-northeast-2`에 `koready-kto-snapshot-storage` stack으로 배포한다. bucket
이름은 전역 중복을 피하기 위해 CloudFormation이 자동 생성한다.

```powershell
aws cloudformation deploy `
  --region ap-northeast-2 `
  --stack-name koready-kto-snapshot-storage `
  --template-file infra/aws/kto-snapshot-storage.yaml `
  --capabilities CAPABILITY_IAM `
  --parameter-overrides EnvironmentName=shared

aws cloudformation describe-stacks `
  --region ap-northeast-2 `
  --stack-name koready-kto-snapshot-storage `
  --query 'Stacks[0].Outputs'
```

배포 전 AWS 계정 활성화와 서울 리전 선택을 확인한다. 장기 access key를 새로 만들지
않고, 로컬 배포에는 AWS CLI/SSO의 임시 로그인 세션을 사용한다.

생성 자원:

- private S3 bucket
- EC2/EB용 최소 권한 IAM role
- role을 담는 instance profile
- TLS와 조건부 PUT을 강제하는 bucket policy

bucket 기준:

- Object Ownership `BucketOwnerEnforced`, ACL 비활성
- Block Public Access 네 설정 활성
- SSE-S3 (`AES256`) 기본 암호화
- versioning 활성
- 미완료 multipart upload는 7일 뒤 정리
- stack 삭제 시 bucket과 데이터는 `Retain`
- `kto/*` PUT은 `If-None-Match: *`가 없으면 거절

애플리케이션 role은 `kto/*`의 `GetObject`, `PutObject`와 bucket 위치·prefix
조회만 허용한다. `DeleteObject`, bucket 정책 수정, 공개 설정 변경 권한은 없다.

## 애플리케이션 설정

S3를 명시적으로 사용할 때만 아래 값을 설정한다.

```text
KTO_SNAPSHOT_STORAGE=s3
KTO_SNAPSHOT_S3_BUCKET=<CloudFormation SnapshotBucketName 출력값>
AWS_REGION=ap-northeast-2
```

`KTO_SNAPSHOT_STORAGE` 기본값은 `local`이다. S3를 선택했는데 bucket 값이 없거나
region이 잘못되면 애플리케이션은 시작 단계에서 실패한다.

## 저장과 재처리

1. KTO 원문 hash를 검증한다.
2. gzip byte와 저장 객체 hash를 계산한다.
3. 원문 hash가 포함된 결정적 object key로 조건부 PUT한다.
4. 성공한 경우에만 DB transaction으로 call log, snapshot 계보와 가공 데이터를
   저장한다.
5. 같은 key가 이미 있으면 객체를 읽어 압축 해제 원문 hash를 다시 검증한다.
6. 내용이 같으면 멱등 재실행으로 처리하고, 다르면 충돌로 중단한다.

S3 `409 ConditionalRequestConflict`는 최대 3회 재시도한다. 인증, 네트워크,
권한이나 다른 S3 오류는 snapshot 저장 실패로 정규화하며 provider 오류 본문이나
자격증명을 로그에 남기지 않는다.

## 이번 단계의 보존 정책

Object Lock과 완료 객체 자동 만료는 적용하지 않는다. 보존 기간이 확정되지 않은
상태에서 WORM 기간이나 삭제 시점을 고정하면 복구와 비용 조정이 어려워질 수 있다.
현재는 versioning, 전역 조건부 PUT 정책과 삭제 권한 없는 애플리케이션 role로
변경 위험을 막는다. 보존 기간 확정 후 별도 변경 이슈에서 Object Lock과 lifecycle을
검토한다.

## 검증 명령

```powershell
aws s3api get-public-access-block --bucket <bucket> --region ap-northeast-2
aws s3api get-bucket-encryption --bucket <bucket> --region ap-northeast-2
aws s3api get-bucket-versioning --bucket <bucket> --region ap-northeast-2
aws s3api get-bucket-ownership-controls --bucket <bucket> --region ap-northeast-2
```

실제 object smoke test는 `If-None-Match: *`가 있는 첫 PUT이 성공하고, 동일 key의
두 번째 PUT이 `412 Precondition Failed`로 거절되는지 확인한다. 테스트 object에는
실제 KTO 응답, 사용자 데이터나 secret을 사용하지 않는다.
