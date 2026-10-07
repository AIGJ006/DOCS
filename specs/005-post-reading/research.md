# Research: 글 읽기 (전체 글 목록·개인 블로그·글 상세)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-07

표기:
- **확정** — 원문·spec·README "정해진 것"에 이미 결정된 것 (근거 절 번호 표시).
- **제안(팀 확인 필요)** — 원문에 없어 합리적 기본값을 고른 것.
- **미결 — 기본안** — spec(또는 연관 spec)에 `[NEEDS CLARIFICATION]`이 남아 있어 기본안으로 진행하는 것.

spec 005 자체에는 `[NEEDS CLARIFICATION]`과 `## Clarifications` 절이 없다. Technical Context의 NEEDS CLARIFICATION도 없다(Spring Boot 버전은 "3.x 이상, 팀 확정"으로 둔다).

---

## A. 공통 기술 선택 (확정)

### R-01 언어·프레임워크·저장소
- **Decision**: Java 21, Spring Boot(3.x 이상, 팀 확정), Maven, Spring Data JPA, Spring Security, Spring Session Data Redis, Flyway, PostgreSQL, Redis, MinIO(AWS SDK v2), React, Docker Compose. 테스트는 JUnit 5 + Testcontainers + Spring Security Test.
- **Rationale**: 02 §2 기술 스택, 01 Q8, constitution "기술 제약". 이 기능은 읽기 전용이라 렌더러(commonmark-java 0.30.0 + GFM)·정화기(OWASP Java HTML Sanitizer)·MinIO SDK를 직접 호출하지 않는다.
- **Alternatives considered**: H2 테스트 DB — 원칙 VIII로 금지. Thymeleaf SSR — 2026-10-07 H7로 React + REST로 결정되어 제외.

### R-02 화면 방식과 서버 첫 응답 (H7)
- **Decision**: 화면은 React(클라이언트 렌더링), 데이터는 REST API. 단 `/@{handle}`과 `/@{handle}/posts/{postId}`는 서버가 첫 응답에 링크 미리보기 메타와 상태 코드(200·301·302·404)를 넣는다.
- **Rationale**: 01 결정 기록 2026-10-07 Q2·H7, 02 §2·§6 SEO 행, spec FR-043.
- **Alternatives considered**: 전부 클라이언트 렌더링(메타·404 없음) — H7 위반, 메신저 미리보기 불가. 전체 SSR — H7로 폐기.

---

## B. 목록 (확정)

### R-03 정렬·표시 기준
- **Decision**: `first_public_at DESC, id DESC`. 카드 날짜도 `first_public_at`.
- **Rationale**: 10 L-1, 05 P-3, spec FR-002. 비공개로 발행했다 나중에 공개한 글이 묻히지 않는다.
- **Alternatives considered**: `published_at` 정렬 — 늦게 공개한 글이 뒤로 묻혀 기각(10 L-1). (`FRIENDS` 적용자의 친구용 블로그 목록만 `published_at`, 06 §6-3.)

### R-04 페이지 크기와 "마지막" 판단
- **Decision**: 9개 고정. 10개를 조회해 10개면 9개 + `nextCursor`, 아니면 `nextCursor: null`. 클라이언트 `size`는 무시. 값 9는 설정값 `blog.list.page-size`(기본 9).
- **Rationale**: 10 L-2·§4-2, spec FR-003, 원칙 VII.
- **Alternatives considered**: `COUNT(*)`로 남은 개수 계산 — 쿼리 1번 추가, 기각. 클라이언트 지정 크기 허용 — 10 §4-2가 금지.

### R-05 커서 형식
- **Decision**: JSON `{"v":1,"k":[epoch 마이크로초(UTC) 정수, id]}`를 패딩 없는 Base64URL로. 서버가 풀어 `WHERE (first_public_at, id) < (:t, :id)`. 첫 페이지는 커서 없음. 오류는 400 `INVALID_CURSOR`(본문 `{code, message, errors, details}`). 모든 커서 목록(태그·피드·검색·트렌딩·댓글)이 같은 형식, 정렬 키가 다르면 `k`만 다르고 기능별 값은 필드 추가, 형식 변경 시 `v` 증가.
- **Rationale**: 10 §4-2, 02 §5-1, 2026-10-07 O8, spec FR-006. 마이크로초는 PostgreSQL `timestamptz` 정밀도와 같아 같은 밀리초 글도 빠지지 않는다.
- **Alternatives considered**: OFFSET 페이지 — 보는 도중 발행·삭제 시 중복·누락(10 §4-3), 기각. 밀리초 시각 — 같은 밀리초 글 누락 위험, 기각.

### R-06 노출 조건과 인덱스
- **Decision**: 004의 공용 `VisibilityFilter` 조건(`status='PUBLISHED' AND visibility='PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL AND member.withdrawn_at IS NULL`)만 쓴다. 홈은 `ix_post_feed`, 블로그는 `author_id` 조건을 더해 `ix_post_blog`. 목록 조건에 작성자 본인 예외 없음.
- **Rationale**: 06 R-2·R-2a·R-2b·V-8, 02 §4-2, 2026-10-07 H1, 004 FR-009, spec FR-001·FR-021. 인덱스 WHERE와 조건이 하나라도 다르면 부분 인덱스를 못 쓴다.
- **Alternatives considered**: 목록마다 WHERE 직접 작성 — 06 R-2 금지. 블로그에서 본인에게 비공개 글 표시 — 06 V-8 기각.

### R-07 카드 SQL 한 번 + 프로필 사진
- **Decision**: `post JOIN member LEFT JOIN image(purpose='PROFILE', status='ATTACHED', detached_at IS NULL)` 한 번, `LIMIT 10`. 프로필 키 `COALESCE(thumb_storage_key, storage_key)` → 앱이 `blog.image.public-base-url` + 키로 주소 생성. `content_md`·`content_html` 미조회. 댓글·좋아요 수는 저장된 카운트 컬럼.
- **Rationale**: 10 §7·§9 C-READ-1 #6, 03 E-21, 51 `uq_image_profile_current`, spec SC-002. 2026-10-07에 `member.profile_image_url`이 삭제되어 사진은 `image`에서 가져온다(spec Assumptions).
- **Alternatives considered**: 모듈별 Service로 나눠 3쿼리 — "SQL 1번" 기준 위반, plan Complexity Tracking 참조. 카드에 태그 포함 — 카드에 태그 없음(10 §7).

### R-08 카드 썸네일
- **Decision**: `post.thumbnail_url`(발행 때 002가 본문 첫 업로드 사진의 640px 썸네일, 없으면 원본으로 지정)을 그대로 쓴다. 화면은 `loading="lazy"`, `alt`=제목, 썸네일이 없으면 같은 크기 빈 영역.
- **Rationale**: 10 §2·§6·L-8, spec FR-008, SC-005.
- **Alternatives considered**: 조회 때 이미지 테이블에서 계산 — 쿼리 추가, 기각.

### R-09 화면 규칙과 공통 범위
- **Decision**: 공통은 카드 정보 구성·9개 + [더 보기]·날짜 형식·로딩/빈 목록/오류/404 문구·대체글. 색·글꼴·배치 개수·썸네일 비율·빈 썸네일 색은 각자.
- **Rationale**: 2026-10-07 O5, 10 머리말, spec FR-007·FR-015.
- **Alternatives considered**: 16:9·`#F1F3F5`·3/2/1 배치 강제 — O5에서 예시로 격하.

### R-10 뒤로 가기 복원
- **Decision**: 카드 목록·`nextCursor`·스크롤 위치를 `sessionStorage`에 30분 보관, 돌아오면 복원. 이어 붙일 때 이미 있는 글 ID는 건너뛴다.
- **Rationale**: 10 L-6·§4-3·§4-4, spec FR-005·FR-018.
- **Alternatives considered**: `localStorage` — 탭 간 공유돼 오래된 목록이 다른 탭에 나타남, 기각. 매번 처음부터 — L-6 기각.

---

## C. 상세 (확정)

### R-11 주소와 처리 순서
- **Decision**: `/@{handle}/posts/{postId}`. ① 대문자 handle → 소문자 301 ② 번호가 숫자가 아니면 404 ③ 글+작성자 조회 → `canRead` → 불가면 404 ④ handle 불일치 → 올바른 주소 301 ⑤ 작성자 본인의 임시글 → `302 /write/{postId}` ⑥ 200. 볼 수 없는 글에는 ④를 하지 않는다.
- **Rationale**: 40 R-1·R-3·R-4·§3, 08 §6, 01 Q4, spec FR-025·FR-026.
- **Alternatives considered**: ④를 ③보다 먼저 — 남의 비공개 글 작성자가 드러나 기각(40 §3).

### R-12 읽기 판정
- **Decision**: `PostAccessPolicy.canRead(post, viewer)`(004 소유) 하나. 42 §5-1 표: 휴지통은 작성자도 404, 숨긴 글은 작성자만 안내와 함께, 탈퇴 유예 작성자의 글은 404, 관리자도 남의 비공개 글 404.
- **Rationale**: 40 R-2, 06 R-1, 42 §3·§5-1, 02 §4-2.
- **Alternatives considered**: 컨트롤러·화면 판정 — 06 R-1 금지.

### R-13 표시 날짜·"수정됨"
- **Decision**: 공개 글 `first_public_at`, 작성자가 보는 비공개 글 `published_at`. `edited_at`이 있을 때만 "수정됨 · M월 D일". 공개 범위만 바꾸면 `edited_at` 불변이라 표시 없음.
- **Rationale**: 40 R-6, 05 P-2, 06 V-6, spec FR-030, SC-011.
- **Alternatives considered**: `updated_at` 사용 — 공개 범위 변경·카운트 변경에도 바뀌어 기각.

### R-14 본문과 코드 강조
- **Decision**: 저장된 `content_html`을 그대로 출력(조회 때 렌더링·정화 안 함). highlight.js는 본문에 `<pre><code>`가 있을 때만 동적 `import()`로 같은 출처에서 로드.
- **Rationale**: 40 §2, 12 §7-2, 02 §4-1 "렌더링은 저장할 때 1번", spec FR-031.
- **Alternatives considered**: 조회 때 렌더링 — 성능·일관성 문제, 기각.

### R-15 상세 쿼리 수
- **Decision**: 글+작성자(+프로필 사진) 1, 태그(`post_tag`→`tag.name`, `position` 순) 1, 내가 좋아요 눌렀는지 `EXISTS` 1, 내가 팔로우 중인지 1, 작성자일 때 `post_draft` 유무 1 → 최대 5번. 비회원이면 좋아요·팔로우 쿼리 생략. `content_md` 미조회.
- **Rationale**: 40 §6, spec Implementation Notes.
- **Alternatives considered**: 한 쿼리로 모두 JOIN — 태그 행이 곱해지고 모듈 경계를 더 넘음, 기각.

### R-16 조회 기록
- **Decision**: 상세 GET은 조회수를 세지 않는다. 화면이 글이 보이는 상태로 1초 지나면 `POST /api/posts/{postId}/views`를 한 번 보낸다(항상 204, 실패 무시, 재시도 없음). 작성자 본인 상세에는 이 기능을 넣지 않는다(서버도 009에서 한 번 더 제외). 화면 조회수는 저장된 `view_count` 그대로.
- **Rationale**: 40 R-7·R-8·§4, 31 W-3·W-4·§4-1·§4-2, spec FR-041, SC-007.
- **Alternatives considered**: 상세 응답 때 서버가 기록 — 31 W-4 기각(봇·미리 불러오기 포함).

### R-17 캐시
- **Decision**: 상세 API·상세 첫 응답 모두 `Cache-Control: private, no-cache`, 글이 `PUBLIC`이 아니면(작성자가 보는 비공개·숨김 글 등) `private, no-store`. 404 응답도 `private, no-store`(제안 — 없는 글과 같게).
- **Rationale**: 40 R-9, 06 R-5, spec FR-042.
- **Alternatives considered**: 공유 캐시(CDN) — 보는 사람마다 내용이 달라 기각.

### R-18 링크 미리보기·검색 엔진 메타
- **Decision**: 공개 글: `<title>{제목} - {닉네임}</title>`, description = `excerpt` 앞 160자, canonical(쿼리 문자열 제외), `og:type=article`, `og:title`, `og:description`, `og:image`(첫 사진 **원본**, 없으면 서비스 기본 이미지), `article:published_time=first_public_at`, `article:modified_time=edited_at`(있을 때만). 값은 HTML 속성 이스케이프. 그 밖(없는 글·볼 수 없는 글·임시·작성자 본인이 보는 비공개 글): 06 §3-1 공통 문구 + `<meta name="robots" content="noindex">`.
- **Rationale**: 40 §5·R-10, 06 §3-1, 10 §6, spec FR-044·FR-045.
- **Alternatives considered**: `og:image`에 640px 썸네일 — 10 §6이 원본으로 결정, 기각.

### R-19 버튼 표시 (H6)
- **Decision**: [수정]·[공개 범위 ▾]·[삭제]는 작성자에게만. [좋아요]·[신고]는 작성자가 아니면 비회원·인증 전 회원에게도 보이고 누르면 로그인/인증 안내. 작성자에게는 좋아요 수만, [팔로우]도 없음.
- **Rationale**: 2026-10-07 H6(01 결정 기록이 40 §2·§7 #6을 수정), 004 FR-045, spec Assumptions.
- **Alternatives considered**: 40 §7 #6 원문(회원에게만) — H6이 더 최근 결정이라 기각.

### R-20 자기 글 좋아요 (README "정해진 것")
- **Decision**: 자기 글 좋아요는 400 `CANNOT_LIKE_OWN_POST`, 판정 순서는 로그인 → 계정 상태 → 볼 수 있나 → 자기 글 → 요청 횟수(42 §3). 이 기능은 작성자에게 [좋아요] 버튼을 그리지 않을 뿐, API는 009 소유.
- **Rationale**: README 정해진 것 2026-10-07(004 FR-037, 009 FR-009·010).
- **Alternatives considered**: 403 — 42 §4 "403은 계정 상태만"으로 기각.

### R-21 정규 주소와 쿼리 문자열
- **Decision**: canonical에 쿼리 문자열을 넣지 않는다. `?comment={id}`만 댓글 영역에 넘겨 007의 `around`로 그 댓글부터 보여준다. 알림 링크 `/@주소/posts/{글}?comment={id}#comment-{id}`.
- **Rationale**: 40 §2·§3, 21 §6, spec FR-027.

---

## D. 원문에 없어 고른 값 — 제안(팀 확인 필요)

### R-22 상세 데이터 API 경로 — 제안(팀 확인 필요)
- **Decision**: `GET /api/posts/{postId}` → 상세 보기(JSON). React 상세 화면이 이 API를 부른다. 판정·순서는 R-11의 ②③⑤와 같고, ①④(주소 정리)는 첫 응답(페이지 경로)이 맡는다. 화면 안 이동(SPA 내 링크)에서 handle이 다르면 화면이 응답의 `canonicalPath`로 `replace` 이동한다. 작성자 본인의 임시글이면 200 + `status: "DRAFT"` + `editorPath: "/write/{postId}"`만 주고 본문은 주지 않는다(화면이 에디터로 이동). 숫자가 아닌 `postId`는 404 `NOT_FOUND`(400 아님 — 페이지와 같게).
- **Rationale**: 02 §4-2가 "상세 API"를 언급하지만 경로가 원문에 없다. 02 §5-1 규약(`/api/posts/{postId}`)과 13 §2의 `DELETE /api/posts/{id}`와 같은 자원 경로를 쓴다.
- **Alternatives considered**: `GET /api/members/{handle}/posts/{postId}` — handle 불일치 처리가 API에도 필요해 복잡, 기각. 임시글에 302 — fetch가 리다이렉트를 따라가 HTML을 받게 되어 기각.

### R-23 블로그 머리말 API — 제안(팀 확인 필요)
- **Decision**: `GET /api/members/{handle}` → `{ handle, nickname, bio, profileImageUrl, publicPostCount, isMe }`. 없는 주소·탈퇴 유예·익명 처리 → 404 `NOT_FOUND`. API는 대문자 handle을 리다이렉트하지 않고 404(화면 주소 쪽이 301 처리). 공개 글 수는 목록과 같은 공용 조건의 `COUNT(*)`(`ix_post_blog` 사용). 팔로워 수·팔로우 여부 등은 010이 같은 응답에 필드를 **추가**한다.
- **Rationale**: 10 §5 상단 정보, 06 §3 "블로그 글 수", 02 §5-1 예시 경로 `/api/members/{handle}`. 원문에 API 정의가 없다.
- **Alternatives considered**: 목록 응답에 머리말을 섞기 — 10 §4-2 응답 형식(`items`, `nextCursor`)을 바꾸게 되어 기각.

### R-24 "다른 목록의 커서" 판별 — 제안(팀 확인 필요, 모든 커서 목록 공통)
- **Decision**: 커서 JSON에 목록 구분 필드 `l`을 더한다. 홈 `"l":"home"`, 블로그 `"l":"blog:{handle}"`. 예: `{"v":1,"l":"home","k":[1790755200123456,37]}`. 풀 때 요청한 목록의 `l`과 다르면 400 `INVALID_CURSOR`. 필드 추가는 10 §4-2 "기능에 필요한 값은 같은 JSON에 필드를 더한다"에 해당하므로 `v`는 1 그대로.
- **Rationale**: 10 §4-2·C-READ-1 #7과 spec FR-006이 "다른 목록의 커서 → 400"을 요구하지만, 원문 형식 `{"v","k"}`만으로는 홈 커서와 블로그 커서를 구별할 수 없다(둘 다 `[시각, id]`). 008·010·012·007 커서도 같은 필드를 쓰도록 공용 `CursorCodec`(shared)에 둔다.
- **Alternatives considered**: HMAC 서명 — 위조 감지는 되지만 커서 안 값은 공개 정렬 값뿐이라 보안 이득이 적고 키 관리가 늘어 기각(spec FR-006은 구조 검증만 요구). 목록마다 다른 `v` — 버전 의미가 흐려져 기각.

### R-25 첫 응답(메타) 구현 방식 — 제안(팀 확인 필요)
- **Decision**: React 빌드의 `index.html`에 `<!--app-head-->` 자리 표시자를 두고, `PageShellController`가 경로 처리(R-11) 후 `SpaShellRenderer`로 메타 태그를 이스케이프해 끼워 넣어 반환한다. 본문(`<div id="root">`)은 비어 있고 React가 API로 그린다. 홈 등 그 밖의 화면 경로는 공통 SPA 대체 경로(정적 `index.html`)를 쓴다.
- **Rationale**: H7은 "서버가 메타와 404 상태 코드를 넣는다"만 정했고 방법은 없다. 문자열 삽입은 템플릿 엔진 없이 가능하고 인라인 스크립트를 만들지 않는다(CSP 유지).
- **Alternatives considered**: Thymeleaf 템플릿 — H7에서 SSR을 걷어낸 취지와 어긋나고 의존성 추가, 기각. 봇 User-Agent만 메타 제공 — 404 상태 코드가 사람에게는 200이 되어 FR-043 위반, 기각.

### R-26 OG 대표 이미지(원본) 찾기 — 제안(팀 확인 필요)
- **Decision**: 첫 응답 경로에서만 `post.thumbnail_url`의 저장 키로 `image.thumb_storage_key`(UNIQUE `uq_image_thumb_key`)를 찾아 그 행의 `storage_key` → 원본 주소. 일치하는 행이 없으면(썸네일 없는 옛 사진이라 이미 원본) `thumbnail_url`을 그대로, `thumbnail_url`이 NULL이면 `blog.seo.default-og-image-url`.
- **Rationale**: 40 §5·10 §6은 `og:image`를 "첫 사진 원본"으로 정했지만 51에는 원본 주소 컬럼이 없다(카드용 `thumbnail_url`만 있음). 스키마를 바꾸지 않고(원칙 I) UNIQUE 인덱스 조회 1번으로 해결한다.
- **Alternatives considered**: `content_html`에서 첫 `<img src>` 파싱 — 외부 이미지·순서 해석 문제, 기각. `_thumb` 문자열 치환 — 원본 확장자(gif 등)가 달라질 수 있어 기각. `post.og_image_url` 컬럼 추가 — 원칙 I 위반(개인 확장으로는 가능).

### R-27 블로그 주소 첫 응답 메타 — 제안(팀 확인 필요)
- **Decision**: `<title>{닉네임} (@{handle})</title>`, description = 소개 앞 160자(없으면 생략), canonical `/@{handle}`, `og:type=profile`, `og:image` = 프로필 사진 원본(없으면 기본 이미지). 없는·탈퇴 유예·익명 처리 주소는 404 + 06 §3-1과 같은 공통 404 화면 메타 + `noindex`.
- **Rationale**: H7·02 §6이 블로그 주소에도 메타를 요구하지만 항목은 정하지 않았다. 40 §5 형식을 따라 맞췄다.
- **Alternatives considered**: 메타 없이 404 코드만 — 미리보기가 비어 공유 품질 저하, 기각.

### R-28 조회 기록 스크립트 위치 — 제안(팀 확인 필요)
- **Decision**: 31 §4-1의 로직(1초 가시성 타이머, `visibilitychange`, `keepalive`, 실패 무시)을 React 번들 안 `useViewBeacon` 훅으로 구현한다. 번들 자체가 우리 서버가 주는 별도 파일이므로 FR-037(인라인 스크립트 없음)을 만족한다. 작성자 본인 상세(`viewer.isAuthor`)와 404 화면에서는 훅을 실행하지 않는다. CSRF 토큰은 001이 정하는 방식(M17)으로 가져와 `X-CSRF-TOKEN` 헤더에 넣는다. GIF 재생(`/js/gif-play.js`, 003 FR-039)도 같은 원칙으로 번들 모듈 또는 같은 출처 파일로 넣는다.
- **Rationale**: 40 §4의 `/js/post-view.js` + `data-` 속성은 서버 렌더링 페이지를 전제로 했다. SPA에서는 화면 안 이동으로 여러 글을 볼 때 정적 스크립트가 다시 실행되지 않는다.
- **Alternatives considered**: `/js/post-view.js`를 첫 응답 HTML에 넣기 — SPA 내부 이동 시 기록 누락, 기각.

### R-29 상대 시간·날짜 형식 세부 — 제안(팀 확인 필요)
- **Decision**: 1분 미만은 "방금 전", 1시간 미만 "N분 전", 24시간 미만 "N시간 전", 그 이후 `YYYY.MM.DD`. 날짜·"수정됨 · M월 D일"은 한국 시간(Asia/Seoul)으로 표시. `<time datetime>`에는 UTC ISO-8601.
- **Rationale**: 10 L-5·spec FR-012는 1분 미만과 시간대를 정하지 않았다. 31이 일별 집계를 한국 시간으로 하므로 맞춘다.
- **Alternatives considered**: "0분 전" — 어색해 기각. 브라우저 시간대 — 같은 글의 날짜가 사람마다 달라 SC-011 확인이 어려워 기각.

### R-30 부가 정보 실패 시 상세 — 제안(팀 확인 필요)
- **Decision**: 태그·좋아요 여부·팔로우 여부·작업본 유무 조회가 실패하거나 해당 모듈(008·009·010)이 아직 없으면 빈 목록/false/없음으로 상세를 계속 응답한다(경고 로그). 글+작성자 조회와 `canRead`만 실패 시 오류.
- **Rationale**: 원칙 V, 01 §3-5. Tier B·C 모듈보다 상세(Tier A)가 먼저 구현된다.
- **Alternatives considered**: 전체 실패 처리 — 부가 기능이 읽기를 막아 기각.

### R-31 목록·머리말 API 캐시 — 제안(팀 확인 필요)
- **Decision**: 목록·블로그 머리말 API는 `Cache-Control: private, no-cache`.
- **Rationale**: 공개 글만 담지만 `FRIENDS` 적용자는 보는 사람마다 결과가 다르고(06 §3), [더 보기] 도중 삭제된 글이 캐시로 남지 않게 한다. 원문은 목록 캐시를 정하지 않았다.
- **Alternatives considered**: 공유 캐시 허용(06 §3 "PUBLIC 응답 캐시 허용") — `FRIENDS`·`isMe` 필드와 충돌, 공통 최소선으로는 기각(개인이 더 공격적으로 캐시해도 됨).

### R-32 리다이렉트 시 쿼리 문자열 — 제안(팀 확인 필요)
- **Decision**: 대문자 handle 301과 handle 불일치 301은 쿼리 문자열(`?comment=` 등)을 유지한다.
- **Rationale**: 알림 링크(`?comment={id}#comment-{id}`)가 리다이렉트 뒤에도 그 댓글로 가야 한다(25 §2, 21 §6). 조각(`#…`)은 브라우저가 유지한다.
- **Alternatives considered**: 쿼리 버림 — 알림에서 들어온 댓글 위치를 잃어 기각.

---

## E. 미결 — 기본안

### R-33 첫 댓글 20개 전달 방식 — 미결(007 FR-016) — 기본안
- **Decision (기본안)**: 상세 API에 댓글을 넣지 않는다. React 상세 화면이 상세 API와 **동시에** 007의 `GET /api/posts/{postId}/comments`(`?comment=`가 있으면 `around={commentId}`)를 불러 첫 20개(+ 각 답글 3개)를 바로 그린다. 댓글 수 머리말은 상세 API의 `commentCount`(`post.comment_count`). 첫 응답 HTML에는 댓글을 넣지 않는다.
- **Rationale**: spec 005 Assumptions는 "상세를 열면 첫 20개가 바로 보인다"는 결과만 요구하고 전달 방식을 plan에 맡겼다. H7이 SSR 대체 규칙을 지웠고, 007 FR-016에 `[NEEDS CLARIFICATION]`이 남아 있다. 병렬 요청이면 댓글 조회 실패가 본문 표시를 막지 않고(원칙 V), interaction 모듈 경계도 지켜진다.
- **Alternatives considered**: 상세 API에 첫 페이지 포함 — 요청 1번이지만 상세 응답이 커지고 댓글 오류가 상세를 막을 수 있음. 007 FR-016이 "포함"으로 결정되면 상세 API에 `comments` 필드를 추가하는 방향으로 바꾼다(응답 형식은 21 §6과 같게).

### R-34 관리자 조회의 조회수 제외 — 미결(009 FR-031) — 기본안
- **Decision (기본안)**: 이 기능은 작성자 본인에게만 조회 기록을 끈다. 관리자 제외 여부는 009가 서버에서 정하고, 화면은 관리자에게도 비콘을 보낸다(서버가 걸러냄).
- **Rationale**: 40 R-8 "관리자 제외는 31에 추가 요청" 상태, spec Assumptions가 009로 위임.
- **Alternatives considered**: 화면에서 관리자 제외 — 판정이 두 곳으로 갈라져 기각(40 R-8 "제외 판정은 기록 API 한곳").

### R-35 CSRF 토큰 전달 방식 — 미결(M17, constitution Follow-up) — 기본안
- **Decision (기본안)**: 001 plan이 정하는 방식을 그대로 쓴다. 조회 기록 비콘은 그 토큰을 `X-CSRF-TOKEN` 헤더로 보낸다(31 §4-1). 이 기능의 GET API는 CSRF 토큰이 필요 없다.
- **Rationale**: 02 §5 "토큰 저장 방식은 M17에서 확정".
