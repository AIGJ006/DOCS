import { expect, test, type Page } from '@playwright/test';
import { createPost, hasAccount, login, publish } from './support';

/**
 * 006 quickstart §4 화면 확인 (T076): 비회원 이동, 탭 글 수, [삭제] 확인창 → 그 줄만 빠짐, 휴지통 줄 문구와 [복구] 알림,
 * 다른 곳에서 이미 처리된 글의 줄 오류 + 목록 다시 불러오기, 375px 가로 스크롤 없음.
 */
interface Counts {
  drafts: number;
  published: number;
  trash: number;
}

async function counts(page: Page): Promise<Counts> {
  const response = await page.request.get('/api/me/posts?tab=drafts');
  expect(response.status()).toBe(200);
  return ((await response.json()) as { counts: Counts }).counts;
}

/** 다른 탭에서 한 것처럼 API로 바로 처리한다. */
async function viaApi(page: Page, method: 'DELETE' | 'POST', path: string) {
  await page.request.get('/api/auth/csrf');
  const state = await page.request.storageState();
  const token = decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '');
  const response = await page.request.fetch(path, { method, headers: { 'X-XSRF-TOKEN': token } });
  expect(response.status(), await response.text()).toBe(200);
}

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

function row(page: Page, title: string) {
  return page.locator('li.manage-row').filter({ hasText: title });
}

test.describe('내 글 관리 (006)', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('비회원은 로그인 화면으로 가고 돌아올 주소가 붙는다', async ({ page }) => {
    await page.goto('/manage/posts?tab=trash');
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fmanage%2Fposts%3Ftab%3Dtrash$/);
  });

  test('탭 글 수, 삭제 → 휴지통 → 복구, 이미 처리된 글, 375px', async ({ page }) => {
    const stamp = Date.now().toString(36);
    await login(page);
    const draftTitle = `E2E 임시글 ${stamp}`;
    const otherDraftTitle = `E2E 다른 탭에서 지울 글 ${stamp}`;
    const publishedTitle = `E2E 발행 글 ${stamp}`;
    await createPost(page, { title: draftTitle, contentMd: '본문' });
    const otherDraft = await createPost(page, { title: otherDraftTitle, contentMd: '본문' });
    const published = await createPost(page, {});
    await publish(page, published, publishedTitle, '발행 본문');
    const before = await counts(page);

    // 2) 탭 머리 글 수, 기본 탭 [임시글]
    await page.goto('/manage/posts');
    await expect(page.getByRole('tab', { name: `임시글 ${before.drafts}` })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByRole('tab', { name: `발행 글 ${before.published}` })).toBeVisible();
    await expect(page.getByRole('tab', { name: `휴지통 ${before.trash}` })).toBeVisible();
    await expect(row(page, draftTitle)).toBeVisible();

    // 3) 발행 글 [삭제] → 확인창 → 그 줄만 빠지고 휴지통 +1
    await page.getByRole('tab', { name: `발행 글 ${before.published}` }).click();
    await expect(page).toHaveURL(/tab=published/);
    await row(page, publishedTitle).getByRole('button', { name: '삭제' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요');
    await expect(dialog.getByRole('button', { name: '휴지통으로' })).toBeFocused();
    await dialog.getByRole('button', { name: '휴지통으로' }).click();
    await expect(row(page, publishedTitle)).toHaveCount(0);
    await expect(page.getByRole('tab', { name: `휴지통 ${before.trash + 1}` })).toBeVisible();
    await expect(page.getByRole('tab', { name: `발행 글 ${before.published - 1}` })).toBeVisible();

    // 4) 휴지통 줄 문구 → [복구](확인창 없음) → 알림
    await page.getByRole('tab', { name: `휴지통 ${before.trash + 1}` }).click();
    await expect(page.getByText('휴지통의 글은 30일 뒤 자동으로 완전히 삭제돼요')).toBeVisible();
    const trashed = row(page, publishedTitle);
    await expect(trashed).toContainText('(발행 글이었음)');
    await expect(trashed).toContainText(/삭제 \d+월 \d+일 · (\d+일 뒤|곧) 완전 삭제/);
    await trashed.getByRole('button', { name: '복구' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    const toast = page.getByRole('status');
    await expect(toast).toContainText('복구했어요');
    await expect(toast.getByRole('link', { name: '발행 글 탭에서 보기' })).toBeVisible();
    await expect(row(page, publishedTitle)).toHaveCount(0);

    // 5) 다른 탭에서 이미 처리한 글 — [삭제]는 FR-025(이미 휴지통이면 200)라 오류 없이 줄만 빠지고,
    //    [복구]는 404라 이유를 보이고 목록을 다시 불러온다
    await page.getByRole('tab', { name: /^임시글/ }).click();
    await expect(row(page, otherDraftTitle)).toBeVisible();
    await viaApi(page, 'DELETE', `/api/posts/${otherDraft}`);
    await row(page, otherDraftTitle).getByRole('button', { name: '삭제' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '휴지통으로' }).click();
    await expect(row(page, otherDraftTitle)).toHaveCount(0);

    await page.goto('/manage/posts?tab=trash');
    await expect(row(page, otherDraftTitle)).toBeVisible();
    await viaApi(page, 'POST', `/api/posts/${otherDraft}/restore`);
    await row(page, otherDraftTitle).getByRole('button', { name: '복구' }).click();
    await expect(page.getByRole('alert').first()).toContainText(
      '이미 처리된 글이에요. 목록을 다시 불러왔어요',
    );
    await expect(row(page, otherDraftTitle)).toHaveCount(0);
    await expect(page.locator('main')).not.toContainText('볼 수 없는 페이지예요');
    await viaApi(page, 'DELETE', `/api/posts/${otherDraft}`);

    // 6) 375px에서 세 탭 모두 가로 스크롤 없음
    await page.setViewportSize({ width: 375, height: 812 });
    for (const tab of ['drafts', 'published', 'trash']) {
      await page.goto(`/manage/posts?tab=${tab}`);
      await expect(page.getByRole('tab', { selected: true })).toBeVisible();
      await expect(page.locator('li.manage-row').first()).toBeVisible();
      await expectNoHorizontalScroll(page, `375px ${tab}`);
    }
  });
});
