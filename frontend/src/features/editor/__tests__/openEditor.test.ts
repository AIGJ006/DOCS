import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import type { WorkingCopy } from '../../../api/posts';
import { loadBackup, resetLocalDraftsForTests, saveBackup, saveDraft } from '../localDraftStore';
import { LOCAL_RESTORED_NOTICE, openEditor } from '../openEditor';

const SERVER: WorkingCopy = {
  postId: 42,
  status: 'DRAFT',
  editing: false,
  title: '서버 제목',
  contentMd: '서버 본문',
  version: 5,
  savedAt: '2026-10-07T05:03:00Z',
  visibility: 'PUBLIC',
  tags: [],
  url: null,
};

const load = async () => SERVER;

beforeEach(async () => {
  await resetLocalDraftsForTests();
});

describe('openEditor', () => {
  it('이 기기에 안 보낸 변경이 있고 기준 버전이 같으면 이 기기 내용 + 안내', async () => {
    await saveDraft(7, 42, {
      title: '로컬 제목',
      contentMd: '로컬 본문',
      baseVersion: 5,
      dirty: true,
      pendingImages: [],
      updatedAt: 1,
    });
    const opened = await openEditor(7, 42, { getWorkingCopy: load });
    expect(opened.initial).toEqual({ title: '로컬 제목', contentMd: '로컬 본문', baseVersion: 5 });
    expect(opened.dirty).toBe(true);
    expect(opened.notice).toBe(LOCAL_RESTORED_NOTICE);
    expect(LOCAL_RESTORED_NOTICE).toBe('이 기기에 저장되지 않은 변경을 불러왔어요');
    expect(opened.conflict).toBeNull();
  });

  it('안 보낸 변경의 기준 버전이 서버와 다르면 충돌 — 서버 내용으로 열고 이 기기 내용은 백업', async () => {
    await saveDraft(7, 42, {
      title: '로컬 제목',
      contentMd: '로컬 본문',
      baseVersion: 3,
      dirty: true,
      pendingImages: [],
      updatedAt: 1,
    });
    const opened = await openEditor(7, 42, { getWorkingCopy: load, now: () => 1000 });
    expect(opened.initial).toEqual({ title: '서버 제목', contentMd: '서버 본문', baseVersion: 5 });
    expect(opened.conflict).toEqual({ title: '로컬 제목', contentMd: '로컬 본문', baseVersion: 3 });
    expect(await loadBackup(7, 42)).toEqual({
      title: '로컬 제목',
      contentMd: '로컬 본문',
      baseVersion: 3,
      backedUpAt: 1000,
    });
  });

  it('보낼 변경이 없으면 서버 내용', async () => {
    await saveDraft(7, 42, {
      title: '옛 로컬',
      contentMd: '옛 본문',
      baseVersion: 2,
      dirty: false,
      pendingImages: [],
      updatedAt: 1,
    });
    const opened = await openEditor(7, 42, { getWorkingCopy: load });
    expect(opened.initial).toEqual({ title: '서버 제목', contentMd: '서버 본문', baseVersion: 5 });
    expect(opened.dirty).toBe(false);
    expect(opened.notice).toBeNull();
    expect(opened.conflict).toBeNull();
  });

  it('열 때 7일 지난 백업을 정리한다', async () => {
    await saveBackup(7, 1, { title: 't', contentMd: 'm', baseVersion: 1, backedUpAt: 0 });
    await openEditor(7, 42, { getWorkingCopy: load, now: () => 8 * 24 * 60 * 60 * 1000 });
    expect(await loadBackup(7, 1)).toBeNull();
  });
});
