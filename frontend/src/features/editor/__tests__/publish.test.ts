import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import type { PublishRequest, PublishResponse, ServerCopy } from '../../../api/posts';
import { finishPublish, PUBLISH_FAILED, publishOnce, type PublishSend } from '../publish';

/** 002 T108 (US6 #1, FR-037): [발행] 한 번 = 새 요청 키 하나, `IN_PROGRESS`만 1초 뒤 같은 키로 다시. */
const BODY: PublishRequest = {
  title: '제목',
  contentMd: '본문',
  tags: ['spring'],
  visibility: 'PUBLIC',
  baseVersion: 3,
};

const RESPONSE: PublishResponse = {
  url: '/@kim/posts/42',
  publishedAt: '2026-10-07T05:00:00Z',
  firstPublicAt: '2026-10-07T05:00:00Z',
  editedAt: null,
  version: 4,
};

const SERVER: ServerCopy = {
  title: '다른 탭',
  contentMd: '다른 본문',
  version: 5,
  savedAt: '2026-10-07T05:03:00Z',
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function inProgress() {
  return new ApiError(409, 'IN_PROGRESS', '발행을 처리하고 있어요');
}

afterEach(() => {
  vi.useRealTimers();
});

describe('publishOnce', () => {
  it('부를 때마다 새 UUID 요청 키를 쓴다', async () => {
    const keys: string[] = [];
    const send: PublishSend = async (_postId, _body, key) => {
      keys.push(key);
      return RESPONSE;
    };

    await publishOnce(42, BODY, { send });
    await publishOnce(42, BODY, { send });

    expect(keys).toHaveLength(2);
    expect(keys[0]).toMatch(UUID);
    expect(keys[1]).toMatch(UUID);
    expect(keys[0]).not.toBe(keys[1]);
  });

  it('성공하면 응답을 돌려준다', async () => {
    const outcome = await publishOnce(42, BODY, { send: async () => RESPONSE });

    expect(outcome).toEqual({ kind: 'published', response: RESPONSE });
  });

  it('409 IN_PROGRESS면 1초 뒤 같은 키로 다시 보낸다', async () => {
    vi.useFakeTimers();
    const calls: { key: string; at: number }[] = [];
    const replies = [
      () => Promise.reject(inProgress()),
      () => Promise.reject(inProgress()),
      () => Promise.resolve(RESPONSE),
    ];
    const send: PublishSend = (_postId, _body, key) => {
      calls.push({ key, at: Date.now() });
      return replies.shift()!();
    };

    const pending = publishOnce(42, BODY, { send });
    await vi.advanceTimersByTimeAsync(999);
    expect(calls).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(calls).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1000);
    const outcome = await pending;

    expect(outcome.kind).toBe('published');
    expect(calls).toHaveLength(3);
    expect(new Set(calls.map((c) => c.key)).size).toBe(1);
    expect(calls[1].at - calls[0].at).toBe(1000);
  });

  it('IN_PROGRESS가 계속되면 정해진 횟수 뒤 멈추고 실패를 알린다', async () => {
    vi.useFakeTimers();
    let count = 0;
    const send: PublishSend = () => {
      count += 1;
      return Promise.reject(inProgress());
    };

    const pending = publishOnce(42, BODY, { send, maxInProgressRetries: 3 });
    await vi.advanceTimersByTimeAsync(5000);
    const outcome = await pending;

    expect(count).toBe(4);
    expect(outcome).toEqual({ kind: 'failed', message: '발행을 처리하고 있어요' });
  });

  it('409 VERSION_CONFLICT면 서버 내용으로 비교 창을 요청한다', async () => {
    const send: PublishSend = () =>
      Promise.reject(
        new ApiError(409, 'VERSION_CONFLICT', '다른 탭이나 기기에서 이 글이 수정되었어요', [], {
          server: SERVER,
        }),
      );

    const outcome = await publishOnce(42, BODY, { send });

    expect(outcome).toEqual({
      kind: 'conflict',
      server: SERVER,
      message: '다른 탭이나 기기에서 이 글이 수정되었어요',
    });
  });

  it('400이면 칸 오류를 돌려준다', async () => {
    const errors = [
      { field: 'title', code: 'TITLE_REQUIRED', message: '제목을 입력해 주세요' },
      { field: 'tags[1]', code: 'TAG_TOO_LONG', message: '태그가 너무 길어요' },
    ];
    const send: PublishSend = () =>
      Promise.reject(new ApiError(400, 'VALIDATION_FAILED', '입력을 확인해 주세요', errors));

    const outcome = await publishOnce(42, BODY, { send });

    expect(outcome).toEqual({ kind: 'invalid', errors, message: '입력을 확인해 주세요' });
  });

  it('네트워크 오류면 일반 실패 문구', async () => {
    const outcome = await publishOnce(42, BODY, {
      send: () => Promise.reject(new TypeError('fetch failed')),
    });

    expect(outcome).toEqual({ kind: 'failed', message: PUBLISH_FAILED });
  });
});

describe('finishPublish', () => {
  it('로컬 초안을 지운 뒤 글 주소로 이동한다', async () => {
    const order: string[] = [];

    await finishPublish(RESPONSE, {
      removeLocalDraft: async () => {
        order.push('remove');
      },
      navigate: (url) => order.push(`go ${url}`),
    });

    expect(order).toEqual(['remove', 'go /@kim/posts/42']);
  });

  it('로컬 초안 삭제가 실패해도 이동한다', async () => {
    const navigate = vi.fn();

    await finishPublish(RESPONSE, {
      removeLocalDraft: () => Promise.reject(new Error('IndexedDB 막힘')),
      navigate,
    });

    expect(navigate).toHaveBeenCalledWith('/@kim/posts/42');
  });
});
