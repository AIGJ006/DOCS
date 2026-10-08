import { render, screen, waitFor } from '@testing-library/react';
import type { MeSummary } from '../../api/me';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import type { FeedPage as Page } from '../../api/types/follow';
import type { PostCard } from '../../api/types/reading';
import { SessionContext } from '../../features/auth/sessionContext';
import { FOLLOW_MESSAGES } from '../../features/follow/followMessages';
import { ME, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import * as listRestore from '../../features/post-list/listRestore';
import FeedPage, { FEED_LIST_KEY } from '../FeedPage';

function card(id: number): PostCard {
  return {
    id,
    url: `/@na_ms/posts/${id}`,
    title: `글 ${id}`,
    excerpt: '요약',
    thumbnailUrl: null,
    firstPublicAt: '2026-10-02T14:03:12.123456Z',
    commentCount: 0,
    likeCount: 0,
    author: { handle: 'na_ms', nickname: '나민서', profileImageUrl: null },
  };
}

function page(ids: number[], nextCursor: string | null, hasFollowing = true): Page {
  return { items: ids.map(card), nextCursor, hasFollowing };
}

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderFeed({ loggedIn = true } = {}) {
  const session = {
    loading: false,
    me: loggedIn ? ({ ...ME, emailVerified: true } as MeSummary) : null,
    refresh: async () => null,
  };
  return render(
    <MemoryRouter initialEntries={['/feed']}>
      <SessionContext.Provider value={session}>
        <Routes>
          <Route path="/feed" element={<FeedPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </SessionContext.Provider>
    </MemoryRouter>,
  );
}

/** 팔로잉 피드 화면 (010 T026, US2, FR-017~022). */
describe('FeedPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    resetClientForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('비로그인은 /login?returnTo=%2Ffeed 로 보낸다 (피드 요청 없음)', async () => {
    const fetchMock = stubFetch({});
    renderFeed({ loggedIn: false });
    expect(await screen.findByTestId('where')).toHaveTextContent('/login?returnTo=%2Ffeed');
    expect(requestsTo(fetchMock, 'GET', '/api/feed')).toHaveLength(0);
  });

  it('팔로우한 사람의 카드를 작성자와 함께 보여 주고 [더 보기]로 이어 붙인다', async () => {
    let call = 0;
    const pages = [page([9, 8, 7, 6, 5, 4, 3, 2, 1], 'c1'), page([0], null)];
    const fetchMock = stubFetch({ 'GET /api/feed': () => json(200, pages[call++]) });
    renderFeed();

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    expect(screen.getAllByText('나민서').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(10));
    const calls = requestsTo(fetchMock, 'GET', '/api/feed');
    expect(String(calls[0][0])).toBe('/api/feed');
    expect(String(calls[1][0])).toContain('cursor=c1');
  });

  it('팔로우한 사람이 없으면 홈으로 안내한다', async () => {
    stubFetch({ 'GET /api/feed': () => json(200, page([], null, false)) });
    renderFeed();
    const empty = await screen.findByTestId('empty-feed');
    expect(empty).toHaveTextContent(FOLLOW_MESSAGES.feedNoFollowing);
    expect(screen.getByRole('link', { name: '홈' })).toHaveAttribute('href', '/');
  });

  it('팔로우는 했지만 공개 글이 없으면 다른 문구', async () => {
    stubFetch({ 'GET /api/feed': () => json(200, page([], null, true)) });
    renderFeed();
    const empty = await screen.findByTestId('empty-feed');
    expect(empty).toHaveTextContent(FOLLOW_MESSAGES.feedNoPosts);
    expect(screen.queryByRole('link', { name: '홈' })).toBeNull();
  });

  it("뒤로 가기(POP)로 돌아오면 'feed' 보관값으로 요청 없이 복원한다", async () => {
    window.scrollTo = vi.fn();
    listRestore.save(FEED_LIST_KEY, { items: [card(5), card(4)], nextCursor: 'c9', scrollY: 0 });
    const fetchMock = stubFetch({ 'GET /api/feed': () => json(200, page([1], null)) });
    renderFeed();
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
    expect(requestsTo(fetchMock, 'GET', '/api/feed')).toHaveLength(0);
  });

  it('첫 목록이 실패하면 [다시 시도]', async () => {
    let fail = true;
    stubFetch({
      'GET /api/feed': () =>
        fail
          ? json(500, { code: 'INTERNAL', message: 'x', errors: [], details: null })
          : json(200, page([1], null)),
    });
    renderFeed();
    const retry = await screen.findByRole('button', { name: '다시 시도' });
    fail = false;
    await userEvent.click(retry);
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(1));
  });
});
