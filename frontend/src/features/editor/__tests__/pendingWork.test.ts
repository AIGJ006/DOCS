import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadDraft, resetLocalDraftsForTests, saveDraft } from '../localDraftStore';
import { flushPendingWork, setActiveEditor } from '../pendingWork';

const dirty = {
  title: 't',
  contentMd: 'm',
  baseVersion: 2,
  dirty: true,
  pendingImages: [],
  updatedAt: 1,
};

beforeEach(async () => {
  await resetLocalDraftsForTests();
});

describe('flushPendingWork', () => {
  it('열린 에디터를 보내고 이 기기의 다른 dirty 글도 보낸다', async () => {
    await saveDraft(7, 1, dirty);
    await saveDraft(7, 2, dirty);
    await saveDraft(8, 3, dirty);
    const flush = vi.fn(async () => true);
    const release = setActiveEditor({ memberId: 7, postId: 1, flush });
    const send = vi.fn(async () => ({ version: 3, savedAt: '2026-10-07T05:00:00Z' }));

    await expect(flushPendingWork(7, send)).resolves.toBe(true);

    expect(flush).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledWith(2, { title: 't', contentMd: 'm', baseVersion: 2 });
    expect(await loadDraft(7, 2)).toMatchObject({ dirty: false, baseVersion: 3 });
    release();
  });

  it('하나라도 못 보내면 false', async () => {
    await saveDraft(7, 2, dirty);
    const send = vi.fn(async () => {
      throw new Error('offline');
    });
    await expect(flushPendingWork(7, send)).resolves.toBe(false);
  });
});
