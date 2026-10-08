import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostDetail } from '../../api/types/reading';
import { errorBody, json, stubFetch } from '../../test/fetchRoutes';
import PostDetailPage from '../PostDetailPage';

const BIO = '스프링 백엔드를 공부합니다.\n주말에는 등산을 합니다.';

function detail(overrides: Partial<PostDetail> = {}): PostDetail {
  return {
    id: 7,
    status: 'PUBLISHED',
    editorPath: null,
    canonicalPath: '/@kim755030/posts/7',
    visibility: 'PUBLIC',
    title: '제목 <b>굵게</b> 아님',
    contentHtml: '<h2 id="n1">N+1</h2><p>본문</p>',
    hasCodeBlock: false,
    displayedAt: '2026-09-28T10:00:00.000001Z',
    firstPublicAt: '2026-09-28T10:00:00.000001Z',
    publishedAt: '2026-09-28T10:00:00Z',
    editedAt: null,
    tags: ['spring', 'jpa', 'C#'],
    likeCount: 12,
    viewCount: 1234,
    commentCount: 3,
    author: {
      handle: 'kim755030',
      nickname: '김민서',
      profileImageUrl: null,
      bio: BIO,
    },
    viewer: {
      loggedIn: false,
      isAuthor: false,
      likedByMe: false,
      followingAuthor: false,
      emailVerified: false,
      isAdmin: false,
    },
    authorView: null,
    ...overrides,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname + location.search + location.hash}</span>;
}

function renderDetail(path = '/@kim755030/posts/7') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/:handle/posts/:postId" element={<PostDetailPage />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
}

/** 글 상세 화면 (005 T030, US2 #1·#3·#5·#6). */
describe('PostDetailPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('제목은 유일한 h1이고 태그 문자열이 글자로 보인다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    const headings = await waitFor(() => screen.getAllByRole('heading', { level: 1 }));
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent('제목 <b>굵게</b> 아님');
    expect(headings[0].querySelector('b')).toBeNull();
  });

  it('본문은 서버가 정화한 HTML을 그대로 그린다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    const content = await screen.findByTestId('post-content');
    expect(content.querySelector('h2')?.textContent).toBe('N+1');
    expect(content.querySelector('p')?.textContent).toBe('본문');
  });

  it('작성자 영역은 닉네임 @handle이고 블로그로 간다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    const chip = await screen.findByTestId('author-chip');
    const link = chip.querySelector('a');
    expect(link).toHaveAttribute('href', '/@kim755030');
    expect(chip).toHaveTextContent('김민서 @kim755030');
  });

  it('표시 날짜를 보여주고 재발행 글에만 "수정됨 · 10월 3일"을 붙인다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });
    const first = renderDetail();

    await screen.findByTestId('post-content');
    expect(screen.getByText('2026.09.28')).toBeInTheDocument();
    expect(screen.queryByTestId('edited-at')).not.toBeInTheDocument();
    first.unmount();

    stubFetch({
      'GET /api/posts/7': () => json(200, detail({ editedAt: '2026-10-03T05:03:00Z' })),
    });
    renderDetail();

    expect(await screen.findByTestId('edited-at')).toHaveTextContent('수정됨 · 10월 3일');
  });

  it('태그는 입력 순서대로 #이름 링크다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    const tags = await waitFor(() => screen.getAllByTestId('tag'));
    expect(tags.map((tag) => tag.textContent)).toEqual(['#spring', '#jpa', '#C#']);
    expect(tags[0]).toHaveAttribute('href', '/tags/spring');
    expect(tags[2]).toHaveAttribute('href', '/tags/C%23');
  });

  it('반응 줄은 좋아요 수가 맨 앞이고 조회수에 안내 문구가 붙는다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    const bar = await screen.findByTestId('reaction-bar');
    expect(bar.firstElementChild).toHaveAttribute('data-testid', 'like-count');
    expect(screen.getByTestId('like-count')).toHaveTextContent('12');
    expect(screen.getByTestId('view-count')).toHaveTextContent('조회 1,234');
    expect(screen.getByTitle('같은 사람은 하루에 한 번만 세요')).toBeInTheDocument();
  });

  it('조회수가 1만 이상이면 "조회 1.2만"', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail({ viewCount: 12345 })) });

    renderDetail();

    expect(await screen.findByTestId('view-count')).toHaveTextContent('조회 1.2만');
  });

  it('작성자 카드의 소개는 줄바꿈을 유지한다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    const bio = await screen.findByTestId('author-bio');
    expect(bio.textContent).toBe(BIO);
    expect(bio).toHaveStyle({ whiteSpace: 'pre-line' });
  });

  it('댓글 머리말에 댓글 수를 보여주고 ?comment= 값을 댓글 영역에 넘긴다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail('/@kim755030/posts/7?comment=55');

    const section = await screen.findByTestId('comment-section');
    expect(section).toHaveTextContent('댓글 3');
    expect(section).toHaveAttribute('data-around-comment', '55');
    expect(section).toHaveAttribute('data-post-id', '7');
  });

  it('API가 404면 공통 404 화면', async () => {
    stubFetch({
      'GET /api/posts/7': () => json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
    });

    renderDetail();

    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
    expect(screen.queryByTestId('post-content')).not.toBeInTheDocument();
  });

  it('canonicalPath가 현재 경로와 다르면 쿼리를 유지해 바꿔 끼운다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail('/@na_ms/posts/7?comment=55');

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/@kim755030/posts/7?comment=55'),
    );
  });

  it('바꿔 끼울 때 쿼리와 #조각을 함께 유지한다 (005 T065)', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail('/@na_ms/posts/7?comment=55#comment-55');

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(
        '/@kim755030/posts/7?comment=55#comment-55',
      ),
    );
  });

  it('문서 제목을 "{제목} - {닉네임}"으로 바꾼다 (005 T065)', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail();

    await waitFor(() => expect(document.title).toBe('제목 <b>굵게</b> 아님 - 김민서'));
  });

  it('볼 수 없는 글이면 문서 제목이 "볼 수 없는 글이에요" (005 T065)', async () => {
    stubFetch({
      'GET /api/posts/7': () => json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
    });

    renderDetail();

    await waitFor(() => expect(document.title).toBe('볼 수 없는 글이에요'));
  });

  it('주소가 @로 시작하지 않으면 404 화면', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, detail()) });

    renderDetail('/kim755030/posts/7');

    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
  });
});
