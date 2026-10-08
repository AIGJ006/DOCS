# Data Model: 다크 모드

**Feature**: 016-dark-mode | **Date**: 2026-10-08 | 스키마 변경 없음(V1), 새 테이블·컬럼·Redis 키 없음

이 기능의 "데이터"는 브라우저 저장소 값 하나, `<html>` 속성 두 개, CSS 토큰이다. 서버·DB·Redis에는 아무것도 저장하지 않는다(spec Key Entities, research R1).

## 1. 브라우저 상태

### 1-1. 저장 값 (`localStorage`)

| 키 | 값 | 없을 때 | 쓰는 곳 | 지우는 때 |
|---|---|---|---|---|
| `theme` | `'light'` \| `'dark'` | `system`으로 본다 | `theme-init.js`(읽기), `themeStore.ts`(읽기·쓰기) | "시스템"을 고르면 `removeItem`. 로그아웃·탈퇴 때 지우지 않는다(07 §7, FR-007) |

- `'system'`은 저장하지 않는다(키가 없음 = 시스템). 예전 구현이 `'system'`을 저장해 두었어도 같은 뜻이라 문제없다.
- 세 값 밖의 값(`'Dark'`, `'"dark"'`, 빈 문자열 등)은 `system`으로 보고 지우지 않는다(다음에 사용자가 버튼을 누르면 덮어씀).
- 읽기·쓰기 예외(`SecurityError`, `QuotaExceededError`)는 삼킨다. 쓰기가 실패해도 화면은 바뀐다(이번 페이지에서만 유지).
- 탭 사이 동기화(`storage` 이벤트) 없음(spec Assumptions).

### 1-2. `<html>` 속성

| 속성 | 값 | 정하는 곳 | 뜻 |
|---|---|---|---|
| `data-theme` | `light` \| `dark` | `theme-init.js`(처음), `useTheme`(이후) | 적용 테마. CSS 선택자가 이 값만 본다 |
| `data-theme-choice` | `system` \| `light` \| `dark` | 같음 | 사용자 선택. 버튼 아이콘·이름이 이 값을 본다 |

- `VITE_DARK_MODE=false` 빌드에서는 두 속성 모두 없다 → `:root` 라이트 값.
- 테스트는 이 두 속성으로 상태를 확인한다(화면 색을 직접 비교하지 않음 — 대비는 §3 검사가 따로 본다).

### 1-3. 상태 전이

```text
           누름              누름              누름
 system ─────────▶ light ─────────▶ dark ─────────▶ system
 (키 없음)        (theme=light)    (theme=dark)    (키 지움)

 system 상태: data-theme = 기기 설정(matchMedia), 기기 설정이 바뀌면 따라 바뀜
 light·dark 상태: data-theme = 고른 값, 기기 설정 변경 무시
```

| 지금 선택 | 누르면 | 저장 | `data-theme` | `matchMedia` 구독 |
|---|---|---|---|---|
| `system` | `light` | `theme = light` | `light` | 끊음 |
| `light` | `dark` | `theme = dark` | `dark` | 없음 |
| `dark` | `system` | 키 지움 | 기기 설정 | 시작 |

## 2. 색 토큰

모든 값은 `F/styles/tokens.css` 한 곳에만 있다. 선택자:

```css
:root, [data-theme="light"] { /* 라이트 값 */ }
[data-theme="dark"]         { /* 다크 값 */ }
```

### 2-1. 공통 토큰 11개 (이름 = 세 사람 공통 규격, 값 = 이 저장소 기본값)

| 토큰 | 용도 | 라이트 | 다크 | 근거 |
|---|---|---|---|---|
| `--color-bg` | 페이지 배경 | `#FFFFFF` | `#121212` | FR-013 |
| `--color-surface` | 카드·머리말·상자·대화 상자 면 | `#FFFFFF` | `#1E1E1E` | FR-013 |
| `--color-text` | 본문 글자 | `#212529` | `#E9ECEF` | FR-013 |
| `--color-text-muted` | 날짜·보조 글자, **입력칸·버튼 윤곽 테두리** | `#6C757D` | `#ADB5BD` | FR-013, research R2 |
| `--color-border` | 구분선·카드 테두리(장식) | `#DEE2E6` | `#343A40` | FR-013 |
| `--color-brand` | 링크·강조 글자 | `#1971C2` | `#74C0FC` | 제안(T003) |
| `--color-brand-fill` | 흰 글자가 올라가는 버튼 배경 | `#1971C2` | `#1864AB` | 제안(T003) |
| `--color-danger` | 삭제·오류 글자와 버튼 | `#C92A2A` | `#FF6B6B` | FR-013 |
| `--thumb-empty` | 썸네일 빈 영역, 태그 칩·배지 배경 | `#F1F3F5` | `#2B2F33` | FR-013(예시값) |
| `--color-code-bg` | 코드 블록 배경 | `#F8F9FA` | `#1A1B1E` | FR-013 |
| `--color-focus` | 키보드 포커스 테두리 | `#1C7ED6` | `#74C0FC` | FR-013 |

쓰지 않는 조합(대비 부족 — `tokenContrast.test.ts`가 짝으로 묶지 않고, 화면 검토 때 확인):

- `--color-text-muted` 글자 on `--color-code-bg`(라이트 4.45), on `--thumb-empty`(라이트 4.22) → 태그 칩·배지 글자는 `--color-text`
- `--color-border`를 입력칸 테두리로 쓰기(라이트 1.30, 다크 1.63) → `--color-text-muted`
- `--color-danger` 배경 위 흰 글자(다크 `#FF6B6B`는 2.7) → 삭제 버튼은 테두리·글자만 `--color-danger`, 면은 `--color-surface`

### 2-2. 보조 토큰 (이름은 이 저장소 것 — 공통 규격 아님, T003)

| 토큰 | 쓰는 곳 | 라이트 | 다크 |
|---|---|---|---|
| `--color-overlay` | 대화 상자 뒤 어둡게(002 `editor.css`, 006 `dialogs.css`) | `rgb(0 0 0 / 40%)` | `rgb(0 0 0 / 60%)` |
| `--color-notice-bg` | 작성자 안내 줄(005 `AuthorStatusBanner`), 수정 중 안내 | `#FFF9DB` | `#3B3000` |
| `--color-warning-bg` | 에디터 충돌·저장 경고 상자 | `#FFF4E5` | `#3A2A12` |
| `--color-warning-border` | 같은 상자 테두리 | `#B26A00` | `#FFB84D` |
| `--diff-del-bg` / `--diff-add-bg` | 비교 화면 지운 줄 / 더한 줄 | `#FFEBE9` / `#E6FFEC` | `#3D1F1F` / `#1C3A26` |
| `--diff-del-strong` / `--diff-add-strong` | 비교 화면 글자 단위 강조 | `#FFC1BC` / `#ABF2BC` | `#7A2E2E` / `#2D6B3F` |
| `--color-toast-bg` / `--color-toast-text` | 알림 줄(006 `Toast`) | `#222222` / `#FFFFFF` | `#E9ECEF` / `#121212` |
| `--color-on-fill` | 채운 버튼·아바타 위 글자 | `#FFFFFF` | `#FFFFFF` |

모든 글자는 위 배경 위에서 `--color-text`(또는 토스트는 `--color-toast-text`)이고 대비 9.99 이상(다크 `--diff-add-strong` 위 5.39). 경고 상자 테두리는 3:1 이상(라이트 4.24, 다크 10.9).

### 2-3. 기본 프로필 원 8색 (001 T120이 쓴다)

| 토큰 | 값(라이트·다크 같음) | 흰 글자 대비 |
|---|---|---|
| `--avatar-1` | `#C92A2A` | 5.46 |
| `--avatar-2` | `#A61E4D` | 7.21 |
| `--avatar-3` | `#862E9C` | 7.28 |
| `--avatar-4` | `#5F3DC4` | 7.12 |
| `--avatar-5` | `#364FC7` | 6.77 |
| `--avatar-6` | `#1864AB` | 6.09 |
| `--avatar-7` | `#0B7285` | 5.59 |
| `--avatar-8` | `#087F5B` | 5.00 |

- 원 안의 글자(닉네임 첫 글자)는 `--color-on-fill`(흰색) 고정이라 두 테마가 같은 값을 쓴다(FR-016).
- 고르는 규칙(회원 ID로 1~8)은 001 T120 소유. 지금 저장소의 임시 `F/components/DefaultAvatar.tsx`(005)는 글자 없는 회색 원(`--card-avatar-bg`)이다 — 이 기능은 이름만 `--thumb-empty`로 바꾸고, 001 T120이 이 8색으로 바꾼다.

### 2-4. 코드 강조 토큰 (`--hljs-*`)

highlight.js 11 `github.css`·`github-dark.css`의 값을 옮기되, 이 저장소 코드 블록 배경(`--color-code-bg`) 위에서 4.5:1이 안 되는 4개는 GitHub Primer의 새 값으로 바꾼다(*표).

| 토큰 | `.hljs-*` 클래스 | 라이트 | 다크 |
|---|---|---|---|
| `--hljs-text` | `.hljs`, `subst`, `emphasis`, `strong` | `#24292E` (13.9) | `#C9D1D9` (11.2) |
| `--hljs-keyword` | `doctag`, `keyword`, `template-tag`, `template-variable`, `type`, `variable.language_` | `#CF222E`* (5.08) | `#FF7B72` (6.83) |
| `--hljs-title` | `title`, `title.class_`, `title.function_` | `#6F42C1` (6.18) | `#D2A8FF` (8.84) |
| `--hljs-attr` | `attr`, `attribute`, `literal`, `meta`, `number`, `operator`, `variable`, `selector-attr`, `selector-class`, `selector-id` | `#005CC5` (5.97) | `#79C0FF` (8.85) |
| `--hljs-string` | `regexp`, `string`, `meta .hljs-string` | `#032F62` (12.6) | `#A5D6FF` (11.2) |
| `--hljs-built-in` | `built_in`, `symbol` | `#BC4C00`* (4.78) | `#FFA657` (8.89) |
| `--hljs-comment` | `comment`, `code`, `formula` | `#6A737D` (4.57) | `#8B949E` (5.60) |
| `--hljs-name` | `name`, `quote`, `selector-tag`, `selector-pseudo` | `#1A7F37`* (4.82) | `#7EE787` (11.2) |
| `--hljs-section` | `section` (굵게) | `#005CC5` (5.97) | `#58A6FF`* (6.82) |
| `--hljs-bullet` | `bullet` | `#735C0F` (6.10) | `#F2CC60` (11.1) |
| `--hljs-addition` / `--hljs-addition-bg` | `addition` | `#1A7F37` / `#F0FFF4` | `#AFF5B4` / `#033A16` |
| `--hljs-deletion` / `--hljs-deletion-bg` | `deletion` | `#B31D28` / `#FFEEF0` | `#FFDCD7` / `#67060C` |

괄호 안은 `--color-code-bg` 대비(덧붙임·지움은 각자 배경 대비, 모두 4.5 이상). 원래 값: 라이트 keyword `#D73A49`(4.34), built_in `#E36209`(3.31), name `#22863A`(4.39), 다크 section `#1F6FEB`(3.72).

## 3. 검사 규칙 (테스트가 데이터로 보는 것)

| 검사 | 파일 | 묶음 | 실패 조건 |
|---|---|---|---|
| 색 직접 쓰기 | `F/styles/__tests__/noRawColors.test.ts` | 공통 | `F/` 아래 `.ts`·`.tsx`·`.css`(테스트, `styles/tokens.css` 제외)에 색 값이 있음 — contracts/theme.md §6 |
| 대비 | `F/styles/__tests__/tokenContrast.test.ts` | 공통(라이트), 선택(다크) | contracts/theme.md §7 짝 중 하나라도 기준 미만 |
| 전환 애니메이션 | `noRawColors.test.ts` 안 | 공통 | `transition`에 `color`·`background`·`border-color`·`all`이 있음 |
| 사진 필터 | `noRawColors.test.ts` 안 | 공통 | `filter`·`mix-blend-mode`가 `img`·썸네일·아바타 규칙에 있음 |
| 화면 대비 | `e2e/theme.spec.ts` (axe `color-contrast`) | 선택 | 5개 화면 × 2 테마 중 위반 1건 이상 |

## 4. 서버 쪽 (데이터 아님, 응답 머리만)

| 경로 | `Cache-Control` | 이유 |
|---|---|---|
| `/js/**` (`theme-init.js`) | `no-cache` | 파일 이름에 해시 없음 — 배포 뒤 바로 새 파일 |
| `/assets/**` (Vite 해시 파일) | `max-age=31536000, immutable` | 내용이 바뀌면 이름이 바뀜 |
| SPA 셸(`index.html` forward, 005 `SpaShellRenderer`, 004 `NotFoundPageRenderer`) | `no-cache` | 셸이 새 해시 파일을 가리키도록 |

셸 응답은 이미 001·005가 `no-store` 등을 주고 있으면 그 값을 따른다(이 기능은 바꾸지 않음 — T011에서 확인).
