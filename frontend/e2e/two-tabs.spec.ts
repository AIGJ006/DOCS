import { expect, test, type Page } from '@playwright/test';
import { createPost, hasAccount, login } from './support';

/**
 * 002 T100 (US5 Independent Test, SC-007): 같은 글을 두 탭에서 열고 A 탭에서 저장한 뒤 B 탭에서 입력하면 B에 충돌 배너가 뜨고
 * 입력은 계속된다. [비교하기]의 세 선택(편집 중인 내용으로 저장 / 저장된 내용 불러오기 / 새 임시글로 따로 저장)을 확인한다.
 */
const BANNER = /다른 탭이나 기기에서 이 글이 수정되었어요\(\d{2}:\d{2}\)/;

async function saveIn(page: Page, title: string) {
  await page.getByLabel('제목').fill(title);
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.locator('.save-status')).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/, {
    timeout: 10_000,
  });
}

async function typeUntilConflict(page: Page, text: string) {
  await page.getByLabel('본문').fill(text);
  // 이 기기 저장 1초 + 서버 전송 3초(+ 5초에 1번 요청 제한) 뒤 409 → 배너
  await expect(page.getByRole('alert').filter({ hasText: BANNER })).toBeVisible({
    timeout: 20_000,
  });
}

test.describe('여러 탭에서 같은 글을 고쳐도 몰래 덮어쓰지 않는다', () => {
  test.skip(!hasAccount, 'E2E_EMAIL·E2E_PASSWORD(이메일 인증된 회원)가 필요합니다');

  test('A 저장 → B 입력 → B 배너, 입력 계속 → 비교 창의 세 선택', async ({ page, context }) => {
    test.setTimeout(180_000);
    await login(page);
    const postId = await createPost(page, { title: '처음 제목', contentMd: '처음 본문' });
    const tabA = page;
    const tabB = await context.newPage();
    await tabA.goto(`/write/${postId}`);
    await tabB.goto(`/write/${postId}`);
    await expect(tabB.getByLabel('제목')).toHaveValue('처음 제목');

    // ① A 저장 → B 입력 → 배너, 편집은 계속된다
    await saveIn(tabA, 'A 탭 제목');
    await typeUntilConflict(tabB, 'B 탭 본문');
    await tabB.getByLabel('본문').fill('B 탭 본문 계속');
    await expect(tabB.getByLabel('본문')).toHaveValue('B 탭 본문 계속');

    // [비교하기] → 서버 내용과 비교, [편집 중인 내용으로 저장]의 확인 문구 → 돌아가기
    await tabB.getByRole('alert').getByRole('button', { name: '비교하기' }).click();
    const dialog = tabB.getByRole('dialog', { name: '저장된 내용과 비교' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByTestId('diff-title')).toContainText('A 탭 제목');
    await dialog.getByRole('button', { name: '편집 중인 내용으로 저장' }).click();
    const confirm = tabB.getByRole('alertdialog');
    await expect(confirm).toContainText(
      /\d{2}:\d{2}에 저장된 내용이 지금 편집 중인 내용으로 바뀌어요\. 정말 저장할까요\?/,
    );
    await confirm.getByRole('button', { name: '돌아가기' }).click();

    // [저장된 내용 불러오기] → 서버 내용 + 백업 안내
    await dialog.getByRole('button', { name: '저장된 내용 불러오기' }).click();
    await expect(tabB.getByLabel('제목')).toHaveValue('A 탭 제목');
    await expect(
      tabB.getByText('편집 중이던 내용은 이 기기에 7일 동안 보관해 두었어요'),
    ).toBeVisible();
    await expect(tabB.getByRole('alert').filter({ hasText: BANNER })).toHaveCount(0);

    // ② 다시 충돌 → [편집 중인 내용으로 저장] → 확인 → 배너 해제
    await saveIn(tabA, 'A 탭 두 번째');
    await typeUntilConflict(tabB, 'B 탭이 이긴다');
    await tabB.getByRole('alert').getByRole('button', { name: '비교하기' }).click();
    await dialog.getByRole('button', { name: '편집 중인 내용으로 저장' }).click();
    await tabB.getByRole('alertdialog').getByRole('button', { name: '저장', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(tabB.getByRole('alert').filter({ hasText: BANNER })).toHaveCount(0);
    await expect(tabB.locator('.save-status')).toHaveText(/^✓ 저장됨 \d{2}:\d{2}$/);

    // ③ 다시 충돌 → [새 임시글로 따로 저장] → 새 글로 이동
    // A 탭은 ②에서 B가 덮어쓴 버전을 모르므로 새로 연 뒤 저장한다
    await tabA.reload();
    await expect(tabA.getByLabel('본문')).toHaveValue('B 탭이 이긴다');
    await saveIn(tabA, 'A 탭 세 번째');
    await typeUntilConflict(tabB, 'B 탭 따로 저장');
    await tabB.getByRole('alert').getByRole('button', { name: '비교하기' }).click();
    await dialog.getByRole('button', { name: '새 임시글로 따로 저장' }).click();
    await expect(tabB).toHaveURL(/\/write\/\d+$/);
    await expect(tabB).not.toHaveURL(new RegExp(`/write/${postId}$`));
    await expect(tabB.getByLabel('본문')).toHaveValue('B 탭 따로 저장');
  });
});
