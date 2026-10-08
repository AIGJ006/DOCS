import { expect, test, type Page } from '@playwright/test';
import { createPost, hasAccount, login, publish } from './support';

/**
 * 005 T074 (FR-014·015, SC-006): 375px·640px·1024px에서 홈·블로그·글 상세에 가로 스크롤이 없고, 같은 줄 카드 높이가 같으며
 * (이 환경에는 사진 저장소가 없어 모두 썸네일 없는 카드), 카드 링크와 작성자 링크로 Tab 이동이 된다.
 */
const WIDTHS = [375, 640, 1024];
const LONG_WORD = 'https://example.com/' + 'long-path-segment-'.repeat(10);

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

/** 카드들을 줄(top 좌표)로 묶어 같은 줄의 높이가 같은지 본다. 몇 줄·몇 개씩인지 돌려준다. */
async function expectEqualRowHeights(page: Page, where: string): Promise<number[]> {
  const cards = page.getByTestId('post-card');
  await expect(cards.first()).toBeVisible();
  const boxes = await cards.evaluateAll((elements) =>
    elements.map((element) => {
      const rect = element.getBoundingClientRect();
      return { top: Math.round(rect.top), height: Math.round(rect.height) };
    }),
  );
  const rows = new Map<number, number[]>();
  for (const box of boxes) {
    rows.set(box.top, [...(rows.get(box.top) ?? []), box.height]);
  }
  for (const [top, heights] of rows) {
    const spread = Math.max(...heights) - Math.min(...heights);
    expect(spread, `${where}: top=${top} 줄 카드 높이 ${heights.join(',')}`).toBeLessThanOrEqual(1);
  }
  return [...rows.values()].map((heights) => heights.length);
}

/** Tab을 눌러 조건에 맞는 요소에 초점이 오는지 본다. */
async function tabUntil(page: Page, selector: string, limit = 40): Promise<boolean> {
  await page.locator('body').click({ position: { x: 1, y: 1 } });
  for (let i = 0; i < limit; i++) {
    await page.keyboard.press('Tab');
    const hit = await page.evaluate(
      (css) => document.activeElement?.matches(css) ?? false,
      selector,
    );
    if (hit) {
      return true;
    }
  }
  return false;
}

test.describe('읽기 화면은 375px~1024px에서 넘치지 않는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('홈·블로그·상세 가로 스크롤 없음, 같은 줄 카드 높이 같음, Tab 이동', async ({ page }) => {
    test.setTimeout(120_000);
    await login(page);
    // 제목·요약 길이를 다르게 해 카드 내용 높이가 서로 다르게 한다
    const titles = [
      '짧은 제목',
      '조금 더 긴 제목으로 두 줄이 될 수도 있는 글 제목입니다 정말로 길어요',
      `띄어쓰기없는긴낱말${'가'.repeat(60)}`,
      '요약이 긴 글',
    ];
    let detailUrl = '';
    for (const [index, title] of titles.entries()) {
      const postId = await createPost(page);
      const body =
        index === 3
          ? `${'긴 요약 문장입니다. '.repeat(30)}\n\n${LONG_WORD}`
          : `본문 ${index}\n\n${LONG_WORD}\n\n\`\`\`\nconst x = "${'x'.repeat(200)}";\n\`\`\`\n\n| a | b | c |\n|---|---|---|\n| ${'긴칸'.repeat(30)} | 1 | 2 |\n`;
      const published = await publish(page, postId, title, body);
      if (index === 1) {
        detailUrl = published.url;
      }
    }
    const blogUrl = detailUrl.replace(/\/posts\/\d+$/, '');

    for (const width of WIDTHS) {
      await page.setViewportSize({ width, height: 900 });

      await page.goto('/');
      const homeRows = await expectEqualRowHeights(page, `홈 ${width}px`);
      await expectNoHorizontalScroll(page, `홈 ${width}px`);
      if (width === 375) {
        expect(Math.max(...homeRows), '375px는 한 줄에 카드 1개').toBe(1);
      }
      if (width === 1024) {
        expect(Math.max(...homeRows), '1024px는 한 줄에 카드 여러 개').toBeGreaterThan(1);
      }

      await page.goto(blogUrl);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expectEqualRowHeights(page, `블로그 ${width}px`);
      await expectNoHorizontalScroll(page, `블로그 ${width}px`);

      await page.goto(detailUrl);
      await expect(page.getByTestId('post-content')).toBeVisible();
      await expectNoHorizontalScroll(page, `상세 ${width}px`);
    }

    // Tab 이동: 홈의 카드(글) 링크와 작성자 링크
    await page.setViewportSize({ width: 375, height: 900 });
    await page.goto('/');
    await expect(page.getByTestId('post-card').first()).toBeVisible();
    expect(await tabUntil(page, '[data-testid="post-card"] a[href*="/posts/"]'), '카드 링크').toBe(
      true,
    );
    expect(await tabUntil(page, '[data-testid="author-chip"] a'), '작성자 링크').toBe(true);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/@[a-z0-9_]+$/);
  });
});
