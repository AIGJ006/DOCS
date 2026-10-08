import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import type { ViewerFlags } from '../api/types/viewerFlags';
import { requestsTo, stubFetch } from '../test/fetchRoutes';
import CommentInputGate from './CommentInputGate';

const flags = (overrides: Partial<ViewerFlags>): ViewerFlags => ({
  loggedIn: true,
  emailVerified: true,
  isAdmin: false,
  isAuthor: false,
  ...overrides,
});

function renderGate(viewer: ViewerFlags) {
  render(
    <MemoryRouter initialEntries={['/@kim/posts/42']}>
      <CommentInputGate viewer={viewer}>
        <textarea aria-label="댓글 입력" />
      </CommentInputGate>
    </MemoryRouter>,
  );
}

/** 댓글 입력창 안내 (004 T059, FR-045, US6-3). */
describe('CommentInputGate', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('비회원 → "로그인하고 댓글 쓰기" (지금 글로 돌아오는 로그인 링크), 입력창 없음', () => {
    renderGate(flags({ loggedIn: false, emailVerified: false }));

    expect(screen.getByRole('link', { name: '로그인하고 댓글 쓰기' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F%40kim%2Fposts%2F42',
    );
    expect(screen.queryByLabelText('댓글 입력')).toBeNull();
  });

  it('인증 전 → "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]"', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'POST /api/auth/email-verification': () => new Response(null, { status: 204 }),
    });
    vi.stubGlobal('fetch', mock);
    renderGate(flags({ emailVerified: false }));

    expect(screen.getByText('이메일 인증 후 댓글을 쓸 수 있어요')).toBeInTheDocument();
    expect(screen.queryByLabelText('댓글 입력')).toBeNull();
    await user.click(screen.getByRole('button', { name: '인증 메일 다시 보내기' }));
    expect(await screen.findByText('인증 메일을 보냈어요')).toBeInTheDocument();
    expect(requestsTo(mock, 'POST', '/api/auth/email-verification')).toHaveLength(1);
  });

  it.each([
    ['회원', flags({})],
    ['관리자', flags({ isAdmin: true })],
    ['작성자', flags({ isAuthor: true })],
  ])('%s → 입력창을 그대로 보인다', (_, viewer) => {
    renderGate(viewer);

    expect(screen.getByLabelText('댓글 입력')).toBeInTheDocument();
  });
});
