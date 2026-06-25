# Render Free 배포 전환 Runbook

## 목표 구조

- Frontend: 기존 배포 유지
- Backend: Render Free Web Service
- DB: Aiven MySQL Free 또는 TiDB Cloud Starter
- File Storage: 기존 AWS S3 유지
- CDN: 기존 CloudFront `https://cdn.readwith.cloud` 유지
- CI/CD: GitHub Actions는 build CI만 수행, Render가 GitHub 연동으로 배포

## 전환 전 백업

AWS 리소스를 내리기 전에 아래 값을 백업한다. secret 원문은 안전한 개인 저장소에만 보관한다.

### Elastic Beanstalk 환경변수

필수:

```env
AWS_DB_URL=
AWS_DB_USERNAME=
AWS_DB_PASSWORD=
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
OPENAI_API_KEY=
JWT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=
OAUTH2_REDIRECT_URI=
CLOUDFRONT_BASE_URL=https://cdn.readwith.cloud
```

이미지 생성:

```env
CHARACTER_IMAGE_MODEL=gpt-image-1
CHARACTER_IMAGE_EDIT_MODEL=gpt-image-1
CHARACTER_IMAGE_QUALITY=medium
CHARACTER_IMAGE_WIDTH=1024
CHARACTER_IMAGE_HEIGHT=1024
CHARACTER_IMAGE_COUNT=1
```

### AWS 리소스 정보

```text
RDS endpoint
RDS database name
RDS username
S3 bucket: readwith-s3-bucket
CloudFront base URL: https://cdn.readwith.cloud
Frontend origin URL
```

## 코드 변경 기준

Render/Koyeb 계열 배포를 위해 애플리케이션은 아래 설정을 지원해야 한다.

```yaml
server:
  port: ${PORT:8080}
```

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

기존 AWS RDS 환경변수는 fallback으로 유지한다.

무료 인스턴스 기본값:

```env
DB_MAX_POOL_SIZE=2
DB_MIN_IDLE=1
JAVA_TOOL_OPTIONS=-Xmx384m -XX:MaxRAMPercentage=75
```

## DB 이전

### 1. RDS dump 생성

```bash
mysqldump -h <AWS_DB_URL> -u <AWS_DB_USERNAME> -p readwith_db --single-transaction --routines --triggers > readwith_dump.sql
```

검증 기준:

```text
readwith_dump.sql 생성 완료
dump 중 에러 없음
파일 크기가 비정상적으로 작지 않음
```

### 2. 무료 MySQL 생성

후보:

```text
Aiven MySQL Free
TiDB Cloud Starter
```

확정할 값:

```env
DATABASE_URL=jdbc:mysql://HOST:PORT/readwith_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=true&requireSSL=true
DATABASE_USERNAME=
DATABASE_PASSWORD=
```

### 3. dump import

```bash
mysql -h <NEW_DB_HOST> -P <PORT> -u <DATABASE_USERNAME> -p <DB_NAME> < readwith_dump.sql
```

검증 SQL:

```sql
SELECT COUNT(*) FROM book;
SELECT COUNT(*) FROM book_character;
SELECT COUNT(*) FROM book_event;
SELECT COUNT(*) FROM chapter;
```

다음 단계 진행 기준:

```text
주요 테이블 row count가 0이 아님
백엔드 시작 시 schema validate 통과
```

## Render 설정

Render Dashboard:

```text
New Web Service
Runtime: Docker
Branch: dev 또는 작업 검증용 branch
Health Check Path: /health
```

Render 환경변수:

```env
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
OPENAI_API_KEY=
JWT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=
OAUTH2_REDIRECT_URI=
CLOUDFRONT_BASE_URL=https://cdn.readwith.cloud
CHARACTER_IMAGE_MODEL=gpt-image-1
CHARACTER_IMAGE_EDIT_MODEL=gpt-image-1
CHARACTER_IMAGE_QUALITY=medium
CHARACTER_IMAGE_WIDTH=1024
CHARACTER_IMAGE_HEIGHT=1024
CHARACTER_IMAGE_COUNT=1
JAVA_TOOL_OPTIONS=-Xmx384m -XX:MaxRAMPercentage=75
DB_MAX_POOL_SIZE=2
DB_MIN_IDLE=1
```

Google OAuth를 Render 백엔드로 사용할 경우:

```env
GOOGLE_REDIRECT_URI=https://<render-service>.onrender.com/login/oauth2/code/google
OAUTH2_REDIRECT_URI=https://<frontend-domain>/auth/callback
```

## S3/CDN 점검

S3는 Render 밖에서도 access key 기반으로 접근 가능해야 한다.

CDN public 파일 확인:

```bash
curl -I https://cdn.readwith.cloud/public/books/13/covers/20260406T205016-34c446ac/cover.jpg
```

기대값:

```text
HTTP/2 200
server: CloudFront
```

백엔드 smoke test:

```text
GET /health
GET /api/books
GET /api/books/{bookId}
GET /api/books/{bookId}/manifest
```

확인 기준:

```text
책 목록 cover URL 표시
manifest combinedXhtmlPath가 cdn.readwith.cloud로 내려옴
private artifact presigned URL 생성 가능
S3 credential error 없음
```

## AWS 정리 순서

새 Render 백엔드와 프론트 연결이 정상일 때만 진행한다.

```text
1. RDS 최종 dump 또는 snapshot 보관
2. Elastic Beanstalk environment terminate
3. RDS stop 또는 delete
4. Load Balancer 삭제 확인
5. Elastic IP 해제 확인
6. EBS volume/snapshot 확인
7. CloudFront 유지
8. S3 유지
9. AWS Budget 알림 설정
```

## 완료 기준

```text
GitHub Actions가 AWS EB 배포를 더 이상 수행하지 않음
Render 배포 성공
/health 200
/api/books 200
새 DB 데이터 조회 정상
S3/CDN public 파일 접근 정상
프론트가 새 백엔드 URL로 정상 동작
AWS 고정비 리소스 정리 계획 확정
```
