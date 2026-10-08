import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';
import { E2E_EMAIL, E2E_PASSWORD, createPost, login, publish } from './support';

/**
 * 007 quickstart §3 화면 확인 (T055·T056). 이메일 인증을 마친 회원 둘이 필요하다: A = `E2E_EMAIL`/`E2E_PASSWORD`(글 작성자),
 * B = `E2E_EMAIL2`/`E2E_PASSWORD2`. 25개 댓글을 한 번에 만드므로 앱은 `BLOG_COMMENT_RATELIMIT_CREATE_LIMIT`를 넉넉히 주고
 * 띄운다(기본 1분 10개). 인증 전 회원 안내(§3-2)는 화면 시험 `CommentForm.test.tsx`가 맡는다.
 */
const EMAIL2 = process.env.E2E_EMAIL2 ?? '';
const PASSWORD2 = process.env.E2E_PASSWORD2 ?? '';
const ready = E2E_EMAIL !== '' && E2E_PASSWORD !== '' && EMAIL2 !== '' && PASSWORD2 !== '';

interface Me {
  handle: string;
  nickname: string;
}

async function csrf(request: APIRequestContext): Promise<string> {
  await request.get('/api/auth/csrf');
  const cookie = (await request.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN');
  if (!cookie) {
    throw new Error('XSRF-TOKEN 쿠키가 없습니다');
  }
  return decodeURIComponent(cookie.value);
}

async function loginAs(page: Page, email: string, password: string) {
  const token = await csrf(page.request);
  const response = await page.request.post('/api/auth/login', {
    form: { email, password },
    headers: { 'X-XSRF-TOKEN': token },
  });
  expect(response.status(), await response.text()).toBe(200);
}

async function me(page: Page): Promise<Me> {
  const response = await page.request.get('/api/me');
  expect(response.status()).toBe(200);
  return (await response.json()) as Me;
}

async function call(page: Page, method: 'POST' | 'PUT', path: string, data: unknown) {
  const token = await csrf(page.request);
  return page.request.fetch(path, { method, data, headers: { 'X-XSRF-TOKEN': token } });
}

async function writeComment(
  page: Page,
  postId: number,
  content: string,
  replyToCommentId: number | null = null,
): Promise<number> {
  const response = await call(page, 'POST', `/api/posts/${postId}/comments`, {
    content,
    replyToCommentId,
  });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: number }).id;
}

async function memberB(browser: Browser, baseURL: string | undefined) {
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  await loginAs(page, EMAIL2, PASSWORD2);
  return { context, page };
}

function comment(page: Page, id: number) {
  return page.locator(`#comment-${id}`);
}

function mainOf(page: Page, id: number) {
  return comment(page, id).locator('[data-testid="comment-main"]').first();
}

async function newestId(page: Page, postId: number): Promise<number> {
  const response = await page.request.get(`/api/posts/${postId}/comments`);
  const body = (await response.json()) as {
    items: { id: number; replies: { id: number }[] }[];
  };
  const ids = body.items.flatMap((item) => [item.id, ...item.replies.map((r) => r.id)]);
  return Math.max(...ids);
}

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

test.describe('댓글 (007 quickstart §3)', () => {
  test.skip(!ready, 'E2E_EMAIL·E2E_PASSWORD·E2E_EMAIL2·E2E_PASSWORD2가 필요합니다');

  test('비회원 안내·동시 요청 → 쓰기·답글·@대상 → 비공개·다시 공개 → 삭제 자리', async ({
    page,
    browser,
    baseURL,
  }) => {
    const dialogs: string[] = [];
    page.on('dialog', (dialog) => {
      dialogs.push(dialog.message());
      void dialog.dismiss();
    });
    const stamp = Date.now().toString(36);
    await login(page);
    const a = await me(page);
    const postId = await createPost(page, {});
    await publish(page, postId, `E2E 댓글 ${stamp}`, '댓글 시험 본문');
    const path = `/@${a.handle}/posts/${postId}`;

    // §3-1 비회원: 안내 문구, 상세와 댓글 요청이 동시에 나간다(댓글 요청이 상세 응답보다 먼저 시작)
    const guestContext = await browser.newContext({ baseURL });
    const guest = await guestContext.newPage();
    const started: Record<string, number> = {};
    const finished: Record<string, number> = {};
    guest.on('request', (request) => {
      started[new URL(request.url()).pathname] ??= Date.now();
    });
    guest.on('requestfinished', (request) => {
      finished[new URL(request.url()).pathname] ??= Date.now();
    });
    await guest.goto(path);
    await expect(guest.getByText('로그인하고 댓글을 남겨 보세요')).toBeVisible();
    await expect(
      guest.getByTestId('comment-section').getByRole('link', { name: '로그인', exact: true }),
    ).toBeVisible();
    await expect(guest.getByText('첫 댓글을 남겨 보세요')).toBeVisible();
    const detailApi = `/api/posts/${postId}`;
    const commentsApi = `/api/posts/${postId}/comments`;
    expect(started[commentsApi], '댓글 요청이 나감').toBeDefined();
    expect(started[commentsApi]).toBeLessThanOrEqual(finished[detailApi]);
    await guestContext.close();

    // §3-3 A가 스크립트·Markdown·빈 줄이 든 댓글 등록 → 글자 그대로, 줄바꿈 유지, 머리말 "댓글 1"
    await page.goto(path);
    const raw = '<script>alert(1)</script>\n\n\n\n**굵게**';
    await page.getByLabel('댓글 입력').fill(raw);
    await page.getByRole('button', { name: '등록' }).click();
    await expect(page.getByRole('heading', { name: '댓글 1' })).toBeVisible();
    const rootId = await newestId(page, postId);
    const content = comment(page, rootId).getByTestId('comment-content');
    await expect(content).toBeVisible();
    expect(await content.textContent()).toBe('<script>alert(1)</script>\n\n**굵게**');
    await expect(content.locator('strong, script')).toHaveCount(0);
    await expect(page.getByLabel('댓글 입력')).toHaveValue('');

    // §3-4 B가 A 댓글에 답글 → A가 B의 답글에 답글 → "@B닉네임에게", 같은 최상위 아래
    const { context: bContext, page: b } = await memberB(browser, baseURL);
    const bMe = await me(b);
    await b.goto(path);
    await b.getByRole('button', { name: `${a.nickname}님 댓글에 답글` }).click();
    await b.getByLabel('답글 입력').fill(`B의 답글 ${stamp}`);
    await b.locator('.comment-reply-form').getByRole('button', { name: '등록' }).click();
    await expect(b.getByText(`B의 답글 ${stamp}`)).toBeVisible();
    // 화면에 보인 직후 목록 API가 아직 새 답글을 돌려주지 않을 때가 있어(전체 실행에서 한 번 관찰) 늘어날 때까지 기다린다
    await expect.poll(() => newestId(b, postId)).toBeGreaterThan(rootId);
    const bReplyId = await newestId(b, postId);

    await page.reload();
    await page.getByRole('button', { name: `${bMe.nickname}님 댓글에 답글` }).click();
    await page.getByLabel('답글 입력').fill(`A의 답글 ${stamp}`);
    await page.locator('.comment-reply-form').getByRole('button', { name: '등록' }).click();
    await expect(page.getByText(`@${bMe.nickname}에게`)).toBeVisible();
    await expect.poll(() => newestId(page, postId)).toBeGreaterThan(bReplyId);
    const aReplyId = await newestId(page, postId);
    await expect(page.getByTestId(`replies-${rootId}`).getByTestId('comment-item')).toHaveCount(2);
    await expect(page.getByRole('heading', { name: '댓글 3' })).toBeVisible();

    // §3-7 글 작성자(A)가 보는 남의 댓글에는 [수정]·[삭제]가 없다. [신고]는 014가 머지되어 남의 댓글에만 있다
    // (FR-022 "014 전까지 숨김"이 끝남 — 014 T026). A 자신의 글·댓글에는 [신고]가 없다
    const others = mainOf(page, bReplyId);
    await expect(others.getByRole('button', { name: '수정' })).toHaveCount(0);
    await expect(others.getByRole('button', { name: '삭제' })).toHaveCount(0);
    await expect(others.getByRole('button', { name: '신고' })).toHaveCount(1);
    await expect(page.getByRole('button', { name: /신고/ })).toHaveCount(1);

    // §3-8 비공개 → B에게 글·댓글 모두 찾을 수 없음 → 다시 공개 → 댓글 돌아옴
    expect(
      (
        await call(page, 'PUT', `/api/posts/${postId}/visibility`, { visibility: 'PRIVATE' })
      ).status(),
    ).toBe(200);
    expect((await b.request.get(`/api/posts/${postId}/comments`)).status()).toBe(404);
    await b.reload();
    await expect(b.getByText('볼 수 없는 페이지예요')).toBeVisible();
    expect(
      (
        await call(page, 'PUT', `/api/posts/${postId}/visibility`, { visibility: 'PUBLIC' })
      ).status(),
    ).toBe(200);
    await b.reload();
    await expect(b.getByText(`B의 답글 ${stamp}`)).toBeVisible();

    // §3-6 A가 답글 있는 자기 최상위 삭제 → "삭제된 댓글이에요" 자리, 답글은 그대로
    await page.reload();
    await mainOf(page, rootId).getByRole('button', { name: '삭제' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '삭제' }).click();
    await expect(comment(page, rootId).getByText('삭제된 댓글이에요').first()).toBeVisible();
    await expect(comment(page, bReplyId)).toBeVisible();
    await expect(page.getByRole('heading', { name: '댓글 2' })).toBeVisible();

    // 남은 답글을 각자 지우면 자리도 사라진다
    await mainOf(page, aReplyId).getByRole('button', { name: '삭제' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '삭제' }).click();
    await expect(comment(page, aReplyId)).toHaveCount(0);
    await b.reload();
    await mainOf(b, bReplyId).getByRole('button', { name: '삭제' }).click();
    await b.getByRole('dialog').getByRole('button', { name: '삭제' }).click();
    await expect(comment(b, rootId)).toHaveCount(0);
    await expect(b.getByText('첫 댓글을 남겨 보세요')).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: '댓글 0' })).toBeVisible();

    expect(dialogs, '알림창 0회').toEqual([]);
    await bContext.close();
  });

  test('25개 → 더 보기·내 댓글 한 번만, 알림 링크 바로 가기, 375px 긴 URL', async ({ page }) => {
    const stamp = Date.now().toString(36);
    await login(page);
    const a = await me(page);
    const postId = await createPost(page, {});
    await publish(page, postId, `E2E 댓글 목록 ${stamp}`, '목록 시험 본문');
    const ids: number[] = [];
    for (let i = 1; i <= 25; i += 1) {
      ids.push(await writeComment(page, postId, `댓글 ${i} ${stamp}`));
    }
    const target = ids[21];
    const replies: number[] = [];
    for (let i = 1; i <= 5; i += 1) {
      replies.push(await writeComment(page, postId, `답글 ${i} ${stamp}`, target));
    }
    const path = `/@${a.handle}/posts/${postId}`;

    // §3-5 20개 + [댓글 더 보기] → 내 새 댓글은 끝에 바로 → [댓글 더 보기] 뒤에도 한 번만
    await page.goto(path);
    const roots = page.locator('.comment-section > .comment-list > li');
    await expect(roots).toHaveCount(20);
    await page.getByLabel('댓글 입력').fill(`새 댓글 ${stamp}`);
    await page.getByRole('button', { name: '등록' }).click();
    await expect(roots).toHaveCount(21);
    await expect(roots.last()).toContainText(`새 댓글 ${stamp}`);
    await page.getByRole('button', { name: '댓글 더 보기' }).click();
    await expect(roots).toHaveCount(26);
    await expect(page.getByText(`새 댓글 ${stamp}`)).toHaveCount(1);

    // §3-9 알림 링크: 22번째 최상위의 5번째 답글 → 펼쳐지고 강조, 위에 [이전 댓글 보기]
    const focus = replies[4];
    await page.goto(`${path}?comment=${focus}#comment-${focus}`);
    const focused = page.locator(`#comment-${focus}`);
    await expect(focused).toBeVisible();
    await expect(focused).toHaveClass(/comment-focus/);
    await expect(focused).toBeInViewport();
    await expect(page.getByRole('button', { name: '이전 댓글 보기' })).toBeVisible();
    await page.getByRole('button', { name: '이전 댓글 보기' }).click();
    await expect(page.locator(`#comment-${ids[1]}`)).toBeVisible();
    // 그 앞(첫 댓글)은 한 번 더
    await page.getByRole('button', { name: '이전 댓글 보기' }).click();
    await expect(page.locator(`#comment-${ids[0]}`)).toBeVisible();
    await expect(page.getByRole('button', { name: '이전 댓글 보기' })).toHaveCount(0);
    await expect(focused).not.toHaveClass(/comment-focus/, { timeout: 5000 });

    // §3-10 375px: 긴 URL·답글 들여쓰기에서 가로 스크롤 없음 (mobile 프로젝트에서 의미가 있다)
    await writeComment(page, postId, `https://example.com/${'a'.repeat(200)}`, ids[0]);
    await page.goto(path);
    await expect(page.locator('.comment-section > .comment-list > li')).toHaveCount(20);
    await expectNoHorizontalScroll(page, '댓글 영역');
    // 접근성: [답글] 버튼 이름, 수정 칸 Esc 취소
    const first = mainOf(page, ids[0]);
    await expect(first.getByRole('button', { name: `${a.nickname}님 댓글에 답글` })).toBeVisible();
    await first.getByRole('button', { name: '수정' }).click();
    await expect(page.getByLabel('댓글 수정')).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByLabel('댓글 수정')).toHaveCount(0);
  });
});
