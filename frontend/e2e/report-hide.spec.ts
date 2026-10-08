import { expect, test, type Browser, type Page } from '@playwright/test';
import { createPost, hasAccount, login, publish } from './support';

/**
 * 014 quickstart §3 종단 흐름 (T063): 회원 B 신고 → 관리자 K 처리 화면에서 숨기기 → B에게 글 404 → 작성자 A 상세 숨김 안내 →
 * K 숨김 해제 → B에게 다시 보임. 375px 가로 스크롤 없음.
 *
 * 회원 셋을 환경 변수로 받는다: 작성자 `E2E_EMAIL`, 신고자 `E2E_READER_EMAIL`, 관리자 `E2E_ADMIN_EMAIL`. 비밀번호는 모두
 * `E2E_PASSWORD`.
 */
const READER_EMAIL = process.env.E2E_READER_EMAIL ?? '';
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? '';
const PASSWORD = process.env.E2E_PASSWORD ?? '';

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

async function pageFor(browser: Browser, email: string | null, width = 1280): Promise<Page> {
  const context = await browser.newContext({ viewport: { width, height: 812 } });
  const page = await context.newPage();
  if (email) {
    await loginAs(page, email);
  }
  return page;
}

async function noHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

test.describe('신고·숨김 (014)', () => {
  test.skip(
    !hasAccount || READER_EMAIL === '' || ADMIN_EMAIL === '',
    'E2E_EMAIL·E2E_READER_EMAIL·E2E_ADMIN_EMAIL·E2E_PASSWORD가 필요합니다',
  );
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('신고 → 숨기기 → 404 → 작성자 안내 → 해제 → 다시 보임', async ({ browser }) => {
    test.setTimeout(120_000);
    const stamp = Date.now().toString(36);
    const title = `신고 E2E ${stamp}`;

    // 작성자 A: 글 발행
    const author = await pageFor(browser, null);
    await login(author);
    const postId = await createPost(author, { title, contentMd: '본문' });
    const { url } = await publish(author, postId, title, '신고될 본문');
    const postPath = new URL(url, 'http://localhost').pathname;

    // 회원 B: 375px에서 [신고] → 사유 → [신고하기]
    const reader = await pageFor(browser, READER_EMAIL, 375);
    await reader.goto(postPath);
    await expect(reader.getByRole('heading', { level: 1, name: title })).toBeVisible();
    await reader.getByTestId('reaction-bar').getByRole('button', { name: '신고' }).click();
    const dialog = reader.getByRole('dialog', { name: '글 신고하기' });
    await expect(dialog).toBeVisible();
    await noHorizontalScroll(reader);
    await dialog.getByRole('radio', { name: '스팸·광고' }).check();
    await dialog.getByRole('button', { name: '신고하기' }).click();
    await expect(reader.getByText('신고가 접수됐어요. 검토 후 처리할게요')).toBeVisible();
    await expect(dialog).toHaveCount(0);

    // 관리자 K: 대기 목록 → 상세 → 숨기기
    const admin = await pageFor(browser, ADMIN_EMAIL, 375);
    await admin.goto('/admin/reports');
    const link = admin.getByRole('link', { name: title });
    await expect(link).toBeVisible();
    await noHorizontalScroll(admin);
    await link.click();
    await expect(admin.getByTestId('current-state')).toHaveText('현재: 공개');
    await expect(admin.getByTestId('snapshot-content')).toHaveText('신고될 본문');
    await noHorizontalScroll(admin);
    await admin.getByRole('radio', { name: '스팸·광고' }).check();
    await admin.getByRole('button', { name: '숨기기' }).click();
    await admin.getByRole('dialog').getByRole('button', { name: '숨기기' }).click();
    await expect(admin.getByTestId('case-status')).toHaveText('숨김');

    // B: 글 404
    const gone = await reader.request.get(`/api/posts/${postId}`);
    expect(gone.status()).toBe(404);
    await reader.goto(postPath);
    await expect(reader.getByRole('heading', { level: 1, name: title })).toHaveCount(0);

    // A: 상세에 숨김 안내와 사유
    await author.goto(postPath);
    await expect(author.getByTestId('hidden-notice')).toHaveText(
      '운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요',
    );

    // K: 처리됨 탭에서 [숨김 해제]
    await admin.goto('/admin/reports?tab=handled');
    const row = admin.getByTestId('case-item').filter({ hasText: title });
    await row.getByRole('button', { name: '숨김 해제' }).click();
    await admin.getByRole('dialog').getByRole('button', { name: '숨김 해제' }).click();
    await expect(row.getByRole('button', { name: '숨김 해제' })).toHaveCount(0);

    // B: 다시 보임
    await reader.goto(postPath);
    await expect(reader.getByRole('heading', { level: 1, name: title })).toBeVisible();
  });
});
