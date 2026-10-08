/**
 * 댓글 API 응답·요청 모양 (007 contracts/openapi.yaml, data-model §4).
 */

/** 표시 상태 — 우선순위 WITHDRAWN_AUTHOR > DELETED > HIDDEN > NORMAL (서버가 정한다). */
export type CommentState = 'NORMAL' | 'DELETED' | 'HIDDEN' | 'WITHDRAWN_AUTHOR';

export interface CommentAuthor {
  handle: string;
  nickname: string;
  /** 없으면 기본 아이콘 */
  profileImageUrl: string | null;
  /** [작성자] 배지 */
  isPostAuthor: boolean;
}

/** 답글의 답글일 때만. 대상이 탈퇴했으면 `{ withdrawn: true }`. */
export type ReplyTo = { handle: string; nickname: string } | { withdrawn: true };

export interface CommentView {
  id: number;
  state: CommentState;
  /** DELETED·WITHDRAWN_AUTHOR는 null, HIDDEN은 작성자 본인에게만 값 */
  content: string | null;
  createdAt: string;
  /** "· 수정됨" */
  edited: boolean;
  author: CommentAuthor | null;
  replyTo: ReplyTo | null;
  /** 보는 사람이 작성자인가 (비회원은 false) */
  mine: boolean;
  parentId: number | null;
}

export interface RootCommentView extends CommentView {
  /** 숨긴 답글 포함 — [답글 N개 더 보기]의 N = replyCount - replies.length */
  replyCount: number;
  replies: CommentView[];
  repliesNextCursor: string | null;
}

export interface CommentPage {
  items: RootCommentView[];
  nextCursor: string | null;
  /** around·이전 방향일 때 앞에 더 있으면 */
  prevCursor: string | null;
  /** around 대상을 펼쳤으면 그 번호 */
  focusCommentId: number | null;
}

export interface ReplyPage {
  items: CommentView[];
  nextCursor: string | null;
}

export interface CreateCommentRequest {
  content: string;
  replyToCommentId?: number | null;
}
