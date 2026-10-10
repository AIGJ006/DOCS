# Quickstart: 016-dark-mode 검증 시나리오

**Feature**: `016-dark-mode` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. 규칙은 [contracts/theme.md](./contracts/theme.md), 토큰 값은 [data-model.md](./data-model.md)를 본다. **공통 묶음**(모든 서비스)과 **선택 묶음**(다크 모드를 켠 서비스)을 나눠 확인한다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS, Playwright 브라우저(`npx playwright install chromium`)
- 선행 기능: 001(`SecurityHeadersFilter`·`SessionBar`·`logout.ts`·`CacheControlPolicy`), 002(`editor.css`·`highlightCode.ts`), 004(`NotFoundPageRenderer`·`VisibilityBadge`), 005(카드·상세·블로그·`SpaShellRenderer`)
- 있으면 함께 확인: 006(대화 상자·관리 목록·알림 줄), 001 T120(`DefaultAvatar` 8색), 007~015 화면(색 직접 쓰기 검사)

## 1. 기동

```bash
docker compose up -d postgres redis minio
(cd frontend && npm ci && npm run build)          # VITE_DARK_MODE 기본 true
./mvnw -pl backend spring-boot:run                # dist를 정적 경로로 서빙
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `frontend/dist/index.html`의 `<head>` 맨 앞(`<meta charset>` 다음)에 `<meta name="color-scheme" content="light dark">`와 `<script src="/js/theme-init.js"></script>`가 CSS `<link>`보다 먼저 있다
- `frontend/dist/js/theme-init.js`가 1KB 미만

## 2. 자동 테스트

```bash
(cd frontend && npx vitest run src/styles src/features/theme)
(cd frontend && npx playwright test e2e/theme.spec.ts)
./mvnw -pl backend test -Dtest=StaticResourceCacheConfigTest
(cd frontend && VITE_DARK_MODE=false npm run build && npx vitest run src/styles)   # 끈 빌드
```

| 테스트 | 묶음 | 확인하는 것 |
|---|---|---|
| `noRawColors.test.ts` | 공통 | `F/` 아래 색 값 직접 쓰기 0, `var(--x, #색)` 대체 값 0, 색 `transition` 0, 사진 `filter` 0, 예외 주석에 이유 있음(contracts §6) |
| `tokenContrast.test.ts` | 공통(라이트)·선택(다크) | contracts §7 짝 전부 기준 이상, 공통 토큰 11개가 라이트 블록에 모두 있음 |
| `themeStore.test.ts` | 선택 | 저장 값 읽기(모르는 값·예외 → `system`), `system` 저장 = 키 지움, 순서 system → light → dark → system |
| `useTheme.test.ts` | 선택 | 처음 값은 `data-theme-choice`에서, `system`일 때만 `matchMedia` 변경 구독, 고정 상태에서 구독 해제 |
| `ThemeSettings.test.tsx`(옛 `ThemeToggle.test.tsx` (2026-10-10 민서 결정: 설정 화면 라디오로 옮김)) | 선택 | 아이콘·`aria-label`·`title` 3가지, 누르면 즉시 두 속성 변경, `aria-live` 안내, `DARK_MODE_ENABLED=false`면 그리지 않음 |
| `themeInit.test.ts` | 선택 | `public/js/theme-init.js`를 jsdom에서 실행 — contracts §3 표 6행 |
| `logout.test.ts`(001 파일에 추가) | 선택 | 로그아웃 뒤 `theme` 남음(US2 #5) |
| `StaticResourceCacheConfigTest` | 선택 | `/js/theme-init.js` `no-cache`+ETag, `/assets/x.js` `max-age=31536000, immutable`, 404 셸 머리 그대로 |
| `e2e/theme.spec.ts` | 선택 | US1 #1~#4(첫 그리기 배경색·저장소 막힘), US2 #1~#5(버튼 순서·새로 고침·이동 유지·`emulateMedia` 변경 따라감·고정 유지·로그아웃 유지), US3 #2·#3·#5, CSP 위반 0(SC-003), axe `color-contrast` 5개 화면 × 2 테마 0건(SC-004), 375px 머리말 넘침 없음 |

## 3. 수동 확인 (브라우저)

1. 운영체제를 다크로 두고 새 시크릿 창으로 홈을 연다 → 처음부터 어두운 배경, 흰 화면이 번쩍이지 않는다(개발자 도구 Performance 녹화 첫 프레임도 어두움)
2. 로그인해 설정 화면 "화면 테마"를 연다 → "시스템 설정 따르기 (기본)"이 골라져 있다 (2026-10-10 민서 결정: 설정 화면 라디오로 옮김 — 옛 머리말 🖥/☀️/🌙 버튼 절차를 대신한다)
3. "라이트 모드"를 고른다 → 즉시 라이트. 새로 고침·글 상세로 이동해도 라이트. 개발자 도구 Application → Local Storage에 `theme = light`
4. "다크 모드"를 고른다 → 즉시 다크. 운영체제를 라이트로 바꿔도 다크 그대로
5. "시스템 설정 따르기 (기본)"을 고른다 → `theme` 키가 사라짐. 운영체제를 다크 ↔ 라이트로 바꾸면 새로 고치지 않아도 따라 바뀐다
6. 다크로 고른 뒤 로그아웃 → 다크 그대로, `theme` 키 남음
7. 개발자 도구 Console에 CSP 위반(`Refused to execute inline script`)이 없다
8. 다크에서 코드 블록이 있는 글 → 코드 강조가 어두운 색 구성, 사진·썸네일·프로필 사진 색이 라이트와 같다
9. 다크에서 썸네일 없는 카드 → 빈 영역이 카드 면과 구분된다. 비공개 글 배지 "🔒 비공개"가 글자로 보인다
10. 다크에서 에디터 → 입력칸·미리보기·비교 화면·경고 상자가 모두 어둡고 읽힌다. 입력칸 테두리가 보인다
11. Tab 키로 이동 → 포커스 테두리가 라이트·다크 모두 보인다
12. 다크에서 스크롤바·날짜 입력칸 등 브라우저 기본 요소가 어둡다. 라이트로 고정하면 운영체제가 다크여도 밝다
13. 없는 주소(`/@nobody/1`) → 404 화면도 같은 테마
14. 375px 폭에서 버튼이 머리말을 넘치지 않고 아이콘만 보인다
15. `VITE_DARK_MODE=false`로 빌드 → 버튼 없음, 운영체제가 다크여도 라이트, `<head>`에 `theme-init.js` 없음

## 4. 다른 기능 확인 (있을 때)

- 006: 휴지통 비우기 대화 상자·관리 목록·알림 줄이 다크에서 읽힌다(뒤 어둡게 `--color-overlay`)
- 001 T120: 기본 프로필 원 8색 위 흰 글자가 라이트·다크 모두 읽힌다
- 007~015: 각 기능의 새 화면이 `noRawColors`를 통과하고 다크에서 읽힌다(댓글·알림·신고 대화 상자·관리자 화면·설정)
