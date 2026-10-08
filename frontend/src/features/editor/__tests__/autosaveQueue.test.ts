import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import type { SaveRequest, SaveResponse } from '../../../api/posts';
import { AutosaveQueue, type AutosaveStatus } from '../autosaveQueue';
import type { LocalDraft } from '../localDraftStore';

type Deferred = { resolve: (r: SaveResponse) => void; reject: (e: unknown) => void };

function setup(options: { baseVersion?: number; online?: boolean } = {}) {
  const sent: SaveRequest[] = [];
  const pending: Deferred[] = [];
  const local: Omit<LocalDraft, 'pendingImages'>[] = [];
  const statuses: AutosaveStatus[] = [];
  let online = options.online ?? true;
  const onConflict = vi.fn();
  const queue = new AutosaveQueue({
    initial: { title: '', contentMd: '', baseVersion: options.baseVersion ?? 0 },
    send: (body) => {
      sent.push(body);
      return new Promise<SaveResponse>((resolve, reject) => pending.push({ resolve, reject }));
    },
    saveLocal: async (d) => {
      local.push(d);
    },
    onStatus: (s) => statuses.push(s),
    onConflict,
    isOnline: () => online,
    random: () => 0,
  });
  return {
    queue,
    sent,
    local,
    statuses,
    onConflict,
    last: () => statuses[statuses.length - 1],
    respond: (r: SaveResponse) => pending.shift()?.resolve(r),
    fail: (e: unknown) => pending.shift()?.reject(e),
    setOnline: (v: boolean) => {
      online = v;
    },
  };
}

async function tick(ms: number) {
  await vi.advanceTimersByTimeAsync(ms);
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('AutosaveQueue', () => {
  it('입력이 1초 멈추면 이 기기에 저장하고, 3초 멈추면 서버로 보낸다', async () => {
    const t = setup();
    t.queue.update('제목', '본문');

    await tick(999);
    expect(t.local).toHaveLength(0);
    await tick(1);
    expect(t.local.at(-1)).toMatchObject({
      title: '제목',
      contentMd: '본문',
      baseVersion: 0,
      dirty: true,
    });
    expect(t.last()).toEqual({ kind: 'local' });
    expect(t.sent).toHaveLength(0);

    await tick(2000);
    expect(t.sent).toEqual([{ title: '제목', contentMd: '본문', baseVersion: 0 }]);

    t.respond({ version: 1, savedAt: '2026-10-07T05:03:00Z' });
    await tick(0);
    expect(t.last()).toEqual({ kind: 'saved', savedAt: '2026-10-07T05:03:00Z' });
    expect(t.queue.baseVersion).toBe(1);
    expect(t.queue.hasUnsent()).toBe(false);
    expect(t.local.at(-1)).toMatchObject({ baseVersion: 1, dirty: false });
  });

  it('계속 입력하면 30초마다 한 번은 보낸다', async () => {
    const t = setup();
    for (let i = 0; i < 31; i++) {
      t.queue.update('제목', `본문 ${i}`);
      await tick(1000);
    }
    expect(t.sent).toHaveLength(1);
    expect(t.sent[0]?.contentMd).toBe('본문 29');
  });

  it('바뀐 내용이 없으면 보내지 않는다', async () => {
    const t = setup();
    t.queue.update('', '');
    await tick(5000);
    expect(t.sent).toHaveLength(0);
  });

  it('요청은 한 번에 하나 — 응답 뒤에 다음을 보낸다', async () => {
    const t = setup();
    t.queue.update('a', '1');
    await tick(3000);
    expect(t.sent).toHaveLength(1);
    t.queue.update('a', '2');
    await tick(5000);
    expect(t.sent).toHaveLength(1);

    t.respond({ version: 1, savedAt: '2026-10-07T05:03:00Z' });
    await tick(0);
    await tick(3000);
    expect(t.sent).toHaveLength(2);
    expect(t.sent[1]).toEqual({ title: 'a', contentMd: '2', baseVersion: 1 });
  });

  it('실패하면 2s → 4s → 8s로 다시 시도하고 그동안 이 기기에 남긴다', async () => {
    const t = setup();
    t.queue.update('a', '1');
    await tick(3000);
    t.fail(new ApiError(500, 'INTERNAL_ERROR', '오류'));
    await tick(0);
    expect(t.last()).toEqual({ kind: 'local' });
    expect(t.queue.hasUnsent()).toBe(true);

    await tick(1999);
    expect(t.sent).toHaveLength(1);
    await tick(1);
    expect(t.sent).toHaveLength(2);
    t.fail(new ApiError(503, 'AUTOSAVE_UNAVAILABLE', '잠시 후'));
    await tick(3999);
    expect(t.sent).toHaveLength(2);
    await tick(1);
    expect(t.sent).toHaveLength(3);
  });

  it('429면 Retry-After만큼 기다린다', async () => {
    const t = setup();
    t.queue.update('a', '1');
    await tick(3000);
    t.fail(new ApiError(429, 'TOO_MANY_REQUESTS', '잠시 후', [], null, 5));
    await tick(4999);
    expect(t.sent).toHaveLength(1);
    await tick(1);
    expect(t.sent).toHaveLength(2);
  });

  it('413은 다시 시도하지 않고 오류 상태', async () => {
    const t = setup();
    t.queue.update('a', '1');
    await tick(3000);
    t.fail(new ApiError(413, 'PAYLOAD_TOO_LARGE', '요청이 너무 커요'));
    await tick(120_000);
    expect(t.sent).toHaveLength(1);
    expect(t.last()?.kind).toBe('error');
  });

  it('409면 서버 전송을 멈추고 충돌 상태 — 이 기기 저장은 계속', async () => {
    const t = setup();
    t.queue.update('a', '1');
    await tick(3000);
    const conflict = new ApiError(409, 'VERSION_CONFLICT', '다른 곳', [], {
      server: { title: 's', contentMd: 's', version: 9, savedAt: '2026-10-07T05:03:00Z' },
    });
    t.fail(conflict);
    await tick(0);
    expect(t.last()).toEqual({ kind: 'conflict' });
    expect(t.onConflict).toHaveBeenCalledWith(conflict);

    t.queue.update('a', '2');
    await tick(1000);
    expect(t.local.at(-1)).toMatchObject({ contentMd: '2', dirty: true });
    await tick(60_000);
    expect(t.sent).toHaveLength(1);
    expect(t.last()).toEqual({ kind: 'conflict' });
  });

  it('오프라인이면 보내지 않고 오프라인 상태, 연결되면 바로 보낸다', async () => {
    const t = setup({ online: false });
    t.queue.update('a', '1');
    await tick(3000);
    expect(t.sent).toHaveLength(0);
    expect(t.last()).toEqual({ kind: 'offline' });

    t.setOnline(true);
    t.queue.setOnline(true);
    await tick(0);
    expect(t.sent).toHaveLength(1);
  });

  it('네트워크 오류(TypeError)는 오프라인 상태로 보이고 다시 시도한다', async () => {
    const t = setup();
    t.queue.update('a', '1');
    await tick(3000);
    t.fail(new TypeError('Failed to fetch'));
    await tick(0);
    expect(t.last()).toEqual({ kind: 'offline' });
    await tick(2000);
    expect(t.sent).toHaveLength(2);
  });

  it('flush는 기다리지 않고 바로 보내고 다 보냈는지 알려 준다', async () => {
    const t = setup();
    t.queue.update('a', '1');
    const done = t.queue.flush();
    await tick(0);
    expect(t.sent).toHaveLength(1);
    t.respond({ version: 1, savedAt: '2026-10-07T05:03:00Z' });
    await expect(done).resolves.toBe(true);
  });
});
