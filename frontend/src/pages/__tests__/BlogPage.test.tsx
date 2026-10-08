import { render, screen, waitFor, within } from '@testing-library/react';
import type { MeSummary } from '../../api/me';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BlogHeader, PostCard, PostCardPage } from '../../api/types/reading';
import { SessionContext } from '../../features/auth/sessionContext';
import { ME, errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import BlogPage from '../BlogPage';

const BIO = '스프링 백엔드를 공부합니다.\n하루에 한 글씩 씁니다.';

function header(overrides: Partial<BlogHeader> = {}): BlogHeader {
  return {
    handle: 'kim755030',
    nickname: '김민서',
    bio: BIO,
    profileImageUrl: null,
    publicPostCount: 12,
    isMe: false,
    followerCount: 3,
    followingCount: 1234,
    followedByMe: false,
    ...overrides,
  };
}

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

function stubBlog(head: BlogHeader, pages: PostCardPage[]) {
  let call = 0;
  return stubFetch({
    'GET /api/members/kim755030': () => json(200, head),
    'GET /api/members/kim755030/posts': () => json(200, pages[Math.min(call++, pages.length - 1)]),
  });
}

function renderBlog(path = '/@kim755030') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/:handle" element={<BlogPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 개인 블로그 화면 (005 T046, US3 #1·#2·#5). */
describe('BlogPage', () => {
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

  it('머리말과 카드를 함께 보여주고 두 API를 각각 한 번만 부른다', async () => {
    const fetchMock = stubBlog(header(), [page(range(12, 9), 'c1')]);

    renderBlog();

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('김민서');
    expect(screen.getByText('@kim755030')).toBeInTheDocument();
    expect(screen.getByText('공개 글 12')).toBeInTheDocument();
    expect(screen.getByTestId('default-avatar')).toBeInTheDocument();
    const bio = screen.getByTestId('blog-bio');
    expect(bio.textContent).toBe(BIO);
    expect(bio).toHaveStyle({ whiteSpace: 'pre-line' });
    expect(requestsTo(fetchMock, 'GET', '/api/members/kim755030')).toHaveLength(1);
    expect(requestsTo(fetchMock, 'GET', '/api/members/kim755030/posts')).toHaveLength(1);
  });

  it('카드에 작성자 영역이 없다', async () => {
    stubBlog(header(), [page(range(12, 9), 'c1')]);

    renderBlog();

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    expect(screen.queryByTestId('author-chip')).not.toBeInTheDocument();
  });

  it('[더 보기]를 누르면 나머지 3개가 이어 붙고 안내 문구로 끝난다', async () => {
    stubBlog(header(), [page(range(12, 9), 'c1'), page([3, 2, 1], null)]);
    renderBlog();
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(12));
    expect(screen.getByText('모든 글을 다 봤어요')).toBeInTheDocument();
  });

  it('글이 없고 남의 블로그면 "아직 공개한 글이 없어요"', async () => {
    stubBlog(header({ publicPostCount: 0, isMe: false, bio: null }), [page([], null)]);

    renderBlog();

    expect(await screen.findByText('아직 공개한 글이 없어요')).toBeInTheDocument();
    expect(screen.queryByTestId('blog-bio')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '글쓰기' })).not.toBeInTheDocument();
  });

  it('글이 없고 내 블로그면 "첫 글을 써 보세요" + [글쓰기]', async () => {
    stubBlog(header({ publicPostCount: 0, isMe: true }), [page([], null)]);

    renderBlog();

    expect(await screen.findByText('첫 글을 써 보세요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '글쓰기' })).toHaveAttribute('href', '/write/new');
  });

  it('머리말이 404면 공통 404 화면', async () => {
    stubFetch({
      'GET /api/members/nobody_here': () =>
        json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
      'GET /api/members/nobody_here/posts': () =>
        json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
    });

    renderBlog('/@nobody_here');

    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
  });

  it('주소가 @로 시작하지 않으면 404 화면', async () => {
    stubBlog(header(), [page(range(12, 9), null)]);

    renderBlog('/kim755030');

    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
  });

  it('첫 글 목록을 못 불러오면 "글을 불러오지 못했어요 [다시 시도]" (005 T071)', async () => {
    let call = 0;
    stubFetch({
      'GET /api/members/kim755030': () => json(200, header()),
      'GET /api/members/kim755030/posts': () =>
        call++ === 0
          ? json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요'))
          : json(200, page(range(12, 9), 'c1')),
    });
    renderBlog();

    expect(await screen.findByText('글을 불러오지 못했어요')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    expect(screen.queryByText('글을 불러오지 못했어요')).not.toBeInTheDocument();
  });

  it('뒤로 가기로 돌아오면 blog:{handle} 보관값으로 카드를 복원한다 (005 T071)', async () => {
    const fetchMock = stubBlog(header(), [page(range(12, 9), 'c1'), page([3, 2, 1], null)]);
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    function Back() {
      const navigate = useNavigate();
      return (
        <button type="button" onClick={() => navigate(-1)}>
          뒤로
        </button>
      );
    }
    render(
      <MemoryRouter initialEntries={['/@kim755030']}>
        <Routes>
          <Route path="/:handle" element={<BlogPage />} />
          <Route path="/:handle/posts/:postId" element={<Back />} />
        </Routes>
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(12));
    expect(sessionStorage.getItem('list-restore:blog:kim755030')).not.toBeNull();

    await userEvent.click(screen.getByRole('link', { name: /글 2 / }));
    await userEvent.click(await screen.findByRole('button', { name: '뒤로' }));

    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(12));
    expect(requestsTo(fetchMock, 'GET', '/api/members/kim755030/posts')).toHaveLength(2);
    expect(screen.getByText('모든 글을 다 봤어요')).toBeInTheDocument();
    vi.restoreAllMocks();
  });
});

/** 블로그 태그 줄·필터 (008 T057, US5 #1·#2). */
describe('BlogPage 태그 필터', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const STRIP = {
    items: [
      { name: 'spring', postCount: 7 },
      { name: 'jpa', postCount: 5 },
    ],
    initialVisible: 10,
  };

  it('머리말 아래 태그 줄을 보인다', async () => {
    stubFetch({
      'GET /api/members/kim755030': () => json(200, header()),
      'GET /api/members/kim755030/posts': () => json(200, page(range(12, 9), 'c1')),
      'GET /api/members/kim755030/tags': () => json(200, STRIP),
    });
    renderBlog();
    const strip = await screen.findByRole('list', { name: '블로그 태그' });
    expect(strip).toHaveTextContent('#spring 7');
    expect(screen.queryByRole('link', { name: '필터 해제' })).not.toBeInTheDocument();
  });

  it('?tag=jpa면 "#jpa 글 5개 [필터 해제]"와 그 태그 목록', async () => {
    const fetchMock = stubFetch({
      'GET /api/members/kim755030': () => json(200, header()),
      'GET /api/members/kim755030/posts': () => json(200, page([5, 4, 3, 2, 1], null)),
      'GET /api/members/kim755030/tags': () => json(200, STRIP),
    });
    renderBlog('/@kim755030?tag=jpa');

    const filter = await screen.findByRole('status', { name: '태그 필터' });
    await waitFor(() => expect(filter).toHaveTextContent('#jpa 글 5개'));
    expect(within(filter).getByRole('link', { name: '필터 해제' })).toHaveAttribute(
      'href',
      '/@kim755030',
    );
    await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(5));
    const [call] = requestsTo(fetchMock, 'GET', '/api/members/kim755030/posts');
    expect(String(call[0])).toContain('tag=jpa');
  });

  it('[필터 해제]를 누르면 ?tag 없는 주소로 전체 목록을 다시 부른다', async () => {
    const fetchMock = stubFetch({
      'GET /api/members/kim755030': () => json(200, header()),
      'GET /api/members/kim755030/posts': () => json(200, page([5], null)),
      'GET /api/members/kim755030/tags': () => json(200, STRIP),
    });
    const user = userEvent.setup();
    renderBlog('/@kim755030?tag=jpa');
    await user.click(await screen.findByRole('link', { name: '필터 해제' }));

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'GET', '/api/members/kim755030/posts')).toHaveLength(2),
    );
    const calls = requestsTo(fetchMock, 'GET', '/api/members/kim755030/posts');
    expect(String(calls[1][0])).not.toContain('tag=');
    expect(screen.queryByRole('status', { name: '태그 필터' })).not.toBeInTheDocument();
  });

  it('태그 줄 밖 태그면 "#이름 [필터 해제]"', async () => {
    stubFetch({
      'GET /api/members/kim755030': () => json(200, header()),
      'GET /api/members/kim755030/posts': () => json(200, page([], null)),
      'GET /api/members/kim755030/tags': () => json(200, STRIP),
    });
    renderBlog('/@kim755030?tag=c%23');
    const filter = await screen.findByRole('status', { name: '태그 필터' });
    await screen.findByRole('list', { name: '블로그 태그' });
    expect(filter).toHaveTextContent('#c#');
    expect(filter).not.toHaveTextContent('글');
    expect(within(filter).getByRole('link', { name: '필터 해제' })).toBeInTheDocument();
  });
  describe('팔로우 (010 T024·T037)', () => {
    function renderLoggedIn() {
      const session = {
        loading: false,
        me: { ...ME, emailVerified: true } as MeSummary,
        refresh: async () => null,
      };
      return render(
        <MemoryRouter initialEntries={['/@kim755030']}>
          <SessionContext.Provider value={session}>
            <Routes>
              <Route path="/:handle" element={<BlogPage />} />
            </Routes>
          </SessionContext.Provider>
        </MemoryRouter>,
      );
    }

    it('머리말에 "공개 글 · 팔로워 · 팔로잉"과 목록 링크, [팔로우] 버튼', async () => {
      stubBlog(header(), [page(range(12, 9), 'c1')]);

      renderBlog();

      const counts = await screen.findByTestId('follow-counts');
      expect(counts).toHaveTextContent('공개 글 12·팔로워 3·팔로잉 1,234');
      expect(within(counts).getByRole('link', { name: '팔로워 3' })).toHaveAttribute(
        'href',
        '/@kim755030/followers',
      );
      expect(within(counts).getByRole('link', { name: '팔로잉 1,234' })).toHaveAttribute(
        'href',
        '/@kim755030/following',
      );
      expect(screen.getByRole('button', { name: '팔로우' })).toHaveAttribute(
        'aria-pressed',
        'false',
      );
    });

    it('팔로우 중이면 [팔로잉 ✓]로 시작하고 누르면 팔로워 수가 바로 줄어든다', async () => {
      stubFetch({
        'GET /api/members/kim755030': () => json(200, header({ followedByMe: true })),
        'GET /api/members/kim755030/posts': () => json(200, page(range(12, 9), 'c1')),
        'DELETE /api/members/kim755030/follow': () =>
          json(200, { following: false, followerCount: 2 }),
      });

      renderLoggedIn();

      const button = await screen.findByRole('button', { pressed: true });
      expect(button).toHaveTextContent('팔로잉 ✓');
      await userEvent.click(button);

      expect(screen.getByTestId('follow-counts')).toHaveTextContent('팔로워 2');
      expect(screen.getByRole('button', { name: '팔로우' })).toHaveAttribute(
        'aria-pressed',
        'false',
      );
    });

    it('내 블로그면 팔로우 버튼이 없다', async () => {
      stubBlog(header({ isMe: true }), [page(range(12, 9), 'c1')]);

      renderBlog();

      await screen.findByTestId('follow-counts');
      expect(screen.queryByRole('button', { name: /팔로/ })).not.toBeInTheDocument();
    });
  });

  describe('이 블로그에서 검색 (012 T033)', () => {
    function searchPage(ids: number[], nextCursor: string | null) {
      return {
        items: ids.map((id) => ({
          ...card(id),
          snippet: { text: `…스프링 ${id}…`, marks: [[1, 4]] },
        })),
        nextCursor,
        notice: null,
      };
    }

    function paramsOf(input: unknown) {
      return new URL(String(input), 'http://localhost').searchParams;
    }

    it('머리말 아래 "이 블로그에서 검색" 입력으로 ?q=를 두고 이 블로그 안 결과를 보인다', async () => {
      const fetchMock = stubFetch({
        'GET /api/members/kim755030': () => json(200, header()),
        'GET /api/members/kim755030/posts': () => json(200, page(range(12, 9), 'c1')),
        'GET /api/search/posts': () => json(200, searchPage([7, 3], null)),
      });
      renderBlog();
      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(9));

      await userEvent.type(
        screen.getByRole('searchbox', { name: '이 블로그에서 검색' }),
        '스프링{Enter}',
      );

      await waitFor(() => expect(screen.getAllByTestId('post-card')).toHaveLength(2));
      const call = requestsTo(fetchMock, 'GET', '/api/search/posts')[0];
      expect(paramsOf(call[0]).get('q')).toBe('스프링');
      expect(paramsOf(call[0]).get('blog')).toBe('kim755030');
      expect(screen.getByText("'스프링' 검색 결과")).toBeInTheDocument();
      expect(screen.getAllByTestId('card-excerpt')[0].querySelector('mark')?.textContent).toBe(
        '스프링',
      );
      // 블로그 카드처럼 작성자 영역이 없다
      expect(screen.queryByText('김민서', { selector: '[data-testid="post-card"] *' })).toBeNull();
    });

    it('?q=로 바로 열면 글 목록은 부르지 않고 정렬 탭이 있다', async () => {
      const fetchMock = stubFetch({
        'GET /api/members/kim755030': () => json(200, header()),
        'GET /api/members/kim755030/posts': () => json(200, page(range(12, 9), 'c1')),
        'GET /api/search/posts': () => json(200, searchPage([], null)),
      });
      renderBlog('/@kim755030?q=%EB%A1%AC%EB%B3%B5%EC%9D%B4%EC%95%BC&sort=latest');

      expect(await screen.findByTestId('search-empty')).toHaveTextContent(
        "'롬복이야'에 대한 글이 없어요",
      );
      expect(requestsTo(fetchMock, 'GET', '/api/members/kim755030/posts')).toHaveLength(0);
      expect(screen.getByRole('button', { name: '최신순' })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
      expect(paramsOf(requestsTo(fetchMock, 'GET', '/api/search/posts')[0][0]).get('sort')).toBe(
        'latest',
      );
      expect(screen.getByRole('link', { name: '검색 해제' })).toHaveAttribute(
        'href',
        '/@kim755030',
      );
    });
  });
});
