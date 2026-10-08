import { expect, test, type Page } from '@playwright/test';
import { api, createPost, hasAccount, login, publish } from './support';

/**
 * 004 quickstart §3·§4 화면 확인 (T078): 글 상세 [공개 범위 ▾]로 비공개 → 비회원 404(없는 번호와 같은 화면) → 다시 공개해도 최초
 * 공개 일자 그대로, 같은 값·FRIENDS·비회원 요청, 내 글 관리 줄의 공개 확인창, 관리자 경로, 375px 가로 스크롤 없음.
 *
 * 인증 전 회원(시나리오 6)·설정 API(시나리오 8)·다른 회원 B(시나리오 4 일부)는 001 기능·두 번째 계정이 필요해 통합 테스트
 * (AccountStateIT·DefaultVisibilityIT·PermissionMatrixIT)로 대신한다.
 */
async function me(page: Page): Promise<{ handle: string }> {
  const response = await page.request.get('/api/me');
  expect(response.status()).toBe(200);
  return (await response.json()) as { handle: string };
}

async function detailJson(page: Page, postId: number) {
  const response = await page.request.get(`/api/posts/${postId}`);
  expect(response.status()).toBe(200);
  return (await response.json()) as { visibility: string; editedAt: string | null };
}

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

test.describe('공개 범위와 권한 (004)', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('관리자 경로: 비회원은 있는 주소·없는 주소 모두 같은 401, 일반 회원은 404', async ({
    page,
    playwright,
    baseURL,
  }) => {
    const anonymous = await playwright.request.newContext({ baseURL });
    const anonBodies = new Set<string>();
    for (const path of ['/admin/reports', '/admin/xyz']) {
      const response = await anonymous.get(path);
      expect(response.status(), path).toBe(401);
      anonBodies.add(await response.text());
    }
    expect(anonBodies.size).toBe(1);
    const apiAnon = await anonymous.get('/api/admin/xyz');
    expect(apiAnon.status()).toBe(401);
    expect(((await apiAnon.json()) as { code: string }).code).toBe('LOGIN_REQUIRED');
    await anonymous.dispose();

    await login(page);
    for (const path of ['/admin/reports', '/admin/xyz', '/api/admin/xyz']) {
      const response = await page.request.get(path);
      expect(response.status(), path).toBe(404);
    }
    await page.goto('/admin/reports');
    await expect(page.getByText('볼 수 없는 페이지예요')).toBeVisible();
  });

  test('상세 [공개 범위 ▾] → 비공개 즉시 404, 다시 공개해도 최초 공개 일자 그대로, 내 글 관리 확인창, 375px', async ({
    page,
    playwright,
    baseURL,
  }) => {
    const stamp = Date.now().toString(36);
    await login(page);
    const { handle } = await me(page);
    const title = `E2E 공개 범위 ${stamp}`;
    const postId = await createPost(page, {});
    await publish(page, postId, title, '공개 범위 본문');
    const detailPath = `/@${handle}/posts/${postId}`;
    const firstVisibility = await api(page, 'PUT', `/api/posts/${postId}/visibility`, {
      visibility: 'PUBLIC',
    });
    expect(firstVisibility.status(), '같은 값 → 200').toBe(200);
    const firstPublicAt = ((await firstVisibility.json()) as { firstPublicAt: string })
      .firstPublicAt;

    // 시나리오 1: 작성자가 상세 화면에서 비공개로 바꾼다 — 다시 발행 없음, "수정됨" 없음
    await page.goto(detailPath);
    const select = page.getByRole('combobox', { name: '공개 범위' });
    await expect(select).toHaveValue('PUBLIC');
    await select.selectOption('PRIVATE');
    await expect.poll(async () => (await detailJson(page, postId)).visibility).toBe('PRIVATE');
    expect((await detailJson(page, postId)).editedAt).toBeNull();

    // 비회원: 화면 404, 없는 번호와 같은 응답(Date 제외), 홈 목록에 없음
    const anonymous = await playwright.request.newContext({ baseURL });
    const hiddenPage = await anonymous.get(detailPath);
    const missingPage = await anonymous.get(`/@${handle}/posts/999999999`);
    expect(hiddenPage.status()).toBe(404);
    expect(missingPage.status()).toBe(404);
    expect(await hiddenPage.text()).toBe(await missingPage.text());
    expect(hiddenPage.headers()['cache-control']).toBe('private, no-store');
    expect(await hiddenPage.text()).toContain('볼 수 없는 글이에요');
    expect(await hiddenPage.text()).toContain('noindex');
    const home = (await (await anonymous.get('/api/posts?size=9')).json()) as {
      items: { id: number }[];
    };
    expect(home.items.map((item) => item.id)).not.toContain(postId);

    // 시나리오 4: FRIENDS 미적용 → 400, 비회원 → 401
    const friends = await api(page, 'PUT', `/api/posts/${postId}/visibility`, {
      visibility: 'FRIENDS',
    });
    expect(friends.status()).toBe(400);
    expect(((await friends.json()) as { code: string }).code).toBe('INVALID_VISIBILITY');
    const anonPut = await anonymous.put(`/api/posts/${postId}/visibility`, {
      data: { visibility: 'PUBLIC' },
    });
    expect([401, 403]).toContain(anonPut.status());
    await anonymous.dispose();

    // 시나리오 3: 내 글 관리 줄에서 다시 공개 — 확인창 뒤 바뀌고 최초 공개 일자는 처음 값
    await page.goto('/manage/posts?tab=published');
    const row = page.locator('li.manage-row').filter({ hasText: title });
    await expect(row.getByText('비공개')).toBeAttached();
    await row.getByRole('combobox', { name: '공개 범위' }).selectOption('PUBLIC');
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('모든 사람이 볼 수 있게 돼요');
    await dialog.getByRole('button', { name: '공개로 바꾸기' }).click();
    await expect(row.getByText('공개', { exact: true })).toBeAttached();
    const again = await api(page, 'PUT', `/api/posts/${postId}/visibility`, {
      visibility: 'PUBLIC',
    });
    expect(((await again.json()) as { firstPublicAt: string }).firstPublicAt).toBe(firstPublicAt);

    // §4: 375px에서 상세·내 글 관리 가로 스크롤 없음
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(detailPath);
    await expect(page.getByRole('combobox', { name: '공개 범위' })).toBeVisible();
    await expectNoHorizontalScroll(page, '글 상세(작성자)');
    await page.goto('/manage/posts?tab=published');
    await expect(row).toBeVisible();
    await expectNoHorizontalScroll(page, '내 글 관리 발행 글');
  });
});
