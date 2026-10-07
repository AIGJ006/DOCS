import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import type { SaveRequest, SaveResponse, ServerCopy } from '../../../api/posts';
import { AutosaveQueue, type AutosaveStatus } from '../autosaveQueue';
import { conflictBannerText, ConflictController, serverCopyOf } from '../conflict';
import { loadBackup, resetLocalDraftsForTests, type LocalDraft } from '../localDraftStore';

/** 002 T099 (US5 #1·#2, FR-021·022·024): 충돌 상태 — 편집은 계속, 서버 전송은 멈춤, 비교 창은 사용자가 고를 때만. */
const SERVER: ServerCopy = {
  title: '다른 탭 제목',
  contentMd: '다른 탭 본문',
  version: 7,
  savedAt: '2026-10-07T05:03:00Z',
};

function conflictError(server: ServerCopy = SERVER) {
  return new ApiError(409, 'VERSION_CONFLICT', '다른 탭이나 기기에서 이 글이 수정되었어요', [], {
    server,
  });
}

function setup() {
  const sent: SaveRequest[] = [];
  const responses: (() => Promise<SaveResponse>)[] = [];
  const local: LocalDraft[] = [];
  const statuses: AutosaveStatus[] = [];
  const controllerRef: { current: ConflictController | null } = { current: null };
  const queue = new AutosaveQueue({
    initial: { title: '내 제목', contentMd: '내 본문', baseVersion: 5 },
    send: (body) => {
      sent.push(body);
      const next = responses.shift();
      return next ? next() : Promise.reject(conflictError());
    },
    saveLocal: async (draft) => {
      local.push(draft);
    },
    onStatus: (s) => statuses.push(s),
    onConflict: (error) => controllerRef.current?.report(serverCopyOf(error) ?? SERVER),
    random: () => 0,
  });
  const changes: (ServerCopy | null)[] = [];
  const opened: number[] = [];
  const controller = new ConflictController({
    queue,
    memberId: 7,
    postId: 42,
    now: () => 1_000,
    onChange: (state) => changes.push(state?.server ?? null),
    onOpenCompare: () => opened.push(1),
  });
  controllerRef.current = controller;
  return { queue, controller, sent, local, statuses, changes, opened, responses };
}

beforeEach(async () => {
  // fake-indexeddb는 타이머를 쓰므로 저장소를 비운 뒤 가짜 타이머로 바꾼다
  await resetLocalDraftsForTests();
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('ConflictController', () => {
  it('자동 저장 409 → 편집은 계속, 이 기기 저장도 계속, 서버 전송만 멈추고 비교 창은 저절로 열지 않는다', async () => {
    const t = setup();
    t.queue.update('내 제목 2', '내 본문 2');
    await vi.advanceTimersByTimeAsync(3_000);
    expect(t.sent).toHaveLength(1);

    expect(t.controller.active).toBe(true);
    expect(t.controller.state?.server).toEqual(SERVER);
    expect(t.statuses.at(-1)).toEqual({ kind: 'conflict' });
    expect(t.opened).toHaveLength(0);

    // 계속 입력해도 이 기기에는 저장되고, 서버에는 보내지 않는다
    t.queue.update('내 제목 3', '내 본문 3');
    await vi.advanceTimersByTimeAsync(60_000);
    expect(t.sent).toHaveLength(1);
    expect(t.local.at(-1)).toMatchObject({ title: '내 제목 3', dirty: true });
    expect(t.opened).toHaveLength(0);
  });

  it('충돌 중에는 [비교하기]·[저장]·[발행]이 비교 창을 연다', () => {
    const t = setup();
    expect(t.controller.intercept()).toBe(false);
    expect(t.opened).toHaveLength(0);

    t.controller.report(SERVER);
    expect(t.controller.intercept()).toBe(true);
    expect(t.controller.intercept()).toBe(true);
    expect(t.controller.intercept()).toBe(true);
    expect(t.opened).toHaveLength(3);
  });

  it('배너 문구는 서버 저장 시각을 서울 시각으로 보인다', () => {
    expect(conflictBannerText(SERVER.savedAt)).toBe(
      '⚠ 다른 탭이나 기기에서 이 글이 수정되었어요(14:03). 지금 내용은 이 기기에만 저장되고 있어요.',
    );
  });

  it('에디터를 열 때 충돌이면 바로 비교 창을 연다', () => {
    const t = setup();
    t.controller.report(SERVER, { open: true });
    expect(t.opened).toHaveLength(1);
    expect(t.statuses.at(-1)).toEqual({ kind: 'conflict' });
  });

  it('편집 중인 내용으로 저장하면 기준 버전을 갱신하고 충돌을 푼다', async () => {
    const t = setup();
    t.controller.report(SERVER);

    t.controller.keptMine(
      { version: 8, savedAt: '2026-10-07T05:05:00Z' },
      { title: '내 제목', contentMd: '내 본문' },
    );

    expect(t.controller.active).toBe(false);
    expect(t.changes.at(-1)).toBeNull();
    expect(t.queue.baseVersion).toBe(8);
    expect(t.statuses.at(-1)).toEqual({ kind: 'saved', savedAt: '2026-10-07T05:05:00Z' });
    t.responses.push(async () => ({ version: 9, savedAt: '2026-10-07T05:06:00Z' }));
    t.queue.update('그다음', '그다음');
    await vi.advanceTimersByTimeAsync(3_000);
    expect(t.sent.at(-1)).toEqual({ title: '그다음', contentMd: '그다음', baseVersion: 8 });
  });

  it('저장된 내용을 불러오면 편집 중 내용을 7일 백업에 두고 서버 내용·버전으로 바꾼다', async () => {
    vi.useRealTimers();
    const t = setup();
    t.controller.report(SERVER);

    const content = await t.controller.loadServer({ title: '내 제목', contentMd: '내 본문' });

    expect(content).toEqual({ title: '다른 탭 제목', contentMd: '다른 탭 본문', baseVersion: 7 });
    expect(await loadBackup(7, 42)).toEqual({
      title: '내 제목',
      contentMd: '내 본문',
      baseVersion: 5,
      backedUpAt: 1_000,
    });
    expect(t.controller.active).toBe(false);
    expect(t.queue.baseVersion).toBe(7);
    expect(t.queue.hasUnsent()).toBe(false);
  });

  it('창을 닫으면 충돌 상태(배너)와 전송 멈춤이 남는다', async () => {
    const t = setup();
    t.controller.report(SERVER);
    t.controller.dismiss();
    expect(t.controller.active).toBe(true);
    t.queue.update('a', 'b');
    await vi.advanceTimersByTimeAsync(10_000);
    expect(t.sent).toHaveLength(0);
  });

  it('409 오류에서 서버 내용을 꺼낸다', () => {
    expect(serverCopyOf(conflictError())).toEqual(SERVER);
    expect(serverCopyOf(new ApiError(409, 'IN_PROGRESS', '발행 중이에요', [], null))).toBeNull();
  });
});
