import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostCard, PostCardPage } from '../../../api/types/reading';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import TrendingList from '../TrendingList';

function card(id: number): PostCard {
  return {
    id,
    url: `/@kim755030/posts/${id}`,
    title: `글 ${id}`,
    excerpt: '요약',
    thumbnailUrl: null,
    firstPublicAt: '2026-10-02T14:03:12.123456Z',
    commentCount: 1,
    likeCount: 2,
    author: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null },
  };
}

function page(ids: number[], nextCursor: string | null): PostCardPage {
  return { items: ids.map(card), nextCursor };
}

function range(from: number, count: number) {
  return Array.from({ length: count }, (_, i) => from - i);
}

function cursorOf(input: unknown): string | null {
  return new URL(String(input), 'http://localhost').searchParams.get('cursor');
}

function renderList() {
  return render(
    <MemoryRouter>
      <TrendingList />
    </MemoryRouter>,
  );
}

/** 트렌딩 목록 (012 T025, US2, FR-011·FR-014·FR-015). */
describe('TrendingList', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('안내 문구와 카드를 보이고 순위 숫자는 없다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/trending': () => json(200, page(range(30, 9), 's1')),
    });
    renderList();

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    expect(screen.getByText('최근 7일 동안 반응이 많은 글 · 10분마다 갱신')).toBeInTheDocument();
    for (const article of screen.getAllByTestId('post-card')) {
      expect(article.textContent).not.toMatch(/^\s*\d+\s*위|#\d+\b/);
    }
    expect(screen.queryByText(/^1$/)).not.toBeInTheDocument();
    expect(String(requestsTo(fetchMock, 'GET', '/api/posts/trending')[0][0])).toBe(
      '/api/posts/trending',
    );
  });

  it('비었으면 "아직 트렌딩 글이 없어요"와 [최신 글 보기] — 최신 글로 채우지 않는다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/trending': () => json(200, page([], null)),
    });
    renderList();

    expect(await screen.findByTestId('empty-trending')).toHaveTextContent(
      '아직 트렌딩 글이 없어요',
    );
    expect(screen.getByRole('link', { name: '최신 글 보기' })).toHaveAttribute('href', '/');
    expect(requestsTo(fetchMock, 'GET', '/api/posts')).toHaveLength(0);
    expect(screen.queryByTestId('post-card')).not.toBeInTheDocument();
  });

  it('nextCursor가 null이면 [더 보기]가 없다', async () => {
    stubFetch({ 'GET /api/posts/trending': () => json(200, page([3, 2, 1], null)) });
    renderList();

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(3));
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
    expect(screen.getByText('모든 글을 다 봤어요')).toBeInTheDocument();
  });

  it('[더 보기]가 410이면 "순위가 새로 바뀌었어요" 후 처음부터 다시 부른다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/trending': () => {
        const calls = requestsTo(fetchMock, 'GET', '/api/posts/trending');
        const cursor = cursorOf(calls[calls.length - 1][0]);
        if (cursor === 's1') {
          return json(410, errorBody('SNAPSHOT_EXPIRED', '순위가 새로 바뀌었어요'));
        }
        return json(200, calls.length === 1 ? page(range(30, 9), 's1') : page([50, 49], null));
      },
    });
    renderList();
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));

    expect(await screen.findByTestId('trending-expired')).toHaveTextContent(
      '순위가 새로 바뀌었어요',
    );
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
    const calls = requestsTo(fetchMock, 'GET', '/api/posts/trending');
    expect(calls).toHaveLength(3);
    expect(cursorOf(calls[2][0])).toBeNull();
    expect(screen.getAllByTestId('card-title').map((node) => node.textContent)).toEqual([
      '글 50',
      '글 49',
    ]);
  });
});
