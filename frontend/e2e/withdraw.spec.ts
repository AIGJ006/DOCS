import { execFileSync } from 'node:child_process';
import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';

/**
 * 015 quickstart §3 화면 확인 (T060): 탈퇴 안내 숫자·버튼 규칙 → 신청 → 완료 화면 → 비회원에게 블로그 404 → 유예 중 로그인은
 * 복구 화면만 → [로그아웃] 뒤에도 유예 → [복구하기] → 홈 + 환영 알림, 5번 실패 잠금, 기한 지남 화면, 375px.
 *
 * 공용 E2E 회원(E2E_EMAIL)을 지우지 않도록 이 시험은 매번 새 회원을 가입시켜 쓴다. 이메일 인증·기한 조작은 DB가 필요하므로
 * `E2E_PG_CONTAINER`(psql을 실행할 Docker 컨테이너 ID)가 있을 때만 한다 — 없으면 그 부분을 건너뛴다. 9번(정리 작업)은 통합
 * 테스트(WithdrawPurgeJobIT)로, 11번(소셜 가입)은 가짜 OAuth 제공자가 필요해 통합 테스트(WithdrawalApiIT)로 대신한다. 10번(익명
 * 처리 뒤 재가입)도 정리 작업이 필요해 RejoinAfterPurgeIT로 대신한다.
 */
const PG = process.env.E2E_PG_CONTAINER ?? '';
const PASSWORD = 'Wd-E2e-2026!q';

function sql(statement: string): string {
  return execFileSync(
    'docker',
    ['exec', PG, 'psql', '-U', 'blog', '-d', 'blog', '-tA', '-c', statement],
    { encoding: 'utf8' },
  ).trim();
}

async function csrf(request: APIRequestContext): Promise<string> {
  await request.get('/api/auth/csrf');
  const state = await request.storageState();
  return decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '');
}

interface Member {
  email: string;
  handle: string;
  nickname: string;
}

function newMember(tag: string): Member {
  const stamp = `${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`;
  return {
    email: `e2e-wd-${tag}-${stamp}@example.com`,
    handle: `wd${tag}${stamp}`.slice(0, 20),
    nickname: `탈퇴${tag}${stamp}`.slice(0, 10),
  };
}

/** 새 회원을 이메일로 가입시킨다(별도 요청 컨텍스트 — 브라우저 세션과 섞이지 않게). */
async function signup(browser: Browser, member: Member): Promise<void> {
  const context = await browser.newContext();
  const request = context.request;
  const token = await csrf(request);
  const agreements = (await (await request.get('/api/agreements/current')).json()) as {
    terms: { version: string };
    privacy: { version: string };
  };
  const response = await request.post('/api/auth/signup', {
    data: {
      email: member.email,
      handle: member.handle,
      password: PASSWORD,
      passwordConfirm: PASSWORD,
      nickname: member.nickname,
      agreements: {
        termsVersion: agreements.terms.version,
        privacyVersion: agreements.privacy.version,
      },
    },
    headers: { 'X-XSRF-TOKEN': token },
  });
  expect(response.status(), await response.text()).toBe(201);
  await context.close();
  if (PG) {
    sql(`UPDATE auth_identity SET email_verified_at = now() WHERE email = '${member.email}'`);
  }
}

/** 로그인 화면으로 로그인한다(화면이 `location.assign`으로 옮긴다). */
async function loginByForm(page: Page, member: Member) {
  await page.goto('/login');
  const main = page.getByRole('main');
  await main.getByLabel('이메일', { exact: true }).fill(member.email);
  await main.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
  await main.getByRole('button', { name: '로그인' }).click();
}

async function publishPost(page: Page, title: string) {
  const token = await csrf(page.request);
  const created = await page.request.post('/api/posts', {
    data: { title, contentMd: '탈퇴 E2E 본문' },
    headers: { 'X-XSRF-TOKEN': token },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { postId } = (await created.json()) as { postId: number };
  const published = await page.request.post(`/api/posts/${postId}/publish`, {
    data: { title, contentMd: '탈퇴 E2E 본문', tags: [], visibility: 'PUBLIC', baseVersion: 0 },
    headers: { 'X-XSRF-TOKEN': token, 'Idempotency-Key': crypto.randomUUID() },
  });
  expect(published.status(), await published.text()).toBe(200);
}

async function expectNoHorizontalScroll(page: Page, where: string) {
  const size = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(size.scrollWidth, `${where}: 가로 스크롤 없음`).toBeLessThanOrEqual(size.innerWidth);
}

async function expectTouchTarget(page: Page, name: string) {
  const box = await page.getByRole('button', { name }).boundingBox();
  expect(box?.height ?? 0, `${name} 높이`).toBeGreaterThanOrEqual(44);
}

test.describe('회원 탈퇴·복구 (015)', () => {
  test.skip(({ isMobile }) => isMobile, '폭은 시험 안에서 직접 바꾼다 — desktop 프로젝트에서만');

  test('비회원은 로그인 화면으로 가고 돌아올 주소가 붙는다', async ({ page }) => {
    await page.goto('/settings/withdraw');
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fsettings%2Fwithdraw$/);
  });

  test('안내 → 신청 → 완료 → 블로그 404 → 유예 로그인 → 로그아웃 → 복구', async ({
    page,
    browser,
  }) => {
    const a = newMember('a');
    await signup(browser, a);
    await loginByForm(page, a);
    await expect(page).toHaveURL(/\/$/);
    const title = `탈퇴 E2E 글 ${a.handle}`;
    if (PG) {
      await publishPost(page, title);
    }

    // 1) 설정 맨 아래 [회원 탈퇴] → 안내 숫자·주소·기한
    await page.goto('/settings');
    await page.getByRole('link', { name: '회원 탈퇴' }).click();
    await expect(page).toHaveURL(/\/settings\/withdraw$/);
    const main = page.getByRole('main');
    await expect(main).toContainText(
      `블로그 @${a.handle}과 글 ${PG ? 1 : 0}개가 바로 보이지 않아요`,
    );
    await expect(main).toContainText('남의 글에 쓴 댓글 0개는');
    await expect(main).toContainText(
      /30일\(\d{4}년 \d{1,2}월 \d{1,2}일 오[전후] \d{1,2}:\d{2}까지\)/,
    );
    await expect(main.getByLabel(/사유/)).toHaveCount(0);

    // 2) 체크·비밀번호 전 비활성, 처음 포커스 아님, Enter로 제출되지 않음
    const submit = main.getByRole('button', { name: '탈퇴하기' });
    await expect(submit).toBeDisabled();
    await expect(submit).not.toBeFocused();
    await main.getByLabel('위 내용을 확인했어요').check();
    await main.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await expect(submit).toBeEnabled();
    let posted = 0;
    page.on('request', (request) => {
      if (request.url().endsWith('/api/me/withdraw')) {
        posted++;
      }
    });
    await main.getByLabel('비밀번호', { exact: true }).press('Enter');
    await page.waitForTimeout(300);
    expect(posted, 'Enter로 제출되지 않음').toBe(0);

    // 12) 375px 가로 스크롤 없음, 버튼 44px 이상
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '탈퇴 화면');
    await expectTouchTarget(page, '탈퇴하기');
    await page.setViewportSize({ width: 1280, height: 800 });

    // 4) 맞는 비밀번호로 신청 → 완료 화면에 기한
    await submit.click();
    await expect(page).toHaveURL(/\/withdrawn$/);
    await expect(page.getByRole('heading', { name: '탈퇴 신청이 완료됐어요' })).toBeVisible();
    await expect(page.getByRole('main')).toContainText(/까지 로그인하면 복구할 수 있어요/);
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '완료 화면');
    await page.setViewportSize({ width: 1280, height: 800 });
    const me = await page.request.get('/api/me');
    expect(me.status(), '세션이 끊겼다').toBe(401);

    // 5) 다른 사람(비회원)에게 블로그는 404
    const guest = await browser.newPage();
    await guest.goto(`/@${a.handle}`);
    await expect(guest.getByText('볼 수 없는 페이지예요')).toBeVisible();
    await guest.close();

    // 6) 다시 로그인 → 복구 화면만, 다른 주소도 되돌아옴
    await loginByForm(page, a);
    await expect(page).toHaveURL(/\/account\/restore$/);
    await expect(page.getByRole('heading', { name: '탈퇴 신청한 계정이에요' })).toBeVisible();
    await expect(page.getByRole('main')).toContainText(/까지 복구할 수 있어요 \(30일 남음\)/);
    await page.goto('/write/new');
    await expect(page).toHaveURL(/\/account\/restore$/);
    await page.setViewportSize({ width: 375, height: 812 });
    await expectNoHorizontalScroll(page, '복구 화면');
    await expectTouchTarget(page, '복구하기');
    await page.setViewportSize({ width: 1280, height: 800 });

    // 7) [로그아웃] → 다시 로그인해도 복구 화면 → [복구하기] → 홈 + 환영 알림
    await page.getByRole('button', { name: '로그아웃' }).click();
    await expect(page).toHaveURL(/\/$/);
    await loginByForm(page, a);
    await expect(page).toHaveURL(/\/account\/restore$/);
    await page.getByRole('button', { name: '복구하기' }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByText('다시 오신 걸 환영해요')).toBeVisible();
    const restored = await page.request.get('/api/me');
    expect(((await restored.json()) as { status: string }).status).toBe('ACTIVE');
    if (PG) {
      await page.goto(`/@${a.handle}`);
      await expect(page.getByText(title)).toBeVisible();
    }
  });

  test('틀린 비밀번호 5번이면 잠금 문구와 비활성 버튼', async ({ page, browser }) => {
    const b = newMember('b');
    await signup(browser, b);
    await loginByForm(page, b);
    await expect(page).toHaveURL(/\/$/);
    await page.goto('/settings/withdraw');
    const main = page.getByRole('main');
    await main.getByLabel('위 내용을 확인했어요').check();
    for (let i = 1; i <= 5; i++) {
      await main.getByLabel('비밀번호', { exact: true }).fill(`Wrong-${i}-pass!`);
      await main.getByRole('button', { name: '탈퇴하기' }).click();
      await expect(main.getByRole('alert')).toBeVisible();
    }
    await main.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await main.getByRole('button', { name: '탈퇴하기' }).click();
    await expect(main.getByRole('alert')).toHaveText('잠시 후 다시 시도해 주세요(약 15분)');
    await expect(main.getByRole('button', { name: '탈퇴하기' })).toBeDisabled();
  });

  test('기한이 지난 유예 계정은 "복구 기한이 지났어요"와 [로그아웃]만', async ({
    page,
    browser,
  }) => {
    test.skip(!PG, 'E2E_PG_CONTAINER가 있어야 신청 시각을 옮길 수 있다');
    const c = newMember('c');
    await signup(browser, c);
    await loginByForm(page, c);
    await expect(page).toHaveURL(/\/$/);
    await page.goto('/settings/withdraw');
    const main = page.getByRole('main');
    await main.getByLabel('위 내용을 확인했어요').check();
    await main.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await main.getByRole('button', { name: '탈퇴하기' }).click();
    await expect(page).toHaveURL(/\/withdrawn$/);
    sql(`UPDATE member SET withdrawn_at = now() - interval '31 days' WHERE handle = '${c.handle}'`);

    await loginByForm(page, c);
    await expect(page).toHaveURL(/\/account\/restore$/);
    await expect(page.getByRole('heading', { name: '복구 기한이 지났어요' })).toBeVisible();
    await expect(page.getByRole('button', { name: '복구하기' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
  });

  test('탈퇴 유예 중인 이메일로 가입하면 복구 안내', async ({ page, browser }) => {
    const d = newMember('d');
    await signup(browser, d);
    await loginByForm(page, d);
    await expect(page).toHaveURL(/\/$/);
    await page.goto('/settings/withdraw');
    const main = page.getByRole('main');
    await main.getByLabel('위 내용을 확인했어요').check();
    await main.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await main.getByRole('button', { name: '탈퇴하기' }).click();
    await expect(page).toHaveURL(/\/withdrawn$/);

    const other = await browser.newContext();
    const response = await other.request.post('/api/auth/signup', {
      data: {
        email: d.email,
        handle: `${d.handle}x`.slice(0, 20),
        password: PASSWORD,
        passwordConfirm: PASSWORD,
        nickname: '다른사람',
        agreements: { termsVersion: 'x', privacyVersion: 'x' },
      },
      headers: { 'X-XSRF-TOKEN': await csrf(other.request) },
    });
    expect(response.status()).toBe(400);
    const body = (await response.json()) as { errors: { field: string; code: string }[] };
    expect(body.errors).toContainEqual(
      expect.objectContaining({ field: 'email', code: 'EMAIL_WITHDRAWAL_PENDING' }),
    );
    await other.close();
  });
});
