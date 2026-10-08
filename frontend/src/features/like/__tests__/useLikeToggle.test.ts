import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import { LIKE_DEBOUNCE_MS, useLikeToggle } from '../useLikeToggle';

const PATH = '/api/posts/7/like';

function setup(initialLiked = false, initialCount = 12) {
  return renderHook(() => useLikeToggle({ postId: 7, initialLiked, initialCount }));
}

async function flush(ms = LIKE_DEBOUNCE_MS) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** 좋아요 즉시 반영·0.3초 마지막 상태·되돌림 (009 T014, US1 #1·#5·#6, SC-005, FR-015). */
describe('useLikeToggle', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    resetClientForTests();
  });

  it('누르면 응답을 기다리지 않고 상태와 수가 바뀌고 0.3초 뒤 PUT 한 번', async () => {
    const fetchMock = stubFetch({
      [`PUT ${PATH}`]: () => json(200, { liked: true, likeCount: 13 }),
    });
    const { result } = setup();

    act(() => result.current.toggle());

    expect(result.current.liked).toBe(true);
    expect(result.current.count).toBe(13);
    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(0);
    await flush();
    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(1);
    expect(requestsTo(fetchMock, 'PUT', PATH)[0][1]?.headers).toBeDefined();
  });

  it('0.3초 안 연타(홀수)는 마지막 상태만 한 번 보낸다', async () => {
    const fetchMock = stubFetch({
      [`PUT ${PATH}`]: () => json(200, { liked: true, likeCount: 13 }),
      [`DELETE ${PATH}`]: () => json(200, { liked: false, likeCount: 12 }),
    });
    const { result } = setup();

    for (let i = 0; i < 5; i++) {
      act(() => result.current.toggle());
      await flush(100);
    }
    expect(result.current.liked).toBe(true);
    await flush();

    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(1);
    expect(requestsTo(fetchMock, 'DELETE', PATH)).toHaveLength(0);
    expect(result.current.liked).toBe(true);
  });

  it('짝수 연타는 요청이 없다', async () => {
    const fetchMock = stubFetch({});
    const { result } = setup(true, 5);

    act(() => result.current.toggle());
    act(() => result.current.toggle());
    await flush();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(result.current.liked).toBe(true);
    expect(result.current.count).toBe(5);
  });

  it('최종 수는 응답 수로 맞춘다 (다른 사람 변화 포함)', async () => {
    stubFetch({ [`DELETE ${PATH}`]: () => json(200, { liked: false, likeCount: 40 }) });
    const { result } = setup(true, 12);

    act(() => result.current.toggle());
    expect(result.current.count).toBe(11);
    await flush();

    expect(result.current.liked).toBe(false);
    expect(result.current.count).toBe(40);
  });

  it('실패하면 누르기 전 상태로 되돌리고 안내한다', async () => {
    stubFetch({
      [`PUT ${PATH}`]: () => json(500, errorBody('INTERNAL_ERROR', '일시적인 오류가 발생했어요')),
    });
    const { result } = setup(false, 12);

    act(() => result.current.toggle());
    await flush();

    expect(result.current.liked).toBe(false);
    expect(result.current.count).toBe(12);
    expect(result.current.notice).toEqual({ kind: 'failed' });
  });

  it('네트워크가 끊겨도 되돌리고 안내한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );
    const { result } = setup(false, 3);

    act(() => result.current.toggle());
    await flush();

    expect(result.current.liked).toBe(false);
    expect(result.current.count).toBe(3);
    expect(result.current.notice?.kind).toBe('failed');
  });

  it('429면 되돌리고 다시 누를 수 있는 시간을 담는다', async () => {
    stubFetch({
      [`PUT ${PATH}`]: () =>
        json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'), {
          'Retry-After': '42',
        }),
    });
    const { result } = setup();

    act(() => result.current.toggle());
    await flush();

    expect(result.current.liked).toBe(false);
    expect(result.current.notice).toEqual({ kind: 'failed', retryAfter: 42 });
  });

  it('401·403 응답은 로그인·인증 안내로 바꾼다', async () => {
    stubFetch({
      [`PUT ${PATH}`]: () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
    });
    const { result } = setup();
    act(() => result.current.toggle());
    await flush();
    expect(result.current.notice).toEqual({ kind: 'login' });
    expect(result.current.liked).toBe(false);

    stubFetch({
      [`PUT ${PATH}`]: () =>
        json(403, errorBody('EMAIL_NOT_VERIFIED', '이메일 인증 후 이용할 수 있어요')),
    });
    act(() => result.current.toggle());
    await flush();
    expect(result.current.notice).toEqual({ kind: 'verifyEmail' });
  });

  it('응답을 기다리는 동안 다시 누르면 응답 뒤에 마지막 상태를 한 번 더 보낸다', async () => {
    let resolvePut: (r: Response) => void = () => undefined;
    const fetchMock = stubFetch({
      [`PUT ${PATH}`]: () => new Promise<Response>((resolve) => (resolvePut = resolve)),
      [`DELETE ${PATH}`]: () => json(200, { liked: false, likeCount: 12 }),
    });
    const { result } = setup();

    act(() => result.current.toggle());
    await flush();
    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(1);
    act(() => result.current.toggle());
    expect(result.current.liked).toBe(false);
    await flush();
    expect(requestsTo(fetchMock, 'DELETE', PATH)).toHaveLength(0);

    await act(async () => {
      resolvePut(json(200, { liked: true, likeCount: 13 }));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.liked).toBe(false);
    await flush();

    expect(requestsTo(fetchMock, 'DELETE', PATH)).toHaveLength(1);
    expect(result.current.liked).toBe(false);
    expect(result.current.count).toBe(12);
  });
});
