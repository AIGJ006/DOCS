import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostDetail } from '../../api/types/reading';
import { json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import { VIEW_BEACON_DELAY_MS } from '../../features/post-detail/useViewBeacon';
import { FOLLOW_DEBOUNCE_MS } from '../../features/follow/useFollowToggle';
import PostDetailPage from '../PostDetailPage';

function authorDetail(overrides: Partial<PostDetail> = {}): PostDetail {
  return {
    id: 7,
    status: 'PUBLISHED',
    editorPath: null,
    canonicalPath: '/@kim755030/posts/7',
    visibility: 'PUBLIC',
    title: '내 글',
    contentHtml: '<p>마지막 발행본</p>',
    hasCodeBlock: false,
    displayedAt: '2026-09-28T10:00:00Z',
    firstPublicAt: '2026-09-28T10:00:00Z',
    publishedAt: '2026-09-28T10:00:00Z',
    editedAt: null,
    tags: [],
    likeCount: 4,
    viewCount: 10,
    commentCount: 0,
    author: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null, bio: null },
    viewer: {
      loggedIn: true,
      isAuthor: true,
      likedByMe: false,
      followingAuthor: false,
      emailVerified: true,
      isAdmin: false,
    },
    authorView: { hasDraft: false, draftSavedAt: null, hidden: false, hiddenReason: null },
    ...overrides,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname + location.search}</span>;
}

function renderDetail(path = '/@kim755030/posts/7') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/:handle/posts/:postId" element={<PostDetailPage />} />
        <Route path="/write/:postId" element={<main data-route="editor" />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
}

/** 작성자가 보는 자기 글 상세 화면 (005 T054, US4 #1~#4·#6, SC-007). */
describe('PostDetailPage — 작성자', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('작성자에게는 [수정]이 있고 [좋아요]·[신고]·[팔로우]는 없으며 좋아요 수만 보인다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, authorDetail()) });

    renderDetail();

    const edit = await screen.findByRole('link', { name: '수정' });
    expect(edit).toHaveAttribute('href', '/write/7');
    expect(screen.getByTestId('author-actions')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /신고/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /팔로우/ })).not.toBeInTheDocument();
    expect(screen.getByTestId('like-count')).toHaveTextContent('4');
  });

  it('[공개 범위 ▾]·[삭제] 자리는 004·006이 넣는 부품을 그린다', async () => {
    stubFetch({ 'GET /api/posts/7': () => json(200, authorDetail()) });

    render(
      <MemoryRouter initialEntries={['/@kim755030/posts/7']}>
        <Routes>
          <Route
            path="/:handle/posts/:postId"
            element={
              <PostDetailPage
                visibilityControl={({ visibility }) => (
                  <button type="button">공개 범위 ▾ {visibility}</button>
                )}
                deleteControl={({ postId }) => <button type="button">삭제 {postId}</button>}
              />
            }
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('button', { name: '공개 범위 ▾ PUBLIC' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제 7' })).toBeInTheDocument();
  });

  it('독자에게는 작성자 버튼이 없다', async () => {
    stubFetch({
      'GET /api/posts/7': () =>
        json(
          200,
          authorDetail({
            viewer: {
              loggedIn: true,
              isAuthor: false,
              likedByMe: false,
              followingAuthor: false,
              emailVerified: true,
              isAdmin: false,
            },
            authorView: null,
          }),
        ),
    });

    renderDetail();

    await screen.findByTestId('post-content');
    expect(screen.queryByTestId('author-actions')).not.toBeInTheDocument();
    expect(screen.queryByTestId('author-status')).not.toBeInTheDocument();
  });

  it('작성자에게는 조회 기록 요청을 보내지 않는다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/7': () => json(200, authorDetail()),
      'POST /api/posts/7/views': () => new Response(null, { status: 204 }),
    });

    renderDetail();
    await screen.findByTestId('post-content');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(VIEW_BEACON_DELAY_MS * 3);
    });

    expect(requestsTo(fetchMock, 'POST', '/api/posts/7/views')).toHaveLength(0);
  });

  it('수정 중이면 마지막 발행본과 수정 중 안내를 함께 보인다', async () => {
    stubFetch({
      'GET /api/posts/7': () =>
        json(
          200,
          authorDetail({
            authorView: {
              hasDraft: true,
              draftSavedAt: '2026-10-03T05:03:00Z',
              hidden: false,
              hiddenReason: null,
            },
          }),
        ),
    });

    renderDetail();

    expect(await screen.findByTestId('post-content')).toHaveTextContent('마지막 발행본');
    expect(screen.getByTestId('draft-notice')).toHaveTextContent(
      '수정 중인 내용이 있어요(10월 3일 14:03 저장)',
    );
  });

  it('비공개 글은 🔒 비공개와 안내, 날짜는 발행 일자', async () => {
    stubFetch({
      'GET /api/posts/7': () =>
        json(
          200,
          authorDetail({
            visibility: 'PRIVATE',
            firstPublicAt: null,
            publishedAt: '2026-09-30T10:00:00Z',
            displayedAt: '2026-09-30T10:00:00Z',
          }),
        ),
    });

    renderDetail();

    expect(await screen.findByTestId('private-badge')).toHaveTextContent('🔒 비공개');
    expect(screen.getByText('나만 볼 수 있는 글이에요')).toBeInTheDocument();
    expect(screen.getByText('2026.09.30')).toBeInTheDocument();
  });

  it('숨겨진 글은 숨김 안내 (014 T041 — 사유 포함)', async () => {
    stubFetch({
      'GET /api/posts/7': () =>
        json(
          200,
          authorDetail({
            authorView: { hasDraft: false, draftSavedAt: null, hidden: true, hiddenReason: 'SPAM' },
          }),
        ),
    });

    renderDetail();

    expect(
      await screen.findByText(
        '운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요',
      ),
    ).toBeInTheDocument();
  });

  it('임시글 응답이면 에디터 주소로 바꿔 끼운다', async () => {
    stubFetch({
      'GET /api/posts/9': () => json(200, { id: 9, status: 'DRAFT', editorPath: '/write/9' }),
    });

    renderDetail('/@kim755030/posts/9');

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/write/9'));
    expect(screen.queryByTestId('post-content')).not.toBeInTheDocument();
  });
  describe('작성자 카드 [팔로우] (010 T023)', () => {
    function readerDetail(followingAuthor: boolean, loggedIn = true) {
      return authorDetail({
        viewer: {
          loggedIn,
          isAuthor: false,
          likedByMe: false,
          followingAuthor,
          emailVerified: true,
          isAdmin: false,
        },
        authorView: null,
      });
    }

    it('독자에게는 작성자 카드에 [팔로우], 팔로우 중이면 [팔로잉 ✓]', async () => {
      stubFetch({ 'GET /api/posts/7': () => json(200, readerDetail(true)) });

      renderDetail();

      const card = await screen.findByTestId('author-card');
      const button = within(card).getByRole('button', { pressed: true });
      expect(button).toHaveTextContent('팔로잉 ✓');
    });

    it('누르면 PUT /api/members/{작성자}/follow', async () => {
      const fetchMock = stubFetch({
        'GET /api/posts/7': () => json(200, readerDetail(false)),
        'PUT /api/members/kim755030/follow': () => json(200, { following: true, followerCount: 1 }),
      });

      renderDetail();

      const card = await screen.findByTestId('author-card');
      fireEvent.click(within(card).getByRole('button', { name: '팔로우' }));
      expect(within(card).getByRole('button', { pressed: true })).toHaveTextContent('팔로잉 ✓');
      await act(async () => {
        await vi.advanceTimersByTimeAsync(FOLLOW_DEBOUNCE_MS);
      });
      expect(requestsTo(fetchMock, 'PUT', '/api/members/kim755030/follow')).toHaveLength(1);
    });

    it('비회원이 누르면 요청 없이 로그인 안내', async () => {
      const fetchMock = stubFetch({
        'GET /api/posts/7': () => json(200, readerDetail(false, false)),
      });

      renderDetail();

      const card = await screen.findByTestId('author-card');
      fireEvent.click(within(card).getByRole('button', { name: '팔로우' }));
      expect(within(card).getByRole('alert')).toHaveTextContent('로그인이 필요해요');
      expect(requestsTo(fetchMock, 'PUT', '/api/members/kim755030/follow')).toHaveLength(0);
    });
  });
});
