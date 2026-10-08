/**
 * 댓글 API (007 contracts/openapi.yaml). 001 `client.ts`가 CSRF 헤더·오류 본문을 맡는다.
 *
 * 댓글 요청의 404는 글 화면 전체를 공통 404로 바꾸지 않는다(`notFoundScreen: false`) — 본문은 그대로 두고 댓글 영역이나 그 댓글
 * 아래에 문구를 보인다(Clarifications Q1).
 */
import { apiDelete, apiGet, apiPatch, apiPost } from './client';
import type { CommentPage, CommentView, CreateCommentRequest, ReplyPage } from './types/comments';

const QUIET = { notFoundScreen: false } as const;

function query(params: Record<string, string | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      search.set(name, value);
    }
  }
  const text = search.toString();
  return text === '' ? '' : `?${text}`;
}

export interface ListCommentsOptions {
  /** 이전 응답의 `nextCursor`·`prevCursor` 그대로 */
  cursor?: string | null;
  /** 알림 링크의 댓글 번호(`?comment=`) — 그 댓글의 최상위부터 */
  around?: string | number | null;
}

export function listComments(
  postId: number | string,
  { cursor, around }: ListCommentsOptions = {},
): Promise<CommentPage> {
  return apiGet<CommentPage>(
    `/api/posts/${postId}/comments${query({
      cursor,
      around: around === null || around === undefined ? null : String(around),
    })}`,
    QUIET,
  );
}

export function listReplies(rootId: number, cursor?: string | null): Promise<ReplyPage> {
  return apiGet<ReplyPage>(`/api/comments/${rootId}/replies${query({ cursor })}`, QUIET);
}

/** 새로 만들면 201, 10초 안 같은 요청이면 200으로 처음 댓글을 돌려준다(본문 모양은 같다). */
export function createComment(
  postId: number | string,
  body: CreateCommentRequest,
): Promise<CommentView> {
  return apiPost<CommentView>(`/api/posts/${postId}/comments`, body, QUIET);
}

export function editComment(commentId: number, content: string): Promise<CommentView> {
  return apiPatch<CommentView>(`/api/comments/${commentId}`, { content }, QUIET);
}

export function deleteComment(commentId: number): Promise<void> {
  return apiDelete<void>(`/api/comments/${commentId}`, undefined, QUIET);
}
