import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { api, createPost, hasAccount, login, publish } from './support';

/**
 * 009 quickstart §3 화면 확인 1~9번 (T041·T042).
 *
 * 회원 셋을 환경 변수로 받는다: 작성자 `E2E_EMAIL`, 독자 `E2E_READER_EMAIL`(인증됨), 인증 전 회원 `E2E_UNVERIFIED_EMAIL`.
 * 비밀번호는 모두 `E2E_PASSWORD`. 조회수 반영 주기는 `E2E_VIEW_FLUSH_MS`(서버 `blog.view.flush-interval`과 같게, 기본 60초).
 *
 * 헤드리스 Chromium의 User-Agent에는 봇 단어 `headlesschrome`이 들어 있어 조회가 세지지 않는다 — 일반 Chrome 값으로 바꾼다.
 * 10번(Redis 정지)은 서버 통합 시험 `ViewRedisOutageIT`가 맡는다.
 */
const READER_EMAIL = process.env.E2E_READER_EMAIL ?? '';
const UNVERIFIED_EMAIL = process.env.E2E_UNVERIFIED_EMAIL ?? '';
const PASSWORD = process.env.E2E_PASSWORD ?? '';
const FLUSH_MS = Number(process.env.E2E_VIEW_FLUSH_MS ?? '60000');
const USER_AGENT =
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36';

async function newPage(browser: Browser): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({ userAgent: USER_AGENT });
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

function likeButton(page: Page) {
  return page.getByRole('button', { name: /^좋아요/ });
}

function pathOf(url: string): string {
  return new URL(url, 'http://localhost').pathname;
}

async function viewCount(page: Page, postId: number): Promise<number> {
  const response = await page.request.get(`/api/posts/${postId}`);
  expect(response.status()).toBe(200);
  return ((await response.json()) as { viewCount: number }).viewCount;
}

test.describe('좋아요·조회수 (009)', () => {
  test.skip(
    !hasAccount || READER_EMAIL === '' || UNVERIFIED_EMAIL === '',
    'E2E_EMAIL·E2E_READER_EMAIL·E2E_UNVERIFIED_EMAIL·E2E_PASSWORD가 필요합니다',
  );
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('좋아요 1~7번과 375px·접근성', async ({ browser }) => {
    test.setTimeout(120_000);
    const stamp = Date.now().toString(36);

    // 작성자: 글 발행, 6) 내 글은 버튼 없이 ♥ + 수
    const author = await newPage(browser);
    await login(author.page);
    const postId = await createPost(author.page, {
      title: `좋아요 E2E ${stamp}`,
      contentMd: '본문',
    });
    const { url } = await publish(author.page, postId, `좋아요 E2E ${stamp}`, '좋아요 본문');
    const postPath = pathOf(url);
    await author.page.goto(postPath);
    await expect(author.page.getByTestId('like-count')).toHaveText(/♥\s*0/);
    await expect(likeButton(author.page)).toHaveCount(0);

    // 독자
    const reader = await newPage(browser);
    await loginAs(reader.page, READER_EMAIL);
    const likeRequests: string[] = [];
    reader.page.on('request', (r) => {
      if (pathOf(r.url()) === `/api/posts/${postId}/like`) {
        likeRequests.push(r.method());
      }
    });
    await reader.page.goto(postPath);
    const button = likeButton(reader.page);
    await expect(button).toHaveAttribute('aria-pressed', 'false');
    await expect(button).toHaveAccessibleName('좋아요 (0)');

    // 1) 누르면 바로 ♥ + 1, PUT 하나
    const put = reader.page.waitForResponse(
      (r) => r.request().method() === 'PUT' && pathOf(r.url()) === `/api/posts/${postId}/like`,
    );
    await button.click();
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await expect(button).toHaveAccessibleName('좋아요 취소 (1)');
    expect((await put).status()).toBe(200);
    expect(likeRequests).toEqual(['PUT']);

    // 2) 빠른 5번 연타(홀수) → 요청 하나, 최종 상태는 반대(취소)
    likeRequests.length = 0;
    for (let i = 0; i < 5; i++) {
      await button.click({ delay: 10 });
    }
    await expect(button).toHaveAttribute('aria-pressed', 'false');
    await reader.page.waitForTimeout(1200);
    expect(likeRequests).toEqual(['DELETE']);
    await expect(button).toHaveAccessibleName('좋아요 (0)');

    // 3) 네트워크 끊김 → 되돌림 + 안내
    await reader.context.setOffline(true);
    await button.click();
    await expect(
      reader.page.getByRole('status').filter({ hasText: '좋아요를 반영하지 못했어요' }),
    ).toBeVisible();
    await expect(button).toHaveAttribute('aria-pressed', 'false');
    await reader.context.setOffline(false);

    // 7) 키보드: Tab으로 가서 Space → 눌림, 이름 "좋아요 취소 (1)", 포커스 표시
    await button.focus();
    await reader.page.keyboard.press('Shift+Tab');
    await reader.page.keyboard.press('Tab');
    await expect(button).toBeFocused();
    const outline = await button.evaluate((el) => getComputedStyle(el).outlineStyle);
    expect(outline, '포커스 표시').not.toBe('none');
    await reader.page.keyboard.press('Space');
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await expect(button).toHaveAccessibleName('좋아요 취소 (1)');

    // T042: 375px — 누를 자리 44px 이상, 가로 스크롤 없음, 안내는 role="status"
    await reader.page.setViewportSize({ width: 375, height: 812 });
    const box = await button.boundingBox();
    expect(box?.width ?? 0).toBeGreaterThanOrEqual(44);
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
    const size = await reader.page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
    }));
    expect(size.scrollWidth).toBeLessThanOrEqual(size.innerWidth);
    await expect(reader.page.locator('.like-area [role="status"]')).toHaveCount(1);

    // 4) 비로그인 → 로그인 안내 → 로그인 → 같은 글로 돌아오고 자동으로 눌리지 않음
    const anonymous = await newPage(browser);
    await anonymous.page.goto(postPath);
    let anonymousLikes = 0;
    anonymous.page.on('request', (r) => {
      if (pathOf(r.url()) === `/api/posts/${postId}/like`) {
        anonymousLikes++;
      }
    });
    await likeButton(anonymous.page).click();
    const notice = anonymous.page
      .getByRole('status')
      .filter({ hasText: '로그인하고 좋아요를 눌러 보세요' });
    await expect(notice).toBeVisible();
    const loginLink = notice.getByRole('link', { name: '로그인' });
    await expect(loginLink).toHaveAttribute(
      'href',
      `/login?returnTo=${encodeURIComponent(postPath)}`,
    );
    await loginLink.click();
    await anonymous.page.getByLabel('이메일').fill(READER_EMAIL);
    await anonymous.page.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await anonymous.page.locator('button[type="submit"]').click();
    await expect(anonymous.page).toHaveURL((u) => u.pathname === postPath);
    await expect(likeButton(anonymous.page)).toHaveAttribute('aria-pressed', 'true');
    await expect(likeButton(anonymous.page)).toHaveAccessibleName('좋아요 취소 (1)');
    expect(anonymousLikes, '돌아온 뒤 자동으로 누르지 않는다').toBe(0);

    // 5) 인증 전 회원 → 안내만, 요청 없음
    const fresh = await newPage(browser);
    await loginAs(fresh.page, UNVERIFIED_EMAIL);
    let freshLikes = 0;
    fresh.page.on('request', (r) => {
      if (pathOf(r.url()) === `/api/posts/${postId}/like`) {
        freshLikes++;
      }
    });
    await fresh.page.goto(postPath);
    await likeButton(fresh.page).click();
    await expect(
      fresh.page.getByRole('status').filter({ hasText: '이메일 인증 후 누를 수 있어요' }),
    ).toBeVisible();
    expect(freshLikes).toBe(0);

    for (const c of [author, reader, anonymous, fresh]) {
      await c.context.close();
    }
  });

  test('조회수 8~9번: vid 쿠키·새로고침 1번만·숨긴 탭은 보일 때 한 번', async ({ browser }) => {
    test.setTimeout(120_000 + FLUSH_MS * 2);
    const stamp = Date.now().toString(36);
    const author = await newPage(browser);
    await login(author.page);
    const postId = await createPost(author.page, { title: `조회 E2E ${stamp}`, contentMd: '본문' });
    const { url } = await publish(author.page, postId, `조회 E2E ${stamp}`, '조회 본문');
    const postPath = pathOf(url);
    const before = await viewCount(author.page, postId);

    // 8) 쿠키 없는 비로그인 → vid 발급 → 1초 뒤 POST …/views 204 → 새로고침 5번 → 반영 뒤 +1만
    const visitor = await newPage(browser);
    const issued: string[] = [];
    visitor.page.on('response', async (r) => {
      const cookie = await r.headerValue('set-cookie');
      if (cookie && /(^|\n)vid=/.test(cookie)) {
        issued.push(pathOf(r.url()));
      }
    });
    const firstView = visitor.page.waitForResponse(
      (r) => r.request().method() === 'POST' && pathOf(r.url()) === `/api/posts/${postId}/views`,
    );
    await visitor.page.goto(postPath);
    expect((await firstView).status()).toBe(204);
    expect(issued.length, 'vid Set-Cookie').toBeGreaterThanOrEqual(1);
    const vid = (await visitor.context.cookies()).find((c) => c.name === 'vid');
    expect(vid?.httpOnly).toBe(true);
    expect(vid?.sameSite).toBe('Lax');
    for (let i = 0; i < 5; i++) {
      const again = visitor.page.waitForResponse(
        (r) => r.request().method() === 'POST' && pathOf(r.url()) === `/api/posts/${postId}/views`,
      );
      await visitor.page.reload();
      expect((await again).status()).toBe(204);
    }
    expect(issued.length, '쿠키가 있으면 다시 주지 않는다').toBe(1);
    await expect
      .poll(() => viewCount(author.page, postId), {
        timeout: FLUSH_MS * 2 + 5000,
        intervals: [1000],
      })
      .toBe(before + 1);
    await author.page.waitForTimeout(FLUSH_MS + 1000);
    expect(await viewCount(author.page, postId), '새로고침은 더 세지 않는다').toBe(before + 1);

    // 9) 숨긴 탭: 요청 없음 → 보이면 1초 뒤 한 번
    const hidden = await browser.newContext({ userAgent: USER_AGENT });
    await hidden.addInitScript(() => {
      const w = window as unknown as { __vis?: string };
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        get: () => w.__vis ?? 'hidden',
      });
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => (w.__vis ?? 'hidden') !== 'visible',
      });
    });
    const tab = await hidden.newPage();
    const posts: number[] = [];
    tab.on('request', (r) => {
      if (r.method() === 'POST' && pathOf(r.url()) === `/api/posts/${postId}/views`) {
        posts.push(Date.now());
      }
    });
    await tab.goto(postPath);
    await tab.waitForTimeout(2500);
    expect(posts, '숨긴 동안 요청 없음').toHaveLength(0);
    const shownAt = Date.now();
    await tab.evaluate(() => {
      (window as unknown as { __vis?: string }).__vis = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await expect.poll(() => posts.length, { timeout: 5000 }).toBe(1);
    expect(posts[0] - shownAt).toBeGreaterThanOrEqual(900);
    await tab.waitForTimeout(1500);
    expect(posts).toHaveLength(1);

    // 작성자는 세지 않는다(요청도 없음)
    let authorViews = 0;
    author.page.on('request', (r) => {
      if (r.method() === 'POST' && pathOf(r.url()) === `/api/posts/${postId}/views`) {
        authorViews++;
      }
    });
    await author.page.goto(postPath);
    await author.page.waitForTimeout(1500);
    expect(authorViews).toBe(0);

    await api(author.page, 'PUT', `/api/posts/${postId}/like`, {}).then((r) =>
      expect(r.status()).toBe(400),
    );

    for (const c of [author.context, visitor.context, hidden]) {
      await c.close();
    }
  });
});
