import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useViewBeacon, VIEW_BEACON_DELAY_MS } from '../useViewBeacon';

function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { value: state, configurable: true });
  document.dispatchEvent(new Event('visibilitychange'));
}

function stubFetchOk() {
  const mock = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', mock);
  return mock;
}

/** 조회 기록 비콘 (005 T031, FR-041, research R-16·R-28). */
describe('useViewBeacon', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      configurable: true,
    });
    document.cookie = 'XSRF-TOKEN=tok-1';
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('보이는 상태로 1초가 지나면 한 번만 기록을 보낸다', () => {
    const fetchMock = stubFetchOk();

    renderHook(() => useViewBeacon({ postId: 7, enabled: true }));
    expect(fetchMock).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(VIEW_BEACON_DELAY_MS));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/posts/7/views');
    expect(init.method).toBe('POST');
    expect(init.keepalive).toBe(true);
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok-1');

    act(() => vi.advanceTimersByTime(10_000));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('1초 전에 숨으면 보내지 않는다', () => {
    const fetchMock = stubFetchOk();
    renderHook(() => useViewBeacon({ postId: 7, enabled: true }));

    act(() => vi.advanceTimersByTime(500));
    act(() => setVisibility('hidden'));
    act(() => vi.advanceTimersByTime(10_000));

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('다시 보이면 남은 시간부터 센다', () => {
    const fetchMock = stubFetchOk();
    renderHook(() => useViewBeacon({ postId: 7, enabled: true }));

    act(() => vi.advanceTimersByTime(600));
    act(() => setVisibility('hidden'));
    act(() => vi.advanceTimersByTime(10_000));
    act(() => setVisibility('visible'));

    act(() => vi.advanceTimersByTime(300));
    expect(fetchMock).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(100));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('숨은 상태로 들어오면 보일 때까지 보내지 않는다', () => {
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    const fetchMock = stubFetchOk();

    renderHook(() => useViewBeacon({ postId: 7, enabled: true }));
    act(() => vi.advanceTimersByTime(10_000));
    expect(fetchMock).not.toHaveBeenCalled();

    act(() => setVisibility('visible'));
    act(() => vi.advanceTimersByTime(VIEW_BEACON_DELAY_MS));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('실패해도 예외를 던지지 않고 다시 보내지 않는다', () => {
    const fetchMock = vi.fn(async () => {
      throw new Error('network down');
    });
    vi.stubGlobal('fetch', fetchMock);

    renderHook(() => useViewBeacon({ postId: 7, enabled: true }));
    act(() => vi.advanceTimersByTime(VIEW_BEACON_DELAY_MS));
    act(() => vi.advanceTimersByTime(10_000));

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('enabled가 false면 아무 요청도 하지 않는다', () => {
    const fetchMock = stubFetchOk();

    renderHook(() => useViewBeacon({ postId: 7, enabled: false }));
    act(() => vi.advanceTimersByTime(10_000));

    expect(fetchMock).not.toHaveBeenCalled();
  });
});
