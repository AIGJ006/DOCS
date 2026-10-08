import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostCard, PostCardPage } from '../../api/types/reading';
import { json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import HomePage from '../HomePage';

function card(id: number): PostCard {
  return {
    id,
    url: `/@kim755030/posts/${id}`,
    title: `글 ${id}`,
    excerpt: '요약',
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

function range(from: number, count: number) {
  return Array.from({ length: count }, (_, i) => from - i);
}

function stubHome(pages: PostCardPage[]) {
  let call = 0;
  return stubFetch({
    'GET /api/posts': () => json(200, pages[Math.min(call++, pages.length - 1)]),
  });
}

function renderHome() {
  return render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  );
}

/** 홈 화면 (005 T021, US1 #2·#3). */
describe('HomePage', () => {
  beforeEach(() => {
    // 뒤로 가기 복원 보관값(005 US6)이 앞 테스트에서 넘어오지 않게
    sessionStorage.clear();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('첫 진입에 목록을 한 번 불러 카드 9개를 보여준다', async () => {
    const fetchMock = stubHome([page(range(20, 9), 'c1')]);

    renderHome();

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    const calls = requestsTo(fetchMock, 'GET', '/api/posts');
    expect(calls).toHaveLength(1);
    expect(String(calls[0][0])).toBe('/api/posts');
  });

  it('[더 보기]를 누르면 다음 9개가 아래에 이어 붙는다', async () => {
    const fetchMock = stubHome([page(range(20, 9), 'c1'), page(range(11, 9), 'c2')]);
    renderHome();
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(18));
    expect(String(requestsTo(fetchMock, 'GET', '/api/posts')[1][0])).toContain('cursor=c1');
    const titles = screen.getAllByTestId('card-title').map((node) => node.textContent);
    expect(titles[0]).toBe('글 20');
    expect(titles[17]).toBe('글 3');
  });

  it('마지막 응답의 nextCursor가 null이면 버튼 대신 안내 문구', async () => {
    stubHome([page(range(20, 9), 'c1'), page([2, 1], null)]);
    renderHome();
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));

    await waitFor(() => expect(screen.getByText('모든 글을 다 봤어요')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
  });

  it('이어 받은 목록에 이미 있는 글이 섞여 있어도 카드는 한 번만 그린다', async () => {
    stubHome([page([5, 4, 3], 'c1'), page([4, 3, 2], null)]);
    renderHome();
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(3));

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(4));
    expect(screen.getAllByTestId('card-title').map((node) => node.textContent)).toEqual([
      '글 5',
      '글 4',
      '글 3',
      '글 2',
    ]);
  });

  describe('[최신] [트렌딩] 탭 (012 T025)', () => {
    function BackButton() {
      const navigate = useNavigate();
      return (
        <button type="button" onClick={() => navigate(-1)}>
          뒤로
        </button>
      );
    }

    function renderAt(entry: string) {
      return render(
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route
              path="/:handle/posts/:postId"
              element={
                <main>
                  <BackButton />
                  <Link to="/">홈</Link>
                </main>
              }
            />
          </Routes>
        </MemoryRouter>,
      );
    }

    it('기본은 최신 탭이고 트렌딩을 부르지 않는다', async () => {
      const fetchMock = stubFetch({
        'GET /api/posts': () => json(200, page(range(20, 9), 'c1')),
        'GET /api/posts/trending': () => json(200, page([99], null)),
      });
      renderAt('/');

      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
      expect(screen.getByRole('tablist', { name: '홈 목록' })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: '최신' })).toHaveAttribute('aria-selected', 'true');
      expect(screen.getByRole('tab', { name: '트렌딩' })).toHaveAttribute('aria-selected', 'false');
      expect(requestsTo(fetchMock, 'GET', '/api/posts/trending')).toHaveLength(0);
    });

    it('/?tab=trending을 바로 열면 트렌딩 탭', async () => {
      const fetchMock = stubFetch({
        'GET /api/posts': () => json(200, page(range(20, 9), 'c1')),
        'GET /api/posts/trending': () => json(200, page([99, 98], null)),
      });
      renderAt('/?tab=trending');

      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
      expect(screen.getByRole('tab', { name: '트렌딩' })).toHaveAttribute('aria-selected', 'true');
      expect(screen.getByText('최근 7일 동안 반응이 많은 글 · 10분마다 갱신')).toBeInTheDocument();
      expect(requestsTo(fetchMock, 'GET', '/api/posts')).toHaveLength(0);
    });

    it('탭을 누르면 바뀌고 탭마다 복원 키가 따로다', async () => {
      const fetchMock = stubFetch({
        'GET /api/posts': () => json(200, page(range(20, 9), 'c1')),
        'GET /api/posts/trending': () => json(200, page([99, 98], null)),
      });
      vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
      renderAt('/');
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

      await user.click(screen.getByRole('tab', { name: '트렌딩' }));
      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
      expect(sessionStorage.getItem('list-restore:home')).not.toBeNull();
      expect(sessionStorage.getItem('list-restore:trending')).not.toBeNull();

      // 트렌딩 글을 열었다가 뒤로 오면 트렌딩 목록을 요청 없이 복원한다
      await user.click(screen.getByRole('link', { name: /글 99/ }));
      await user.click(await screen.findByRole('button', { name: '뒤로' }));
      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
      expect(screen.getByRole('tab', { name: '트렌딩' })).toHaveAttribute('aria-selected', 'true');
      expect(requestsTo(fetchMock, 'GET', '/api/posts/trending')).toHaveLength(1);
      expect(requestsTo(fetchMock, 'GET', '/api/posts')).toHaveLength(1);
    });
  });
});
