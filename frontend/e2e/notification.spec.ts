import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { createPost, hasAccount, login } from './support';

/**
 * 011 quickstart §3 화면 확인 1~12번 (T058).
 *
 * 회원 셋을 환경 변수로 받는다(모두 이메일 인증, 비밀번호 `E2E_PASSWORD`):
 * - A 받는 사람 `E2E_EMAIL` — 공개 글을 쓴다
 * - B `E2E_READER_EMAIL` — 댓글·좋아요·팔로우
 * - C `E2E_AUTHOR2_EMAIL` — 좋아요·팔로우(새 글 알림을 받는다)
 *
 * 8번(다른 탭에 있는 동안 확인 요청 없음)은 화면 테스트 `useUnreadCount.test.ts`가 가짜 타이머로 확인한다.
 * 9번의 20개씩 [더 보기]는 `NotificationsPage.test.tsx`가 확인하고, 여기서는 `size=20` 요청만 본다.
 */
const READER_EMAIL = process.env.E2E_READER_EMAIL ?? '';
const AUTHOR2_EMAIL = process.env.E2E_AUTHOR2_EMAIL ?? '';
const PASSWORD = process.env.E2E_PASSWORD ?? '';

interface Me {
  handle: string;
  nickname: string;
}

interface Item {
  id: number;
  type: string;
  read: boolean;
  post: { title?: string } | null;
}

async function newPage(browser: Browser): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext();
  return { context, page: await context.newPage() };
}

async function token(page: Page): Promise<string> {
  await page.request.get('/api/auth/csrf');
  const state = await page.request.storageState();
  return decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '');
}

async function loginAs(page: Page, email: string): Promise<void> {
  const response = await page.request.post('/api/auth/login', {
    form: { email, password: PASSWORD },
    headers: { 'X-XSRF-TOKEN': await token(page) },
  });
  expect(response.status(), await response.text()).toBe(200);
}

async function call(
  page: Page,
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  data?: unknown,
  headers: Record<string, string> = {},
) {
  const response = await page.request.fetch(path, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': await token(page), ...headers },
  });
  expect(response.status(), `${method} ${path}: ${await response.text()}`).toBeLessThan(300);
  return response;
}

async function meOf(page: Page): Promise<Me> {
  const response = await page.request.get('/api/me');
  expect(response.status()).toBe(200);
  return (await response.json()) as Me;
}

async function write(page: Page, title: string): Promise<{ id: number; path: string }> {
  const id = await createPost(page, { title, contentMd: '본문' });
  const response = await call(
    page,
    'POST',
    `/api/posts/${id}/publish`,
    { title, contentMd: `${title} 본문`, tags: [], visibility: 'PUBLIC', baseVersion: 0 },
    { 'Idempotency-Key': crypto.randomUUID() },
  );
  const { url } = (await response.json()) as { url: string };
  return { id, path: new URL(url, 'http://localhost').pathname };
}

async function notifications(page: Page): Promise<Item[]> {
  const response = await page.request.get('/api/notifications?size=20');
  expect(response.status()).toBe(200);
  return ((await response.json()) as { items: Item[] }).items;
}

/** 알림 저장은 커밋 뒤 비동기라 잠깐 기다린다. */
async function waitForItems(page: Page, check: (items: Item[]) => boolean): Promise<void> {
  await expect.poll(async () => check(await notifications(page)), { timeout: 15_000 }).toBe(true);
}

function bell(page: Page) {
  return page.locator('.notification-bell-button');
}

async function openBell(page: Page) {
  await page.reload();
  await bell(page).click();
  return page.locator('.notification-panel');
}

async function noHorizontalScroll(page: Page): Promise<void> {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth).toBeLessThanOrEqual(size.innerWidth);
}

async function atLeast44(page: Page, selector: string): Promise<void> {
  const box = await page.locator(selector).first().boundingBox();
  expect(box).not.toBeNull();
  expect(box!.width).toBeGreaterThanOrEqual(44);
  expect(box!.height).toBeGreaterThanOrEqual(44);
}

test.describe('알림 (011)', () => {
  test.skip(
    !hasAccount || READER_EMAIL === '' || AUTHOR2_EMAIL === '',
    'E2E_EMAIL·E2E_READER_EMAIL·E2E_AUTHOR2_EMAIL·E2E_PASSWORD가 필요합니다',
  );
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('quickstart §3 1~12번', async ({ browser }) => {
    test.setTimeout(240_000);
    const stamp = Date.now().toString(36);

    // 1) A·B·C. A가 공개 글
    const a = await newPage(browser);
    await login(a.page);
    const aMe = await meOf(a.page);
    const b = await newPage(browser);
    await loginAs(b.page, READER_EMAIL);
    const bMe = await meOf(b.page);
    const c = await newPage(browser);
    await loginAs(c.page, AUTHOR2_EMAIL);
    const cMe = await meOf(c.page);
    const title = `알림 글 ${stamp}`;
    const post = await write(a.page, title);
    // 같은 E2E 회원을 쓰는 다른 spec(댓글·좋아요·팔로우)이 먼저 돌면 A에게 안 읽은 알림이 남아 있다 — 모두 읽음으로 시작한다
    expect((await call(a.page, 'POST', '/api/notifications/read-all', {})).status()).toBeLessThan(
      300,
    );
    await a.page.goto('/');
    await expect(bell(a.page)).toHaveAccessibleName('알림');

    // 2) B 댓글 → A 종 배지 1, 이름 "안 읽은 알림 1개"
    const comment = (await (
      await call(b.page, 'POST', `/api/posts/${post.id}/comments`, { content: `댓글 ${stamp}` })
    ).json()) as { id: number };
    await waitForItems(a.page, (items) => items.some((i) => i.type === 'COMMENT'));
    await a.page.reload();
    await expect(bell(a.page)).toHaveAccessibleName('안 읽은 알림 1개');
    await expect(a.page.getByTestId('notification-badge')).toHaveText('1');

    // 3) 펼침 → ● + 굵은 문장, 누르면 그 댓글로 이동해 강조, 배지 사라짐
    const panel = await openBell(a.page);
    const first = panel.getByTestId('notification-item').first();
    await expect(first).toContainText(
      `${bMe.nickname}님이 「${title}」에 댓글을 남겼어요: "댓글 ${stamp}"`,
    );
    await expect(first.locator('.notification-dot')).toHaveText('●');
    await expect(first.locator('strong.notification-text')).toBeVisible();
    await first.locator('.notification-item-main').click();
    await expect(a.page).toHaveURL(new RegExp(`\\?comment=${comment.id}#comment-${comment.id}$`));
    await expect(a.page.locator(`#comment-${comment.id}`)).toBeVisible();
    await expect(a.page.getByTestId('notification-badge')).toHaveCount(0);

    // 4) B·C 좋아요 → 묶음 하나 "C님 외 1명이". C 취소 → "B님이"
    await call(b.page, 'PUT', `/api/posts/${post.id}/like`);
    await waitForItems(a.page, (items) => items.some((i) => i.type === 'LIKE'));
    await call(c.page, 'PUT', `/api/posts/${post.id}/like`);
    let likePanel = a.page.locator('.notification-panel');
    await expect(async () => {
      likePanel = await openBell(a.page);
      await expect(likePanel).toContainText(`${cMe.nickname}님 외 1명이 「${title}」을 좋아해요`, {
        timeout: 2_000,
      });
    }).toPass({ timeout: 15_000 });
    await expect(likePanel.getByText(/을 좋아해요/)).toHaveCount(1);
    await call(c.page, 'DELETE', `/api/posts/${post.id}/like`);
    await expect(async () => {
      likePanel = await openBell(a.page);
      await expect(likePanel).toContainText(`${bMe.nickname}님이 「${title}」을 좋아해요`, {
        timeout: 2_000,
      });
    }).toPass({ timeout: 15_000 });

    // 5) B 팔로우 → 언팔로우 → 팔로우 → 새 팔로워 알림 하나, 누르면 /@a/followers
    const followPath = `/api/members/${encodeURIComponent(aMe.handle)}/follow`;
    await call(b.page, 'PUT', followPath);
    await waitForItems(a.page, (items) => items.some((i) => i.type === 'FOLLOW'));
    await call(b.page, 'DELETE', followPath);
    await call(b.page, 'PUT', followPath);
    await waitForItems(a.page, (items) => items.filter((i) => i.type === 'FOLLOW').length === 1);
    const followPanel = await openBell(a.page);
    const followItem = followPanel
      .getByTestId('notification-item')
      .filter({ hasText: '회원님을 팔로우해요' });
    await expect(followItem).toHaveCount(1);
    await followItem.locator('.notification-item-main').click();
    await expect(a.page).toHaveURL(new RegExp(`/@${aMe.handle}/followers$`));

    // 6) C가 A 팔로우 → A 새 글 → C에게 "A님이 새 글을 올렸어요"
    await call(c.page, 'PUT', followPath);
    const newTitle = `새 글 ${stamp}`;
    const newPost = await write(a.page, newTitle);
    await waitForItems(c.page, (items) => items.some((i) => i.type === 'NEW_POST'));
    await c.page.goto('/');
    let cPanel = await openBell(c.page);
    await expect(cPanel).toContainText(`${aMe.nickname}님이 새 글을 올렸어요: 「${newTitle}」`);

    // 7) 비공개로 → C에게 "볼 수 없는 글이에요", 누르면 이동 없이 읽음만. 다시 공개하면 제목이 돌아온다
    await call(a.page, 'PUT', `/api/posts/${newPost.id}/visibility`, { visibility: 'PRIVATE' });
    cPanel = await openBell(c.page);
    const hiddenItem = cPanel
      .getByTestId('notification-item')
      .filter({ hasText: '볼 수 없는 글이에요' });
    await expect(hiddenItem).toHaveCount(1);
    await expect(cPanel).not.toContainText(newTitle);
    const before = c.page.url();
    await hiddenItem.locator('.notification-item-main').click();
    await expect(hiddenItem.locator('.notification-dot')).toHaveCount(0);
    expect(c.page.url()).toBe(before);
    await call(a.page, 'PUT', `/api/posts/${newPost.id}/visibility`, { visibility: 'PUBLIC' });
    cPanel = await openBell(c.page);
    await expect(cPanel).toContainText(`「${newTitle}」`);

    // 펼침 목록 키보드: Tab으로 종에 가서 Enter로 열고 Esc로 닫으면 초점이 종으로
    await c.page.reload();
    await bell(c.page).focus();
    await c.page.keyboard.press('Enter');
    await expect(c.page.locator('.notification-panel')).toBeVisible();
    await c.page.keyboard.press('Tab');
    await c.page.keyboard.press('Escape');
    await expect(c.page.locator('.notification-panel')).toHaveCount(0);
    await expect(bell(c.page)).toBeFocused();

    // 9) [모두 읽음] → 배지 사라짐. [모든 알림 보기] → /notifications (20개씩), [×]로 삭제
    await call(b.page, 'POST', `/api/posts/${post.id}/comments`, { content: `둘째 댓글 ${stamp}` });
    await waitForItems(a.page, (items) => items.filter((i) => i.type === 'COMMENT').length === 2);
    const readPanel = await openBell(a.page);
    await expect(a.page.getByTestId('notification-badge')).toBeVisible();
    await readPanel.getByRole('button', { name: '모두 읽음' }).click();
    await expect(a.page.getByTestId('notification-badge')).toHaveCount(0);
    const pageRequest = a.page.waitForRequest((r) =>
      r.url().includes('/api/notifications?size=20'),
    );
    await readPanel.getByRole('link', { name: '모든 알림 보기' }).click();
    await expect(a.page).toHaveURL(/\/notifications$/);
    await pageRequest;
    const rows = a.page.getByTestId('notification-item');
    await expect(rows.first()).toBeVisible();
    const count = await rows.count();
    expect(count).toBeGreaterThan(1);
    await rows.first().getByRole('button', { name: '알림 삭제' }).click();
    await expect(rows).toHaveCount(count - 1);

    // 10) 설정에서 좋아요 끄기 → B가 다른 글에 좋아요 → 새 알림 없음
    await a.page.goto('/settings');
    await expect(a.page.getByText('운영 알림(신고 결과·숨김)은 끌 수 없어요')).toBeVisible();
    const likeSwitch = a.page.getByRole('switch', { name: '좋아요' });
    const saved = a.page.waitForResponse(
      (r) => r.url().endsWith('/api/me/notification-settings') && r.request().method() === 'PUT',
    );
    await likeSwitch.click();
    expect((await saved).status()).toBe(200);
    await expect(likeSwitch).toHaveAttribute('aria-checked', 'false');
    const likesBefore = (await notifications(a.page)).filter((i) => i.type === 'LIKE').length;
    await call(b.page, 'PUT', `/api/posts/${newPost.id}/like`);
    await a.page.waitForTimeout(3_000);
    const likesAfter = await notifications(a.page);
    expect(likesAfter.filter((i) => i.type === 'LIKE').length).toBe(likesBefore);
    expect(likesAfter.some((i) => i.type === 'LIKE' && i.post?.title === title)).toBe(true);

    // 11) 네트워크가 끊기면 "알림을 불러오지 못했어요 [다시 시도]", 배지는 그대로
    await call(b.page, 'POST', `/api/posts/${post.id}/comments`, { content: `셋째 댓글 ${stamp}` });
    await waitForItems(a.page, (items) => items.some((i) => !i.read));
    await a.page.goto('/');
    await expect(a.page.getByTestId('notification-badge')).toHaveText('1');
    await a.page.route('**/api/notifications?size=10', (route) => route.abort());
    await bell(a.page).click();
    await expect(a.page.locator('.notification-panel')).toContainText('알림을 불러오지 못했어요');
    await expect(
      a.page.locator('.notification-panel').getByRole('button', { name: '다시 시도' }),
    ).toBeVisible();
    await expect(a.page.getByTestId('notification-badge')).toHaveText('1');
    await a.page.unroute('**/api/notifications?size=10');

    // 12) 375px: 펼침 목록·알림 화면 가로 스크롤 없음, 종·[×] 44px 이상
    await a.page.setViewportSize({ width: 375, height: 812 });
    await a.page.goto('/');
    await bell(a.page).click();
    await expect(a.page.locator('.notification-panel')).toBeVisible();
    await noHorizontalScroll(a.page);
    await atLeast44(a.page, '.notification-bell-button');
    await a.page.goto('/notifications');
    await expect(a.page.getByTestId('notification-item').first()).toBeVisible();
    await noHorizontalScroll(a.page);
    await atLeast44(a.page, '.notification-delete');

    await a.context.close();
    await b.context.close();
    await c.context.close();
  });
});
