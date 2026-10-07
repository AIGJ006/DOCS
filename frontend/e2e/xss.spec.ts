import { expect, test } from '@playwright/test';
import { createPost, hasAccount, login, publish, xssCorpus } from './support';

/**
 * 002 T060 (SC-003, US2 #1·#3·#4): 32개 공격 문자열을 발행해도 알림창이 뜨지 않고, 외부 링크는 새 탭 +
 * `window.opener === null`, 외부 이미지는 `<img>`가 아닌 링크다.
 */
test.describe('본문의 스크립트는 실행되지 않는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('공격 문자열 32개를 발행하고 에디터 미리보기를 열어도 알림창 0번', async ({ page }) => {
    test.setTimeout(180_000);
    const dialogs: string[] = [];
    page.on('dialog', async (dialog) => {
      dialogs.push(dialog.message());
      await dialog.dismiss();
    });
    await login(page);

    const corpus = xssCorpus();
    expect(corpus).toHaveLength(32);
    for (const { name, markdown } of corpus) {
      const postId = await createPost(page);
      await publish(page, postId, `XSS ${name}`, markdown);
      await page.goto(`/write/${postId}`);
      await expect(page.getByLabel('본문')).toHaveValue(markdown);
      await expect(page.getByTestId('preview-html')).not.toBeEmpty();
      await page.waitForTimeout(200);
    }
    expect(dialogs).toEqual([]);
  });

  test('외부 링크는 새 탭으로 열리고 opener가 없다, 외부 이미지는 링크다', async ({
    page,
    context,
  }) => {
    await context.route('https://spring.io/**', (route) =>
      route.fulfill({ status: 200, contentType: 'text/html', body: '<p>밖</p>' }),
    );
    await login(page);
    const postId = await createPost(page, {
      title: '링크',
      contentMd: '[스프링](https://spring.io/)\n\n![밖 사진](https://evil.example/a.png)',
    });
    await page.goto(`/write/${postId}`);

    const preview = page.getByTestId('preview-html');
    const link = preview.getByRole('link', { name: '스프링' });
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(preview.locator('img')).toHaveCount(0);
    await expect(preview.getByRole('link', { name: '[이미지] 밖 사진' })).toBeVisible();

    const [popup] = await Promise.all([context.waitForEvent('page'), link.click()]);
    await popup.waitForLoadState();
    expect(await popup.evaluate(() => window.opener)).toBeNull();
  });

  test.fixme('005 글 상세 화면에서도 알림창 0번', async () => {
    // 005 글 상세 화면(/@{handle}/posts/{id})이 이 브랜치에 없다. 005가 들어오면 같은 32개 글을 상세 화면으로 연다.
  });
});
