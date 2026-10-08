import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { TagSuggestion } from '../../../api/types/tags';
import { TAG_SUGGEST_DEBOUNCE_MS, useTagSuggest, type SuggestLoader } from '../useTagSuggest';

function item(name: string, postCount = 1, mine = false): TagSuggestion {
  return { name, postCount, mine };
}

/** 부를 때마다 끝낼 수 있는 약속을 돌려주는 가짜 불러오기 */
function deferredLoader() {
  const calls: {
    q: string;
    signal?: AbortSignal;
    resolve: (v: TagSuggestion[]) => void;
    reject: (e: unknown) => void;
  }[] = [];
  const load = vi.fn<SuggestLoader>(
    (q, signal) =>
      new Promise<TagSuggestion[]>((resolve, reject) => {
        calls.push({ q, signal, resolve, reject });
      }),
  );
  return { load, calls };
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

/** 자동완성 훅 (008 T041, US3 #4·#5, research R12). */
describe('useTagSuggest', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('0.3초 멈추기 전에는 부르지 않는다', async () => {
    const { load, calls } = deferredLoader();
    const { result, rerender } = renderHook(({ text }) => useTagSuggest(text, false, load), {
      initialProps: { text: 's' },
    });
    rerender({ text: 'sp' });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS - 1));
    expect(load).not.toHaveBeenCalled();
    rerender({ text: 'Spr' });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS - 1));
    expect(load).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(1));
    expect(load).toHaveBeenCalledTimes(1);
    // 정규화한 검색어로 부른다
    expect(calls[0].q).toBe('spr');
    expect(result.current).toEqual([]);

    calls[0].resolve([item('spring', 3), item('spring-boot', 1, true)]);
    await flush();
    expect(result.current.map((s) => s.name)).toEqual(['spring', 'spring-boot']);
  });

  it('한글 조합 중에는 부르지 않고, 조합이 끝나면 0.3초 뒤 부른다', async () => {
    const { load } = deferredLoader();
    const { rerender } = renderHook(({ text, composing }) => useTagSuggest(text, composing, load), {
      initialProps: { text: '스', composing: true },
    });
    rerender({ text: '스프', composing: true });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS * 3));
    expect(load).not.toHaveBeenCalled();

    rerender({ text: '스프링', composing: false });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    expect(load).toHaveBeenCalledTimes(1);
    expect(load.mock.calls[0][0]).toBe('스프링');
  });

  it('늦게 온 이전 응답은 버린다', async () => {
    const { load, calls } = deferredLoader();
    const { result, rerender } = renderHook(({ text }) => useTagSuggest(text, false, load), {
      initialProps: { text: 'ja' },
    });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    rerender({ text: 'jav' });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    expect(calls).toHaveLength(2);
    expect(calls[0].signal?.aborted).toBe(true);

    calls[1].resolve([item('java')]);
    await flush();
    calls[0].resolve([item('jabba')]);
    await flush();
    expect(result.current.map((s) => s.name)).toEqual(['java']);
  });

  it('실패·429·빈 결과·불러오는 중에는 목록이 없다', async () => {
    const { load, calls } = deferredLoader();
    const { result, rerender } = renderHook(({ text }) => useTagSuggest(text, false, load), {
      initialProps: { text: 'ja' },
    });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    calls[0].resolve([item('java')]);
    await flush();
    expect(result.current).toHaveLength(1);

    // 입력이 바뀌면 새 응답이 올 때까지 목록을 내린다(불러오는 중)
    rerender({ text: 'jav' });
    expect(result.current).toEqual([]);
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    calls[1].reject(new Error('429'));
    await flush();
    expect(result.current).toEqual([]);

    rerender({ text: 'java' });
    act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    calls[2].resolve([]);
    await flush();
    expect(result.current).toEqual([]);
  });

  it('정리 결과가 빈 입력(#·공백)은 부르지 않는다', () => {
    const { load } = deferredLoader();
    const { rerender } = renderHook(({ text }) => useTagSuggest(text, false, load), {
      initialProps: { text: '' },
    });
    for (const text of ['#', '  ', '#  ']) {
      rerender({ text });
      act(() => vi.advanceTimersByTime(TAG_SUGGEST_DEBOUNCE_MS));
    }
    expect(load).not.toHaveBeenCalled();
  });
});
