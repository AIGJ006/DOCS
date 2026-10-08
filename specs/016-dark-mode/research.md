# Research: 다크 모드

**Feature**: 016-dark-mode | **Date**: 2026-10-08

spec Implementation Notes·Clarifications와 원문(45·10·11·12·07)에서 정한 것은 "확정", 이 계획이 새로 정한 것은 "제안"으로 표시한다. 작업은 **공통 묶음**(모든 서비스)과 **선택 묶음**(다크 모드를 켠 서비스)으로 나눈다(Clarifications Q1).

## R1. 저장 (확정)

- **Decision**: 브라우저 `localStorage` 키 `theme` = `'system' | 'light' | 'dark'`. 없거나 세 값 밖이면 `system`. 읽기·쓰기 모두 `try/catch`(사생활 보호 모드·저장소 차단·용량 초과) — 실패하면 저장하지 않고 화면만 바꾼다. 서버·계정·DB에 저장하지 않는다(ERD 변경 없음).
- 로그아웃(001 `logout.ts`)은 `theme`를 지우지 않는다(07 §7 — 지우는 것은 임시 글 데이터뿐). 001 `logout.ts` 주석에 이미 적혀 있다.
- 탭 사이 실시간 동기화(`storage` 이벤트)는 하지 않는다(spec Assumptions 범위 밖).
- **Rationale**: 45 §2, T-3·T-8, FR-003·FR-007.

## R2. 공통 토큰 11개 (확정 + 제안)

- **Decision**: `F/styles/tokens.css`에 이름 11개(45 §3, Clarifications Q3)를 정의한다. 값은 FR-013 표 그대로, "각자"인 두 칸은 제안값:
  | 토큰 | 용도 | 라이트 | 다크 |
  |---|---|---|---|
  | `--color-bg` | 페이지 배경 | `#FFFFFF` | `#121212` |
  | `--color-surface` | 카드·머리말·상자 | `#FFFFFF` | `#1E1E1E` |
  | `--color-text` | 본문 글자 | `#212529` | `#E9ECEF` |
  | `--color-text-muted` | 날짜·보조 글자, **입력칸 테두리** | `#6C757D` | `#ADB5BD` |
  | `--color-border` | 구분선·카드 테두리(장식) | `#DEE2E6` | `#343A40` |
  | `--color-brand` | 링크·강조 글자 (제안) | `#1971C2` | `#74C0FC` |
  | `--color-brand-fill` | 흰 글자가 올라가는 버튼 배경 (제안) | `#1971C2` | `#1864AB` |
  | `--color-danger` | 삭제·오류 | `#C92A2A` | `#FF6B6B` |
  | `--thumb-empty` | 썸네일 빈 영역 (예시값) | `#F1F3F5` | `#2B2F33` |
  | `--color-code-bg` | 코드 블록 배경 | `#F8F9FA` | `#1A1B1E` |
  | `--color-focus` | 키보드 포커스 테두리 | `#1C7ED6` | `#74C0FC` |
- 계산한 대비(WCAG 식): 본문 15.4 / 15.8, 보조 글자 4.69 / 9.03, 링크 5.02 / 9.53, 버튼 흰 글자 5.02 / 6.09, 삭제 5.46 / 6.75, 포커스 4.20 / 9.53 — 모두 기준 이상.
- **찾은 문제**: 원문 `--color-border` 값은 배경 대비 1.30(라이트)·1.63(다크)이라 FR-012의 "입력칸 테두리 3:1"을 만족하지 못한다. 카드·구분선 같은 장식 테두리는 WCAG 1.4.11 대상이 아니므로 값을 그대로 두고, **입력칸·버튼 윤곽처럼 알아봐야 하는 테두리는 `--color-text-muted`를 쓴다**(4.69 / 9.03)(제안 — 팀 확인 T003). 보조 글자를 코드 블록 배경 위에 올리면 라이트 4.45라 그 조합은 쓰지 않는다.
- **Rationale**: 45 §3·§4, FR-011~FR-013, 2026-10-07 O5(값은 각자).

## R3. 보조 토큰 (제안)

- **Decision**: 지금 화면에는 11개로 표현할 수 없는 색이 있다. 같은 파일에 라이트·다크 쌍으로 두고 이름은 공통 규격이 아니다(서비스마다 더하거나 뺄 수 있음).
  | 보조 토큰 | 쓰는 곳 | 라이트 | 다크 |
  |---|---|---|---|
  | `--color-overlay` | 대화 상자 뒤 어둡게 (002 `editor.css`, 006 `dialogs.css`) | `rgb(0 0 0 / 40%)` | `rgb(0 0 0 / 60%)` |
  | `--color-notice-bg` | 작성자 안내(수정 중·비공개·숨김, 005 `AuthorStatusBanner`) | `#FFF9DB` | `#3B3000` |
  | `--color-warning-bg`·`--color-warning-border` | 에디터 충돌·저장 경고 상자 | `#FFF4E5`·`#B26A00` | `#3A2A12`·`#FFB84D` |
  | `--diff-del-bg`·`--diff-add-bg` | 비교 화면 줄 | `#FFEBE9`·`#E6FFEC` | `#3D1F1F`·`#1C3A26` |
  | `--diff-del-strong`·`--diff-add-strong` | 비교 화면 글자 단위 | `#FFC1BC`·`#ABF2BC` | `#7A2E2E`·`#2D6B3F` |
  | `--color-toast-bg`·`--color-toast-text` | 알림 줄 (006 `Toast`) | `#222222`·`#FFFFFF` | `#E9ECEF`·`#121212` |
  | `--color-on-fill` | 채운 버튼·아바타 위 흰 글자 | `#FFFFFF` | `#FFFFFF` |
  | `--avatar-1` … `--avatar-8` | 기본 프로필 원 (001 T120) | 아래 | 라이트와 같음 |
  | `--hljs-*` (14개) | 코드 강조(R9) | github 색 구성 | github-dark 색 구성 |
- 기본 프로필 8색: `#C92A2A`, `#A61E4D`, `#862E9C`, `#5F3DC4`, `#364FC7`, `#1864AB`, `#0B7285`, `#087F5B` — 흰 글자 대비 5.0~7.3(FR-016). 원 안의 글자는 흰색 고정이라 두 테마가 같은 값을 쓴다.
- **Rationale**: plan 설계 후 확인 1, FR-016·FR-019.
- **Alternatives considered**: 비교 화면 색을 `--color-danger`와 투명도로 만들기 — 추가 줄은 대응하는 공통 색이 없다.

## R4. Tier A 이름 바꾸기 (Clarifications Q3)

- **Decision**: 공통 묶음. 지금 쓰는 색 변수 → 공통 토큰:
  | 지금 | 바꿀 이름 | 파일 |
  |---|---|---|
  | `--card-bg` | `--color-surface` | `PostCard.tsx`, `AuthorCard.tsx` |
  | `--card-border` | `--color-border` | `PostCard.tsx` |
  | `--card-thumb-empty-bg` | `--thumb-empty` | `PostCard.tsx` |
  | `--card-excerpt-color`, `--card-meta-color`, `--muted` | `--color-text-muted` | `PostCard.tsx`, `ReactionBar.tsx`, `LoadMoreButton.tsx`, `PostDetailPage.tsx`, `BlogPage.tsx` |
  | `--divider` | `--color-border` | `ReactionBar.tsx` |
  | `--tag-bg` | `--thumb-empty` (태그 칩 배경 — 카드 면과 구분되는 옅은 면) | `TagList.tsx` |
  | `--notice-bg` | `--color-notice-bg`(보조) | `AuthorStatusBanner.tsx` |
  | `--badge-bg` | `--thumb-empty` | `AuthorStatusBanner.tsx` |
  | `--card-avatar-bg` | `--avatar-{n}`(001 T120이 원 색을 정함) | `DefaultAvatar.tsx` |
  - `var(--x, #색)`의 대체 값은 모두 지운다(대체 값도 색을 직접 쓰는 것 — FR-011). 토큰 파일은 `main.tsx`에서 가장 먼저 불러오므로 대체 값이 필요 없다.
  - 크기·모양 변수(`--card-radius`, `--card-gap`, `--card-min-width`, `--card-thumb-ratio`, `--card-title-size`, `--card-excerpt-size`, `--card-meta-size`, `--page-max-width`, `--content-max-width`)는 색이 아니라 이름을 바꾸지 않는다.
  - 002 `editor.css`(색 값이 있는 줄 19개)와 006 `dialogs.css`·`managePosts.css`(6·7줄)도 같은 규칙. 006 파일은 006 머지 후.
- **Rationale**: Clarifications Q3, FR-011.

## R5. 색 직접 쓰기 검사 (제안)

- **Decision**: 공통 묶음. `F/styles/__tests__/noRawColors.test.ts`(Vitest, Node `fs`로 파일을 읽음): `F/` 아래 `*.ts`·`*.tsx`·`*.css` 중 `__tests__`·`*.test.*`·`src/test/**`·`styles/tokens.css`를 뺀 파일(`code-highlight.css`도 대상 — `var(--hljs-*)`만 씀)에서 정규식 `#[0-9a-fA-F]{3,8}\b`(문자열·CSS 값 위치), `\brgba?\(`, `\bhsla?\(`, 색 이름 값(`: (white|black|red|…);`·`'white'`)을 찾으면 파일·줄과 함께 실패(정확한 규칙은 contracts/theme.md §6). 예외는 `/* raw-color-ok: 이유 */` 주석이 같은 줄에 있을 때만(예: SVG 아이콘 `currentColor`는 대상 아님).
  - 정규식은 HTML 엔티티(`&#123;`)·URL 조각(`#comment-12`)을 잡지 않도록 "CSS 값 위치(`:` 뒤) 또는 따옴표 안의 단독 값"만 본다.
- 007~015 화면 작업도 이 검사를 통과해야 한다(새 화면은 처음부터 토큰). ANALYSIS-tier-bc에 공통 규칙으로 올린다.
- **Rationale**: FR-011 "화면 요소는 색 값을 직접 쓰지 않는다"를 사람 검토 없이 지키기 위해.
- **Alternatives considered**: stylelint 규칙 — 인라인 `style={{}}`(지금 Tier A 대부분)을 못 본다. 새 의존성도 필요하다.

## R6. 첫 화면 테마 결정 (확정)

- **Decision**: 선택 묶음. `frontend/public/js/theme-init.js`(Vite가 그대로 `dist/js/`에 복사, 해시 없음):
  ```js
  (function () {
    var choice = 'system';
    try { var s = localStorage.getItem('theme'); if (s === 'light' || s === 'dark') choice = s; } catch (e) {}
    var dark = choice === 'dark' ||
      (choice === 'system' && !!window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    var root = document.documentElement;
    root.dataset.theme = dark ? 'dark' : 'light';
    root.dataset.themeChoice = choice;
  })();
  ```
  - `defer`·`async`·`type="module"` 없이 `<head>`에서 CSS `<link>`보다 앞에 둔다 — 첫 그리기 전에 끝난다(T-5). `system`을 저장하지 않고 "없음"으로 둔다.
  - 인라인 스크립트가 아니므로 CSP `script-src 'self'` 그대로(001 `SecurityHeadersFilter`, 12 §8, SC-003).
  - 005 `SpaShellRenderer`·004 `NotFoundPageRenderer`는 빌드된 `index.html`을 읽어 `<!--app-head-->` 자리에 메타를 넣으므로 서버가 내보내는 모든 셸(상세·블로그·404)에 같은 스크립트가 들어간다.
- **Rationale**: 45 §2 T-5, FR-008·FR-009, 12 §8.

## R7. 켜기·끄기 (제안)

- **Decision**: `VITE_DARK_MODE`(빌드 환경 변수, 기본 `true`). `vite.config.ts`의 작은 플러그인 `themeHead()`가 `transformIndexHtml`로 `<head>` 맨 앞(`<meta charset>` 다음)에 `<meta name="color-scheme" content="light dark">`와 `<script src="/js/theme-init.js"></script>`를 넣는다. `false`면 넣지 않는다 → `data-theme` 없음 → `:root` 라이트 값만, 버튼 없음(`F/config.ts` `DARK_MODE_ENABLED`).
  - 다크 모드를 만들지 않는 서비스(Clarifications Q1)는 `VITE_DARK_MODE=false`로 빌드하면 공통 테스트에서 선택 묶음이 빠진다(화면 테스트는 `DARK_MODE_ENABLED`로 건너뜀, `e2e/theme.spec.ts`는 `test.skip`).
- **Rationale**: FR-001(규격만 공통), 01 §2-3 Tier C.
- **Alternatives considered**: `index.html`에 스크립트를 고정해 두고 끈 서비스가 지우기 — 공통 파일을 서비스마다 고쳐야 한다.

## R8. 실행 중 전환 (확정 + 제안)

- **Decision**: 선택 묶음.
  - `themeStore.ts`: `readChoice()`(R1 규칙), `writeChoice(choice)`(`system`이면 `removeItem`), `nextChoice(c)` = system → light → dark → system, `resolve(choice, prefersDark)`.
  - `useTheme()`: 처음 값은 `document.documentElement.dataset.themeChoice`(theme-init 결과)에서 읽는다(다시 계산하지 않아 처음 그리기와 어긋나지 않음). `setChoice`는 저장 + `dataset.theme`·`dataset.themeChoice` 갱신을 같은 이벤트 안에서. `choice === 'system'`일 때만 `matchMedia('(prefers-color-scheme: dark)')`의 `change`를 구독해 `dataset.theme`을 바꾼다(FR-006). 고정 상태에서는 구독을 끊는다.
  - `ThemeToggle`: `<button type="button">`, 아이콘 🖥(시스템)·☀️(라이트)·🌙(다크)은 `aria-hidden`, `aria-label`·`title` = "테마: 시스템 설정 (누르면 라이트)" / "테마: 라이트 (누르면 다크)" / "테마: 다크 (누르면 시스템 설정)". 누른 뒤 `aria-live="polite"` 안내 "다크 테마로 바꿨어요". 375px에서 아이콘만(글자 없음).
  - 위치: 머리말 줄 맨 오른쪽(`F/App.tsx`의 `SessionBar` 옆). 001 공통 머리말이 생기면 그쪽 맨 오른쪽으로 옮긴다.
  - 색 전환 애니메이션 없음: `tokens.css`·`base.css`에 `transition` 없음, 검사(R5 테스트에 `transition: …(color|background)` 금지 추가).
- **Rationale**: 45 §2 T-4, FR-004~FR-006·FR-010.

## R9. 요소별 (확정 + 제안)

- **Decision**: 선택 묶음(코드 강조 색 구성의 라이트 값은 공통 묶음).
  - 코드 강조: highlight.js의 두 CSS(`github.css`·`github-dark.css`)는 같은 선택자(`.hljs-keyword` 등)를 써서 함께 불러오면 뒤의 것이 이긴다. 그래서 `F/styles/code-highlight.css` 하나에서 `.hljs-*` 규칙을 `var(--hljs-keyword)` 등으로 칠하고, 값은 `tokens.css`에 라이트(github)·다크(github-dark) 쌍으로 옮긴다(BSD-3 라이선스 표기를 파일 머리에). 원래 값 중 이 저장소 코드 블록 배경 위에서 4.5:1이 안 되는 4개(라이트 keyword `#D73A49` 4.34·built_in `#E36209` 3.31·name `#22863A` 4.39, 다크 section `#1F6FEB` 3.72)는 GitHub Primer의 새 값(`#CF222E`·`#BC4C00`·`#1A7F37`·`#58A6FF`)으로 바꾼다(data-model §2-4). 코드 블록 배경은 `--color-code-bg`. 지금 저장소에는 강조 CSS가 하나도 없다(002 `highlightCode.ts`가 클래스만 붙임) — 라이트 강조도 이 작업으로 처음 생긴다.
  - 사진: `img`에 `filter`·`opacity`·`mix-blend-mode`를 주지 않는다(FR-015). 검사(R5 테스트에 `filter:` 금지 — `img` 관련 파일).
  - 상태 배지: 004 `VisibilityBadge`("🔒 비공개")·005 "수정 중" 안내는 이미 글자·아이콘이 있다(FR-019). 006 관리 목록 배지도 글자가 있다 — 확인만.
  - 에디터: 002는 자체 `textarea` + 미리보기라 토큰만 쓰면 다크가 된다(FR-018). 외부 에디터를 쓰는 개인 서비스는 그 에디터 설정을 각자.
  - 브라우저가 그리는 요소: `<meta name="color-scheme" content="light dark">`(R7) + `base.css`에서 `:root[data-theme="light"] { color-scheme: light }`, `:root[data-theme="dark"] { color-scheme: dark }`(고정 상태에서 스크롤바·입력칸도 고정, FR-020).
  - 링크 미리보기(005 `LinkPreviewMeta` og 태그)와 메일(001 메일 템플릿)은 서버가 만든 라이트 기준 — 변경 없음(FR-021).
- **Rationale**: 45 §4, FR-015~FR-021.

## R10. 정적 파일 캐시 (제안)

- **Decision**: `theme-init.js`는 파일 이름에 해시가 없어, 기본 동작(브라우저 추정 캐시)이면 배포 뒤에도 옛 파일이 쓰일 수 있다. `B/shared/web/StaticResourceCacheConfig.java`(`WebMvcConfigurer.addResourceHandlers` — 지금 `shared/security`의 `ViewerWebMvcConfig`·`WebSecurityMvcConfig`는 인자·보안 설정만 하고 정적 자원 규칙은 없다): `/js/**` → `CacheControl.noCache()`(ETag·Last-Modified 재검사), `/assets/**`(Vite 해시 파일) → `max-age=31536000, immutable`. 머리 문자열은 001 `CacheControlPolicy`에 상수(`STATIC_REVALIDATE`, `STATIC_IMMUTABLE`)로 더한다(추가만). SPA 셸은 `SpaForwardingController`·`SpaShellRenderer`·`NotFoundPageRenderer`가 이미 정한 머리(404는 `private, no-store`)를 그대로 두고, 머리가 없는 셸 응답이 있으면 `no-cache`를 붙인다(T011에서 확인).
- **Rationale**: FR-005·FR-008(배포 뒤에도 같은 판정), 헌법 V.

## R11. 대비 확인 (제안)

- **Decision**:
  - 공통 묶음: `F/styles/__tests__/tokenContrast.test.ts` — `tokens.css`를 읽어 짝마다 WCAG 대비를 계산: 본문·보조 글자 vs `--color-bg`·`--color-surface` 4.5, 링크 vs 배경 4.5, 흰 글자 vs `--color-brand-fill` 4.5, `--color-danger` vs 배경 4.5, `--color-focus`·`--color-text-muted`(입력 테두리) vs 배경 3.0, 흰 글자 vs `--avatar-1..8` 4.5. 다크 값이 정의돼 있으면 다크도. 개인 서비스가 값을 바꾸면 이 테스트가 막는다(FR-012 "바꾼 값도 기준").
  - 선택 묶음: `e2e/theme.spec.ts`에서 `@axe-core/playwright`로 홈·블로그·글 상세·에디터·설정 5개 화면을 `colorScheme: 'light'`·`'dark'`로 `color-contrast` 규칙만 검사(SC-004). 새 개발 의존성(팀 확인 T004).
- **Rationale**: FR-012, SC-004, 45 #6.
