import { expect, test, type Page } from '@playwright/test';
import { api, createPost, hasAccount, login } from './support';

/**
 * 002 T121 (constitution 비기능 최소선, FR-013·023): 375px·데스크톱(프로젝트 mobile·desktop)에서 에디터·저장 상태·발행 설정·
 * 미리보기·충돌 배너·비교 창에 가로 스크롤이 없고 저장 상태가 글자로 보인다.
 */
const LONG_WORD = 'https://example.com/' + 'very-long-path-segment-'.repeat(12);
const LONG_CODE = '```\n' + 'const x = "' + 'x'.repeat(240) + '";\n```';
const CONTENT = `# 넓은 내용\n\n${LONG_WORD}\n\n${LONG_CODE}\n\n| a | b | c | d | e | f |\n|---|---|---|---|---|---|\n| ${'긴칸'.repeat(20)} | 1 | 2 | 3 | 4 | 5 |\n`;

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

test.describe('에디터는 375px에서도 가로로 넘치지 않는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('에디터·저장 상태·미리보기·발행 설정·충돌 배너·비교 창', async ({ page }) => {
    test.setTimeout(90_000);
    await login(page);
    const postId = await createPost(page, { title: '반응형 확인', contentMd: CONTENT });
    await page.goto(`/write/${postId}`);
    await expect(page.getByLabel('제목')).toHaveValue('반응형 확인');

    // 에디터 + 미리보기
    const preview = page.getByRole('region', { name: '미리보기' });
    await expect(preview.getByRole('heading', { name: '넓은 내용' })).toBeVisible({
      timeout: 10_000,
    });
    await expectNoHorizontalScroll(page, '에디터·미리보기');

    // 저장 상태는 글자로 보인다
    const status = page.getByRole('status').first();
    await page.getByLabel('본문').fill(`${CONTENT}\n추가 문단`);
    await expect(status).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, { timeout: 10_000 });
    await expect(status).toBeVisible();
    await expectNoHorizontalScroll(page, '저장 상태');

    // 발행 설정
    await page.getByRole('button', { name: '발행하기' }).click();
    const publish = page.getByRole('dialog', { name: '발행 설정' });
    await expect(publish).toBeVisible();
    await expectNoHorizontalScroll(page, '발행 설정');
    await publish.getByRole('button', { name: '닫기' }).click();

    // 다른 곳에서 저장 → 이 탭 입력 → 충돌 배너
    const current = await page.request.get(`/api/posts/${postId}/working-copy`);
    const { version } = (await current.json()) as { version: number };
    const saved = await api(page, 'PUT', `/api/posts/${postId}/working-copy`, {
      title: '다른 곳 제목',
      contentMd: `${CONTENT}\n다른 곳 문단`,
      baseVersion: version,
    });
    expect(saved.status(), await saved.text()).toBe(200);
    await page.getByLabel('본문').fill(`${CONTENT}\n이 탭 문단`);
    const banner = page.getByRole('alert').filter({ hasText: '다른 탭이나 기기에서' });
    await expect(banner).toBeVisible({ timeout: 20_000 });
    await expectNoHorizontalScroll(page, '충돌 배너');

    // 비교 창
    await banner.getByRole('button', { name: '비교하기' }).click();
    const diff = page.getByRole('dialog', { name: '저장된 내용과 비교' });
    await expect(diff).toBeVisible();
    await expect(diff.getByTestId('diff-body')).toBeVisible();
    await expectNoHorizontalScroll(page, '비교 창');
    await diff.getByRole('button', { name: '닫기' }).click();
  });
});
