import { expect, test, type Page } from '@playwright/test';
import { api, createPost, hasAccount, login } from './support';

/**
 * 008 quickstart §3 화면 확인 (T064~T067): 발행 칩(정규화·중복 강조·형식 오류·Alt+방향키 안내) → 글 상세 태그 → 태그 페이지 →
 * 301·404 → 비공개 전용 태그 빈 화면 → `/tags` → 블로그 태그 줄·필터, 한글 조합 중 자동완성 요청 없음, 375px 가로 스크롤 없음.
 * 회원 하나(E2E_EMAIL)로 돈다 — §3-6의 "남의 비공개 태그"는 서버 통합 시험(TagSuggestIT)이 맡는다.
 */
async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

async function publishWithTags(
  page: Page,
  postId: number,
  title: string,
  tags: string[],
  visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC',
) {
  const response = await api(
    page,
    'POST',
    `/api/posts/${postId}/publish`,
    { title, contentMd: '태그 본문', tags, visibility, baseVersion: 0 },
    { 'Idempotency-Key': crypto.randomUUID() },
  );
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as { url: string };
}

async function myHandle(page: Page): Promise<string> {
  const response = await page.request.get('/api/me');
  expect(response.status()).toBe(200);
  return ((await response.json()) as { handle: string }).handle;
}

test.describe('태그 (008)', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('발행 칩 → 상세 태그 → 태그 페이지 → 301·404 → 빈 태그 → /tags → 블로그 필터', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const stamp = Date.now().toString(36);
    await login(page);
    const postId = await createPost(page, { title: `태그 E2E ${stamp}`, contentMd: '본문' });
    await page.goto(`/write/${postId}`);
    await page.getByRole('button', { name: '발행하기' }).click();
    const dialog = page.getByRole('dialog', { name: '발행 설정' });
    const input = dialog.getByRole('combobox', { name: '태그 입력' });

    // 1) 정규화 칩과 중복 강조
    await input.fill('Spring Boot');
    await input.press('Enter');
    const chips = dialog.getByRole('list', { name: '붙인 태그' }).getByRole('listitem');
    await expect(chips).toHaveCount(1);
    await expect(chips.first()).toHaveAttribute('data-tag', 'spring-boot');
    await input.fill('#SPRING  BOOT');
    await input.press('Enter');
    await expect(chips).toHaveCount(1);
    await expect(chips.first()).toHaveAttribute('data-flash', 'true');

    // 2) 형식 오류: 칩 없음 + 글자 문구(색만으로 구분하지 않음), 입력 유지
    await input.fill(`${String.fromCodePoint(0x1f525)}hot`);
    await input.press('Enter');
    await expect(dialog.getByText('쓸 수 없는 글자가 있어요')).toBeVisible();
    await expect(input).toHaveValue(`${String.fromCodePoint(0x1f525)}hot`);
    await expect(chips).toHaveCount(1);

    // 3) 허용 문자 태그, Alt+← 순서 바꾸기와 aria-live 안내, "6 / 10"
    const unique = `e2e-${stamp}`;
    for (const raw of ['C#', 'C++', 'Node.JS', '.NET', unique]) {
      await input.fill(raw);
      await input.press('Enter');
    }
    await expect(dialog.getByText('6 / 10')).toBeVisible();
    await chips.nth(2).focus();
    await page.keyboard.press('Alt+ArrowLeft');
    await expect(dialog.getByRole('status').filter({ hasText: '번째로 옮겼어요' })).toHaveText(
      'c++ 태그를 2번째로 옮겼어요',
    );
    const order = await chips.evaluateAll((els) => els.map((el) => el.getAttribute('data-tag')));
    expect(order).toEqual(['spring-boot', 'c++', 'c#', 'node.js', '.net', unique]);

    // 375px: 발행 창 칩
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '발행 창 칩');
    await page.setViewportSize({ width: 1280, height: 800 });

    // 4) 발행 → 상세 태그(입력 순서) → #c# → /tags/c%23
    await dialog.getByRole('button', { name: '발행', exact: true }).click();
    await expect(page).toHaveURL(/\/posts\/\d+$/, { timeout: 15_000 });
    const tagLinks = page.getByTestId('tag');
    await expect(tagLinks).toHaveText([
      '#spring-boot',
      '#c++',
      '#c#',
      '#node.js',
      '#.net',
      `#${unique}`,
    ]);
    await tagLinks.filter({ hasText: /^#c#$/ }).click();
    await expect(page).toHaveURL(/\/tags\/c%23$/);
    await expect(page.getByRole('heading', { name: '#c#' })).toBeVisible();
    await page.goto(`/tags/${unique}`);
    await expect(page.getByText('공개 글 1', { exact: true })).toBeVisible();
    await expect(page.getByRole('article')).toHaveCount(1);
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '태그 페이지');
    await page.setViewportSize({ width: 1280, height: 800 });

    // 5) 301과 404
    const moved = await page.request.get('/tags/Spring%20Boot', { maxRedirects: 0 });
    expect(moved.status()).toBe(301);
    expect(moved.headers()['location']).toBe('/tags/spring-boot');
    await page.goto('/tags/Spring%20Boot');
    await expect(page).toHaveURL(/\/tags\/spring-boot$/);
    const missing = await page.goto('/tags/%F0%9F%94%A5');
    expect(missing?.status()).toBe(404);
    await expect(page.getByText('볼 수 없는 페이지예요')).toBeVisible();

    // 6) 내 비공개 글 태그는 내 자동완성에 "내 태그"로 보인다
    const secretTag = `이직준비${stamp.replace(/[^0-9]/g, '').slice(0, 4)}`;
    const privatePost = await createPost(page, { title: `비공개 ${stamp}` });
    await publishWithTags(page, privatePost, `비공개 ${stamp}`, [secretTag], 'PRIVATE');
    const suggest = await page.request.get(`/api/tags/suggest?q=${encodeURIComponent(secretTag)}`);
    expect(await suggest.json()).toEqual([{ name: secretTag, postCount: 0, mine: true }]);

    // 7) 비로그인: 비공개 전용 태그와 아무도 안 쓴 태그는 같은 빈 화면
    const anonymous = await page.context().browser()!.newContext();
    const anon = await anonymous.newPage();
    for (const name of [secretTag, `아무도안쓴${stamp.replace(/[^0-9]/g, '').slice(0, 4)}`]) {
      await anon.goto(new URL(`/tags/${encodeURIComponent(name)}`, page.url()).href);
      await expect(anon.getByText('아직 이 태그로 공개된 글이 없어요')).toBeVisible();
      await expect(anon.getByText('공개 글 0', { exact: true })).toBeVisible();
    }

    // 8) /tags: 공개 글 수 순, 비공개 전용 태그는 없다
    await anon.goto(new URL('/tags', page.url()).href);
    const index = anon.getByRole('list', { name: '태그 목록' });
    await expect(index.getByRole('link', { name: new RegExp(`^#${unique} `) })).toBeVisible();
    await expect(index.getByRole('link', { name: new RegExp(`^#${secretTag}`) })).toHaveCount(0);
    await anon.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(anon, '/tags');
    await anonymous.close();

    // 9) 블로그 태그 줄 → 필터 → ?tag=JPA는 301
    const handle = await myHandle(page);
    await page.goto(`/@${handle}`);
    const strip = page.getByRole('list', { name: '블로그 태그' });
    await expect(strip).toBeVisible();
    await page.goto(`/@${handle}?tag=${unique}`);
    const filter = page.getByRole('status', { name: '태그 필터' });
    await expect(filter).toContainText(`#${unique} 글 1개`);
    await expect(page.getByTestId('post-card')).toHaveCount(1);
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '블로그 태그 줄');
    await page.setViewportSize({ width: 1280, height: 800 });
    await filter.getByRole('link', { name: '필터 해제' }).click();
    await expect(page).toHaveURL(new RegExp(`/@${handle}$`));
    const upper = await page.request.get(`/@${handle}?tag=${unique.toUpperCase()}`, {
      maxRedirects: 0,
    });
    expect(upper.status()).toBe(301);
    expect(upper.headers()['location']).toBe(`/@${handle}?tag=${unique}`);
  });

  test('한글 조합 중에는 자동완성 요청이 없고 조합이 끝나면 0.3초 뒤 한 번 나간다', async ({
    page,
  }) => {
    await login(page);
    const postId = await createPost(page, { title: '조합 확인', contentMd: '본문' });
    await page.goto(`/write/${postId}`);
    await page.getByRole('button', { name: '발행하기' }).click();
    const input = page
      .getByRole('dialog', { name: '발행 설정' })
      .getByRole('combobox', { name: '태그 입력' });
    const requests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/tags/suggest')) {
        requests.push(request.url());
      }
    });

    await input.focus();
    await input.dispatchEvent('compositionstart');
    await input.pressSequentially('스프링', { delay: 150 });
    await page.waitForTimeout(800);
    expect(requests).toHaveLength(0);

    await input.dispatchEvent('compositionend');
    await expect.poll(() => requests.length, { timeout: 3000 }).toBe(1);
    expect(decodeURIComponent(requests[0])).toContain('q=스프링');
    await page.waitForTimeout(600);
    expect(requests).toHaveLength(1);
  });
});
