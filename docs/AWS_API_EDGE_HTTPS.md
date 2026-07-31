# AWS API HTTPS 연결

## 목적

`api.koready.cloud` 요청을 HTTPS로 받은 뒤 서울 리전의 Elastic Beanstalk
`koready-staging-eb` 환경으로 전달한다. 프론트엔드는 EB 임시 주소 대신 이 고정 API
주소를 사용한다.

## 구성

```text
Frontend
   |
   | HTTPS: api.koready.cloud
   v
Route 53 alias
   |
   v
CloudFront
   |
   | HTTP: origin Host = EB domain
   v
Elastic Beanstalk single instance
```

- 인증서는 `us-east-1` ACM에서 발급한다. CloudFront 인증서는 반드시 이 리전에 있어야 한다.
- CloudFront는 HTTP 요청을 HTTPS로 리다이렉트한다.
- API 캐시는 비활성화한다. 사용자별 응답이나 갱신된 데이터가 섞이거나 오래 남지 않는다.
- `Authorization`, 쿼리 문자열, 쿠키를 원본으로 전달한다.
- 사용자 요청의 `Host` 헤더는 전달하지 않는다. EB가 자신의 도메인으로 요청을 받도록 한다.
- EB 원본 연결은 현재 단일 인스턴스 구성에 맞춰 HTTP를 사용한다. 외부 구간은 HTTPS다.

## 배포

ACM 인증서가 `ISSUED` 상태이고 Route 53 호스팅 영역이 준비된 뒤 실행한다.

```powershell
aws cloudformation deploy `
  --region us-east-1 `
  --stack-name koready-api-edge `
  --template-file infra/aws/api-edge-https.yaml `
  --parameter-overrides `
    ApiDomainName=api.koready.cloud `
    HostedZoneId=Z027386622UXDFXGIOPU3 `
    CertificateArn=<ACM_CERTIFICATE_ARN> `
    OriginDomainName=koready-staging-eb.eba-qqeyievg.ap-northeast-2.elasticbeanstalk.com `
    EnvironmentName=staging `
  --tags Project=KoReady Environment=staging
```

CloudFront는 전역 서비스이므로 스택은 `us-east-1`에 생성한다. 원본 EB와 애플리케이션
리소스는 계속 `ap-northeast-2`를 사용한다.

## 검증

```powershell
curl.exe -I http://api.koready.cloud/actuator/health/readiness
curl.exe https://api.koready.cloud/actuator/health/readiness
curl.exe -I https://api.koready.cloud/swagger-ui/index.html
```

다음 항목을 함께 확인한다.

1. HTTP 요청이 HTTPS로 리다이렉트된다.
2. readiness 응답이 `UP`이다.
3. Swagger UI와 OpenAPI 문서가 HTTP 200으로 열린다.
4. 임의의 프론트 개발 Origin에서 공개 GET과 OPTIONS 사전 요청이 허용된다.
5. 인증이 필요한 API는 CORS가 허용돼도 기존 인증 정책에 따라 401을 반환한다.

## 비용과 운영 주의

- 이 구성은 HTTPS를 위해 별도 Application Load Balancer를 추가하지 않는다.
- Route 53 호스팅 영역과 CloudFront 전송량·요청량에 따른 비용은 발생할 수 있다.
- CloudFront를 제거하기 전에 `api.koready.cloud`가 다른 배포 환경을 가리키는지 확인한다.
- EB 인스턴스의 공인 IP는 EB가 관리하며 DNS 고정 IP로 사용하지 않는다. 별도 Elastic IP가
  할당된 경우에만 연결 대상을 확인한 뒤 해제한다.
