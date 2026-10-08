---

description: "Task list for 016-dark-mode (다크 모드: 테마 선택·색 토큰·명도 대비)"
---

# Tasks: 다크 모드

**Input**: Design documents from `/specs/016-dark-mode/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (theme.md), quickstart.md

**Tests**: 포함한다. 서버·DB 규칙이 없는 화면 기능이라 헌법 VIII 대상은 없고, 대신 Vitest(토큰·검사·상태)와 Playwright(첫 그리기·CSP·axe)로 확인한다. 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

**두 묶음 (Clarifications Q1)**: 작업마다 **[공통]**(모든 서비스 — 토큰 이름·라이트 값·색 직접 쓰기 금지) 또는 **[선택]**(다크 모드를 켠 서비스만 — 테마 결정·버튼·다크 값·axe)을 붙였다. 다크 모드를 만들지 않는 서비스는 Phase 2 Checkpoint에서 멈추고 `VITE_DARK_MODE=false`로 빌드한다. 이 저장소(나민서)는 둘 다 한다.

## Cross-feature Dependencies

이 기능은 서버·DB를 바꾸지 않는다(정적 파일 캐시 설정 한 곳 제외). 대부분 다른 기능이 만든 화면 파일의 **색 변수 이름만** 바꾼다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 — `B/shared/web/SecurityHeadersFilter.java`(CSP `script-src 'self'`), `CacheControlPolicy`, `F/features/auth/SessionBar.tsx`·`logout.ts`, `.visually-hidden`(`F/features/auth/auth.css`)
- 선행: specs/002 — `F/pages/editor.css`, `F/features/editor/highlightCode.ts`(highlight.js 클래스만 붙임)
- 선행: specs/004 — `NotFoundPageRenderer`(빌드된 셸 사용), `VisibilityBadge`
- 선행: specs/005 — `PostCard`·`AuthorCard`·`TagList`·`ReactionBar`·`LoadMoreButton`·`DefaultAvatar`(005 임시)·`AuthorStatusBanner`·`PostDetailPage`·`BlogPage`, `B/shared/web/shell/SpaShellRenderer.java`

**006 머지 후**

- `F/components/dialogs.css`·`F/features/manage-posts/managePosts.css`의 색 이름 바꾸기(T016). 006이 만드는 파일이다
- `F/App.tsx` 머리말에 버튼을 두는 작업(T031). 006이 같은 파일에 `/manage/posts` 경로를 더한다

**먼저 하는 쪽이 만든다 (다른 기능과 같은 파일)**

- `F/components/DefaultAvatar.tsx` 원 색 — 001 T120이 8색(`--avatar-1..8`, data-model §2-3)으로 바꾼다. 이 기능은 T014에서 이름만 바꾸고, 001 T120이 먼저면 8색이 토큰을 쓰는지 확인만

**후속 (다른 스펙이 이 기능을 사용)**

- 007~015의 새 화면: 처음부터 토큰만 쓰고 `noRawColors.test.ts`(T008)를 통과해야 한다(ANALYSIS-tier-bc 공통 규칙)
- 001 T120: 기본 프로필 8색 값(data-model §2-3)
- 강성찬·김민서 서비스: 토큰 이름 11개(contracts/theme.md §5)는 같게, 값·다크 모드 구현은 각자

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`, `P/` = `frontend/public/`, `V/` = `frontend/vite/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 설정값, 테스트 도우미, 팀 확인 질문

- [X] T001 [공통] 선행 확인: `F/` 아래 색 값이 있는 파일·줄 수(지금 10개 파일 36줄 — `editor.css` 19, `PostCard.tsx` 5, `ReactionBar.tsx` 3, `BlogPage.tsx`·`AuthorStatusBanner.tsx` 각 2, 나머지 각 1), 006의 `dialogs.css`(6)·`managePosts.css`(7), 001 T120 `DefaultAvatar` 상태, 001 `SecurityHeadersFilter` CSP 값, 005 `SpaShellRenderer`·004 `NotFoundPageRenderer`가 `classpath:static/index.html`을 읽는지, `frontend/index.html`에 인라인 스크립트가 없는지 기록한다
- [X] T002 [P] [선택] 설정값 `F/config.ts`에 `DARK_MODE_ENABLED = import.meta.env.VITE_DARK_MODE !== 'false'`, 타입 `F/vite-env.d.ts`(없으면 만들고 `ImportMetaEnv.VITE_DARK_MODE?: string`), `frontend/.env.example`이 있으면 `VITE_DARK_MODE=true` 한 줄(비밀값 아님)
- [X] T003 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다: ① 보조 토큰(`--color-overlay`·`--color-notice-bg`·`--color-warning-*`·`--diff-*`·`--color-toast-*`·`--color-on-fill`·`--avatar-*`·`--hljs-*`) 이름은 공통 규격에서 뺀다 ② 브랜드 제안값 라이트 `#1971C2`/`#1971C2`, 다크 `#74C0FC`/`#1864AB` ③ 원문 `--color-border`는 입력칸 3:1을 못 넘으므로 입력칸·버튼 윤곽은 `--color-text-muted` ④ github 강조 색 4개를 대비 때문에 바꾼 값(data-model §2-4). 답이 오기 전에는 기본안
- [X] T004 팀 확인 질문: ① 새 개발 의존성 `@axe-core/playwright`(선택 묶음 e2e만) ② 공통 저장소의 `VITE_DARK_MODE` 기본값 `true`(다크 모드를 만들지 않는 서비스는 `false`로 빌드) ③ 007~015 새 화면에도 `noRawColors` 검사를 공통 규칙으로. 답이 오기 전에는 기본안(①은 T036을 답 뒤로 미룬다)
- [X] T005 [P] [선택] 테스트 도우미 `F/test/matchMedia.ts`(jsdom에 없는 `window.matchMedia`를 흉내 — `setPrefersDark(bool)`이 `change` 이벤트를 보냄)와 `F/test/storage.ts`(`localStorage` 접근이 예외를 던지게 하는 `blockStorage()`·`restoreStorage()`). `F/test/setup.ts`는 고치지 않고 테스트마다 불러 쓴다

---

## Phase 2: Foundational (Blocking Prerequisites) — 공통 묶음

**Purpose**: 토큰 파일, 기본 스타일, 코드 강조, Tier A 이름 바꾸기, 색 직접 쓰기 검사 — 다크 모드를 만들지 않는 서비스도 여기까지 한다

**⚠️ CRITICAL**: 이 Phase가 끝나기 전에는 User Story 작업을 시작하지 않는다

- [X] T006 [P] [공통] 테스트 `F/styles/__tests__/tokenContrast.test.ts`: `tokens.css`를 `fs`로 읽어 라이트 블록(`:root, [data-theme="light"]`)의 contracts/theme.md §7 짝을 WCAG 식으로 계산(16진 → 상대 휘도), 공통 토큰 11개가 모두 정의돼 있는지, 반투명 값은 짝에서 빠지는지. `[data-theme="dark"]` 블록이 있으면 같은 짝을 다크로도(T033이 다크 짝을 더 엄격히)
- [X] T007 [공통] `F/styles/tokens.css`: data-model §2-1~§2-4 값 전부(공통 11, 보조 11, 아바타 8, 코드 강조 14), 라이트는 `:root, [data-theme="light"]`, 다크는 `[data-theme="dark"]`(다크 블록은 [선택] — 끈 서비스는 지워도 됨), `transition` 없음. `F/main.tsx` 맨 위에 `import './styles/tokens.css'`(다른 CSS보다 먼저)(T006 통과)
- [X] T008 [P] [공통] 테스트 `F/styles/__tests__/noRawColors.test.ts`: contracts/theme.md §6 규칙(16진·함수 색·색 이름 값·`var()` 대체 값·색 `transition`·사진 `filter`, 허용 값, 주소 조각·엔티티·주석 무시, `raw-color-ok: 이유` 예외)과 실패 메시지 `파일:줄: 색 값을 직접 쓰지 말고 토큰을 쓰세요 (찾은 값)`. 규칙 자체를 시험하는 표 테스트(잡아야 할 문자열 10개·잡지 말아야 할 문자열 8개)를 같은 파일에. 지금은 T001 목록 때문에 실패해야 한다
- [X] T009 [공통] `F/styles/base.css`: `body { background: var(--color-bg); color: var(--color-text) }`, `a { color: var(--color-brand) }`, `:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px }`, `input, textarea, select { border-color: var(--color-text-muted); background: var(--color-surface); color: var(--color-text) }`, `:root[data-theme="light"] { color-scheme: light }`, `:root[data-theme="dark"] { color-scheme: dark }`(FR-020), `pre, code { background: var(--color-code-bg) }`. `F/main.tsx`에서 `tokens.css` 다음에 불러온다
- [X] T010 [공통] `F/styles/code-highlight.css`: highlight.js 11 `github.css`·`github-dark.css`의 선택자 묶음을 data-model §2-4 표대로 `var(--hljs-*)`로 칠한다(굵게·기울임 포함, 배경은 `--color-code-bg`), 파일 머리에 원본 출처와 BSD-3 라이선스 표기. `F/main.tsx`에서 `base.css` 다음에 불러온다. 002 `highlightCode.ts`는 고치지 않는다
- [X] T011 [P] [공통] 정적 파일 캐시: `B/shared/web/CacheControlPolicy.java`에 `STATIC_REVALIDATE = "no-cache"`·`STATIC_IMMUTABLE = "max-age=31536000, immutable"`(001 소유, 추가만), `B/shared/web/StaticResourceCacheConfig.java`(`WebMvcConfigurer.addResourceHandlers` — `/js/**` `noCache()`, `/assets/**` 1년 `immutable`, 위치 `classpath:/static/`), 테스트 `T/shared/web/StaticResourceCacheConfigTest.java`(MockMvc: 두 경로 머리, `/js/theme-init.js` `ETag`·조건부 요청 304, 없는 경로는 기존 404 본문·`private, no-store` 그대로, SPA 셸 머리 그대로 — data-model §4)
- [X] T012 [P] [공통] 005 부품 이름 바꾸기(research R4 표): `F/components/PostCard.tsx`(`--card-bg`→`--color-surface`, `--card-border`→`--color-border`, `--card-thumb-empty-bg`→`--thumb-empty`, `--card-excerpt-color`·`--card-meta-color`→`--color-text-muted`), `AuthorCard.tsx`, `TagList.tsx`(`--tag-bg`→`--thumb-empty`, 칩 글자 `--color-text`), `ReactionBar.tsx`(`--divider`→`--color-border`), `LoadMoreButton.tsx`(테두리 `--color-text-muted`). 모든 `var(--x, #색)` 대체 값 삭제, 크기 변수는 그대로(005 소유 파일, 이름만). 005 화면 테스트가 그대로 통과하는지 확인
- [X] T013 [P] [공통] 005 화면 이름 바꾸기: `F/features/post-detail/AuthorStatusBanner.tsx`(`--notice-bg`→`--color-notice-bg`, `--badge-bg`→`--thumb-empty`), `F/pages/PostDetailPage.tsx`, `F/pages/BlogPage.tsx`(`--muted`→`--color-text-muted`)(005 소유 파일, 이름만)
- [X] T014 [P] [공통] `F/components/DefaultAvatar.tsx`(005 임시): `--card-avatar-bg`→`--thumb-empty`, 대체 값 삭제. 001 T120이 이미 8색으로 바꿨으면 `--avatar-1..8`·`--color-on-fill`을 쓰는지 확인만
- [X] T015 [P] [공통] 002 `F/pages/editor.css` 19줄: 면·글자·테두리는 공통 토큰, 대화 상자 뒤는 `--color-overlay`, 충돌·저장 경고 상자는 `--color-warning-bg`·`--color-warning-border`, 비교 화면은 `--diff-*`(002 소유 파일, 색 값만). 입력칸 테두리는 `--color-text-muted`. 002 화면 테스트·`E/editor-responsive.spec.ts`가 그대로 통과하는지 확인
- [X] T016 [공통] 006 `F/components/dialogs.css`(6줄, 뒤 어둡게 `--color-overlay`·알림 줄 `--color-toast-*`)·`F/features/manage-posts/managePosts.css`(7줄) 이름 바꾸기(**006 머지 후**, 006 소유 파일, 색 값만)
- [X] T017 [공통] T008이 더 찾는 곳(001 `auth.css`·`SessionBar`, 004 화면 등 — T001 이후 새로 들어온 파일 포함)을 고쳐 T008을 통과시킨다. 고칠 수 없는 줄은 `raw-color-ok: 이유` 주석과 함께 목록을 남긴다(T012~T016 다음)

**Checkpoint**: 공통 묶음 완료 — 모든 화면이 토큰만 쓰고 라이트 값으로 예전과 같게 보인다. 다크 모드를 만들지 않는 서비스는 여기서 멈춘다

---

## Phase 3: User Story 1 - 처음 방문하면 기기 설정에 맞는 테마로 보인다 (Priority: P1) 🎯 MVP — 선택 묶음

**Goal**: 저장된 선택이 없으면 기기 설정을 따르고, 첫 화면부터 그 테마로 그려진다

**Independent Test**: 기기 설정 다크인 새 브라우저로 여러 페이지를 열어 처음부터 다크인지, 첫 그리기에 밝은 배경이 없는지 본다(quickstart §3 1·7·13)

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T018 [P] [US1] [선택] 테스트 `F/features/theme/__tests__/themeInit.test.ts`: `P/js/theme-init.js`를 읽어 jsdom 창에서 실행(T005 도우미) — contracts/theme.md §3 표 6행(US1 #1·#3·#4), 결과가 두 속성뿐이고 전역 변수가 생기지 않음, 파일 1,024바이트 미만
- [X] T019 [P] [US1] [선택] 테스트 `V/__tests__/themeHead.test.ts`: 플러그인의 `transformIndexHtml`이 `VITE_DARK_MODE` 없음·`true`면 `<meta name="color-scheme" content="light dark">`·`<script src="/js/theme-init.js">`(속성 `defer`·`async`·`type` 없음)를 `injectTo: 'head-prepend'`로 돌려주고, `false`면 빈 배열. `frontend/vitest.config.ts` `include`에 `vite/**/*.test.ts`, `tsconfig.node.json` `include`에 `vite/**/*.ts`를 더한다
- [X] T020 [US1] [선택] 테스트 `E/theme.spec.ts`(US1 부분): `colorScheme: 'dark'` 새 문맥에서 앱 JS(`/assets/*.js`) 응답을 붙잡아 둔 채 화면 캡처 → `<html>` 배경 `#121212`(SC-001), 홈·글 상세·블로그·404 각각 `data-theme="dark"`(US1 #1·#2), `colorScheme: 'light'` → `light`(US1 #3), `addInitScript`로 `localStorage` 접근을 막아도 오류 없이 기기 설정(US1 #4), `securitypolicyviolation` 수집 0건(SC-003), `DARK_MODE_ENABLED`가 꺼진 빌드면 `test.skip`

### Implementation for User Story 1

- [X] T021 [P] [US1] [선택] `P/js/theme-init.js`(research R6 코드 그대로, 모듈 아님)(T018 통과)
- [X] T022 [US1] [선택] `V/themeHead.ts` 플러그인과 `frontend/vite.config.ts` `plugins`에 `themeHead()` 등록(`loadEnv`로 `VITE_DARK_MODE`). `npm run build` 후 `dist/index.html`에서 두 태그가 `<meta charset>` 다음, CSS `<link>`보다 앞인지 확인(T019 통과)
- [X] T023 [US1] [선택] 서버 셸 확인: 테스트 셸 픽스처 `backend/src/test/resources/static/index.html`에 두 태그를 더하고, 005 `T/shared/web/shell/SpaShellRendererTest.java`·004 `T/shared/web/NotFoundPageRendererTest.java`에 렌더 결과가 두 태그를 `<!--app-head-->` 치환 뒤에도 그대로 앞에 두는지 단언을 하나씩 추가한다(005·004 소유 테스트 파일·픽스처, 추가만). 렌더러 코드는 바꾸지 않는다(T020 통과)

**Checkpoint**: 기기 설정을 따르는 첫 화면이 깜빡임 없이 그려진다

---

## Phase 4: User Story 2 - 테마를 직접 고르고, 그 선택이 유지된다 (Priority: P1) — 선택 묶음

**Goal**: 머리말 맨 오른쪽 버튼으로 시스템 → 라이트 → 다크를 돌리고, 선택이 새로 고침·이동·로그아웃 뒤에도 남으며, "시스템"이면 기기 설정을 바로 따라간다

**Independent Test**: 버튼으로 세 상태를 돌리며 새로 고침·이동 후 유지되는지, "시스템"에서 기기 설정을 바꿔 즉시 반영되는지 본다(quickstart §3 2~6·14)

### Tests for User Story 2 ⚠️

- [X] T024 [P] [US2] [선택] 테스트 `F/features/theme/__tests__/themeStore.test.ts`: `readChoice`(없음·`light`·`dark`·모르는 값·예외), `writeChoice`(`system`이면 `removeItem`, 예외 삼킴), `nextChoice` 순서(US2 #1), `resolve(choice, prefersDark)` 6가지
- [X] T025 [P] [US2] [선택] 테스트 `F/features/theme/__tests__/useTheme.test.ts`: 처음 값은 `document.documentElement.dataset.themeChoice`, `setChoice`가 같은 동기 호출 안에서 두 속성·저장을 바꿈, `system`일 때만 `matchMedia` `change` 구독(US2 #3), 고정 상태에서는 기기 설정을 바꿔도 그대로(US2 #4), 고정 → 시스템이면 구독 시작·해제 누수 없음
- [X] T026 [P] [US2] [선택] 테스트 `F/features/theme/__tests__/ThemeToggle.test.tsx`: 세 상태의 아이콘(`aria-hidden`)·`aria-label`·`title`(contracts/theme.md §4 표), 누를 때마다 다음 상태·즉시 `data-theme` 변경, `aria-live` 안내 3가지, `DARK_MODE_ENABLED=false`면 아무것도 그리지 않음
- [X] T027 [P] [US2] [선택] 001 `F/features/auth/logout.test.ts`에 "로그아웃 뒤 `localStorage.theme`가 남는다" 한 경우 추가(US2 #5, 001 소유 테스트 파일, 추가만)
- [X] T028 [US2] [선택] 테스트 `E/theme.spec.ts`(US2 부분, T020과 같은 파일 — 다음에): 버튼 세 번 순환(US2 #1), 다크 고른 뒤 새로 고침·글 상세 이동·뒤로 가기에서 유지(US2 #2, SC-002), `page.emulateMedia({ colorScheme })`로 바꾸면 시스템 상태에서 즉시 따라감(US2 #3, SC-005)·라이트 고정 상태에서 그대로(US2 #4), 로그인 → 다크 → 로그아웃 뒤 다크(US2 #5), 새 문맥(다른 기기 흉내)은 기기 설정(US2 #6), `mobile` 프로젝트(375px)에서 머리말 가로 넘침 없음

### Implementation for User Story 2

- [X] T029 [P] [US2] [선택] `F/features/theme/themeStore.ts`(research R8, data-model §1)(T024 통과)
- [X] T030 [US2] [선택] `F/features/theme/useTheme.ts`(T025 통과)
- [X] T031 [US2] [선택] `F/features/theme/ThemeToggle.tsx`(안내 영역은 001 `.visually-hidden` 사용, 스타일은 토큰만)와 `F/App.tsx` 머리말 줄 맨 오른쪽(`SessionBar` 옆)에 `DARK_MODE_ENABLED`일 때 배치(**006 머지 후** — 같은 파일). 001 공통 머리말이 생기면 그쪽 맨 오른쪽으로 옮긴다(T026·T028 통과)
- [X] T032 [US2] [선택] 001 `F/features/auth/logout.ts`가 `theme`를 지우지 않는지 확인만(주석에 이미 있음 — 코드 변경 없음)(T027 통과)

**Checkpoint**: US1 + US2 = 다크 모드 MVP

---

## Phase 5: User Story 3 - 다크에서도 글과 화면 요소가 잘 읽힌다 (Priority: P2) — 선택 묶음

**Goal**: 다크 값이 대비 기준을 지키고, 사진은 그대로, 코드 강조·배지·에디터·빈 썸네일이 다크에서 읽힌다

**Independent Test**: 주요 화면 5개를 라이트·다크로 axe 검사해 위반 0건(quickstart §3 8~12)

### Tests for User Story 3 ⚠️

- [X] T033 [US3] [선택] `F/styles/__tests__/tokenContrast.test.ts`(T006과 같은 파일 — 다음에): 다크 블록이 있으면 §7 짝 전부 필수, 다크 블록에 공통 11개가 모두 다시 정의돼 있는지(빠지면 라이트 값이 새어 나옴), `--thumb-empty`와 `--color-surface`가 같은 값이 아닌지(US3 #5)
- [X] T034 [US3] [선택] 테스트 `E/theme.spec.ts`(US3 부분, T028 다음): 다크에서 본문 사진·썸네일·프로필 사진의 계산된 `filter`·`mix-blend-mode`·`opacity`가 라이트와 같음(US3 #2, SC-006), 코드 블록 `.hljs-keyword` 계산 색이 다크 값(US3 #3), 비공개 배지에 "비공개" 글자(US3 #4), 썸네일 없는 카드 빈 영역 색 ≠ 카드 면(US3 #5), 에디터 입력칸·미리보기·비교 화면이 다크 면
- [X] T035 [US3] [선택] 테스트 `E/theme.spec.ts` axe 부분(T034 다음, T036 뒤에 켬): 홈·블로그·글 상세(코드 블록 있는 글)·에디터·설정 5개 화면 × `colorScheme` 라이트·다크, `color-contrast` 규칙 위반 0(US3 #1, SC-004). 설정 화면이 아직 없으면(001 T122 전) 그 화면만 `test.fixme`

### Implementation for User Story 3

- [X] T036 [US3] [선택] `frontend/package.json` `devDependencies`에 `@axe-core/playwright`(다른 개발 의존성처럼 `^` 범위)와 `package-lock.json` 갱신(**T004 답 뒤**)
- [X] T037 [US3] [선택] T034·T035가 찾은 다크 문제를 고친다: 토큰을 잘못 고른 곳(입력칸에 `--color-border`, 칩 글자에 `--color-text-muted`, 삭제 버튼 면에 `--color-danger` 등 data-model §2-1 "쓰지 않는 조합")을 바꾸고, 값이 문제면 `tokens.css` 다크 값을 고친 뒤 T033을 다시 돌린다
- [X] T038 [US3] [선택] 확인만 기록: 004 `VisibilityBadge`·005 수정 중 안내·006 관리 목록 배지가 글자·아이콘을 가짐(FR-019), 002 에디터는 자체 `textarea`라 토큰만으로 다크(FR-018), 005 `LinkPreviewMeta`·001 메일 템플릿은 서버가 만든 라이트 기준이라 변경 없음(FR-021)

**Checkpoint**: 모든 User Story 동작, 대비 위반 0

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 끈 빌드 확인, 다른 기능 인계, 종단 확인

- [X] T039 [공통] 끈 빌드 확인: `VITE_DARK_MODE=false npm run build` → `dist/index.html`에 두 태그 없음, 버튼 없음, 운영체제 다크에서도 라이트, `noRawColors`·라이트 `tokenContrast` 통과, 선택 묶음 테스트 건너뜀(contracts/theme.md §9 표)
- [X] T040 [P] [공통] 인계 기록: 001 T120 담당에게 8색 값(data-model §2-3)과 `--color-on-fill`, 007~015 담당에게 "새 화면은 토큰만 — `noRawColors` 통과"를 ANALYSIS-tier-bc 공통 규칙에서 가리킨다. 강성찬·김민서 서비스에 토큰 이름 11개(contracts/theme.md §5)와 에디터 다크 지원 여부 확인(spec Assumptions)을 남긴다
- [X] T041 quickstart.md §1~§4 실행 결과를 기록하고 어긋난 문서를 고친다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작
- **Foundational (Phase 2, 공통 묶음)**: Setup 다음 — 모든 User Story를 막는다. T016은 006 머지 후(다른 작업과 독립), T017은 T012~T016 다음
- **US1 (Phase 3)**: Foundational 다음
- **US2 (Phase 4)**: Foundational 다음. 첫 값을 `data-theme-choice`에서 읽으므로 US1(T021·T022) 다음이 자연스럽지만, 단위 테스트는 속성을 직접 넣어 독립 진행 가능. T031은 006 머지 후
- **US3 (Phase 5)**: US1·US2 다음(다크 상태를 만들 수 있어야 검사). T036은 T004 답 뒤
- **Polish (Phase 6)**: 원하는 스토리가 끝난 뒤

### User Story Dependencies

- **US1 (P1)**: 독립
- **US2 (P1)**: 화면 끝까지 확인은 US1 위에서
- **US3 (P2)**: US1·US2 위에서 다크 값·요소를 검사

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 같은 파일을 고치는 작업은 순서대로 한다: `F/main.tsx`(T007 → T009 → T010), `tokenContrast.test.ts`(T006 → T033), `E/theme.spec.ts`(T020 → T028 → T034 → T035), `tokens.css`(T007 → T037), `CacheControlPolicy.java`(T011, 001 소유), 005 파일(T012·T013·T014 — 서로 다른 파일), 002 `editor.css`(T015), 006 파일(T016, `F/App.tsx` T031), 001 테스트(T027)

### Parallel Opportunities

- Phase 2의 T006·T008·T011·T012·T013·T014·T015는 서로 다른 파일
- US1 테스트 T018·T019, US2 테스트 T024·T025·T026·T027은 서로 다른 파일
- 서버 작업(T011)은 화면 작업과 독립

---

## Parallel Example: Phase 2 (공통 묶음)

```bash
# 검사 테스트와 이름 바꾸기를 함께 한다
Task: "tokenContrast.test.ts in F/styles/__tests__/tokenContrast.test.ts"
Task: "noRawColors.test.ts in F/styles/__tests__/noRawColors.test.ts"
Task: "005 부품 이름 바꾸기 in F/components/"
Task: "002 editor.css 토큰 in F/pages/editor.css"
Task: "StaticResourceCacheConfig in B/shared/web/StaticResourceCacheConfig.java"
```

---

## Implementation Strategy

### MVP First (공통 묶음 + User Story 1 + 2)

1. Phase 1·2 완료(공통 묶음 — 모든 서비스)
2. US1: `theme-init.js`·플러그인 — 기기 설정 따라 첫 화면
3. US2: 버튼·저장·시스템 따라가기
4. **STOP and VALIDATE**: quickstart §3 1~7·13·14

### Incremental Delivery

1. 공통 묶음 → 라이트 화면이 예전과 같고 토큰만 씀(다른 두 서비스도 이 상태로 병합 가능)
2. US1 + US2 → 다크 모드 MVP
3. US3 → 대비·요소 검사 → Polish

### Parallel Team Strategy

1. 함께 Phase 1·2(이름 바꾸기는 파일별로 나눔)
2. 그 뒤: 개발자 A US1 → US3, 개발자 B US2

---

## Notes

- [P] = 다른 파일, 의존 없음
- 화면 요소는 색 값을 직접 쓰지 않는다 — 새 색이 필요하면 `tokens.css`에 토큰을 먼저 더한다(FR-011)
- 인라인 스크립트·`eval` 금지(CSP, 헌법 IV). 인라인 `style`에도 색 값 대신 `var(--토큰)`
- 색 전환 애니메이션·사진 필터 금지(FR-010·FR-015) — `noRawColors`가 함께 검사
- 다른 기능이 소유한 파일은 색 변수 이름만 바꾸고 동작은 건드리지 않는다
- 각 작업 또는 논리 묶음마다 커밋한다

---

## 구현 기록 (2026-10-08)

41개 작업 완료. 계획과 달라진 점:

- **T001 선행 확인**: 계획 당시 10개 파일 36줄에서, 구현 시점에는 15개 파일 60줄(001 `defaultAvatarColor.ts` 9, `siteHeader.css` 2, 004 `visibility.css`·001 `settings.css` 각 1 추가). `frontend/index.html`에 인라인 스크립트 없음, 두 서버 셸은 `classpath:static/index.html`을 읽음, CSP `script-src 'self'` 확인.
- **T002**: `vite-env.d.ts`는 만들지 않았다. `vite/client` 타입의 `ImportMetaEnv`가 `VITE_*`를 이미 받는다(같은 방식의 `VITE_FRIENDS_VISIBILITY`가 있음). `.env.example`은 프론트 폴더에 없어 건드리지 않음(기본 켬).
- **T003·T004**: ANALYSIS-tier-bc R14 "기본안 승인"(팀 결정 10)대로. `@axe-core/playwright`를 개발 의존성으로 넣었다(T036).
- **T007 보조 토큰**: 계획 목록에 `--color-shadow`(머리말 메뉴 그림자), `--color-success`(설정 "확인됨" 글자), `--color-danger-bg`(관리 목록 "숨김" 배지), `--color-toast-link`(알림 줄 안 링크)를 더했다. 기본 아바타 8색은 001 T120이 이미 정한 값(FNV 해시 8색)을 `--avatar-1..8`로 옮겼다(data-model §2-3 표 값 대신 — 화면이 바뀌지 않게). SVG `fill` 속성은 `var()`를 못 읽어 `style`로 칠한다.
- **T009**: 입력칸 규칙은 `:where(...)`로 우선순위 0, 체크박스·라디오·범위·파일·색 입력은 뺐다. `.visually-hidden`도 `base.css`에 둔다(테마 버튼 안내용, 001 `auth.css`와 같은 규칙).
- **T011**: 테스트는 보안 필터까지 포함한 전체 앱으로 `StaticResourceCacheConfigIT`(Spring Security 기본 캐시 머리가 덮지 않는지 확인). ETag는 크기·수정 시각으로 만드는 약한 ETag.
- **T019·T022 플러그인**: `injectTo: 'head-prepend'`는 `<meta charset>`보다 앞에 넣어서, `transformIndexHtml`(`order: 'pre'`)이 `<meta charset>` 바로 다음에 문자열로 넣는다.
- **T023**: 테스트 셸 픽스처에 두 태그, `SpaShellRendererTest`·`NotFoundPageRendererTest`에 단언 하나씩.
- **T027**: 001 `logout.test.ts`에 "테마 설정은 지우지 않는다"가 이미 있어 더하지 않았다.
- **T031**: 버튼은 공통 머리말(PR #13) `SiteHeader`의 `site-header-actions` 맨 끝. 010 [피드]·011 알림은 이 버튼보다 앞에 둔다.
- **T037 axe가 찾은 것**: `opacity`로 흐리게 한 글자(작성자 칩·카드의 `@주소`, 설정 글자 수·친구 목록) → `--color-text-muted`. 채운 단추 위 글자 `--color-bg`(다크에서 3.2) → `--color-on-fill`(010 팔로우, 015 탈퇴). 다크에서 브라우저 기본 단추 면(`ButtonFace` #6b6b6b, 4.49) → 칠하지 않은 단추만 `--thumb-empty` 면.
- **T038 확인**: 004 `VisibilityBadge`·005 "🔒 비공개"·006 관리 목록 배지는 글자가 있다. 002 에디터는 자체 `textarea`라 토큰만으로 다크. 링크 미리보기 메타·메일은 서버가 만들어 테마와 무관.
- **T039 끈 빌드**: `VITE_DARK_MODE=false npx vite build` → `dist/index.html`에 `theme-init.js`·`color-scheme` 없음. `ThemeToggle` 끈 빌드 단위 테스트 통과.
- **T041 quickstart**: §2 자동 테스트 전부 통과(화면 단위 테스트 전체, `e2e/theme.spec.ts` desktop·mobile 18개, 백엔드 셸·캐시 테스트). §3 수동 확인 1~15는 종단 테스트와 스크린샷(라이트·다크 × 1280·375)으로 대신 확인.
