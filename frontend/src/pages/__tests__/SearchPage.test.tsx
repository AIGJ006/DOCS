import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PersonItem, PostSearchItem, PostSearchPage } from '../../api/types/discovery';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import SearchPage from '../SearchPage';

function item(id: number): PostSearchItem {
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
    snippet: { text: `…트랜잭션 이야기 ${id}…`, marks: [[1, 5]] },
  };
}

function page(ids: number[], nextCursor: string | null, notice: PostSearchPage['notice'] = null) {
  return { items: ids.map(item), nextCursor, notice };
}

function range(from: number, count: number) {
  return Array.from({ length: count }, (_, i) => from - i);
}

function paramsOf(input: unknown): URLSearchParams {
  return new URL(String(input), 'http://localhost').searchParams;
}

function BackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(-1)}>
      뒤로
    </button>
  );
}

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderSearch(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/search"
          element={
            <>
              <SearchPage />
              <Where />
            </>
          }
        />
        <Route
          path="/:handle/posts/:postId"
          element={
            <main data-route="post-detail">
              <BackButton />
            </main>
          }
        />
        <Route path="/:handle" element={<main data-route="blog" />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 검색 화면 (012 T012·T033, US1·US3, FR-024·FR-028·FR-032~FR-037). */
describe('SearchPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('글 탭이 기본이고 관련도순으로 부른다 — 카드 요약 자리에 강조된 주변 문장', async () => {
    const fetchMock = stubFetch({
      'GET /api/search/posts': () => json(200, page(range(20, 9), 'c1')),
    });
    renderSearch('/search?q=트랜잭션');

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    expect(screen.getByRole('tab', { name: '글' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: '사람' })).toHaveAttribute('aria-selected', 'false');
    expect(screen.getByRole('button', { name: '관련도순' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    const calls = requestsTo(fetchMock, 'GET', '/api/search/posts');
    expect(calls).toHaveLength(1);
    expect(paramsOf(calls[0][0]).get('q')).toBe('트랜잭션');
    expect(paramsOf(calls[0][0]).get('sort')).toBeNull();
    const first = screen.getAllByTestId('card-excerpt')[0];
    expect(first.querySelector('mark')?.textContent).toBe('트랜잭션');
    expect(screen.queryByTestId('search-notice')).not.toBeInTheDocument();
  });

  it('[최신순]을 누르면 주소에 sort=latest를 두고 처음부터 다시 부른다', async () => {
    const fetchMock = stubFetch({
      'GET /api/search/posts': () => json(200, page(range(20, 3), null)),
    });
    renderSearch('/search?q=트랜잭션');
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(3));

    await userEvent.click(screen.getByRole('button', { name: '최신순' }));

    await waitFor(() => expect(requestsTo(fetchMock, 'GET', '/api/search/posts')).toHaveLength(2));
    const second = requestsTo(fetchMock, 'GET', '/api/search/posts')[1];
    expect(paramsOf(second[0]).get('sort')).toBe('latest');
    expect(paramsOf(second[0]).get('cursor')).toBeNull();
    expect(screen.getByTestId('where').textContent).toContain('sort=latest');
    expect(screen.getByRole('button', { name: '최신순' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('2글자 단어가 있으면 안내, 결과가 없으면 "\'롬복\'에 대한 글이 없어요"', async () => {
    stubFetch({
      'GET /api/search/posts': () => json(200, page([], null, 'TWO_CHAR_TITLE_TAG_ONLY')),
    });
    renderSearch('/search?q=롬복');

    expect(await screen.findByTestId('search-empty')).toHaveTextContent(
      "'롬복'에 대한 글이 없어요",
    );
    expect(screen.getByTestId('search-notice')).toHaveTextContent(
      '두 글자 단어는 제목·태그에서만 찾았어요',
    );
  });

  it('남는 단어가 없으면 요청하지 않고 "두 글자 이상 입력해 주세요"', async () => {
    const fetchMock = stubFetch({});
    renderSearch('/search?q=a%20b');

    expect(await screen.findByText('두 글자 이상 입력해 주세요')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('429면 "잠시 후 다시 시도해 주세요"와 [다시 시도]', async () => {
    let call = 0;
    stubFetch({
      'GET /api/search/posts': () =>
        call++ === 0
          ? json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'), {
              'Retry-After': '30',
            })
          : json(200, page([3, 2], null)),
    });
    renderSearch('/search?q=트랜잭션');

    expect(await screen.findByText(/잠시 후 다시 시도해 주세요/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
  });

  it('[더 보기]는 nextCursor를 그대로 보내고 아래에 이어 붙인다', async () => {
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/search/posts': () =>
        json(200, call++ === 0 ? page(range(20, 9), 'c1') : page(range(11, 2), null)),
    });
    renderSearch('/search?q=트랜잭션&sort=latest');
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(11));
    const second = requestsTo(fetchMock, 'GET', '/api/search/posts')[1];
    expect(paramsOf(second[0]).get('cursor')).toBe('c1');
    expect(paramsOf(second[0]).get('sort')).toBe('latest');
    expect(screen.getByText('모든 글을 다 봤어요')).toBeInTheDocument();
  });

  it('글을 열었다가 뒤로 오면 요청 없이 결과를 복원한다', async () => {
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/search/posts': () =>
        json(200, call++ === 0 ? page(range(20, 9), 'c1') : page(range(11, 9), 'c2')),
    });
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    renderSearch('/search?q=트랜잭션');
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(await screen.findByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(18));

    await user.click(screen.getByRole('link', { name: /글 15/ }));
    await user.click(await screen.findByRole('button', { name: '뒤로' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(18));
    expect(requestsTo(fetchMock, 'GET', '/api/search/posts')).toHaveLength(2);
    expect(sessionStorage.getItem('list-restore:search:posts:relevance:트랜잭션')).not.toBeNull();
  });

  it('검색 화면 입력으로 다시 검색하면 주소의 q가 바뀌고 탭은 유지된다', async () => {
    stubFetch({
      'GET /api/search/posts': () => json(200, page([1], null)),
      'GET /api/search/people': () => json(200, { items: [] }),
    });
    renderSearch('/search?q=트랜잭션&tab=people');

    const box = screen.getByRole('searchbox', { name: '검색어' });
    await userEvent.clear(box);
    await userEvent.type(box, '김민서{Enter}');

    expect(screen.getByTestId('where').textContent).toBe(
      `/search?q=${encodeURIComponent('김민서')}&tab=people`,
    );
  });

  describe('사람 탭', () => {
    const people: PersonItem[] = [
      { handle: 'kim755030', nickname: '김민서', profileImageUrl: null, bioFirstLine: '백엔드' },
      { handle: 'kimminseo', nickname: '김민서2', profileImageUrl: null, bioFirstLine: null },
    ];

    it('결과를 보이고 누르면 블로그로 간다', async () => {
      const fetchMock = stubFetch({
        'GET /api/search/people': () => json(200, { items: people }),
      });
      renderSearch('/search?q=김민서&tab=people');

      await waitFor(() => expect(screen.getAllByTestId('person-item')).toHaveLength(2));
      expect(screen.getByRole('tab', { name: '사람' })).toHaveAttribute('aria-selected', 'true');
      expect(paramsOf(requestsTo(fetchMock, 'GET', '/api/search/people')[0][0]).get('q')).toBe(
        '김민서',
      );
      expect(requestsTo(fetchMock, 'GET', '/api/search/posts')).toHaveLength(0);
      expect(screen.queryByRole('button', { name: '관련도순' })).not.toBeInTheDocument();

      await userEvent.click(screen.getByRole('link', { name: /@kim755030/ }));
      expect(document.querySelector('[data-route="blog"]')).not.toBeNull();
    });

    it('없으면 빈 상태 문구', async () => {
      stubFetch({ 'GET /api/search/people': () => json(200, { items: [] }) });
      renderSearch('/search?q=없는사람&tab=people');

      expect(await screen.findByTestId('people-empty')).toHaveTextContent(
        "'없는사람'에 대한 사람이 없어요",
      );
    });

    it('1글자(@ 제외)면 요청 없이 "두 글자 이상 입력해 주세요"', async () => {
      const fetchMock = stubFetch({});
      renderSearch('/search?q=%40%EA%B9%80&tab=people');

      expect(await screen.findByText('두 글자 이상 입력해 주세요')).toBeInTheDocument();
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('[글] 탭으로 바꾸면 글 검색을 부른다', async () => {
      const fetchMock = stubFetch({
        'GET /api/search/people': () => json(200, { items: people }),
        'GET /api/search/posts': () => json(200, page([1], null)),
      });
      renderSearch('/search?q=김민서&tab=people');
      await waitFor(() => expect(screen.getAllByTestId('person-item')).toHaveLength(2));

      await userEvent.click(screen.getByRole('tab', { name: '글' }));

      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(1));
      expect(requestsTo(fetchMock, 'GET', '/api/search/posts')).toHaveLength(1);
      expect(screen.getByTestId('where').textContent).toBe(
        `/search?q=${encodeURIComponent('김민서')}`,
      );
    });
  });
});
