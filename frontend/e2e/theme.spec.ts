import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { createPost, hasAccount, login, publish } from './support';

/**
 * 016 다크 모드 종단 확인 (T020·T028·T034·T035, quickstart §3).
 * 다크 모드를 끈 빌드(`VITE_DARK_MODE=false`)면 `<head>`에 theme-init.js가 없어 모두 건너뛴다.
 */

const DARK_BG = 'rgb(18, 18, 18)'; // --color-bg 다크 #121212
const LIGHT_BG = 'rgb(255, 255, 255)';

async function darkModeBuilt(page: Page): Promise<boolean> {
  const response = await page.request.get('/');
  return (await response.text()).includes('/js/theme-init.js');
}

/** CSP 위반을 모은다 (SC-003). */
async function collectCspViolations(context: BrowserContext) {
  await context.addInitScript(() => {
    const store: string[] = [];
    (window as unknown as { __csp: string[] }).__csp = store;
    document.addEventListener('securitypolicyviolation', (event) => {
      store.push(`${event.violatedDirective} ${event.blockedURI}`);
    });
  });
}

async function cspViolations(page: Page): Promise<string[]> {
  return page.evaluate(() => (window as unknown as { __csp?: string[] }).__csp ?? []);
}

async function html(page: Page) {
  return page.evaluate(() => ({
    theme: document.documentElement.dataset.theme,
    choice: document.documentElement.dataset.themeChoice,
  }));
}

async function newPage(browser: Browser, colorScheme: 'light' | 'dark') {
  const context = await browser.newContext({ colorScheme });
  await collectCspViolations(context);
  return { context, page: await context.newPage() };
}

async function publishedCodePost(page: Page): Promise<string> {
  await login(page);
  const postId = await createPost(page);
  const { url } = await publish(
    page,
    postId,
    `테마 확인 ${Date.now()}`,
    '본문 글자\n\n```js\nconst answer = 42; // 주석\nfunction hello() { return "hi"; }\n```\n',
  );
  return new URL(url, 'http://localhost').pathname;
}

test.beforeEach(async ({ page }) => {
  test.skip(!(await darkModeBuilt(page)), '다크 모드를 끈 빌드');
});

test.describe('US1 처음 방문하면 기기 설정을 따른다', () => {
  test('기기 다크: 앱 JS가 오기 전 첫 그리기부터 어두운 배경 (SC-001, US1 #2)', async ({
    browser,
  }) => {
    const { context, page } = await newPage(browser, 'dark');
    let release: () => void = () => undefined;
    const held = new Promise<void>((resolve) => (release = resolve));
    await page.route('**/assets/*.js', async (route) => {
      await held;
      await route.continue();
    });
    await page.goto('/', { waitUntil: 'commit' });
    await page.waitForFunction(() =>
      Array.from(document.styleSheets).some((sheet) => sheet.href?.includes('/assets/')),
    );
    expect(await page.evaluate(() => document.getElementById('root')?.childElementCount)).toBe(0);
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'system' });
    expect(await page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe(
      DARK_BG,
    );
    release();
    await context.close();
  });

  test('기기 다크: 홈·404·블로그 모두 다크, CSP 위반 0 (US1 #1, SC-003)', async ({ browser }) => {
    const { context, page } = await newPage(browser, 'dark');
    for (const path of ['/', '/@nobody-here/posts/1', '/@nobody-here', '/login']) {
      await page.goto(path);
      expect(await html(page), path).toEqual({ theme: 'dark', choice: 'system' });
      expect(await page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe(
        DARK_BG,
      );
    }
    expect(await cspViolations(page)).toEqual([]);
    await context.close();
  });

  test('기기 라이트: 라이트 (US1 #3)', async ({ browser }) => {
    const { context, page } = await newPage(browser, 'light');
    await page.goto('/');
    expect(await html(page)).toEqual({ theme: 'light', choice: 'system' });
    expect(await page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe(
      LIGHT_BG,
    );
    await context.close();
  });

  test('저장소가 막혀도 오류 없이 기기 설정 (US1 #4)', async ({ browser }) => {
    const { context, page } = await newPage(browser, 'dark');
    await context.addInitScript(() => {
      Object.defineProperty(window, 'localStorage', {
        configurable: true,
        get() {
          throw new DOMException('blocked', 'SecurityError');
        },
      });
    });
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto('/');
    await expect(page.getByRole('banner')).toBeVisible();
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'system' });
    expect(errors.filter((message) => message.includes('blocked'))).toEqual([]);
    await context.close();
  });

  test('비회원: 머리말에 테마 버튼이 없고 기기 설정을 따른다, 머리말이 가로로 넘치지 않는다', async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    const header = page.getByRole('banner');
    await expect(header.getByRole('link', { name: '회원 가입' })).toBeVisible();
    await expect(page.getByTestId('theme-toggle')).toHaveCount(0);
    await expect(header.getByRole('button', { name: /테마/ })).toHaveCount(0);
    expect(await html(page)).toEqual({ theme: 'light', choice: 'system' });
    await page.emulateMedia({ colorScheme: 'dark' });
    await expect.poll(() => html(page)).toEqual({ theme: 'dark', choice: 'system' });
    const size = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
    }));
    expect(size.scrollWidth).toBeLessThanOrEqual(size.innerWidth);
  });
});

test.describe('US2 설정 화면에서 테마를 고르고 유지한다 (2026-10-10 머리말 버튼에서 옮김)', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  function themeSection(page: Page) {
    return page.getByRole('region', { name: '화면 테마' });
  }

  test('라디오 선택 즉시 적용, 새로 고침·이동·뒤로 가기 뒤 유지, 시스템이면 기기 설정을 따라감', async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await login(page);
    await page.goto('/settings');
    const section = themeSection(page);
    await expect(section.getByRole('heading', { name: '화면 테마' })).toBeVisible();
    const radios = section.getByRole('radio');
    await expect(radios).toHaveCount(3);
    const system = section.getByRole('radio', { name: '시스템 설정 따르기 (기본)' });
    const light = section.getByRole('radio', { name: '라이트 모드' });
    const dark = section.getByRole('radio', { name: '다크 모드' });
    await expect(system).toBeChecked();

    // US2 #1: 고르면 바로 적용
    await light.check();
    expect(await html(page)).toEqual({ theme: 'light', choice: 'light' });
    await dark.check();
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');
    await expect(page.getByTestId('theme-announcement')).toHaveText('다크 테마로 바꿨어요');

    // US2 #2·SC-002: 새로 고침·이동·뒤로 가기
    await page.reload();
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });
    await expect(dark).toBeChecked();
    await page.goto('/');
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });
    await page.goBack();
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });
    await expect(dark).toBeChecked();

    // 다크 고정이면 기기 설정을 바꿔도 그대로
    await page.emulateMedia({ colorScheme: 'light' });
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });

    // US2 #3: 시스템으로 돌아오면 기기 설정을 바로 따라간다(SC-005)
    await system.check();
    expect(await html(page)).toEqual({ theme: 'light', choice: 'system' });
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBeNull();
    await page.emulateMedia({ colorScheme: 'dark' });
    await expect.poll(() => html(page)).toEqual({ theme: 'dark', choice: 'system' });
    await page.emulateMedia({ colorScheme: 'light' });
    await expect.poll(() => html(page)).toEqual({ theme: 'light', choice: 'system' });

    // US2 #4: 라이트 고정이면 기기 다크로 바꿔도 라이트
    await light.check();
    await page.emulateMedia({ colorScheme: 'dark' });
    expect(await html(page)).toEqual({ theme: 'light', choice: 'light' });

    // 설정 화면이 가로로 넘치지 않는다
    const size = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
    }));
    expect(size.scrollWidth).toBeLessThanOrEqual(size.innerWidth);

    // 다음 시험을 위해 시스템으로
    await system.check();
  });

  test('로그인 → 다크 → 로그아웃 뒤에도 다크, 다른 기기는 그 기기 설정 (US2 #5·#6)', async ({
    page,
    browser,
  }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await login(page);
    await page.goto('/settings');
    await themeSection(page).getByRole('radio', { name: '다크 모드' }).check();
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });

    const header = page.getByRole('banner');
    await header.getByRole('button', { name: /계정 메뉴/ }).click();
    await header.getByRole('button', { name: '로그아웃' }).click();
    await expect(header.getByRole('link', { name: '회원 가입' })).toBeVisible();
    expect(await html(page)).toEqual({ theme: 'dark', choice: 'dark' });
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');

    const other = await newPage(browser, 'light');
    await login(other.page);
    await other.page.goto('/');
    expect(await html(other.page)).toEqual({ theme: 'light', choice: 'system' });
    await other.page.goto('/settings');
    await expect(
      themeSection(other.page).getByRole('radio', { name: '시스템 설정 따르기 (기본)' }),
    ).toBeChecked();
    await other.context.close();
  });
});

test.describe('US3 다크에서도 읽힌다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('코드 강조 다크 색, 사진에 필터 없음, 빈 썸네일은 카드 면과 다름, 에디터 다크 면', async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    const path = await publishedCodePost(page);

    await page.goto(path);
    await expect(page.locator('.hljs-keyword').first()).toBeVisible();
    // US3 #3: --hljs-keyword 다크 #ff7b72
    await expect(page.locator('.hljs-keyword').first()).toHaveCSS('color', 'rgb(255, 123, 114)');

    // US3 #2·SC-006: 사진·프로필 사진·기본 아바타에 필터·혼합·투명도 없음
    const imageStyles = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img, svg[data-testid="default-avatar"]')).map((el) => {
        const style = getComputedStyle(el);
        return `${style.filter}|${style.mixBlendMode}|${style.opacity}`;
      }),
    );
    for (const style of imageStyles) {
      expect(style).toBe('none|normal|1');
    }

    // US3 #5: 썸네일 없는 카드의 빈 영역 ≠ 카드 면
    await page.goto('/');
    const card = page.getByTestId('post-card').first();
    await expect(card).toBeVisible();
    const colors = await card.evaluate((el) => ({
      card: getComputedStyle(el).backgroundColor,
      thumb: getComputedStyle(el.querySelector('[data-testid="card-thumb"]')!).backgroundColor,
    }));
    expect(colors.card).toBe('rgb(30, 30, 30)');
    expect(colors.thumb).not.toBe(colors.card);

    // 에디터 입력칸 다크 면
    await page.goto('/write/new');
    const textarea = page.locator('textarea').first();
    await expect(textarea).toBeVisible();
    expect(await textarea.evaluate((el) => getComputedStyle(el).backgroundColor)).toBe(
      'rgb(30, 30, 30)',
    );
  });

  test('주요 화면 5개 × 라이트·다크 대비 위반 0 (SC-004, axe color-contrast)', async ({ page }) => {
    test.setTimeout(120_000);
    const path = await publishedCodePost(page);
    const handle = path.split('/')[1];
    const screens = ['/', `/${handle}`, path, '/write/new', '/settings'];
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme });
      for (const screen of screens) {
        await page.goto(screen);
        await page.waitForLoadState('networkidle');
        expect(await html(page)).toEqual({ theme: scheme, choice: 'system' });
        const result = await new AxeBuilder({ page }).withRules(['color-contrast']).analyze();
        const problems = result.violations.flatMap((violation) =>
          violation.nodes.map(
            (node) => `${screen} ${scheme}: ${node.target.join(' ')} ${node.failureSummary}`,
          ),
        );
        expect(problems).toEqual([]);
      }
    }
  });
});
