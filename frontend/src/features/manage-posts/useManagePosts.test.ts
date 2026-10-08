import { act, renderHook, screen, waitFor } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ManagePostPage } from '../../api/managePosts';
import { errorBody, json, stubFetch } from '../../test/fetchRoutes';
import { manageItem } from './testItems';
import { useManagePosts, type ManageQuery } from './useManagePosts';

function page(ids: number[], nextCursor: string | null, counts: ManagePostPage['counts']) {
  return { items: ids.map((id) => manageItem(id)), nextCursor, counts };
}

const COUNTS = { drafts: 3, published: 24, trash: 1 };

function LocationSpy() {
  const location = useLocation();
  return createElement('span', { 'data-testid': 'location' }, location.pathname + location.search);
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(
    MemoryRouter,
    { initialEntries: ['/manage/posts?tab=trash'] },
    createElement(LocationSpy),
    createElement(Routes, null, [
      createElement(Route, { key: 'm', path: '/manage/posts', element: children }),
      createElement(Route, { key: 'l', path: '/login', element: null }),
    ]),
  );
}

function urls(mock: ReturnType<typeof stubFetch>) {
  return mock.mock.calls.map(([input]) => String(input));
}

/** 관리 목록 훅 (006 T036, research R17, SC-006, FR-004). */
describe('useManagePosts', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('[더 보기]에 이미 화면에 있는 id가 섞여 오면 건너뛴다', async () => {
    const mock = stubFetch({
      'GET /api/me/posts': () => json(200, page([5, 4, 3], 'c1', COUNTS)),
    });
    const { result } = renderHook(() => useManagePosts({ tab: 'drafts', visibility: null }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.items).toHaveLength(3));

    mock.mockImplementation(async () => json(200, page([3, 2, 1], null, null)));
    await act(() => result.current.loadMore());

    expect(result.current.items.map((i) => i.id)).toEqual([5, 4, 3, 2, 1]);
    expect(result.current.nextCursor).toBeNull();
    expect(urls(mock).at(-1)).toBe('/api/me/posts?tab=drafts&cursor=c1');
  });

  it('counts는 첫 응답 값만 쓰고 이후 null로 덮지 않으며 adjustCounts는 화면 숫자만 바꾼다', async () => {
    const mock = stubFetch({
      'GET /api/me/posts': () => json(200, page([5], 'c1', COUNTS)),
    });
    const { result } = renderHook(() => useManagePosts({ tab: 'published', visibility: null }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.counts).toEqual(COUNTS));

    mock.mockImplementation(async () => json(200, page([4], null, null)));
    await act(() => result.current.loadMore());
    expect(result.current.counts).toEqual(COUNTS);

    act(() => result.current.adjustCounts({ trash: +1, published: -1 }));
    expect(result.current.counts).toEqual({ drafts: 3, published: 23, trash: 2 });
    expect(mock).toHaveBeenCalledTimes(2);
  });

  it('removeRow·updateRow는 그 줄만 바꾼다', async () => {
    stubFetch({ 'GET /api/me/posts': () => json(200, page([3, 2, 1], null, COUNTS)) });
    const { result } = renderHook(() => useManagePosts({ tab: 'drafts', visibility: null }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.items).toHaveLength(3));
    const untouched = result.current.items[2];

    act(() => result.current.removeRow(2));
    act(() => result.current.updateRow(3, { editing: true }));

    expect(result.current.items.map((i) => i.id)).toEqual([3, 1]);
    expect(result.current.items[0].editing).toBe(true);
    expect(result.current.items[1]).toBe(untouched);
  });

  it('탭·필터를 바꾸면 커서와 목록을 초기화하고 첫 페이지를 다시 부른다', async () => {
    const mock = stubFetch({
      'GET /api/me/posts': () => json(200, page([5, 4], 'c1', COUNTS)),
    });
    const { result, rerender } = renderHook((query: ManageQuery) => useManagePosts(query), {
      wrapper,
      initialProps: { tab: 'drafts', visibility: null } as ManageQuery,
    });
    await waitFor(() => expect(result.current.nextCursor).toBe('c1'));

    mock.mockImplementation(async () => json(200, page([9], null, COUNTS)));
    rerender({ tab: 'published', visibility: 'private' });

    await waitFor(() => expect(result.current.items.map((i) => i.id)).toEqual([9]));
    expect(result.current.nextCursor).toBeNull();
    expect(urls(mock).at(-1)).toBe('/api/me/posts?tab=published&visibility=private');
  });

  it('reload는 첫 페이지를 다시 불러 글 수를 새로 받는다', async () => {
    const mock = stubFetch({
      'GET /api/me/posts': () => json(200, page([5, 4], 'c1', COUNTS)),
    });
    const { result } = renderHook(() => useManagePosts({ tab: 'drafts', visibility: null }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.items).toHaveLength(2));

    mock.mockImplementation(async () =>
      json(200, page([4], null, { drafts: 1, published: 24, trash: 2 })),
    );
    await act(() => result.current.reload());

    expect(result.current.items.map((i) => i.id)).toEqual([4]);
    expect(result.current.counts).toEqual({ drafts: 1, published: 24, trash: 2 });
    expect(urls(mock).at(-1)).toBe('/api/me/posts?tab=drafts');
  });

  it('비회원(401)이면 로그인 화면으로 보내고 돌아올 주소를 붙인다', async () => {
    stubFetch({
      'GET /api/me/posts': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
    });
    renderHook(() => useManagePosts({ tab: 'trash', visibility: null }), { wrapper });

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(
        '/login?returnTo=%2Fmanage%2Fposts%3Ftab%3Dtrash',
      ),
    );
  });
});
