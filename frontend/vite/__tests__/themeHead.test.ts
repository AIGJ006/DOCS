import { describe, expect, it } from 'vitest';
import { injectThemeHead, themeHead } from '../themeHead';

/** 016 T019: `<meta charset>` 다음에 color-scheme 메타와 theme-init.js (defer·async·type 없음), 끈 빌드는 없음. */
const HTML = `<!doctype html>
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <!--app-head-->
    <title>BuildLOG</title>
    <link rel="stylesheet" href="/assets/index.css">
  </head>
</html>`;

describe('themeHead', () => {
  it.each([undefined, 'true', ''])(
    'VITE_DARK_MODE=%s → charset 다음, CSS보다 앞에 두 태그',
    (value) => {
      const html = injectThemeHead(HTML, value);
      const charset = html.indexOf('<meta charset');
      const meta = html.indexOf('<meta name="color-scheme" content="light dark" />');
      const script = html.indexOf('<script src="/js/theme-init.js"></script>');
      const css = html.indexOf('<link rel="stylesheet"');
      expect(charset).toBeGreaterThanOrEqual(0);
      expect(meta).toBeGreaterThan(charset);
      expect(script).toBeGreaterThan(meta);
      expect(css).toBeGreaterThan(script);
      const tag = html.match(/<script src="\/js\/theme-init\.js"[^>]*>/)?.[0] ?? '';
      expect(tag).not.toMatch(/defer|async|type=/);
    },
  );

  it('VITE_DARK_MODE=false → 넣지 않는다', () => {
    expect(injectThemeHead(HTML, 'false')).toBe(HTML);
  });

  it('charset이 없으면 head 맨 앞', () => {
    const html = injectThemeHead('<html><head><title>x</title></head></html>', undefined);
    expect(html.indexOf('theme-init.js')).toBeLessThan(html.indexOf('<title>'));
  });

  it('플러그인은 다른 플러그인보다 먼저(pre) 같은 결과를 돌려준다', () => {
    const hook = themeHead(undefined).transformIndexHtml as {
      order: string;
      handler: (html: string) => string;
    };
    expect(hook.order).toBe('pre');
    expect(hook.handler(HTML)).toBe(injectThemeHead(HTML, undefined));
  });
});
