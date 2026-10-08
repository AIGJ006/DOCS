import { expect, test, type Page } from '@playwright/test';
import { E2E_EMAIL, E2E_PASSWORD, hasAccount } from './support';

/**
 * 공통 머리말: 홈에서 머리말 [로그인]으로 로그인하고, 머리말 [글쓰기]로 새 글 화면에 간다.
 * 375px(mobile 프로젝트)에서도 같은 길로 가고, 머리말이 가로로 넘치지 않는다.
 */
async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

test('홈 → 머리말 [로그인] → 머리말 [글쓰기]', async ({ page }) => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  await page.goto('/');
  const header = page.getByRole('banner');
  await expect(header.getByRole('link', { name: '회원 가입' })).toBeVisible();
  await expectNoHorizontalScroll(page, '홈(비로그인)');

  await header.getByRole('link', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('이메일').fill(E2E_EMAIL);
  await page.getByLabel('비밀번호').fill(E2E_PASSWORD);
  await page.getByRole('main').getByRole('button', { name: '로그인' }).click();

  await expect(page).toHaveURL(/\/$/);
  const write = page.getByRole('banner').getByRole('link', { name: '글쓰기' });
  await expect(write).toBeVisible();
  await expect(page.getByRole('banner').getByRole('button', { name: /계정 메뉴/ })).toBeVisible();
  await expectNoHorizontalScroll(page, '홈(로그인)');

  await write.click();
  await expect(page).toHaveURL(/\/write\/(new|\d+)/);
  // 글쓰기 화면에도 머리말은 남고 [글쓰기]만 빠진다
  await expect(page.getByRole('banner').getByRole('button', { name: /계정 메뉴/ })).toBeVisible();
  await expect(page.getByRole('banner').getByRole('link', { name: '글쓰기' })).toHaveCount(0);
});
