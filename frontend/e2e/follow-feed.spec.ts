import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { api, createPost, hasAccount, login } from './support';

/**
 * 010 quickstart §3 화면 확인 1~12번 (T044·T045).
 *
 * 회원 넷을 환경 변수로 받는다(모두 이메일 인증, 비밀번호 `E2E_PASSWORD`):
 * - B 작성자 `E2E_EMAIL` — 공개 글 둘·비공개 글 하나
 * - C 작성자 `E2E_AUTHOR2_EMAIL` — 공개 글 하나
 * - A 독자 `E2E_READER_EMAIL` — B·C를 팔로우한다
 * - D `E2E_LONELY_EMAIL` — 아무도 팔로우하지 않는다
 */
const READER_EMAIL = process.env.E2E_READER_EMAIL ?? '';
const AUTHOR2_EMAIL = process.env.E2E_AUTHOR2_EMAIL ?? '';
const LONELY_EMAIL = process.env.E2E_LONELY_EMAIL ?? '';
const PASSWORD = process.env.E2E_PASSWORD ?? '';

async function newPage(browser: Browser): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext();
  return { context, page: await context.newPage() };
}

async function loginAs(page: Page, email: string): Promise<void> {
  await page.request.get('/api/auth/csrf');
  const state = await page.request.storageState();
  const token = decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '');
  const response = await page.request.post('/api/auth/login', {
    form: { email, password: PASSWORD },
    headers: { 'X-XSRF-TOKEN': token },
  });
  expect(response.status(), await response.text()).toBe(200);
}

async function handleOf(page: Page): Promise<string> {
  const response = await page.request.get('/api/me');
  expect(response.status()).toBe(200);
  return ((await response.json()) as { handle: string }).handle;
}

async function write(
  page: Page,
  title: string,
  visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC',
): Promise<{ id: number; path: string }> {
  const id = await createPost(page, { title, contentMd: '본문' });
  const response = await api(
    page,
    'POST',
    `/api/posts/${id}/publish`,
    { title, contentMd: `${title} 본문`, tags: [], visibility, baseVersion: 0 },
    { 'Idempotency-Key': crypto.randomUUID() },
  );
  expect(response.status(), await response.text()).toBe(200);
  const { url } = (await response.json()) as { url: string };
  return { id, path: new URL(url, 'http://localhost').pathname };
}

async function noHorizontalScroll(page: Page): Promise<void> {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth).toBeLessThanOrEqual(size.innerWidth);
}

test.describe('팔로우·피드 (010)', () => {
  test.skip(
    !hasAccount || READER_EMAIL === '' || AUTHOR2_EMAIL === '' || LONELY_EMAIL === '',
    'E2E_EMAIL·E2E_AUTHOR2_EMAIL·E2E_READER_EMAIL·E2E_LONELY_EMAIL·E2E_PASSWORD가 필요합니다',
  );
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('quickstart §3 1~12번', async ({ browser }) => {
    test.setTimeout(180_000);
    const stamp = Date.now().toString(36);

    // 1) B·C 글
    const b = await newPage(browser);
    await login(b.page);
    const bHandle = await handleOf(b.page);
    await write(b.page, `B 공개 하나 ${stamp}`);
    await write(b.page, `B 공개 둘 ${stamp}`);
    await write(b.page, `B 비공개 ${stamp}`, 'PRIVATE');
    const c = await newPage(browser);
    await loginAs(c.page, AUTHOR2_EMAIL);
    const cPost = await write(c.page, `C 공개 ${stamp}`);

    // 2) A로 B 블로그 → [팔로우] → 바로 [팔로잉 ✓], 팔로워 1
    const a = await newPage(browser);
    await loginAs(a.page, READER_EMAIL);
    const aHandle = await handleOf(a.page);
    await a.page.goto(`/@${bHandle}`);
    const counts = a.page.getByTestId('follow-counts');
    await expect(counts).toContainText('팔로워 0');
    const button = a.page.locator('.follow-button');
    await expect(button).toHaveText('팔로우');
    const put = a.page.waitForResponse(
      (r) => r.url().endsWith(`/api/members/${bHandle}/follow`) && r.request().method() === 'PUT',
    );
    await button.click();
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await expect(button).toHaveText(/팔로잉/);
    await expect(counts).toContainText('팔로워 1');
    expect((await put).status()).toBe(200);

    // 3) 마우스를 올리면 [언팔로우] → 확인 창 없이 [팔로우] → 다시 팔로우
    await a.page.mouse.move(0, 0);
    await button.hover();
    await expect(button).toHaveText('언팔로우');
    let dialogs = 0;
    a.page.on('dialog', (d) => {
      dialogs += 1;
      void d.dismiss();
    });
    const del = a.page.waitForResponse(
      (r) =>
        r.url().endsWith(`/api/members/${bHandle}/follow`) && r.request().method() === 'DELETE',
    );
    await button.click();
    await expect(button).toHaveText('팔로우');
    expect((await del).status()).toBe(200);
    await expect(counts).toContainText('팔로워 0');
    const again = a.page.waitForResponse(
      (r) => r.url().endsWith(`/api/members/${bHandle}/follow`) && r.request().method() === 'PUT',
    );
    await button.click();
    expect((await again).status()).toBe(200);
    await expect(counts).toContainText('팔로워 1');
    expect(dialogs).toBe(0);

    // 4) 네트워크가 끊기면 원래대로 + "잠시 후 다시 시도해 주세요"
    await a.page.route('**/api/members/*/follow', (route) => route.abort());
    await a.page.mouse.move(0, 0);
    await button.click();
    await expect(
      a.page.getByRole('status').filter({ hasText: '잠시 후 다시 시도해 주세요' }),
    ).toBeVisible();
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await expect(counts).toContainText('팔로워 1');
    await a.page.unroute('**/api/members/*/follow');

    // 5) C 글 상세 작성자 카드 [팔로우] → 새로 고쳐도 [팔로잉 ✓]
    await a.page.goto(cPost.path);
    const cardButton = a.page.locator('.follow-button');
    await expect(cardButton).toHaveText('팔로우');
    const putC = a.page.waitForResponse(
      (r) => r.url().includes('/follow') && r.request().method() === 'PUT',
    );
    await cardButton.click();
    expect((await putC).status()).toBe(200);
    await a.page.reload();
    await expect(a.page.locator('.follow-button')).toHaveAttribute('aria-pressed', 'true');

    // 6) 머리말 [피드] → B·C 공개 글만 최신순, 비공개 없음
    await a.page.getByRole('banner').getByRole('link', { name: '피드' }).click();
    await expect(a.page).toHaveURL(/\/feed$/);
    const titles = a.page.getByTestId('card-title');
    await expect(titles.first()).toHaveText(`C 공개 ${stamp}`);
    const texts = await titles.allTextContents();
    expect(texts.slice(0, 3)).toEqual([
      `C 공개 ${stamp}`,
      `B 공개 둘 ${stamp}`,
      `B 공개 하나 ${stamp}`,
    ]);
    expect(texts).not.toContain(`B 비공개 ${stamp}`);

    // 7) 글을 열고 뒤로 → 요청 없이 카드 그대로
    const before = texts.length;
    let feedRequests = 0;
    a.page.on('request', (r) => {
      if (new URL(r.url()).pathname === '/api/feed') {
        feedRequests += 1;
      }
    });
    await titles.first().click();
    await expect(a.page).toHaveURL(new RegExp(`${cPost.path}$`));
    await a.page.goBack();
    await expect(titles).toHaveCount(before);
    expect(feedRequests).toBe(0);

    // 8) B 블로그 "팔로워 1" → 목록에 A, A 자신은 버튼 없음
    await a.page.goto(`/@${bHandle}`);
    await a.page.getByRole('link', { name: '팔로워 1' }).click();
    await expect(a.page).toHaveURL(new RegExp(`/@${bHandle}/followers$`));
    const rows = a.page.getByTestId('follow-list-item');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(`@${aHandle}`);
    await expect(rows.first().locator('.follow-button')).toHaveCount(0);

    // 9) 비로그인: 목록은 보이고, [팔로우]는 로그인 안내. /feed는 로그인으로, 머리말에 [피드] 없음
    const anon = await newPage(browser);
    await anon.page.goto(`/@${bHandle}/followers`);
    await expect(anon.page.getByTestId('follow-list-item')).toHaveCount(1);
    await anon.page.locator('.follow-button').click();
    await expect(anon.page.getByRole('alert')).toContainText('로그인이 필요해요');
    await expect(anon.page.getByRole('banner').getByRole('link', { name: '피드' })).toHaveCount(0);
    await anon.page.goto('/feed');
    await expect(anon.page).toHaveURL(/\/login\?returnTo=%2Ffeed$/);
    expect((await anon.page.request.get('/api/feed')).status()).toBe(401);

    // 10) 아무도 팔로우하지 않은 D
    const d = await newPage(browser);
    await loginAs(d.page, LONELY_EMAIL);
    await d.page.goto('/feed');
    await expect(d.page.getByTestId('empty-feed')).toContainText(
      '팔로우한 사람이 없어요. 홈에서 읽고 싶은 블로그를 찾아보세요',
    );
    await expect(d.page.getByTestId('empty-feed').getByRole('link', { name: '홈' })).toBeVisible();

    // 11) 없는 주소는 404(첫 응답부터), 대문자는 301 소문자
    const missing = await anon.page.goto(`/@nobody${stamp}/followers`);
    expect(missing?.status()).toBe(404);
    await expect(anon.page.getByText('볼 수 없는 페이지예요')).toBeVisible();
    const upper = await anon.page.request.get(`/@${bHandle.toUpperCase()}/followers`, {
      maxRedirects: 0,
    });
    expect(upper.status()).toBe(301);
    expect(upper.headers()['location']).toContain(`/@${bHandle}/followers`);

    // 12) 375px: 가로 스크롤 없음, 버튼 44px 이상, 키보드 초점에서도 [언팔로우]
    await a.page.setViewportSize({ width: 375, height: 812 });
    for (const path of ['/feed', `/@${bHandle}/followers`, `/@${bHandle}`]) {
      await a.page.goto(path);
      await a.page.waitForLoadState('networkidle');
      await noHorizontalScroll(a.page);
    }
    const headerButton = a.page.locator('.follow-button');
    const box = await headerButton.boundingBox();
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
    await headerButton.focus();
    await expect(headerButton).toHaveText('언팔로우');
    await a.page.locator('body').focus();

    for (const ctx of [a, b, c, d, anon]) {
      await ctx.context.close();
    }
  });
});
