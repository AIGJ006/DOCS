import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { PostCard, PostCardPage } from '../../../api/types/reading';
import { useCursorList } from '../useCursorList';

function card(id: number): PostCard {
  return {
    id,
    url: `/@kim755030/posts/${id}`,
    title: `글 ${id}`,
    excerpt: null,
    thumbnailUrl: null,
    firstPublicAt: '2026-10-02T14:03:12.123456Z',
    commentCount: 0,
    likeCount: 0,
    author: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null },
  };
}

function page(ids: number[], nextCursor: string | null): PostCardPage {
  return { items: ids.map(card), nextCursor };
}

/** 이어 보기 훅 (005 FR-004·005, research R-10). */
describe('useCursorList', () => {
  it('첫 요청은 커서 없이 보낸다', async () => {
    const load = vi.fn<(cursor?: string | null) => Promise<PostCardPage>>(async () =>
      page([3, 2, 1], 'c1'),
    );

    const { result } = renderHook(() => useCursorList(load));

    await waitFor(() => expect(result.current.status).toBe('idle'));
    expect(load).toHaveBeenCalledTimes(1);
    expect(load.mock.calls[0][0]).toBeUndefined();
    expect(result.current.items.map((item) => item.id)).toEqual([3, 2, 1]);
    expect(result.current.nextCursor).toBe('c1');
    expect(result.current.done).toBe(false);
  });

  it('loadMore는 마지막 응답의 nextCursor를 그대로 보낸다', async () => {
    const load = vi.fn<(cursor?: string | null) => Promise<PostCardPage>>(async (cursor) =>
      cursor ? page([1], null) : page([3, 2], 'c1'),
    );
    const { result } = renderHook(() => useCursorList(load));
    await waitFor(() => expect(result.current.items).toHaveLength(2));

    await act(async () => {
      await result.current.loadMore();
    });

    expect(load.mock.calls[1][0]).toBe('c1');
    expect(result.current.items.map((item) => item.id)).toEqual([3, 2, 1]);
  });

  it('이어 붙일 때 이미 있는 id는 건너뛴다', async () => {
    const load = async (cursor?: string | null) =>
      cursor ? page([2, 1], null) : page([3, 2], 'c1');
    const { result } = renderHook(() => useCursorList(load));
    await waitFor(() => expect(result.current.items).toHaveLength(2));

    await act(async () => {
      await result.current.loadMore();
    });

    expect(result.current.items.map((item) => item.id)).toEqual([3, 2, 1]);
  });

  it('nextCursor가 null이면 done이고 더 부르지 않는다', async () => {
    const load = vi.fn<(cursor?: string | null) => Promise<PostCardPage>>(async () =>
      page([1], null),
    );
    const { result } = renderHook(() => useCursorList(load));
    await waitFor(() => expect(result.current.done).toBe(true));

    await act(async () => {
      await result.current.loadMore();
    });

    expect(load).toHaveBeenCalledTimes(1);
  });

  it('실패하면 status가 error이고 같은 커서로 다시 시도한다', async () => {
    const load = vi.fn<(cursor?: string | null) => Promise<PostCardPage>>();
    load.mockResolvedValueOnce(page([3, 2], 'c1'));
    load.mockRejectedValueOnce(new Error('offline'));
    load.mockResolvedValueOnce(page([1], null));
    const { result } = renderHook(() => useCursorList(load));
    await waitFor(() => expect(result.current.items).toHaveLength(2));

    await act(async () => {
      await result.current.loadMore();
    });
    expect(result.current.status).toBe('error');
    expect(result.current.nextCursor).toBe('c1');
    expect(result.current.items).toHaveLength(2);

    await act(async () => {
      await result.current.retry();
    });

    expect(load.mock.calls[2][0]).toBe('c1');
    expect(result.current.status).toBe('idle');
    expect(result.current.items.map((item) => item.id)).toEqual([3, 2, 1]);
    expect(result.current.done).toBe(true);
  });

  it('첫 요청이 실패하면 비어 있고 다시 시도는 커서 없이 보낸다', async () => {
    const load = vi.fn<(cursor?: string | null) => Promise<PostCardPage>>();
    load.mockRejectedValueOnce(new Error('offline'));
    load.mockResolvedValueOnce(page([2, 1], null));
    const { result } = renderHook(() => useCursorList(load));
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.loadedOnce).toBe(false);

    await act(async () => {
      await result.current.retry();
    });

    expect(load.mock.calls[1][0]).toBeUndefined();
    expect(result.current.items).toHaveLength(2);
    expect(result.current.loadedOnce).toBe(true);
  });

  it('불러오는 중에는 다시 부르지 않는다', async () => {
    let resolve: ((value: PostCardPage) => void) | null = null;
    const load = vi.fn<(cursor?: string | null) => Promise<PostCardPage>>(
      () =>
        new Promise<PostCardPage>((r) => {
          resolve = r;
        }),
    );
    const { result } = renderHook(() => useCursorList(load));
    await waitFor(() => expect(result.current.status).toBe('loading'));

    await act(async () => {
      await result.current.loadMore();
    });
    expect(load).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolve?.(page([1], null));
    });
    expect(result.current.items).toHaveLength(1);
  });
});
