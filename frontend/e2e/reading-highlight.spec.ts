import { expect, test, type Page } from '@playwright/test';
import { createPost, hasAccount, login, publish } from './support';

/**
 * 005 T075 (FR-031, quickstart Q-12): 코드 강조(highlight.js)는 첫 번들과 따로 나뉜 청크이고, 코드 블록이 없는 글 상세는
 * 그 청크를 한 번도 받지 않는다. 코드 블록이 있는 글은 그 청크를 받아 강조한다.
 */
const HIGHLIGHT_CHUNK = /\/assets\/highlightCode-[\w-]+\.js$/;

function recordScripts(page: Page): string[] {
  const urls: string[] = [];
  page.on('request', (request) => {
    if (request.resourceType() === 'script') {
      urls.push(new URL(request.url()).pathname);
    }
  });
  return urls;
}

test.describe('코드 강조 청크는 필요할 때만 받는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  test.skip(({ isMobile }) => isMobile, '번들 분리는 화면 폭과 무관 — desktop에서만');

  test('코드 블록이 없는 글은 강조 청크 요청 0건, 있는 글은 받아서 강조한다', async ({ page }) => {
    await login(page);
    const plainId = await createPost(page);
    const plain = await publish(page, plainId, '코드 없는 글', '그냥 문단입니다.\n\n- 목록 하나');
    const codeId = await createPost(page);
    const code = await publish(
      page,
      codeId,
      '코드 있는 글',
      '설명\n\n```java\nclass A { int x = 1; }\n```\n',
    );

    const scripts = recordScripts(page);
    await page.goto(plain.url);
    await expect(page.getByRole('heading', { level: 1, name: '코드 없는 글' })).toBeVisible();
    await expect(page.getByTestId('post-content')).toContainText('그냥 문단입니다.');
    await page.waitForLoadState('networkidle');
    expect(scripts.length, '첫 번들은 받았다').toBeGreaterThan(0);
    expect(scripts.filter((url) => HIGHLIGHT_CHUNK.test(url))).toEqual([]);

    await page.goto(code.url);
    await expect(page.getByTestId('post-content').locator('code.hljs')).toBeVisible();
    expect(scripts.filter((url) => HIGHLIGHT_CHUNK.test(url))).toHaveLength(1);
  });
});
