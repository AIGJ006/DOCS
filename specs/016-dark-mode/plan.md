# Implementation Plan: 다크 모드

**Branch**: `016-dark-mode` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-dark-mode/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

모든 화면의 색을 11개 공통 색 토큰(`--color-bg` … `--color-focus`)으로만 정의하고, 토큰마다 라이트·다크 값 한 쌍을 한 파일에 둔다. 다크 모드를 켠 서비스는 `<head>`의 외부 파일 `/js/theme-init.js`가 첫 화면을 그리기 전에 저장된 선택(`localStorage` `theme`)과 기기 설정으로 `data-theme`를 정하고, 머리말 맨 오른쪽 버튼이 시스템 → 라이트 → 다크를 돌린다. 규격만 공통이고 구현은 원하는 서비스만 한다(지금은 나민서). 그래서 작업을 두 묶음으로 나눈다: **공통 묶음**(토큰 이름·라이트 값·색 값 직접 쓰기 금지 — 모든 서비스)과 **선택 묶음**(테마 결정·버튼·다크 값·대비 검사 — 켠 서비스만).

기술 접근 (상세 근거는 [research.md](./research.md)):

- **서버·DB 변경 없음.** 선택은 브라우저 `localStorage`에만(`theme` = `system`|`light`|`dark`). ERD·API 없음(R1).
- **토큰 파일 하나.** `F/styles/tokens.css` — `:root, [data-theme="light"]`에 라이트 값, `[data-theme="dark"]`에 다크 값. 공통 이름 11개(spec FR-011)와, 공통 이름에 없는 색(대화 상자 뒤 어둡게, 경고 상자, 비교 화면 추가·삭제, 코드 강조 색 등)은 같은 파일의 "보조 토큰"(`--color-overlay`, `--color-warning-*`, `--diff-*`, `--hljs-*`)으로 둔다. 보조 토큰 이름은 공통 규격이 아니다(팀 확인 T003)(R2·R3).
- **이름 바꾸기.** Tier A 공통 부품의 `--card-bg`·`--card-border`·`--card-thumb-empty-bg`·`--card-meta-color`·`--card-excerpt-color`·`--muted`·`--divider`·`--tag-bg`·`--notice-bg`·`--badge-bg`를 공통 토큰으로 바꾸고, `var(--x, #색)` 대체 값을 지운다. 크기·모양 변수(`--card-radius`·`--card-gap`·`--page-max-width` 등)는 색이 아니라 그대로 둔다(Clarifications Q3)(R4).
- **색 직접 쓰기 금지 검사.** Vitest `noRawColors.test.ts`가 `F/` 아래 `.ts`·`.tsx`·`.css`(테스트·`tokens.css` 제외)에서 `#hex`·`rgb()`·`hsl()`·색 이름 값을 찾으면 실패한다. 007~015 화면도 같은 검사를 받는다(R5).
- **깜빡임 없는 첫 화면(선택 묶음).** `frontend/public/js/theme-init.js`(1KB 미만, 모듈 아님, `defer`·`async` 없음)를 `index.html` `<head>`의 CSS보다 앞에 둔다. Vite 플러그인(`frontend/vite/themeHead.ts`, `transformIndexHtml` `injectTo: 'head-prepend'`)이 `VITE_DARK_MODE`가 `false`가 아닐 때만 이 `<script>`와 `<meta name="color-scheme" content="light dark">`를 넣는다 — 끈 서비스는 라이트만. 인라인 스크립트가 없어 CSP `script-src 'self'`(001 `SecurityHeadersFilter`) 그대로(R6·R7).
- **실행 중 동작(선택 묶음).** `F/features/theme/themeStore.ts`(읽기·쓰기 `try/catch`, 모르는 값은 `system`), `useTheme.ts`(`matchMedia` 변경 구독 — `system`일 때만 `data-theme` 갱신), `ThemeToggle.tsx`(🖥/☀️/🌙 + `aria-label`·`title` "테마: 시스템 설정(누르면 라이트)"). 로그아웃(001 `logout.ts`)은 `theme`를 지우지 않는다(R8).
- **요소별(선택 묶음).** 코드 강조는 highlight.js 클래스(`.hljs-*`)를 보조 토큰으로 칠하는 `F/styles/code-highlight.css` 하나(github / github-dark 색 구성 값을 옮김 — 두 CSS 파일을 통째로 불러오면 서로 덮어씀). 사진은 `filter` 없음. 상태 배지는 이미 글자·아이콘이 있음(004 `VisibilityBadge` "🔒 비공개"). 에디터는 자체 `textarea` + 미리보기라 토큰만으로 다크가 된다. 링크 미리보기·메일은 서버가 만들어 테마와 무관(R9).
- **정적 파일 캐시.** 해시 없는 `/js/theme-init.js`가 오래 캐시되지 않도록 `Cache-Control: no-cache`(ETag 재검사)를 준다. 해시 붙은 `/assets/**`는 1년 `immutable`. 머리 문자열은 001 `CacheControlPolicy`에 상수로 더한다(R10).
- **대비 확인.** 단위 테스트가 `tokens.css`의 글자·배경 짝(본문 4.5:1, 보조 글자 4.5:1, 테두리·포커스 3:1, 흰 글자 위 버튼 4.5:1, 기본 프로필 8색 4.5:1)을 WCAG 식으로 계산하고, Playwright + axe(`@axe-core/playwright`, 새 개발 의존성 — 팀 확인 T004)가 주요 화면 5개를 라이트·다크로 검사한다(R11).

## Technical Context

**Language/Version**: TypeScript 6 + React 18 (화면), Java 21(정적 파일 캐시 설정 한 곳)

**Primary Dependencies**:

- 화면(기존): React 18, react-router 7, Vite 8, highlight.js 11(002 `highlightCode.ts`), 001 `SessionBar`·`logout.ts`·`DefaultAvatar`(T120), 005 `PostCard`·`ReactionBar`·`AuthorStatusBanner`, 002 `editor.css`, 006 `dialogs.css`·`managePosts.css`(006 머지 후)
- 새 개발 의존성(제안): `@axe-core/playwright`(대비 자동 검사, 다른 개발 의존성처럼 `^` 범위)
- 서버(기존): Spring Boot 4.1.1 Web MVC 정적 자원 처리, 001 `SecurityHeadersFilter`(CSP 확인만)

**Storage**: 브라우저 `localStorage` 키 `theme` 하나. 서버·DB·Redis 없음

**Testing**: Vitest + Testing Library + jsdom(`matchMedia`·`localStorage` 흉내), Playwright(`colorScheme: 'dark'` 문맥, 첫 그리기 화면 캡처, CSP 위반 이벤트 수집, axe 검사), 서버는 MockMvc로 정적 파일 캐시 헤더

**Target Platform**: 최신 데스크톱·모바일 브라우저(`prefers-color-scheme`·`matchMedia` 변경 이벤트 지원)

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA) — 이 기능은 화면만

**Performance Goals**:

- `theme-init.js` 1KB 미만, 실행 1ms 미만(동기 실행이라 첫 그리기를 늦추지 않을 만큼)
- 버튼을 누른 뒤 같은 프레임 안에 색이 바뀜(전환 애니메이션 없음)

**Constraints**:

- 인라인 스크립트·`eval` 없음(CSP `script-src 'self'`, SC-003)
- 색 전환 `transition` 없음(FR-010)
- 사진에 `filter`·`mix-blend-mode` 없음(FR-015)
- 화면 요소는 색 값을 직접 쓰지 않는다(FR-011, 검사 T008)
- 375px 폭에서 버튼이 머리말을 넘치지 않음

**Scale/Scope**:

- 공통 토큰 11개 + 보조 토큰 11개 + 아바타 8개 + 코드 강조 14개, 바꿀 파일: Tier A 화면 10개 파일(색 값이 있는 줄 36개 — `editor.css` 19) + 006 CSS 2개(13줄) + 이 기능 새 파일 8개
- 테마 상태 3개, 대비 검사 화면 5개 × 2 테마

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | 토큰 이름은 공통 규격, 값은 각자(2026-10-07 O5). 다크 모드 자체는 선택 구현이고 끈 서비스는 라이트만 쓴다. 계정 동기화는 개인 확장 |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | 서버 모듈 변경 없음(정적 파일 캐시 설정은 `shared/web`) |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS (해당 없음)** | 권한과 무관. 404 화면도 같은 셸이라 같은 테마 |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 외부 파일 스크립트만, CSP 그대로. `theme` 값은 세 값 밖이면 버린다(속성에 그대로 넣지 않음) |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 저장소 접근 실패·`matchMedia` 없음 → 기기 설정 또는 라이트로, 오류 없음 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 테마는 개인 정보가 아니라 로그아웃 때 남긴다(07 §7) |
| VII. 수치는 설정값으로 | **PASS** | 색 값은 `tokens.css` 한 곳, 켜기·끄기는 `VITE_DARK_MODE` |
| VIII. 실제 DB로 통합 테스트 | **PASS (해당 없음)** | 데이터 규칙 없음. 동작은 브라우저 종단 테스트로 |

**Gate 결과 (Phase 0 전)**: 위반 없음.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 새 위반은 없다.
- 새로 확인한 점:
  1. 11개 공통 이름만으로는 지금 화면의 색을 다 표현할 수 없다(대화 상자 뒤 어둡게 `rgb(0 0 0 / 40%)`, 에디터 충돌 경고 상자, 비교 화면 추가·삭제 4색, 수정 중 안내 노란 배경, 코드 강조 색). "보조 토큰"을 같은 파일에 라이트·다크 쌍으로 두고 이름은 공통 규격에서 뺀다(팀 확인 T003).
  2. 원문 FR-013 표의 "링크·강조 글자 / 버튼 배경"은 값이 "각자"다. 이 저장소 기본값으로 라이트 `#1971C2`·`#1971C2`(흰 글자 4.5:1 이상), 다크 `#74C0FC`·`#1864AB`를 제안한다(T003).
  3. 대비 자동 검사에 새 개발 의존성 `@axe-core/playwright`가 필요하다. 의존성 없이 토큰 짝만 계산하는 단위 테스트는 공통 묶음에 두고, axe 검사는 선택 묶음에 둔다(팀 확인 T004).
  4. 공통 저장소의 `VITE_DARK_MODE` 기본값을 정해야 한다. 이 계획은 `true`(나민서 MUST, 다른 두 사람은 `false`로 빌드)로 둔다(T004).
  5. 001 `DefaultAvatar`(T120) 8색은 "016 확정 전 임시"다. 이 계획이 라이트·다크 모두 흰 글자 4.5:1 이상인 8색을 정한다(data-model §2-3). 001 T120이 그 값을 쓴다.
  6. highlight.js `github` 색 구성 중 3개(keyword·built_in·name)와 `github-dark`의 section은 이 저장소 코드 블록 배경 위에서 4.5:1이 안 된다. GitHub Primer의 새 값으로 바꾼다(data-model §2-4, T003).
  7. 머리말은 아직 001 임시 `SessionBar`뿐이다. 버튼은 `F/App.tsx` 머리말 줄 맨 오른쪽에 두고, 공통 머리말이 생기면 그쪽으로 옮긴다(006 머지 후 — 같은 파일).

## Project Structure

### Documentation (this feature)

```text
specs/016-dark-mode/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── theme.md               # 저장 키·data 속성·theme-init 판정·버튼 순서·토큰 이름과 값·검사 규칙
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
frontend/
├── public/js/theme-init.js                       # 선택 묶음: 첫 그리기 전 data-theme (모듈 아님)
├── index.html                                    # (변경 없음 — 플러그인이 head에 넣음)
├── vite.config.ts                                # + themeHead() 플러그인 등록 (VITE_DARK_MODE)
├── vite/themeHead.ts                             # 선택 묶음: 플러그인 본문(테스트하려고 분리) + __tests__/themeHead.test.ts
├── e2e/theme.spec.ts                             # 선택 묶음: 깜빡임·유지·시스템 따라감·CSP·axe
└── src/
    ├── main.tsx                                  # + import './styles/tokens.css', './styles/code-highlight.css'
    ├── styles/
    │   ├── tokens.css                            # 공통 토큰 11개 + 보조 토큰, 라이트·다크
    │   ├── code-highlight.css                    # .hljs-* → --hljs-* 토큰
    │   ├── base.css                              # body 배경·글자, a, :focus-visible, color-scheme
    │   └── __tests__/noRawColors.test.ts, tokenContrast.test.ts
    ├── features/theme/
    │   ├── themeStore.ts                         # 읽기·쓰기·다음 상태
    │   ├── useTheme.ts                           # data-theme 적용, matchMedia 구독
    │   ├── ThemeToggle.tsx                       # 버튼
    │   └── __tests__/
    ├── config.ts                                 # + DARK_MODE_ENABLED (import.meta.env.VITE_DARK_MODE)
    ├── App.tsx                                   # 머리말 맨 오른쪽 ThemeToggle (006 머지 후)
    ├── components/PostCard.tsx, AuthorCard.tsx, TagList.tsx, ReactionBar.tsx, LoadMoreButton.tsx, DefaultAvatar.tsx   # 토큰 이름 (005·001 소유)
    ├── features/post-detail/AuthorStatusBanner.tsx, pages/PostDetailPage.tsx, pages/BlogPage.tsx                    # 토큰 이름 (005 소유)
    ├── pages/editor.css                          # 토큰·보조 토큰 (002 소유)
    └── components/dialogs.css, features/manage-posts/managePosts.css                                                 # 토큰 (006 소유, 006 머지 후)

backend/src/main/java/com/team/blog/shared/web/StaticResourceCacheConfig.java   # /js/** no-cache, /assets/** immutable
backend/src/main/java/com/team/blog/shared/web/CacheControlPolicy.java          # + STATIC_REVALIDATE, STATIC_IMMUTABLE (001 소유, 추가만)
backend/src/test/java/com/team/blog/shared/web/StaticResourceCacheConfigTest.java
```

**Structure Decision**: 화면 기능이라 `frontend/src/features/theme`과 전역 스타일 `frontend/src/styles`에 둔다. 토큰 파일은 다크 모드를 켜지 않는 서비스도 쓰는 공통 파일이고, 테마 결정·버튼은 `VITE_DARK_MODE`로 켜고 끈다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

위반 없음.
