# Readwith

<br>

## 0. 프로젝트 설명

Readwith는 **등장인물 관계를 시각화해 복잡한 소설을 더 쉽게 읽도록 돕는 서비스**입니다. 고전문학이나 장편소설을 읽다 보면 인물이 많아질수록 누가 누구와 어떤 관계였는지 놓치기 쉽고, 앞부분을 다시 찾아보는 일도 잦아집니다.

기존의 작품 분석 서비스는 책 전체 내용을 한꺼번에 보여줘 아직 읽지 않은 내용까지 노출하는 경우가 많았습니다. Readwith는 사용자가 읽은 위치까지만 관계 변화를 보여주고, 인물별 시점 요약과 그래프를 함께 제공해 스포일러 없이 작품의 흐름을 따라갈 수 있도록 만들었습니다.

이 기능을 만들려면 AI가 찾아낸 관계 변화가 책의 어느 위치에서 일어났는지 다시 연결해야 했습니다. 하지만 EPUB은 책마다 HTML 구조가 다르고, 분석을 위해 본문을 TXT로 정리하면 원래 위치 정보도 사라집니다. Readwith 백엔드는 이 문제를 해결하기 위해 EPUB을 공통된 형태로 정리하고, 원본과 분석 텍스트를 오갈 수 있는 위치 체계를 만들었습니다.

> 4인 팀에서 아이디어 제안과 PM, 백엔드·인프라를 맡았으며 졸업작품전시회 최우수상을 수상했습니다.

<br>

## 1. 아키텍처

<img width="811" height="386" alt="readwith drawio" src="https://github.com/user-attachments/assets/6bf2108b-ced2-46e7-845b-9e51edff4e30" />

책을 업로드하면 목차와 XHTML을 분석해 reader와 AI가 함께 사용할 chapter로 정리합니다. reader용 본문과 AI 분석용 텍스트를 만들 때 같은 위치 정보를 남겨두고, AI가 추출한 관계 변화는 이벤트별로 저장합니다. 사용자가 책을 읽을 때는 현재 위치까지 쌓인 변화만 합쳐 관계 그래프를 만듭니다.

정규화나 이미지 생성처럼 오래 걸리는 작업은 요청이 끝날 때까지 기다리게 하지 않습니다. 먼저 작업을 등록한 뒤 상태와 로그를 남기며 처리하고, 실패한 경우 어디에서 멈췄는지 확인해 다시 진행할 수 있도록 했습니다.

<br>

## 2. 사용 기술

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 4.1.0, Spring Data JPA, Spring Security |
| Database | MySQL, Flyway, HikariCP |
| Auth | Google OAuth2, JWT |
| EPUB | Jsoup, EPUB Normalization Pipeline, Locator |
| AI | Spring AI 2.0.0, OpenAI, GPT Image |
| Storage | AWS S3, CloudFront, Presigned URL |
| Runtime | Docker, Render |
| Test / Docs | JUnit, Gutenberg Regression Test, Swagger/OpenAPI |

<br>

## 3. 주요 문제 해결

### 3-1. EPUB·분석 텍스트 간 위치 연결

AI에 EPUB의 HTML을 그대로 넣으면 불필요한 태그가 많아지고 분석할 텍스트도 커집니다. 그래서 본문만 정리한 TXT를 사용했지만, 이 과정에서 문장이 원래 EPUB의 어디에 있었는지 알 수 없게 됐습니다. AI가 관계 변화를 찾아도 reader 화면의 위치와 연결할 수 없는 상태였습니다.

**주요 변경 사항**

- `chapterIndex + blockIndex + offset` 기반 Locator 구성
- 정규화 시 문단별 시작 위치와 길이를 `meta.json`에 기록
- `LocatorResolutionService`에서 Locator와 TXT 기준 위치 간 양방향 변환

진행률, 북마크, 인물 이벤트와 관계 그래프가 같은 위치 기준을 사용하도록 연결했습니다.

[관련 PR #83](https://github.com/Read-With/BE/pull/83)

<br>

### 3-2. EPUB 구조 정규화와 공통 분석 단위 구성

처음에는 EPUB의 spine 항목 하나를 chapter 하나로 봤습니다. 하지만 하나의 XHTML에 여러 장이 들어 있는 책은 내용이 지나치게 크게 묶였고, 반대로 목차 항목을 그대로 나누면 전집이나 희곡이 수백 개의 작은 chapter로 쪼개졌습니다.

**주요 변경 사항**

- 목차 fragment를 활용한 실제 장 경계 탐색
- `nav → ncx → heading` 순서의 경계 탐색 기준 적용
- 목차·색인·라이선스 페이지 제외 및 짧은 단위 병합
- 한 권당 최대 20개의 canonical chapter 구성
- 정규화 규칙 버전 저장을 통한 재처리 대상 판단

원작 목차를 그대로 복제하기보다 reader와 AI가 같은 분석 단위를 사용하도록 설계했습니다. AI 처리량까지 고려해 정규화 규칙 v2의 병합 기준을 정했습니다.

[관련 PR #104](https://github.com/Read-With/BE/pull/104)

<br>

### 3-3. 독서 본문과 AI 분석 파일의 접근 범위 분리

reader가 사용하는 본문은 빠르게 열려야 하지만, AI 분석에 쓰는 중간 파일까지 모두 공개할 필요는 없습니다.

**주요 변경 사항**

- 독서 본문 `combined.xhtml`의 CloudFront 제공
- `meta.json`과 chapter별 TXT의 presigned URL 제공
- DB에 배포 도메인 대신 파일 기준 경로 저장
- 정규화 완료 후 본문 우선 제공, 관계 분석 완료 전 그래프·요약은 빈 값 반환

책 읽기와 AI 분석의 완료 시점을 분리해, 분석이 끝나기 전에도 본문을 읽을 수 있도록 했습니다.

<br>

### 3-4. 관계 변화의 이벤트 단위 저장

인물 관계는 사건이 일어날 때마다 조금씩 바뀝니다. 매번 전체 그래프를 저장하면 이미 있던 인물과 관계가 계속 반복되고, 어떤 사건 때문에 관계가 달라졌는지도 찾기 어려웠습니다.

**주요 변경 사항**

- `relationship-delta-v1` 형식으로 이벤트별 관계 변경분 저장
- 독서 위치까지의 이벤트를 순서대로 합쳐 현재 그래프 구성
- 같은 이벤트의 분석 결과 재업로드 시 기존 내용 교체
- 이벤트·인물 방향 조합에 unique constraint 적용

전체 그래프를 반복 저장하던 구조를 변경분 중심으로 바꾸고, 독서 위치에 맞는 관계를 조회하도록 구성했습니다.

[Delta 업로드와 조회 #123](https://github.com/Read-With/BE/pull/123) · [관계 중복 방지 #136](https://github.com/Read-With/BE/pull/136)

<br>

### 3-5. 대용량 분석 파일의 비동기 처리

관계 분석 파일을 요청 안에서 한꺼번에 읽고 저장하면 메모리 사용량이 커지고, 같은 시간에 실행되는 EPUB 정규화에도 영향을 줬습니다.

**주요 변경 사항**

- 분석 파일의 S3 비공개 경로 저장 후 `202 Accepted` 반환
- 서버에서 파일을 하나씩 읽어 비동기 처리
- 기존 정규화 작업 테이블에 상태와 로그 기록
- 같은 책의 분석 중복 실행 방지
- 전체 파일 처리 완료 후 책의 분석 완료 상태 변경

실패한 입력 파일은 바로 삭제하지 않고 남겨, 원인을 확인한 뒤 다시 처리할 수 있도록 했습니다.

[관련 PR #138](https://github.com/Read-With/BE/pull/138)

<br>

### 3-6. S3 업로드와 DB 트랜잭션 분리

EPUB 원본과 표지를 S3에 올리는 동안 DB 트랜잭션도 계속 열린 채로 남아 있었습니다. 업로드가 겹치면 적은 커넥션을 오래 차지해 다른 요청까지 느려질 수 있었습니다.

**주요 변경 사항**

- S3 파일 업로드를 DB 트랜잭션 밖으로 분리
- 책 정보 저장과 정규화 작업 등록 구간의 트랜잭션 최소화
- 원본 업로드·DB 반영 실패 시 정규화 중단 및 `FAILED` 상태 기록
- 표지 업로드 실패 시 표지 없이 본문 처리 계속 진행

본문 처리에 필수인 작업과 부가 작업의 실패 범위를 나눠, 표지 업로드 실패가 책 전체 처리를 막지 않도록 했습니다.

[관련 PR #144](https://github.com/Read-With/BE/pull/144)

<br>

### 3-7. 캐릭터 이미지 스타일 통일과 재생성 비용 절감

처음에는 등장인물을 등록하면 모든 이미지를 바로 생성했습니다. 대표 이미지의 스타일이 마음에 들지 않으면 나머지 이미지도 모두 다시 만들어야 했고, 한 책 안에서도 그림체가 달라지는 문제가 있었습니다.

**주요 변경 사항**

- 대표 이미지 후보 생성 후 관리자 선택 단계 추가
- 선택된 이미지를 기준으로 나머지 캐릭터 생성
- 장시간 이미지 생성의 비동기 처리 및 서버 재시작 후 작업 재확인
- OpenAI Batch 성공 후 S3·DB 게시 실패 시 게시 단계만 재시도

대표 스타일을 먼저 확정하고 나머지 이미지를 생성하도록 순서를 바꿨습니다. 게시 단계에서 실패한 경우에는 이미 과금된 생성 결과를 다시 사용하도록 했습니다.

[Batch 이미지 생성 #150](https://github.com/Read-With/BE/pull/150) · [결과 재게시 #152](https://github.com/Read-With/BE/pull/152) · [대표 후보 비동기 생성 #154](https://github.com/Read-With/BE/pull/154)

<br>

## 4. 512MB 환경의 메모리 최적화

Render 무료 환경의 512MB 메모리에서 서버가 `status 137`로 종료되는 문제가 있었습니다. 단순히 heap 하나만 줄이지 않고, 동시에 실행되는 작업과 DB 커넥션까지 함께 조정했습니다.

| 영역 | 현재 설정 |
| --- | --- |
| JVM | `Xms64m / Xmx256m`, Serial GC, processor 1 |
| Database | HikariCP `maximum-pool-size: 2`, `minimum-idle: 1` |
| Async | 이미지 생성과 EPUB 정규화를 한 번에 하나씩 실행 |
| JPA | `default_batch_fetch_size: 100` |
| API Docs | 운영 환경에서는 Swagger 기본 비활성화 |

이미지 업로드 과정에서 byte 배열을 base64로 바꿨다가 다시 되돌리던 과정도 제거했습니다. 이 설정으로 작은 서버에서 한 번에 많은 일을 처리하려 하기보다, 적은 작업을 끝까지 안정적으로 처리하는 쪽을 선택했습니다.

[관련 PR #140](https://github.com/Read-With/BE/pull/140)

<br>

## 5. 검증

- Gutenberg EPUB 표본 12종 모두 정규화 성공
- EPUB 위치를 분석용 위치로 바꾼 뒤 다시 원래 위치로 돌아오는 round-trip 실패 0건
- `combined.xhtml`의 section 수와 `meta.json`의 chapter 수가 일치하는지 확인
- 목차·라이선스 같은 placeholder가 본문 chapter에 들어오지 않는지 확인
- 같은 관계가 중복 저장되지 않고 이벤트 순서대로 그래프가 만들어지는지 테스트
- 작업 중복 실행, 서버 재시작 후 재개, 부분 성공과 실패 상태 테스트
