import { expect, test, type Page } from '@playwright/test';
import { hasAccount, login } from './support';

/**
 * 001 T147 (quickstart §4-12): 375px에서 가입·로그인·소셜 가입 마무리·설정 화면에 가로 스크롤이 없고, 비밀번호 규칙이 글자와 ✓로
 * 보이며, 주소 칸이 소문자로 바꾸고 `-`·한글을 막고, 로그아웃하면 이 기기의 `draft:{memberId}:*` 임시 글이 지워지되
 * localStorage(테마 같은 화면 설정)는 그대로다.
 */
async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

/** localforage(name devlog, store editor_drafts)와 같은 IndexedDB에 키를 직접 넣고 읽는다. */
function idb(page: Page, action: 'put' | 'keys', key?: string): Promise<string[]> {
  return page.evaluate(
    ({ action, key }) =>
      new Promise<string[]>((resolve, reject) => {
        const open = indexedDB.open('devlog');
        open.onupgradeneeded = () => {
          if (!open.result.objectStoreNames.contains('editor_drafts')) {
            open.result.createObjectStore('editor_drafts');
          }
        };
        open.onerror = () => reject(open.error);
        open.onsuccess = () => {
          const db = open.result;
          if (!db.objectStoreNames.contains('editor_drafts')) {
            db.close();
            resolve([]);
            return;
          }
          const tx = db.transaction('editor_drafts', action === 'put' ? 'readwrite' : 'readonly');
          const store = tx.objectStore('editor_drafts');
          if (action === 'put') {
            store.put({ title: 'e2e', contentMd: 'e2e', savedAt: Date.now() }, key!);
            tx.oncomplete = () => {
              db.close();
              resolve([]);
            };
          } else {
            const request = store.getAllKeys();
            request.onsuccess = () => {
              db.close();
              resolve(request.result.map(String));
            };
          }
          tx.onerror = () => reject(tx.error);
        };
      }),
    { action, key },
  );
}

test.describe('계정 화면 375px·입력 칸·로그아웃 정리', () => {
  test.skip(({ isMobile }) => !isMobile, '375px 화면은 mobile 프로젝트에서');

  test('가입·로그인·소셜 가입 마무리 화면 가로 스크롤 없음', async ({ page }) => {
    for (const path of ['/signup', '/login', '/signup/social']) {
      await page.goto(path);
      await expect(page.locator('main, #root').first()).toBeVisible();
      await page.waitForLoadState('networkidle');
      await expectNoHorizontalScroll(page, path);
    }
  });

  test('비밀번호 규칙은 글자 + ✓, 주소 칸은 소문자·- 차단·한글 차단', async ({ page }) => {
    await page.goto('/signup');
    await page.getByLabel('비밀번호', { exact: true }).fill('Blog#2026a');
    const rules = page.getByRole('list', { name: '비밀번호 규칙' }).locator('li');
    await expect(rules.first()).toBeVisible();
    const count = await rules.count();
    expect(count).toBeGreaterThan(1);
    for (let i = 0; i < count; i++) {
      await expect(rules.nth(i)).toHaveAttribute('data-met', 'true');
      await expect(rules.nth(i)).toContainText('✓');
      expect((await rules.nth(i).innerText()).replace(/[✓✗\s]/g, '').length).toBeGreaterThan(0);
    }
    await page.getByLabel('비밀번호', { exact: true }).fill('abc');
    await expect(rules.locator('[data-met="false"]').first()).toContainText('✗');

    const handle = page.getByLabel('블로그 주소');
    await expect(handle).toHaveAttribute('inputmode', 'url');
    await expect(handle).toHaveAttribute('autocapitalize', 'off');
    await handle.fill('');
    await handle.pressSequentially('My-Blog');
    await expect(handle).toHaveValue('myblog');
    // 한글 자판 입력(조합 결과가 들어오는 경우)도 막는다.
    await handle.pressSequentially('한글2');
    await expect(handle).toHaveValue('myblog2');
  });

  test('설정 화면 가로 스크롤 없음, 로그아웃하면 draft:{memberId}:* 삭제·localStorage 유지', async ({
    page,
  }) => {
    test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
    await login(page);
    const me = (await (await page.request.get('/api/me')).json()) as { memberId: number };
    await page.goto('/settings');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expectNoHorizontalScroll(page, '/settings');

    await page.evaluate(() => localStorage.setItem('theme', 'dark'));
    await idb(page, 'put', `draft:${me.memberId}:e2e-1`);
    await idb(page, 'put', `draft:${me.memberId + 100000}:other`);
    expect(await idb(page, 'keys')).toContain(`draft:${me.memberId}:e2e-1`);

    await page.getByRole('button', { name: '로그아웃' }).click();
    await expect(page.getByRole('button', { name: '로그아웃' })).toHaveCount(0);
    await expect
      .poll(async () =>
        (await idb(page, 'keys')).filter((k) => k.startsWith(`draft:${me.memberId}:`)),
      )
      .toEqual([]);
    expect(await idb(page, 'keys')).toContain(`draft:${me.memberId + 100000}:other`);
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');
  });
});
