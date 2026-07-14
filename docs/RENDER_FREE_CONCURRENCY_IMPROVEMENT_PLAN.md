# Render Free Concurrency Improvement Plan

## 배경

Render Free 플랜은 메모리 제한이 512MB라서 백그라운드 작업을 공격적으로 병렬 처리하면 JVM RSS가 제한을 넘을 수 있다.
PR #140은 우선 OOM 종료를 막기 위해 JVM 메모리와 async worker 수를 보수적으로 낮춘다.

이 문서는 이후 3~4명의 동시 업로드/분석 요청을 더 안정적으로 처리하기 위한 후속 개선 방향을 정리한다.
목표는 worker 수를 바로 늘리는 것이 아니라, worker 수를 안전하게 늘릴 수 있는 구조를 먼저 만드는 것이다.

## 현재 안정화 방향

- Render Free에서는 느리더라도 실패보다 대기를 우선한다.
- EPUB 정규화, relationship delta import, 이미지 생성은 작은 worker 수로 순차 처리한다.
- JVM heap뿐 아니라 metaspace, direct memory, code cache, thread stack까지 제한해 RSS 피크를 예측 가능하게 만든다.
- Swagger/OpenAPI와 큰 Hibernate batch fetch size처럼 무료 플랜에서 부팅/조회 메모리를 키우는 설정은 기본적으로 줄인다.

## 남아 있는 병목

### DB 커넥션 풀 대기

Render Free 기준 `DB_MAX_POOL_SIZE=2`는 메모리에는 안전하지만, 동시 업로드 3~4건에서는 커넥션 대기를 만들 수 있다.
특히 DB 트랜잭션 안에서 S3 업로드나 파일 파싱 같은 외부 I/O를 수행하면 커넥션을 오래 점유한다.

위험한 흐름:

- `BookService.uploadBook()`는 `@Transactional` 범위 안에서 EPUB metadata 추출, S3 source upload, cover upload, job 생성까지 수행한다.
- `RelationshipDeltaImportJobService.queueRelationshipDeltaImport()`는 `@Transactional` 범위 안에서 relationship delta 파일을 S3에 staging한다.
- `AdminImageGenerationService`의 일부 흐름은 OpenAI/S3 호출과 DB 상태 변경이 같은 서비스 트랜잭션 범위에 놓일 수 있다.

이 상황은 DB row deadlock이라기보다는 connection pool starvation에 가깝다.

### Queue rejection 정책

현재 executor는 `CallerRunsPolicy`를 사용한다.
큐가 가득 차면 요청 스레드가 긴 백그라운드 작업을 직접 실행할 수 있어, 요청 timeout 또는 추가 메모리 피크로 이어질 수 있다.

### 작업 유형 간 간섭

EPUB 정규화와 relationship delta import가 같은 executor를 공유한다.
큰 EPUB 정규화가 오래 걸리면 relationship delta import도 같은 큐에서 대기한다.

## 개선 순서

### 1. 외부 I/O를 트랜잭션 밖으로 분리

S3 업로드, OpenAI 호출, EPUB 파싱은 DB 커넥션을 잡은 상태에서 수행하지 않도록 분리한다.
DB 트랜잭션은 상태 저장, job 생성, 상태 전이만 짧게 수행한다.

권장 방향:

- upload 요청에서 파일을 먼저 S3 또는 임시 staging 영역에 저장한다.
- staging 완료 후 짧은 트랜잭션으로 `Book`, `ProcessingJob`, job log만 생성한다.
- 실제 정규화/분석은 after-commit 이후 async worker가 처리한다.
- 실패 시 staged object cleanup을 별도 보상 처리로 둔다.

### 2. Queue full 응답을 명확하게 만든다

무료 플랜에서는 큐가 가득 찼을 때 요청 스레드가 직접 실행하는 것보다 명확한 실패 응답이 낫다.

검토 방향:

- `CallerRunsPolicy` 대신 custom rejection handler를 둔다.
- queue full이면 `429 Too Many Requests` 또는 도메인 에러로 "작업 큐가 가득 찼으니 잠시 후 재시도"를 반환한다.
- 큐 길이, active count, rejected count를 로그로 남긴다.

### 3. Executor를 작업 유형별로 분리

작업 유형별 리소스 성격이 다르므로 executor를 분리한다.

- normalization executor: EPUB 정규화 전용
- relationship analysis executor: relationship delta import 전용
- image generation executor: OpenAI image/S3 upload 전용

Render Free에서는 각 executor worker를 1로 유지하되, queue capacity와 rejection 정책을 별도로 조정한다.
유료 플랜에서는 메모리 로그를 확인한 뒤 작업별로 worker 수를 단계적으로 늘린다.

### 4. 성능 상향은 계측 후 단계적으로 적용

성능 상향은 다음 지표를 확인한 뒤 진행한다.

- Render memory RSS 피크
- GC 빈도와 pause time
- Hikari active/idle/pending connection
- async executor active count와 queue size
- normalization 평균/최대 처리 시간
- upload 요청 평균/최대 응답 시간

## 환경변수 기준

### Render Free 안정 기본값

```env
JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -Xss256k -XX:+ExitOnOutOfMemoryError
DB_MAX_POOL_SIZE=2
DB_MIN_IDLE=1
SPRING_MAIN_LAZY_INITIALIZATION=true
SPRINGDOC_ENABLED=false
HIBERNATE_DEFAULT_BATCH_FETCH_SIZE=100
```

### 메모리 1GB 이상에서 검토 가능한 후보

아래 값은 바로 적용할 기본값이 아니다.
트랜잭션 범위와 queue rejection 정책을 먼저 개선한 뒤, Render 메모리 로그를 확인하면서 단계적으로 검토한다.

```env
JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m -XX:+UseG1GC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -Xss512k -XX:+ExitOnOutOfMemoryError
DB_MAX_POOL_SIZE=3
DB_MIN_IDLE=1
```

## 검증 시나리오

- 동시 EPUB 업로드 3~4건을 요청했을 때 요청이 timeout 대신 정상 queue 또는 명확한 재시도 응답을 받는지 확인한다.
- 정규화 job이 처리 중일 때 relationship delta import 요청이 무기한 대기하지 않는지 확인한다.
- 이미지 생성 중 EPUB 업로드가 DB 커넥션 대기로 실패하지 않는지 확인한다.
- queue full 상황에서 요청 스레드가 직접 긴 작업을 실행하지 않는지 확인한다.
- Render memory RSS가 제한을 넘지 않는지 확인한다.

## 관련 이슈

- #139: Render Free 플랜 메모리 초과 종료 대응
- #141: Render Free 동시 업로드 안정성과 성능 상향 기반 개선
