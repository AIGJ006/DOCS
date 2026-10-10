# 테마 계약: 저장 값, 첫 화면 판정, 버튼, 토큰 이름, 검사 규칙

**Feature**: 016-dark-mode | 근거: research R1~R11, docs/45-dark-mode.md §2~§4

HTTP API가 없는 기능이라 이 문서가 계약이다. **§1~§5와 §7의 기준은 다크 모드를 만드는 모든 서비스의 공통 규격**이고, §5 값·§6 정규식·§8 파일 위치는 이 저장소 구현이다.

## §1. 저장 값

| 항목 | 규칙 |
|---|---|
| 저장소 | `window.localStorage` |
| 키 | `theme` |
| 값 | `light` \| `dark` (시스템 = 키 없음) |
| 읽기 | `try { localStorage.getItem('theme') } catch { null }` → `light`·`dark`가 아니면 `system` |
| 쓰기 | `light`·`dark` → `setItem`, `system` → `removeItem`. 예외는 삼키고 화면만 바꾼다 |
| 지우지 않는 때 | 로그아웃(001 `logout.ts`), 탈퇴 신청(015), 세션 만료 |

## §2. `<html>` 속성

| 속성 | 값 | 설명 |
|---|---|---|
| `data-theme` | `light` \| `dark` | 적용 테마. CSS는 이 속성만 본다 |
| `data-theme-choice` | `system` \| `light` \| `dark` | 사용자 선택 |

두 속성은 항상 함께 바뀐다(같은 동기 코드 안). 다크 모드를 끈 빌드에서는 둘 다 없다(§9).

## §3. 첫 화면 판정 (`/js/theme-init.js`)

| 저장 값 | 기기 설정 (`prefers-color-scheme`) | `data-theme` | `data-theme-choice` |
|---|---|---|---|
| `dark` | 무엇이든 | `dark` | `dark` |
| `light` | 무엇이든 | `light` | `light` |
| 없음·모르는 값 | `dark` | `dark` | `system` |
| 없음·모르는 값 | `light`·지원 안 함 | `light` | `system` |
| 저장소 예외 | `dark` | `dark` | `system` |
| 저장소 예외 | `matchMedia` 없음 | `light` | `system` |

넣는 규칙:

- 우리 사이트 파일(`/js/theme-init.js`)로만. 인라인 `<script>`·`eval`·`data:` 주소 금지(CSP `script-src 'self'`).
- `<head>` 안, 모든 `<link rel="stylesheet">`·`<style>`보다 앞. `defer`·`async`·`type="module"` 없음.
- 같은 자리에 `<meta name="color-scheme" content="light dark">`.
- 이 저장소: `vite.config.ts`의 `themeHead()` 플러그인이 `transformIndexHtml`에서 두 태그를 `injectTo: 'head-prepend'`로 넣는다(Vite가 넣는 CSS `<link>`보다 앞). 서버 셸(005 `SpaShellRenderer`, 004 `NotFoundPageRenderer`)은 빌드된 `dist/index.html`을 읽으므로 같은 태그를 갖는다.
- 파일 크기 1KB 미만, 바깥 변수 없음(즉시 실행 함수).

## §4. 전환 버튼

> (2026-10-10 민서 결정: 설정 화면 라디오로 옮김) — 아래 머리말 버튼 표는 옛 설계다. 지금은 설정 화면 `ThemeSettings`(fieldset 라디오 3개: 시스템 설정 따르기 (기본) / 라이트 모드 / 다크 모드)가 같은 저장·적용(§1·§2)을 쓴다.

| 항목 | 규칙 |
|---|---|
| 위치 | 모든 페이지 머리말 맨 오른쪽(같은 자리) |
| 요소 | `<button type="button">` |
| 순서 | `system` → `light` → `dark` → `system` |
| 적용 | 누른 그 이벤트 안에서 §1 저장 + §2 속성 변경(새로 고침·지연 없음) |
| 시스템 따라가기 | `data-theme-choice = system`일 때만 `matchMedia('(prefers-color-scheme: dark)')` `change`로 `data-theme` 갱신 |
| 전환 효과 | 없음(`transition` 금지 — §6) |

| 지금 선택 | 아이콘(`aria-hidden="true"`) | `aria-label`·`title` |
|---|---|---|
| `system` | 🖥 | `테마: 시스템 설정 (누르면 라이트)` |
| `light` | ☀️ | `테마: 라이트 (누르면 다크)` |
| `dark` | 🌙 | `테마: 다크 (누르면 시스템 설정)` |

누른 뒤 `aria-live="polite"` 영역(화면에 보이지 않음)에 한 번 알린다:

| 바뀐 선택 | 안내 |
|---|---|
| `light` | `라이트 테마로 바꿨어요` |
| `dark` | `다크 테마로 바꿨어요` |
| `system` | `기기 설정을 따라가요` |

문구 끝에 마침표를 붙이지 않는다(README 정해진 것).

## §5. 색 토큰 이름 (공통 규격)

**이름 11개는 세 서비스가 같다**(Clarifications Q3). 값은 각자 바꿀 수 있고, 바꾼 값도 §7 기준을 지킨다.

```text
--color-bg  --color-surface  --color-text  --color-text-muted  --color-border
--color-brand  --color-brand-fill  --color-danger  --thumb-empty  --color-code-bg  --color-focus
```

| 쓰임 | 토큰 |
|---|---|
| 페이지 배경 | `--color-bg` |
| 카드·머리말·상자·대화 상자 면 | `--color-surface` |
| 본문 글자, 태그 칩·배지 글자 | `--color-text` |
| 날짜·보조 글자, 입력칸·버튼 윤곽 테두리 | `--color-text-muted` |
| 구분선·카드 테두리(장식) | `--color-border` |
| 링크·강조 글자 | `--color-brand` |
| 흰 글자가 올라가는 버튼 면 | `--color-brand-fill` |
| 삭제·오류 글자·테두리 | `--color-danger` |
| 썸네일 빈 영역, 태그 칩·배지 면 | `--thumb-empty` |
| 코드 블록 면 | `--color-code-bg` |
| 키보드 포커스 테두리(`:focus-visible` `outline`) | `--color-focus` |

선택자는 `:root, [data-theme="light"] { … }`와 `[data-theme="dark"] { … }`. `@media (prefers-color-scheme: dark)` 규칙은 넣어도 되고 안 넣어도 된다(FR-022 삭제). 이 저장소는 넣지 않는다(스크립트가 꺼지면 앱이 그려지지 않음).

이 저장소 값·보조 토큰(`--color-overlay`, `--color-notice-bg`, `--color-warning-*`, `--diff-*`, `--color-toast-*`, `--color-on-fill`, `--avatar-1..8`, `--hljs-*`)은 [data-model.md §2](../data-model.md). 보조 토큰 이름은 공통 규격이 아니다.

## §6. 색 직접 쓰기 금지 (`noRawColors.test.ts`)

대상: `frontend/src/**/*.{ts,tsx,css}`. 제외: `**/__tests__/**`, `**/*.test.*`, `src/test/**`, `src/styles/tokens.css`.

| 찾는 것 | 정규식(요지) | 예 |
|---|---|---|
| 16진 색 | `(?<=[:\s,(]|['"\`])#[0-9a-fA-F]{3,8}\b` — CSS 값 위치 또는 따옴표 바로 뒤 | `color: #333`, `'#fff'` |
| 함수 색 | `\b(rgb|rgba|hsl|hsla|hwb|lab|lch|oklab|oklch|color)\(` | `rgb(0 0 0 / 40%)` |
| 색 이름 값 | `(?<=:\s*|['"])(white|black|red|green|blue|gray|grey|silver|yellow|orange|purple|pink|brown|navy|teal)(?=\s*[;'",}!]|$)` | `background: white`, `color: 'black'` |
| `var()` 대체 값 안의 색 | 위 세 규칙이 그대로 잡음 | `var(--muted, #6c757d)` |
| 색 전환 효과 | `transition[^;]*\b(color|background|border-color|all)\b` | `transition: background .2s` |
| 사진 필터 | `\b(filter|mix-blend-mode)\s*:` (`backdrop-filter` 제외) | `img { filter: brightness(.8) }` |

- 허용: `transparent`, `currentColor`, `inherit`, `initial`, `unset`, `none`.
- 잡지 않음: 주소 조각(`#comment-12`, `/#top`), HTML 엔티티(`&#123;`), 주석 안. 정규식은 CSS 값 위치·따옴표 단독 값만 본다.
- 예외: 같은 줄에 `/* raw-color-ok: 이유 */` 또는 `// raw-color-ok: 이유`. 이유 없는 예외 주석은 실패.
- 실패 메시지: `파일:줄: 색 값을 직접 쓰지 말고 토큰을 쓰세요 (찾은 값)`.
- `code-highlight.css`도 대상이다(`var(--hljs-*)`만 씀).
- 007~015의 새 화면도 이 검사를 받는다(ANALYSIS-tier-bc 공통 규칙).

## §7. 대비 기준 (`tokenContrast.test.ts`, WCAG 2.2 AA 상대 휘도식)

| 앞 | 뒤 | 기준 |
|---|---|---|
| `--color-text` | `--color-bg`, `--color-surface`, `--color-code-bg`, `--thumb-empty`, `--color-notice-bg`, `--color-warning-bg`, `--diff-*` 4개 | 4.5 |
| `--color-text-muted` | `--color-bg`, `--color-surface` | 4.5 |
| `--color-brand` | `--color-bg`, `--color-surface` | 4.5 |
| `--color-on-fill` | `--color-brand-fill`, `--avatar-1..8` | 4.5 |
| `--color-danger` | `--color-bg`, `--color-surface` | 4.5 |
| `--color-toast-text` | `--color-toast-bg` | 4.5 |
| `--hljs-*` 글자 10개 | `--color-code-bg` | 4.5 |
| `--hljs-addition` / `--hljs-deletion` | `--hljs-addition-bg` / `--hljs-deletion-bg` | 4.5 |
| `--color-focus` | `--color-bg`, `--color-surface` | 3.0 |
| `--color-text-muted`(입력 테두리로) | `--color-bg`, `--color-surface` | 3.0 |
| `--color-warning-border` | `--color-warning-bg` | 3.0 |

- 라이트 짝은 모든 서비스(공통 묶음), 다크 짝은 `[data-theme="dark"]` 블록이 있을 때만(선택 묶음).
- 반투명 색(`--color-overlay`)은 짝에서 뺀다.
- 화면 단위 검사: `e2e/theme.spec.ts` axe `color-contrast` 규칙, 홈·블로그·글 상세·에디터·설정 × 라이트·다크(SC-004).

## §8. 보안·캐시

| 항목 | 규칙 |
|---|---|
| CSP | 변경 없음. `script-src 'self'`(001 `SecurityHeadersFilter`)에서 `/js/theme-init.js`가 실행되고 위반 보고 0 |
| `/js/theme-init.js` | `Cache-Control: no-cache` + `ETag`·`Last-Modified` |
| `/assets/**` | `Cache-Control: max-age=31536000, immutable` |
| SPA 셸·404 | 기존 머리 그대로(404 `private, no-store`) |

## §9. 다크 모드를 끈 빌드 (`VITE_DARK_MODE=false`)

| 항목 | 켠 빌드 | 끈 빌드 |
|---|---|---|
| `<head>`의 `theme-init.js`·`color-scheme` | 있음 | 없음 |
| `data-theme`·`data-theme-choice` | 있음 | 없음(`:root` 라이트 값) |
| 설정 화면 "화면 테마" 칸(옛 머리말 버튼) | 있음 | 없음 |
| `tokens.css` 다크 블록 | 있음 | 있어도 쓰이지 않음(지워도 됨) |
| `code-highlight.css`(라이트 값) | 있음 | 있음 |
| `noRawColors`·`tokenContrast`(라이트) | 실행 | 실행 |
| `tokenContrast`(다크)·`e2e/theme.spec.ts` | 실행 | 건너뜀 |
