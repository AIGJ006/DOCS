import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PostAuthorView } from '../../../api/types/reading';
import { requestsTo, stubFetch } from '../../../test/fetchRoutes';
import AuthorStatusBanner, { HIDDEN_NOTICE, PRIVATE_NOTICE } from '../AuthorStatusBanner';

function authorView(overrides: Partial<PostAuthorView> = {}): PostAuthorView {
  return { hasDraft: false, draftSavedAt: null, hidden: false, hiddenReason: null, ...overrides };
}

function renderBanner(props: Partial<Parameters<typeof AuthorStatusBanner>[0]> = {}) {
  const onDiscarded = vi.fn();
  render(
    <MemoryRouter>
      <AuthorStatusBanner
        postId={7}
        visibility="PUBLIC"
        authorView={authorView()}
        onDiscarded={onDiscarded}
        {...props}
      />
    </MemoryRouter>,
  );
  return { onDiscarded };
}

/** 작성자 안내 (005 T054, US4 #1·#3·#4, FR-038·039). */
describe('AuthorStatusBanner', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('공개·작업본 없음·숨김 아님이면 아무것도 그리지 않는다', () => {
    renderBanner();

    expect(screen.queryByTestId('author-status')).not.toBeInTheDocument();
  });

  it('수정 중이면 저장 시각(한국 시간)과 [이어서 수정]·[변경 취소]를 보인다', () => {
    renderBanner({
      authorView: authorView({ hasDraft: true, draftSavedAt: '2026-10-03T05:03:00Z' }),
    });

    expect(screen.getByTestId('draft-notice')).toHaveTextContent(
      '수정 중인 내용이 있어요(10월 3일 14:03 저장)',
    );
    expect(screen.getByRole('link', { name: '이어서 수정' })).toHaveAttribute('href', '/write/7');
    expect(screen.getByRole('button', { name: '변경 취소' })).toBeInTheDocument();
  });

  it('[변경 취소]는 확인 뒤 작업본을 버리고 상세를 다시 부른다', async () => {
    const fetchMock = stubFetch({
      'DELETE /api/posts/7/working-copy': () => new Response(null, { status: 204 }),
    });
    const { onDiscarded } = renderBanner({
      authorView: authorView({ hasDraft: true, draftSavedAt: '2026-10-03T05:03:00Z' }),
    });
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '변경 취소' }));
    expect(requestsTo(fetchMock, 'DELETE', '/api/posts/7/working-copy')).toHaveLength(0);
    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '버리기' }));

    await waitFor(() => expect(onDiscarded).toHaveBeenCalledTimes(1));
    expect(requestsTo(fetchMock, 'DELETE', '/api/posts/7/working-copy')).toHaveLength(1);
  });

  it('확인 창에서 [계속 고치기]를 고르면 아무 요청도 하지 않는다', async () => {
    const fetchMock = stubFetch({});
    const { onDiscarded } = renderBanner({
      authorView: authorView({ hasDraft: true, draftSavedAt: '2026-10-03T05:03:00Z' }),
    });
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '변경 취소' }));
    await user.click(screen.getByRole('button', { name: '계속 고치기' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(onDiscarded).not.toHaveBeenCalled();
  });

  it('비공개 글은 🔒 비공개 표시와 "나만 볼 수 있는 글이에요"', () => {
    renderBanner({ visibility: 'PRIVATE' });

    expect(screen.getByTestId('private-badge')).toHaveTextContent('🔒 비공개');
    expect(screen.getByText(PRIVATE_NOTICE)).toBeInTheDocument();
    expect(PRIVATE_NOTICE).toBe('나만 볼 수 있는 글이에요');
  });

  it('숨겨진 글은 숨김 안내를 사유와 함께 보인다 (014 T041)', () => {
    renderBanner({ authorView: authorView({ hidden: true, hiddenReason: 'SPAM' }) });

    expect(screen.getByTestId('hidden-notice')).toHaveTextContent(
      '운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요',
    );
    expect(HIDDEN_NOTICE).toBe('운영 정책에 따라 숨겨진 글이에요. 다른 사람에게는 보이지 않아요');
  });

  it('사유가 없는 숨김은 괄호 없이', () => {
    renderBanner({ authorView: authorView({ hidden: true, hiddenReason: null }) });

    expect(screen.getByTestId('hidden-notice')).toHaveTextContent(HIDDEN_NOTICE);
  });
});
