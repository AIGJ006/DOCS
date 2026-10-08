import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MeSummary } from '../../api/me';
import type { PostCard, PostCardPage } from '../../api/types/reading';
import { SessionContext } from '../../features/auth/sessionContext';
import { errorBody, json, ME, requestsTo, stubFetch } from '../../test/fetchRoutes';
import HomePage, { EMPTY_HOME_TEXT, INITIAL_LOAD_FAILED_TEXT } from '../HomePage';

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

function cursorOf(input: unknown): string | null {
  return new URL(String(input), 'http://localhost').searchParams.get('cursor');
}

function BackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(-1)}>
      뒤로
    </button>
  );
}

function renderHome(me: MeSummary | null = null) {
  return render(
    <SessionContext.Provider value={{ loading: false, me, refresh: async () => me }}>
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route
            path="/:handle/posts/:postId"
            element={
              <main data-route="post-detail">
                <BackButton />
                <Link to="/">홈</Link>
              </main>
            }
          />
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

/** 홈 화면 상태 문구와 뒤로 가기 복원 (005 T067, US6 #1~#4, FR-016~018). */
describe('HomePage — 상태·복원', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('[더 보기] 요청 중에는 "불러오는 중…"으로 바뀌고 누를 수 없다', async () => {
    let release: (value: Response) => void = () => undefined;
    let call = 0;
    stubFetch({
      'GET /api/posts': () =>
        call++ === 0
          ? json(200, page(range(20, 9), 'c1'))
          : new Promise<Response>((resolve) => {
              release = resolve;
            }),
    });
    renderHome();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.click(await screen.findByRole('button', { name: '더 보기' }));

    const loading = screen.getByRole('button', { name: '불러오는 중…' });
    expect(loading).toBeDisabled();
    release(json(200, page(range(11, 9), 'c2')));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(18));
  });

  it('[더 보기]가 실패하면 "불러오지 못했어요 [다시 시도]"이고 다시 시도는 같은 커서로 요청한다', async () => {
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/posts': () => {
        call += 1;
        if (call === 1) {
          return json(200, page(range(20, 9), 'c1'));
        }
        if (call === 2) {
          return json(503, errorBody('UNAVAILABLE', '잠시 후 다시 시도해 주세요'));
        }
        return json(200, page(range(11, 9), 'c2'));
      },
    });
    renderHome();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.click(await screen.findByRole('button', { name: '더 보기' }));

    expect(await screen.findByText('불러오지 못했어요')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(9);
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(18));
    const calls = requestsTo(fetchMock, 'GET', '/api/posts');
    expect(calls.map(([input]) => cursorOf(input))).toEqual([null, 'c1', 'c1']);
  });

  it('첫 목록을 못 불러오면 "글을 불러오지 못했어요 [다시 시도]"', async () => {
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/posts': () =>
        call++ === 0
          ? json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요'))
          : json(200, page(range(20, 9), 'c1')),
    });
    renderHome();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    expect(await screen.findByText(INITIAL_LOAD_FAILED_TEXT)).toBeInTheDocument();
    expect(INITIAL_LOAD_FAILED_TEXT).toBe('글을 불러오지 못했어요');
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(9));
    expect(screen.queryByText(INITIAL_LOAD_FAILED_TEXT)).not.toBeInTheDocument();
    const calls = requestsTo(fetchMock, 'GET', '/api/posts');
    expect(calls.map(([input]) => cursorOf(input))).toEqual([null, null]);
  });

  it('글이 하나도 없으면 로그인 회원에게 빈 홈 문구와 [글쓰기]', async () => {
    stubFetch({ 'GET /api/posts': () => json(200, page([], null)) });

    renderHome({ ...ME, emailVerified: true } as MeSummary);

    expect(await screen.findByText(EMPTY_HOME_TEXT)).toBeInTheDocument();
    expect(EMPTY_HOME_TEXT).toBe('아직 올라온 글이 없어요. 첫 글의 주인공이 되어 보세요');
    expect(screen.getByRole('link', { name: '글쓰기' })).toHaveAttribute('href', '/write/new');
    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
    expect(screen.queryByText('모든 글을 다 봤어요')).not.toBeInTheDocument();
  });

  it('글이 하나도 없으면 비회원에게는 같은 문구와 [로그인]', async () => {
    stubFetch({ 'GET /api/posts': () => json(200, page([], null)) });

    renderHome(null);

    expect(await screen.findByText(EMPTY_HOME_TEXT)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?returnTo=/',
    );
    expect(screen.queryByRole('link', { name: '글쓰기' })).not.toBeInTheDocument();
  });

  it('[더 보기] 뒤 글을 열었다가 뒤로 오면 요청 없이 카드와 스크롤 위치를 복원한다', async () => {
    const pages = [page(range(40, 9), 'c1'), page(range(31, 9), 'c2'), page(range(22, 9), 'c3')];
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/posts': () => json(200, pages[Math.min(call++, pages.length - 1)]),
    });
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    renderHome();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.click(await screen.findByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(18));
    await user.click(screen.getByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(27));
    Object.defineProperty(window, 'scrollY', { value: 2400, configurable: true });

    await user.click(screen.getByRole('link', { name: /글 30/ }));
    expect(await screen.findByRole('button', { name: '뒤로' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '뒤로' }));

    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(27));
    expect(requestsTo(fetchMock, 'GET', '/api/posts')).toHaveLength(3);
    expect(scrollTo).toHaveBeenCalledWith(0, 2400);
    expect(screen.getByRole('button', { name: '더 보기' })).toBeInTheDocument();
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });
  });

  it('보관한 지 30분이 지나면 처음 9개부터 다시 부른다', async () => {
    const pages = [page(range(40, 9), 'c1'), page(range(31, 9), 'c2')];
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/posts': () => json(200, pages[Math.min(call++, pages.length - 1)]),
    });
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    renderHome();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(await screen.findByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(18));
    await user.click(screen.getByRole('link', { name: /글 40/ }));
    await screen.findByRole('button', { name: '뒤로' });

    vi.setSystemTime(new Date('2026-10-07T05:31:00Z'));
    await user.click(screen.getByRole('button', { name: '뒤로' }));

    await waitFor(() => expect(requestsTo(fetchMock, 'GET', '/api/posts')).toHaveLength(3));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(9));
  });

  it('링크로 새로 들어오면(뒤로 가기가 아니면) 복원하지 않는다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts': () => json(200, page(range(40, 9), 'c1')),
    });
    renderHome();
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(9));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole('link', { name: /글 40/ }));

    await user.click(await screen.findByRole('link', { name: '홈' }));

    await waitFor(() => expect(requestsTo(fetchMock, 'GET', '/api/posts')).toHaveLength(2));
  });
});
