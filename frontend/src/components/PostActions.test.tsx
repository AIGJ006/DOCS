import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, resetClientForTests } from '../api/client';
import type { ViewerFlags } from '../api/types/viewerFlags';
import PostActions, { type PostActionsProps } from './PostActions';

const AUTHOR: ViewerFlags = { loggedIn: true, emailVerified: true, isAdmin: false, isAuthor: true };
const MEMBER: ViewerFlags = {
  loggedIn: true,
  emailVerified: true,
  isAdmin: false,
  isAuthor: false,
};
const UNVERIFIED: ViewerFlags = {
  loggedIn: true,
  emailVerified: false,
  isAdmin: false,
  isAuthor: false,
};
const ANONYMOUS: ViewerFlags = {
  loggedIn: false,
  emailVerified: false,
  isAdmin: false,
  isAuthor: false,
};
const ADMIN: ViewerFlags = { loggedIn: true, emailVerified: true, isAdmin: true, isAuthor: false };

function renderActions(props: Partial<PostActionsProps> & { viewer: ViewerFlags }) {
  const handlers = {
    onLike: vi.fn(async () => {}),
    onReport: vi.fn(),
    onFollow: vi.fn(async () => {}),
    onHide: vi.fn(),
    onUnhide: vi.fn(),
    onDelete: vi.fn(),
  };
  render(
    <MemoryRouter initialEntries={['/@kim/posts/42']}>
      <PostActions
        postId={42}
        visibility="PUBLIC"
        likeCount={3}
        editPath="/write/42"
        {...handlers}
        {...props}
      />
    </MemoryRouter>,
  );
  return handlers;
}

const buttonNames = () => screen.queryAllByRole('button').map((b) => b.textContent);

/** 글 상세 버튼 표시 규칙 (004 T058, FR-045, US6). */
describe('PostActions', () => {
  beforeEach(() => resetClientForTests());
  afterEach(() => vi.unstubAllGlobals());

  it('US6-1 작성자에게는 [수정]·[공개 범위]·[삭제]와 좋아요 수만 보이고 [좋아요]·[신고]·[팔로우]는 없다', () => {
    renderActions({ viewer: AUTHOR });

    expect(screen.getByRole('link', { name: '수정' })).toHaveAttribute('href', '/write/42');
    expect(screen.getByRole('combobox', { name: '공개 범위' })).toHaveValue('PUBLIC');
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument();
    expect(screen.getByTestId('post-actions-like-count')).toHaveTextContent('3');
    for (const name of ['좋아요', '신고', '팔로우']) {
      expect(screen.queryByRole('button', { name })).toBeNull();
    }
  });

  it.each([
    ['비회원', ANONYMOUS],
    ['인증 전 회원', UNVERIFIED],
    ['다른 회원', MEMBER],
  ])('US6-2 %s에게는 [좋아요]·[신고]가 보이고 [수정]·[공개 범위]·[삭제]는 없다', (_, viewer) => {
    renderActions({ viewer });

    expect(buttonNames()).toEqual(expect.arrayContaining(['좋아요', '신고']));
    expect(screen.queryByRole('link', { name: '수정' })).toBeNull();
    expect(screen.queryByRole('combobox', { name: '공개 범위' })).toBeNull();
    expect(screen.queryByRole('button', { name: '삭제' })).toBeNull();
    expect(screen.queryByRole('button', { name: '숨김' })).toBeNull();
  });

  it('US6-2 비회원이 [좋아요]를 누르면 로그인 안내만 나오고 아무 요청도 보내지 않는다', async () => {
    const user = userEvent.setup();
    const handlers = renderActions({ viewer: ANONYMOUS });

    await user.click(screen.getByRole('button', { name: '좋아요' }));

    expect(handlers.onLike).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요해요');
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F%40kim%2Fposts%2F42',
    );
  });

  it('인증 전 회원이 [신고]·[팔로우]를 누르면 이메일 인증 안내가 나오고 실행하지 않는다', async () => {
    const user = userEvent.setup();
    const handlers = renderActions({ viewer: UNVERIFIED });

    await user.click(screen.getByRole('button', { name: '신고' }));
    expect(screen.getByRole('alert')).toHaveTextContent('이메일 인증 후 이용할 수 있어요');
    await user.click(screen.getByRole('button', { name: '팔로우' }));

    expect(handlers.onReport).not.toHaveBeenCalled();
    expect(handlers.onFollow).not.toHaveBeenCalled();
  });

  it('회원이 누르면 실행하고, 서버가 401로 거부하면 로그인 안내(자동 재시도 없음)', async () => {
    const user = userEvent.setup();
    const onLike = vi.fn(async () => {
      throw new ApiError(401, 'LOGIN_REQUIRED', '로그인이 필요해요');
    });
    renderActions({ viewer: MEMBER, onLike });

    await user.click(screen.getByRole('button', { name: '좋아요' }));

    expect(onLike).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('alert')).toHaveTextContent('로그인이 필요해요');
  });

  it('관리자에게는 [숨김]/[숨김 해제]만 더해지고 남의 글 [수정]·[삭제]는 없다', async () => {
    const user = userEvent.setup();
    const handlers = renderActions({ viewer: ADMIN });

    expect(screen.queryByRole('link', { name: '수정' })).toBeNull();
    expect(screen.queryByRole('button', { name: '삭제' })).toBeNull();
    await user.click(screen.getByRole('button', { name: '숨김' }));
    expect(handlers.onHide).toHaveBeenCalledTimes(1);
  });

  it('숨긴 글이면 관리자에게 [숨김 해제]가 보인다', () => {
    renderActions({ viewer: ADMIN, hidden: true });

    expect(screen.getByRole('button', { name: '숨김 해제' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '숨김' })).toBeNull();
  });

  it('[삭제] 자리를 006 부품으로 바꿔 끼울 수 있다', () => {
    renderActions({ viewer: AUTHOR, deleteControl: <button type="button">휴지통으로</button> });

    expect(screen.getByRole('button', { name: '휴지통으로' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제' })).toBeNull();
  });
});
