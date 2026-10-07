<!--
Sync Impact Report
- Version change: (template) → 1.0.0
- Added principles: I~VIII (전부 신규)
- Added sections: 기술 제약, 개발 워크플로·품질 게이트, Governance
- Templates requiring updates: .specify/templates/plan-template.md ✅ (Constitution Check는 이 문서를 참조하므로 수정 불필요)
- Source: docs/01-common-requirements.md §2·§3, docs/02-architecture.md §1·§2·§5·§6
- Follow-up TODOs: Spring Boot 버전 확정(02 §2 "팀 확정"), CSRF 토큰 저장 방식(02 §5, M17)
-->

# 팀 공통 블로그 플랫폼 Constitution

강성찬·나민서·김민서 세 사람이 하나의 공통 기반(아키텍처·ERD·업무 규칙) 위에 각자의 블로그 서비스(친구 공개형·티스토리형·Velog형)를 **추가만** 하는 방식으로 만든다. 이 문서는 모든 스펙·계획·구현이 지켜야 하는 원칙이다. 상세 근거는 [docs/01](../../docs/01-common-requirements.md), [docs/02](../../docs/02-architecture.md)에 있다.

## Core Principles

### I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 한다
- 공통 ERD(기준선: 통합 V1, [docs/51](../../docs/51-erd-unified.md))의 테이블·컬럼은 바꾸지 않는다. 개인 확장은 새 테이블·nullable 컬럼·새 모듈 패키지로만 붙인다.
- 개인 확장을 더해도 공통 완료 기준(Tier A·B의 C-XXX)은 그대로 만족해야 한다.
- 이유: 세 서비스가 같은 Service·테스트를 공유하고, 한 사람의 확장이 다른 사람의 기능을 깨지 않게 하기 위해서다.

### II. 하나의 저장소, 하나의 배포 단위 (모듈러 모놀리스)
- MSA는 금지한다. 기능 모듈(account·post·tag·media·interaction·discovery·shared)은 다른 모듈의 Repository·테이블을 직접 쓰지 않고, 공개된 Service 또는 도메인 이벤트로만 소통한다.
- 권한·상태 전이·검증 같은 업무 규칙은 Service 계층에 두고 공통으로 쓴다. 컨트롤러·화면·OpenAPI 명세는 각자 쓴다.

### III. 권한은 두 겹, 볼 수 없는 것은 404 (NON-NEGOTIABLE)
- 화면에서 숨기는 것만으로 막지 않는다. 모든 쓰기·읽기 권한은 서버 Service에서 다시 검사한다.
- 현재 사용자는 세션에서만 꺼내며, 작성자 ID를 요청 파라미터로 받지 않는다.
- 남의 비공개·임시 리소스와 존재하지 않는 리소스는 똑같이 **404**로 응답한다(링크 미리보기 포함). 권한 판단의 기준은 [docs/42 권한 매트릭스](../../docs/42-permission-matrix.md)다.
- 공개가 아닌 글은 홈·블로그·태그·검색·피드·sitemap 어디에도 나오지 않는다.

### IV. 사용자 콘텐츠는 실행되지 않는다
- 본문은 Markdown 원문을 저장하고, HTML은 서버의 렌더러 하나만 만들며 반드시 허용 목록 방식으로 정화한다. 직접 쓴 HTML은 글자로 보인다.
- 제목·소개·댓글·닉네임은 글자만(이스케이프) 다룬다. CSP·보안 헤더를 항상 건다.
- 비밀값(OAuth Secret, SMTP, 저장소 키)은 환경 변수로만 주입한다.

### V. 부가 기능의 실패는 글쓰기·읽기를 막지 않는다
- 알림·AI·조회수·트렌딩 집계 등이 실패해도 글 작성·발행·읽기는 성공한다. 부가 기능은 커밋 후 도메인 이벤트로 붙인다.
- Redis 장애 시에도 글 읽기는 계속되고, 보안상 확인이 필요한 것(인증·재설정 토큰)만 거부한다([docs/02 §2-1](../../docs/02-architecture.md)).
- AI는 제안만 하고, 사용자가 고른 것만 반영한다.

### VI. 데이터는 잃지 않고, 지울 때는 정책대로 지운다
- 자동 저장은 브라우저 → Redis → DB 3단계로 유실을 막고, 수동 저장·발행은 즉시 DB에 반영한다. 발행 글 수정 중에도 독자에게는 마지막 발행본이 보인다.
- 글 삭제는 휴지통 30일 후 자동 완전 삭제, 회원 탈퇴는 30일 유예 후 익명 처리한다.
- DB 스키마는 Flyway 마이그레이션으로만 바꾼다.

### VII. 수치는 설정값으로
- 태그 최대 개수, 조회수 중복 판정 기준, 페이지 크기 등 사람마다 다를 수 있는 수치는 코드가 아니라 설정값으로 둔다.

### VIII. 권한·데이터 규칙은 실제 DB로 통합 테스트한다
- 권한 관련 기능은 통합 테스트가 필수다. 테스트 DB는 H2가 아닌 실제 PostgreSQL(Testcontainers)을 쓴다.
- 각 스펙의 인수 시나리오(Given/When/Then)는 테스트로 옮길 수 있어야 한다.

## 기술 제약

| 영역 | 공통 선택 |
|---|---|
| 서버 | Java 21, Spring Boot(Maven, 버전 팀 확정), Spring Data JPA |
| 화면 | React + REST API, 같은 도메인에서 서빙. 글 상세·블로그 주소는 서버가 링크 미리보기 메타와 404 상태 코드를 넣는다 |
| 인증 | 이메일 가입 + Google + GitHub, 세션 쿠키(Spring Session + Redis) + CSRF 토큰. 공통 코드는 JWT를 쓰지 않는다 |
| 저장소 | PostgreSQL(pg_trgm), Redis(복제 + 자동 전환), MinIO(S3 API, Presigned URL) |
| 실행 | Docker Compose (app + PostgreSQL + Redis + MinIO) |
| API 규약 | `/api/...` 복수형 명사, 내 리소스는 `/api/me/...`, 켜고 끄는 상태는 `PUT`/`DELETE`, 공통 오류 본문 `{code, message, errors, details}`, 불투명 커서 |

비기능 최소선: 목록·상세 서버 응답 300ms 이내(글 1만 건), 목록 쿼리 수가 글 수에 비례하지 않음(N+1 금지), 좋아요 동시 요청도 1건, 375px~데스크톱 가로 스크롤 없음, 공개 글만 sitemap·OG 메타.

## 개발 워크플로·품질 게이트

- 기능은 Spec Kit 흐름(`/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement` → `/speckit-converge`)으로 진행한다. 스펙은 `specs/NNN-기능/`에 둔다.
- `/speckit-plan`의 Constitution Check는 위 원칙 I~VIII을 하나씩 확인한다. 위반이 필요하면 plan의 Complexity Tracking에 이유를 적는다.
- 원문 설계 문서(`docs/`)와 스펙이 다르면, 회의 결정 날짜가 더 최근인 쪽을 따르고 다른 쪽을 고친다.
- Tier A → Tier B → Tier C 순서로 구현한다. Tier C의 화면·기능 우선순위는 1차 공통이 끝난 뒤 다시 정한다.

## Governance

- 이 Constitution은 다른 모든 관례보다 우선한다. 개정은 세 사람의 합의로 하고, 개정 시 버전과 날짜를 올리며 영향받는 스펙·템플릿을 함께 고친다.
- 버전: 원칙 삭제·의미 변경은 MAJOR, 원칙·섹션 추가는 MINOR, 문구 정리는 PATCH.
- 모든 PR 리뷰는 이 원칙 준수를 확인한다.

**Version**: 1.0.0 | **Ratified**: 2026-10-07 | **Last Amended**: 2026-10-07
