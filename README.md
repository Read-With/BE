# Readwith

## 0. 프로젝트 설명

Readwith는 **LLM 기반 등장인물 관계 시각화로 독서를 돕는 독서 지원 서비스**입니다. 프로젝트는 고전문학이나 장편소설처럼 등장인물이 많고 관계 변화가 복잡한 작품을 읽을 때, 독자가 맥락을 놓치기 쉽고 이미 읽은 부분을 다시 돌아가며 확인해야 한다는 문제에서 시작했습니다.

기존 요약형 독서 도구가 작품 전체를 한 번에 분석해 결말 중심 정보나 스포일러를 노출하는 한계가 있었다면, Readwith는 **챕터·이벤트 단위로 인물 관계 변화를 추적하고 시각화**해서 사용자가 읽은 범위 안에서만 작품을 탐색적으로 이해할 수 있도록 돕는 것을 목표로 했습니다.

- 등장인물과 관계를 노드·간선 그래프로 시각화해 작품 이해를 지원
- 관계의 강도와 감정 변화를 시간 흐름에 따라 추적
- 인물 관점 요약과 인터랙티브 그래프를 통해 몰입형 독서 경험 제공
- 사용자가 읽은 범위까지만 정보를 노출해 스포일러를 최소화

### 해결하려는 사용자 문제

- 복잡한 인물 관계와 변화 때문에 줄거리와 맥락 파악이 어려운 문제
- 작품을 이해하려고 앞부분을 반복해서 다시 읽어야 하는 문제
- 기존 분석 도구가 작품 전체 정보를 한 번에 보여줘 스포일러를 유발하는 문제

## 1. 아키텍처
<img width="811" height="386" alt="readwith drawio" src="https://github.com/user-attachments/assets/6bf2108b-ced2-46e7-845b-9e51edff4e30" />



## 2. 사용 기술

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 3.3.1, Spring Data JPA, Spring Security |
| Database | MySQL, Flyway, HikariCP |
| Auth | Google OAuth2, JWT |
| Content Processing | EPUB Normalization Pipeline, Locator Model, Jsoup |
| AI / Async | Spring AI, OpenAI, DALL-E, Async Executor |
| Infra | AWS S3, CloudFront, Presigned URL |
| Docs / Test | Swagger/OpenAPI, JUnit |

## 3. 문제 해결 내용

### 3-1. EPUB를 `txt`로 변환하는 순간 위치 좌표가 사라지는 문제

기존 EPUB 위치 체계에서는 분석을 위해 본문을 `txt`로 변환하는 과정에서 좌표 정보가 끊겼습니다. 이 상태로는 진행률, 북마크, 이벤트 위치를 리더 화면과 안정적으로 연결하기 어려웠습니다. 이를 해결하기 위해 `chapterIndex + blockIndex + offset` 조합의 `locator`를 설계했고, 저장·조회 단계에서는 `locator + txtOffset`을 함께 다루는 구조로 바꿨습니다. 설계는 미니 프로젝트로 먼저 검증한 뒤 Readwith 본 프로젝트에 이식했습니다.

### 3-2. 책마다 제각각인 EPUB HTML 구조 때문에 위치 계산이 흔들리는 문제

Project Gutenberg에 등록된 EPUB들은 책마다 XHTML 태그 구조와 spine 구성이 달라서, 동일한 방식으로 위치를 계산하면 결과가 쉽게 흔들릴 수 있었습니다. 이를 해결하기 위해 `NormalizationPipelineService`를 중심으로 정규화 파이프라인을 설계했고, spine 해석, 본문 sanitize, paragraph 추출, chapter artifact 생성, `meta.json` 작성, validation report 생성까지 한 흐름으로 묶었습니다. 이후 `LocatorResolutionService`가 이 메타를 읽어 locator와 txtOffset을 상호 변환하도록 구성했습니다.

### 3-3. 외부 AI 이미지 생성 API가 트랜잭션 안에 들어가 DB 커넥션을 오래 점유하는 문제

AI 이미지 생성, 파일 다운로드, S3 업로드 같은 외부 I/O가 트랜잭션 안에 섞이면 커넥션 점유 시간이 길어지고, 저사양 환경에서는 더 빠르게 병목이 발생할 수 있었습니다. 그래서 외부 호출과 DB 반영을 분리하고, 실제 저장 작업만 짧은 트랜잭션으로 처리하는 방향으로 구조를 개선했습니다.

### 3-4. 저사양 서버 환경에서 메모리, 커넥션, 스레드를 함께 줄여야 했던 문제

t2.micro 급 환경을 전제로 운영하다 보니 기본 설정만으로는 안정적인 동작이 어려웠습니다. HikariCP를 `8/2` 수준으로 줄이고, JVM heap을 `256MB~512MB`로 제한했으며, 이미지 생성과 정규화 executor도 작은 풀로 조정했습니다. 운영 환경에서는 스왑 메모리까지 설정해 OOM 위험을 낮추는 방향으로 대응했습니다.

### 3-5. 리더와 분석 서버가 서로 다른 방식으로 자산을 소비해야 하는 문제

리더는 빠르게 읽을 수 있는 공개 자산이 필요했고, AI 분석 서버는 민감한 중간 산출물을 안전하게 내려받을 수 있어야 했습니다. 그래서 `combined.xhtml`은 `public prefix + CloudFront` 경로로 제공하고, `meta.json`과 챕터별 `chapter_*.txt`는 `private prefix + presigned URL`로 내려주는 구조를 설계했습니다. 같은 S3를 사용하더라도 소비 주체와 도메인 성격에 따라 접근 방식을 분리한 셈입니다.

### 3-6. CRUD 분석 결과 읽기 API 비중이 높아 캐싱 후보를 분리할 필요가 있었던 점

API 흐름을 CRUD 관점에서 분석해 보니 생성·수정보다 읽기 API 비중이 훨씬 높고, 그중에서도 사용자 상태에 의존하지 않는 응답이 적지 않았습니다. 그래서 manifest처럼 사용자 비의존적인 읽기 응답은 후속적으로 캐싱을 도입할 수 있는 후보로 분리해 두었습니다. 아직 README 기준으로는 계획 단계이지만, 트래픽 특성을 보고 확장 지점을 먼저 정리했다는 점이 의미 있었습니다.

### 3-7. 검증 가능한 결과

- Gutenberg EPUB 샘플 12종 회귀 테스트에서 `성공 12 / 실패 0 / round-trip failure 0`
- HikariCP `maximum-pool-size: 8`, `minimum-idle: 2`
- JVM Heap `Xms256m / Xmx512m`
- 이미지 생성 executor `core 2 / max 3 / queue 50`
- 정규화 executor `core 1 / max 2 / queue 20`
- 비공개 artifact presigned URL TTL `1시간`
- 이미지 생성 요청 간 지연 `2000ms`, 배치 크기 `10`
