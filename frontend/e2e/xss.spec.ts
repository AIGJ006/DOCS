import { expect, test } from '@playwright/test';
import { createPost, hasAccount, login, publish, xssCorpus } from './support';

/**
 * 002 T060 (SC-003, US2 #1·#3·#4): 32개 공격 문자열을 발행해도 알림창이 뜨지 않고, 외부 링크는 새 탭 +
 * `window.opener === null`, 외부 이미지는 `<img>`가 아닌 링크다.
 */
test.describe('본문의 스크립트는 실행되지 않는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('공격 문자열 32개를 발행하고 에디터 미리보기를 열어도 알림창 0번', async ({
    page,
  }, testInfo) => {
    // 같은 계정으로 두 프로젝트가 돌면 미리보기 요청 제한(1분 60번)에 걸린다. 정화는 화면 폭과 무관해 desktop에서만 본다.
    test.skip(
      testInfo.project.name !== 'desktop',
      '미리보기 요청 제한 — desktop 프로젝트에서만 실행',
    );
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

  test('005 글 상세 화면에서도 알림창 0번', async ({ page }, testInfo) => {
    // 발행 요청이 32번이라 한 프로젝트에서만 돌린다. 정화 결과는 화면 폭과 무관하다.
    test.skip(testInfo.project.name !== 'desktop', '발행 횟수 — desktop 프로젝트에서만 실행');
    test.setTimeout(180_000);
    const dialogs: string[] = [];
    page.on('dialog', async (dialog) => {
      dialogs.push(dialog.message());
      await dialog.dismiss();
    });
    await login(page);

    const corpus = xssCorpus();
    expect(corpus).toHaveLength(32);
    const urls: string[] = [];
    for (const { name, markdown } of corpus) {
      const postId = await createPost(page);
      const { url } = await publish(page, postId, `XSS 상세 ${name}`, markdown);
      urls.push(url);
    }

    for (const [index, url] of urls.entries()) {
      await page.goto(url);
      // 상세 화면이 그려졌는지(제목은 글자 그대로) 확인한 뒤 본문 자리를 본다
      await expect(page.getByRole('heading', { level: 1 })).toContainText(
        `XSS 상세 ${corpus[index].name}`,
      );
      await expect(page.getByTestId('post-content')).toBeAttached();
      // 상세에는 인라인 스크립트가 없다 (FR-037)
      await expect(page.locator('article script')).toHaveCount(0);
      await page.waitForTimeout(100);
    }
    expect(dialogs).toEqual([]);
  });
});
