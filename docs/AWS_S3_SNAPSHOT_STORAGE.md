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
| 로컬에서 staging 수집 | S3 선택 가능 | AWS CLI `aws login`/SSO 임시 profile |
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

## 실제 배포 검증 (2026-07-19)

서울 리전의 실제 AWS 계정에 `koready-kto-snapshot-storage` stack을 배포했고
상태가 `CREATE_COMPLETE`임을 확인했다. 공개 저장소에는 계정 ID, 자동 생성된 bucket
이름, 결제 정보와 알림 수신 주소를 기록하지 않는다.

| 검증 항목 | 실제 확인 결과 |
|---|---|
| 리전 | `ap-northeast-2` |
| 외부 공개 여부 | `IsPublic=false` |
| Block Public Access | 네 설정 모두 `true` |
| 객체 소유권·ACL | `BucketOwnerEnforced`, ACL 비활성 |
| 기본 암호화 | SSE-S3 `AES256` |
| 버전 관리 | `Enabled` |
| bucket 강제 정책 | TLS 사용, `If-None-Match: *` 조건부 쓰기 |
| 애플리케이션 role | 위치·prefix 조회와 `GetObject`/`PutObject`만 허용 |
| 삭제 권한 | `DeleteObject` 없음 |
| 미완료 multipart 정리 | 7일 |

비식별 16-byte 시험 파일로 다음 순서의 스모크 테스트를 수행했다.

1. 조건 헤더가 없는 `kto/*` PUT은 `AccessDenied`로 거절됐다.
2. `If-None-Match: *`가 있는 첫 PUT은 성공했다.
3. 같은 key의 두 번째 조건부 PUT은 `PreconditionFailed`로 거절됐다.
4. 저장 객체가 `AES256`으로 암호화됐음을 확인했다.
5. 시험 객체의 version을 지정해 삭제한 뒤 남은 version과 delete marker가 0건임을
   확인했다.

월간 AWS 비용 예산은 USD 5로 설정했다. 실제 비용 85%·100%와 예측 비용 100%에서
알림을 받는다. 이 값은 서비스 예산 확정 전의 초기 안전장치이며, 비용을 강제로
차단하는 한도는 아니다.

현재 Render에는 AWS 자격증명을 넣지 않았고 KTO 웹 배치도 계속 비활성 상태다.
로컬 수집 작업은 AWS CLI `aws login` 또는 SSO 임시 세션으로 실행하고, 향후 EB는
이 stack이 만든 instance profile을 연결한다. EB 환경, EC2와 AWS DB는 이번 배포에
포함하지 않았다.

### 애플리케이션 종단 검증

2026-07-19 실제 `searchFestival2` 200건을 S3 snapshot과 Aiven staging까지
처리했다. 157,164byte 원문은 25,516byte gzip으로 저장됐고 S3 metadata와 Aiven의
원문 hash, 저장 객체 hash와 크기가 모두 일치했다. 같은 입력 재실행은 기존 객체와
DB 계보를 재사용했으며 S3 version과 DB 행을 추가하지 않았다. 세부 시간과 DB 건수는
`KTO_BATCH_OPERATIONS.md`의 종단 검증 기록을 따른다.

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
