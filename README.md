# Readwith

## 0. 프로젝트 설명

Readwith는 **사용자가 읽은 위치까지만 등장인물 관계 변화를 시각화하는 독서 지원 서비스**입니다. 고전문학이나 장편소설에서 복잡한 인물 관계를 이해하려고 앞부분을 반복해서 확인해야 하는 문제와, 작품 전체를 한 번에 분석하는 기존 도구가 스포일러를 노출하는 문제에서 시작했습니다.

백엔드에서는 EPUB마다 다른 문서 구조를 reader와 AI가 함께 사용할 수 있는 **canonical 콘텐츠와 Locator**로 변환하고, AI가 추출한 관계 변화를 **Delta 이벤트**로 저장해 독서 진행 위치에 맞는 그래프를 복원합니다. 정규화, 관계 데이터 적재와 이미지 생성은 비동기 processing job으로 관리해 실패 추적과 재처리가 가능한 파이프라인으로 구성했습니다.

| 구분 | 내용 |
| --- | --- |
| 팀 구성 | 4명 |
| 역할 | 아이디어 제안, PM, 백엔드·인프라 |
| 주요 영역 | EPUB 정규화, Locator, 관계 Delta, 비동기 processing job |
| 성과 | 졸업작품전시회 최우수상 |

### 핵심 사용자 가치

- 사용자의 현재 독서 진도까지만 관계 변화와 인물 정보를 노출
- 관계의 강도와 감정 변화를 이벤트 순서에 따라 그래프로 복원
- 인물 관점 요약과 인터랙티브 그래프로 복잡한 작품의 맥락 이해 지원

## 1. 아키텍처

<img width="811" height="386" alt="readwith drawio" src="https://github.com/user-attachments/assets/6bf2108b-ced2-46e7-845b-9e51edff4e30" />

핵심 콘텐츠 흐름은 다음과 같습니다.

```text
EPUB 업로드
  -> TOC·XHTML 구조 분석
  -> canonical chapter 정규화
  -> combined.xhtml / meta.json / chapter txt 생성
  -> AI 관계 분석
  -> relationship delta 적재
  -> 사용자 Locator까지 누적한 관계 그래프 응답
```

장시간 작업은 HTTP 요청에서 직접 완료를 기다리지 않고 상태와 로그를 가진 job으로 처리합니다.

```text
202 Accepted -> QUEUED -> RUNNING -> READY / FAILED
                         \-> 처리 로그·실패 원인·재시도
```

## 2. 사용 기술

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 4.1.0, Spring Data JPA, Spring Security |
| Database | MySQL, Flyway, HikariCP |
| Auth | Google OAuth2, JWT |
| Content Processing | EPUB Normalization Pipeline, Canonical Locator, Jsoup |
| AI / Async | Spring AI 2.0.0, OpenAI, GPT Image, Async Executor |
| Storage / Delivery | AWS S3, CloudFront, Presigned URL |
| Runtime | Docker, Render |
| Docs / Test | Swagger/OpenAPI, JUnit, Gutenberg Regression Test |

## 3. 핵심 문제 해결

### 3-1. EPUB을 분석용 TXT로 변환하면 원본 위치가 사라지는 문제

AI에는 토큰 사용과 분석 품질을 고려해 EPUB의 HTML 원문이 아닌 정제된 텍스트를 전달합니다. 하지만 정제 과정에서는 DOM 위치와 태그 정보가 사라져, AI가 추출한 관계 변화가 실제 reader의 어느 위치에서 발생했는지 다시 연결할 수 없었습니다.

이를 해결하기 위해 `chapterIndex + blockIndex + offset` 구조의 canonical Locator를 설계했습니다. 정규화 과정에서 문단별 시작 위치와 길이를 `meta.json`에 기록하고, `LocatorResolutionService`가 Locator와 `txtOffset`을 양방향으로 변환합니다. 진행률, 북마크, 이벤트와 관계 그래프가 특정 EPUB의 DOM 구조에 직접 의존하지 않고 같은 위치 언어를 사용하도록 구성했습니다.

- 관련 PR: [#83 EPUB 정규화·좌표 엔진 이식](https://github.com/Read-With/BE/pull/83)

### 3-2. 책마다 다른 chapter 구조를 reader와 AI의 공통 단위로 만드는 문제

`spine item 1개 = chapter 1개` 방식은 하나의 XHTML에 여러 장이 들어 있는 책을 과도하게 합쳤습니다. 반대로 TOC leaf를 그대로 chapter로 사용하면 전집, 희곡과 장시에서 수백 개 단위로 잘게 쪼개졌습니다.

정규화 규칙 v2에서는 먼저 TOC fragment로 정확한 경계를 찾은 뒤, reader와 AI가 함께 사용할 canonical chapter로 재병합했습니다.

```text
nav 우선 / ncx fallback / heading heuristic fallback
 -> TOC fragment split
 -> frontmatter·contents·license·index 제외
 -> 짧은 unit 및 scene 병합
 -> canonical chapter 생성
```

원작 목차를 그대로 복제하기보다 reader 표시와 AI 분석이 동일한 단위를 공유하도록 했고, 분석 처리량을 고려해 canonical chapter를 최대 20개로 병합하는 정책을 적용했습니다. `ruleVersion`을 저장해 규칙 변경 이후 기존 산출물을 `OUTDATED`로 식별할 수 있습니다.

- 관련 PR: [#104 EPUB 정규화 규칙 v2](https://github.com/Read-With/BE/pull/104)

### 3-3. reader와 AI 분석 서버가 서로 다른 자산 접근 방식을 요구하는 문제

reader는 본문을 빠르게 열 수 있어야 하지만, AI 분석용 중간 산출물을 모두 공개할 필요는 없습니다. 같은 S3 안에서도 소비 주체와 데이터 성격에 따라 접근 경계를 분리했습니다.

- `combined.xhtml`: public prefix에 저장하고 CloudFront URL로 제공
- `meta.json`, `chapter_*.txt`: private prefix에 저장하고 presigned URL로 제공
- DB에는 배포 도메인이 포함된 절대 URL 대신 artifact root와 key 저장
- 정규화가 완료되면 본문 읽기를 허용하고, 분석 전에는 그래프·요약 응답을 분리

이 구조로 reader 제공 경로와 AI ingestion 계약을 독립적으로 변경할 수 있게 했습니다.

### 3-4. 관계 그래프 전체를 반복 저장하던 구조를 Delta로 전환

인물 관계는 독서 진행에 따라 계속 변합니다. 매 이벤트마다 전체 그래프 스냅샷을 저장하면 같은 노드와 간선이 반복되고, 어떤 사건에서 관계가 바뀌었는지 추적하기 어렵습니다.

`relationship-delta-v1` 계약을 정의해 이벤트별 변화만 저장하고, 조회 시 이벤트 순서대로 fold하여 현재 그래프를 복원하도록 변경했습니다.

- raw relationship delta와 누적 graph 조회 경로 분리
- 이벤트 순서에 따라 `evidenceCount`, `positivity`, `labels`, `latestReason`, `directionCounts` 누적
- 동일 이벤트의 재업로드는 기존 Delta를 교체해 결과를 멱등하게 유지
- `(event_id, from_char_id, to_char_id)` unique constraint로 같은 방향의 edge 중복 저장 차단

- 관련 PR: [#123 relationship delta 업로드·조회](https://github.com/Read-With/BE/pull/123), [#136 relationship edge 중복 저장 방지](https://github.com/Read-With/BE/pull/136)

### 3-5. 큰 관계 분석 파일을 제한된 메모리에서 안정적으로 적재하는 문제

대용량 multipart 요청을 한 번에 메모리에 올리고 동기 처리하면 제한된 서버 자원에서 다른 요청과 정규화 작업까지 영향을 받습니다. 관계 Delta 적재를 비동기 job으로 분리했습니다.

- 요청 파일을 private S3 staging prefix에 저장하고 `202 Accepted` 반환
- S3 파일을 하나씩 stream 파싱해 replace 저장
- 같은 책의 활성 분석 job 중복 실행 차단
- `processing_job`, `processing_job_log`를 정규화와 관계 적재에서 공통 사용
- 완료 시점에만 책의 분석 상태 갱신
- 실패 분석과 재처리를 위해 입력 artifact와 처리 로그 보존

- 관련 PR: [#138 relationship delta 업로드 job화](https://github.com/Read-With/BE/pull/138)

### 3-6. 외부 파일 I/O가 EPUB 업로드 트랜잭션을 오래 점유하는 문제

EPUB source와 표지 S3 업로드가 DB 트랜잭션 안에 포함되면 동시 업로드 시 커넥션을 오래 점유합니다. 업로드 오케스트레이션 전체는 트랜잭션 없이 실행하고, 책 생성, artifact 반영, job 생성과 실패 상태 반영만 짧은 트랜잭션으로 분리했습니다.

source staging이나 DB 확정이 실패하면 정규화 job을 dispatch하지 않고 책을 `FAILED`로 남기는 보상 흐름도 추가했습니다. 표지 업로드 실패는 본문 처리 전체를 실패시키지 않고 표지 없이 계속 진행하도록 실패 범위를 구분했습니다.

- 관련 PR: [#144 EPUB 업로드 트랜잭션 범위 축소](https://github.com/Read-With/BE/pull/144)

### 3-7. AI 이미지 생성의 비용과 일관성을 관리자 승인형 파이프라인으로 제어

인물 업로드 직후 모든 캐릭터 이미지를 자동 생성하면 스타일 편차를 뒤늦게 발견하고 전체 이미지를 다시 생성해야 합니다. 대표 이미지 후보를 먼저 만들고 관리자가 선택한 reference image를 기준으로 나머지 캐릭터를 fan-out하는 구조로 바꿨습니다.

- 대표 후보 생성과 선택을 비동기 processing job으로 관리
- 선택한 reference와 series-lock prompt를 모든 캐릭터 생성에 공통 적용
- OpenAI Batch를 이용해 fan-out하고 서버 재시작 후에도 제출·조회 재개
- slot별 독립 처리와 부분 성공 판정
- Batch는 성공했지만 게시가 실패한 경우 기존 output을 재사용해 S3·DB 게시만 재시도
- 새 이미지 URL의 DB commit 이후 교체된 S3 객체 삭제

- 관련 PR: [#150 GPT Image Batch fan-out](https://github.com/Read-With/BE/pull/150), [#152 Batch 결과 재게시](https://github.com/Read-With/BE/pull/152), [#154 대표 후보 생성 비동기화](https://github.com/Read-With/BE/pull/154)

## 4. 제한된 런타임 자원 최적화

Render 512MB 환경에서 프로세스가 `status 137`로 종료되는 문제를 기준으로 JVM, DB 커넥션과 작업 동시성을 함께 줄였습니다.

| 영역 | 적용 내용 |
| --- | --- |
| JVM | `Xms64m / Xmx256m`, Serial GC, processor 1, metaspace·direct memory·stack 제한 |
| Database | HikariCP `maximum-pool-size: 2`, `minimum-idle: 1` |
| Async | 이미지 생성과 EPUB 정규화 동시 실행 수 1로 제한 |
| JPA | `default_batch_fetch_size`를 100으로 축소 |
| API Docs | 운영 환경에서 Swagger/OpenAPI 기본 비활성화 |
| Image Upload | 불필요한 base64 재인코딩을 제거하고 byte 배열을 S3에 직접 업로드 |

- 관련 PR: [#140 Render Free 메모리 초과 종료 대응](https://github.com/Read-With/BE/pull/140)

## 5. 검증 결과

- 저장소의 Gutenberg EPUB 회귀 표본 12종 `정규화 성공 12 / 실패 0`
- EPUB 위치에서 분석 위치를 거쳐 다시 원본으로 돌아오는 `round-trip failure 0`
- canonical chapter 수, `combined.xhtml` section 수와 `meta.json` chapter 수 일치 검증
- placeholder title 제외와 `startPos/endPos`, 문단 시작점·길이 구조 검증
- Delta 중복 적재, 이벤트 교체, 순서 기반 fold 결과 테스트
- processing job 상태 전이, 중복 dispatch, 재개와 부분 성공 테스트
- 외부 I/O와 DB 반영 순서 및 실패 보상 흐름 테스트
