import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import { FOLLOW_DEBOUNCE_MS, useFollowToggle } from '../useFollowToggle';

const PATH = '/api/members/na_ms/follow';

function setup(initialFollowing = false, initialCount = 12) {
  return renderHook(() => useFollowToggle({ handle: 'na_ms', initialFollowing, initialCount }));
}

async function flush(ms = FOLLOW_DEBOUNCE_MS) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** 팔로우 즉시 반영·0.3초 마지막 상태·되돌림 (010 T016, FR-010, SC-009). */
describe('useFollowToggle', () => {
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
      [`PUT ${PATH}`]: () => json(200, { following: true, followerCount: 13 }),
    });
    const { result } = setup();

    act(() => result.current.toggle());

    expect(result.current.following).toBe(true);
    expect(result.current.count).toBe(13);
    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(0);
    await flush();
    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(1);
  });

  it('0.3초 안 연타(홀수)는 마지막 상태만 한 번 보낸다', async () => {
    const fetchMock = stubFetch({
      [`PUT ${PATH}`]: () => json(200, { following: true, followerCount: 13 }),
      [`DELETE ${PATH}`]: () => json(200, { following: false, followerCount: 12 }),
    });
    const { result } = setup();

    for (let i = 0; i < 5; i++) {
      act(() => result.current.toggle());
      await flush(100);
    }
    await flush();

    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(1);
    expect(requestsTo(fetchMock, 'DELETE', PATH)).toHaveLength(0);
    expect(result.current.following).toBe(true);
  });

  it('짝수 연타는 요청이 없다', async () => {
    const fetchMock = stubFetch({});
    const { result } = setup(true, 5);

    act(() => result.current.toggle());
    act(() => result.current.toggle());
    await flush();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(result.current.following).toBe(true);
    expect(result.current.count).toBe(5);
  });

  it('응답 값으로 맞춘다 (다른 사람 변화 포함)', async () => {
    stubFetch({
      [`DELETE ${PATH}`]: () => json(200, { following: false, followerCount: 40 }),
    });
    const { result } = setup(true, 12);

    act(() => result.current.toggle());
    expect(result.current.count).toBe(11);
    await flush();

    expect(result.current.following).toBe(false);
    expect(result.current.count).toBe(40);
  });

  it('실패하면 누르기 전 상태로 되돌리고 오류를 둔다', async () => {
    stubFetch({
      [`PUT ${PATH}`]: () => json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요')),
    });
    const { result } = setup(false, 3);

    act(() => result.current.toggle());
    expect(result.current.following).toBe(true);
    await flush();

    expect(result.current.following).toBe(false);
    expect(result.current.count).toBe(3);
    expect(result.current.error).toBeInstanceOf(ApiError);
    act(() => result.current.dismiss());
    expect(result.current.error).toBeNull();
  });
});
