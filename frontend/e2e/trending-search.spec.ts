import { expect, test, type Browser, type Page } from '@playwright/test';
import { api, createPost, hasAccount, login } from './support';

/**
 * 012 quickstart §3 화면 확인 3·5~13·15 (T041). 작성자 `E2E_EMAIL`, 독자 `E2E_READER_EMAIL`(인증됨), 비밀번호 `E2E_PASSWORD`.
 * 작성자 닉네임·주소는 `E2E_AUTHOR_NICKNAME`·`E2E_AUTHOR_HANDLE`.
 *
 * 트렌딩은 스냅샷이 없을 때의 즉시 계산을 본다 — 서버를 `blog.trending.refresh-on-startup=false`로 띄운다(반응 없는 기동 직후
 * 스냅샷이 10분 동안 "빈 순위"로 남지 않게). 14번(1분 31번 → 429)은 `SearchRateLimitIT`가 맡는다.
 */
const READER_EMAIL = process.env.E2E_READER_EMAIL ?? '';
const PASSWORD = process.env.E2E_PASSWORD ?? '';
const AUTHOR_NICKNAME = process.env.E2E_AUTHOR_NICKNAME ?? '';
const AUTHOR_HANDLE = process.env.E2E_AUTHOR_HANDLE ?? '';

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

async function publishPost(
  page: Page,
  title: string,
  contentMd: string,
  visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC',
) {
  const postId = await createPost(page, { title, contentMd });
  const response = await api(
    page,
    'POST',
    `/api/posts/${postId}/publish`,
    { title, contentMd, tags: ['spring'], visibility, baseVersion: 0 },
    { 'Idempotency-Key': crypto.randomUUID() },
  );
  expect(response.status(), await response.text()).toBe(200);
  return { postId, url: ((await response.json()) as { url: string }).url };
}

async function noHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

async function newPage(browser: Browser) {
  const context = await browser.newContext();
  return { context, page: await context.newPage() };
}

test.describe('트렌딩·검색 (012)', () => {
  test.skip(
    !hasAccount || READER_EMAIL === '' || AUTHOR_HANDLE === '' || AUTHOR_NICKNAME === '',
    'E2E_EMAIL·E2E_READER_EMAIL·E2E_PASSWORD·E2E_AUTHOR_HANDLE·E2E_AUTHOR_NICKNAME가 필요합니다',
  );
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('quickstart §3 3·5~13·15', async ({ browser }) => {
    test.setTimeout(180_000);
    const stamp = Date.now().toString(36);
    const word = `검색${stamp}`;

    // 1) 작성자: 공개 글 둘(하나는 본문에 공격 문자열), 비공개 글 하나
    const author = await newPage(browser);
    await login(author.page);
    const liked = await publishPost(
      author.page,
      `트랜잭션 ${word} 정리`,
      `본문에도 ${word}가 있다`,
    );
    const xss = await publishPost(
      author.page,
      `다른 제목 ${stamp}`,
      `앞 문장 <img src=x onerror=alert(1)> ${word} 뒤 문장`,
    );
    const hidden = await publishPost(author.page, `트랜잭션 ${word} 비공개`, '비공개', 'PRIVATE');

    // 2) 독자가 좋아요 → 트렌딩 후보
    const reader = await newPage(browser);
    await loginAs(reader.page, READER_EMAIL);
    const likeResponse = await api(reader.page, 'PUT', `/api/posts/${liked.postId}/like`, {});
    expect(likeResponse.status(), await likeResponse.text()).toBeLessThan(300);

    const page = reader.page;
    const dialogs: string[] = [];
    page.on('dialog', (dialog) => {
      dialogs.push(dialog.message());
      void dialog.dismiss();
    });

    // 3) 홈 [트렌딩] 탭 — 키보드로 고른다
    await page.goto('/');
    const trendingTab = page.getByRole('tab', { name: '트렌딩' });
    await expect(page.getByRole('tab', { name: '최신' })).toHaveAttribute('aria-selected', 'true');
    await trendingTab.focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/\?tab=trending$/);
    await expect(trendingTab).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('최근 7일 동안 반응이 많은 글 · 10분마다 갱신')).toBeVisible();
    const likedCard = page.getByTestId('post-card').filter({ hasText: `트랜잭션 ${word} 정리` });
    await expect(likedCard).toHaveCount(1);
    await expect(likedCard).not.toContainText(/\d+\s*위/);
    // 글을 열었다 뒤로 → 트렌딩 탭 그대로
    await likedCard.getByRole('link').first().click();
    await expect(page).toHaveURL(new RegExp(`/posts/${liked.postId}$`));
    await page.goBack();
    await expect(page.getByRole('tab', { name: '트렌딩' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(likedCard).toHaveCount(1);

    // 15) 375px 가로 스크롤 없음 (홈 트렌딩)
    await page.setViewportSize({ width: 375, height: 812 });
    await noHorizontalScroll(page);
    await page.setViewportSize({ width: 1280, height: 800 });

    // 5) 머리말 검색창 → /search?q=, 제목 일치가 먼저, 비공개 없음, 강조
    const box = page.getByRole('searchbox', { name: '검색', exact: true });
    await box.fill(word);
    await box.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/search\\?q=${encodeURIComponent(word)}$`));
    const cards = page.getByTestId('post-card');
    await expect(cards).toHaveCount(2);
    await expect(cards.first()).toContainText(`트랜잭션 ${word} 정리`);
    await expect(page.getByText(`트랜잭션 ${word} 비공개`)).toHaveCount(0);
    await expect(cards.first().locator('mark').first()).toHaveText(word);
    // 8) 공격 문자열은 글자로 보이고 알림 창이 뜨지 않는다
    await expect(cards.nth(1)).toContainText('<img src=x onerror=alert(1)>');
    expect(dialogs).toEqual([]);
    // 정렬 [최신순] — 키보드
    await page.getByRole('button', { name: '최신순' }).focus();
    await page.keyboard.press('Space');
    await expect(page).toHaveURL(/sort=latest/);
    await expect(cards.first()).toContainText(`다른 제목 ${stamp}`);
    await page.setViewportSize({ width: 375, height: 812 });
    await noHorizontalScroll(page);
    await page.setViewportSize({ width: 1280, height: 800 });

    // 6) 2글자 단어 안내 + 결과 없음
    await box.fill('롬복');
    await box.press('Enter');
    await expect(page.getByText('두 글자 단어는 제목·태그에서만 찾았어요')).toBeVisible();

    // 7) 남는 단어가 없으면 요청 없이 안내
    let searchRequests = 0;
    page.on('request', (request) => {
      if (new URL(request.url()).pathname.startsWith('/api/search/')) {
        searchRequests += 1;
      }
    });
    await box.fill('a');
    await box.press('Enter');
    await expect(page.getByRole('alert')).toHaveText('두 글자 이상 입력해 주세요');
    expect(searchRequests).toBe(0);

    // 9) [사람] 탭
    await page.goto(`/search?q=${encodeURIComponent(AUTHOR_NICKNAME)}`);
    await page.getByRole('tab', { name: '사람' }).click();
    const person = page.getByTestId('person-item').filter({ hasText: `@${AUTHOR_HANDLE}` });
    await expect(person).toHaveCount(1);
    await page.setViewportSize({ width: 375, height: 812 });
    await noHorizontalScroll(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await person.getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/@${AUTHOR_HANDLE}$`));

    // 11) 이 블로그에서 검색
    const blogBox = page.getByRole('searchbox', { name: '이 블로그에서 검색' });
    await blogBox.fill(word);
    await blogBox.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/@${AUTHOR_HANDLE}\\?q=`));
    await expect(page.getByTestId('post-card')).toHaveCount(2);

    // 10) #spring → 태그 페이지, # spring → 보통 검색
    await box.fill('#spring');
    await box.press('Enter');
    await expect(page).toHaveURL(/\/tags\/spring$/);
    await box.fill('# spring');
    await box.press('Enter');
    await expect(page).toHaveURL(/\/search\?q=%23%20spring$/);

    // 12) 첫 응답 noindex
    for (const path of [`/search?q=${word}`, `/@${AUTHOR_HANDLE}?q=${word}`]) {
      const html = await (await page.request.get(path)).text();
      expect(html, path).toMatch(/<meta name="robots" content="noindex/);
    }

    // 13) sitemap: 공개 글 주소만
    const sitemap = await (await page.request.get('/sitemap.xml')).text();
    expect(sitemap).toContain(`/posts/${liked.postId}</loc>`);
    expect(sitemap).toContain(`/posts/${xss.postId}</loc>`);
    expect(sitemap).not.toContain(`/posts/${hidden.postId}</loc>`);

    expect(dialogs).toEqual([]);
    await author.context.close();
    await reader.context.close();
  });
});
