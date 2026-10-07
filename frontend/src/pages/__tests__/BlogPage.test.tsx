import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BlogHeader, PostCard, PostCardPage } from '../../api/types/reading';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
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
});
