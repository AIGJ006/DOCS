---

description: "Task list for 008-tag (태그와 태그별 글 목록)"
---

# Tasks: 태그와 태그별 글 목록

**Input**: Design documents from `/specs/008-tag/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, normalization.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 tag 모듈을 소유하고, 002가 "TODO(008)"로 남긴 임시 `TagService`와 005가 남긴 태그 링크·`PostTagNamesQuery` 기본 구현을 넘겨받는다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `BannedWordFilter`(`AccountPolicyConfig` Bean, `policy/banned-words.txt`), `RateLimiter`(T023), `@LoginRequired`, `shared/error`(공통 오류 본문·`ValidationException`·`NotFoundException`·`TooManyRequestsException`), `CursorCodec`·`ListScope`, `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`RedisOutage`
- 선행: specs/002 발행 — `TagService`(임시, T046)·`PublishValidator`·`PublishService`·`EditorQueryService`(지금 태그 미리 채우기), `PostReasonCode.TOO_MANY_TAGS`·`INVALID_TAG`, `PostAuthoringProperties.maxTags`, 교체 점검표 테스트(`PublishValidatorTest`·`PublishIT`·`PublishQueryCountIT`·`PublishTransactionIT`·`PublishIdempotencyIT`), 화면 `PublishDialog`(임시 칩)
- 선행: specs/004 — `VisibilityFilter`(별칭 `p`·`m` 계약), 권한 하네스(`support/permission/`, `INCLUDED`/`EXCLUDED` 목록 행동)
- 선행: specs/005 — `PostCardQueryRepository`·`PostListService`·`PostListCursor`·`BlogQueryService`·`BlogController`·`PageShellController`·`LinkPreviewMetaFactory`·`NotFoundPageRenderer`, `PostReadingPorts`(`PostTagNamesQuery` 기본 구현), 화면 `TagList`·`BlogPage`·`useCursorList`·`PostCardGrid`·`LoadMoreButton`

**006 머지 후**

- 006-manage-delete(브랜치 `006-manage`, 구현 중)가 `F/App.tsx`에 `/manage/posts` 라우트를 더한다. 이 기능의 라우트 작업(T037)은 006 머지 후 같은 파일에 더한다(충돌 방지). 그 밖에 겹치는 파일은 없다 — 글 완전 삭제 때 `post_tag`는 FK CASCADE라 할 일이 없다

**후속 (다른 스펙이 이 기능을 사용)**

- 012-trending-search: 검색창 `#태그` 바로 이동(FR-040)은 012가 `TagNormalizer.normalizeQuery`·`normalizeTag.ts`·`tagPath.ts`로 만든다. "최근 많이 쓰인 태그"도 012 소유
- 013-ai-tag-suggest: 추천 결과를 `TagNormalizer.normalize`로 거른다(FR-041)
- 014·015: 숨김·탈퇴 신청 글은 `VisibilityFilter`로 빠진다. 할 일 없음(quickstart §5로 확인만)

**팀 결정 대기 (기본안으로 진행)**

- Clarifications Q3 전제: "008보다 운영 배포가 먼저면 정리 마이그레이션". 확인 작업 T002, 필요하면 조건부 T075
- `RedisGuard` OOM 503 문구(002 T032, ANALYSIS-tier-bc): 자동완성 화면은 code와 상관없이 실패를 무시하므로 이 기능은 영향 없음

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 작업 확인, 운영 배포 순서 확인(Clarifications Q3), 설정값·테스트 원본 준비

- [X] T001 선행 확인: `B/tag/application/TagService.java`(TODO(008)·교체 점검표), `B/post/application/port/PostTagNamesQuery.java`, `B/post/config/PostReadingPorts.java`, `B/post/infra/VisibilityFilter.java`, `B/discovery/infra/PostCardQueryRepository.java`, `B/discovery/web/PageShellController.java`, `B/account/application/policy/BannedWordFilter.java`, `B/shared/infra/ratelimit/RateLimiter.java`, `T/support/permission/AbstractPermissionMatrixIT.java`, `F/components/editor/PublishDialog.tsx`, `F/components/TagList.tsx`가 있는지, V1에 `tag`(`ck_tag_name`·`ix_tag_name_prefix`)·`post_tag`(`uq_post_tag_position`·`ix_post_tag_tag`)가 있는지 확인한다. 빠진 것이 있으면 소유 스펙에 보고하고 시작하지 않는다 (구현 메모: 모두 있음. V1 tag·post_tag 제약·인덱스 확인. PublishValidatorTest·TitleNormalizerTest는 T/post/unit/에 있다)
- [X] T002 **확인 작업(Clarifications Q3)**: 배포 담당에게 "008 머지 전에 운영 DB에 태그가 생길 배포가 있는가"를 묻고 결과를 `specs/008-tag/research.md` R13 아래에 적는다. "있다"면 조건부 작업 T075를 이 기능 범위에 넣는다. 답을 받기 전까지 "없다"(정리 마이그레이션 없음)로 진행한다 (구현 메모: 사람에게 묻지 않고 "없다"(Tier A 운영 배포 전)로 가정해 research.md R13에 적었다. T075는 범위 밖)
- [X] T003 [P] 설정값 `B/tag/application/TagProperties.java`(`@ConfigurationProperties("blog.tag")`, `@Validated`: `topLimit`(1~1000), `BlogStrip(limit, initial)`(initial ≤ limit), `Suggest(limit, RateLimit(limit, window))`)와 `R/application.yml`의 `blog.tag.*` 기본값(data-model §5, research R16)을 추가한다
- [X] T004 [P] 정규화 예시 원본 `TR/tag/normalization-cases.csv`(열 `input,expected,code,note`)를 contracts/normalization.md §2 표 그대로 만든다. 전각·보이지 않는 글자는 `\uXXXX`, 금칙어 행은 `{BANNED_0}`·`{BANNED_0_DIGIT}` 자리표시로 두고 테스트가 `policy/banned-words.txt` 첫 단어로 바꾼다(문서·CSV에 단어를 적지 않음) (구현 메모: 모든 칸을 큰따옴표로 감쌌다(앞뒤 공백 보존). \\uXXXX 표기는 입력 칸에만 쓴다)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 정규화 클래스(서버·화면), 오류 코드 이동, 보이지 않는 글자 공용화, 목록 구분 값, 카드 조회의 태그 조건, 화면 API·주소 함수

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T005 [P] 보이지 않는 글자 단위 테스트 `T/shared/text/InvisibleCharactersTest.java`: U+200B~200F·U+2060~2069·U+FEFF·U+202A~202E·ISO 제어 문자는 참, 한글·한글 자모·U+0020·U+00A0은 거짓. 기존 `TitleNormalizerTest`가 그대로 통과하는지 함께 돌린다(research R2)
- [X] T006 [P] 정규화 단위 테스트 `T/tag/unit/TagNormalizerTest.java`: ① `@CsvFileSource("/tag/normalization-cases.csv")`로 예시 표 전부(SC-001) ② 우선순위(`🔥` 31자 → `INVALID_TAG`, 허용 문자 31자 금칙어 → `TAG_TOO_LONG`) ③ `normalizeQuery`는 금칙어를 거르지 않고 실패면 빈 값 ④ `Locale.setDefault(tr)`에서도 `I` → `i` ⑤ null·빈 문자열 → `INVALID_TAG` ⑥ 금칙어 거부 결과에 단어가 없음(`Rejected` 값 객체에 코드뿐) (구현 메모: 검색어 진입점(normalizeQuery)도 같은 CSV로 매개변수 시험을 하나 더 둔다)
- [X] T007 [P] 목록 구분 값 단위 테스트 `T/shared/web/cursor/ListScopeTest.java`에 추가: `ListScope.tag("c#")` = `tag:c#`, `blogTag("kim", "jpa")` = `blog:kim:tag:jpa`, `tag:` 커서를 `home`·`blog:kim`·다른 태그 목록에 쓰면 400 `INVALID_CURSOR` (구현 메모: ListScopeTest가 없어 새로 만들었다)
- [X] T008 [P] 카드 조회 태그 조건 통합 테스트 `T/discovery/integration/PostCardTagFilterIT.java`: `CardFilter(null, tagId)`가 그 태그 글만, `CardFilter(ownerId, tagId)`가 그 블로그의 그 태그 글만 돌려주고, `tagId` 없는 기존 호출 결과가 바뀌지 않는다. 005 `HomePostListIT`·`BlogPostListIT`(이름은 005 tasks 기준)를 함께 돌린다(research R6) (구현 메모: 카드 조건 값은 discovery.application.CardFilter(record, all()·author())로 두었다(010이 필드를 더해 쓴다). 005 테스트 PostCardQueryRepositoryIntegrationTest·ListIndexExplainIntegrationTest의 호출을 CardFilter로 바꿨다)
- [X] T009 [P] 화면 정규화 테스트 `F/features/tag/__tests__/normalizeTag.test.ts`: `fs.readFileSync`로 `backend/src/test/resources/tag/normalization-cases.csv`를 읽어 금칙어 행을 뺀 모든 행이 서버와 같은 결과·코드인지(R4). `tagPath.test.ts`: contracts/normalization.md §3 표의 경로 인코딩 7행(`c++`는 `+` 그대로, `c#`은 `%23`) (구현 메모: tsconfig types에 node가 없어 테스트 파일에 /// <reference types="node" />를 두고 node:fs로 읽는다(Vite ?raw는 fs.allow 밖이라 거부됨))

### Implementation for Foundational

- [X] T010 [P] `B/shared/text/InvisibleCharacters.java`(`static boolean isInvisible(int cp)`, `static String strip(String)`)를 만들고 `B/post/domain/TitleNormalizer.java`의 `isRemoved`를 이것으로 바꾼다(T005 통과, 002 소유 파일 — 002 담당에게 알림) (구현 메모: 002 담당 알림 대상: TitleNormalizer.isRemoved 삭제 → InvisibleCharacters.strip)
- [X] T011 [P] 오류 코드 `B/tag/domain/TagReasonCode.java`(`ReasonCode`: `INVALID_TAG`·`TAG_TOO_LONG`·`TAG_BANNED_WORD`, 문구는 data-model §2-2, 끝 마침표 없음, `fieldError(String field)` 도우미)와 결과 값 `B/tag/domain/TagNormalization.java`(sealed: `Accepted(name)`·`Rejected(code)`)를 만든다
- [X] T012 정규화 클래스 `B/tag/domain/TagNormalizer.java`(`@Component`, `BannedWordFilter` 주입, `normalize(raw)`·`normalizeQuery(raw)`, contracts/normalization.md §1 단계, 패턴 상수 `ALLOWED`·`HAS_WORD`는 V1 `ck_tag_name`과 같은 문구)를 구현한다(T006 통과). 금칙어 거부 때 로그를 남기지 않는다 (구현 메모: 패턴 상수 ALLOWED·HAS_WORD는 V1 문구 그대로, 우선순위 판정은 문자 집합 검사 → 길이 순서로 따로 한다)
- [X] T013 [P] `B/shared/web/cursor/ListScope.java`에 `tag(String name)`·`blogTag(String handle, String name)`를 추가하고 값 규칙 주석에 `tag:{name}`·`blog:{handle}:tag:{name}`을 적는다(T007 통과, 001 소유 파일 — 알림) (구현 메모: 001 담당 알림 대상)
- [X] T014 카드 조회에 태그 조건: `B/discovery/infra/PostCardQueryRepository.java`의 `findCards`·`cardQuery`가 `CardFilter(Long authorId, Long tagId)`를 받게 바꾸고 `tagId`가 있으면 `AND EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = :tagId)`를 더한다. `B/discovery/application/PostListService.java`의 `page(scope, filter, cursor, viewer)`로 넓히고 기존 호출(`HomeQueryService`·`BlogQueryService`)을 고친다. 클래스 주석의 원칙 II 예외 문단에 `post_tag`를 더한다(T008 통과, plan Complexity Tracking) (구현 메모: PostListService.page(scope, CardFilter, cursor, viewer))
- [X] T015 [P] 화면 정규화·주소: `F/features/tag/normalizeTag.ts`(`normalizeTag(raw): {ok: true, name} | {ok: false, code}`, 금칙어 없음), `F/features/tag/tagPath.ts`(`tagPath(name)`, `tagQueryValue(name)`), `F/features/tag/tagMessages.ts`(code → 문구, data-model §2-2·`TOO_MANY_TAGS`)를 만든다(T009 통과) (구현 메모: normalizeTagQuery(raw)도 둔다(012 검색창용). tagPathSegment(name)는 API 경로에도 쓴다)
- [X] T016 [P] 화면 API `F/api/tags.ts`(`getTagSummary(name)`, `listTagPosts(name, cursor)`, `listTopTags()`, `suggestTags(q, signal)`, `getBlogTags(handle)`; 경로는 `tagPath` 규칙의 `encodeURIComponent(name).replace(/%2B/g,'+')`)와 타입 `F/api/types/tags.ts`(contracts/openapi.yaml 스키마)를 만든다. `F/api/members.ts`의 `listBlogPosts(handle, cursor, tag?)`에 `tag` 쿼리(`encodeURIComponent`)를 더한다 (구현 메모: suggestTags는 notFoundScreen:false로 부른다(실패를 화면 전환 없이 무시))

**Checkpoint**: 예시 표가 서버·화면에서 같은 결과를 내고, 카드 조회가 태그 조건을 받는다

---

## Phase 3: User Story 1 - 발행할 때 태그 붙이기 (Priority: P1) 🎯 MVP

**Goal**: 발행 설정 창에서 태그를 칩으로 입력하고, 같은 뜻의 입력은 한 태그가 되며, 문제가 있는 태그를 한 번에 모두 알려 준다

**Independent Test**: 22 §2-1 예시 입력으로 발행해 저장 결과와 오류를 확인하고, 같은 새 태그로 10건을 동시에 발행해 태그가 하나만 생기는지 본다

### Tests for User Story 1 ⚠️

- [X] T017 [P] [US1] 발행 검증 단위 테스트 `T/post/domain/PublishValidatorTest.java`에 추가: `개수_초과와_칸_오류를_함께_모은다`(서로 다른 11개 + `🔥hot` → `tags`/`TOO_MANY_TAGS` + `tags[11]`/`INVALID_TAG`), `개수는_중복_제거_후에_센다`(같은 이름 11칸 → 통과), `TAG_TOO_LONG_TAG_BANNED_WORD는_그_칸_번호로`. 기존 `형식이_틀린_태그는_그_칸_번호로_INVALID_TAG`·`여러_항목이_틀리면_모두_모은다`·`태그_11개는_TOO_MANY_TAGS_10개는_통과`·`대소문자_중복은_하나로_센다`는 그대로 통과해야 한다(002 T122) (구현 메모: 파일은 T/post/unit/PublishValidatorTest.java. 기존 형식이_틀린_태그는_그_칸_번호로_INVALID_TAG의 31자 칸은 008 규칙대로 tags[3]/TAG_TOO_LONG으로 기대값을 바꿨다(칸 번호 규칙은 그대로). 금칙어는 운영 policy/banned-words.txt에서 읽는다)
- [X] T018 [P] [US1] 발행 통합 테스트 `T/tag/integration/TagPublishIT.java`: `같은_뜻의_입력은_한_태그가_된다`(US1 #1·#2), `문제_태그를_모두_한_번에_알려준다`(US1 #4, 금칙어 칸 `message`에 단어 없음, 응답 본문 전체에도 없음), `11개면_발행되지_않는다`(US1 #5, 글 상태·`post_tag` 그대로), `입력_순서대로_처음_것만_남긴다`(US1 #6, `position` 0·1), `같은_새_태그로_동시에_10건_발행해도_태그는_하나`(US1 #8, `CountDownLatch` 10스레드, `SELECT count(*) FROM tag WHERE name = …` = 1, 10건 모두 연결), `태그만_바꿔_다시_발행하면_수정됨`(US1 #9, 002 수정됨 표시 필드), `다시_발행할_때_지금_태그를_순서대로_준다`(US1 #7, 에디터 조회 응답) (구현 메모: 공용 컨텍스트(IntegrationTestBase)만 쓴다. 수정됨은 응답 editedAt·post.edited_at으로 본다)
- [X] T019 [P] [US1] 태그 입력 화면 테스트 `F/components/editor/__tests__/TagInput.test.tsx`: Enter·쉼표로 추가되고 띄어쓰기로 나뉘지 않는다(US1 #3, 칩 글자 `#spring-boot`), 같은 이름은 추가되지 않고 기존 칩 강조, 형식 오류는 칩 없이 문구·입력 유지, × 클릭·빈 칸 Backspace 삭제, Alt+←/→ 순서 바꾸기와 `aria-live` 안내, 드래그 순서 바꾸기(`dragstart`/`drop` 이벤트), "2 / 10" 표시와 최대 개수에서 입력 막기, `errors`의 `tags[i]`가 i번째 칩을 오류 칩(테두리 + "⚠" + 문구)으로, `initialTags` 미리 채우기 (구현 메모: 칩 글자는 #이름, 칩 목록 이름은 "붙인 태그"(role=list). 조합 중 Enter 무시도 시험한다)
- [X] T020 [P] [US1] `F/components/editor/__tests__/PublishDialog.test.tsx` 갱신: 임시 칩 대신 `TagInput`을 쓰고, 400 칸 오류(`tags[2]`·`TAG_BANNED_WORD`)가 그 칩에 보이며 다른 칩은 정상, 기존 "400 칸 오류를 모두 보인다"가 통과한다 (구현 메모: FR-014: 발행하지 않고 닫은 칩은 onTagsChange로 EditorPage가 들고 있다가 다시 열 때 넘긴다(localDraftStore에는 태그 칸이 없어 화면 상태로 둔다))

### Implementation for User Story 1

- [X] T021 [US1] `B/tag/application/TagService.java`를 최종 구현으로 바꾼다: `TagNormalizer` 주입, `normalizeAll`은 `Accepted` 이름만 입력 순서로 중복 제거, `validate`는 `Rejected` 칸마다 `TagReasonCode.fieldError("tags[" + i + "]")`, 정적 `normalize`·`isValidName`·`NAME`·`MEANINGFUL` 제거, `PostReasonCode` import 제거, 클래스 주석의 TODO(008) 제거(교체 점검표는 남김). `replacePostTags`·`tagNamesOf`의 SQL은 그대로 둔다(쿼리 3번 유지)
- [X] T022 [US1] `B/post/domain/PostReasonCode.java`에서 `INVALID_TAG`를 지우고 참조처(테스트 포함, `grep -rn "PostReasonCode.INVALID_TAG"`)를 `TagReasonCode.INVALID_TAG`로 바꾼다. 응답 `code` 문자열이 같은지 `PublishIT` 기존 단언으로 확인한다(research R3) (구현 메모: grep 결과 참조처는 TagService뿐이었다)
- [X] T023 [US1] `B/post/domain/PublishValidator.java`: 개수 초과일 때도 `tagService.validate`를 함께 모으도록 바꾼다(research R3, T017 통과). 002 소유 파일 — 002 담당에게 알림 (구현 메모: 002 담당 알림 대상)
- [X] T024 [US1] `F/components/editor/TagInput.tsx`(research R12): 칩 목록 `role="list"`, 칩마다 버튼 ×(`aria-label="{name} 태그 빼기"`), `draggable` + `onDragStart/onDragOver/onDrop`, 칩 `tabIndex=0` + Alt+방향키, 개수 `aria-describedby`, 오류 문구는 `tagMessages`. 375px에서 줄바꿈되고 가로 스크롤 없음(T019 통과) (구현 메모: 스타일은 features/tag/tag.css(색은 var(--color-*, 기본값) — 016 토큰이 아직 없음))
- [X] T025 [US1] `F/components/editor/PublishDialog.tsx`의 임시 칩 입력(`addTag`·`removeTag`·`onTagKeyDown`)을 `TagInput`으로 바꾸고, 보내는 `tags`는 칩의 정규화된 이름 배열(순서 그대로)로 한다. 발행하지 않고 닫을 때 기존 `localDraftStore` 저장 동작은 그대로 둔다(FR-014, T020 통과) (구현 메모: EditorPage에 pendingTags 상태 한 줄을 더했다)
- [X] T026 [US1] T017·T018과 002 교체 점검표 테스트(`PublishIT`, `PublishQueryCountIT#발행_SQL_수는_태그_사진_수에_비례하지_않는다`, `PublishTransactionIT`, `PublishIdempotencyIT#트랜잭션이_실패하면_키가_풀려_같은_키_같은_내용으로_다시_발행할_수_있다`)를 함께 돌려 모두 통과하는지 확인한다 (구현 메모: TagPublishIT 7·PublishIT 11·PublishQueryCountIT 2·PublishTransactionIT 5·PublishIdempotencyIT 9·PostDetailIntegrationTest 8 통과)

**Checkpoint**: US1 단독으로 발행·정규화·오류·동시 생성이 동작한다(MVP 1단계)

---

## Phase 4: User Story 2 - 태그별 글 목록 보기 (Priority: P1)

**Goal**: `/tags/{이름}`에서 그 태그의 공개 글을 홈과 같은 카드로 보고, 주소의 301·404를 서버가 준다

**Independent Test**: 여러 상태의 글에 같은 태그를 붙이고 비회원으로 태그 페이지를 열어 공개 글만 보이는지, 허용 문자 태그 주소가 모두 왕복되는지 확인한다

### Tests for User Story 2 ⚠️

- [X] T027 [P] [US2] 목록 통합 테스트 `T/tag/integration/TagPostListIT.java`: `공개_글만_최신순_9개와_커서`(US2 #1, 12개 → 9 + 3), `머리말_글_수는_공개_글만`(US2 #1·#2), `비공개_전용_태그와_없는_태그의_응답이_같다`(US2 #3·SC-005 — summary·posts 두 API 모두 상태·헤더·본문 바이트 비교, 이름만 다름을 고려해 같은 이름으로 DB를 바꿔 가며 비교), `친구_공개_규칙이어도_전체_공개만`(FRIENDS 규칙 Bean을 켠 테스트 설정이 있으면, 없으면 `@Disabled("FRIENDS 규칙 도입 시")`), `정규화되지_않은_이름은_404`·`형식이_틀린_이름은_404`(API는 301 없음), `다른_목록의_커서는_400` (구현 메모: 친구 공개 규칙 Bean이 아직 없어 그 시험은 @Disabled. 바이트 비교는 이름이 같은 두 상태(태그 없음 → 비공개 글에만 붙음)를 차례로 만들어 비교했다)
- [X] T028 [P] [US2] 페이지 셸 통합 테스트 `T/tag/integration/TagPageShellIT.java`(실제 `springSecurityFilterChain` + `StrictHttpFirewall`을 거치는 `MockMvc` 또는 `TestRestTemplate`): `허용_문자_태그_주소가_모두_왕복된다`(SC-002·US2 #4: `c#`·`c++`·`node.js`·`.net`·`스프링-부트`·`자바_기초` → 200, 그 셸이 React 셸), `정규화되지_않은_주소는_301`(US2 #5, `Location: /tags/spring-boot`, 쿼리 유지), `형식이_틀린_이름은_404`(US2 #6, 공통 404 화면 본문), `글이_없는_태그도_200`(메타에 글 수 없음) (구현 메모: MockMvc + 실제 보안 필터 체인으로 보냈다. a%2Fb처럼 방화벽이 막는 주소는 넣지 않았다)
- [X] T029 [P] [US2] 권한 매트릭스 `TR/permission/tag-list.csv`(research R14의 행 전부 — `tag.posts`·`tag.top`·`tag.suggest`·`blog.tags` × 행위자 × 대상 상태, owner `008`)와 `T/tag/integration/TagPermissionMatrixIT.java`(`AbstractPermissionMatrixIT` 상속), 실행기 `T/tag/integration/permission/TagPostsAction.java`(`tag.posts`, 대상 글에 `perm-tag` 태그를 붙인 뒤 목록 포함 여부)를 만든다. 나머지 세 실행기는 각 스토리에서 더한다(그 전까지 `pending: 008`)
- [X] T030 [P] [US2] 화면 테스트 `F/pages/__tests__/TagPage.test.tsx`: 머리말 "#spring-boot · 공개 글 12", 카드 9개·[더 보기]·불러오는 중 비활성·실패 "불러오지 못했어요 [다시 시도]"(FR-027), 글 0개면 "아직 이 태그로 공개된 글이 없어요", API 404면 `NotFoundPage`. `F/components/__tests__/TagList.test.tsx`: 링크가 `tagPath` 모양(`/tags/c%23`, `/tags/c++`)

### Implementation for User Story 2

- [X] T031 [US2] 집계 저장소 `B/tag/infra/TagQueryRepository.java`(`JdbcClient` + `VisibilityFilter`): `findIdByName(name)`, `countPublic(tagId)`(`forViewer(Viewer.anonymous(), null)` 조건, 익명 조건을 쓰는 이유 주석 — research R5). 클래스 주석에 원칙 II 예외(plan Complexity Tracking)를 적는다 (구현 메모: countPublic은 태그 번호 대신 이름으로 받아 tag→post_tag→post→member 한 번의 SQL로 센다)
- [X] T032 [US2] `B/tag/application/TagQueryService.java`(`@Transactional(readOnly = true)`): `summary(name)` — `normalizeQuery(name)`가 `name`과 같지 않으면 `NotFoundException`, 태그가 없으면 `{name, 0}`. `findIdByName` 공개(discovery가 부름) (구현 메모: 이름 확인은 requireCanonical(name)으로 따로 두어 discovery TagPostQueryService도 같은 규칙을 쓴다)
- [X] T033 [US2] `B/discovery/application/TagPostQueryService.java`: `page(name, cursor)` — 이름 확인(T032와 같은 규칙) → 태그 번호 없으면 `CursorPage.empty()` (커서가 있으면 먼저 `ListScope.tag(name)`로 검증해 400 규칙 유지) → `PostListService.page(ListScope.tag(name), CardFilter(null, tagId), cursor, Viewer.anonymous())`
- [X] T034 [US2] `B/tag/web/TagController.java`: `GET /api/tags/{name}/summary`, `GET /api/tags/{name}/posts`(`size` 무시), `Cache-Control: private, no-cache`(005 `CacheControlPolicy.NO_CACHE`). SecurityConfig는 `/api/me/**`만 막으므로 따로 고치지 않는다(T027 통과)
- [X] T035 [US2] `B/discovery/web/PageShellController.java`에 `GET /tags/{name}` 추가(contracts/normalization.md §4: 실패 404 화면 → 다르면 301 `UriUtils.encodePathSegment` → 200 셸)와 `B/discovery/application/LinkPreviewMetaFactory.java`에 `forTag(name)`(제목 "#name", 설명 고정 문구, 글 수 없음)을 더한다(T028 통과, 005 소유 파일 — 005 회귀 `PageShellControllerIT` 함께 실행) (구현 메모: 같은 컨트롤러에 GET /tags(T051)도 함께 넣었다. 설명 문구는 '#name 태그로 모은 글'(글 수 없음))
- [X] T036 [US2] `B/tag/application/TagNamesQueryAdapter.java`(`@Component implements PostTagNamesQuery`, `TagService.tagNamesOf` 위임)를 등록해 005 기본 구현을 물리고, `B/post/application/port/PostTagNamesQuery.java` 주석의 "008이 넘겨받는다"를 "008 `TagNamesQueryAdapter`"로 고친다. 005 글 상세 테스트(`PostDetailApiIT` 태그 단언)를 돌린다 (구현 메모: 일반 설정 클래스의 @ConditionalOnMissingBean은 스캔 순서에 따라 판정이 흔들릴 수 있어 PostReadingPorts의 태그 기본 Bean을 지웠다(좋아요·팔로우 기본값은 그대로))
- [X] T037 [US2] (**006 머지 후**) `F/App.tsx`에 `/tags`·`/tags/:name` 라우트를 `/:handle` 앞에 더하고(react-router가 `:name`을 디코드한 값을 주는지 `c%23`로 확인), `F/pages/TagPage.tsx`(`getTagSummary` + `useCursorList(listTagPosts)` 동시 호출, 005 `PostCardGrid`·`LoadMoreButton`, 뒤로 가기 복원 키 `tag:{name}`)를 만든다(T030 통과) (구현 메모: /tags 라우트는 T052에서 화면과 함께 더한다. 머리말 상태는 이름과 함께 보관해 effect 안 동기 setState를 피했다(react-hooks 규칙))
- [X] T038 [P] [US2] `F/components/TagList.tsx`의 링크를 `tagPath(tag)`로 바꾸고 구현 메모("008 소유")를 지운다
- [X] T039 [US2] T029의 `tag.posts` 행 전부 통과 확인(`PUBLISHED_PUBLIC`만 `INCLUDED`, SC-004 일부) (구현 메모: tag.posts 28행 통과)

**Checkpoint**: US1 + US2로 태그를 붙이고 눌러서 그 태그 글을 본다(MVP)

---

## Phase 5: User Story 3 - 작성 중 태그 자동완성 (Priority: P2)

**Goal**: 로그인한 작성자에게 내 태그와 공개 글 태그를 앞부분 일치로 제안하고, 실패해도 입력을 막지 않는다

**Independent Test**: 남의 비공개 글에만 쓰인 `이직준비`가 `이직` 입력에 제안되지 않는지, 내 비공개 글 태그는 제안되는지, 비회원은 401인지 확인한다

### Tests for User Story 3 ⚠️

- [X] T040 [P] [US3] 자동완성 통합 테스트 `T/tag/integration/TagSuggestIT.java`: `내_태그가_먼저_그다음_공개_글_수_순`(US3 #1, 최대 10, `mine` 값), `남의_비공개_전용_태그는_제안하지_않는다`(US3 #2), `내_비공개_글_태그는_제안한다`, `비회원은_401_인증_전은_허용`(US3 #3), `검색어_정리_결과가_비면_빈_목록`(`#`·`🔥`·공백), `밑줄은_와일드카드가_아니다`(`a_` 검색에 `ab` 없음, research R11), `금칙어_검색어도_거부하지_않는다`, `1분_61번째는_429`(code `TOO_MANY_REQUESTS`, `Retry-After`), `Redis_장애면_제한_없이_응답`(`RedisOutage`), `탈퇴_신청·숨김·휴지통_글만_쓴_태그는_공개_수_0` (구현 메모: 세션 저장소도 Redis라 장애 중에는 로그인 요청이 401이 된다 — Redis_장애면_제한_없이_응답은 TagSuggestService를 직접 61번 불러 확인했다. 정리 결과가 빈 검색어는 요청 제한을 세지 않는 시험을 더했다)
- [X] T041 [P] [US3] 화면 훅 테스트 `F/features/tag/__tests__/useTagSuggest.test.ts`(가짜 타이머): 0.3초 멈춤 전 호출 없음, `compositionstart`~`compositionend` 사이 호출 없음(US3 #4), 늦게 온 이전 응답 버림, 실패·429·빈 결과·불러오는 중에는 목록 없음(US3 #5). `TagInput.test.tsx`에 추가: 목록 `role="listbox"`·↑↓·Enter 선택·Esc 닫기, 제안이 있어도 Enter가 입력값을 그대로 새 태그로 넣는 경우(선택 없음) (구현 메모: useTagSuggest(text, composing, load) — 결과를 검색어에 묶어 두고 지금 검색어와 같을 때만 보여 effect 안 동기 setState 없이 '불러오는 중 빈 목록'을 만든다)
- [X] T042 [P] [US3] 권한 실행기 `T/tag/integration/permission/TagSuggestAction.java`(`tag.suggest`, 대상 글 태그 이름의 앞 두 글자로 검색해 포함 여부) — `tag-list.csv` `tag.suggest` 행(AUTHOR는 모든 상태 `INCLUDED`, ANONYMOUS 401) (구현 메모: tag.top·blog.tags 실행기도 같은 모양으로 함께 만들었다(T048·T056))

### Implementation for User Story 3

- [X] T043 [US3] `B/tag/infra/TagQueryRepository.java`에 `suggest(prefix, me, limit)`(research R11 SQL 한 번, `LIKE :prefix ESCAPE '\'`, 후보 상한 200 상수 + 주석)를 더한다 (구현 메모: LIKE 이스케이프는 역슬래시·_·% 세 글자. 결과 줄은 TagSuggestionRow)
- [X] T044 [US3] `B/tag/application/TagSuggestService.java`: `normalizeQuery(q)` → 빈 값이면 `[]` → `RateLimiter.acquireOrThrow("ratelimit:tag-suggest:" + memberId, limit, window)`(판정 순서 맨 끝 — 검색어 정리 뒤, SQL 앞) → `suggest`. `TagController`에 `@LoginRequired GET /api/tags/suggest`(현재 사용자는 세션에서만)를 더한다(T040 통과) (구현 메모: 트랜잭션 없이 Service에서 Redis 카운트 → SQL 한 문장. 응답 TagSuggestionView)
- [X] T045 [US3] `F/features/tag/useTagSuggest.ts`(요청 번호 `useRef`, `AbortController`, 300ms 상수 `TAG_SUGGEST_DEBOUNCE_MS`)와 `TagInput.tsx`의 제안 목록(`role="listbox"`, `aria-activedescendant`, 후보 표시 "#spring-boot · 3 · 내 태그")을 구현한다(T041 통과) (구현 메모: 입력칸은 role=combobox(aria-expanded·aria-controls·aria-activedescendant). 이미 붙인 태그는 후보에서 빼고, 초점을 잃으면 닫는다. 시험용 loadSuggestions prop)
- [X] T046 [US3] T042 행 통과 확인 (구현 메모: tag.suggest 28행 통과)

**Checkpoint**: 자동완성이 붙어도 실패·제한 때 입력과 발행이 막히지 않는다(SC-008)

---

## Phase 6: User Story 4 - 전체 태그 목록 보기 (Priority: P2)

**Goal**: `/tags`에서 공개 글에 많이 쓰인 태그 상위 100개를 본다

**Independent Test**: 공개 글 수가 다른 태그와 비공개 전용 태그를 만들고 `/tags`의 순서·포함 여부를 확인한다

### Tests for User Story 4 ⚠️

- [X] T047 [P] [US4] 통합 테스트 `T/tag/integration/TagIndexIT.java`: `공개_글_수_순_상위_100`(US4 #1, 같은 수면 이름 순, 101번째 없음, `limit` 쿼리 무시), `공개_글_수_0인_태그는_없다`(US4 #2), `없으면_빈_목록`(US4 #3), `비공개로_바꾸면_다음_요청에서_빠진다`(FR-029 — 비공개 전환·휴지통·숨김(`hidden_at` 직접 기록)·작성자 `withdrawn_at` 각각 바로 다음 요청) (구현 메모: 숨김은 hidden_at·hidden_by(관리자)·hidden_reason을 함께 기록했다(V1 제약))
- [X] T048 [P] [US4] 권한 실행기 `T/tag/integration/permission/TagTopAction.java`(`tag.top`, 대상 글만 쓰는 고유 태그가 목록에 있는가) (구현 메모: T042와 함께 만들었다)
- [X] T049 [P] [US4] 화면 테스트 `F/pages/__tests__/TagIndexPage.test.tsx`: `#이름 글 수` 목록과 링크(`tagPath`), 빈 목록 "아직 태그가 없어요", 실패 "불러오지 못했어요 [다시 시도]"

### Implementation for User Story 4

- [X] T050 [US4] `TagQueryRepository.top(limit)`(research R8 SQL, 캐시 없음)와 `TagQueryService.top()`, `TagController`의 `GET /api/tags`(`limit` 무시, `blog.tag.top-limit`)를 구현한다(T047 통과) (구현 메모: 응답 TagIndexView{items: TagCountView[]})
- [X] T051 [US4] `PageShellController`에 `GET /tags`(200 셸 + `LinkPreviewMetaFactory.forTagIndex()`)를 더한다. `TagPageShellIT`에 `태그_목록_주소는_200` 추가 (구현 메모: GET /tags 매핑과 forTagIndex()는 T035에서 함께 넣었고 여기서 시험을 더했다)
- [X] T052 [US4] `F/pages/TagIndexPage.tsx`(목록·빈 상태·375px 줄바꿈)를 만들고 T037의 `/tags` 라우트에 연결한다(T049 통과)
- [X] T053 [US4] T048 행 통과 확인 (구현 메모: tag.top 28행 통과)

**Checkpoint**: `/tags`가 매번 계산한 결과를 보인다

---

## Phase 7: User Story 5 - 블로그 안에서 태그로 거르기 (Priority: P3)

**Goal**: 블로그 위에 그 블로그의 태그와 글 수를 보이고 `?tag=`로 그 태그 글만 본다

**Independent Test**: 태그가 다른 공개 글과 비공개 글이 있는 블로그에서 태그 줄 순서·글 수와 필터 결과를 확인한다

### Tests for User Story 5 ⚠️

- [ ] T054 [P] [US5] 통합 테스트 `T/tag/integration/BlogTagIT.java`: `글_수_많은_순으로_태그와_수`(US5 #1, `initialVisible` = 10, 최대 100), `필터는_그_태그_글만_9개와_커서`(US5 #2, 커서 구분 `blog:{h}:tag:{t}` — 필터 없는 블로그 커서·태그 페이지 커서는 400), `비공개_전용_태그는_줄에_없다`(US5 #4, 주인이 봐도), `없는_블로그는_404`, `API의_정규화되지_않은_tag는_404`
- [ ] T055 [P] [US5] `TagPageShellIT`에 추가: `블로그_필터_대문자는_301`(US5 #3, `/@kim?tag=JPA` → `/@kim?tag=jpa`, 다른 쿼리 유지), `블로그_필터_형식_오류는_404`, `handle_대문자와_tag_대문자가_함께면_handle_먼저`
- [ ] T056 [P] [US5] 권한 실행기 `T/tag/integration/permission/BlogTagsAction.java`(`blog.tags`)
- [ ] T057 [P] [US5] 화면 테스트 `F/components/__tests__/BlogTagStrip.test.tsx`(10개 + [태그 더 보기] → 나머지 펼침, 버튼은 남은 수 표시, 태그 없으면 줄 숨김)와 `F/pages/__tests__/BlogPage.test.tsx` 추가(`?tag=jpa`면 "#jpa 글 5개 [필터 해제]" + 그 태그 목록, [필터 해제]는 `?tag` 없는 주소로, 태그 줄 밖 태그면 "#이름 [필터 해제]")

### Implementation for User Story 5

- [ ] T058 [US5] `TagQueryRepository.blogTags(viewer, ownerId, limit)`(`forViewer(viewer, ownerId)` 조건, 글 수 많은 순·이름 순)와 `TagQueryService.blogTags`, `B/tag/web/BlogTagController.java`(`GET /api/members/{handle}/tags`, 주인 찾기는 005 `BlogQueryService.requireOwner` — tag → discovery 의존을 피하려면 001 `MemberQueryService.findReadableBlogOwner`를 직접 부른다)를 구현한다(T054 일부 통과)
- [ ] T059 [US5] `B/discovery/application/BlogQueryService.java`의 `listPosts(handle, tag, cursor, viewer)`: `tag`가 있으면 `normalizeQuery`와 같지 않으면 `NotFoundException`, 태그 번호 없으면 빈 페이지, 있으면 `ListScope.blogTag` + `CardFilter(ownerId, tagId)`. `B/discovery/web/BlogController.java`에 `@RequestParam(required = false) String tag`를 더한다(T054 통과)
- [ ] T060 [US5] `PageShellController.blogShell`에 `?tag=` 처리(contracts/normalization.md §4 — handle 301 다음, 실패 404 화면, 다르면 301 `UriComponentsBuilder`로 그 값만 바꿔 인코딩)를 더한다(T055 통과)
- [ ] T061 [US5] `F/components/BlogTagStrip.tsx`와 `F/pages/BlogPage.tsx`(태그 줄은 머리말 아래, `useSearchParams`의 `tag`가 있으면 필터 머리말 + `listBlogPosts(handle, cursor, tag)`, 뒤로 가기 복원 키 `blog:{h}:tag:{t}`)를 구현한다. `headerSlot`·`sidebarSlot` 자리는 그대로 둔다(T057 통과)
- [ ] T062 [US5] T056 행 통과 확인 — 이로써 `tag-list.csv` 전 행이 `pending` 없이 통과한다(SC-004)

**Checkpoint**: 모든 스토리가 독립적으로 동작한다

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 성능 측정, 종단 확인, 접근성·화면 폭, 문서·인계

- [ ] T063 [P] 성능 측정 `T/tag/integration/TagPerformanceIT.java`(`@Tag("perf")`, 기본 빌드에서 제외): quickstart §4 시드를 SQL `generate_series`로 만들고 5가지 요청의 p95와 태그별 목록 `EXPLAIN (ANALYZE, BUFFERS)`를 출력한다. 결과를 quickstart §4 표에 적는다(SC-007, FR-029). 전체 태그 목록이 300ms를 넘으면 T073을 연다
- [ ] T064 [P] 종단 확인 `E/tag.spec.ts`(Playwright): quickstart §3의 1~9번(발행 칩 → 상세 태그 → 태그 페이지 → 301 → 404 → 비공개 전용 태그 빈 화면 → `/tags` → 블로그 필터)을 자동화한다
- [ ] T065 [P] 한글 입력기 확인: Playwright로 `compositionstart`/`compositionend`를 흉내 내 조합 중 `/api/tags/suggest` 요청이 없는지(quickstart §3-10). 실제 입력기 확인은 수동(크롬·사파리)으로 하고 결과를 quickstart에 적는다
- [ ] T066 [P] 375px 확인: 발행 창 칩 10개(30자 태그 포함)·태그 페이지·`/tags`·블로그 태그 줄에서 가로 스크롤 없음(`E/tag.spec.ts`의 `viewport: {width: 375}` 단계)
- [ ] T067 [P] 접근성 확인: 칩 오류가 색 + 글자로 표시되는지, Alt+방향키 안내가 `aria-live`로 읽히는지, 자동완성 `listbox`가 키보드만으로 쓰이는지 `@axe-core/playwright`(이미 있으면) 또는 수동 점검표로 확인
- [ ] T068 `grep -rn "TODO(008)\|008 소유\|008에서 교체\|008이 교체" backend/src frontend/src`가 0건인지 확인하고 남은 표시를 정리한다(002 T046·005 T033·T039 구현 메모)
- [ ] T069 [P] 012·013 인계 메모: `specs/012-trending-search/plan.md`·`specs/013-ai-tag-suggest/plan.md`(작성되어 있으면)의 의존 항목에 `TagNormalizer.normalizeQuery`/`normalize`, `normalizeTag.ts`, `tagPath.ts`의 위치와 계약(contracts/normalization.md §5)을 적는다
- [ ] T070 [P] 원문 문서 갱신 제안: `docs/22-tag.md` §5~§8 API 표 옆에 "구현 계약은 specs/008-tag/contracts/openapi.yaml (머리말 `/summary`, 캐시 없음, 오류 형식 O8)"를 덧붙이는 변경을 팀에 제안한다(원문 수정은 팀 승인 후)
- [ ] T071 quickstart.md §1~§5를 처음부터 끝까지 실행하고 결과를 기록한다
- [ ] T072 전체 회귀: `./mvnw -pl backend verify`(002·005 테스트 포함)와 `npm test`·`npm run build`·`npm run lint`

### 조건부 작업

- [ ] T073 (T063에서 전체 태그 목록 p95 > 300ms일 때만) "공개에서 빠질 때 지우는 캐시"(Clarifications Q1 B안): Redis `tags:top`(TTL 10분) + `PostWentPublic`·`PostVisibilityChanged`·`PostTrashed`·`PostRestored`·`PostPurged`·숨김(014)·탈퇴 신청/철회(015) 이벤트의 AFTER_COMMIT 리스너에서 키 삭제. Redis 장애면 매번 계산으로 돌아간다. `TagIndexIT#비공개로_바꾸면_다음_요청에서_빠진다`가 그대로 통과해야 한다
- [ ] T074 (팀이 태그 페이지 sitemap 포함을 정하면) 012 sitemap 작업에 태그 페이지 주소 생성을 넘긴다 — 현재 결정은 "포함하지 않음"(012 Clarifications)
- [ ] T075 (T002 결과 "운영 배포가 먼저"일 때만) 정리 마이그레이션 `R/db/migration/V{다음 번호}__tag_renormalize.sql` + Java 마이그레이션 `B/tag/infra/migration/V{n}__TagRenormalize.java`: 모든 `tag.name`을 `TagNormalizer.normalizeQuery`로 바꾸고, 같은 이름이 되면 하나로 합친 뒤 `post_tag`를 옮긴다(한 글에 겹치면 작은 `position` 유지, 나머지 `position` 다시 매김), 형식 실패 태그는 연결을 지우고 태그를 남겨 로그 집계만. 번호는 ANALYSIS-tier-bc의 마이그레이션 번호 배정을 따른다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001·002·004·005의 해당 작업이 끝나야 Phase 1 확인(T001)을 통과한다
- **Setup (Phase 1)**: T002는 답을 기다리는 동안 다른 작업을 막지 않는다
- **Foundational (Phase 2)**: Setup 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
  - US2의 T037(라우트)은 **006 머지 후**
  - US4의 T052는 T037의 라우트 파일을 같이 쓴다(T037 후)
- **Polish (Phase 8)**: 원하는 스토리 완료 후. T063은 US2·US4 후, T064는 US1·US2·US4·US5 후

### User Story Dependencies

- **US1 (P1)**: Foundational 이후. 다른 스토리에 의존하지 않는다
- **US2 (P1)**: Foundational 이후. 테스트 데이터는 US1의 발행으로 만들지만 SQL 시드로도 가능해 독립 실행된다
- **US3 (P2)**: US1의 `TagInput`(T024) 후 화면 작업(T045). 서버 작업(T043·T044)은 Foundational 후 독립
- **US4 (P2)**: Foundational 이후 독립(`TagQueryRepository`는 T031 후 같은 파일)
- **US5 (P3)**: Foundational 이후. `TagQueryRepository`·`PageShellController`·`TagPageShellIT`를 US2와 같이 쓰므로 그 작업 뒤에 붙인다

### Within Each User Story

- 테스트 작업을 먼저 쓰고 실패를 확인한 뒤 구현한다
- 도메인 → 저장소 → Service → Controller → 페이지 셸 → 화면 API → 훅 → 컴포넌트 → 페이지
- 같은 파일을 고치는 작업은 순서대로 한다: `TagQueryRepository`(T031 → T043 → T050 → T058), `TagController`(T034 → T044 → T050), `PageShellController`(T035 → T051 → T060), `TagPageShellIT`(T028 → T051 → T055), `TagInput.tsx`(T024 → T045), `TagInput.test.tsx`(T019 → T041), `App.tsx`(T037 → T052), `BlogQueryService`(T014 → T059)

### Parallel Opportunities

- Phase 1: T003·T004 병렬
- Phase 2: 테스트 T005~T009 병렬, 구현 T010·T011·T013·T015·T016 병렬
- US1: 테스트 T017~T020 병렬
- US2: 테스트 T027~T030 병렬, T038은 언제든
- US3·US4 서버 작업은 서로 다른 메서드지만 같은 파일이라 한 사람이 순서대로, 화면 작업(T045·T052)은 병렬
- US5: 테스트 T054~T057 병렬
- Polish: T063~T067·T069·T070 병렬

---

## Parallel Example: User Story 1

```bash
# User Story 1 테스트를 함께 작성:
Task: "PublishValidatorTest 추가 in backend/src/test/java/com/team/blog/post/domain/PublishValidatorTest.java"
Task: "TagPublishIT in backend/src/test/java/com/team/blog/tag/integration/TagPublishIT.java"
Task: "TagInput.test.tsx in frontend/src/components/editor/__tests__/TagInput.test.tsx"
Task: "PublishDialog.test.tsx 갱신"

# 서버와 화면을 함께 구현:
Task: "TagService 최종 구현 + PostReasonCode.INVALID_TAG 이동 + PublishValidator (backend)"
Task: "TagInput.tsx (frontend)"
```

## Parallel Example: Foundational

```bash
Task: "InvisibleCharactersTest", "TagNormalizerTest", "ListScopeTest", "PostCardTagFilterIT", "normalizeTag.test.ts / tagPath.test.ts"
Task: "InvisibleCharacters + TitleNormalizer", "TagReasonCode + TagNormalization", "ListScope", "normalizeTag.ts/tagPath.ts/tagMessages.ts", "api/tags.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2)

1. Phase 1 확인(T001), 배포 순서 질문(T002), 설정·CSV(T003·T004)
2. Phase 2 Foundational
3. Phase 3 US1 → **STOP and VALIDATE**: 예시 입력 발행, 동시 10건, 002 점검표 회귀
4. Phase 4 US2 → 태그 페이지·301·404. 여기까지가 **권장 MVP**(C-TAG-1 #1~#8 중 자동완성 제외 전부)
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 정규화 규칙(서버·화면)
2. US1 → 발행 칩 → 데모
3. US2 → 태그 페이지 → 데모
4. US3 → 자동완성
5. US4 → 전체 태그 목록
6. US5 → 블로그 태그 줄·필터
7. Polish → 성능 측정·종단 확인

### Parallel Team Strategy

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A: US1 서버 → US2 서버 → US4 서버(`TagQueryRepository`·`TagController` 소유)
   - Developer B: US1 화면(`TagInput`) → US3 화면 → US2·US4 화면
   - Developer C: US3 서버 → US5(서버·화면)
3. `PageShellController`는 A가 소유하고 US5의 셸 변경(T060)은 A의 T035·T051 뒤에 붙인다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 001·002·005 소유 파일을 고치는 작업(T010·T013·T014·T022·T023·T025·T035·T036·T038·T059·T060·T061)은 그 기능의 회귀 테스트를 함께 돌리고 담당에게 알린다
- 금칙어 단어는 테스트·문서·로그 어디에도 직접 적지 않는다(`policy/banned-words.txt`에서 읽음)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
