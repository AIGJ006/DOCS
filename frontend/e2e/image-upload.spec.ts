import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { createPost, hasAccount, login, publish } from './support';

/**
 * 003 T027 (US1 #1·#2, SC-001·SC-002): GPS EXIF가 든 JPEG를 붙여넣으면 브라우저가 줄이고 다시 만들어 저장소로 직접
 * 올리고, 본문에는 `![](공개 주소)`가 들어간다. 저장소의 파일에는 EXIF(GPS 포함)가 없다.
 *
 * 저장소(MinIO)가 필요하다 — scripts의 e2e 실행 스크립트처럼 앱과 저장소를 띄운 뒤 돌린다. 웹킷 브라우저는 이 환경에 없어
 * 크로미엄만 돌린다(사파리 JPEG 대체는 단위 테스트 imageProcessor.test.ts와 수동 확인 T091).
 */
const FIXTURE = readFileSync(join(import.meta.dirname, 'fixtures/gps.jpg'));
const IMAGE_MD = /!\[\]\((https?:\/\/[^)\s]+\/images\/\d{4}\/\d{2}\/[0-9a-f-]{36}\.(webp|jpg))\)/;

async function pasteImage(page: Page, bytes: Buffer, name: string) {
  const textarea = page.getByLabel('본문');
  await textarea.click();
  await textarea.evaluate(
    (element, { base64, fileName }) => {
      const binary = atob(base64);
      const data = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) data[i] = binary.charCodeAt(i);
      const transfer = new DataTransfer();
      transfer.items.add(new File([data], fileName, { type: 'image/jpeg' }));
      element.dispatchEvent(
        new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }),
      );
    },
    { base64: bytes.toString('base64'), fileName: name },
  );
}

test.describe('사진 붙여넣기 업로드', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('GPS가 든 JPEG → 본문 주소 → 상세 표시, 저장소 파일에 EXIF·GPS 없음', async ({ page }) => {
    test.setTimeout(90_000);
    expect(FIXTURE.includes('Exif')).toBe(true);
    await login(page);
    const postId = await createPost(page);
    await page.goto(`/write/${postId}`);
    await page.getByLabel('제목').fill('사진 업로드 E2E');

    const puts: string[] = [];
    page.on('request', (request) => {
      if (request.method() === 'PUT' && request.url().includes('/images/')) {
        puts.push(request.url().split('?')[0]);
      }
    });

    await pasteImage(page, FIXTURE, 'IMG_0001_GPS.jpg');
    const textarea = page.getByLabel('본문');
    await expect(textarea).toHaveValue(IMAGE_MD, { timeout: 30_000 });
    const content = await textarea.inputValue();
    expect(content).not.toContain('IMG_0001');
    expect(content).not.toContain('uploading:');
    const url = IMAGE_MD.exec(content)![1];

    // 저장소에서 받은 파일: 줄인 결과(1920px 이하), 메타데이터 없음 (SC-002)
    const stored = await page.request.get(url);
    expect(stored.status()).toBe(200);
    expect(stored.headers()['cache-control']).toBe('public, max-age=31536000, immutable');
    const body = await stored.body();
    expect(body.length).toBeLessThan(FIXTURE.length);
    for (const marker of ['Exif', 'EXIF', 'GPS', 'XMP ']) {
      expect(body.includes(marker), marker).toBe(false);
    }
    expect(puts).toContain(url);

    // 발행 → 상세에서 이미지가 보인다
    const published = await publish(page, postId, '사진 업로드 E2E', content);
    await page.goto(published.url);
    const img = page.locator(`article img[src="${url}"]`);
    await expect(img).toBeVisible();
    await expect
      .poll(() => img.evaluate((el) => (el as HTMLImageElement).naturalWidth))
      .toBeGreaterThan(0);
    expect(await img.getAttribute('alt')).toBe('');
  });

  // SC-001 측정 (quickstart §3-1): E2E_SC001_FILE에 5MB 안팎 JPEG 경로를 주면 올라간 원본 크기를 출력한다
  test('5MB JPEG의 올라간 크기 측정', async ({ page }) => {
    const path = process.env.E2E_SC001_FILE;
    test.skip(!path, 'E2E_SC001_FILE이 없으면 건너뛴다');
    test.setTimeout(90_000);
    const source = readFileSync(path!);
    await login(page);
    const postId = await createPost(page);
    await page.goto(`/write/${postId}`);
    await pasteImage(page, source, 'big.jpg');
    const textarea = page.getByLabel('본문');
    await expect(textarea).toHaveValue(IMAGE_MD, { timeout: 60_000 });
    const url = IMAGE_MD.exec(await textarea.inputValue())![1];
    const stored = await (await page.request.get(url)).body();
    const thumb = await (await page.request.get(url.replace(/\.(webp|jpg)$/, '_thumb.$1'))).body();
    console.log(`SC-001: 원래 ${source.length}B → 원본 ${stored.length}B, 썸네일 ${thumb.length}B`);
    expect(stored.length).toBeLessThan(source.length);
  });

  // 003 T050 (US3 #1~#4, SC-008): 저장소에 닿지 못하면 기기에 보관 → 발행 막힘 → 연결되면 본문 주소로 교체 → 발행
  test('저장소에 닿지 못하면 기기에 보관하고, 연결되면 올려서 발행할 수 있다', async ({ page }) => {
    test.setTimeout(120_000);
    await login(page);
    const postId = await createPost(page);
    await page.goto(`/write/${postId}`);
    await page.getByLabel('제목').fill('오프라인 사진 E2E');

    // 저장소 요청만 끊는다(앱 API는 그대로) — 브라우저에는 네트워크 오류로 보인다
    const storageBlocked = (url: URL) => url.pathname.startsWith('/blog/images/');
    await page.route(storageBlocked, (route) => route.abort('internetdisconnected'));

    await pasteImage(page, FIXTURE, 'IMG_0002.jpg');
    const textarea = page.getByLabel('본문');
    await expect(textarea).toHaveValue(/^!\[\]\(local:[0-9a-f-]+\)\n$/, { timeout: 30_000 });
    await expect(page.getByText('업로드 대기 사진 1장')).toBeVisible();
    const preview = page.getByTestId('preview-html').locator('img');
    await expect(preview).toHaveAttribute('src', /^blob:/, { timeout: 10_000 });
    await expect(page.getByRole('status')).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, {
      timeout: 10_000,
    });

    await page.getByRole('button', { name: '발행하기' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '발행' }).click();
    await expect(page.getByText('업로드가 끝나지 않은 사진이 있어요').first()).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: '닫기' }).click();

    await page.unroute(storageBlocked);
    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    await expect(textarea).toHaveValue(IMAGE_MD, { timeout: 30_000 });
    await expect(page.getByText('업로드 대기 사진 1장')).toHaveCount(0);
    await expect(page.getByRole('status')).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, {
      timeout: 15_000,
    });

    await page.getByRole('button', { name: '발행하기' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '발행' }).click();
    await expect(page).not.toHaveURL(/\/write\//, { timeout: 15_000 });
    await expect(page.locator('article img').first()).toBeVisible();
  });

  // 003 T098 (SC-013, 005 SC-005 측정 — quickstart §4): 사진 든 공개 글 9개 → 홈 카드 썸네일 9장 전송량 합계
  test('홈 카드 9장의 썸네일 전송량은 원본의 약 1/9', async ({ page, browser }) => {
    test.setTimeout(300_000);
    const source = process.env.E2E_SC001_FILE ? readFileSync(process.env.E2E_SC001_FILE) : FIXTURE;
    await login(page);
    const originals: string[] = [];
    for (let i = 0; i < 9; i++) {
      const postId = await createPost(page);
      await page.goto(`/write/${postId}`);
      await pasteImage(page, source, `card-${i}.jpg`);
      const textarea = page.getByLabel('본문');
      await expect(textarea).toHaveValue(IMAGE_MD, { timeout: 60_000 });
      const content = await textarea.inputValue();
      originals.push(IMAGE_MD.exec(content)![1]);
      await publish(page, postId, `카드 ${i + 1}`, content);
    }

    // 캐시가 빈 새 창으로 홈을 연다
    const fresh = await browser.newContext();
    const home = await fresh.newPage();
    const transferred = new Map<string, number>();
    home.on('response', async (response) => {
      if (response.url().includes('/images/')) {
        const body = await response.body().catch(() => Buffer.alloc(0));
        transferred.set(response.url(), body.length);
      }
    });
    await home.goto('/');
    const cards = home.locator('img[src*="_thumb."]');
    await expect(cards).toHaveCount(9, { timeout: 15_000 });
    for (const img of await cards.all()) {
      await img.scrollIntoViewIfNeeded();
      await expect
        .poll(() => img.evaluate((el) => (el as HTMLImageElement).naturalWidth))
        .toBeGreaterThan(0);
    }
    await home.waitForLoadState('networkidle');
    const thumbBytes = [...transferred.entries()]
      .filter(([url]) => url.includes('_thumb.'))
      .reduce((sum, [, size]) => sum + size, 0);
    const originalUrls = [...transferred.keys()].filter((url) => !url.includes('_thumb.'));
    let originalBytes = 0;
    for (const url of originals) {
      originalBytes += (await (await page.request.get(url)).body()).length;
    }
    console.log(
      `SC-013: 썸네일 9장 ${thumbBytes}B (원본 9장이면 ${originalBytes}B, 비율 ${(thumbBytes / originalBytes).toFixed(3)})`,
    );
    expect(originalUrls).toEqual([]);
    expect(thumbBytes).toBeLessThan(originalBytes / 3);
    await fresh.close();
  });
});
