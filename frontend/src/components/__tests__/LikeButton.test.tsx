import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import type { ViewerFlags } from '../../api/types/viewerFlags';
import { LIKE_DEBOUNCE_MS } from '../../features/like/useLikeToggle';
import { json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import LikeButton from '../LikeButton';

const MEMBER: ViewerFlags = {
  loggedIn: true,
  emailVerified: true,
  isAdmin: false,
  isAuthor: false,
};

function renderButton(
  viewer: ViewerFlags,
  { liked = false, count = 12, path = '/@kim755030/posts/7?comment=3' } = {},
) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LikeButton postId={7} viewer={viewer} initialLiked={liked} initialCount={count} />
    </MemoryRouter>,
  );
}

/** 좋아요 버튼 (009 T014·T021, FR-013·FR-017, US2 #1~#3). */
describe('LikeButton', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    resetClientForTests();
  });

  it('누르지 않은 상태는 ♡와 "좋아요 (N)", 누른 상태는 ♥와 "좋아요 취소 (N)"', () => {
    const { unmount } = renderButton(MEMBER, { count: 12 });
    const button = screen.getByRole('button', { name: '좋아요 (12)' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    expect(button).toHaveTextContent('♡');
    unmount();

    renderButton(MEMBER, { liked: true, count: 13 });
    const pressed = screen.getByRole('button', { name: '좋아요 취소 (13)' });
    expect(pressed).toHaveAttribute('aria-pressed', 'true');
    expect(pressed).toHaveTextContent('♥');
    expect(screen.getByTestId('like-count')).toHaveTextContent('13');
  });

  it('1만 이상은 1.2만으로 보인다', () => {
    renderButton(MEMBER, { count: 12_999 });
    expect(screen.getByTestId('like-count')).toHaveTextContent('1.2만');
  });

  it('Enter·Space로 누를 수 있다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = stubFetch({
      'PUT /api/posts/7/like': () => json(200, { liked: true, likeCount: 13 }),
      'DELETE /api/posts/7/like': () => json(200, { liked: false, likeCount: 12 }),
    });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderButton(MEMBER);

    await user.tab();
    expect(screen.getByRole('button', { name: '좋아요 (12)' })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: '좋아요 취소 (13)' })).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(LIKE_DEBOUNCE_MS);
    });
    expect(requestsTo(fetchMock, 'PUT', '/api/posts/7/like')).toHaveLength(1);

    await user.keyboard(' ');
    expect(screen.getByRole('button', { name: '좋아요 (12)' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('실패하면 되돌리고 "좋아요를 반영하지 못했어요"를 알린다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    stubFetch({ 'PUT /api/posts/7/like': () => json(500, { code: 'INTERNAL_ERROR' }) });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderButton(MEMBER);

    await user.click(screen.getByRole('button', { name: '좋아요 (12)' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(LIKE_DEBOUNCE_MS);
    });

    expect(screen.getByRole('status')).toHaveTextContent('좋아요를 반영하지 못했어요');
    expect(screen.getByRole('button', { name: '좋아요 (12)' })).toBeInTheDocument();
  });

  it('비회원이 누르면 로그인 안내와 지금 주소로 돌아오는 로그인 링크, 요청 없음', async () => {
    const fetchMock = stubFetch({});
    const user = userEvent.setup();
    renderButton({ loggedIn: false, emailVerified: false, isAdmin: false, isAuthor: false });

    await user.click(screen.getByRole('button', { name: '좋아요 (12)' }));

    expect(screen.getByRole('status')).toHaveTextContent('로그인하고 좋아요를 눌러 보세요');
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      `/login?returnTo=${encodeURIComponent('/@kim755030/posts/7?comment=3')}`,
    );
    expect(screen.getByRole('button', { name: '좋아요 (12)' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('인증 전 회원이 누르면 "이메일 인증 후 누를 수 있어요", 요청 없음', async () => {
    const fetchMock = stubFetch({});
    const user = userEvent.setup();
    renderButton({ loggedIn: true, emailVerified: false, isAdmin: false, isAuthor: false });

    await user.click(screen.getByRole('button', { name: '좋아요 (12)' }));

    expect(screen.getByRole('status')).toHaveTextContent('이메일 인증 후 누를 수 있어요');
    expect(screen.getByRole('button', { name: '인증 메일 다시 보내기' })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('작성자는 버튼 없이 ♥와 수만', () => {
    renderButton({ loggedIn: true, emailVerified: true, isAdmin: false, isAuthor: true });

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByTestId('like-count')).toHaveTextContent('♥ 12');
  });

  it('401·403 응답도 같은 안내로 바꾼다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    stubFetch({
      'PUT /api/posts/7/like': () =>
        json(403, {
          code: 'EMAIL_NOT_VERIFIED',
          message: '이메일 인증 후 이용할 수 있어요',
          errors: [],
          details: null,
        }),
    });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderButton(MEMBER);

    await user.click(screen.getByRole('button', { name: '좋아요 (12)' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(LIKE_DEBOUNCE_MS);
    });

    expect(screen.getByRole('status')).toHaveTextContent('이메일 인증 후 누를 수 있어요');
    expect(screen.getByRole('button', { name: '좋아요 (12)' })).toBeInTheDocument();
  });
});
