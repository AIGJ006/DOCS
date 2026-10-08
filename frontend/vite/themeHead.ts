import type { Plugin } from 'vite';

/**
 * 016 다크 모드: 빌드한 `index.html`의 `<head>`에서 `<meta charset>` 바로 다음(CSS `<link>`보다 앞)에
 * `color-scheme` 메타와 `/js/theme-init.js`를 넣는다 (research R6·R7, contracts/theme.md §3).
 *
 * - CSS보다 앞에서 동기 실행돼 첫 그리기 전에 `data-theme`가 정해진다(FR-008).
 * - 인라인 스크립트가 아니라 우리 사이트 파일이라 CSP `script-src 'self'` 그대로다(FR-009).
 * - `defer`·`async`·`type="module"`을 붙이지 않는다.
 * - `VITE_DARK_MODE=false`면 아무것도 넣지 않는다(다크 모드를 만들지 않는 서비스, FR-001).
 *
 * 서버 셸(005 `SpaShellRenderer`, 004 `NotFoundPageRenderer`)은 이 빌드 결과를 읽으므로 같은 태그를 갖는다.
 */
export const THEME_HEAD_TAGS =
  '<meta name="color-scheme" content="light dark" />\n    <script src="/js/theme-init.js"></script>';

const CHARSET = /<meta charset="[^"]*"\s*\/?>/i;

export function injectThemeHead(html: string, darkMode: string | undefined): string {
  if (darkMode === 'false') {
    return html;
  }
  if (CHARSET.test(html)) {
    return html.replace(CHARSET, (charset) => `${charset}\n    ${THEME_HEAD_TAGS}`);
  }
  return html.replace(/<head>/i, (head) => `${head}\n    ${THEME_HEAD_TAGS}`);
}

export function themeHead(darkMode: string | undefined): Plugin {
  return {
    name: 'blog-theme-head',
    transformIndexHtml: {
      order: 'pre',
      handler: (html: string) => injectThemeHead(html, darkMode),
    },
  };
}
