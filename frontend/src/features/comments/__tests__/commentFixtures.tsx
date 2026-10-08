import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { CommentPage, CommentView, RootCommentView } from '../../../api/types/comments';
import type { ViewerFlags } from '../../../api/types/viewerFlags';
import CommentSection, { type CommentSectionProps } from '../CommentSection';

let seq = 1000;

export function comment(overrides: Partial<CommentView> = {}): CommentView {
  const id = overrides.id ?? ++seq;
  return {
    id,
    state: 'NORMAL',
    content: `댓글 ${id}`,
    createdAt: '2026-10-07T03:00:00Z',
    edited: false,
    author: {
      handle: `user${id}`,
      nickname: `회원${id}`,
      profileImageUrl: null,
      isPostAuthor: false,
    },
    replyTo: null,
    mine: false,
    parentId: null,
    ...overrides,
  };
}

export function root(
  overrides: Partial<RootCommentView> = {},
  replies: CommentView[] = [],
): RootCommentView {
  const base = comment(overrides);
  return {
    ...base,
    replyCount: overrides.replyCount ?? replies.length,
    replies: overrides.replies ?? replies.map((r) => ({ ...r, parentId: base.id })),
    repliesNextCursor: overrides.repliesNextCursor ?? null,
  };
}

export function page(items: RootCommentView[], overrides: Partial<CommentPage> = {}): CommentPage {
  return { items, nextCursor: null, prevCursor: null, focusCommentId: null, ...overrides };
}

export function roots(count: number, startId: number): RootCommentView[] {
  return Array.from({ length: count }, (_, i) => root({ id: startId + i }));
}

export const MEMBER: ViewerFlags = {
  loggedIn: true,
  emailVerified: true,
  isAdmin: false,
  isAuthor: false,
};

export const GUEST: ViewerFlags = {
  loggedIn: false,
  emailVerified: false,
  isAdmin: false,
  isAuthor: false,
};

export function renderSection(props: Partial<CommentSectionProps> = {}, path = '/@kim/posts/7') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <CommentSection postId={7} commentCount={0} viewer={GUEST} {...props} />
    </MemoryRouter>,
  );
}

/** fetch mock의 호출 주소(쿼리 포함)들 */
export function urls(mock: { mock: { calls: unknown[][] } }, method = 'GET'): string[] {
  return mock.mock.calls
    .filter(([, init]) => ((init as RequestInit | undefined)?.method ?? 'GET') === method)
    .map(([input]) => String(input));
}
