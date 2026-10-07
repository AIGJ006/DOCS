import { expect, test } from '@playwright/test';
import { createPost, hasAccount, login } from './support';

/** 002 T075 (US3 #1·#2·#3·#4): 이 기기 저장 → 서버 저장, 오프라인 → 복구 후 동기화, 떠날 때 확인, 다른 기기에서 열기. */
test.describe('쓰다 만 글을 잃지 않는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('입력 → 이 기기 저장 → 서버 저장, 오프라인 → 복구, 떠날 때 확인, 새 브라우저에서 같은 내용', async ({
    page,
    browser,
    baseURL,
  }) => {
    test.setTimeout(120_000);
    await login(page);
    const postId = await createPost(page);
    await page.goto(`/write/${postId}`);
    const status = page.getByRole('status');

    await page.getByLabel('제목').fill('자동 저장 E2E');
    await page.getByLabel('본문').fill('첫 문단');
    await expect(status).toHaveText('● 이 기기에 저장됨 (동기화 대기)', { timeout: 2500 });
    await expect(status).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, { timeout: 6000 });

    await page.context().setOffline(true);
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));
    await page.getByLabel('본문').fill('첫 문단\n\n오프라인 문단');
    await expect(status).toHaveText('⚠ 오프라인 — 이 기기에 저장 중, 연결되면 자동 동기화', {
      timeout: 5000,
    });
    await page.context().setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    // 서버 요청 제한(5초에 1번)이 걸려 있으면 Retry-After 뒤에 보낸다
    await expect(status).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, { timeout: 15_000 });

    // 미전송 내용이 있을 때 떠나면 확인창 (beforeunload)
    await page.getByLabel('본문').fill('첫 문단\n\n오프라인 문단\n\n떠나기 직전');
    let asked = false;
    page.once('dialog', async (dialog) => {
      asked = dialog.type() === 'beforeunload';
      await dialog.dismiss();
    });
    await page.goto('/', { waitUntil: 'commit' }).catch(() => undefined);
    expect(asked).toBe(true);

    // 다른 기기(IndexedDB 없는 새 컨텍스트)에서 열면 서버에 저장된 내용
    await expect(status).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, { timeout: 15_000 });
    const other = await browser.newContext({ baseURL });
    const otherPage = await other.newPage();
    await login(otherPage);
    await otherPage.goto(`/write/${postId}`);
    await expect(otherPage.getByLabel('제목')).toHaveValue('자동 저장 E2E');
    await expect(otherPage.getByLabel('본문')).toHaveValue(
      '첫 문단\n\n오프라인 문단\n\n떠나기 직전',
    );
    await other.close();
  });
});
