import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import {
  NOTIFICATION_POLL_MS,
  NOTIFICATIONS_CHANGED_EVENT,
  useUnreadCount,
} from '../useUnreadCount';

const PATH = '/api/notifications/unread-count';
let visibility: DocumentVisibilityState = 'visible';

function setVisibility(state: DocumentVisibilityState) {
  visibility = state;
  document.dispatchEvent(new Event('visibilitychange'));
}

async function flush(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** 안 읽은 수 확인 (011 T025, research R16). */
describe('useUnreadCount', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetClientForTests();
    visibility = 'visible';
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => visibility,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('마운트 즉시 1번, 30초마다 다시 부른다', async () => {
    let count = 2;
    const fetchMock = stubFetch({ [`GET ${PATH}`]: () => json(200, { count: count++ }) });
    const { result } = renderHook(() => useUnreadCount(true));
    await flush();
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(1);
    expect(result.current.count).toBe(2);

    await flush(NOTIFICATION_POLL_MS);
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(2);
    expect(result.current.count).toBe(3);
  });

  it('hidden이면 멈추고 visible이 되면 즉시 1번 부른다', async () => {
    const fetchMock = stubFetch({ [`GET ${PATH}`]: () => json(200, { count: 1 }) });
    renderHook(() => useUnreadCount(true));
    await flush();
    act(() => setVisibility('hidden'));
    await flush(NOTIFICATION_POLL_MS * 3);
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(1);

    act(() => setVisibility('visible'));
    await flush();
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(2);
    await flush(NOTIFICATION_POLL_MS);
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(3);
  });

  it('실패하면 마지막 값을 그대로 둔다', async () => {
    let fail = false;
    stubFetch({
      [`GET ${PATH}`]: () =>
        fail
          ? json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요'))
          : json(200, { count: 4 }),
    });
    const { result } = renderHook(() => useUnreadCount(true));
    await flush();
    expect(result.current.count).toBe(4);
    fail = true;
    await flush(NOTIFICATION_POLL_MS);
    expect(result.current.count).toBe(4);
  });

  it('로그아웃 상태(enabled=false)면 부르지 않는다', async () => {
    const fetchMock = stubFetch({ [`GET ${PATH}`]: () => json(200, { count: 1 }) });
    const { result } = renderHook(() => useUnreadCount(false));
    await flush(NOTIFICATION_POLL_MS * 2);
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(0);
    expect(result.current.count).toBe(0);
  });

  it('줄이기는 바로 반영하고, 바뀜 알림이 오면 즉시 다시 확인한다', async () => {
    const fetchMock = stubFetch({ [`GET ${PATH}`]: () => json(200, { count: 5 }) });
    const { result } = renderHook(() => useUnreadCount(true));
    await flush();
    act(() => result.current.decrease(2));
    expect(result.current.count).toBe(3);
    act(() => result.current.decrease(10));
    expect(result.current.count).toBe(0);
    act(() => {
      window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
    });
    await flush();
    expect(requestsTo(fetchMock, 'GET', PATH)).toHaveLength(2);
    expect(result.current.count).toBe(5);
  });
});
