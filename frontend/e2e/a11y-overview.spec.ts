import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { E2E_PASSWORD, createPost, hasAccount, login, publish } from './support';

/**
 * 배포 전 전체 점검 (final-check 5): 주요 화면이 가로로 넘치지 않고(375px은 mobile 프로젝트) axe 심각·치명 위반이 없다.
 * 비회원 화면, 로그인한 회원 화면, 관리자 화면(`E2E_ADMIN_EMAIL`이 있을 때)을 본다. 색 대비는 theme.spec이 라이트·다크로 본다.
 */
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? '';

async function check(page: Page, path: string, ready: () => Promise<void>) {
  await page.goto(path);
  await ready();
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${path}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .disableRules(['color-contrast'])
    .analyze();
  const serious = result.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}(${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(serious, `${path}: axe 심각 위반`).toEqual([]);
}

const main = (page: Page) => () => expect(page.locator('main').first()).toBeVisible();

test.describe('주요 화면 가로 넘침·접근성 (final-check)', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD가 필요합니다');

  test('비회원 화면', async ({ page }) => {
    await login(page);
    const me = (await (await page.request.get('/api/me')).json()) as { handle: string };
    const postId = await createPost(page, {});
    await publish(
      page,
      postId,
      '접근성 점검 글 아주 긴 제목이 화면 폭을 넘지 않는지 보는 제목입니다',
      '# 머리말\n\n본문 `code` 와 아주긴단어아주긴단어아주긴단어아주긴단어아주긴단어아주긴단어아주긴단어아주긴단어\n\n```js\nconst x = "아주 긴 코드 줄이 화면 밖으로 나가더라도 코드 블록 안에서만 스크롤되어야 한다";\n```',
    );
    await page.context().clearCookies();

    await check(page, '/', main(page));
    await check(page, '/login', main(page));
    await check(page, '/signup', main(page));
    await check(page, '/search?q=' + encodeURIComponent('접근성'), main(page));
    await check(page, '/tags', main(page));
    await check(page, `/@${me.handle}`, main(page));
    await check(page, `/@${me.handle}/posts/${postId}`, () =>
      expect(page.getByRole('heading', { level: 1 })).toBeVisible(),
    );
    await check(page, `/@${me.handle}/followers`, main(page));
    await check(page, '/no-such-page-final-check', main(page));
  });

  test('로그인한 회원 화면', async ({ page }) => {
    await login(page);
    await check(page, '/feed', main(page));
    await check(page, '/notifications', main(page));
    await check(page, '/manage/posts', main(page));
    await check(page, '/manage/categories', main(page));
    await check(page, '/settings', main(page));
    await check(page, '/write/new', () => expect(page.getByLabel('제목')).toBeVisible());
  });

  test('관리자 신고 목록', async ({ page }) => {
    test.skip(!ADMIN_EMAIL, 'E2E_ADMIN_EMAIL이 필요합니다');
    const token = await page.request.get('/api/auth/csrf').then(async () => {
      const cookie = (await page.request.storageState()).cookies.find(
        (c) => c.name === 'XSRF-TOKEN',
      );
      return decodeURIComponent(cookie?.value ?? '');
    });
    const response = await page.request.post('/api/auth/login', {
      form: { email: ADMIN_EMAIL, password: E2E_PASSWORD },
      headers: { 'X-XSRF-TOKEN': token },
    });
    expect(response.status()).toBe(200);
    await check(page, '/admin/reports', main(page));
  });
});
