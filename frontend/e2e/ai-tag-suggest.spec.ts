import { expect, test, type Page, type Route } from '@playwright/test';
import { createPost, hasAccount, login } from './support';

/**
 * 013 AI 태그 추천 화면 (T054). 추천·상태·동의 API는 `page.route`로 흉내 낸다 — 실제 AI·키 없이 돈다.
 * 동의 창 → [동의하고 추천받기] → 칩 2개 → 하나 클릭 → 태그 입력에 들어감 → 발행 창 닫기 → 다시 열어도 그대로 → 발행하면 저장된 태그에 있음.
 * 375px에서 가로 스크롤 없음.
 */
const LONG_BODY =
  'Spring Boot에서 JPA 지연 로딩으로 목록을 읽으면 글마다 댓글 쿼리가 한 번씩 더 나간다. ' +
  'fetch join이나 batch size 설정으로 쿼리 수를 줄일 수 있다. Hibernate 영속성 컨텍스트와 트랜잭션 범위도 함께 본다.';

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

interface Mock {
  agreed: boolean;
  suggestCalls: number;
  agreeBodies: unknown[];
}

async function mockAi(page: Page): Promise<Mock> {
  const mock: Mock = { agreed: false, suggestCalls: 0, agreeBodies: [] };
  const json = (route: Route, status: number, body: unknown) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

  await page.route(/\/api\/posts\/\d+\/tag-suggestions\/status$/, (route) =>
    json(route, 200, {
      available: true,
      consentRequired: !mock.agreed,
      consentVersion: '2026-10-08',
      provider: 'GEMINI',
      remainingToday: 20,
    }),
  );
  await page.route(/\/api\/posts\/\d+\/tag-suggestions$/, (route) => {
    mock.suggestCalls += 1;
    if (!mock.agreed) {
      return json(route, 409, {
        code: 'AI_CONSENT_REQUIRED',
        message: 'AI 태그 추천을 쓰려면 먼저 동의해 주세요',
        errors: [],
        details: { version: '2026-10-08' },
      });
    }
    return json(route, 200, {
      tags: ['spring', 'hibernate'],
      provider: 'GEMINI',
      cached: false,
      truncated: false,
      remainingToday: 19,
    });
  });
  await page.route(/\/api\/me\/agreements\/ai$/, (route) => {
    if (route.request().method() === 'PUT') {
      mock.agreeBodies.push(route.request().postDataJSON());
      mock.agreed = true;
      return json(route, 200, {
        agreed: true,
        version: '2026-10-08',
        currentVersion: '2026-10-08',
        agreedAt: new Date().toISOString(),
      });
    }
    return route.continue();
  });
  return mock;
}

test.describe('AI 태그 추천 (013)', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('동의 → 추천 칩 → 태그에 붙이기 → 닫았다 열어도 그대로 → 발행하면 저장', async ({
    page,
  }) => {
    test.setTimeout(90_000);
    const stamp = Date.now().toString(36);
    await login(page);
    const postId = await createPost(page, { title: `AI 추천 E2E ${stamp}`, contentMd: LONG_BODY });
    const mock = await mockAi(page);

    await page.goto(`/write/${postId}`);
    await page.getByRole('button', { name: '글 등록' }).click();
    const publishDialog = page.getByRole('dialog', { name: '발행 설정' });
    await expect(publishDialog).toBeVisible();

    // 1) 동의 창 — 처음 초점은 [동의하고 추천받기], 동의 전에는 추천 요청이 가지 않는다
    await publishDialog.getByRole('button', { name: 'AI 태그 추천' }).click();
    const consent = page.getByRole('dialog', { name: 'AI 태그 추천을 쓰기 전에 확인해 주세요' });
    await expect(consent).toBeVisible();
    await expect(consent.getByRole('button', { name: '동의하고 추천받기' })).toBeFocused();
    expect(mock.suggestCalls).toBe(0);

    // 2) 동의 → PUT {version} → 원래 요청 다시 → 칩 2개
    await consent.getByRole('button', { name: '동의하고 추천받기' }).click();
    await expect(consent).toBeHidden();
    const suggested = publishDialog.getByRole('list', { name: 'AI 추천 태그' });
    await expect(suggested.getByRole('button')).toHaveCount(2);
    expect(mock.agreeBodies).toEqual([{ version: '2026-10-08' }]);
    expect(mock.suggestCalls).toBe(1);
    await expect(publishDialog.getByText('AI 제안이에요')).toBeVisible();

    // 375px: 칩이 보이는 발행 창
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '발행 창 + 추천 칩');
    await page.setViewportSize({ width: 1280, height: 800 });

    // 3) 칩 하나 클릭 → 붙인 태그에 들어가고 추천 칩에서 빠진다
    await suggested.getByRole('button', { name: 'spring 태그 붙이기' }).click();
    const chips = publishDialog.getByRole('list', { name: '붙인 태그' }).getByRole('listitem');
    await expect(chips).toHaveCount(1);
    await expect(chips.first()).toHaveAttribute('data-tag', 'spring');
    await expect(suggested.getByRole('button')).toHaveCount(1);

    // 4) 발행 창 닫기 → 다시 열어도 그대로
    await publishDialog.getByRole('button', { name: '닫기' }).click();
    await expect(publishDialog).toBeHidden();
    await page.getByRole('button', { name: '글 등록' }).click();
    await expect(
      publishDialog.getByRole('list', { name: '붙인 태그' }).getByRole('listitem'),
    ).toHaveCount(1);

    // 5) 발행 → 저장된 태그
    await publishDialog.getByRole('button', { name: '발행', exact: true }).click();
    await expect(page).not.toHaveURL(new RegExp(`/write/${postId}$`), { timeout: 15_000 });
    const copy = await page.request.get(`/api/posts/${postId}/working-copy`);
    expect(copy.status()).toBe(200);
    expect(((await copy.json()) as { tags: string[] }).tags).toEqual(['spring']);
  });
});
