import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { json, stubFetch } from '../../../test/fetchRoutes';
import { comment, page, renderSection, root, roots, urls } from './commentFixtures';

/** 알림에서 특정 댓글로 바로 가기 (007 T051, US5, FR-025). */
describe('CommentSection around', () => {
  let scrolled: HTMLElement[];

  beforeEach(() => {
    resetClientForTests();
    scrolled = [];
    Element.prototype.scrollIntoView = vi.fn(function (this: HTMLElement) {
      scrolled.push(this);
    });
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('aroundCommentId면 around로 부르고, 대상으로 스크롤해 2초 강조한다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const replies = [1, 2, 3, 4, 5].map((n) => comment({ id: 500 + n, parentId: 45 }));
    const mock = stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page(
            [root({ id: 45, replyCount: 8, repliesNextCursor: 'r5' }, replies), ...roots(19, 46)],
            {
              prevCursor: 'p45',
              nextCursor: 'n64',
              focusCommentId: 505,
            },
          ),
        ),
    });

    renderSection({ commentCount: 80, aroundCommentId: '505' });

    await waitFor(() => expect(scrolled.map((el) => el.id)).toEqual(['comment-505']));
    expect(urls(mock)[0]).toBe('/api/posts/7/comments?around=505');
    const target = document.getElementById('comment-505') as HTMLElement;
    expect(target).toHaveClass('comment-focus');
    expect(screen.getByRole('button', { name: '이전 댓글 보기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '답글 3개 더 보기' })).toBeInTheDocument();

    await act(async () => {
      vi.advanceTimersByTime(2100);
    });
    expect(target).not.toHaveClass('comment-focus');
  });

  it('[이전 댓글 보기]는 prevCursor로 불러 앞에 붙인다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({});
    mock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('cursor=p21')) {
        return json(200, page(roots(20, 1), { prevCursor: null }));
      }
      return json(
        200,
        page(roots(20, 21), { prevCursor: 'p21', nextCursor: null, focusCommentId: 21 }),
      );
    });

    renderSection({ commentCount: 40, aroundCommentId: '21' });
    await waitFor(() => expect(screen.getAllByTestId('comment-item')).toHaveLength(20));

    await user.click(screen.getByRole('button', { name: '이전 댓글 보기' }));

    await waitFor(() => expect(screen.getAllByTestId('comment-item')).toHaveLength(40));
    expect(screen.getAllByTestId('comment-item')[0]).toHaveAttribute('id', 'comment-1');
    expect(screen.queryByRole('button', { name: '이전 댓글 보기' })).toBeNull();
    expect(urls(mock)[1]).toBe('/api/posts/7/comments?cursor=p21');
  });

  it('focusCommentId가 null이면(볼 수 없는 대상) 첫 페이지만 보이고 스크롤하지 않는다', async () => {
    stubFetch({
      'GET /api/posts/7/comments': () => json(200, page(roots(3, 1))),
    });

    renderSection({ commentCount: 3, aroundCommentId: '999' });

    await waitFor(() => expect(screen.getAllByTestId('comment-item')).toHaveLength(3));
    expect(scrolled).toHaveLength(0);
    expect(document.querySelector('.comment-focus')).toBeNull();
  });
});
