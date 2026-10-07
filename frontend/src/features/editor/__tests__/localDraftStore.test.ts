import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  backupKey,
  clearMemberDrafts,
  draftKey,
  draftStorage,
  loadBackup,
  loadDraft,
  purgeExpiredBackups,
  removeDraft,
  resetLocalDraftsForTests,
  saveBackup,
  saveDraft,
  type LocalDraft,
} from '../localDraftStore';

const DAY = 24 * 60 * 60 * 1000;

function draft(overrides: Partial<LocalDraft> = {}): LocalDraft {
  return {
    title: '제목',
    contentMd: '본문',
    baseVersion: 3,
    dirty: true,
    pendingImages: [],
    updatedAt: 1_000,
    ...overrides,
  };
}

beforeEach(async () => {
  await resetLocalDraftsForTests();
});

describe('localDraftStore', () => {
  it('키는 draft:{memberId}:{postId}, 값은 정해진 필드만 (로그인 정보·토큰 없음)', async () => {
    await saveDraft(7, 42, draft());
    expect(draftKey(7, 42)).toBe('draft:7:42');
    const raw = await draftStorage().getItem<Record<string, unknown>>('draft:7:42');
    expect(Object.keys(raw ?? {}).sort()).toEqual(
      ['baseVersion', 'contentMd', 'dirty', 'pendingImages', 'title', 'updatedAt'].sort(),
    );
    expect(await loadDraft(7, 42)).toEqual(draft());
  });

  it('백업 키는 draft-backup:{memberId}:{postId}, 값은 {title, contentMd, baseVersion, backedUpAt}', async () => {
    await saveBackup(7, 42, { title: 't', contentMd: 'm', baseVersion: 2, backedUpAt: 5 });
    expect(backupKey(7, 42)).toBe('draft-backup:7:42');
    expect(await loadBackup(7, 42)).toEqual({
      title: 't',
      contentMd: 'm',
      baseVersion: 2,
      backedUpAt: 5,
    });
  });

  it('clearMemberDrafts는 그 회원의 draft·백업만 모두 지운다', async () => {
    await saveDraft(7, 1, draft());
    await saveDraft(7, 2, draft());
    await saveBackup(7, 1, { title: 't', contentMd: 'm', baseVersion: 1, backedUpAt: 1 });
    await saveDraft(8, 1, draft());
    await saveBackup(8, 1, { title: 't', contentMd: 'm', baseVersion: 1, backedUpAt: 1 });
    await saveDraft(77, 1, draft());

    await clearMemberDrafts(7);

    expect(await loadDraft(7, 1)).toBeNull();
    expect(await loadDraft(7, 2)).toBeNull();
    expect(await loadBackup(7, 1)).toBeNull();
    expect(await loadDraft(8, 1)).not.toBeNull();
    expect(await loadBackup(8, 1)).not.toBeNull();
    expect(await loadDraft(77, 1)).not.toBeNull();
  });

  it('7일 지난 백업은 purgeExpiredBackups에서 지운다', async () => {
    const now = 100 * DAY;
    await saveBackup(7, 1, {
      title: 'old',
      contentMd: 'm',
      baseVersion: 1,
      backedUpAt: now - 7 * DAY - 1,
    });
    await saveBackup(7, 2, {
      title: 'new',
      contentMd: 'm',
      baseVersion: 1,
      backedUpAt: now - 6 * DAY,
    });
    await saveDraft(7, 1, draft({ updatedAt: 0 }));

    await purgeExpiredBackups(now);

    expect(await loadBackup(7, 1)).toBeNull();
    expect(await loadBackup(7, 2)).not.toBeNull();
    expect(await loadDraft(7, 1)).not.toBeNull();
  });

  it('removeDraft는 그 글의 draft만 지운다 (발행 성공·dirty=false로 떠날 때)', async () => {
    await saveDraft(7, 1, draft());
    await saveBackup(7, 1, { title: 't', contentMd: 'm', baseVersion: 1, backedUpAt: 1 });
    await removeDraft(7, 1);
    expect(await loadDraft(7, 1)).toBeNull();
    expect(await loadBackup(7, 1)).not.toBeNull();
  });
});
